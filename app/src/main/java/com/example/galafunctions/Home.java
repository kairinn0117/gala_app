package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Home extends Fragment {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvTrips;
    private TextView tvEmptyTrips;
    private EditText etSearch;

    private ImageButton btnFilter, btnPlanned;
    private ImageButton fabAddGala;

    private final ArrayList<Trip> rawList = new ArrayList<>();
    private final ArrayList<Trip> displayList = new ArrayList<>();
    private TripAdapter adapter;

    private ListenerRegistration plannedListener;
    private String searchQuery = "";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        rvTrips = view.findViewById(R.id.rvTrips);
        tvEmptyTrips = view.findViewById(R.id.tvEmptyTrips);
        etSearch = view.findViewById(R.id.etSearch);

        btnPlanned = view.findViewById(R.id.btnPlanned);
        btnFilter = view.findViewById(R.id.btnFilter);

        fabAddGala = view.findViewById(R.id.fabAddGala);

        rvTrips.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new TripAdapter(requireContext(), displayList);
        rvTrips.setAdapter(adapter);

        // ✅ SWIPE TO ARCHIVE
        attachSwipeToArchive();

        fabAddGala.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CreateTrip.class))
        );

        btnPlanned.setOnClickListener(v -> {
            if (getActivity() == null) return;
            startActivity(new Intent(getActivity(), ScheduledTripsActivity.class));
        });

        btnFilter.setOnClickListener(v ->
                Toast.makeText(getContext(), "Filter next step.", Toast.LENGTH_SHORT).show()
        );

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                applySearch();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        attachPlannedListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        detachListener();
    }

    private void attachPlannedListener() {
        detachListener();

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        plannedListener = db.collection("users")
                .document(uid)
                .collection("trips")
                // NOTE: You can change this to show also SCHEDULED / IN_PROGRESS if you want
                .whereEqualTo("status", "PLANNED")
                .whereEqualTo("is_archived", false)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(getContext(), "Load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    rawList.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Trip t = doc.toObject(Trip.class);
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

        for (Trip t : rawList) {
            if (t == null) continue;
            if (matchesSearch(t)) displayList.add(t);
        }

        adapter.notifyDataSetChanged();
        tvEmptyTrips.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesSearch(Trip t) {
        if (TextUtils.isEmpty(searchQuery)) return true;

        String name = (t.trip_name != null) ? t.trip_name.toLowerCase() : "";
        String loc  = (t.location != null) ? t.location.toLowerCase() : "";
        String cat  = (t.trip_category != null) ? t.trip_category.toLowerCase() : "";

        return name.contains(searchQuery) || loc.contains(searchQuery) || cat.contains(searchQuery);
    }

    private void detachListener() {
        if (plannedListener != null) {
            plannedListener.remove();
            plannedListener = null;
        }
    }

    // -------------------------
    // ✅ Swipe to Archive
    // -------------------------

    private void attachSwipeToArchive() {
        ItemTouchHelper.SimpleCallback cb = new ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT
        ) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos < 0 || pos >= displayList.size()) {
                    adapter.notifyDataSetChanged();
                    return;
                }

                Trip trip = displayList.get(pos);
                showArchiveConfirm(trip, pos);
            }
        };

        new ItemTouchHelper(cb).attachToRecyclerView(rvTrips);
    }

    private void showArchiveConfirm(Trip trip, int swipedPosition) {
        if (trip == null || TextUtils.isEmpty(trip.tripId)) {
            adapter.notifyItemChanged(swipedPosition);
            return;
        }

        String name = !TextUtils.isEmpty(trip.trip_name) ? trip.trip_name : "this gala";

        new AlertDialog.Builder(requireContext())
                .setTitle("Archive Gala?")
                .setMessage("Are you sure you want to archive \"" + name + "\"?\n\nYou can restore it later in Archive.")
                .setNegativeButton("Cancel", (d, w) -> {
                    // reset swipe
                    adapter.notifyItemChanged(swipedPosition);
                })
                .setPositiveButton("Archive", (d, w) -> archiveTrip(trip.tripId))
                .show();
    }

    private void archiveTrip(String tripId) {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put("is_archived", true);
        updates.put("archived_at", Timestamp.now());
        updates.put("status", "ARCHIVED"); // optional, helpful label

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(getContext(), "Archived!", Toast.LENGTH_SHORT).show();
                    // optional: open ArchiveActivity after archive
                    if (getActivity() != null) {
                        startActivity(new Intent(getActivity(), ArchiveActivity.class));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(getContext(), "Archive failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}
