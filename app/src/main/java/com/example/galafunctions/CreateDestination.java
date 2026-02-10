package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateDestination extends AppCompatActivity {

    // Basic fields
    private EditText etName, etLocation, etBudget, etDescription;
    private Spinner spType;

    // Time spinners (REQUIRED)
    private Spinner spHour, spMinute, spAmPm;

    private Button btnSave, btnCancel;
    private LinearLayout layoutDestinationBudget;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private boolean budgetEnabled = false;

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

        // TripId must exist
        tripId = getIntent().getStringExtra("tripId");
        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind views
        etName = findViewById(R.id.etDestinationName);
        spType = findViewById(R.id.spDestinationType);

        etLocation = findViewById(R.id.etDestinationLocation);
        Button btnSearchMap = findViewById(R.id.btnSearchMap);

        layoutDestinationBudget = findViewById(R.id.layoutDestinationBudget);
        etBudget = findViewById(R.id.etDestinationBudget);

        // Time spinners
        spHour = findViewById(R.id.spHour);
        spMinute = findViewById(R.id.spMinute);
        spAmPm = findViewById(R.id.spAmPm);

        etDescription = findViewById(R.id.etDestinationDescription);

        btnSave = findViewById(R.id.btnSaveDestination);
        btnCancel = findViewById(R.id.btnCancelDestination);

        // ✅ populate time spinners
        populateTimeSpinners();

        // Map picker result
        mapPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String address = result.getData().getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);
                        if (!TextUtils.isEmpty(address)) {
                            etLocation.setText(address);
                        } else {
                            Toast.makeText(this, "No address selected.", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // ✅ open map picker
        btnSearchMap.setOnClickListener(v -> {
            Intent intent = new Intent(CreateDestination.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        // Cancel
        btnCancel.setOnClickListener(v -> finish());

        // Read trip settings (budget_enabled)
        loadTripSettings();

        // Save
        btnSave.setOnClickListener(v -> saveDestination());
    }

    private void populateTimeSpinners() {
        // Hours: 1-12
        ArrayList<String> hours = new ArrayList<>();
        hours.add("HH");
        for (int h = 1; h <= 12; h++) hours.add(String.valueOf(h));

        // Minutes: 00-59
        ArrayList<String> minutes = new ArrayList<>();
        minutes.add("MM");
        for (int m = 0; m < 60; m++) minutes.add(String.format(Locale.getDefault(), "%02d", m));

        // AM/PM
        ArrayList<String> ampm = new ArrayList<>();
        ampm.add("AM/PM");
        ampm.add("AM");
        ampm.add("PM");

        ArrayAdapter<String> hourAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, hours);
        ArrayAdapter<String> minuteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, minutes);
        ArrayAdapter<String> ampmAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ampm);

        spHour.setAdapter(hourAdapter);
        spMinute.setAdapter(minuteAdapter);
        spAmPm.setAdapter(ampmAdapter);

        // default to placeholders
        spHour.setSelection(0);
        spMinute.setSelection(0);
        spAmPm.setSelection(0);
    }

    private void loadTripSettings() {
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
                .addOnSuccessListener(doc -> {
                    Boolean b = doc.getBoolean("budget_enabled");
                    budgetEnabled = (b != null && b);

                    layoutDestinationBudget.setVisibility(budgetEnabled ? View.VISIBLE : View.GONE);

                    if (!budgetEnabled) {
                        etBudget.setText("");
                        etBudget.setError(null);
                    }
                })
                .addOnFailureListener(e -> {
                    budgetEnabled = false;
                    layoutDestinationBudget.setVisibility(View.GONE);
                });
    }

    private void saveDestination() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        String name = etName.getText().toString().trim();
        String type = (spType.getSelectedItem() != null) ? spType.getSelectedItem().toString().trim() : "";
        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        // Required time from spinners
        String hour = String.valueOf(spHour.getSelectedItem());
        String minute = String.valueOf(spMinute.getSelectedItem());
        String ampm = String.valueOf(spAmPm.getSelectedItem());

        // Validation
        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }

        // ✅ must not be placeholder
        if ("HH".equals(hour) || "MM".equals(minute) || "AM/PM".equals(ampm)) {
            Toast.makeText(this, "Please select a valid time.", Toast.LENGTH_SHORT).show();
            return;
        }

        String time = hour + ":" + minute + " " + ampm;

        Double budget = null;
        if (budgetEnabled) {
            String budgetStr = etBudget.getText().toString().trim();
            if (TextUtils.isEmpty(budgetStr)) {
                etBudget.setError("Required");
                return;
            }
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
        }

        btnSave.setEnabled(false);

        String destinationId = db.collection("tmp").document().getId();

        Map<String, Object> destination = new HashMap<>();
        destination.put("destination_name", name);
        destination.put("type", type);
        destination.put("location", location);
        destination.put("time", time); // ✅ destination start time
        destination.put("description", description);
        destination.put("status", "PENDING");
        destination.put("created_at", Timestamp.now());

        if (budgetEnabled && budget != null) {
            destination.put("budget", budget);
            destination.put("spent", 0.0);
        }

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destinationId)
                .set(destination)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Destination saved!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}