package com.wenchao.cardstack;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static com.wenchao.cardstack.CardUtils.DIRECTION_BOTTOM;


public class CardStack extends RelativeLayout {

    private  Context mContext;
    private boolean mEnableRotation;
    private int mGravity;
    private int mColor = -1;
    private int mIndex = 0;
    private int mNumVisible = 4;
    private boolean canSwipe = true;
    private ArrayAdapter<?> mAdapter;
    private OnTouchListener mOnTouchListener;
    private CardAnimator mCardAnimator;
    private boolean mEnableLoop = true;// 是否允许循环滚动


    private CardEventListener mEventListener = new DefaultStackEventListener(300);
    private int mContentResource = 0;
    private int mMargin;
    private int currentIndex;
    private float mTouchStartX;
    private float mTouchStartY;
    private float x;
    private float y;
    int state;
    private float StartX;
    private float StartY;
    private DragGestureDetector dd;

    public interface CardEventListener {
        //section
        // 0 | 1
        //--------
        // 2 | 3
        // swipe distance, most likely be used with height and width of a view ;

        boolean swipeEnd(int section, float distance);

        boolean swipeStart(int section, float distance);

        boolean swipeContinue(int section, float distanceX, float distanceY);

        void discarded(int mIndex, int direction);

        void topCardTapped();
    }

