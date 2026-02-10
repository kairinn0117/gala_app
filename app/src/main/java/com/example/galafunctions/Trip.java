package com.example.galafunctions;

public class Trip {
    public String tripId;

    public String trip_name;
    public String location;

    // kept for old compatibility (pwede blank na)
    public String date;
    public String time;

    public String cover_url;

    // PLANNED / SCHEDULED / IN_PROGRESS / ARCHIVED
    public String status;

    public Boolean is_template;

    // ✅ add this kasi ginagamit sa search at UI
    public String trip_category;

    // ✅ for archive logic
    public Boolean is_archived;

    // optional (if you want later)
    public Boolean budget_enabled;
    public Double trip_budget;
    public Double total_spent;

    public Trip() {}
}