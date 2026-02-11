package com.example.galafunctions;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class FinishedTripAdapter extends RecyclerView.Adapter<FinishedTripAdapter.VH> {

    private final Context context;
    private final List<FinishedTrip> trips;

    public FinishedTripAdapter(Context context, List<FinishedTrip> trips) {
        this.context = context;
        this.trips = trips;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.cardview_finished_trip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        FinishedTrip t = trips.get(position);

        h.title.setText(t.trip_name != null ? t.trip_name : "");
        h.location.setText(t.location != null ? t.location : "");
        h.category.setText(t.trip_category != null ? t.trip_category : "");

        // Prefer scheduled header if exists, else fallback to old date/time
        String date = !TextUtils.isEmpty(t.scheduled_date) ? t.scheduled_date : (t.date != null ? t.date : "");
        String time = !TextUtils.isEmpty(t.first_destination_time) ? t.first_destination_time : (t.time != null ? t.time : "");

        String dt = "";
        if (!TextUtils.isEmpty(date)) dt += date;
        if (!TextUtils.isEmpty(time)) dt += (dt.isEmpty() ? "" : " • ") + time;

        h.datetime.setText(dt.isEmpty() ? "—" : dt);

        if (!TextUtils.isEmpty(t.cover_url)) {
            Glide.with(h.cover.getContext())
                    .load(t.cover_url)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_broken_image_24)
                    .error(R.drawable.baseline_broken_image_24)
                    .into(h.cover);
        } else {
            h.cover.setImageResource(R.drawable.baseline_broken_image_24);
        }

        h.itemView.setOnClickListener(v -> {
            if (t.tripId == null) return;

            // ✅ go to destinations report viewer (READ ONLY)
            Intent i = new Intent(context, DestinationsReportActivity.class);
            i.putExtra("tripId", t.tripId);
            context.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, datetime, location, category;

        VH(@NonNull View itemView) {
            super(itemView);
            cover = itemView.findViewById(R.id.imgCoverFinished);
            title = itemView.findViewById(R.id.tvFinishedTitle);
            datetime = itemView.findViewById(R.id.tvFinishedDateTime);
            location = itemView.findViewById(R.id.tvFinishedLocation);
            category = itemView.findViewById(R.id.tvFinishedCategory);
        }
    }
}