package com.example.galafunctions;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DestinationSummaryAdapter extends RecyclerView.Adapter<DestinationSummaryAdapter.VH> {

    public interface OnDestinationClick {
        void onClick(Destination dest);
    }

    private final Context context;
    private final List<Destination> list;
    private final OnDestinationClick onClick;

    public DestinationSummaryAdapter(Context context, List<Destination> list, OnDestinationClick onClick) {
        this.context = context;
        this.list = list;
        this.onClick = onClick;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.card_destination_summary, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Destination d = list.get(position);

        String name = !TextUtils.isEmpty(d.destination_name) ? d.destination_name : "Destination";
        String time = !TextUtils.isEmpty(d.time) ? d.time : "—";
        String status = !TextUtils.isEmpty(d.status) ? d.status : "—";

        double spent = (d.spent_total != null) ? d.spent_total : 0.0;

        h.tvName.setText(name);
        h.tvLine1.setText("Time: " + time + " • " + status);
        h.tvLine2.setText("Spent: ₱" + String.format("%.2f", spent));

        h.itemView.setOnClickListener(v -> {
            if (onClick != null) onClick.onClick(d);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvLine1, tvLine2;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvLine1 = itemView.findViewById(R.id.tvLine1);
            tvLine2 = itemView.findViewById(R.id.tvLine2);
        }
    }
}