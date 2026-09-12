package com.bakinngtray.passivememoryrecorder;

import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i){
        if(i==null)return;
        String a=i.getAction();
        if(Intent.ACTION_BOOT_COMPLETED.equals(a)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)){
            Prefs.stopped(c);
            NotificationHelper.channels(c);
            NotificationHelper.stopped(c,"После перезагрузки или обновления нужно снова включить запись.");
        }
    }
}
