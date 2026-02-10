package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StartGala extends AppCompatActivity {

    private TextView tvTripTitle, tvProgress;

    private TextView tvDestName, tvDestType, tvDestLocation, tvDestDateTime,
            tvDestBudget, tvDestDescription;

    private Button btnEndTrip, btnDoneNext;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;

    private final ArrayList<DocumentSnapshot> destinations = new ArrayList<>();
    private int currentIndex = 0;

    private Button btnBack;

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

        btnEndTrip.setOnClickListener(v -> endTrip());
        btnDoneNext.setOnClickListener(v -> markDoneAndNext());

        // Start gala: status IN_PROGRESS + active_at
        startGalaNowIfNeeded();

        // Load header + destinations
        loadTripTitle();
        loadDestinations();

        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> {
            // balik sa Home (MainActivity)
            Intent i = new Intent(StartGala.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
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
                    updates.put("active_at", com.google.firebase.Timestamp.now());

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

        // NOTE: if you don't have created_at, remove orderBy
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

                    // start at first PENDING destination if exists
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
        return 0; // fallback
    }

    private void showEmptyDestination() {
        tvProgress.setText("No destinations yet");
        tvDestName.setText("No destination");
        tvDestType.setText("");
        tvDestLocation.setText("");
        tvDestDateTime.setText("");
        tvDestBudget.setText("");
        tvDestDescription.setText("Add destinations first.");
        btnDoneNext.setEnabled(false);
    }

    private void showCurrentDestination() {
        if (destinations.isEmpty()) {
            showEmptyDestination();
            return;
        }

        if (currentIndex < 0) currentIndex = 0;
        if (currentIndex >= destinations.size()) {
            // finished all
            tvProgress.setText("All destinations completed ✅");
            tvDestName.setText("Done!");
            tvDestType.setText("");
            tvDestLocation.setText("");
            tvDestDateTime.setText("");
            tvDestBudget.setText("");
            tvDestDescription.setText("You finished your gala.");
            btnDoneNext.setEnabled(false);
            return;
        }

        DocumentSnapshot d = destinations.get(currentIndex);

        String name = d.getString("destination_name");
        String type = d.getString("type");
        String location = d.getString("location");
        String desc = d.getString("description");

        Double budget = d.getDouble("budget");
        String timeIn = d.getString("time_in");
        String timeOut = d.getString("time_out");

        tvProgress.setText("Destination " + (currentIndex + 1) + " of " + destinations.size());

        tvDestName.setText(name != null ? name : "Destination");
        tvDestType.setText(!TextUtils.isEmpty(type) ? "Type: " + type : "Type: —");
        tvDestLocation.setText(!TextUtils.isEmpty(location) ? "Location: " + location : "Location: —");

        // time display
        String timeText = "";
        if (!TextUtils.isEmpty(timeIn) && !TextUtils.isEmpty(timeOut)) {
            timeText = timeIn + " - " + timeOut;
        } else if (!TextUtils.isEmpty(timeIn)) {
            timeText = timeIn;
        } else if (!TextUtils.isEmpty(timeOut)) {
            timeText = timeOut;
        }
        tvDestDateTime.setText(!TextUtils.isEmpty(timeText) ? "Time: " + timeText : "Time: —");

        if (budget != null) {
            tvDestBudget.setText("Budget: ₱" + String.format("%.2f", budget));
        } else {
            tvDestBudget.setText("Budget: —");
        }

        tvDestDescription.setText(!TextUtils.isEmpty(desc) ? desc : "(No description)");

        btnDoneNext.setEnabled(true);
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
                    // update local snapshot list (best-effort)
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
        updates.put("status", "COMPLETED");
        updates.put("active_at", null);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(unused -> {
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