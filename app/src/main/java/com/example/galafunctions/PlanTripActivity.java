package com.example.galafunctions;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class PlanTripActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "tripId";
    private static final int REQ_POST_NOTIF = 1101;

    private EditText etPlanDate;
    private ImageButton btnCancel, btnSave;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;

    private boolean hasDate = false;

    private String originalDate = "";
    private boolean isSaving = false;

    // ✅ Force PH timezone
    private static final TimeZone PH_TZ = TimeZone.getTimeZone("Asia/Manila");

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
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSaveSchedule);

        originalDate = safeText(etPlanDate);

        etPlanDate.setOnClickListener(v -> showDatePicker());
        btnCancel.setOnClickListener(v -> confirmDiscardIfNeeded());
        btnSave.setOnClickListener(v -> confirmSaveSchedule());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscardIfNeeded();
            }
        });

        ensureNotificationPermission();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIF
                );
            }
        }
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance(PH_TZ);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            hasDate = true;

            String pickedDate = String.format(Locale.getDefault(),
                    "%04d-%02d-%02d", y, (m + 1), d);

            etPlanDate.setText(pickedDate);

        }, c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void confirmSaveSchedule() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasDate || TextUtils.isEmpty(safeText(etPlanDate))) {
            etPlanDate.setError("Required");
            return;
        }

        String dateStr = safeText(etPlanDate);

        new AlertDialog.Builder(this)
                .setTitle("Save Schedule?")
                .setMessage("Are you sure you want to set this trip date to:\n\n" + dateStr)
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Yes", (d, w) -> saveSchedule())
                .show();
    }

    private void confirmDiscardIfNeeded() {
        if (isSaving) return;

        if (!hasChanges()) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("You have unsaved changes. Are you sure you want to discard them?")
                .setNegativeButton("No", (d, w) -> {})
                .setPositiveButton("Yes", (d, w) -> finish())
                .show();
    }

    private boolean hasChanges() {
        String current = safeText(etPlanDate);
        if (TextUtils.isEmpty(originalDate)) {
            return hasDate && !TextUtils.isEmpty(current);
        }
        return !TextUtils.equals(originalDate, current);
    }

    private String safeText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    // -------------------------
    // ✅ DATE -> MILLIS (PH)
    // -------------------------
    private long parseDateAtPH(String dateStr, int hour, int minute) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            sdf.setLenient(false);
            sdf.setTimeZone(PH_TZ);

            Date d = sdf.parse(dateStr);
            if (d == null) return -1;

            Calendar c = Calendar.getInstance(PH_TZ);
            c.setTime(d);
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);

            return c.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    // reminder 1: day before 8:00 AM (PH)
    private long computeDayBefore8am(String dateStr) {
        long sameDay8am = parseDateAtPH(dateStr, 8, 0);
        if (sameDay8am <= 0) return -1;
        return sameDay8am - (24L * 60 * 60 * 1000);
    }

    // reminder 2: same day 8:00 AM (PH)
    private long computeSameDay8am(String dateStr) {
        return parseDateAtPH(dateStr, 8, 0);
    }

    private void saveSchedule() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasDate) {
            etPlanDate.setError("Required");
            return;
        }

        isSaving = true;
        btnSave.setEnabled(false);
        btnCancel.setEnabled(false);
        etPlanDate.setEnabled(false);

        String uid = auth.getCurrentUser().getUid();
        String dateStr = safeText(etPlanDate);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "SCHEDULED");
        updates.put("scheduled_date", dateStr);
        updates.put("scheduled_at", Timestamp.now());

        // these will be computed later based on first destination
        updates.put("scheduled_time", null);
        updates.put("scheduled_sort_millis", null);
        updates.put("first_destination_time", null);
        updates.put("first_destination_start_millis", null);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(unused -> {

                    // ✅ instant notification
                    NotificationUtils.showTripNotification(
                            PlanTripActivity.this,
                            tripId,
                            "Trip scheduled ✅",
                            "Your trip is set on " + dateStr
                    );

                    // ✅ reminder 1: day before 8AM (PH)
                    long dayBefore = computeDayBefore8am(dateStr);
                    if (dayBefore > 0) {
                        ReminderScheduler.scheduleReminder(
                                PlanTripActivity.this,
                                tripId + "_daybefore",
                                tripId,
                                dayBefore,
                                "GALA Reminder ⏰",
                                "Tomorrow na yung trip mo (" + dateStr + ")."
                        );
                    }

                    // ✅ reminder 2: same day 8AM (PH)
                    long sameDay = computeSameDay8am(dateStr);
                    if (sameDay > 0) {
                        ReminderScheduler.scheduleReminder(
                                PlanTripActivity.this,
                                tripId + "_sameday",
                                tripId,
                                sameDay,
                                "Trip starts today ✅",
                                "Today yung trip mo (" + dateStr + "). Open your trip details."
                        );
                    }

                    Toast.makeText(this, "Trip scheduled date saved!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK, new Intent());
                    finish();
                })
                .addOnFailureListener(e -> {
                    isSaving = false;
                    btnSave.setEnabled(true);
                    btnCancel.setEnabled(true);
                    etPlanDate.setEnabled(true);

                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_POST_NOTIF) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this, "Notifications are off. Reminders may not show.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
