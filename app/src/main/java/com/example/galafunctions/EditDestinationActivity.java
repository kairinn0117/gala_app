package com.example.galafunctions;

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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EditDestinationActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "tripId";
    public static final String EXTRA_DEST_ID = "destinationId";

    private EditText etName, etLocation, etBudget, etDescription;
    private Spinner spType;

    // Time spinners (required)
    private Spinner spHour, spMinute, spAmPm;

    private LinearLayout layoutDestinationBudget;
    private Button btnCancel, btnSave;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private String destinationId;

    private boolean budgetEnabled = false;

    // optional coords
    private Double pickedLat = null;
    private Double pickedLng = null;

    private ActivityResultLauncher<Intent> mapPickerLauncher;

    // ✅ NEW: time rule bounds
    private long baseDateMillis = -1L;      // midnight of trip date
    private long minAllowedMillis = -1L;    // prevEnd + 1 min (or now if today)
    private long maxAllowedMillis = -1L;    // nextStart - 1 min (optional)
    private boolean rulesLoaded = false;

    // duration per destination (for end_time_millis)
    private static final int DEFAULT_DURATION_MINUTES = 60;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_destination);

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
        destinationId = getIntent().getStringExtra(EXTRA_DEST_ID);

        if (TextUtils.isEmpty(tripId) || TextUtils.isEmpty(destinationId)) {
            Toast.makeText(this, "Missing ids.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etName = findViewById(R.id.etDestinationName);
        spType = findViewById(R.id.spDestinationType);
        etLocation = findViewById(R.id.etDestinationLocation);

        layoutDestinationBudget = findViewById(R.id.layoutDestinationBudget);
        etBudget = findViewById(R.id.etDestinationBudget);

        spHour = findViewById(R.id.spHour);
        spMinute = findViewById(R.id.spMinute);
        spAmPm = findViewById(R.id.spAmPm);

        etDescription = findViewById(R.id.etDestinationDescription);

        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);

        Button btnSearchMap = findViewById(R.id.btnSearchMap);

        // Map picker
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);

                        double lat = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LAT, 0);
                        double lng = result.getData().getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LNG, 0);

                        if (!TextUtils.isEmpty(address)) {
                            etLocation.setText(address);
                        }

                        pickedLat = lat;
                        pickedLng = lng;
                    }
                }
        );

        btnSearchMap.setOnClickListener(v -> {
            Intent intent = new Intent(EditDestinationActivity.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveEdits());

        // 1) read trip settings (budget_enabled) so we can hide/show budget
        loadTripSettingsThenDestination();
    }

    private void loadTripSettingsThenDestination() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(tripDoc -> {
                    Boolean b = tripDoc.getBoolean("budget_enabled");
                    budgetEnabled = (b != null && b);

                    layoutDestinationBudget.setVisibility(budgetEnabled ? View.VISIBLE : View.GONE);
                    if (!budgetEnabled) {
                        etBudget.setText("");
                        etBudget.setError(null);
                    }

                    // ✅ load date + sequencing rules
                    String dateStr = tripDoc.getString("scheduled_date");
                    if (TextUtils.isEmpty(dateStr)) dateStr = tripDoc.getString("date");

                    baseDateMillis = parseDateToMidnightMillis(dateStr);
                    if (baseDateMillis <= 0) {
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        baseDateMillis = cal.getTimeInMillis();
                    }

                    // 2) now load destination details
                    loadDestination();
                })
                .addOnFailureListener(e -> {
                    // safe default
                    budgetEnabled = false;
                    layoutDestinationBudget.setVisibility(View.GONE);

                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    baseDateMillis = cal.getTimeInMillis();

                    loadDestination();
                });
    }

    private void loadDestination() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destinationId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Destination not found.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String name = doc.getString("destination_name");
                    String type = doc.getString("type");
                    String location = doc.getString("location");
                    Double budget = doc.getDouble("budget");
                    String description = doc.getString("description");
                    String time = doc.getString("time");

                    // coords
                    pickedLat = doc.getDouble("lat");
                    pickedLng = doc.getDouble("lng");

                    etName.setText(name != null ? name : "");
                    etLocation.setText(location != null ? location : "");
                    etDescription.setText(description != null ? description : "");

                    if (!TextUtils.isEmpty(type) && spType.getAdapter() != null) {
                        int pos = getSpinnerPosition(spType, type);
                        if (pos >= 0) spType.setSelection(pos);
                    }

                    if (budgetEnabled && budget != null) {
                        etBudget.setText(String.valueOf(budget));
                    }

                    if (!TextUtils.isEmpty(time)) {
                        setTimeSpinnersFromString(time);
                    }

                    // ✅ after we have current destination loaded -> compute bounds
                    computePrevNextBounds(uid);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ✅ NEW: prev and next destination bounds based on millis
    private void computePrevNextBounds(String uid) {
        rulesLoaded = false;
        minAllowedMillis = baseDateMillis; // earliest is day start by default
        maxAllowedMillis = -1L;

        // If trip date is today, earliest should not be past current time
        long now = System.currentTimeMillis();
        if (isSameDay(now, baseDateMillis) && minAllowedMillis < now) {
            minAllowedMillis = now;
        }

        // 1) get current destination start_time_millis (or compute from spinner later)
        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destinationId)
                .get()
                .addOnSuccessListener(currDoc -> {
                    Long currentStart = currDoc.getLong("start_time_millis");
                    if (currentStart == null || currentStart <= 0) {
                        // if old docs, we still can compute bounds by time field, but not perfect.
                        // We'll just rely on prev max end_time_millis and next min start_time_millis.
                        currentStart = Long.MAX_VALUE;
                    }

                    final long currentStartFinal = currentStart;

                    // PREV: latest end_time_millis less than currentStart
                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .collection("destinations")
                            .orderBy("end_time_millis", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(qsPrev -> {
                                long bestPrevEnd = -1L;

                                for (var d : qsPrev.getDocuments()) {
                                    if (d.getId().equals(destinationId)) continue;
                                    Long end = d.getLong("end_time_millis");
                                    if (end == null || end <= 0) continue;

                                    if (end < currentStartFinal) {
                                        bestPrevEnd = end;
                                        break; // because DESC, first match is nearest prev
                                    }
                                }

                                if (bestPrevEnd > 0) {
                                    long candidateMin = bestPrevEnd + 60000; // +1 min
                                    if (candidateMin > minAllowedMillis) minAllowedMillis = candidateMin;
                                }

                                // NEXT: earliest start_time_millis greater than currentStart
                                db.collection("users")
                                        .document(uid)
                                        .collection("trips")
                                        .document(tripId)
                                        .collection("destinations")
                                        .orderBy("start_time_millis", Query.Direction.ASCENDING)
                                        .get()
                                        .addOnSuccessListener(qsNext -> {
                                            long bestNextStart = -1L;

                                            for (var d : qsNext.getDocuments()) {
                                                if (d.getId().equals(destinationId)) continue;
                                                Long start = d.getLong("start_time_millis");
                                                if (start == null || start <= 0) continue;

                                                if (start > currentStartFinal) {
                                                    bestNextStart = start;
                                                    break; // ASC, first match is nearest next
                                                }
                                            }

                                            if (bestNextStart > 0) {
                                                maxAllowedMillis = bestNextStart - 60000; // -1 min
                                            }

                                            rulesLoaded = true;
                                        })
                                        .addOnFailureListener(e -> rulesLoaded = true);
                            })
                            .addOnFailureListener(e -> rulesLoaded = true);
                })
                .addOnFailureListener(e -> rulesLoaded = true);
    }

    private void saveEdits() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        String name = etName.getText().toString().trim();
        String type = (spType.getSelectedItem() != null) ? spType.getSelectedItem().toString().trim() : "";
        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        String hourStr = (spHour.getSelectedItem() != null) ? spHour.getSelectedItem().toString() : "";
        String minuteStr = (spMinute.getSelectedItem() != null) ? spMinute.getSelectedItem().toString() : "";
        String ampmStr = (spAmPm.getSelectedItem() != null) ? spAmPm.getSelectedItem().toString() : "";

        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }
        if (TextUtils.isEmpty(hourStr) || TextUtils.isEmpty(minuteStr) || TextUtils.isEmpty(ampmStr)) {
            Toast.makeText(this, "Please select a valid time.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!rulesLoaded) {
            Toast.makeText(this, "Loading time rules… try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        int hour12;
        int minute;
        try {
            hour12 = Integer.parseInt(hourStr);
            minute = Integer.parseInt(minuteStr);
        } catch (Exception e) {
            Toast.makeText(this, "Invalid time.", Toast.LENGTH_SHORT).show();
            return;
        }

        int hour24 = to24Hour(hour12, ampmStr);
        long startMillis = toMillisOnTripDate(hour24, minute);
        long endMillis = startMillis + (DEFAULT_DURATION_MINUTES * 60_000L);

        // ✅ VALIDATION: after prev +1 min
        if (minAllowedMillis > 0 && startMillis < minAllowedMillis) {
            Toast.makeText(this, "Time must be after previous destination.", Toast.LENGTH_LONG).show();
            return;
        }

        // ✅ VALIDATION: before next -1 min (if exists)
        if (maxAllowedMillis > 0 && startMillis > maxAllowedMillis) {
            Toast.makeText(this, "Time must be before next destination.", Toast.LENGTH_LONG).show();
            return;
        }

        // Also check end doesn't overlap next
        if (maxAllowedMillis > 0 && endMillis > (maxAllowedMillis + 60000)) {
            Toast.makeText(this, "This destination overlaps the next one.", Toast.LENGTH_LONG).show();
            return;
        }

        Double budget = null;
        if (budgetEnabled) {
            String budgetStr = etBudget.getText().toString().trim();
            if (!TextUtils.isEmpty(budgetStr)) {
                try {
                    budget = Double.parseDouble(budgetStr);
                    if (budget < 0) {
                        etBudget.setError("Must be 0 or more");
                        return;
                    }
                } catch (Exception e) {
                    etBudget.setError("Number only");
                    return;
                }
            } else {
                budget = null;
            }
        }

        btnSave.setEnabled(false);

        String timeStr = String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, ampmStr.toUpperCase());

        Map<String, Object> updates = new HashMap<>();
        updates.put("destination_name", name);
        updates.put("type", type);
        updates.put("location", location);
        updates.put("description", description);
        updates.put("time", timeStr);

        // ✅ NEW: millis fields
        updates.put("start_time_millis", startMillis);
        updates.put("end_time_millis", endMillis);

        // optional coords
        if (pickedLat != null && pickedLng != null) {
            updates.put("lat", pickedLat);
            updates.put("lng", pickedLng);
        }

        if (budgetEnabled) {
            updates.put("budget", budget);
        } else {
            updates.put("budget", null);
        }

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destinationId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Destination updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ------- helpers -------

    private int getSpinnerPosition(Spinner spinner, String value) {
        if (spinner.getAdapter() == null) return -1;
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            String item = String.valueOf(spinner.getAdapter().getItem(i));
            if (value.equalsIgnoreCase(item)) return i;
        }
        return -1;
    }

    private void setTimeSpinnersFromString(String time) {
        try {
            String t = time.trim(); // "9:05 PM"
            String[] parts = t.split(" ");
            if (parts.length < 2) return;

            String hm = parts[0];
            String ampm = parts[1].toUpperCase();

            String[] hmParts = hm.split(":");
            if (hmParts.length < 2) return;

            String hour = hmParts[0];
            String minute = hmParts[1];

            int hourPos = getSpinnerPosition(spHour, hour);
            int minPos = getSpinnerPosition(spMinute, minute);
            int ampmPos = getSpinnerPosition(spAmPm, ampm);

            if (hourPos >= 0) spHour.setSelection(hourPos);
            if (minPos >= 0) spMinute.setSelection(minPos);
            if (ampmPos >= 0) spAmPm.setSelection(ampmPos);

        } catch (Exception ignored) {}
    }

    private int to24Hour(int hour12, String ampm) {
        String ap = (ampm != null) ? ampm.toUpperCase() : "AM";
        int h = hour12 % 12;
        if ("PM".equals(ap)) h += 12;
        return h;
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