package com.example.galafunctions;

public class ScheduledTrip {
    public String tripId;

    public String trip_name;
    public String location;
    public String cover_url;

    public String scheduled_date;     // e.g. "2026-02-12"
    public String scheduled_time;     // e.g. "09:00 AM"
    public Long scheduled_at_millis;  // used for sorting

    public ScheduledTrip() {}
}