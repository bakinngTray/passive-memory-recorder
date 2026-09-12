package com.bakinngtray.passivememoryrecorder;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "pmr";
    private static final String RECORDING = "recording";
    private static final String SUPPRESSED = "suppressed";
    private static SharedPreferences p(Context c){ return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    static boolean isRecording(Context c){ return p(c).getBoolean(RECORDING,false); }
    static boolean isSuppressed(Context c){ return p(c).getBoolean(SUPPRESSED,false); }
    static void started(Context c){ p(c).edit().putBoolean(RECORDING,true).putBoolean(SUPPRESSED,false).apply(); }
    static void stopped(Context c){ p(c).edit().putBoolean(RECORDING,false).apply(); }
    static void suppress(Context c){ p(c).edit().putBoolean(SUPPRESSED,true).apply(); }
}
