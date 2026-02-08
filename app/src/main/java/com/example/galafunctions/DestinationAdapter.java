package com.example.galafunctions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DestinationAdapter extends RecyclerView.Adapter<DestinationAdapter.DestVH> {

    // 🔹 click listener interface
    public interface OnDestinationClickListener {
        void onDestinationClick(Destination destination);
    }

    private final List<Destination> list;
    private final OnDestinationClickListener listener;
    private final boolean budgetEnabled; // optional, safe for future use

    // 🔹 constructor
    public DestinationAdapter(
            List<Destination> list,
            boolean budgetEnabled,
            OnDestinationClickListener listener
    ) {
        this.list = list;
        this.budgetEnabled = budgetEnabled;
        this.listener = listener;
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

        // 🔹 optional budget support (only show if enabled)
        if (holder.budget != null) {
            if (budgetEnabled && d.budget != null) {
                holder.budget.setVisibility(View.VISIBLE);
                holder.budget.setText("Budget: ₱" + String.format("%.2f", d.budget));
            } else {
                holder.budget.setVisibility(View.GONE);
            }
        }

        // 🔹 CLICK HANDLER
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDestinationClick(d);
            }
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class DestVH extends RecyclerView.ViewHolder {

        TextView name, location, status;
        TextView budget; // optional (safe even if not in layout)

        DestVH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvDestinationName);
            location = itemView.findViewById(R.id.tvDestinationLocation);
            status = itemView.findViewById(R.id.tvDestinationStatus);

            // optional — if not present in XML, magiging null lang
            budget = itemView.findViewById(R.id.tvDestinationBudget);
        }
    }
}