package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class TripActivity extends AppCompatActivity {

    private ImageView imgCover;

    // Existing
    private TextView tvTripName, tvTripDateTime, tvTripLocation;

    // Updated (removed tvTripCity)
    private TextView tvTripCategory, tvTemplateBadge, tvTripBudget;
    private TextView btnEnableBudget; // NOTE: TextView in XML
    private TextView tvEmptyDestinations;

    private Button btnStartTrip, btnAddDestination, btnBackTrip, btnEditTrip;
    private RecyclerView rvDestinations;

    // ✅ RecyclerView data
    private DestinationAdapter destinationAdapter;
    private ArrayList<Destination> destinationList = new ArrayList<>();

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private boolean isTemplate = false;
    private boolean budgetEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip);

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

        tripId = getIntent().getStringExtra("tripId");

        // Bind views
        imgCover = findViewById(R.id.imgCover);

        tvTripName = findViewById(R.id.tvTripName);
        tvTripDateTime = findViewById(R.id.tvTripDateTime);
        tvTripLocation = findViewById(R.id.tvTripLocation);

        tvTripCategory = findViewById(R.id.tvTripCategory);
        tvTemplateBadge = findViewById(R.id.tvTemplateBadge);
        tvTripBudget = findViewById(R.id.tvTripBudget);

        btnEnableBudget = findViewById(R.id.btnEnableBudget);
        tvEmptyDestinations = findViewById(R.id.tvEmptyDestinations);

        rvDestinations = findViewById(R.id.rvDestinations);

        btnEditTrip = findViewById(R.id.btnEditTrip);
        btnAddDestination = findViewById(R.id.btnAddDestination);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnBackTrip = findViewById(R.id.btnBackTrip);

        // Basic safety
        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Trip not found (missing tripId).", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ Setup destinations RecyclerView
        rvDestinations.setLayoutManager(new LinearLayoutManager(this));
        destinationAdapter = new DestinationAdapter(destinationList);
        rvDestinations.setAdapter(destinationAdapter);

        // ✅ Load trip details + destinations
        loadTripDetails();
        loadDestinations();

        // Back
        btnBackTrip.setOnClickListener(v -> finish());

        // Add destination (IMPORTANT: pass tripId)
        btnAddDestination.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, CreateDestination.class);
            intent.putExtra("tripId", tripId);
            startActivity(intent);
        });

        // Start/Activate button (dynamic)
        btnStartTrip.setOnClickListener(v -> {
            if (isTemplate) {
                activateTrip(); // will flip template -> false
            } else {
                Intent intent = new Intent(TripActivity.this, StartGala.class);
                intent.putExtra("tripId", tripId);
                startActivity(intent);
                finish();
            }
        });

        // Enable budgeting (UI only for now; logic later)
        if (btnEnableBudget != null) {
            btnEnableBudget.setOnClickListener(v ->
                    Toast.makeText(this, "Enable Budgeting clicked (we'll add dialog next).", Toast.LENGTH_SHORT).show()
            );
        }
    }

    private void loadTripDetails() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
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
                        return;
                    }

                    String name = doc.getString("trip_name");
                    String location = doc.getString("location");
                    String date = doc.getString("date");
                    String time = doc.getString("time");
                    String coverUrl = doc.getString("cover_url");

                    String category = doc.getString("trip_category");

                    Boolean templateVal = doc.getBoolean("is_template");
                    Boolean budgetVal = doc.getBoolean("budget_enabled");

                    Double tripBudget = doc.getDouble("trip_budget");

                    isTemplate = (templateVal != null && templateVal);
                    budgetEnabled = (budgetVal != null && budgetVal);

                    tvTripName.setText(name != null ? name : "");

                    // Date/time display: avoid showing " • " when empty
                    String dt = "";
                    if (!TextUtils.isEmpty(date)) dt += date;
                    if (!TextUtils.isEmpty(time)) dt += (dt.isEmpty() ? "" : " • ") + time;
                    tvTripDateTime.setText(dt.isEmpty() ? "" : dt);

                    tvTripLocation.setText(location != null ? location : "");
                    tvTripCategory.setText(category != null ? category : "OTHER");

                    // Template badge + button text
                    tvTemplateBadge.setVisibility(isTemplate ? View.VISIBLE : View.GONE);
                    btnStartTrip.setText(isTemplate ? "Activate" : "Start");

                    // Budget views
                    if (budgetEnabled) {
                        tvTripBudget.setVisibility(View.VISIBLE);
                        btnEnableBudget.setVisibility(View.GONE);

                        String budgetText = (tripBudget != null)
                                ? "Budget: ₱" + String.format("%.2f", tripBudget)
                                : "Budget: ₱0.00";
                        tvTripBudget.setText(budgetText);
                    } else {
                        tvTripBudget.setVisibility(View.GONE);
                        btnEnableBudget.setVisibility(View.VISIBLE);
                    }

                    if (coverUrl != null && !coverUrl.trim().isEmpty()) {
                        loadImageFromUrl(coverUrl);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ✅ NEW: load destinations into RecyclerView
    private void loadDestinations() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                // .orderBy("created_at") // optional; remove if you get index error
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;

                    destinationList.clear();

                    for (DocumentSnapshot doc : snapshots) {
                        Destination d = doc.toObject(Destination.class);
                        if (d != null) {
                            d.destinationId = doc.getId();
                            destinationList.add(d);
                        }
                    }

                    destinationAdapter.notifyDataSetChanged();

                    if (tvEmptyDestinations != null) {
                        tvEmptyDestinations.setVisibility(destinationList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void activateTrip() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        // Simple activation: set is_template = false
        btnStartTrip.setEnabled(false);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update("is_template", false)
                .addOnSuccessListener(unused -> {
                    isTemplate = false;
                    tvTemplateBadge.setVisibility(View.GONE);
                    btnStartTrip.setText("Start");
                    btnStartTrip.setEnabled(true);
                    Toast.makeText(this, "Trip activated!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    btnStartTrip.setEnabled(true);
                    Toast.makeText(this, "Activate failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadImageFromUrl(String imageUrl) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(imageUrl);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();

                java.io.InputStream input = connection.getInputStream();
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);

                runOnUiThread(() -> imgCover.setImageBitmap(bitmap));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
