package com.bakinngtray.passivememoryrecorder;

import android.Manifest;
import android.app.Service;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class RecordingService extends Service {
    static final String ACTION_START="com.bakinngtray.passivememoryrecorder.START";
    static final String ACTION_STOP="com.bakinngtray.passivememoryrecorder.STOP";
    private static final long SEGMENT_MS=60L*60L*1000L;
    private static final String PATH="Music/PassiveMemoryRecorder";

    private final Handler rotationHandler=new Handler(Looper.getMainLooper());
    private final Runnable rotationRunnable=this::rotateSegment;

    private MediaRecorder recorder;
    private Uri currentUri;
    private ParcelFileDescriptor currentPfd;
    private boolean intentionalStop;

    @Override public void onCreate(){ super.onCreate(); NotificationHelper.channels(this); }

    @Override public int onStartCommand(Intent i,int flags,int startId){
        String action=i==null?null:i.getAction();
        if(ACTION_STOP.equals(action)){ stopAndNotify("Запись остановлена пользователем."); return START_NOT_STICKY; }
        if(ACTION_START.equals(action) || (i==null && Prefs.isRecording(this))){ startRecording(); return START_STICKY; }
        stopSelf(); return START_NOT_STICKY;
    }

    private synchronized void startRecording(){
        if(recorder!=null)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){ fail("Нет разрешения на микрофон."); return; }
        intentionalStop=false;
        NotificationHelper.cancelStopped(this);
        if(Build.VERSION.SDK_INT>=29){
            startForeground(NotificationHelper.RECORDING_ID,NotificationHelper.recording(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        }else{
            startForeground(NotificationHelper.RECORDING_ID,NotificationHelper.recording(this));
        }
        try{
            beginSegment();
            Prefs.started(this);
            scheduleRotation();
        }catch(Exception e){ fail("Не удалось начать запись: "+safe(e)); }
    }

    private void beginSegment() throws Exception {
        currentUri=createPending(fileNameNow());
        currentPfd=getContentResolver().openFileDescriptor(currentUri,"rw");
        if(currentPfd==null)throw new IOException("Не удалось открыть выходной файл");

        MediaRecorder next=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
        try{
            next.setAudioSource(MediaRecorder.AudioSource.MIC);
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            next.setAudioChannels(1);
            next.setAudioSamplingRate(44_100);
            next.setAudioEncodingBitRate(48_000);
            next.setOutputFile(currentPfd.getFileDescriptor());
            next.setOnErrorListener((mr,what,extra)->fail("Запись неожиданно остановилась (ошибка "+what+")."));
            next.prepare();
            next.start();
            recorder=next;
        }catch(Exception e){
            try{ next.release(); }catch(Exception ignored){}
            close(currentPfd); currentPfd=null;
            delete(currentUri); currentUri=null;
            throw e;
        }
    }

    private void scheduleRotation(){
        rotationHandler.removeCallbacks(rotationRunnable);
        rotationHandler.postDelayed(rotationRunnable,SEGMENT_MS);
    }

    private synchronized void rotateSegment(){
        if(recorder==null)return;
        rotationHandler.removeCallbacks(rotationRunnable);

        try{
            recorder.stop();
        }catch(RuntimeException e){
            fail("Не удалось завершить часовой файл: "+safe(e));
            return;
        }
        recorder.release(); recorder=null;
        close(currentPfd); currentPfd=null;
        finalizeItem(currentUri); currentUri=null;

        // Closed files can be uploaded while the next hour is already recording.
        UploadScheduler.schedule(this);

        try{
            beginSegment();
            scheduleRotation();
        }catch(Exception e){
            fail("Не удалось начать следующий час записи: "+safe(e));
        }
    }

    private synchronized void stopAndNotify(String reason){
        intentionalStop=true;
        rotationHandler.removeCallbacks(rotationRunnable);
        Prefs.stopped(this);
        if(recorder!=null){ try{ recorder.stop(); }catch(RuntimeException ignored){} recorder.release(); recorder=null; }
        close(currentPfd); currentPfd=null;
        finalizeItem(currentUri); currentUri=null;
        UploadScheduler.schedule(this);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        NotificationHelper.stopped(this,reason);
    }

    private synchronized void fail(String reason){
        rotationHandler.removeCallbacks(rotationRunnable);
        Prefs.stopped(this);
        if(recorder!=null){ try{ recorder.reset(); }catch(RuntimeException ignored){} recorder.release(); recorder=null; }
        close(currentPfd); currentPfd=null;
        finalizeItem(currentUri); currentUri=null;
        UploadScheduler.schedule(this);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        NotificationHelper.stopped(this,reason);
    }

    private Uri createPending(String name) throws IOException{
        ContentValues v=new ContentValues();
        v.put(MediaStore.Audio.Media.DISPLAY_NAME,name);
        v.put(MediaStore.Audio.Media.MIME_TYPE,"audio/mp4");
        v.put(MediaStore.Audio.Media.RELATIVE_PATH,PATH);
        v.put(MediaStore.Audio.Media.IS_PENDING,1);
        Uri u=getContentResolver().insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),v);
        if(u==null)throw new IOException("MediaStore не создал файл");
        return u;
    }

    private void finalizeItem(Uri u){ if(u==null)return; try{ ContentValues v=new ContentValues(); v.put(MediaStore.Audio.Media.IS_PENDING,0); getContentResolver().update(u,v,null,null); }catch(Exception ignored){} }
    private void delete(Uri u){ if(u==null)return; try{ getContentResolver().delete(u,null,null); }catch(Exception ignored){} }
    private static void close(ParcelFileDescriptor p){ if(p==null)return; try{p.close();}catch(IOException ignored){} }
    private static String fileNameNow(){ return "PMR_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+".m4a"; }
    private static String safe(Exception e){ String m=e.getMessage(); return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m; }

    @Override public void onDestroy(){
        rotationHandler.removeCallbacks(rotationRunnable);
        boolean active=recorder!=null;
        if(active){
            try{recorder.stop();}catch(RuntimeException ignored){}
            recorder.release(); recorder=null;
            close(currentPfd); currentPfd=null;
            finalizeItem(currentUri); currentUri=null;
            UploadScheduler.schedule(this);
        }
        if(active&&!intentionalStop){ Prefs.stopped(this); NotificationHelper.stopped(this,"Запись неожиданно остановилась."); }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }
}
