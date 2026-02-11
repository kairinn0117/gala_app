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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StartGala extends AppCompatActivity {

    private TextView tvTripTitle, tvProgress;
    private TextView tvDestName, tvDestType, tvDestLocation, tvDestDateTime,
            tvDestBudget, tvDestDescription;

    private Button btnEndTrip, btnDoneNext, btnBack, btnDirections;

    // ✅ Extras wrapper
    private LinearLayout layoutExtras;

    // ✅ Photo UI
    private ImageView imgDestPhoto;
    private Button btnAddPhoto;
    private Uri selectedPhotoUri = null;
    private FirebaseStorage storage;

    // ✅ Budget UI
    private TextView tvSpentSummary;
    private EditText etSpent;
    private Button btnAddSpent;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;

    private final ArrayList<DocumentSnapshot> destinations = new ArrayList<>();
    private int currentIndex = 0;

    // directions fields
    private Double currentLat = null;
    private Double currentLng = null;
    private String currentAddressForMaps = null;

    private ActivityResultLauncher<String> photoPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_start_gala);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind
        tvTripTitle = findViewById(R.id.tvTripTitle);
        tvProgress = findViewById(R.id.tvProgress);

        tvDestName = findViewById(R.id.tvDestName);
        tvDestType = findViewById(R.id.tvDestType);
        tvDestLocation = findViewById(R.id.tvDestLocation);
        tvDestDateTime = findViewById(R.id.tvDestDateTime);
        tvDestBudget = findViewById(R.id.tvDestBudget);
        tvDestDescription = findViewById(R.id.tvDestDescription);

        btnEndTrip = findViewById(R.id.btnEndTrip);
        btnDoneNext = findViewById(R.id.btnDoneNext);
        btnBack = findViewById(R.id.btnBack);
        btnDirections = findViewById(R.id.btnDirections);

        // ✅ extras wrapper
        layoutExtras = findViewById(R.id.layoutExtras);

        // Photo
        imgDestPhoto = findViewById(R.id.imgDestPhoto);
        btnAddPhoto = findViewById(R.id.btnAddPhoto);

        // Budget spent
        tvSpentSummary = findViewById(R.id.tvSpentSummary);
        etSpent = findViewById(R.id.etSpent);
        btnAddSpent = findViewById(R.id.btnAddSpent);

        btnEndTrip.setOnClickListener(v -> endTrip());
        btnDoneNext.setOnClickListener(v -> markDoneAndNext());

        btnBack.setOnClickListener(v -> {
            Intent i = new Intent(StartGala.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });

        btnDirections.setOnClickListener(v -> openDirections());

        // Photo picker
        photoPicker = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri == null) return;
                    selectedPhotoUri = uri;
                    uploadDestinationPhoto(uri);
                }
        );

        btnAddPhoto.setOnClickListener(v -> photoPicker.launch("image/*"));
        btnAddSpent.setOnClickListener(v -> addSpent());

        startGalaNowIfNeeded();
        loadTripTitle();
        loadDestinations();
    }

    private void startGalaNowIfNeeded() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(doc -> {
                    String status = doc.getString("status");
                    if ("IN_PROGRESS".equalsIgnoreCase(status)) return;

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("status", "IN_PROGRESS");
                    updates.put("active_at", Timestamp.now());

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .update(updates);
                });
    }

    private void loadTripTitle() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .get()
                .addOnSuccessListener(doc -> {
                    String name = doc.getString("trip_name");
                    tvTripTitle.setText(name != null ? name : "Trip");
                });
    }

    private void loadDestinations() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .orderBy("created_at", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    destinations.clear();
                    destinations.addAll(qs.getDocuments());

                    if (destinations.isEmpty()) {
                        showEmptyDestination();
                        return;
                    }

                    currentIndex = findFirstPendingIndex();
                    showCurrentDestination();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load destinations: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private int findFirstPendingIndex() {
        for (int i = 0; i < destinations.size(); i++) {
            String status = destinations.get(i).getString("status");
            if (TextUtils.isEmpty(status) || "PENDING".equalsIgnoreCase(status)) return i;
        }
        return 0;
    }

    private void showEmptyDestination() {
        tvProgress.setText("No destinations yet");
        tvDestName.setText("No destination");
        tvDestType.setText("");
        tvDestLocation.setText("");
        tvDestDateTime.setText("");
        tvDestBudget.setText("");
        tvDestDescription.setText("Add destinations first.");

        if (layoutExtras != null) layoutExtras.setVisibility(View.GONE);

        btnDoneNext.setEnabled(false);
        btnBack.setEnabled(true);
        btnDirections.setEnabled(false);
        btnEndTrip.setEnabled(true);
    }

    // ✅ finished UI mode
    private void setFinishedUI() {
        tvProgress.setText("All destinations completed ✅");
        tvDestName.setText("Done!");
        tvDestType.setText("");
        tvDestLocation.setText("");
        tvDestDateTime.setText("");
        tvDestBudget.setText("");
        tvDestDescription.setText("Press End Trip to finish.");

        if (layoutExtras != null) layoutExtras.setVisibility(View.GONE);

        btnDoneNext.setEnabled(false);
        btnBack.setEnabled(false);
        btnDirections.setEnabled(false);

        // only End Trip
        btnEndTrip.setEnabled(true);
    }

    private void showCurrentDestination() {
        if (destinations.isEmpty()) {
            showEmptyDestination();
            return;
        }

        if (currentIndex < 0) currentIndex = 0;

        // ✅ finished all
        if (currentIndex >= destinations.size()) {
            setFinishedUI();
            return;
        }

        // ✅ normal mode
        btnBack.setEnabled(true);
        btnDirections.setEnabled(true);
        btnDoneNext.setEnabled(true);
        btnEndTrip.setEnabled(true);

        if (layoutExtras != null) layoutExtras.setVisibility(View.VISIBLE);

        DocumentSnapshot d = destinations.get(currentIndex);

        String name = d.getString("destination_name");
        String type = d.getString("type");
        String location = d.getString("location");
        String desc = d.getString("description");

        Double budget = d.getDouble("budget");
        String time = d.getString("time");

        currentLat = d.getDouble("lat");
        currentLng = d.getDouble("lng");
        currentAddressForMaps = location;

        Double spent = d.getDouble("spent_total");
        if (spent == null) spent = 0.0;

        String photoUrl = d.getString("photo_url");

        tvProgress.setText("Destination " + (currentIndex + 1) + " of " + destinations.size());
        tvDestName.setText(name != null ? name : "Destination");
        tvDestType.setText(!TextUtils.isEmpty(type) ? "Type: " + type : "Type: —");
        tvDestLocation.setText(!TextUtils.isEmpty(location) ? "Location: " + location : "Location: —");
        tvDestDateTime.setText(!TextUtils.isEmpty(time) ? "Time: " + time : "Time: —");

        if (budget != null) tvDestBudget.setText("Budget: ₱" + String.format("%.2f", budget));
        else tvDestBudget.setText("Budget: —");

        tvDestDescription.setText(!TextUtils.isEmpty(desc) ? desc : "(No description)");

        tvSpentSummary.setText("Spent: ₱" + String.format("%.2f", spent));
        etSpent.setText("");

        if (!TextUtils.isEmpty(photoUrl)) {
            imgDestPhoto.setVisibility(View.VISIBLE);
            Glide.with(imgDestPhoto.getContext()).load(photoUrl).centerCrop().into(imgDestPhoto);
        } else {
            imgDestPhoto.setVisibility(View.GONE);
        }
    }

    private void openDirections() {
        if (destinations.isEmpty() || currentIndex >= destinations.size()) {
            Toast.makeText(this, "No destination selected.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLat != null && currentLng != null) {
            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + currentLat + "," + currentLng);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) startActivity(mapIntent);
            else startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + currentLat + "," + currentLng)));
            return;
        }

        String address = currentAddressForMaps;
        if (TextUtils.isEmpty(address)) {
            Toast.makeText(this, "No location found.", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(address));
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) startActivity(mapIntent);
        else startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(address))));
    }

    private void addSpent() {
        if (auth.getCurrentUser() == null) return;
        if (destinations.isEmpty() || currentIndex >= destinations.size()) return;

        String input = etSpent.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            etSpent.setError("Required");
            return;
        }

        double add;
        try {
            add = Double.parseDouble(input);
            if (add <= 0) { etSpent.setError("Must be > 0"); return; }
        } catch (Exception e) {
            etSpent.setError("Invalid number");
            return;
        }

        DocumentSnapshot d = destinations.get(currentIndex);
        Double budget = d.getDouble("budget");
        Double spent = d.getDouble("spent_total");
        if (spent == null) spent = 0.0;

        double newSpent = spent + add;

        if (budget == null) {
            saveSpentToFirestore(newSpent);
            return;
        }

        if (newSpent > budget) {
            new AlertDialog.Builder(this)
                    .setTitle("Overspend?")
                    .setMessage("This will exceed the budget (₱" + String.format("%.2f", budget) + "). Continue?")
                    .setNegativeButton("No", (dialog, which) -> {})
                    .setPositiveButton("Yes", (dialog, which) -> saveSpentToFirestore(newSpent))
                    .show();
        } else {
            saveSpentToFirestore(newSpent);
        }
    }

    private void saveSpentToFirestore(double newSpent) {
        String uid = auth.getCurrentUser().getUid();
        String destId = destinations.get(currentIndex).getId();

        btnAddSpent.setEnabled(false);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destId)
                .update("spent_total", newSpent)
                .addOnSuccessListener(unused -> {
                    btnAddSpent.setEnabled(true);
                    tvSpentSummary.setText("Spent: ₱" + String.format("%.2f", newSpent));
                    etSpent.setText("");
                })
                .addOnFailureListener(e -> {
                    btnAddSpent.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadDestinationPhoto(Uri uri) {
        if (auth.getCurrentUser() == null) return;
        if (destinations.isEmpty() || currentIndex >= destinations.size()) return;

        String uid = auth.getCurrentUser().getUid();
        String destId = destinations.get(currentIndex).getId();

        btnAddPhoto.setEnabled(false);

        // optional: keep file extension
        String ext = "jpg";
        String mime = getContentResolver().getType(uri);
        if (mime != null) {
            if (mime.contains("png")) ext = "png";
            else if (mime.contains("webp")) ext = "webp";
            else if (mime.contains("jpeg")) ext = "jpg";
        }

        StorageReference ref = storage.getReference()
                .child("users").child(uid)
                .child("trips").child(tripId)
                .child("destinations").child(destId)
                .child("photo_" + UUID.randomUUID() + "." + ext);

        ref.putFile(uri)
                .addOnFailureListener(e -> {
                    btnAddPhoto.setEnabled(true);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                })
                .continueWithTask(task -> {
                    // ✅ IMPORTANT: if upload failed, stop here
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return ref.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    String url = downloadUri.toString();

                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(tripId)
                            .collection("destinations")
                            .document(destId)
                            .update("photo_url", url)
                            .addOnSuccessListener(unused -> {
                                btnAddPhoto.setEnabled(true);

                                imgDestPhoto.setVisibility(ImageView.VISIBLE);
                                Glide.with(imgDestPhoto.getContext())
                                        .load(url)
                                        .centerCrop()
                                        .into(imgDestPhoto);

                                Toast.makeText(this, "Photo saved!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                btnAddPhoto.setEnabled(true);
                                Toast.makeText(this, "Save photo failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    btnAddPhoto.setEnabled(true);
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }


    private void markDoneAndNext() {
        if (auth.getCurrentUser() == null) return;
        if (destinations.isEmpty() || currentIndex >= destinations.size()) return;

        String uid = auth.getCurrentUser().getUid();
        DocumentSnapshot current = destinations.get(currentIndex);
        String destId = current.getId();

        btnDoneNext.setEnabled(false);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destId)
                .update("status", "DONE")
                .addOnSuccessListener(unused -> {
                    currentIndex++;
                    btnDoneNext.setEnabled(true);
                    showCurrentDestination();
                })
                .addOnFailureListener(e -> {
                    btnDoneNext.setEnabled(true);
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void endTrip() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        btnEndTrip.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();

        // ✅ Finished (Gallery)
        updates.put("status", "COMPLETED");
        updates.put("ended_at", Timestamp.now());

        // ✅ NOT archived (kasi archived is separate bucket)
        updates.put("is_archived", false);

        // cleanup
        updates.put("active_at", null);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    // balik home
                    Intent intent = new Intent(StartGala.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnEndTrip.setEnabled(true);
                    Toast.makeText(this, "End failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}