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
import androidx.appcompat.app.AlertDialog;
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
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class TripActivity extends AppCompatActivity {

    private ImageView imgCover;

    private TextView tvTripName, tvTripDateTime, tvTripLocation;
    private TextView tvTripCategory, tvTemplateBadge, tvTripBudget;
    private TextView btnEnableBudget;
    private TextView tvEmptyDestinations;

    private Button btnStartTrip, btnAddDestination, btnBackTrip, btnEditTrip, btnPlanTrip;
    private RecyclerView rvDestinations;

    private DestinationAdapter destinationAdapter;
    private final ArrayList<Destination> destinationList = new ArrayList<>();

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;

    private boolean isTemplate = false;
    private boolean budgetEnabled = false;
    private String tripStatus = "PLANNED";

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
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Trip not found (missing tripId).", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

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
        btnPlanTrip = findViewById(R.id.btnPlanTrip);
        btnStartTrip = findViewById(R.id.btnStartTrip);
        btnBackTrip = findViewById(R.id.btnBackTrip);

        rvDestinations.setLayoutManager(new LinearLayoutManager(this));

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

        btnBackTrip.setOnClickListener(v -> finish());

        btnAddDestination.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, CreateDestination.class);
            intent.putExtra("tripId", tripId);
            startActivity(intent);
        });

        // ✅ PLAN / RESCHEDULE with validation
        btnPlanTrip.setOnClickListener(v -> {
            if (!hasAtLeastOneDestination()) {
                showNeedDestinationDialog("Plan Trip");
                return;
            }

            Intent i = new Intent(TripActivity.this, PlanTripActivity.class);
            i.putExtra(PlanTripActivity.EXTRA_TRIP_ID, tripId);
            startActivity(i);
        });

        // ✅ START / ACTIVATE / START NOW with validation
        btnStartTrip.setOnClickListener(v -> {
            // template can be activated even without destinations? up to you.
            // If you ALSO want to require destinations before activating, move the check above confirmActivateTrip.
            if (isTemplate) {
                confirmActivateTrip();
                return;
            }

            if (!hasAtLeastOneDestination()) {
                showNeedDestinationDialog("Start Gala");
                return;
            }

            confirmStartGala();
        });

        if (btnEnableBudget != null) {
            btnEnableBudget.setOnClickListener(v ->
                    Toast.makeText(this, "Enable Budgeting clicked (dialog next).", Toast.LENGTH_SHORT).show()
            );
        }

        btnEditTrip.setOnClickListener(v -> {
            Intent intent = new Intent(TripActivity.this, EditTripActivity.class);
            intent.putExtra("tripId", tripId);
            startActivity(intent);
        });

        loadTripDetails();
        updateActionButtons(); // ✅ initial UI state
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTripDetails();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachDestinationsListener();
    }

    // -------------------------
    // ✅ VALIDATION HELPERS
    // -------------------------

    private boolean hasAtLeastOneDestination() {
        return destinationList != null && !destinationList.isEmpty();
    }

    private void showNeedDestinationDialog(String actionName) {
        new AlertDialog.Builder(this)
                .setTitle("Add Destination First")
                .setMessage("You need to add at least 1 destination before you can " + actionName + ".")
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Add Destination", (d, w) -> {
                    Intent intent = new Intent(TripActivity.this, CreateDestination.class);
                    intent.putExtra("tripId", tripId);
                    startActivity(intent);
                })
                .show();
    }

    private void updateActionButtons() {
        boolean hasDest = hasAtLeastOneDestination();

        // template logic: activate can still be allowed even if no dest
        if (isTemplate) {
            if (btnPlanTrip != null) btnPlanTrip.setEnabled(false);
            if (btnStartTrip != null) btnStartTrip.setEnabled(true); // Activate always allowed
            return;
        }

        // for normal trip, both need at least 1 destination
        if (btnPlanTrip != null) btnPlanTrip.setEnabled(hasDest);
        if (btnStartTrip != null) btnStartTrip.setEnabled(hasDest);
    }

    // -------------------------
    // LOAD TRIP DETAILS
    // -------------------------

    private void loadTripDetails() {
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

                    String name = doc.getString("trip_name");
                    String location = doc.getString("location");
                    String coverUrl = doc.getString("cover_url");
                    String category = doc.getString("trip_category");

                    // planned fields (legacy)
                    String plannedDate = doc.getString("date");
                    String plannedTime = doc.getString("time");

                    // scheduled fields (DATE ONLY + earliest destination time)
                    String scheduledDate = doc.getString("scheduled_date");
                    String firstTime = doc.getString("first_destination_time");
                    String fallbackScheduledTime = doc.getString("scheduled_time");

                    String status = doc.getString("status");
                    tripStatus = !TextUtils.isEmpty(status) ? status.toUpperCase() : "PLANNED";

                    Boolean templateVal = doc.getBoolean("is_template");
                    Boolean budgetVal = doc.getBoolean("budget_enabled");
                    Double tripBudget = doc.getDouble("trip_budget");

                    isTemplate = (templateVal != null && templateVal);
                    budgetEnabled = (budgetVal != null && budgetVal);

                    tvTripName.setText(name != null ? name : "");
                    tvTripLocation.setText(location != null ? location : "");
                    tvTripCategory.setText(category != null ? category : "OTHER");

                    String dt = "";
                    if ("SCHEDULED".equals(tripStatus)) {
                        if (!TextUtils.isEmpty(scheduledDate)) dt += scheduledDate;

                        String timeToShow = !TextUtils.isEmpty(firstTime)
                                ? firstTime
                                : (fallbackScheduledTime != null ? fallbackScheduledTime : "");

                        if (!TextUtils.isEmpty(timeToShow)) {
                            dt += (dt.isEmpty() ? "" : " • ") + timeToShow;
                        }
                    } else {
                        if (!TextUtils.isEmpty(plannedDate)) dt += plannedDate;
                        if (!TextUtils.isEmpty(plannedTime)) dt += (dt.isEmpty() ? "" : " • ") + plannedTime;
                    }
                    tvTripDateTime.setText(dt.isEmpty() ? "—" : dt);

                    tvTemplateBadge.setVisibility(isTemplate ? View.VISIBLE : View.GONE);

                    if (isTemplate) {
                        btnStartTrip.setText("Activate");
                        btnPlanTrip.setVisibility(View.GONE);
                    } else {
                        btnPlanTrip.setVisibility(View.VISIBLE);
                        if ("SCHEDULED".equals(tripStatus)) {
                            btnPlanTrip.setText("Reschedule");
                            btnStartTrip.setText("Start Now");
                        } else {
                            btnPlanTrip.setText("Plan Trip");
                            btnStartTrip.setText("Start");
                        }
                    }

                    if (budgetEnabled) {
                        tvTripBudget.setVisibility(View.VISIBLE);
                        if (btnEnableBudget != null) btnEnableBudget.setVisibility(View.GONE);

                        String budgetText = (tripBudget != null)
                                ? "Budget: ₱" + String.format(java.util.Locale.getDefault(), "%.2f", tripBudget)
                                : "Budget: ₱0.00";
                        tvTripBudget.setText(budgetText);
                    } else {
                        tvTripBudget.setVisibility(View.GONE);
                        if (btnEnableBudget != null) btnEnableBudget.setVisibility(View.VISIBLE);
                    }

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

                    if (!TextUtils.isEmpty(coverUrl)) loadImageFromUrl(coverUrl);

                    attachDestinationsListener();

                    // ✅ update buttons after trip info load
                    updateActionButtons();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void attachDestinationsListener() {
        detachDestinationsListener();

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        destinationsListener = db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .orderBy("start_time_millis", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Destinations error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshots == null) return;

                    destinationList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Destination d = doc.toObject(Destination.class);
                        if (d != null) {
                            d.destinationId = doc.getId();
                            destinationList.add(d);
                        }
                    }

                    destinationAdapter.notifyDataSetChanged();

                    boolean empty = destinationList.isEmpty();

                    if (tvEmptyDestinations != null) {
                        tvEmptyDestinations.setVisibility(empty ? View.VISIBLE : View.GONE);
                    }

                    // ✅ LIVE update buttons based on destination count
                    updateActionButtons();
                });
    }

    private void detachDestinationsListener() {
        if (destinationsListener != null) {
            destinationsListener.remove();
            destinationsListener = null;
        }
    }

    // -------------------------
    // ✅ START GALA CONFIRMATION
    // -------------------------

    private void confirmStartGala() {
        if ("COMPLETED".equalsIgnoreCase(tripStatus)) {
            Toast.makeText(this, "This trip is already completed.", Toast.LENGTH_SHORT).show();
            return;
        }

        // extra safety: if list empty (shouldn't happen)
        if (!hasAtLeastOneDestination()) {
            showNeedDestinationDialog("Start Gala");
            return;
        }

        String title = "Start Gala?";
        String msg;

        if ("IN_PROGRESS".equalsIgnoreCase(tripStatus)) {
            title = "Resume Gala?";
            msg = "This trip is already in progress. Do you want to continue?";
        } else if ("SCHEDULED".equalsIgnoreCase(tripStatus)) {
            msg = "Are you sure you want to start now? This will begin your trip immediately.";
        } else {
            msg = "Are you sure you want to start this trip now?";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Yes", (d, w) -> openStartGala())
                .show();
    }

    private void openStartGala() {
        Intent intent = new Intent(TripActivity.this, StartGala.class);
        intent.putExtra("tripId", tripId);
        startActivity(intent);
    }

    private void confirmActivateTrip() {
        new AlertDialog.Builder(this)
                .setTitle("Activate Trip?")
                .setMessage("Are you sure you want to activate this template trip?")
                .setNegativeButton("Cancel", (d, w) -> {})
                .setPositiveButton("Yes", (d, w) -> activateTrip())
                .show();
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
                    btnPlanTrip.setVisibility(View.VISIBLE);
                    btnPlanTrip.setText("Plan Trip");
                    Toast.makeText(this, "Trip activated!", Toast.LENGTH_SHORT).show();

                    // ✅ update button states now that template is off
                    updateActionButtons();
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
