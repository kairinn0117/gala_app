package com.example.galafunctions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateTrip extends AppCompatActivity {

    // Existing
    private ImageView imgCoverPick;                 // R.id.imgCover
    private Button btnChangeCover;                  // R.id.btnChangeCover
    private EditText etTripName, etLocation, etPeopleCount, etTripDate, etTripTime, etTripDescription;
    private Button btnCreateTrip, btnCancelTrip;

    // Updated (removed spCity)
    private Spinner spTripCategory;
    private Switch swIsTemplate, swBudgetEnabled;
    private LinearLayout layoutBudgetSection;
    private EditText etTripBudget;

    private Uri selectedImageUri = null;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private ActivityResultLauncher<String> pickImageLauncher;

    private ActivityResultLauncher<Intent> mapPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_trip);

        // Keep this only if your root has android:id="@+id/main"
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
        storage = FirebaseStorage.getInstance();

        // Existing views
        imgCoverPick = findViewById(R.id.imgCover);
        btnChangeCover = findViewById(R.id.btnChangeCover);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etPeopleCount = findViewById(R.id.etPeopleCount);

        etTripDate = findViewById(R.id.etDate);
        etTripTime = findViewById(R.id.etTime);
        etTripDescription = findViewById(R.id.etDescription);

        btnCreateTrip = findViewById(R.id.btnCreateTrip);
        btnCancelTrip = findViewById(R.id.btnCancelTrip);

        // Updated views
        spTripCategory = findViewById(R.id.spTripCategory);
        swIsTemplate = findViewById(R.id.swIsTemplate);
        swBudgetEnabled = findViewById(R.id.swBudgetEnabled);
        layoutBudgetSection = findViewById(R.id.layoutBudgetSection);
        etTripBudget = findViewById(R.id.etTripBudget);

        // Image picker launcher
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgCoverPick.setImageURI(uri); // preview
                    }
                }
        );

        // Pick image (either click cover or click button)
        imgCoverPick.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnChangeCover.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Template switch behavior (date/time optional when template)
        swIsTemplate.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Optional UI behavior: disable/enable date/time fields
            etTripDate.setEnabled(!isChecked);
            etTripTime.setEnabled(!isChecked);

            if (isChecked) {
                etTripDate.setError(null);
                etTripTime.setError(null);
            }
        });

        // Budget switch behavior (show/hide budget section)
        swBudgetEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutBudgetSection.setVisibility(View.VISIBLE);
            } else {
                layoutBudgetSection.setVisibility(View.GONE);
                etTripBudget.setText("");
                etTripBudget.setError(null);
            }
        });

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

                        // 1️⃣ Set location text
                        if (address != null) {
                            etLocation.setText(address);
                        }

                        // 2️⃣ OPTIONAL (for later Firestore use)
                        // store these in variables if you want
                        // selectedLat = lat;
                        // selectedLng = lng;
                    }
                }
        );

        Button btnTripSearchMap = findViewById(R.id.btnTripSearchMap);

        btnTripSearchMap.setOnClickListener(v -> {
            Intent intent = new Intent(CreateTrip.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });



        // Cancel
        btnCancelTrip.setOnClickListener(v -> finish());

        // Create
        btnCreateTrip.setOnClickListener(v -> createTrip());
    }

    private void createTrip() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        // Spinner value
        String tripCategory = (spTripCategory.getSelectedItem() != null)
                ? spTripCategory.getSelectedItem().toString().trim()
                : "OTHER";

        boolean isTemplate = swIsTemplate.isChecked();
        boolean budgetEnabled = swBudgetEnabled.isChecked();

        // Text fields
        String tripName = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim(); // REQUIRED now
        String peopleStr = etPeopleCount.getText().toString().trim();
        String date = etTripDate.getText().toString().trim();
        String time = etTripTime.getText().toString().trim();
        String description = etTripDescription.getText().toString().trim();

        String budgetStr = etTripBudget.getText().toString().trim();

        // ---------- Validation ----------
        if (TextUtils.isEmpty(tripName)) {
            etTripName.setError("Required");
            return;
        }

        if (TextUtils.isEmpty(location)) {
            etLocation.setError("Required");
            return;
        }

        if (TextUtils.isEmpty(peopleStr)) {
            etPeopleCount.setError("Required");
            return;
        }

        int peopleCount;
        try {
            peopleCount = Integer.parseInt(peopleStr);
            if (peopleCount <= 0) {
                etPeopleCount.setError("Must be at least 1");
                return;
            }
        } catch (Exception e) {
            etPeopleCount.setError("Number only");
            return;
        }

        // Date/time required only if NOT template
        if (!isTemplate) {
            if (TextUtils.isEmpty(date)) {
                etTripDate.setError("Required");
                return;
            }
            if (TextUtils.isEmpty(time)) {
                etTripTime.setError("Required");
                return;
            }
        }

        Double tripBudget = null;
        if (budgetEnabled) {
            // If enabled, require a valid number
            if (TextUtils.isEmpty(budgetStr)) {
                etTripBudget.setError("Required");
                return;
            }
            try {
                tripBudget = Double.parseDouble(budgetStr);
                if (tripBudget < 0) {
                    etTripBudget.setError("Must be 0 or more");
                    return;
                }
            } catch (Exception e) {
                etTripBudget.setError("Number only");
                return;
            }
        }

        btnCreateTrip.setEnabled(false);

        // Create tripId now
        String tripId = db.collection("tmp").document().getId();

        // If no image picked, just save Firestore without cover_url
        if (selectedImageUri == null) {
            saveTripToFirestore(uid, tripId, tripName, tripCategory, location,
                    peopleCount, isTemplate, budgetEnabled, tripBudget, date, time, description, null);
            return;
        }

        // ---- Make final copies for lambda ----
        final String fUid = uid;
        final String fTripId = tripId;
        final String fTripName = tripName;
        final String fTripCategory = tripCategory;
        final String fLocation = location;
        final int fPeopleCount = peopleCount;
        final boolean fIsTemplate = isTemplate;
        final boolean fBudgetEnabled = budgetEnabled;
        final Double fTripBudget = tripBudget;
        final String fDate = date;
        final String fTime = time;
        final String fDescription = description;

        // Upload image to Storage
        String fileName = "cover_" + UUID.randomUUID();
        StorageReference ref = storage.getReference()
                .child("users")
                .child(fUid)
                .child("trips")
                .child(fTripId)
                .child(fileName);

        ref.putFile(selectedImageUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    String coverUrl = downloadUri.toString();
                    saveTripToFirestore(fUid, fTripId, fTripName, fTripCategory, fLocation,
                            fPeopleCount, fIsTemplate, fBudgetEnabled, fTripBudget,
                            fDate, fTime, fDescription, coverUrl);
                })
                .addOnFailureListener(e -> {
                    btnCreateTrip.setEnabled(true);
                    Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTripToFirestore(
            String uid,
            String tripId,
            String tripName,
            String tripCategory,
            String location,
            int peopleCount,
            boolean isTemplate,
            boolean budgetEnabled,
            Double tripBudget,
            String date,
            String time,
            String description,
            String coverUrl
    ) {
        Map<String, Object> trip = new HashMap<>();
        trip.put("trip_name", tripName);
        trip.put("trip_category", tripCategory);

        // Location is REQUIRED now (text + optional maps later)
        trip.put("location", location);

        trip.put("people_count", peopleCount);

        trip.put("is_template", isTemplate);
        trip.put("budget_enabled", budgetEnabled);

        if (budgetEnabled && tripBudget != null) {
            trip.put("trip_budget", tripBudget);
            // Optional tracking fields for later:
            trip.put("total_spent", 0.0);
        }

        // If template, date/time can be empty; still store keys for consistency
        trip.put("date", date);
        trip.put("time", time);

        trip.put("description", description);

        // Status stays PLANNED for both; StartGala later will set IN_PROGRESS
        trip.put("status", "PLANNED");

        trip.put("created_at", Timestamp.now());

        if (coverUrl != null) trip.put("cover_url", coverUrl);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .set(trip)
                .addOnSuccessListener(unused -> {
                    Intent intent = new Intent(CreateTrip.this, TripActivity.class);
                    intent.putExtra("tripId", tripId);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnCreateTrip.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}