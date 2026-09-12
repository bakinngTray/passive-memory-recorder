package com.bakinngtray.passivememoryrecorder;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

final class NotificationHelper {
    static final int RECORDING_ID = 1001;
    static final int STOPPED_ID = 1002;
    private static final String CH_RECORDING = "recording";
    private static final String CH_STOPPED = "stopped";

    static void channels(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(nm==null)return;
        NotificationChannel a=new NotificationChannel(CH_RECORDING,"Запись активна",NotificationManager.IMPORTANCE_LOW);
        a.setSound(null,null); a.enableVibration(false);
        NotificationChannel b=new NotificationChannel(CH_STOPPED,"Запись выключена",NotificationManager.IMPORTANCE_HIGH);
        b.enableVibration(true);
        nm.createNotificationChannel(a); nm.createNotificationChannel(b);
    }

    static Notification recording(Context c){
        PendingIntent stop=PendingIntent.getService(c,1,new Intent(c,RecordingService.class).setAction(RecordingService.ACTION_STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c,CH_RECORDING)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Пассивная память: запись активна")
                .setContentText("Music/PassiveMemoryRecorder")
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null,"Остановить",stop).build())
                .build();
    }

    static void stopped(Context c,String reason){
        if(Prefs.isSuppressed(c))return;
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        channels(c);
        PendingIntent start=PendingIntent.getForegroundService(c,2,new Intent(c,RecordingService.class).setAction(RecordingService.ACTION_START),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent suppress=PendingIntent.getBroadcast(c,3,new Intent(c,ReminderReceiver.class).setAction(ReminderReceiver.ACTION_SUPPRESS),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CH_STOPPED)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Пассивная память выключена")
                .setContentText(reason==null?"Микрофон не записывает.":reason)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ERROR)
                .addAction(new Notification.Action.Builder(null,"ВКЛЮЧИТЬ ЗАПИСЬ",start).build())
                .addAction(new Notification.Action.Builder(null,"НЕ НАПОМИНАТЬ",suppress).build())
                .build();
        NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null)nm.notify(STOPPED_ID,n);
    }

    static void cancelStopped(Context c){ NotificationManager nm=c.getSystemService(NotificationManager.class); if(nm!=null)nm.cancel(STOPPED_ID); }
}
