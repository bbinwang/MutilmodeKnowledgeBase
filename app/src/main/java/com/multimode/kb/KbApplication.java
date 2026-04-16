package com.multimode.kb;

import android.app.Application;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.OneTimeWorkRequest;

import com.multimode.kb.di.AppComponent;
import com.multimode.kb.worker.ResumeWorker;

import io.objectbox.BoxStore;
import io.objectbox.android.Admin;

public class KbApplication extends Application implements Configuration.Provider {

    private static final String TAG = "KbApplication";
    private AppComponent appComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        appComponent = new AppComponent(this);
        appComponent.initialize();
        Log.i(TAG, "Knowledge Base initialized");

        // Enqueue resume worker to restart any incomplete indexing
        OneTimeWorkRequest resumeWork = new OneTimeWorkRequest.Builder(ResumeWorker.class).build();
        androidx.work.WorkManager.getInstance(this).enqueue(resumeWork);
    }

    public AppComponent getAppComponent() {
        return appComponent;
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(appComponent.getWorkerFactory())
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }
}
