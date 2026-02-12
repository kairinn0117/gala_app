package com.example.galafunctions;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class TripReminderWorker extends Worker {

    public static final String KEY_TRIP_ID = "tripId";
    public static final String KEY_TITLE = "title";
    public static final String KEY_MESSAGE = "message";

    public TripReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String tripId = getInputData().getString(KEY_TRIP_ID);
        String title = getInputData().getString(KEY_TITLE);
        String msg = getInputData().getString(KEY_MESSAGE);

        if (tripId == null) return Result.failure();

        NotificationUtils.showTripNotification(
                getApplicationContext(),
                tripId,
                title != null ? title : "GALA Reminder ⏰",
                msg != null ? msg : "Your trip is coming up. Ready ka na ba?"
        );

        return Result.success();
    }
}