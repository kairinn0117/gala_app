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

import java.util.HashMap;
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

    private ActivityResultLauncher<Intent> mapPickerLauncher;

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
                        if (!TextUtils.isEmpty(address)) {
                            etLocation.setText(address);
                        }
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

                    // 2) now load destination details
                    loadDestination();
                })
                .addOnFailureListener(e -> {
                    // safe default
                    budgetEnabled = false;
                    layoutDestinationBudget.setVisibility(View.GONE);
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
                    String time = doc.getString("time"); // ✅ new field

                    etName.setText(name != null ? name : "");
                    etLocation.setText(location != null ? location : "");
                    etDescription.setText(description != null ? description : "");

                    // Spinner type selection (entries already in XML)
                    if (!TextUtils.isEmpty(type) && spType.getAdapter() != null) {
                        int pos = getSpinnerPosition(spType, type);
                        if (pos >= 0) spType.setSelection(pos);
                    }

                    // Budget (only if enabled)
                    if (budgetEnabled && budget != null) {
                        etBudget.setText(String.valueOf(budget));
                    }

                    // Time -> set spinners
                    if (!TextUtils.isEmpty(time)) {
                        setTimeSpinnersFromString(time);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
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

        // Required time
        String hour = (spHour.getSelectedItem() != null) ? spHour.getSelectedItem().toString() : "";
        String minute = (spMinute.getSelectedItem() != null) ? spMinute.getSelectedItem().toString() : "";
        String ampm = (spAmPm.getSelectedItem() != null) ? spAmPm.getSelectedItem().toString() : "";

        String time = hour + ":" + minute + " " + ampm;

        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }
        if (TextUtils.isEmpty(hour) || TextUtils.isEmpty(minute) || TextUtils.isEmpty(ampm)) {
            Toast.makeText(this, "Please select a valid time.", Toast.LENGTH_SHORT).show();
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
                // if enabled but left empty, you can decide:
                // - require it (setError)
                // - or allow null
                // I’ll allow null here.
                budget = null;
            }
        }

        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("destination_name", name);
        updates.put("type", type);
        updates.put("location", location);
        updates.put("description", description);
        updates.put("time", time); // ✅ save as one field

        if (budgetEnabled) {
            updates.put("budget", budget); // can be null
        } else {
            // if trip doesn't use budget, remove budget value
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

    // Expects format like "9:05 PM" or "12:30 AM"
    private void setTimeSpinnersFromString(String time) {
        try {
            String t = time.trim(); // "9:05 PM"
            String[] parts = t.split(" ");
            if (parts.length < 2) return;

            String hm = parts[0]; // "9:05"
            String ampm = parts[1].toUpperCase(); // "PM"

            String[] hmParts = hm.split(":");
            if (hmParts.length < 2) return;

            String hour = hmParts[0]; // "9"
            String minute = hmParts[1]; // "05"

            int hourPos = getSpinnerPosition(spHour, hour);
            int minPos = getSpinnerPosition(spMinute, minute);
            int ampmPos = getSpinnerPosition(spAmPm, ampm);

            if (hourPos >= 0) spHour.setSelection(hourPos);
            if (minPos >= 0) spMinute.setSelection(minPos);
            if (ampmPos >= 0) spAmPm.setSelection(ampmPos);

        } catch (Exception ignored) {}
    }
}