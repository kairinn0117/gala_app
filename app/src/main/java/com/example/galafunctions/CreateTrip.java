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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CreateTrip extends AppCompatActivity {

    private ImageView imgCover;
    private Button btnChangeCover, btnCreate, btnCancel;

    private EditText etTripName, etLocation, etBudget, etDescription;
    private Spinner spCategory;
    private Switch swBudget;
    private LinearLayout layoutBudget;

    private Uri selectedImageUri;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<Intent> mapPickerLauncher;
    private Button btnTripSearchMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_trip);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Bind views
        imgCover = findViewById(R.id.imgCover);
        btnChangeCover = findViewById(R.id.btnChangeCover);
        btnCreate = findViewById(R.id.btnCreateTrip);
        btnCancel = findViewById(R.id.btnCancelTrip);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etBudget = findViewById(R.id.etTripBudget);
        etDescription = findViewById(R.id.etDescription);

        spCategory = findViewById(R.id.spTripCategory);
        swBudget = findViewById(R.id.swBudgetEnabled);
        layoutBudget = findViewById(R.id.layoutBudgetSection);

        btnTripSearchMap = findViewById(R.id.btnTripSearchMap);

        // ✅ Image picker (Glide preview = no ANR)
        imagePicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    selectedImageUri = uri;

                    if (uri != null) {
                        Glide.with(CreateTrip.this)
                                .load(uri)
                                .centerCrop()
                                .into(imgCover);
                    } else {
                        imgCover.setImageResource(R.drawable.ic_launcher_background);
                    }
                }
        );

        imgCover.setOnClickListener(v -> imagePicker.launch("image/*"));
        btnChangeCover.setOnClickListener(v -> imagePicker.launch("image/*"));

        // Budget toggle
        swBudget.setOnCheckedChangeListener((b, checked) ->
                layoutBudget.setVisibility(checked ? View.VISIBLE : View.GONE)
        );

        // ✅ Map picker
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
            Intent intent = new Intent(CreateTrip.this, MapPickerActivity.class);
            mapPickerLauncher.launch(intent);
        });

        btnCancel.setOnClickListener(v -> finish());
        btnCreate.setOnClickListener(v -> createTrip());
    }

    private void createTrip() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        String name = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String category = (spCategory.getSelectedItem() != null) ? spCategory.getSelectedItem().toString() : "";
        boolean budgetEnabled = swBudget.isChecked();

        if (TextUtils.isEmpty(name)) { etTripName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }

        Double budget = null;
        if (budgetEnabled) {
            String budgetStr = etBudget.getText().toString().trim();
            if (TextUtils.isEmpty(budgetStr)) { etBudget.setError("Required"); return; }
            try {
                budget = Double.parseDouble(budgetStr);
            } catch (Exception e) {
                etBudget.setError("Invalid number");
                return;
            }
        }

        btnCreate.setEnabled(false);

        String tripId = db.collection("tmp").document().getId();

        Map<String, Object> trip = new HashMap<>();
        trip.put("trip_name", name);
        trip.put("trip_category", category);
        trip.put("location", location);

        trip.put("budget_enabled", budgetEnabled);
        trip.put("trip_budget", budget);
        trip.put("description", etDescription.getText().toString());

        trip.put("status", "PLANNED");
        trip.put("is_archived", false);
        trip.put("created_at", Timestamp.now());
        trip.put("scheduled_date", "");
        trip.put("scheduled_time", "");

        if (selectedImageUri == null) {
            saveTrip(uid, tripId, trip);
            return;
        }

        StorageReference ref = storage.getReference()
                .child("users").child(uid).child("trips").child(tripId)
                .child("cover_" + UUID.randomUUID());

        ref.putFile(selectedImageUri)
                .continueWithTask(t -> ref.getDownloadUrl())
                .addOnSuccessListener(uri -> {
                    trip.put("cover_url", uri.toString());
                    saveTrip(uid, tripId, trip);
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveTrip(String uid, String tripId, Map<String, Object> trip) {
        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .set(trip)
                .addOnSuccessListener(v -> {
                    Intent i = new Intent(this, TripActivity.class);
                    i.putExtra("tripId", tripId);
                    startActivity(i);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnCreate.setEnabled(true);
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}