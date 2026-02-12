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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class ScheduledTripAdapter extends RecyclerView.Adapter<ScheduledTripAdapter.VH> {

    public interface OnTripClick {
        void onClick(ScheduledTrip trip);
    }

    private final Context context;
    private final List<ScheduledTrip> list;
    private final OnTripClick listener;

    // ✅ Force PH timezone
    private static final TimeZone PH_TZ = TimeZone.getTimeZone("Asia/Manila");

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

        h.tvTripName.setText(!TextUtils.isEmpty(t.trip_name) ? t.trip_name : "Trip");
        h.tvLocation.setText(!TextUtils.isEmpty(t.location) ? t.location : "—");

        String when = "";
        if (!TextUtils.isEmpty(t.scheduled_date)) when += t.scheduled_date;

        if (!TextUtils.isEmpty(t.first_destination_time)) {
            when += (when.isEmpty() ? "" : " • ") + t.first_destination_time;
        }

        h.tvScheduledWhen.setText(!when.isEmpty() ? ("Scheduled: " + when) : "Scheduled: —");

        // ✅ STRICT OVERDUE (exact date+time only, PH timezone safe)
        boolean overdue = isOverdue(t);
        h.tvOverdue.setVisibility(overdue ? View.VISIBLE : View.GONE);

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

    // ✅ OVERDUE ONLY IF PAST EXACT DATE + TIME (PH)
    private boolean isOverdue(ScheduledTrip t) {
        long now = System.currentTimeMillis();

        // 1) best: already computed exact millis
        if (t.scheduled_sort_millis != null && t.scheduled_sort_millis > 0) {
            return now > t.scheduled_sort_millis;
        }

        // 2) compute from date + time (STRICT: both required)
        if (!TextUtils.isEmpty(t.scheduled_date) && !TextUtils.isEmpty(t.first_destination_time)) {
            long computed = parseDateTimeMillisPH(t.scheduled_date, t.first_destination_time);
            return computed > 0 && now > computed;
        }

        // 3) if time missing -> DO NOT mark overdue
        return false;
    }

    // Combine "yyyy-MM-dd" + "hh:mm a" using PH timezone
    private long parseDateTimeMillisPH(String dateStr, String timeStr) {
        if (TextUtils.isEmpty(dateStr) || TextUtils.isEmpty(timeStr)) return -1;

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            dateFormat.setLenient(false);
            dateFormat.setTimeZone(PH_TZ);

            Date date = dateFormat.parse(dateStr);
            if (date == null) return -1;

            Calendar cal = Calendar.getInstance(PH_TZ);
            cal.setTime(date);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            timeFormat.setLenient(false);
            timeFormat.setTimeZone(PH_TZ);

            Date time = timeFormat.parse(timeStr.trim());
            if (time == null) return -1;

            Calendar timeCal = Calendar.getInstance(PH_TZ);
            timeCal.setTime(time);

            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));

            return cal.getTimeInMillis();

        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgCover;
        TextView tvTripName, tvScheduledWhen, tvLocation, tvOverdue;

        VH(@NonNull View itemView) {
            super(itemView);
            imgCover = itemView.findViewById(R.id.imgCover);
            tvTripName = itemView.findViewById(R.id.tvTripName);
            tvScheduledWhen = itemView.findViewById(R.id.tvScheduledWhen);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvOverdue = itemView.findViewById(R.id.tvOverdue);
        }
    }
}
