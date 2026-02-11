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

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripVH> {

    private final Context context;
    private final List<Trip> trips;

    public TripAdapter(Context context, List<Trip> trips) {
        this.context = context;
        this.trips = trips;
    }

    @NonNull
    @Override
    public TripVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.cardview_gala, parent, false);
        return new TripVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripVH holder, int position) {
        Trip trip = trips.get(position);

        holder.galaTitle.setText(trip.trip_name != null ? trip.trip_name : "");

        String date = trip.date != null ? trip.date : "";
        String time = trip.time != null ? trip.time : "";

        String dt = "";
        if (!TextUtils.isEmpty(date)) dt += date;
        if (!TextUtils.isEmpty(time)) dt += (dt.isEmpty() ? "" : " • ") + time;

        holder.datetime.setText(dt.isEmpty() ? "—" : dt);
        holder.loc.setText(trip.location != null ? trip.location : "");

        // ✅ Cover (Glide)
        if (!TextUtils.isEmpty(trip.cover_url)) {
            Glide.with(holder.cover.getContext())
                    .load(trip.cover_url)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_broken_image_24)
                    .error(R.drawable.baseline_broken_image_24)
                    .into(holder.cover);
        } else {
            holder.cover.setImageResource(R.drawable.baseline_broken_image_24);
        }

        holder.itemView.setOnClickListener(v -> {
            if (trip.tripId == null) return;

            Intent intent = new Intent(context, TripActivity.class);
            intent.putExtra("tripId", trip.tripId);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return trips.size();
    }

    static class TripVH extends RecyclerView.ViewHolder {
        TextView galaTitle, datetime, loc;
        ImageView cover;

        TripVH(@NonNull View itemView) {
            super(itemView);
            galaTitle = itemView.findViewById(R.id.galaTitle);
            datetime = itemView.findViewById(R.id.datetime_txt);
            loc = itemView.findViewById(R.id.loc_txt);
            cover = itemView.findViewById(R.id.imageView2);
        }
    }
}