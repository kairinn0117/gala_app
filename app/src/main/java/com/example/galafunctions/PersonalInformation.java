package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserInfo;

public class PersonalInformation extends AppCompatActivity {

    private FirebaseAuth mAuth;

    private ImageButton btnBack, btnChangePassword, btnDeleteAccount;
    private EditText usernameField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_information);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        btnBack = findViewById(R.id.Backbtn);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        usernameField = findViewById(R.id.editTextText);

        btnBack.setOnClickListener(v -> finish());

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // Username = Email (your requirement)
        String email = user.getEmail() != null ? user.getEmail() : "";
        usernameField.setText(email);
        usernameField.setEnabled(false);
        usernameField.setFocusable(false);

        boolean googleOnly = isGoogleOnly(user);

        // ✅ Change Password: block Google-only users from entering Password.java
        if (googleOnly) {
            btnChangePassword.setEnabled(false);
            btnChangePassword.setAlpha(0.4f);
            btnChangePassword.setOnClickListener(v ->
                    Toast.makeText(this, "Google-only account: cannot change password.", Toast.LENGTH_SHORT).show()
            );
        } else {
            btnChangePassword.setEnabled(true);
            btnChangePassword.setAlpha(1.0f);
            btnChangePassword.setOnClickListener(v ->
                    startActivity(new Intent(PersonalInformation.this, Password.class))
            );
        }

        // ✅ Delete Account: always allowed, DeleteAccount handles Google vs password logic
        btnDeleteAccount.setOnClickListener(v ->
                startActivity(new Intent(PersonalInformation.this, DeleteAccount.class))
        );
    }

    private boolean isGoogleOnly(FirebaseUser user) {
        boolean hasGoogle = false;
        boolean hasPassword = false;

        for (UserInfo info : user.getProviderData()) {
            if ("google.com".equals(info.getProviderId())) hasGoogle = true;
            if ("password".equals(info.getProviderId())) hasPassword = true;
        }

        return hasGoogle && !hasPassword;
    }
}