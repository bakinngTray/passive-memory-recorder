package com.bakinngtray.passivememoryrecorder;

import android.content.*;

public class ReminderReceiver extends BroadcastReceiver {
    static final String ACTION_SUPPRESS="com.bakinngtray.passivememoryrecorder.SUPPRESS";
    @Override public void onReceive(Context c, Intent i){
        if(i!=null && ACTION_SUPPRESS.equals(i.getAction())){
            Prefs.suppress(c);
            NotificationHelper.cancelStopped(c);
        }
    }
}
