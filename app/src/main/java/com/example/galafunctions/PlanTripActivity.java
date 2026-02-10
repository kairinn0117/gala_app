package com.example.galafunctions;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PlanTripActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "tripId";

    private EditText etPlanDate, etPlanTime;
    private Button btnCancel, btnSave;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;

    // store picked values
    private int pickedYear, pickedMonth, pickedDay;
    private int pickedHour24, pickedMinute;

    private boolean hasDate = false;
    private boolean hasTime = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_plan_trip);

        View main = findViewById(R.id.main);
        if (main != null) {
            ViewCompat.setOnApplyWindowInsetsListener(main, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etPlanDate = findViewById(R.id.etPlanDate);
        etPlanTime = findViewById(R.id.etPlanTime);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSaveSchedule);

        etPlanDate.setOnClickListener(v -> showDatePicker());
        etPlanTime.setOnClickListener(v -> showTimePicker());

        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveSchedule());
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        new DatePickerDialog(this, (view, y, m, d) -> {
            pickedYear = y;
            pickedMonth = m; // 0-based
            pickedDay = d;
            hasDate = true;

            String pickedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, (m + 1), d);
            etPlanDate.setText(pickedDate);
        }, year, month, day).show();
    }

    private void showTimePicker() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {
            pickedHour24 = hourOfDay;
            pickedMinute = minuteOfHour;
            hasTime = true;

            etPlanTime.setText(formatTo12Hour(hourOfDay, minuteOfHour));
        }, hour, minute, false).show();
    }

    private String formatTo12Hour(int hourOfDay, int minute) {
        String ampm = (hourOfDay >= 12) ? "PM" : "AM";
        int hour12 = hourOfDay % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, ampm);
    }

    private long toMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, pickedYear);
        cal.set(Calendar.MONTH, pickedMonth);
        cal.set(Calendar.DAY_OF_MONTH, pickedDay);
        cal.set(Calendar.HOUR_OF_DAY, pickedHour24);
        cal.set(Calendar.MINUTE, pickedMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void saveSchedule() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasDate) { etPlanDate.setError("Required"); return; }
        if (!hasTime) { etPlanTime.setError("Required"); return; }

        long scheduledMillis = toMillis();
        long now = System.currentTimeMillis();

        if (scheduledMillis <= now) {
            Toast.makeText(this, "Please choose a future time.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);

        String uid = auth.getCurrentUser().getUid();
        String dateStr = etPlanDate.getText().toString().trim();
        String timeStr = etPlanTime.getText().toString().trim();

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "SCHEDULED");
        updates.put("scheduled_date", dateStr);
        updates.put("scheduled_time", timeStr);
        updates.put("scheduled_at_millis", scheduledMillis);
        updates.put("scheduled_at", Timestamp.now());

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(unused -> {

                    // ✅ schedule local notification
                    scheduleReminder(tripId, scheduledMillis);

                    Toast.makeText(this, "Trip scheduled! We'll remind you.", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK, new Intent());
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void scheduleReminder(String tripId, long scheduledMillis) {

        // Android 13+: ask notification permission (simple version)
        if (Build.VERSION.SDK_INT >= 33) {
            // You can request permission in your main screen once; for now, just proceed.
            // If permission denied, notification won't show.
        }

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_TITLE, "GALA Reminder");
        intent.putExtra(ReminderReceiver.EXTRA_BODY, "Time to start your trip! Open the app and press Start.");

        // unique requestCode per trip (stable)
        int requestCode = tripId.hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            // exact alarm (best for reminders)
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledMillis,
                    pendingIntent
            );
        }
    }
}