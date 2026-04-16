package com.multimode.kb.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.multimode.kb.di.AppComponent;

import java.util.Map;

/**
 * Custom WorkerFactory that injects AppComponent dependencies into Workers.
 */
public class KbWorkerFactory extends WorkerFactory {

    private final AppComponent appComponent;

    public KbWorkerFactory(AppComponent appComponent) {
        this.appComponent = appComponent;
    }

    @Override
    public ListenableWorker createWorker(@NonNull Context appContext,
                                         @NonNull String workerClassName,
                                         @NonNull WorkerParameters workerParameters) {
        try {
            Class<? extends ListenableWorker> workerClass =
                    Class.forName(workerClassName).asSubclass(ListenableWorker.class);

            if (workerClass == DocumentIngestionWorker.class) {
                return new DocumentIngestionWorker(appContext, workerParameters, appComponent);
            }
            if (workerClass == EmbeddingGenerationWorker.class) {
                return new EmbeddingGenerationWorker(appContext, workerParameters, appComponent);
            }
            if (workerClass == DirectoryScanWorker.class) {
                return new DirectoryScanWorker(appContext, workerParameters, appComponent);
            }
            if (workerClass == ResumeWorker.class) {
                return new ResumeWorker(appContext, workerParameters, appComponent);
            }

            // Fallback: use default constructor
            return workerClass.getConstructor(Context.class, WorkerParameters.class)
                    .newInstance(appContext, workerParameters);

        } catch (Exception e) {
            throw new RuntimeException("Could not create worker: " + workerClassName, e);
        }
    }
}