    public void discardTop(final int direction) {
        mCardAnimator.discard(direction, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator arg0) {
                mCardAnimator.initLayout();
                mIndex++;
                loadLast();

                viewCollection.get(0).setOnTouchListener(null);
                viewCollection.get(viewCollection.size() - 1).setOnTouchListener(mOnTouchListener);
                mEventListener.discarded(mIndex - 1, direction);
            }
        });
    }

    /**
     * 设置方向，支持上、下。
     * 设置后调用{@link #reset(boolean)} 来重新初始化布局
     *
     * @param gravity {@link CardAnimator#TOP} 向上 {@link CardAnimator#BOTTOM} 向下，默认值
     */
    public void setStackGravity(int gravity) {
        mGravity = gravity;
    }

    /**
     * 获取当前方向
     *
     * @return
     */
    public int getStackGravity() {
        return mGravity;
    }

    /**
     * 是否允许旋转
     * <p/>
     * 设置后调用{@link #reset(boolean)} 来重新初始化布局
     *
     * @param enableRotation
     */
    public void setEnableRotation(boolean enableRotation) {
        mEnableRotation = enableRotation;
    }

    /**
     * 是否循环滚动
     * 设置后调用{@link #reset(boolean)} 来重新初始化布局
     *
     * @param enableLoop
     */
    public void setEnableLoop(boolean enableLoop) {
        mEnableLoop = enableLoop;
    }

    /**
     * 是否允许旋转
     *
     * @return
     */
    public boolean isEnableRotation() {
        return mEnableRotation;
    }

    /**
     * 是否循环滚动
     *
     * @return
     */
    public boolean isEnableLoop() {
        return mEnableLoop;
    }

    public int getCurrIndex() {
        //sync?
        return mIndex;
    }

    //only necessary when I need the attrs from xml, this will be used when inflating layout
    public CardStack(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.CardStack);

            mColor = array.getColor(R.styleable.CardStack_card_backgroundColor, mColor);
            mGravity = array.getInteger(R.styleable.CardStack_card_gravity, Gravity.BOTTOM);
            mEnableRotation = array.getBoolean(R.styleable.CardStack_card_enable_rotation, false);
            mNumVisible = array.getInteger(R.styleable.CardStack_card_stack_size, mNumVisible);
            mEnableLoop = array.getBoolean(R.styleable.CardStack_card_enable_loop, mEnableLoop);
            mMargin = array.getDimensionPixelOffset(R.styleable.CardStack_card_margin, 5);
            array.recycle();
        }

        //get attrs assign minVisiableNum
        for (int i = 0; i < mNumVisible; i++) {
            addContainerViews(false);
        }
        setupAnimation();
    }

    private void addContainerViews(boolean anim) {
        FrameLayout v = new FrameLayout(getContext());
        viewCollection.add(v);
        addView(v);
        if (anim) {
            Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.undo_anim);
            v.startAnimation(animation);
        }
    }

    public void setStackMargin(int margin) {
        mMargin = margin;
        mCardAnimator.setStackMargin(mMargin);
        mCardAnimator.initLayout();
    }

    public int getStackMargin() {
        return mMargin;
    }


    public void setContentResource(int res) {
        mContentResource = res;
    }

    public void setCanSwipe(boolean can) {
        this.canSwipe = can;
    }

    public void reset(boolean resetIndex) {
        reset(true, false);
    }

    private void reset(boolean resetIndex, boolean animFirst) {
        if (resetIndex) mIndex = 0;
        removeAllViews();
        viewCollection.clear();
        for (int i = 0; i < mNumVisible; i++) {
            addContainerViews(i == mNumVisible - 1 && animFirst);
        }
        setupAnimation();
        loadData();
    }

    public void setVisibleCardNum(int visiableNum) {
        mNumVisible = visiableNum;
        if (mNumVisible >= mAdapter.getCount()) {
            mNumVisible = mAdapter.getCount();
        }
        reset(false);
    }

    public void setThreshold(int t) {
        mEventListener = new DefaultStackEventListener(t);
    }

    public void setListener(CardEventListener cel) {
        mEventListener = cel;
    }

    private void setupAnimation() {
        final View cardView = viewCollection.get(viewCollection.size() - 1);
        mCardAnimator = new CardAnimator(viewCollection, mColor, mMargin);
        mCardAnimator.setGravity(mGravity);
        mCardAnimator.setEnableRotation(mEnableRotation);
        //mCardAnimator.setStackMargin(mMargin);
        mCardAnimator.initLayout();


//        mOnTouchListener = new OnTouchListener() {
//
//            private static final String DEBUG_TAG = "MotionEvents";
//
//            @Override
//            public boolean onTouch(View view, MotionEvent event) {
//                x = event.getRawX();
//                y = event.getRawY() - 25; // 25是系统状态栏的高度
//                Log.i("currP", "currX" + x + "====currY" + y);// 调试信息
//                switch (event.getAction()) {
//                    case MotionEvent.ACTION_DOWN:
//                        state = MotionEvent.ACTION_DOWN;
//                        StartX = x;
//                        StartY = y;
//                        // 获取相对View的坐标，即以此View左上角为原点
//                        mTouchStartX = event.getX();
//                        mTouchStartY = event.getY();
//                        Log.i("startP", "startX" + mTouchStartX + "====startY"
//                                + mTouchStartY);// 调试信息
//
//                        break;
//                    case MotionEvent.ACTION_MOVE:
//                        state = MotionEvent.ACTION_MOVE;
////                        updateViewPosition();
//                        break;
//
//                    case MotionEvent.ACTION_UP:
//                        state = MotionEvent.ACTION_UP;
//
////                        updateViewPosition();
//                        //关键部分：移动距离较小，视为onclick点击行为
//                        if (Math.abs(x - StartX) <= 2 && Math.abs(y - StartY) <= 2){
//                            return CardStack.super.onTouchEvent(event);
//                        }
//                        mTouchStartX = mTouchStartY = 0;
//                        break;
//                }
//                dd.onTouchEvent(event);
//                return true;
//            }
//
//        };
//        cardView.setOnTouchListener(mOnTouchListener);

    }

    float downX;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return false;
    }

    boolean isOne = false;

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        x = event.getRawX();
        y = event.getRawY() - 25; // 25是系统状态栏的高度
        Log.i("currP", "currX" + x + "====currY" + y);// 调试信息
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                state = MotionEvent.ACTION_DOWN;
                StartX = x;
                StartY = y;
                // 获取相对View的坐标，即以此View左上角为原点
                mTouchStartX = event.getX();
                mTouchStartY = event.getY();
                Log.i("startP", "startX" + mTouchStartX + "====startY"
                        + mTouchStartY);// 调试信息

                break;
            case MotionEvent.ACTION_MOVE:
                state = MotionEvent.ACTION_MOVE;
//                        updateViewPosition();
                break;

            case MotionEvent.ACTION_UP:
                state = MotionEvent.ACTION_UP;

