package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class CreateDestination extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_destination);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText etName = findViewById(R.id.etDestinationName);
        Spinner spType = findViewById(R.id.spDestinationType);
        EditText etLocation = findViewById(R.id.etDestinationLocation);
        EditText etBudget = findViewById(R.id.etDestinationBudget);
        EditText etDate = findViewById(R.id.etDestinationDate);
        EditText etTime = findViewById(R.id.etDestinationTime);
        EditText etDesc = findViewById(R.id.etDestinationDescription);
        Button btnSaveDestination = findViewById(R.id.btnSaveDestination);
        Button btnCancelDestination = findViewById(R.id.btnCancelDestination);

        // Spinner options
        String[] types = {"Home", "Tourist Spot", "Food"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                types
        );
        spType.setAdapter(adapter);

        btnCancelDestination.setOnClickListener(v -> {
            finish(); // back to TripActivity
        });

        btnSaveDestination.setOnClickListener(v -> {
            Intent intent = new Intent(CreateDestination.this, TripActivity.class);
            startActivity(intent);
            finish(); // optional: para di bumalik sa CreateDestination pag back
        });
    }
}