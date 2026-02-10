package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
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

public class TemplateActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvTemplates;
    private TextView tvEmptyTemplates;
    private EditText etSearch;
    private Button btnFilter;

    private final ArrayList<Trip> templates = new ArrayList<>();
    private TripAdapter adapter;

    private ListenerRegistration listener;
    private String searchQuery = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_template);

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

        rvTemplates = findViewById(R.id.rvTemplates);
        tvEmptyTemplates = findViewById(R.id.tvEmptyTemplates);
        etSearch = findViewById(R.id.etSearchTemplates);
        btnFilter = findViewById(R.id.btnFilterTemplates);

        rvTemplates.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TripAdapter(this, templates);
        rvTemplates.setAdapter(adapter);

        // ✅ Click: open TripActivity (since your TripAdapter opens TripActivity already,
        // you can skip RecyclerItemClickListener completely.
        // If you still want custom click, comment out TripAdapter click inside adapter.)

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = (s != null) ? s.toString().trim().toLowerCase() : "";
                attachListener(); // reload (simple)
            }
        });

        btnFilter.setOnClickListener(v ->
                Toast.makeText(this, "Filter next step (later).", Toast.LENGTH_SHORT).show()
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
        String loc = (t.location != null) ? t.location.toLowerCase() : "";

        return name.contains(searchQuery) || loc.contains(searchQuery);
    }

    private void attachListener() {
        detachListener();

        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        listener = db.collection("users")
                .document(uid)
                .collection("trips")
                .whereEqualTo("is_template", true)
                .whereEqualTo("is_archived", false)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Load error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snap == null) return;

                    templates.clear();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        Trip t = doc.toObject(Trip.class);
                        if (t != null) {
                            t.tripId = doc.getId();
                            if (matchesSearch(t)) templates.add(t);
                        }
                    }

                    adapter.notifyDataSetChanged();
                    tvEmptyTemplates.setVisibility(
                            templates.isEmpty() ? View.VISIBLE : View.GONE
                    );
                });
    }

    private void detachListener() {
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }
}