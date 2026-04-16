package com.multimode.kb;

import android.app.Application;
import android.util.Log;

import androidx.work.Configuration;

import com.multimode.kb.di.AppComponent;

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
