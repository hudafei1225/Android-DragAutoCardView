package com.wenchao.cardstack;


import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

public class MyLinearLayout extends LinearLayout {


    float downX;

    public MyLinearLayout(Context context) {
        super(context);
    }

    public MyLinearLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public MyLinearLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();

                break;

            case MotionEvent.ACTION_UP:
                float upX = event.getX();
                if (Math.abs(upX - downX) < 5) {

                    return super.onTouchEvent(event);
                }else {
                    getParent().requestDisallowInterceptTouchEvent(true);

                }
                break;
        }
        return super.onTouchEvent(event);
    }
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev)
    {
        onTouchEvent(ev);
        return false;
    }
}
