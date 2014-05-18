package com.example.itsatrap.app;

/**
 * Created by maegereg on 5/10/14.
 */
public class User
{

    private String email;
    private int id;
    private String username;

    public User(String email)
    {
        this.email = email;
    }

    public User(String email, int id, String username)
    {
        this.email = email;
        this.id = id;
        this.username = username;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public int getId(int id)
    {
        return id;
    }

    public String getUsername()
    {
        return username;
    }

    public String getEmail()
    {
        return email;
    }
}
