package com.example.galafunctions;

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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class Gallery extends Fragment {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvFinishedTrips;
    private TextView tvEmptyFinished;
    private EditText etGallerySearch;
    private Button btnGalleryFilter;

    // ✅ IMPORTANT: use FinishedTrip model (NOT Trip)
    private final ArrayList<FinishedTrip> rawList = new ArrayList<>();
    private final ArrayList<FinishedTrip> displayList = new ArrayList<>();

    // ✅ IMPORTANT: use FinishedTripAdapter (NOT TripAdapter)
    private FinishedTripAdapter adapter;

    private ListenerRegistration finishedListener;
    private String searchQuery = "";

    public Gallery() {}

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

        View view = inflater.inflate(R.layout.fragment_gallery, container, false);

        rvFinishedTrips = view.findViewById(R.id.rvFinishedTrips);
        tvEmptyFinished = view.findViewById(R.id.tvEmptyFinished);
        etGallerySearch = view.findViewById(R.id.etGallerySearch);
        btnGalleryFilter = view.findViewById(R.id.btnGalleryFilter);

        rvFinishedTrips.setLayoutManager(new LinearLayoutManager(getContext()));

        // ✅ adapter for finished trips
        adapter = new FinishedTripAdapter(requireContext(), displayList);
        rvFinishedTrips.setAdapter(adapter);

        btnGalleryFilter.setOnClickListener(v ->
                Toast.makeText(getContext(), "Filter next step.", Toast.LENGTH_SHORT).show()
        );

        etGallerySearch.addTextChangedListener(new TextWatcher() {
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
        attachFinishedListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        detachListener();
    }

    private void attachFinishedListener() {
        detachListener();

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        finishedListener = db.collection("users")
                .document(uid)
                .collection("trips")
                .whereEqualTo("status", "COMPLETED")
                .whereEqualTo("is_archived", false)
                .orderBy("ended_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(getContext(), "Load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    rawList.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        FinishedTrip t = doc.toObject(FinishedTrip.class);
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

        for (FinishedTrip t : rawList) {
            if (t == null) continue;
            if (matchesSearch(t)) displayList.add(t);
        }

        adapter.notifyDataSetChanged();
        tvEmptyFinished.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesSearch(FinishedTrip t) {
        if (TextUtils.isEmpty(searchQuery)) return true;

        String name = (t.trip_name != null) ? t.trip_name.toLowerCase() : "";
        String loc  = (t.location != null) ? t.location.toLowerCase() : "";
        String cat  = (t.trip_category != null) ? t.trip_category.toLowerCase() : "";

        return name.contains(searchQuery) || loc.contains(searchQuery) || cat.contains(searchQuery);
    }

    private void detachListener() {
        if (finishedListener != null) {
            finishedListener.remove();
            finishedListener = null;
        }
    }
}