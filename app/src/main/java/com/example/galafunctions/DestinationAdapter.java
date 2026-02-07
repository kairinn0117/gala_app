package com.example.galafunctions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DestinationAdapter extends RecyclerView.Adapter<DestinationAdapter.DestVH> {

    private final List<Destination> list;

    public DestinationAdapter(List<Destination> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public DestVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cardview_destination, parent, false);
        return new DestVH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DestVH holder, int position) {
        Destination d = list.get(position);

        holder.name.setText(d.destination_name != null ? d.destination_name : "");
        holder.location.setText(d.location != null ? d.location : "");
        holder.status.setText(d.status != null ? d.status : "PENDING");
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class DestVH extends RecyclerView.ViewHolder {
        TextView name, location, status;

        DestVH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvDestinationName);
            location = itemView.findViewById(R.id.tvDestinationLocation);
            status = itemView.findViewById(R.id.tvDestinationStatus);
        }
    }
}