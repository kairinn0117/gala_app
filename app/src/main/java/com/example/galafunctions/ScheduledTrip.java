package com.example.galafunctions;

public class ScheduledTrip {
    public String tripId;

    public String trip_name;
    public String location;
    public String cover_url;

    public String scheduled_date;           // "2026-02-12"
    public String first_destination_time;   // "02:45 PM" (display)
    public Long scheduled_sort_millis;      // date + first destination time millis (sort)

    public ScheduledTrip() {}
}