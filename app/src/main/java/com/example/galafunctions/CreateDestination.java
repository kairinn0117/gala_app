package com.example.galafunctions;

import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateDestination extends AppCompatActivity {

    private EditText etName, etLocation, etBudget, etDescription, etDestTime;
    private Spinner spType;

    private Button btnSave, btnCancel;
    private LinearLayout layoutDestinationBudget;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private boolean budgetEnabled = false;

    // Map coords (optional)
    private Double pickedLat = null;
    private Double pickedLng = null;

    // Time picker
    private int pickedHour24, pickedMinute;
    private boolean hasTime = false;

    // sequencing
    private long baseDateMillis = -1L;     // midnight of scheduled_date
    private long minAllowedMillis = -1L;   // lastEnd + 1min OR now (if today)
    private boolean baseLoaded = false;

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private ActivityResultLauncher<Intent> mapPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_destination);

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

        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind
        etName = findViewById(R.id.etDestinationName);
        spType = findViewById(R.id.spDestinationType);
        etLocation = findViewById(R.id.etDestinationLocation);
        etDescription = findViewById(R.id.etDestinationDescription);
        etDestTime = findViewById(R.id.etDestTime);

        layoutDestinationBudget = findViewById(R.id.layoutDestinationBudget);
        etBudget = findViewById(R.id.etDestinationBudget);

        btnSave = findViewById(R.id.btnSaveDestination);
        btnCancel = findViewById(R.id.btnCancelDestination);

        Button btnSearchMap = findViewById(R.id.btnSearchMap);

        // time picker
        etDestTime.setOnClickListener(v -> showTimePicker());

        // map picker
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);
                        double lat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LAT, 0);
                        double lng = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LNG, 0);

                        if (!TextUtils.isEmpty(address)) etLocation.setText(address);

                        pickedLat = lat;
                        pickedLng = lng;
                    }
                }
        );

        btnSearchMap.setOnClickListener(v ->
                mapPickerLauncher.launch(new Intent(CreateDestination.this, MapPickerActivity.class))
        );

        btnCancel.setOnClickListener(v -> finish());

        loadTripSettings();
        loadTripBaseDateAndMinTime();

        btnSave.setOnClickListener(v -> saveDestination());
    }

    private void loadTripSettings() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(doc -> {
                    Boolean b = doc.getBoolean("budget_enabled");
                    budgetEnabled = (b != null && b);

                    layoutDestinationBudget.setVisibility(budgetEnabled ? View.VISIBLE : View.GONE);

                    if (!budgetEnabled) {
                        etBudget.setText("");
                        etBudget.setError(null);
                    }
                });
    }

    // ✅ Trip date + last destination ordering
    private void loadTripBaseDateAndMinTime() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(tripDoc -> {

                    String scheduledDate = tripDoc.getString("scheduled_date");
                    String fallbackDate = tripDoc.getString("date");
                    String dateStr = !TextUtils.isEmpty(scheduledDate) ? scheduledDate : fallbackDate;

                    baseDateMillis = parseDateToMidnightMillis(dateStr);
                    if (baseDateMillis <= 0) {
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        baseDateMillis = cal.getTimeInMillis();
                    }

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .collection("destinations")
                            .orderBy("end_time_millis", Query.Direction.DESCENDING)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(qs -> {
                                long candidateMin = baseDateMillis;

                                if (!qs.isEmpty()) {
                                    DocumentSnapshot last = qs.getDocuments().get(0);
                                    Long lastEnd = last.getLong("end_time_millis");
                                    Long lastStart = last.getLong("start_time_millis");

                                    if (lastEnd != null && lastEnd > 0) {
                                        candidateMin = lastEnd + 60_000L;
                                    } else if (lastStart != null && lastStart > 0) {
                                        candidateMin = lastStart + (DEFAULT_DURATION_MINUTES * 60_000L) + 60_000L;
                                    }
                                }

                                long now = System.currentTimeMillis();
                                if (isSameDay(now, baseDateMillis) && candidateMin < now) {
                                    candidateMin = now;
                                }

                                minAllowedMillis = candidateMin;
                                baseLoaded = true;
                            })
                            .addOnFailureListener(e -> {
                                baseLoaded = true;
                                minAllowedMillis = -1;
                            });

                })
                .addOnFailureListener(e -> {
                    baseLoaded = true;
                    minAllowedMillis = -1;
                });
    }

    private void showTimePicker() {
        if (!baseLoaded) {
            Toast.makeText(this, "Loading time rules… try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {

            long selectedMillis = toMillisOnTripDate(hourOfDay, minuteOfHour);

            if (minAllowedMillis > 0 && selectedMillis < minAllowedMillis) {
                Toast.makeText(this, "Must be after previous destination.", Toast.LENGTH_LONG).show();
                return;
            }

            pickedHour24 = hourOfDay;
            pickedMinute = minuteOfHour;
            hasTime = true;

            etDestTime.setText(formatTo12Hour(hourOfDay, minuteOfHour));

        }, hour, minute, false).show();
    }

    private void saveDestination() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        String name = etName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String type = (spType.getSelectedItem() != null) ? spType.getSelectedItem().toString() : "";

        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }
        if (!hasTime) { etDestTime.setError("Pick time"); return; }

        Double budget = null;
        if (budgetEnabled) {
            String b = etBudget.getText().toString().trim();
            if (TextUtils.isEmpty(b)) { etBudget.setError("Required"); return; }
            try {
                budget = Double.parseDouble(b);
                if (budget < 0) { etBudget.setError("Must be 0 or more"); return; }
            } catch (Exception e) {
                etBudget.setError("Invalid number");
                return;
            }
        }

        long startMillis = toMillisOnTripDate(pickedHour24, pickedMinute);
        long endMillis = startMillis + (DEFAULT_DURATION_MINUTES * 60_000L);

        if (minAllowedMillis > 0 && startMillis < minAllowedMillis) {
            Toast.makeText(this, "Time must be after previous destination.", Toast.LENGTH_LONG).show();
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> destination = new HashMap<>();
        destination.put("destination_name", name);
        destination.put("type", type);
        destination.put("location", location);
        destination.put("time", etDestTime.getText().toString().trim());
        destination.put("start_time_millis", startMillis);
        destination.put("end_time_millis", endMillis);

        if (pickedLat != null && pickedLng != null) {
            destination.put("lat", pickedLat);
            destination.put("lng", pickedLng);
        }

        destination.put("description", description);
        destination.put("status", "PENDING");
        destination.put("created_at", Timestamp.now());

        if (budgetEnabled && budget != null) {
            destination.put("budget", budget);
            destination.put("spent_total", 0.0);
        }

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .add(destination)
                .addOnSuccessListener(ref -> updateTripIfThisIsEarliestDestination(uid, startMillis))
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ✅ if THIS is earliest destination, update trip schedule time fields
    private void updateTripIfThisIsEarliestDestination(String uid, long thisStartMillis) {

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .orderBy("start_time_millis", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {

                    if (qs.isEmpty()) {
                        Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    Long earliest = qs.getDocuments().get(0).getLong("start_time_millis");
                    if (earliest == null) {
                        Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // if not earliest, no update
                    if (!earliest.equals(thisStartMillis)) {
                        Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String firstTimeStr = formatTo12Hour(pickedHour24, pickedMinute);

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("first_destination_time", firstTimeStr);
                    updates.put("first_destination_start_millis", earliest);

                    // keep scheduled list compatible
                    updates.put("scheduled_time", firstTimeStr);
                    updates.put("scheduled_sort_millis", earliest);

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(this, "Saved but trip time not synced.", Toast.LENGTH_SHORT).show();
                                finish();
                            });

                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private long toMillisOnTripDate(int hour24, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(baseDateMillis > 0 ? baseDateMillis : System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, hour24);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String formatTo12Hour(int hourOfDay, int minute) {
        String ampm = (hourOfDay >= 12) ? "PM" : "AM";
        int hour12 = hourOfDay % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, ampm);
    }

    private long parseDateToMidnightMillis(String yyyyMMdd) {
        if (TextUtils.isEmpty(yyyyMMdd)) return -1L;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(yyyyMMdd));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return -1L;
        }
    }

    private boolean isSameDay(long millisA, long millisB) {
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(millisA);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(millisB);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}