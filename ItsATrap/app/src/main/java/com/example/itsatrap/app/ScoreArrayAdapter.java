package com.example.itsatrap.app;

import android.app.Activity;
import android.content.Context;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/**
 * Created by maegereg on 5/10/14.
 */
public class ScoreArrayAdapter extends ArrayAdapter<PlayerInfo>
{

    private int resource;
    private Context context;
    private List<PlayerInfo> objects;

    public ScoreArrayAdapter(Context context, int resource, List<PlayerInfo> objects)
    {
        super(context, resource, objects);
        this.context = context;
        this.resource = resource;
        this.objects = objects;
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        View row = convertView;

        if (row == null)
        {
            LayoutInflater inflater = ((Activity)context).getLayoutInflater();
            row = inflater.inflate(resource, parent, false);
        }

        ((TextView)row.findViewById(R.id.drawer_name_text)).setText(objects.get(position).getName());
        ((TextView)row.findViewById(R.id.drawer_score_text)).setText(Integer.toString(objects.get(position).getScore()));

        return row;
    }
}
