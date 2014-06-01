package com.example.itsatrap.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class MapViewGroup extends ViewGroup {
    public MapViewGroup(Context context) {
        super(context);
    }

    public MapViewGroup(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MapViewGroup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int childCount = getChildCount();
        assert childCount == 1;
        getChildAt(0).layout(left, top, right, bottom);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }
}
