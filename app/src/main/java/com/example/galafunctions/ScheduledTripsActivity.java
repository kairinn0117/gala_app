package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class ScheduledTripsActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvScheduled;
    private TextView tvEmpty;
    private EditText etSearch;
    private ImageButton btnFilter;

    private final ArrayList<ScheduledTrip> rawList = new ArrayList<>();
    private final ArrayList<ScheduledTrip> displayList = new ArrayList<>();

    private ScheduledTripAdapter adapter;
    private ListenerRegistration listener;

    private String searchQuery = "";

    // ✅ ASC = nearest upcoming -> farthest
    // ✅ DESC = farthest -> nearest
    private boolean sortNearestFirst = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_scheduled_trips);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        rvScheduled = findViewById(R.id.rvScheduled);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        btnFilter = findViewById(R.id.btnFilter);

        rvScheduled.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ScheduledTripAdapter(this, displayList, trip -> {
            if (trip == null || TextUtils.isEmpty(trip.tripId)) return;

            Intent i = new Intent(ScheduledTripsActivity.this, TripActivity.class);
            i.putExtra("tripId", trip.tripId);
            startActivity(i);
        });

        rvScheduled.setAdapter(adapter);

        // ✅ Filter button = toggle nearest/farthest
        btnFilter.setOnClickListener(v -> {
            sortNearestFirst = !sortNearestFirst;

            Toast.makeText(
                    this,
                    sortNearestFirst ? "Showing nearest trips first" : "Showing farthest trips first",
                    Toast.LENGTH_SHORT
            ).show();

            attachScheduledListener(); // re-query with new order
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                applySearch();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        attachScheduledListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachListener();
    }

    private void attachScheduledListener() {
        detachListener();

        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        // ✅ Nearest first = ASC (small millis = nearer date)
        // ✅ Farthest first = DESC
        Query.Direction dir = sortNearestFirst ? Query.Direction.ASCENDING : Query.Direction.DESCENDING;

        listener = db.collection("users")
                .document(uid)
                .collection("trips")
                .whereEqualTo("status", "SCHEDULED")
                .whereEqualTo("is_archived", false)
                .orderBy("scheduled_sort_millis", dir)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    rawList.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ScheduledTrip t = doc.toObject(ScheduledTrip.class);
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

        for (ScheduledTrip t : rawList) {
            if (t == null) continue;
            if (matchesSearch(t)) displayList.add(t);
        }

        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(displayList.isEmpty() ? TextView.VISIBLE : TextView.GONE);
    }

    private boolean matchesSearch(ScheduledTrip t) {
        if (TextUtils.isEmpty(searchQuery)) return true;

        String name = (t.trip_name != null) ? t.trip_name.toLowerCase() : "";
        String loc  = (t.location != null) ? t.location.toLowerCase() : "";

        return name.contains(searchQuery) || loc.contains(searchQuery);
    }

    private void detachListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }
}
