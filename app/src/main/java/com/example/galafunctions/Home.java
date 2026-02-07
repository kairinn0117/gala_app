package com.example.galafunctions;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 * Use the {@link Home#newInstance} factory method to
 * create an instance of this fragment.
 */
public class Home extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // ✅ RecyclerView stuff
    private RecyclerView rvTrips;
    private TripAdapter tripAdapter;
    private ArrayList<Trip> tripList = new ArrayList<>();
    private TextView tvEmptyTrips; // optional (only works if you add it in XML)

    // ✅ Firebase
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public Home() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment Home.
     */
    // TODO: Rename and change types and number of parameters
    public static Home newInstance(String param1, String param2) {
        Home fragment = new Home();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        FloatingActionButton fabAddGala = view.findViewById(R.id.fabAddGala);
        if (fabAddGala != null) {
            fabAddGala.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), CreateTrip.class);
                startActivity(intent);
            });
        }

        // ✅ RecyclerView bind (make sure fragment_home.xml has rvTrips)
        rvTrips = view.findViewById(R.id.rvTrips);

        if (rvTrips != null) {
            rvTrips.setLayoutManager(new LinearLayoutManager(getContext()));
            tripAdapter = new TripAdapter(getContext(), tripList);
            rvTrips.setAdapter(tripAdapter);
        }

        // Optional empty state text (only if you add tvEmptyTrips in fragment_home.xml)
        tvEmptyTrips = view.findViewById(R.id.tvEmptyTrips);

        // ✅ Load trips
        loadTrips();

        return view;
    }
    private void loadTrips() {
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(uid)
                .collection("trips")
                .orderBy("created_at") // remove this line if you get index error
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots == null) return;

                    tripList.clear();

                    for (DocumentSnapshot doc : snapshots) {
                        Trip trip = doc.toObject(Trip.class);
                        if (trip != null) {
                            trip.tripId = doc.getId(); // IMPORTANT for opening TripActivity
                            tripList.add(trip);
                        }
                    }

                    if (tripAdapter != null) {
                        tripAdapter.notifyDataSetChanged();
                    }

                    // Optional empty state
                    if (tvEmptyTrips != null) {
                        tvEmptyTrips.setVisibility(tripList.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
    }

}