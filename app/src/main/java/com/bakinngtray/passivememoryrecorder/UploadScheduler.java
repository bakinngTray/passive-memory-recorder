package com.bakinngtray.passivememoryrecorder;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class UploadScheduler {
    private static final int JOB_ID = 7331;

    private UploadScheduler() {}

    static void schedule(Context context) {
        if (Prefs.getUploadTreeUri(context) == null) return;
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;

        JobInfo job = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, UploadJobService.class))
                .setMinimumLatency(0)
                .setOverrideDeadline(1_000)
                .build();
        scheduler.schedule(job);
    }
}
