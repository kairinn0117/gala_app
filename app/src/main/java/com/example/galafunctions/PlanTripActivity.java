package com.example.galafunctions;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
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

    private EditText etPlanDate;
    private ImageButton btnCancel, btnSave;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;

    private int pickedYear, pickedMonth, pickedDay;
    private boolean hasDate = false;

    // ✅ for discard detection
    private String originalDate = "";
    private boolean isSaving = false;

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

        // ✅ keep initial state (if may laman from xml or prefill someday)
        originalDate = safeText(etPlanDate);

        etPlanDate.setOnClickListener(v -> showDatePicker());

        // ✅ Cancel now confirms if there are changes
        btnCancel.setOnClickListener(v -> confirmDiscardIfNeeded());

        // ✅ Save now confirms
        btnSave.setOnClickListener(v -> confirmSaveSchedule());

        // ✅ Back gesture confirm discard if may changes
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmDiscardIfNeeded();
            }
        });
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();

        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {

            pickedYear = y;
            pickedMonth = m;
            pickedDay = d;
            hasDate = true;

            String pickedDate = String.format(Locale.getDefault(),
                    "%04d-%02d-%02d", y, (m + 1), d);

            etPlanDate.setText(pickedDate);

        }, c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH));

        // ✅ no past dates
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    // -------------------------
    // ✅ CONFIRMATIONS
    // -------------------------

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
        // if original empty then consider change if user picked a date
        if (TextUtils.isEmpty(originalDate)) {
            return hasDate && !TextUtils.isEmpty(current);
        }
        return !TextUtils.equals(originalDate, current);
    }

    private String safeText(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    // -------------------------
    // ✅ SAVE (same logic mo)
    // -------------------------

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

        // NOTE: scheduled_time + scheduled_sort_millis will come from FIRST destination.
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
}