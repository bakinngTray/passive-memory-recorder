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
    private static final long ROTATION_BYTES=22_000_000L;
    private static final String PATH="Music/PassiveMemoryRecorder";

    private MediaRecorder recorder;
    private Uri currentUri;
    private Uri nextUri;
    private ParcelFileDescriptor initialPfd;
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
            currentUri=createPending(fileNameNow());
            initialPfd=getContentResolver().openFileDescriptor(currentUri,"rw");
            if(initialPfd==null)throw new IOException("Не удалось открыть выходной файл");
            recorder=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioChannels(1);
            recorder.setAudioSamplingRate(44_100);
            recorder.setAudioEncodingBitRate(48_000);
            recorder.setMaxFileSize(ROTATION_BYTES);
            recorder.setOutputFile(initialPfd.getFileDescriptor());
            recorder.setOnInfoListener(this::onInfo);
            recorder.setOnErrorListener((mr,what,extra)->fail("Запись неожиданно остановилась (ошибка "+what+")."));
            recorder.prepare();
            recorder.start();
            Prefs.started(this);
        }catch(Exception e){ fail("Не удалось начать запись: "+safe(e)); }
    }

    private synchronized void onInfo(MediaRecorder mr,int what,int extra){
        if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_APPROACHING){ prepareNext(mr); }
        else if(what==MediaRecorder.MEDIA_RECORDER_INFO_NEXT_OUTPUT_FILE_STARTED){ switched(); }
        else if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED){ fail("Не удалось подготовить следующий файл."); }
    }

    private void prepareNext(MediaRecorder mr){
        if(nextUri!=null)return;
        ParcelFileDescriptor pfd=null;
        try{
            nextUri=createPending("PMR_pending_"+System.currentTimeMillis()+".m4a");
            pfd=getContentResolver().openFileDescriptor(nextUri,"rw");
            if(pfd==null)throw new IOException("Не удалось открыть следующий файл");
            mr.setNextOutputFile(pfd.getFileDescriptor());
            pfd.close();
        }catch(Exception e){ close(pfd); delete(nextUri); nextUri=null; fail("Не удалось продолжить запись: "+safe(e)); }
    }

    private synchronized void switched(){
        finalizeItem(currentUri); close(initialPfd); initialPfd=null;
        UploadScheduler.schedule(this);
        currentUri=nextUri; nextUri=null;
        rename(currentUri,fileNameNow());
    }

    private synchronized void stopAndNotify(String reason){
        intentionalStop=true; Prefs.stopped(this);
        if(recorder!=null){ try{ recorder.stop(); }catch(RuntimeException ignored){} recorder.reset(); recorder.release(); recorder=null; }
        close(initialPfd); initialPfd=null;
        finalizeItem(currentUri); currentUri=null;
        delete(nextUri); nextUri=null;
        UploadScheduler.schedule(this);
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        NotificationHelper.stopped(this,reason);
    }

    private synchronized void fail(String reason){
        Prefs.stopped(this);
        if(recorder!=null){ try{ recorder.reset(); }catch(RuntimeException ignored){} recorder.release(); recorder=null; }
        close(initialPfd); initialPfd=null;
        finalizeItem(currentUri); currentUri=null;
        delete(nextUri); nextUri=null;
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
    private void rename(Uri u,String n){ if(u==null)return; try{ ContentValues v=new ContentValues(); v.put(MediaStore.Audio.Media.DISPLAY_NAME,n); getContentResolver().update(u,v,null,null); }catch(Exception ignored){} }
    private void delete(Uri u){ if(u==null)return; try{ getContentResolver().delete(u,null,null); }catch(Exception ignored){} }
    private static void close(ParcelFileDescriptor p){ if(p==null)return; try{p.close();}catch(IOException ignored){} }
    private static String fileNameNow(){ return "PMR_"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.US).format(new Date())+".m4a"; }
    private static String safe(Exception e){ String m=e.getMessage(); return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m; }

    @Override public void onDestroy(){
        boolean active=recorder!=null;
        if(active){
            try{recorder.stop();}catch(RuntimeException ignored){}
            recorder.release(); recorder=null;
            close(initialPfd);
            finalizeItem(currentUri);
            delete(nextUri);
            UploadScheduler.schedule(this);
        }
        if(active&&!intentionalStop){ Prefs.stopped(this); NotificationHelper.stopped(this,"Запись неожиданно остановилась."); }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){ return null; }
}
