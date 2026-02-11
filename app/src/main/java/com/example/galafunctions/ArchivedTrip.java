package com.example.galafunctions;

import com.google.firebase.Timestamp;

public class ArchivedTrip {
    public String tripId;
    public String trip_name;
    public String location;
    public String trip_category;
    public String status;
    public String cover_url;
    public Boolean budget_enabled;
    public Double trip_budget;

    public Timestamp archived_at;

    public ArchivedTrip() {}
}