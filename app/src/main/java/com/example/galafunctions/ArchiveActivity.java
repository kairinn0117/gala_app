package com.example.galafunctions;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
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
    private ImageButton btnFilter, btnBack;

    private final ArrayList<ArchivedTrip> rawList = new ArrayList<>();
    private final ArrayList<ArchivedTrip> displayList = new ArrayList<>();
    private ArchivedTripAdapter adapter;

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
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        rvArchived.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ArchivedTripAdapter(this, displayList, new ArchivedTripAdapter.OnArchivedTripClick() {
            @Override
            public void onClick(ArchivedTrip trip) {
                // ✅ CLICK = SHOW OPTIONS ONLY (no TripActivity)
                if (trip == null || TextUtils.isEmpty(trip.tripId)) return;
                showOptionsDialog(trip);
            }

            @Override
            public void onLongPress(ArchivedTrip trip) {
                // ✅ LONG PRESS = SAME OPTIONS
                if (trip == null || TextUtils.isEmpty(trip.tripId)) return;
                showOptionsDialog(trip);
            }
        });
        rvArchived.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                applySearch();
            }
        });

        btnFilter.setOnClickListener(v ->
                Toast.makeText(this, "Filter (next step)", Toast.LENGTH_SHORT).show()
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

    private void attachListener() {
        detachListener();

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        listener = db.collection("users")
                .document(uid)
                .collection("trips")
                .whereEqualTo("is_archived", true)
                .orderBy("archived_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    rawList.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ArchivedTrip t = doc.toObject(ArchivedTrip.class);
                        if (t != null) {
                            t.tripId = doc.getId();
                            rawList.add(t);
                        }
                    }

                    applySearch();
                });
    }

    private void applySearch() {
        displayList.clear();

        for (ArchivedTrip t : rawList) {
            if (t == null) continue;
            if (matchesSearch(t)) displayList.add(t);
        }

        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesSearch(ArchivedTrip t) {
        if (TextUtils.isEmpty(searchQuery)) return true;

        String name = (t.trip_name != null) ? t.trip_name.toLowerCase() : "";
        String loc  = (t.location != null) ? t.location.toLowerCase() : "";
        String cat  = (t.trip_category != null) ? t.trip_category.toLowerCase() : "";

        return name.contains(searchQuery) || loc.contains(searchQuery) || cat.contains(searchQuery);
    }

    private void detachListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    // -------------------------
    // ✅ OPTIONS
    // -------------------------

    private void showOptionsDialog(ArchivedTrip trip) {
        String title = !TextUtils.isEmpty(trip.trip_name) ? trip.trip_name : "Archived Gala";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(new CharSequence[]{
                        "Restore",
                        "Permanent Delete"
                }, (dialog, which) -> {
                    if (which == 0) {
                        restoreTrip(trip.tripId);
                    } else {
                        confirmPermanentDelete(trip);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmPermanentDelete(ArchivedTrip trip) {
        String name = !TextUtils.isEmpty(trip.trip_name) ? trip.trip_name : "this gala";

        new AlertDialog.Builder(this)
                .setTitle("Permanent Delete?")
                .setMessage("Are you sure you want to permanently delete \"" + name + "\"?\n\n" +
                        "It will be moved to Permanent Deleted (so you still have a record).")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> moveToPermanentDeleted(trip))
                .show();
    }

    private void restoreTrip(String tripId) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("is_archived", false);
        updates.put("archived_at", null);
        updates.put("status", "PLANNED"); // adjust if you want

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this, "Restored!", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ✅ move to a safe place instead of true delete
    private void moveToPermanentDeleted(ArchivedTrip trip) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> deleted = new HashMap<>();
        deleted.put("trip_name", trip.trip_name);
        deleted.put("location", trip.location);
        deleted.put("trip_category", trip.trip_category);
        deleted.put("status", trip.status);
        deleted.put("cover_url", trip.cover_url);
        deleted.put("budget_enabled", trip.budget_enabled);
        deleted.put("trip_budget", trip.trip_budget);

        // keep archive metadata if you want
        deleted.put("archived_at", trip.archived_at);

        deleted.put("permanent_deleted_at", Timestamp.now());

        db.collection("users")
                .document(uid)
                .collection("permanent_deleted_trips")
                .document(trip.tripId)
                .set(deleted)
                .addOnSuccessListener(unused -> {
                    // remove from active trips after copying
                    db.collection("users")
                            .document(uid)
                            .collection("trips")
                            .document(trip.tripId)
                            .delete()
                            .addOnSuccessListener(u2 ->
                                    Toast.makeText(this, "Moved to Permanent Deleted!", Toast.LENGTH_SHORT).show()
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