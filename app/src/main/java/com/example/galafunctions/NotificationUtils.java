package com.example.galafunctions;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotificationUtils {

    public static final String CHANNEL_ID = "gala_reminders";

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GALA Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Trip schedule reminders");

            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static void showTripNotification(Context context, String tripId, String title, String message) {
        ensureChannel(context);

        // tap notification body -> TripActivity
        Intent openTripIntent = new Intent(context, TripActivity.class);
        openTripIntent.putExtra("tripId", tripId);
        openTripIntent.putExtra("from_notification", true);
        openTripIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openTripPI = PendingIntent.getActivity(
                context,
                (tripId + "_open").hashCode(),
                openTripIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // action button -> TripActivity
        Intent readyIntent = new Intent(context, TripActivity.class);
        readyIntent.putExtra("tripId", tripId);
        readyIntent.putExtra("from_notification", true);
        readyIntent.putExtra("action_ready", true);
        readyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent readyPI = PendingIntent.getActivity(
                context,
                (tripId + "_ready").hashCode(),
                readyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_circle_notifications_24) // ✅ meron ka na
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openTripPI)
                .addAction(
                        R.drawable.baseline_check_circle_24, // ✅ make sure meron; if wala, change to existing drawable
                        "Are you ready to start?",
                        readyPI
                );

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((tripId + title + message).hashCode(), b.build());
    }
}