//                        updateViewPosition();
                //关键部分：移动距离较小，视为onclick点击行为
                if (Math.abs(x - StartX) <= 5 && Math.abs(y - StartY) <= 5 || mAdapter.getCount() <= 1) {
                    int position = getCurrIndex() % mAdapter.getCount();
                    Toast.makeText(mContext, "点击了 ：" + position, Toast.LENGTH_SHORT).show();
                    return true;
                }

        }
        dd.onTouchEvent(event);
        return true;
    }
    OnClickListener clickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {

        }
    };

    private DataSetObserver mOb = new DataSetObserver() {
        @Override
        public void onChanged() {
            reset(false);
        }
    };


    //ArrayList

    ArrayList<View> viewCollection = new ArrayList<View>();

    public CardStack(Context context) {
        super(context);
    }

    public void setAdapter(final ArrayAdapter<?> adapter) {
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mOb);
        }
        mAdapter = adapter;
        adapter.registerDataSetObserver(mOb);
        dd = new DragGestureDetector(mAdapter,CardStack.this.getContext(), new DragGestureDetector.DragListener() {

            @Override
            public boolean onDragStart(MotionEvent e1, MotionEvent e2,
                                       float distanceX, float distanceY) {
                getParent().requestDisallowInterceptTouchEvent(true);
                if (canSwipe) {
                    mCardAnimator.drag(e1, e2, distanceX, distanceY);
                }
                stopTimer();
                float x1 = e1.getRawX();
                float y1 = e1.getRawY();
                float x2 = e2.getRawX();
                float y2 = e2.getRawY();
                final int direction = CardUtils.direction(x1, y1, x2, y2);
                float distance = CardUtils.distance(x1, y1, x2, y2);
                mEventListener.swipeStart(direction, distance);
                return true;
            }

            @Override
            public boolean onDragContinue(MotionEvent e1, MotionEvent e2,
                                          float distanceX, float distanceY) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float x1 = e1.getRawX();
                float y1 = e1.getRawY();
                float x2 = e2.getRawX();
                float y2 = e2.getRawY();
                final int direction = CardUtils.direction(x1, y1, x2, y2);
                if (canSwipe) {
                    mCardAnimator.drag(e1, e2, distanceX, distanceY);
                }
                mEventListener.swipeContinue(direction, Math.abs(x2 - x1), Math.abs(y2 - y1));
                return true;
            }

            @Override
            public boolean onDragEnd(MotionEvent e1, MotionEvent e2) {
                //reverse(e1,e2);
                getParent().requestDisallowInterceptTouchEvent(true);
                float x1 = e1.getRawX();
                float y1 = e1.getRawY();
                float x2 = e2.getRawX();
                float y2 = e2.getRawY();
                float distance = CardUtils.distance(x1, y1, x2, y2);
                final int direction = CardUtils.direction(x1, y1, x2, y2);

                boolean discard = mEventListener.swipeEnd(direction, distance);
                if (discard) {
                    if (canSwipe) {
                        mCardAnimator.discard(direction, new AnimatorListenerAdapter() {

                            @Override
                            public void onAnimationEnd(Animator arg0) {

                                mCardAnimator.initLayout();
                                mIndex++;
                                mEventListener.discarded(mIndex, direction);
//
                                //mIndex = mIndex%mAdapter.getCount();
                                loadLast();
                                startTimer();
//                                viewCollection.get(0).setOnTouchListener(null);
//                                viewCollection.get(viewCollection.size() - 1)
//                                        .setOnTouchListener(mOnTouchListener);
                            }

                        });
                    }
                } else {
                    if (canSwipe) {

                        mCardAnimator.reverse(e1, e2);
                    }
                }
                return true;
            }

            @Override
            public boolean onTapUp() {
                mEventListener.topCardTapped();
                return true;
            }
        }
        );
        loadData();
    }


    public ArrayAdapter getAdapter() {
        return mAdapter;
    }

    public View getTopView() {
        return ((ViewGroup) viewCollection.get(viewCollection.size() - 1)).getChildAt(0);
    }

    private void loadData() {
        for (int i = mNumVisible - 1; i >= 0; i--) {
            ViewGroup parent = (ViewGroup) viewCollection.get(i);
            int index = (mIndex + mNumVisible - 1) - i;
            if (index > mAdapter.getCount() - 1) {
                parent.setVisibility(View.GONE);

            } else {
                View child = mAdapter.getView(index, getContentView(), this);
                parent.addView(child);
                parent.setVisibility(View.VISIBLE);
            }
        }
    }

    private View getContentView() {
        View contentView = null;
        if (mContentResource != 0) {
            LayoutInflater lf = LayoutInflater.from(getContext());
            contentView = lf.inflate(mContentResource, null);
        }
        return contentView;

    }

    // 加载下一个
    private void loadLast() {
        ViewGroup parent = (ViewGroup) viewCollection.get(0);
        int lastIndex = ((mNumVisible - 1) + mIndex);

        // 超出索引
        if (lastIndex > mAdapter.getCount() - 1) {
            if (mEnableLoop && mAdapter.getCount() > 0) {
                // 循环处理
                lastIndex = lastIndex % mAdapter.getCount();
            } else {
                parent.setVisibility(View.GONE);
                return;
            }
        }

        View child = mAdapter.getView(lastIndex, getContentView(), parent);
        parent.removeAllViews();
        parent.addView(child);
    }

    // 加载下一个
    private void loadAnimaLast() {
        ViewGroup parent = (ViewGroup) viewCollection.get(0);
        int lastIndex = ((mNumVisible - 1) + mCurrent);

        // 超出索引
        if (lastIndex > mAdapter.getCount() - 1) {
            if (mEnableLoop) {
                // 循环处理
                lastIndex = lastIndex % mAdapter.getCount();
            } else {
                parent.setVisibility(View.GONE);
                return;
            }
        }

        View child = mAdapter.getView(lastIndex, getContentView(), parent);
        parent.removeAllViews();
        parent.addView(child);
    }

    /**
     * 获取可见卡片个数
     *
     * @return
     */
    public int getVisibleCardNum() {
        return mNumVisible;
    }

    public void undo() {
        if (mIndex == 0) return;
        mIndex --;
        reset(false, true);
    }
     int mCurrent = viewCollection.size() - 1;
    public void exitWithAnimation() {
        final View childView = getChildAt(mCurrent);
        final int direction = CardUtils.direction(0, 200, 0, 500);
        float distance = CardUtils.distance(0, 200, 0, 200);
//        mEventListener.swipeStart(direction, distance);
        animationSet = new AnimatorSet();
        animationSet.setInterpolator(new AccelerateInterpolator());


        final float translationY = childView.getTranslationY();
        animationSet.playTogether(
                        ObjectAnimator.ofFloat(childView, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(childView, "translationY", translationY, childView.getTranslationY() + 150 * (float) getsinValue(0,
                            100)));

            animationSet.setDuration(800).start();

            animationSet.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {

                    mCardAnimator.discard(DIRECTION_BOTTOM, new AnimatorListenerAdapter() {

                        @Override
                        public void onAnimationEnd(Animator arg0) {
                            childView.setTranslationY(translationY);
                            childView.setAlpha(1);
                            mCardAnimator.initLayout();
                            mIndex++;
                            mEventListener.discarded(mIndex, DIRECTION_BOTTOM);
//
                            //mIndex = mIndex%mAdapter.getCount();
                            loadLast();
//
//                                viewCollection.get(0).setOnTouchListener(null);
//                                viewCollection.get(viewCollection.size() - 1)
//                                        .setOnTouchListener(mOnTouchListener);
                        }

                    });
//                        mCardAnimator.initLayout();
//                        mEventListener.discarded(mCurrent, DIRECTION_BOTTOM_LEFT);
//                        mCurrent--;
//                        //mIndex = mIndex%mAdapter.getCount();
//                        loadLast();
//
//                        viewCollection.get(0).setOnTouchListener(null);
//                        viewCollection.get(viewCollection.size() - 1)
//                                .setOnTouchListener(mOnTouchListener);


                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });


    }
    private StackRunnable mRunnable;
    private AnimatorSet  animationSet;
    boolean mIsAnimation;

    private class StackRunnable implements Runnable {

        @Override
        public void run() {
            exitWithAnimation();
        }
    }
    private Handler mHandler = new Handler(Looper.getMainLooper());

    private double getsinValue(float x, float y) {
        return y / Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
    }
    public void startLoop() {


        if (mRunnable != null || mIsAnimation) {
            return;
        }
        mRunnable = new StackRunnable();
        exitWithAnimation();
        mIsAnimation = true;
    }

    public void stopLoop() {

        mRunnable = null;
        mHandler.removeCallbacks(null);
        if(animationSet != null) {
            animationSet.cancel();
        }
        mIsAnimation = false;
    }


    Timer timer; // 定时器
    // 停止滚动
    public void stopTimer() {
        Log.d("", "stopTimer");
        if (timer != null) {
            timer.cancel();
            if(animationSet != null) {
                animationSet.cancel();
            }

            timer = null;
        }
    }

    // 开始滚动
    public void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                ((Activity)mContext).runOnUiThread(new Runnable() {
                    public void run() {
                        for (int index = 0; index < viewCollection.size(); index++) {
                            mCurrent = index;
                        }
                        exitWithAnimation();
                    }
                });
            }
        }, 1000, 3000);
    }

}
