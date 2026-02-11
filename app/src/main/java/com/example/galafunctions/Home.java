package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class Home extends Fragment {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvTrips;
    private TextView tvEmptyTrips;
    private EditText etSearch;

    private Button btnFilter, btnPlanned;
    private FloatingActionButton fabAddGala;

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

        // NOTE: TripAdapter click logic should already go to TripActivity using tripId
        adapter = new TripAdapter(requireContext(), displayList);
        rvTrips.setAdapter(adapter);

        fabAddGala.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), CreateTrip.class))
        );

        // ✅ UPDATED: btnPlanned now opens ScheduledTripsActivity (SCHEDULED list)
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
        // Home stays as PLANNED list
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
                .whereEqualTo("status", "PLANNED")
                .whereEqualTo("is_archived", false)
                .orderBy("created_at", Query.Direction.DESCENDING)
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
            if (matchesSearch(t)) {
                displayList.add(t);
            }
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
}