package com.example.galafunctions;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class ReminderScheduler {

    public static void scheduleReminder(
            Context context,
            String uniqueKey,         // ✅ changed from tripId -> uniqueKey
            String tripId,            // ✅ real tripId still passed to Worker
            long triggerAtMillis,
            String title,
            String message
    ) {
        long now = System.currentTimeMillis();
        long delay = triggerAtMillis - now;

        // if past na, gawin nating 10 sec para makita agad (safe fallback)
        if (delay < 0) delay = 10_000;

        Data data = new Data.Builder()
                .putString(TripReminderWorker.KEY_TRIP_ID, tripId)
                .putString(TripReminderWorker.KEY_TITLE, title)
                .putString(TripReminderWorker.KEY_MESSAGE, message)
                .build();

        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(TripReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build();

        // ✅ unique work per reminder type
        String workName = "trip_reminder_" + uniqueKey;

        WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, req);
    }

    public static void cancelReminder(Context context, String uniqueKey) {
        WorkManager.getInstance(context).cancelUniqueWork("trip_reminder_" + uniqueKey);
    }
}
