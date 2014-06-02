package com.example.itsatrap.app;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by maegereg on 5/10/14.
 */
public class PlayerInfo{

    private String name;
    private int score;

    public PlayerInfo(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public PlayerInfo(JSONObject jsonObject) {
        try {
            this.name = jsonObject.getString("name");
            this.score = jsonObject.getInt("score");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getName()
    {
        return name;
    }

    public int getScore()
    {
        return score;
    }

    public void setScore(int newScore)
    {
        score = newScore;
    }


}
