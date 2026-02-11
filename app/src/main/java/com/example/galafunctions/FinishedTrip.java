package com.example.galafunctions;

import com.google.firebase.Timestamp;

public class FinishedTrip {
    public String tripId;

    public String trip_name;
    public String location;
    public String cover_url;
    public String trip_category;

    // legacy
    public String date;
    public String time;

    // new schedule fields (optional display)
    public String scheduled_date;
    public String first_destination_time;

    // end/archived metadata (optional)
    public String status;
    public Boolean is_archived;

    public Timestamp ended_at;

    public FinishedTrip() {}
}