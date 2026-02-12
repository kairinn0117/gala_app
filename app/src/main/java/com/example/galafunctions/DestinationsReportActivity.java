package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class DestinationsReportActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private RecyclerView rvDestinations;

    private final ArrayList<Destination> list = new ArrayList<>();
    private DestinationSummaryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_destinations_report);

        // ✅ Handle window insets to prevent status bar overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tripId = getIntent().getStringExtra("tripId");
        if (TextUtils.isEmpty(tripId)) {
            Toast.makeText(this, "Missing tripId.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        rvDestinations = findViewById(R.id.rvDestinations);
        rvDestinations.setLayoutManager(new LinearLayoutManager(this));

        adapter = new DestinationSummaryAdapter(this, list, dest -> {
            if (dest == null) return;

            if (TextUtils.isEmpty(dest.photo_url)) {
                Toast.makeText(this, "No image", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent i = new Intent(this, DestinationPhotoActivity.class);
            i.putExtra("photo_url", dest.photo_url);
            i.putExtra("destination_name", dest.destination_name);
            startActivity(i);
        });

        rvDestinations.setAdapter(adapter);

        loadDestinations();
    }

    private void loadDestinations() {
        if (auth.getCurrentUser() == null) return;
        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .orderBy("created_at", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    list.clear();
                    for (var doc : qs.getDocuments()) {
                        Destination d = doc.toObject(Destination.class);
                        if (d != null) {
                            d.destinationId = doc.getId();
                            list.add(d);
                        }
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}