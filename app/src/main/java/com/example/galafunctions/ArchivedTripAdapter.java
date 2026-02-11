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

import java.util.ArrayList;

public class ArchivedTripAdapter extends RecyclerView.Adapter<ArchivedTripAdapter.VH> {

    public interface OnArchivedTripClick {
        void onClick(ArchivedTrip trip);
        void onLongPress(ArchivedTrip trip);
    }

    private final Context context;
    private final ArrayList<ArchivedTrip> list;
    private final OnArchivedTripClick listener;

    public ArchivedTripAdapter(Context context, ArrayList<ArchivedTrip> list, OnArchivedTripClick listener) {
        this.context = context;
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_archived_trip, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        ArchivedTrip t = list.get(position);
        if (t == null) return;

        h.tvName.setText(!TextUtils.isEmpty(t.trip_name) ? t.trip_name : "Trip");
        h.tvMeta.setText(buildMeta(t));

        if (!TextUtils.isEmpty(t.cover_url)) {
            h.imgCover.setVisibility(View.VISIBLE);
            Glide.with(h.imgCover.getContext()).load(t.cover_url).centerCrop().into(h.imgCover);
        } else {
            h.imgCover.setVisibility(View.GONE);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(t);
        });

        h.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongPress(t);
            return true;
        });
    }

    private String buildMeta(ArchivedTrip t) {
        String loc = !TextUtils.isEmpty(t.location) ? t.location : "—";
        String cat = !TextUtils.isEmpty(t.trip_category) ? t.trip_category : "—";
        return loc + " • " + cat;
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvName, tvMeta;

        VH(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.imgCover);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
        }
    }
}