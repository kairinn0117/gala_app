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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateDestination extends AppCompatActivity {

    // Basic fields
    private EditText etName, etLocation, etBudget, etDescription, etDestTime;
    private Spinner spType;

    private Button btnSave, btnCancel;
    private LinearLayout layoutDestinationBudget;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private boolean budgetEnabled = false;

    // Map picked coords (optional)
    private Double pickedLat = null;
    private Double pickedLng = null;

    // Time picker
    private int pickedHour24, pickedMinute;
    private boolean hasTime = false;

    // ✅ NEW: sequencing support
    private long baseDateMillis = -1L;      // midnight of trip date
    private long minAllowedMillis = -1L;    // lastEnd + 1 minute OR now (if today)
    private boolean baseLoaded = false;

    // duration per destination (for end_time_millis)
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

        // Bind views
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

        // ⏰ Time picker
        etDestTime.setOnClickListener(v -> showTimePicker());

        // Map picker
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                        String address = result.getData()
                                .getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);

                        double lat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LAT, 0);
                        double lng = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LNG, 0);

                        if (!TextUtils.isEmpty(address)) {
                            etLocation.setText(address);
                        }

                        // optional coords (for directions)
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
        loadTripBaseDateAndMinTime(); // ✅ NEW

        btnSave.setOnClickListener(v -> saveDestination());
    }

    // ✅ NEW: load trip date + last destination end_time_millis
    private void loadTripBaseDateAndMinTime() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(tripDoc -> {

                    // get trip date string
                    String dateStr = tripDoc.getString("scheduled_date");
                    if (TextUtils.isEmpty(dateStr)) dateStr = tripDoc.getString("date");

                    baseDateMillis = parseDateToMidnightMillis(dateStr);
                    if (baseDateMillis <= 0) {
                        // fallback: today midnight
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        baseDateMillis = cal.getTimeInMillis();
                    }

                    // now check last destination end time
                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .collection("destinations")
                            .orderBy("end_time_millis", Query.Direction.DESCENDING)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(qs -> {

                                long candidateMin = baseDateMillis; // default earliest is trip day start

                                if (!qs.isEmpty()) {
                                    Long lastEnd = qs.getDocuments().get(0).getLong("end_time_millis");
                                    if (lastEnd != null && lastEnd > 0) {
                                        candidateMin = lastEnd + 60000; // +1 min rule
                                    } else {
                                        // fallback if old destinations don't have end_time_millis
                                        // use their start_time_millis + 60 min
                                        Long lastStart = qs.getDocuments().get(0).getLong("start_time_millis");
                                        if (lastStart != null && lastStart > 0) {
                                            candidateMin = lastStart + (DEFAULT_DURATION_MINUTES * 60_000L) + 60000;
                                        }
                                    }
                                }

                                // if trip date is today -> also block past time
                                long now = System.currentTimeMillis();
                                if (isSameDay(now, baseDateMillis)) {
                                    if (candidateMin < now) candidateMin = now;
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

    // ⏰ TimePickerDialog with sequential validation
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

            // ✅ sequential validation
            if (minAllowedMillis > 0 && selectedMillis < minAllowedMillis) {
                Toast.makeText(
                        this,
                        "Time must be after previous destination.",
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            pickedHour24 = hourOfDay;
            pickedMinute = minuteOfHour;
            hasTime = true;

            etDestTime.setText(formatTo12Hour(hourOfDay, minuteOfHour));

        }, hour, minute, false).show();
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
                    budgetEnabled = b != null && b;
                    layoutDestinationBudget.setVisibility(
                            budgetEnabled ? View.VISIBLE : View.GONE
                    );

                    if (!budgetEnabled) {
                        etBudget.setText("");
                        etBudget.setError(null);
                    }
                });
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
            if (TextUtils.isEmpty(b)) {
                etBudget.setError("Required");
                return;
            }
            try {
                budget = Double.parseDouble(b);
                if (budget < 0) {
                    etBudget.setError("Must be 0 or more");
                    return;
                }
            } catch (Exception e) {
                etBudget.setError("Invalid number");
                return;
            }
        }

        // ✅ compute millis
        long startMillis = toMillisOnTripDate(pickedHour24, pickedMinute);
        long endMillis = startMillis + (DEFAULT_DURATION_MINUTES * 60_000L);

        // final safety check (in case changed habang naka-open)
        if (minAllowedMillis > 0 && startMillis < minAllowedMillis) {
            Toast.makeText(this, "Time must be after previous destination.", Toast.LENGTH_LONG).show();
            return;
        }

        btnSave.setEnabled(false);

        Map<String, Object> destination = new HashMap<>();
        destination.put("destination_name", name);
        destination.put("type", type);
        destination.put("location", location);
        destination.put("time", etDestTime.getText().toString());

        // ✅ NEW FIELDS
        destination.put("start_time_millis", startMillis);
        destination.put("end_time_millis", endMillis);

        // optional coords
        if (pickedLat != null && pickedLng != null) {
            destination.put("lat", pickedLat);
            destination.put("lng", pickedLng);
        }

        destination.put("description", description);
        destination.put("status", "PENDING");
        destination.put("created_at", Timestamp.now());

        if (budgetEnabled && budget != null) {
            destination.put("budget", budget);
            destination.put("spent_total", 0.0); // match StartGala field
        }

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .add(destination)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // -------- helpers --------

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
