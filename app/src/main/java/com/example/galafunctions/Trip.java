package com.example.galafunctions;

public class Trip {
    public String tripId;

    public String trip_name;
    public String location;

    public String date; // old
    public String time; // old

    public String cover_url;

    public String status;         // PLANNED / SCHEDULED / IN_PROGRESS / ARCHIVED
    public Boolean is_template;
    public String trip_category;
    public Boolean is_archived;

    public Boolean budget_enabled;
    public Double trip_budget;
    public Double total_spent;

    // ✅ schedule fields
    public String scheduled_date;               // set by PlanTrip
    public String first_destination_time;       // set by earliest destination
    public Long first_destination_start_millis; // set by earliest destination
    public Long scheduled_sort_millis;          // used for sorting scheduled list

    // keep for compatibility
    public String scheduled_time;
    public com.google.firebase.Timestamp ended_at;

    public Trip() {}
}