package com.example.galafunctions;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

    // Use the same IDs you already have in XML
    private ImageView imgCoverPick;                 // R.id.imgCover
    private Button btnChangeCover;                  // R.id.btnChangeCover
    private EditText etTripName, etLocation, etPeopleCount, etTripDate, etTripTime, etTripDescription;
    private Button btnCreateTrip, btnCancelTrip;

    private Uri selectedImageUri = null;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_trip);

        // If your layout doesn't have @id/main, either add it in XML root OR remove this listener.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // IMPORTANT: assign to CLASS FIELDS (no local variables)
        imgCoverPick = findViewById(R.id.imgCover);
        btnChangeCover = findViewById(R.id.btnChangeCover);

        etTripName = findViewById(R.id.etTripName);
        etLocation = findViewById(R.id.etLocation);
        etPeopleCount = findViewById(R.id.etPeopleCount);

        // Your XML uses etDate / etTime / etDescription (based on your code)
        etTripDate = findViewById(R.id.etDate);
        etTripTime = findViewById(R.id.etTime);
        etTripDescription = findViewById(R.id.etDescription);

        btnCreateTrip = findViewById(R.id.btnCreateTrip);
        btnCancelTrip = findViewById(R.id.btnCancelTrip);

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

        String tripName = etTripName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String peopleStr = etPeopleCount.getText().toString().trim();
        String date = etTripDate.getText().toString().trim();
        String time = etTripTime.getText().toString().trim();
        String description = etTripDescription.getText().toString().trim();

        // Basic validation
        if (TextUtils.isEmpty(tripName)) { etTripName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }
        if (TextUtils.isEmpty(date)) { etTripDate.setError("Required"); return; }
        if (TextUtils.isEmpty(time)) { etTripTime.setError("Required"); return; }
        if (TextUtils.isEmpty(peopleStr)) { etPeopleCount.setError("Required"); return; }

        int peopleCount;
        try {
            peopleCount = Integer.parseInt(peopleStr);
        } catch (Exception e) {
            etPeopleCount.setError("Number only");
            return;
        }

        btnCreateTrip.setEnabled(false);

        // Create tripId now
        String tripId = db.collection("tmp").document().getId();

        // Make final copies for lambda
        final String fUid = uid;
        final String fTripId = tripId;
        final String fTripName = tripName;
        final String fLocation = location;
        final int fPeopleCount = peopleCount;
        final String fDate = date;
        final String fTime = time;
        final String fDescription = description;

        // If no image picked, just save Firestore without cover_url
        if (selectedImageUri == null) {
            saveTripToFirestore(fUid, fTripId, fTripName, fLocation, fPeopleCount, fDate, fTime, fDescription, null);
            return;
        }

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
                    saveTripToFirestore(fUid, fTripId, fTripName, fLocation, fPeopleCount, fDate, fTime, fDescription, coverUrl);
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
            String location,
            int peopleCount,
            String date,
            String time,
            String description,
            String coverUrl
    ) {
        Map<String, Object> trip = new HashMap<>();
        trip.put("trip_name", tripName);
        trip.put("location", location);
        trip.put("people_count", peopleCount);
        trip.put("date", date);
        trip.put("time", time);
        trip.put("description", description);
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
