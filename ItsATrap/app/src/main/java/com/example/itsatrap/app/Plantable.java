package com.example.itsatrap.app;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

/**
 * Created by maegereg on 5/10/14.
 */
public class Plantable{

    private String plantableId;
    private String ownerId;
    private LatLng location;
    private boolean isActive;
//    private Date plantTime;
//    //In seconds
//    private long duration;
//    //In meters
    private float radius;

    public Plantable(JSONObject plantable) throws JSONException {
        this.plantableId = plantable.getString("id");
        this.ownerId = plantable.getString("owner");
        this.isActive = true;
        JSONObject location = plantable.getJSONObject("location");
        this.location = new LatLng(location.getDouble("lat"), location.getDouble("lon"));


        this.radius = 2;
    }

    public Plantable(String plantableId, String ownerId, LatLng location, Date plantTime, long duration, float radius) {
        this.plantableId = plantableId;
        this.ownerId = ownerId;
        this.location = location;
//        this.plantTime = plantTime;
//        this.duration = duration;
        this.radius = 2;

        isActive = true;
    }

    public void setPlantableId(String id)
    {
        plantableId = id;
    }

    public void setActive(boolean isActive)
    {
        this.isActive = isActive;
    }

//    public void setDuration(long duration)
//    {
//        this.duration = duration;
//    }
//
//    public void setRadius(float radius)
//    {
//        this.radius = radius;
//    }

    public String getPlantableId()
    {
        return plantableId;
    }

    public String getOwnerId()
    {
        return ownerId;
    }

    public LatLng getLocation()
    {
        return location;
    }

    public boolean isActive()
    {
        return isActive;
    }

//    public Date getPlantTime()
//    {
//        return plantTime;
//    }

//    public long getDuration()
//    {
//        return duration;
//    }
//
    public float getRadius()
    {
        return radius;
    }

}
