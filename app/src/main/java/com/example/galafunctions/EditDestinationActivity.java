package com.example.galafunctions;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditDestinationActivity extends AppCompatActivity {

    public static final String EXTRA_TRIP_ID = "tripId";
    public static final String EXTRA_DEST_ID = "destinationId";

    private EditText etName, etLocation, etBudget, etDescription;
    private Spinner spType;
    private Button btnCancel, btnSave;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String tripId;
    private String destinationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_edit_destination);

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

        tripId = getIntent().getStringExtra(EXTRA_TRIP_ID);
        destinationId = getIntent().getStringExtra(EXTRA_DEST_ID);

        if (TextUtils.isEmpty(tripId) || TextUtils.isEmpty(destinationId)) {
            Toast.makeText(this, "Missing ids.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etName = findViewById(R.id.etDestinationName);
        spType = findViewById(R.id.spDestinationType);
        etLocation = findViewById(R.id.etDestinationLocation);
        etBudget = findViewById(R.id.etDestinationBudget);
        etDescription = findViewById(R.id.etDestinationDescription);

        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);

        // Type options (same as create)
        String[] types = {"Hotel", "Tourist Spot", "Food", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                types
        );
        spType.setAdapter(adapter);

        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveEdits());

        loadDestination();
    }

    private void loadDestination() {
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
                .collection("destinations")
                .document(destinationId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Destination not found.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    String name = doc.getString("destination_name");
                    String type = doc.getString("type");
                    String location = doc.getString("location");
                    Double budget = doc.getDouble("budget");
                    String description = doc.getString("description");

                    etName.setText(name != null ? name : "");
                    etLocation.setText(location != null ? location : "");
                    etDescription.setText(description != null ? description : "");

                    if (budget != null) etBudget.setText(String.valueOf(budget));

                    if (!TextUtils.isEmpty(type)) {
                        ArrayAdapter a = (ArrayAdapter) spType.getAdapter();
                        int pos = a.getPosition(type);
                        if (pos >= 0) spType.setSelection(pos);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void saveEdits() {
        if (auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login first.", Toast.LENGTH_SHORT).show();
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        String name = etName.getText().toString().trim();
        String type = (spType.getSelectedItem() != null) ? spType.getSelectedItem().toString().trim() : "";
        String location = etLocation.getText().toString().trim();
        String budgetStr = etBudget.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        if (TextUtils.isEmpty(name)) { etName.setError("Required"); return; }
        if (TextUtils.isEmpty(location)) { etLocation.setError("Required"); return; }

        Double budget = null;
        if (!TextUtils.isEmpty(budgetStr)) {
            try {
                budget = Double.parseDouble(budgetStr);
                if (budget < 0) {
                    etBudget.setError("Must be 0 or more");
                    return;
                }
            } catch (Exception e) {
                etBudget.setError("Number only");
                return;
            }
        }

        btnSave.setEnabled(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("destination_name", name);
        updates.put("type", type);
        updates.put("location", location);
        updates.put("description", description);

        // optional: if blank -> remove budget value
        if (budget != null) updates.put("budget", budget);
        else updates.put("budget", null);

        db.collection("users")
                .document(uid)
                .collection("trips")
                .document(tripId)
                .collection("destinations")
                .document(destinationId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Destination updated!", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}