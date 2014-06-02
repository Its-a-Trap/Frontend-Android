package com.example.itsatrap.app;

/**
 * Created by maegereg on 6/1/14.
 * Modified version of the standard android ArcShape class
 * Now has getters and setters to enable animation
 */
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.shapes.RectShape;

/**
 * Creates an arc shape. The arc shape starts at a specified
 * angle and sweeps clockwise, drawing slices of pie.
 * The arc can be drawn to a Canvas with its own draw() method,
 * but more graphical control is available if you instead pass
 * the ArcShape to a {@link android.graphics.drawable.ShapeDrawable}.
 */
public class VariableArcShape extends RectShape {
    protected float mStart;
    protected float mSweep;
    protected boolean isSet;
    protected int width;
    protected int height;
    /**
     * ArcShape constructor.
     *
     * @param startAngle the angle (in degrees) where the arc begins
     * @param sweepAngle the sweep angle (in degrees). Anything equal to or
     * greater than 360 results in a complete circle/oval.
     */
    public VariableArcShape(float startAngle, float sweepAngle, int width, int height) {
        mStart = startAngle;
        mSweep = sweepAngle;
        isSet = false;
        this.width = width;
        this.height = height;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (!isSet)
        {
            int leftClip = (canvas.getWidth() - width);
            int rightClip = canvas.getWidth() - leftClip;
            int topClip = canvas.getHeight() - height;
            int bottomClip = canvas.getHeight() - topClip;
            canvas.clipRect(leftClip, topClip, rightClip, bottomClip);
        }
        canvas.drawArc(rect(), mStart, mSweep, true, paint);
    }

    public void setStartAngle(float newStartAngle)
    {
        mStart = newStartAngle;
    }

    public void setSweepAngle(float newSweepAngle)
    {
        mSweep = newSweepAngle;
    }

    public float getStartAngle()
    {
        return mStart;
    }

    public float getSweepAngle()
    {
        return mSweep;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public void setHeight(int height)
    {
        this.height = height;
    }
}

