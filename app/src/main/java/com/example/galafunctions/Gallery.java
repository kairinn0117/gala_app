package com.example.galafunctions;

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
    private ImageButton btnGalleryFilter;

    private final ArrayList<FinishedTrip> rawList = new ArrayList<>();
    private final ArrayList<FinishedTrip> displayList = new ArrayList<>();

    private FinishedTripAdapter adapter;

    private ListenerRegistration finishedListener;
    private String searchQuery = "";

    // ✅ SORT OPTIONS
    private enum SortMode {
        NAME_ASC,
        NAME_DESC,
        ENDED_NEWEST,
        ENDED_OLDEST
    }

    private SortMode currentSort = SortMode.ENDED_NEWEST;

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

        adapter = new FinishedTripAdapter(requireContext(), displayList);
        rvFinishedTrips.setAdapter(adapter);

        // ✅ FILTER/SORT BUTTON
        btnGalleryFilter.setOnClickListener(v -> showSortDialog());

        etGallerySearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                applySearchAndSort();
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
                .orderBy("ended_at", Query.Direction.DESCENDING) // default newest
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

                    applySearchAndSort();
                });
    }

    private void showSortDialog() {
        String[] options = {
                "A-Z (Trip Name)",
                "Z-A (Trip Name)",
                "Newest Finished",
                "Oldest Finished"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Sort Finished Trips")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            currentSort = SortMode.NAME_ASC;
                            break;
                        case 1:
                            currentSort = SortMode.NAME_DESC;
                            break;
                        case 2:
                            currentSort = SortMode.ENDED_NEWEST;
                            break;
                        case 3:
                            currentSort = SortMode.ENDED_OLDEST;
                            break;
                    }
                    applySearchAndSort();
                })
                .show();
    }

    private void applySearchAndSort() {
        displayList.clear();

        for (FinishedTrip t : rawList) {
            if (t == null) continue;
            if (matchesSearch(t)) displayList.add(t);
        }

        applySort();

        adapter.notifyDataSetChanged();
        tvEmptyFinished.setVisibility(displayList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void applySort() {
        switch (currentSort) {

            case NAME_ASC:
                displayList.sort((a, b) -> {
                    String n1 = a.trip_name != null ? a.trip_name.toLowerCase() : "";
                    String n2 = b.trip_name != null ? b.trip_name.toLowerCase() : "";
                    return n1.compareTo(n2);
                });
                break;

            case NAME_DESC:
                displayList.sort((a, b) -> {
                    String n1 = a.trip_name != null ? a.trip_name.toLowerCase() : "";
                    String n2 = b.trip_name != null ? b.trip_name.toLowerCase() : "";
                    return n2.compareTo(n1);
                });
                break;

            case ENDED_NEWEST:
                displayList.sort((a, b) -> {
                    long t1 = (a.ended_at != null) ? a.ended_at.toDate().getTime() : 0;
                    long t2 = (b.ended_at != null) ? b.ended_at.toDate().getTime() : 0;
                    return Long.compare(t2, t1);
                });
                break;

            case ENDED_OLDEST:
                displayList.sort((a, b) -> {
                    long t1 = (a.ended_at != null) ? a.ended_at.toDate().getTime() : 0;
                    long t2 = (b.ended_at != null) ? b.ended_at.toDate().getTime() : 0;
                    return Long.compare(t1, t2);
                });
                break;
        }
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
