package com.example.galafunctions;

import android.content.Context;
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

public class ScheduledTripAdapter extends RecyclerView.Adapter<ScheduledTripAdapter.VH> {

    public interface OnTripClick {
        void onClick(ScheduledTrip trip);
    }

    private final Context context;
    private final List<ScheduledTrip> list;
    private final OnTripClick listener;

    public ScheduledTripAdapter(Context context, List<ScheduledTrip> list, OnTripClick listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_scheduled_trip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ScheduledTrip t = list.get(position);

        h.tvTripName.setText(t.trip_name != null ? t.trip_name : "Trip");

        String when = "";
        if (!TextUtils.isEmpty(t.scheduled_date)) when += t.scheduled_date;
        if (!TextUtils.isEmpty(t.scheduled_time)) when += (when.isEmpty() ? "" : " • ") + t.scheduled_time;
        h.tvScheduledWhen.setText(!when.isEmpty() ? ("Scheduled: " + when) : "Scheduled: —");

        h.tvLocation.setText(!TextUtils.isEmpty(t.location) ? t.location : "—");

        if (!TextUtils.isEmpty(t.cover_url)) {
            Glide.with(h.imgCover.getContext())
                    .load(t.cover_url)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_broken_image_24)
                    .error(R.drawable.baseline_broken_image_24)
                    .into(h.imgCover);
        } else {
            h.imgCover.setImageResource(R.drawable.baseline_broken_image_24);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(t);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTripName, tvScheduledWhen, tvLocation;

        VH(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.imgCover);
            tvTripName = itemView.findViewById(R.id.tvTripName);
            tvScheduledWhen = itemView.findViewById(R.id.tvScheduledWhen);
            tvLocation = itemView.findViewById(R.id.tvLocation);
        }
    }
}