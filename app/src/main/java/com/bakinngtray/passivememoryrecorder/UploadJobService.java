package com.bakinngtray.passivememoryrecorder;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public boolean onStartJob(JobParameters params) {
        executor.execute(() -> {
            try {
                DriveUploader.uploadAll(this);
            } finally {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        return false;
    }

    @Override public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
