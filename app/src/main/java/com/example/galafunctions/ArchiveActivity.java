package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ArchiveActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvArchived;
    private TextView tvEmpty;
    private EditText etSearch;
    private Button btnFilter;

    private final ArrayList<Trip> archivedList = new ArrayList<>();
    private TripAdapter adapter;

    private ListenerRegistration listener;
    private String searchQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_archive);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        rvArchived = findViewById(R.id.rvArchived);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        btnFilter = findViewById(R.id.btnFilter);

        rvArchived.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(this, archivedList);
        rvArchived.setAdapter(adapter);

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                attachListener(); // reload filtered
            }
        });

        // Filter placeholder
        btnFilter.setOnClickListener(v ->
                Toast.makeText(this, "Filter (next step)", Toast.LENGTH_SHORT).show()
        );

        // ✅ Add long-press options (restore / delete to deleted_trips)
        rvArchived.addOnItemTouchListener(
                new RecyclerItemClickListener(this, rvArchived,
                        (view, position) -> {
                            // normal click -> open trip
                            Trip t = archivedList.get(position);
                            Intent i = new Intent(ArchiveActivity.this, TripActivity.class);
                            i.putExtra("tripId", t.tripId);
                            startActivity(i);
                        },
                        (view, position) -> {
                            Trip t = archivedList.get(position);
                            showOptionsDialog(t);
                        }
                )
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachListener();
    }

    private boolean matchesSearch(Trip t) {
        if (TextUtils.isEmpty(searchQuery)) return true;

        String name = (t.trip_name != null) ? t.trip_name.toLowerCase() : "";
        String loc  = (t.location != null) ? t.location.toLowerCase() : "";
        String cat  = (t.trip_category != null) ? t.trip_category.toLowerCase() : "";

        return name.contains(searchQuery) || loc.contains(searchQuery) || cat.contains(searchQuery);
    }

    private void attachListener() {
        detachListener();

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        listener = db.collection("users")
                .document(uid)
                .collection("trips")
                // archived rules: either status == ARCHIVED or is_archived == true (support both)
                .whereEqualTo("is_archived", true)
                .orderBy("archived_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    archivedList.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Trip t = doc.toObject(Trip.class);
                        if (t != null) {
                            t.tripId = doc.getId();
                            if (matchesSearch(t)) archivedList.add(t);
                        }
                    }

                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(archivedList.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                });
    }

    private void detachListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    private void showOptionsDialog(Trip trip) {
        new AlertDialog.Builder(this)
                .setTitle(trip.trip_name != null ? trip.trip_name : "Trip")
                .setItems(new CharSequence[]{"Restore", "Delete (move to Deleted)"}, (dialog, which) -> {
                    if (which == 0) restoreTrip(trip);
                    else moveToDeleted(trip);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreTrip(Trip trip) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("is_archived", false);
        updates.put("archived_at", null);
        updates.put("status", "PLANNED"); // balik planned

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(trip.tripId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Restored!", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void moveToDeleted(Trip trip) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // Copy fields to deleted_trips
        Map<String, Object> deleted = new HashMap<>();
        deleted.put("trip_name", trip.trip_name);
        deleted.put("location", trip.location);
        deleted.put("date", trip.date);
        deleted.put("time", trip.time);
        deleted.put("cover_url", trip.cover_url);
        deleted.put("trip_category", trip.trip_category);
        deleted.put("status", trip.status);
        deleted.put("is_template", trip.is_template);
        deleted.put("budget_enabled", trip.budget_enabled);
        deleted.put("trip_budget", trip.trip_budget);
        deleted.put("total_spent", trip.total_spent);

        deleted.put("deleted_at", Timestamp.now());

        // 1) Save to deleted_trips
        db.collection("users")
                .document(uid)
                .collection("deleted_trips")
                .document(trip.tripId)
                .set(deleted)
                .addOnSuccessListener(unused -> {
                    // 2) Remove from trips (soft delete to separate collection)
                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(trip.tripId)
                            .delete()
                            .addOnSuccessListener(u2 ->
                                    Toast.makeText(this, "Moved to Deleted!", Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Move failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}