package com.example.galafunctions;

import android.os.Bundle;
import android.util.Log;
import android.content.Intent;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

//        MaterialButton btnLogout = findViewById(R.id.logout_btn);
//
//        btnLogout.setOnClickListener(v -> logoutUser());


        FirebaseApp.initializeApp(this);
        try{
            FirebaseApp app = FirebaseApp.initializeApp(this);
            if (app != null){
                Log.d("FirebaseInit", "Firebase successfully connected" + app.getName());
            }else {
                Log.e("FirebaseInit", "Firebase initialization returned null.");
            }
        } catch (Exception e){
            Log.e("FirebaseInit","Firebase initialization failed:" + e.getMessage());
        }

//        GalaFragment gf1 = new GalaFragment();
//
//        getSupportFragmentManager().beginTransaction()
//                .replace(R.id.fragmentContainerView, gf1)
//                .commit();

        BottomNavigationView bnv1 = findViewById(R.id.bnv1);

        if (savedInstanceState == null){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainerView2, new Home())
                    .commit();
        }

        bnv1.setOnItemSelectedListener(item ->{
            if(item.getItemId() == R.id.home){
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Home.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack("name")
                        .commit();
                return true;
            }else if(item.getItemId() == R.id.map){
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Maps.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack("name")
                        .commit();
                return true;
            }else if(item.getItemId() == R.id.gallery){
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Gallery.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack("name")
                        .commit();
                return true;
            }else if(item.getItemId() == R.id.settings){
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView2, Settings.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack("name")
                        .commit();
                return true;
            }
            return false;
        });
    }

//    private void logoutUser() {
//
//        // 1. Firebase sign out
//        FirebaseAuth.getInstance().signOut();
//
//        // 2. Google sign out
//        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                .requestEmail()
//                .build();
//
//        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
//
//        googleSignInClient.signOut().addOnCompleteListener(task -> {
//            // 3. Go back to Login screen
//            Intent intent = new Intent(MainActivity.this, Login.class);
//            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//            startActivity(intent);
//            finish();
//        });
//    }
//
//    protected void onStart() {
//        super.onStart();
//        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
//            startActivity(new Intent(this, Login.class));
//            finish();
//        }
//    }

}