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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CreateDestination extends AppCompatActivity {

    // XML views (UPDATED to match your new XML)
    private EditText etName, etLocation, etBudget, etTimeIn, etTimeOut, etDescription;
    private Spinner spType;
    private Button btnSave, btnCancel;
    private LinearLayout layoutDestinationBudget;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private boolean budgetEnabled = false; // will be read from Trip doc

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

        // Bind views (IMPORTANT: assign to class fields)
        etName = findViewById(R.id.etDestinationName);
        spType = findViewById(R.id.spDestinationType);
        etLocation = findViewById(R.id.etDestinationLocation);

        layoutDestinationBudget = findViewById(R.id.layoutDestinationBudget);
        etBudget = findViewById(R.id.etDestinationBudget);

        etTimeIn = findViewById(R.id.etTimeIn);
        etTimeOut = findViewById(R.id.etTimeOut);

        etDescription = findViewById(R.id.etDestinationDescription);

        btnSave = findViewById(R.id.btnSaveDestination);
        btnCancel = findViewById(R.id.btnCancelDestination);


        mapPickerLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {

                        String address = result.getData()
                                .getStringExtra(MapPickerActivity.EXTRA_RESULT_ADDRESS);

                        double lat = result.getData()
                                .getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LAT, 0);

                        double lng = result.getData()
                                .getDoubleExtra(MapPickerActivity.EXTRA_RESULT_LNG, 0);

                        if (address != null) {
                            etLocation.setText(address); // destination location field
                        }

                        // Optional for later:
                        // destLat = lat;
                        // destLng = lng;
                    }
                }
        );

        Button btnSearchMap = findViewById(R.id.btnSearchMap);

        btnSearchMap.setOnClickListener(v -> {
            Intent intent = new Intent(CreateDestination.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        // Cancel
        btnCancel.setOnClickListener(v -> finish());

        // Load trip settings (budget_enabled) then show/hide budget UI
        loadTripSettings();

        // Save
        btnSave.setOnClickListener(v -> saveDestination());
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
                    if (!doc.exists()) {
                        Toast.makeText(this, "Trip not found.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    Boolean b = doc.getBoolean("budget_enabled");
                    budgetEnabled = (b != null && b);

                    // Show/Hide budget section
                    layoutDestinationBudget.setVisibility(budgetEnabled ? View.VISIBLE : View.GONE);

                    if (!budgetEnabled) {
                        etBudget.setText("");
                        etBudget.setError(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to read trip settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    // Safe default: hide budget if we can't confirm
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

        // Optional time fields
        String timeIn = etTimeIn.getText().toString().trim();
        String timeOut = etTimeOut.getText().toString().trim();

        // Budget (only if budgetEnabled)
        String budgetStr = etBudget.getText().toString().trim();

        // ---------- Validation ----------
        if (TextUtils.isEmpty(name)) {
            etName.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Required");
            return;
        }

        Double budget = null;
        if (budgetEnabled) {
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
        destination.put("description", description);
        destination.put("status", "PENDING");
        destination.put("created_at", Timestamp.now());

        // Save optional time-in/out if user typed something
        if (!TextUtils.isEmpty(timeIn)) destination.put("time_in", timeIn);
        if (!TextUtils.isEmpty(timeOut)) destination.put("time_out", timeOut);

        // Save budget only if enabled
        if (budgetEnabled && budget != null) {
            destination.put("budget", budget);
            // for later expense tracking
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
                    finish(); // back to TripActivity
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}