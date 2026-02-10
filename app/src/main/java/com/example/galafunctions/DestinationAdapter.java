package com.example.galafunctions;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class DestinationAdapter extends RecyclerView.Adapter<DestinationAdapter.DestVH> {

    // 🔹 click listener interface
    public interface OnDestinationClickListener {
        void onDestinationClick(Destination destination);
    }

    private final List<Destination> list;
    private final OnDestinationClickListener listener;
    private final boolean budgetEnabled;

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

        // Name
        if (holder.name != null) {
            holder.name.setText(d.destination_name != null ? d.destination_name : "");
        }

        // Location
        if (holder.location != null) {
            holder.location.setText(d.location != null ? d.location : "");
        }

        // Status
        if (holder.status != null) {
            holder.status.setText(d.status != null ? d.status : "PENDING");
        }

        // ✅ Time (NEW) - if you add tvDestinationTime in XML
        if (holder.time != null) {
            if (d.time != null && !d.time.trim().isEmpty()) {
                holder.time.setVisibility(View.VISIBLE);
                holder.time.setText("Time: " + d.time);
            } else {
                holder.time.setVisibility(View.GONE);
            }
        }

        // ✅ Budget (optional)
        if (holder.budget != null) {
            if (budgetEnabled && d.budget != null) {
                holder.budget.setVisibility(View.VISIBLE);
                holder.budget.setText(String.format(Locale.getDefault(),
                        "Budget: ₱%.2f", d.budget));
            } else {
                holder.budget.setVisibility(View.GONE);
            }
        }

        // CLICK
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDestinationClick(d);
        });
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    static class DestVH extends RecyclerView.ViewHolder {

        TextView name, location, status;
        TextView time;   // ✅ NEW (optional)
        TextView budget; // optional

        DestVH(@NonNull View itemView) {
            super(itemView);

            name = itemView.findViewById(R.id.tvDestinationName);
            location = itemView.findViewById(R.id.tvDestinationLocation);
            status = itemView.findViewById(R.id.tvDestinationStatus);

            // ✅ Add this id in your cardview_destination.xml if you want it shown
            time = itemView.findViewById(R.id.tvDestinationTime);

            // optional — if not present in XML, magiging null lang
            budget = itemView.findViewById(R.id.tvDestinationBudget);
        }
    }
}