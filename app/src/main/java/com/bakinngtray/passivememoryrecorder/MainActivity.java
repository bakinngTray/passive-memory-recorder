package com.bakinngtray.passivememoryrecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private static final int REQ=100;
    private static final int REQ_UPLOAD_FOLDER=200;
    private TextView status;
    private TextView uploadStatus;
    private Button start;
    private Button stop;
    private boolean batteryPromptRequested=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        NotificationHelper.channels(this);
        buildUi();
        requestNeeded();
    }

    @Override protected void onResume(){
        super.onResume();
        refresh();
        if(hasMic()&&!Prefs.isRecording(this)) NotificationHelper.stopped(this,"Микрофон не записывает.");
        if(hasMic()) requestBatteryExemptionIfNeeded();
    }

    private void buildUi(){
        int p=dp(24);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p,p,p,p);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title=new TextView(this);
        title.setText("Passive Memory Recorder"); title.setTextSize(26); title.setTextColor(Color.BLACK); title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title,lp());

        status=new TextView(this);
        status.setTextSize(20); status.setPadding(0,dp(32),0,dp(24)); status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status,lp());

        start=new Button(this); start.setText("ВКЛЮЧИТЬ ЗАПИСЬ"); start.setOnClickListener(v->startRecording()); root.addView(start,lp());
        stop=new Button(this); stop.setText("Остановить запись"); stop.setOnClickListener(v->stopRecording()); root.addView(stop,lp());

        uploadStatus=new TextView(this);
        uploadStatus.setTextSize(15); uploadStatus.setPadding(0,dp(24),0,dp(4)); uploadStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(uploadStatus,lp());

        Button chooseUploadFolder=new Button(this);
        chooseUploadFolder.setText("ВЫБРАТЬ ПАПКУ В GOOGLE DRIVE");
        chooseUploadFolder.setOnClickListener(v->chooseUploadFolder());
        root.addView(chooseUploadFolder,lp());

        TextView info=new TextView(this);
        info.setText("Локальные файлы: Internal storage / Music / PassiveMemoryRecorder\n\nAAC/M4A, mono, автоматическая ротация примерно раз в час. После завершения каждого файла приложение пытается отправить все готовые записи в выбранную папку Google Drive. Локальный файл удаляется только после успешного полного копирования.");
        info.setTextSize(15); info.setPadding(0,dp(20),0,0); root.addView(info,lp());
        setContentView(root);
    }

    private void chooseUploadFolder(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent,REQ_UPLOAD_FOLDER);
    }

    private void startRecording(){
        if(!hasMic()){ requestNeeded(); return; }
        requestBatteryExemptionIfNeeded();
        startForegroundService(new Intent(this,RecordingService.class).setAction(RecordingService.ACTION_START));
        new Handler(Looper.getMainLooper()).postDelayed(this::refresh,600);
    }

    private void stopRecording(){
        startService(new Intent(this,RecordingService.class).setAction(RecordingService.ACTION_STOP));
        new Handler(Looper.getMainLooper()).postDelayed(this::refresh,400);
    }

    private void refresh(){
        boolean on=Prefs.isRecording(this);
        status.setText(on?"● ЗАПИСЬ АКТИВНА":"● ЗАПИСЬ ВЫКЛЮЧЕНА");
        status.setTextColor(on?Color.rgb(0,120,60):Color.rgb(190,20,20));
        start.setEnabled(!on); stop.setEnabled(on);
        uploadStatus.setText(Prefs.getUploadTreeUri(this)==null?"Google Drive: папка не выбрана":"Google Drive: папка выбрана");
    }

    private boolean hasMic(){ return checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED; }

    private void requestNeeded(){
        ArrayList<String> ps=new ArrayList<>();
        if(!hasMic())ps.add(Manifest.permission.RECORD_AUDIO);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)ps.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!ps.isEmpty())requestPermissions(ps.toArray(new String[0]),REQ);
    }

    private void requestBatteryExemptionIfNeeded(){
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.M || batteryPromptRequested) return;
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm==null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;

        batteryPromptRequested=true;
        try{
            Intent intent=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:"+getPackageName()));
            startActivity(intent);
        }catch(Exception ignored){
            try{
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            }catch(Exception ignoredToo){ }
        }
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQ_UPLOAD_FOLDER||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{
            getContentResolver().takePersistableUriPermission(uri,flags);
            Prefs.setUploadTreeUri(this,uri);
            UploadScheduler.schedule(this);
            Toast.makeText(this,"Папка выбрана. Готовые записи будут отправляться автоматически.",Toast.LENGTH_LONG).show();
        }catch(Exception e){
            Toast.makeText(this,"Не удалось сохранить доступ к папке.",Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ){
            refresh();
            if(hasMic()&&!Prefs.isRecording(this))NotificationHelper.stopped(this,"Микрофон не записывает.");
            if(hasMic())requestBatteryExemptionIfNeeded();
        }
    }

    private LinearLayout.LayoutParams lp(){ LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); x.setMargins(0,dp(8),0,dp(8)); return x; }
    private int dp(int v){ return Math.round(v*getResources().getDisplayMetrics().density); }
}
