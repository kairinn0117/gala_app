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
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;

public class TripActivity extends AppCompatActivity {

    private ImageView imgCover;

    // Trip header
    private TextView tvTripName, tvTripDateTime, tvTripLocation;
    private TextView tvTripCategory, tvTemplateBadge, tvTripBudget;
    private TextView btnEnableBudget; // TextView in XML
    private TextView tvEmptyDestinations;

    private Button btnStartTrip, btnAddDestination, btnBackTrip, btnEditTrip;
    private RecyclerView rvDestinations;

    // RecyclerView
    private DestinationAdapter destinationAdapter;
    private final ArrayList<Destination> destinationList = new ArrayList<>();

    // Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private boolean isTemplate = false;
    private boolean budgetEnabled = false;

    // Firestore listener (para di dumoble kapag onResume)
    private ListenerRegistration destinationsListener;

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

        // Safety
        if (tripId == null || tripId.trim().isEmpty()) {
            Toast.makeText(this, "Trip not found (missing tripId).", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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

        // Recycler setup
        rvDestinations.setLayoutManager(new LinearLayoutManager(this));

        // ✅ Use updated adapter (with click listener + budgetEnabled)
        destinationAdapter = new DestinationAdapter(
                destinationList,
                budgetEnabled,
                destination -> {
                    if (destination == null || TextUtils.isEmpty(destination.destinationId)) {
                        Toast.makeText(this, "Destination not found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent i = new Intent(TripActivity.this, EditDestinationActivity.class);
                    i.putExtra(EditDestinationActivity.EXTRA_TRIP_ID, tripId);
                    i.putExtra(EditDestinationActivity.EXTRA_DEST_ID, destination.destinationId);
                    startActivity(i);
                }
        );
        rvDestinations.setAdapter(destinationAdapter);

        // Load trip details (will also attach destinations listener after budgetEnabled is known)
        loadTripDetails();

        // Buttons
        btnBackTrip.setOnClickListener(v -> finish());

        btnAddDestination.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, CreateDestination.class);
            intent.putExtra("tripId", tripId);
            startActivity(intent);
        });

        btnStartTrip.setOnClickListener(v -> {
            if (isTemplate) {
                activateTrip();
            } else {
                Intent intent = new Intent(TripActivity.this, StartGala.class);
                intent.putExtra("tripId", tripId);
                startActivity(intent);
                finish();
            }
        });

        if (btnEnableBudget != null) {
            btnEnableBudget.setOnClickListener(v ->
                    Toast.makeText(this, "Enable Budgeting clicked (we'll add dialog next).", Toast.LENGTH_SHORT).show()
            );
        }

        btnEditTrip.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, EditTripActivity.class);
            intent.putExtra("tripId", tripId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTripDetails(); // reload header + (re)attach listener
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ✅ important: remove snapshot listener para di magdoble kapag balik
        detachDestinationsListener();
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
                        finish();
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

                    String dt = "";
                    if (!TextUtils.isEmpty(date)) dt += date;
                    if (!TextUtils.isEmpty(time)) dt += (dt.isEmpty() ? "" : " • ") + time;
                    tvTripDateTime.setText(dt.isEmpty() ? "" : dt);

                    tvTripLocation.setText(location != null ? location : "");
                    tvTripCategory.setText(category != null ? category : "OTHER");

                    tvTemplateBadge.setVisibility(isTemplate ? View.VISIBLE : View.GONE);
                    btnStartTrip.setText(isTemplate ? "Activate" : "Start");

                    if (budgetEnabled) {
                        tvTripBudget.setVisibility(View.VISIBLE);
                        if (btnEnableBudget != null) btnEnableBudget.setVisibility(View.GONE);

                        String budgetText = (tripBudget != null)
                                ? "Budget: ₱" + String.format("%.2f", tripBudget)
                                : "Budget: ₱0.00";
                        tvTripBudget.setText(budgetText);
                    } else {
                        tvTripBudget.setVisibility(View.GONE);
                        if (btnEnableBudget != null) btnEnableBudget.setVisibility(View.VISIBLE);
                    }

                    // ✅ Recreate adapter with updated budgetEnabled (para mag show/hide budget sa items)
                    destinationAdapter = new DestinationAdapter(
                            destinationList,
                            budgetEnabled,
                            destination -> {
                                if (destination == null || TextUtils.isEmpty(destination.destinationId)) {
                                    Toast.makeText(this, "Destination not found.", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                Intent i = new Intent(TripActivity.this, EditDestinationActivity.class);
                                i.putExtra(EditDestinationActivity.EXTRA_TRIP_ID, tripId);
                                i.putExtra(EditDestinationActivity.EXTRA_DEST_ID, destination.destinationId);
                                startActivity(i);
                            }
                    );
                    rvDestinations.setAdapter(destinationAdapter);

                    if (coverUrl != null && !coverUrl.trim().isEmpty()) {
                        loadImageFromUrl(coverUrl);
                    }

                    // ✅ Make sure destinations are listening (after we know budgetEnabled)
                    attachDestinationsListener();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void attachDestinationsListener() {
        detachDestinationsListener(); // prevent duplicates

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        destinationsListener = db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Destinations listener error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshots == null) return;

                    destinationList.clear();

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Destination d = doc.toObject(Destination.class);
                        if (d != null) {
                            d.destinationId = doc.getId(); // ✅ important
                            destinationList.add(d);
                        }
                    }

                    destinationAdapter.notifyDataSetChanged();

                    if (tvEmptyDestinations != null) {
                        tvEmptyDestinations.setVisibility(destinationList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void detachDestinationsListener() {
        if (destinationsListener != null) {
            destinationsListener.remove();
            destinationsListener = null;
        }
    }

    private void activateTrip() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();
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