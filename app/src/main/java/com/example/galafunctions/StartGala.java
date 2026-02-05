package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class StartGala extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_start_gala);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        TextView tvTripTitle = findViewById(R.id.tvTripTitle);
        TextView tvProgress = findViewById(R.id.tvProgress);

        TextView tvDestName = findViewById(R.id.tvDestName);
        TextView tvDestType = findViewById(R.id.tvDestType);
        TextView tvDestLocation = findViewById(R.id.tvDestLocation);
        TextView tvDestDateTime = findViewById(R.id.tvDestDateTime);
        TextView tvDestBudget = findViewById(R.id.tvDestBudget);
        TextView tvDestDescription = findViewById(R.id.tvDestDescription);

        Button btnEndTrip = findViewById(R.id.btnEndTrip);
        Button btnDoneNext = findViewById(R.id.btnDoneNext);

        btnEndTrip.setOnClickListener(v -> {
            Intent intent = new Intent(StartGala.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });
    }
}