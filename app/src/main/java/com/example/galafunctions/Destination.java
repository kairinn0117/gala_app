package com.example.galafunctions;

import com.google.firebase.Timestamp;

public class Destination {
    public String destinationId;

    public String destination_name;
    public String type;
    public String location;

    public String time;
    public Long start_time_millis;
    public Long end_time_millis;

    public Double lat;
    public Double lng;

    public Double budget;
    public Double spent_total;

    public String description;
    public String status;

    // ✅ ADD THIS (for StartGala uploaded photo)
    public String photo_url;

    // ✅ Optional but recommended (if you already store these)
    public Timestamp created_at;
    public Timestamp updated_at;

    public Destination() {}
}