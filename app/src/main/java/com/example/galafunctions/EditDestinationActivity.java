package com.example.galafunctions;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
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

    private EditText etName, etLocation, etBudget, etDescription, etDestTime, etCustomType;
    private Spinner spType;

    private LinearLayout layoutDestinationBudget;

    private ImageButton btnSave, btnSearchMap, btnCancel;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private String destinationId;

    private boolean budgetEnabled = false;

    // optional coords
    private Double pickedLat = null;
    private Double pickedLng = null;

    private ActivityResultLauncher<Intent> mapPickerLauncher;

    // time picker state
    private int pickedHour24, pickedMinute;
    private boolean hasTime = false;

    // time rule bounds
    private long baseDateMillis = -1L;      // midnight of trip date
    private long minAllowedMillis = -1L;    // prevEnd + 1 min (or now if today)
    private long maxAllowedMillis = -1L;    // nextStart - 1 min (optional)
    private boolean rulesLoaded = false;

    private static final int DEFAULT_DURATION_MINUTES = 60;

    private boolean isSaving = false;

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

        // Bind
        etName = findViewById(R.id.etDestinationName);
        spType = findViewById(R.id.spDestinationType);
        etCustomType = findViewById(R.id.etCustomDestinationType);

        etLocation = findViewById(R.id.etDestinationLocation);

        layoutDestinationBudget = findViewById(R.id.layoutDestinationBudget);
        etBudget = findViewById(R.id.etDestinationBudget);

        etDestTime = findViewById(R.id.etDestTime);
        etDescription = findViewById(R.id.etDestinationDescription);

        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);
        btnSearchMap = findViewById(R.id.btnSearchMap);

        // ✅ Back confirm (no deprecated onBackPressed)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isSaving) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    return;
                }
                showCancelConfirm();
            }
        });

        // ✅ map-only location + tap opens map
        lockLocationField();

        // type spinner -> show custom field on Others
        spType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (spType.getSelectedItem() != null)
                        ? spType.getSelectedItem().toString().trim()
                        : "";

                if (isOther(selected)) {
                    etCustomType.setVisibility(View.VISIBLE);
                } else {
                    etCustomType.setVisibility(View.GONE);
                    etCustomType.setText("");
                    etCustomType.setError(null);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // time picker click
        etDestTime.setOnClickListener(v -> showTimePicker());

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
                            etLocation.setError(null);
                        }

                        pickedLat = lat;
                        pickedLng = lng;
                    }
                }
        );

        View.OnClickListener openMap = v ->
                mapPickerLauncher.launch(new Intent(EditDestinationActivity.this, MapPickerActivity.class));

        btnSearchMap.setOnClickListener(openMap);
        etLocation.setOnClickListener(openMap);

        btnCancel.setOnClickListener(v -> {
            if (isSaving) return;
            showCancelConfirm();
        });

        btnSave.setOnClickListener(v -> {
            if (isSaving) return;
            if (!validateInputs()) return;
            showSaveConfirm();
        });

        loadTripSettingsThenDestination();
    }

    // ---------- Confirm dialogs ----------

    private void showSaveConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Save changes?")
                .setMessage("Are you sure you want to update this destination?")
                .setNegativeButton("No", (d, w) -> d.dismiss())
                .setPositiveButton("Yes", (d, w) -> {
                    d.dismiss();
                    saveEdits();
                })
                .show();
    }

    private void showCancelConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("Are you sure you want to cancel? Your changes will be lost.")
                .setNegativeButton("No", (d, w) -> d.dismiss())
                .setPositiveButton("Yes", (d, w) -> {
                    d.dismiss();
                    finish();
                })
                .show();
    }

    // ---------- Validation ----------

    private boolean validateInputs() {
        String name = etName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();

        String selectedType = (spType.getSelectedItem() != null) ? spType.getSelectedItem().toString().trim() : "";
        String customType = etCustomType.getText().toString().trim();

        etName.setError(null);
        etLocation.setError(null);
        etDestTime.setError(null);
        etBudget.setError(null);
        etCustomType.setError(null);

        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return false; }
        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Required (pick from map)");
            Toast.makeText(this, "Please pick a location using the map.", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (isOther(selectedType) && TextUtils.isEmpty(customType)) {
            etCustomType.setError("Required");
            return false;
        }

        if (!hasTime) { etDestTime.setError("Pick time"); return false; }

        if (!rulesLoaded) {
            Toast.makeText(this, "Loading time rules… try again.", Toast.LENGTH_SHORT).show();
            return false;
        }

        long startMillis = toMillisOnTripDate(pickedHour24, pickedMinute);
        long endMillis = startMillis + (DEFAULT_DURATION_MINUTES * 60_000L);

        if (minAllowedMillis > 0 && startMillis < minAllowedMillis) {
            Toast.makeText(this, "Time must be after previous destination.", Toast.LENGTH_LONG).show();
            return false;
        }

        if (maxAllowedMillis > 0 && startMillis > maxAllowedMillis) {
            Toast.makeText(this, "Time must be before next destination.", Toast.LENGTH_LONG).show();
            return false;
        }

        if (maxAllowedMillis > 0 && endMillis > (maxAllowedMillis + 60_000L)) {
            Toast.makeText(this, "This destination overlaps the next one.", Toast.LENGTH_LONG).show();
            return false;
        }

        if (budgetEnabled) {
            String budgetStr = etBudget.getText().toString().trim();
            if (!TextUtils.isEmpty(budgetStr)) {
                try {
                    double b = Double.parseDouble(budgetStr);
                    if (b < 0) { etBudget.setError("Must be 0 or more"); return false; }
                } catch (Exception e) {
                    etBudget.setError("Number only");
                    return false;
                }
            }
        }

        return true;
    }

    // ---------- Trip settings ----------

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

                    loadDestination();
                })
                .addOnFailureListener(e -> {
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

                    pickedLat = doc.getDouble("lat");
                    pickedLng = doc.getDouble("lng");

                    etName.setText(name != null ? name : "");
                    etLocation.setText(location != null ? location : "");
                    etDescription.setText(description != null ? description : "");

                    // spinner selection; if not found -> set to Others + fill custom
                    if (!TextUtils.isEmpty(type) && spType.getAdapter() != null) {
                        int pos = getSpinnerPosition(spType, type);
                        if (pos >= 0) {
                            spType.setSelection(pos);
                        } else {
                            int otherPos = findOtherPosition(spType);
                            if (otherPos >= 0) spType.setSelection(otherPos);
                            etCustomType.setVisibility(View.VISIBLE);
                            etCustomType.setText(type);
                        }
                    }

                    if (budgetEnabled && budget != null) {
                        etBudget.setText(String.valueOf(budget));
                    } else {
                        etBudget.setText("");
                    }

                    if (!TextUtils.isEmpty(time)) {
                        etDestTime.setText(time);

                        int[] hm = parse12HourStringToHourMinute(time);
                        if (hm != null) {
                            pickedHour24 = hm[0];
                            pickedMinute = hm[1];
                            hasTime = true;
                        }
                    }

                    computePrevNextBounds(uid);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ---------- Time picker ----------

    private void showTimePicker() {
        if (!rulesLoaded) {
            Toast.makeText(this, "Loading time rules… try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar c = Calendar.getInstance();
        int hour = hasTime ? pickedHour24 : c.get(Calendar.HOUR_OF_DAY);
        int minute = hasTime ? pickedMinute : c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minuteOfHour) -> {

            long selectedMillis = toMillisOnTripDate(hourOfDay, minuteOfHour);

            if (minAllowedMillis > 0 && selectedMillis < minAllowedMillis) {
                Toast.makeText(this, "Time must be after previous destination.", Toast.LENGTH_LONG).show();
                return;
            }

            if (maxAllowedMillis > 0 && selectedMillis > maxAllowedMillis) {
                Toast.makeText(this, "Time must be before next destination.", Toast.LENGTH_LONG).show();
                return;
            }

            pickedHour24 = hourOfDay;
            pickedMinute = minuteOfHour;
            hasTime = true;

            etDestTime.setText(formatTo12Hour(hourOfDay, minuteOfHour));
            etDestTime.setError(null);

        }, hour, minute, false).show();
    }

    // prev and next bounds based on millis
    private void computePrevNextBounds(String uid) {
        rulesLoaded = false;
        minAllowedMillis = baseDateMillis;
        maxAllowedMillis = -1L;

        long now = System.currentTimeMillis();
        if (isSameDay(now, baseDateMillis) && minAllowedMillis < now) {
            minAllowedMillis = now;
        }

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destinationId)
                .get()
                .addOnSuccessListener(currDoc -> {
                    Long currentStart = currDoc.getLong("start_time_millis");
                    if (currentStart == null || currentStart <= 0) currentStart = Long.MAX_VALUE;
                    final long currentStartFinal = currentStart;

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .collection("destinations")
                            .orderBy("end_time_millis", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(qsPrev -> {
                                long bestPrevEnd = -1L;

                                for (DocumentSnapshot d : qsPrev.getDocuments()) {
                                    if (d.getId().equals(destinationId)) continue;
                                    Long end = d.getLong("end_time_millis");
                                    if (end == null || end <= 0) continue;

                                    if (end < currentStartFinal) {
                                        bestPrevEnd = end;
                                        break;
                                    }
                                }

                                if (bestPrevEnd > 0) {
                                    long candidateMin = bestPrevEnd + 60_000L;
                                    if (candidateMin > minAllowedMillis) minAllowedMillis = candidateMin;
                                }

                                db.collection("users")
                                        .document(uid)
                                        .collection("trips")
                                        .document(tripId)
                                        .collection("destinations")
                                        .orderBy("start_time_millis", Query.Direction.ASCENDING)
                                        .get()
                                        .addOnSuccessListener(qsNext -> {
                                            long bestNextStart = -1L;

                                            for (DocumentSnapshot d : qsNext.getDocuments()) {
                                                if (d.getId().equals(destinationId)) continue;
                                                Long start = d.getLong("start_time_millis");
                                                if (start == null || start <= 0) continue;

                                                if (start > currentStartFinal) {
                                                    bestNextStart = start;
                                                    break;
                                                }
                                            }

                                            if (bestNextStart > 0) {
                                                maxAllowedMillis = bestNextStart - 60_000L;
                                            }

                                            rulesLoaded = true;
                                        })
                                        .addOnFailureListener(e -> rulesLoaded = true);
                            })
                            .addOnFailureListener(e -> rulesLoaded = true);
                })
                .addOnFailureListener(e -> rulesLoaded = true);
    }

    // ---------- Save ----------

    private void saveEdits() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        isSaving = true;
        btnSave.setEnabled(false);
        btnCancel.setEnabled(false);

        String name = etName.getText().toString().trim();
        String selectedType = (spType.getSelectedItem() != null) ? spType.getSelectedItem().toString().trim() : "";
        String finalType = selectedType;

        if (isOther(selectedType)) {
            finalType = etCustomType.getText().toString().trim();
        }

        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        long startMillis = toMillisOnTripDate(pickedHour24, pickedMinute);
        long endMillis = startMillis + (DEFAULT_DURATION_MINUTES * 60_000L);

        Double budget = null;
        if (budgetEnabled) {
            String budgetStr = etBudget.getText().toString().trim();
            if (!TextUtils.isEmpty(budgetStr)) budget = Double.parseDouble(budgetStr);
        }

        String timeStr = formatTo12Hour(pickedHour24, pickedMinute);

        Map<String, Object> updates = new HashMap<>();
        updates.put("destination_name", name);
        updates.put("type", finalType);
        updates.put("location", location);
        updates.put("description", description);

        updates.put("time", timeStr);
        updates.put("start_time_millis", startMillis);
        updates.put("end_time_millis", endMillis);

        if (pickedLat != null && pickedLng != null) {
            updates.put("lat", pickedLat);
            updates.put("lng", pickedLng);
        }

        if (budgetEnabled) updates.put("budget", budget);
        else updates.put("budget", null);

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
                    isSaving = false;
                    btnSave.setEnabled(true);
                    btnCancel.setEnabled(true);
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ---------- helpers ----------

    private void lockLocationField() {
        etLocation.setFocusable(false);
        etLocation.setFocusableInTouchMode(false);
        etLocation.setClickable(true);
        etLocation.setCursorVisible(false);
        etLocation.setLongClickable(false);
        etLocation.setTextIsSelectable(false);
        etLocation.setInputType(android.text.InputType.TYPE_NULL);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            etLocation.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
    }

    private boolean isOther(String value) {
        if (TextUtils.isEmpty(value)) return false;
        String v = value.trim().toLowerCase();
        return v.equals("others") || v.equals("other");
    }

    private int getSpinnerPosition(Spinner spinner, String value) {
        if (spinner.getAdapter() == null) return -1;
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            String item = String.valueOf(spinner.getAdapter().getItem(i));
            if (value.equalsIgnoreCase(item)) return i;
        }
        return -1;
    }

    private int findOtherPosition(Spinner spinner) {
        if (spinner.getAdapter() == null) return -1;
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            String item = String.valueOf(spinner.getAdapter().getItem(i)).trim().toLowerCase();
            if (item.equals("others") || item.equals("other")) return i;
        }
        return -1;
    }

    private String formatTo12Hour(int hourOfDay, int minute) {
        String ampm = (hourOfDay >= 12) ? "PM" : "AM";
        int hour12 = hourOfDay % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.getDefault(), "%02d:%02d %s", hour12, minute, ampm);
    }

    private int[] parse12HourStringToHourMinute(String time) {
        try {
            String t = time.trim();
            String[] parts = t.split(" ");
            if (parts.length < 2) return null;

            String hm = parts[0];
            String ampm = parts[1].toUpperCase(Locale.getDefault());

            String[] hmParts = hm.split(":");
            if (hmParts.length < 2) return null;

            int hour12 = Integer.parseInt(hmParts[0]);
            int minute = Integer.parseInt(hmParts[1]);

            int hour24 = hour12 % 12;
            if ("PM".equals(ampm)) hour24 += 12;

            return new int[]{hour24, minute};
        } catch (Exception e) {
            return null;
        }
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