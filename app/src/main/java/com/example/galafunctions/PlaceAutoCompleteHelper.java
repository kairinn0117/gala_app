package com.example.galafunctions;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.util.ArrayList;
import java.util.List;

public class PlaceAutoCompleteHelper {

    public interface OnPredictionClick {
        void onClick(AutocompletePrediction prediction);
    }

    private final Context context;
    private final EditText input;
    private final PlacesClient placesClient;
    private final OnPredictionClick callback;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long debounceMs = 300;

    private List<AutocompletePrediction> lastPredictions = new ArrayList<>();

    public PlaceAutoCompleteHelper(Context context, EditText input, PlacesClient placesClient, OnPredictionClick callback) {
        this.context = context;
        this.input = input;
        this.placesClient = placesClient;
        this.callback = callback;

        setup();
    }

    private void setup() {
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> fetchPredictions(s.toString()), debounceMs);
            }
        });
    }

    public void forceSearch() {
        fetchPredictions(input.getText().toString());
    }

    private void fetchPredictions(String query) {
        if (TextUtils.isEmpty(query) || query.trim().length() < 2) return;

        FindAutocompletePredictionsRequest request =
                FindAutocompletePredictionsRequest.builder()
                        .setQuery(query.trim())
                        .build();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    lastPredictions = response.getAutocompletePredictions();
                    if (lastPredictions == null || lastPredictions.isEmpty()) return;
                    showDialogSuggestions(lastPredictions);
                })
                .addOnFailureListener(e -> {
                    // silent fail (no spam toast)
                });
    }

    private void showDialogSuggestions(List<AutocompletePrediction> predictions) {
        ArrayList<String> display = new ArrayList<>();
        for (AutocompletePrediction p : predictions) {
            String primary = (p.getPrimaryText(null) != null) ? p.getPrimaryText(null).toString() : "";
            String secondary = (p.getSecondaryText(null) != null) ? p.getSecondaryText(null).toString() : "";
            if (!TextUtils.isEmpty(primary) && !TextUtils.isEmpty(secondary)) {
                display.add(primary + " • " + secondary);
            } else if (!TextUtils.isEmpty(primary)) {
                display.add(primary);
            } else {
                display.add(secondary);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, display);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setAdapter(adapter, (d, which) -> {
                    if (which >= 0 && which < predictions.size()) {
                        AutocompletePrediction chosen = predictions.get(which);

                        // set text to chosen primary
                        if (chosen.getPrimaryText(null) != null) {
                            input.setText(chosen.getPrimaryText(null).toString());
                            input.setSelection(input.getText().length());
                        }

                        callback.onClick(chosen);
                    }
                })
                .create();

        // Avoid showing multiple dialogs if user is typing fast
        if (!dialog.isShowing()) dialog.show();
    }
}