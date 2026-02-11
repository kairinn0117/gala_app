package com.example.galafunctions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EditTripActivity extends AppCompatActivity {

    private ImageView imgCover;
    private Button btnChangeCover, btnTripSearchMap;

    private Spinner spTripCategory;
    private Switch swBudgetEnabled;
    private LinearLayout layoutBudgetSection;

    private EditText etTripName, etLocation, etTripBudget, etDescription;
    private Button btnSaveTrip, btnCancelTrip;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private String tripId;
    private Uri selectedImageUri = null;
    private String existingCoverUrl = "";

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<Intent> mapPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_trip);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind views (same as CreateTrip)
        imgCover = findViewById(R.id.imgCover);
        btnChangeCover = findViewById(R.id.btnChangeCover);

        spTripCategory = findViewById(R.id.spTripCategory);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etTripBudget = findViewById(R.id.etTripBudget);
        etDescription = findViewById(R.id.etDescription);

        swBudgetEnabled = findViewById(R.id.swBudgetEnabled);
        layoutBudgetSection = findViewById(R.id.layoutBudgetSection);

        btnTripSearchMap = findViewById(R.id.btnTripSearchMap);

        btnSaveTrip = findViewById(R.id.btnSaveTrip);
        btnCancelTrip = findViewById(R.id.btnCancelTrip);

        // Image picker (Glide preview)
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    selectedImageUri = uri;
                    if (uri != null) {
                        Glide.with(EditTripActivity.this)
                                .load(uri)
                                .centerCrop()
                                .into(imgCover);
                    }
                }
        );

        imgCover.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
        btnChangeCover.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Budget toggle
        swBudgetEnabled.setOnCheckedChangeListener((b, checked) -> {
            layoutBudgetSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (!checked) {
                etTripBudget.setText("");
                etTripBudget.setError(null);
            }
        });

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

        btnTripSearchMap.setOnClickListener(v -> {
            Intent intent = new Intent(EditTripActivity.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        btnCancelTrip.setOnClickListener(v -> finish());
        btnSaveTrip.setOnClickListener(v -> saveTripEdits());

        loadTrip();
    }

    private void loadTrip() {
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

                    String tripName = doc.getString("trip_name");
                    String category = doc.getString("trip_category");
                    String location = doc.getString("location");
                    String description = doc.getString("description");

                    Boolean budgetEnabled = doc.getBoolean("budget_enabled");
                    Double tripBudget = doc.getDouble("trip_budget");

                    String coverUrl = doc.getString("cover_url");
                    existingCoverUrl = (coverUrl != null) ? coverUrl : "";

                    etTripName.setText(tripName != null ? tripName : "");
                    etLocation.setText(location != null ? location : "");
                    etDescription.setText(description != null ? description : "");

                    // Spinner selection (using entries="@array/trip_categories")
                    if (!TextUtils.isEmpty(category) && spTripCategory.getAdapter() != null) {
                        ArrayAdapter adapter = (ArrayAdapter) spTripCategory.getAdapter();
                        int pos = adapter.getPosition(category);
                        if (pos >= 0) spTripCategory.setSelection(pos);
                    }

                    boolean budgetChecked = (budgetEnabled != null && budgetEnabled);
                    swBudgetEnabled.setChecked(budgetChecked);
                    layoutBudgetSection.setVisibility(budgetChecked ? View.VISIBLE : View.GONE);

                    if (budgetChecked && tripBudget != null) {
                        etTripBudget.setText(String.valueOf(tripBudget));
                    } else {
                        etTripBudget.setText("");
                    }

                    if (!TextUtils.isEmpty(existingCoverUrl)) {
                        Glide.with(EditTripActivity.this)
                                .load(existingCoverUrl)
                                .centerCrop()
                                .into(imgCover);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void saveTripEdits() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        String tripName = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        String category = (spTripCategory.getSelectedItem() != null)
                ? spTripCategory.getSelectedItem().toString().trim()
                : "OTHER";

        boolean budgetEnabled = swBudgetEnabled.isChecked();

        // Validation (same as CreateTrip)
        if (TextUtils.isEmpty(tripName)) { etTripName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }

        Double tripBudget = null;
        if (budgetEnabled) {
            String budgetStr = etTripBudget.getText().toString().trim();
            if (TextUtils.isEmpty(budgetStr)) { etTripBudget.setError("Required"); return; }
            try {
                tripBudget = Double.parseDouble(budgetStr);
                if (tripBudget < 0) { etTripBudget.setError("Must be 0 or more"); return; }
            } catch (Exception e) {
                etTripBudget.setError("Invalid number");
                return;
            }
        }

        btnSaveTrip.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("trip_name", tripName);
        updates.put("trip_category", category);
        updates.put("location", location);
        updates.put("budget_enabled", budgetEnabled);
        updates.put("trip_budget", tripBudget);
        updates.put("description", description);

        // ✅ Clean old fields (optional but recommended para same schema na)
        updates.put("people_count", null);
        updates.put("date", "");
        updates.put("time", "");
        updates.put("is_template", null);

        // No new cover -> update only
        if (selectedImageUri == null) {
            db.collection("users")
                    .document(uid)
                    .collection("trips")
                    .document(tripId)
                    .update(updates)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Trip updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        btnSaveTrip.setEnabled(true);
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
            return;
        }

        // New cover selected -> upload then update
        String fileName = "cover_" + UUID.randomUUID();
        StorageReference ref = storage.getReference()
                .child("users")
                .child(uid)
                .child("trips")
                .child(tripId)
                .child(fileName);

        ref.putFile(selectedImageUri)
                .continueWithTask(task -> ref.getDownloadUrl())
                .addOnSuccessListener(downloadUri -> {
                    updates.put("cover_url", downloadUri.toString());

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .update(updates)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Trip updated!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                btnSaveTrip.setEnabled(true);
                                Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnSaveTrip.setEnabled(true);
                    Toast.makeText(this, "Cover upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}