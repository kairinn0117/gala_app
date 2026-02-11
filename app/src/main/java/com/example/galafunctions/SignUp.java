package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

public class SignUp extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private GoogleSignInClient googleSignInClient;

    private EditText usernameField, emailField, passField, confirmField;
    private Button btnSignUp, btnGoogle;
    private TextView goLogin;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (data == null) {
                    Toast.makeText(this, "Google sign-up cancelled.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    if (account != null && account.getIdToken() != null) {
                        firebaseAuthWithGoogle(account.getIdToken());
                    } else {
                        Toast.makeText(this, "Google token missing.", Toast.LENGTH_SHORT).show();
                    }
                } catch (ApiException e) {
                    Toast.makeText(this, "Google sign-up failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();

        // Views
        usernameField = findViewById(R.id.createUser_field);
        emailField = findViewById(R.id.email_field);
        passField = findViewById(R.id.setPass_field);
        confirmField = findViewById(R.id.confirmPass_field);

        btnSignUp = findViewById(R.id.signup_btn);
        btnGoogle = findViewById(R.id.signup_google);
        goLogin = findViewById(R.id.textView3);

        // Text click -> Login
        goLogin.setOnClickListener(v -> {
            startActivity(new Intent(SignUp.this, Login.class));
            finish();
        });

        // Email/Password signup
        btnSignUp.setOnClickListener(v -> signUpWithEmailPassword());

        // Google setup
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogle.setOnClickListener(v ->
                googleLauncher.launch(googleSignInClient.getSignInIntent())
        );
    }

    private void signUpWithEmailPassword() {
        String username = usernameField.getText().toString().trim();
        String email = emailField.getText().toString().trim();
        String pass = passField.getText().toString().trim();
        String confirm = confirmField.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(email) ||
                TextUtils.isEmpty(pass) || TextUtils.isEmpty(confirm)) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!pass.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(authResult -> {
                    // Save display name to FirebaseAuth user profile
                    if (mAuth.getCurrentUser() != null) {
                        UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                                .setDisplayName(username)
                                .build();
                        mAuth.getCurrentUser().updateProfile(req);
                    }

                    Toast.makeText(this, "Sign up successful! Please log in.", Toast.LENGTH_SHORT).show();

                    // Go back to Login after success
                    startActivity(new Intent(SignUp.this, Login.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Sign up failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);

        mAuth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    Toast.makeText(this, "Google sign up successful! Please log in.", Toast.LENGTH_SHORT).show();

                    // Optional: sign out after google sign up so they "log in again" (your requested flow)
                    mAuth.signOut();
                    googleSignInClient.signOut();

                    startActivity(new Intent(SignUp.this, Login.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Google sign up failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}