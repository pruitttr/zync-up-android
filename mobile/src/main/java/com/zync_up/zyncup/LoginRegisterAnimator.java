package com.zync_up.zyncup;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Matrix;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;

public class LoginRegisterAnimator extends GestureDetector.SimpleOnGestureListener
        implements View.OnTouchListener {

    private final String DEBUG_TAG = LoginRegisterAnimator.class.getSimpleName();
    private final int MINIMUM_DISTANCE = 800;

    private Context mContext;
    private CardView mFrontForm;
    private CardView mBackForm;
    private FrameLayout mFrame;

    private int yDelta;
    private float frontOriginalY;
    private float backOriginalY;
    private CardView.LayoutParams mBackFormParams;
    private CardView.LayoutParams mFrontFormParams;

    public LoginRegisterAnimator(Context context, CardView frontForm, CardView backForm, FrameLayout frame) {
        mContext = context;
        mFrontForm = frontForm;
        mBackForm = backForm;
        mFrame = frame;

        mFrontFormParams = (CardView.LayoutParams) frontForm.getLayoutParams();
        mBackFormParams = (CardView.LayoutParams) backForm.getLayoutParams();

        frontOriginalY = getRelativeTop(frontForm);
        backOriginalY = getRelativeTop(backForm);

    }

    @Override
    public boolean onDown(MotionEvent event) {
        Log.d(DEBUG_TAG,"onDown: " + event.toString());
        return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2,
                           float velocityX, float velocityY) {
        float distance = event2.getY() - event1.getY();
        return true;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        //mGestureDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_UP:
                flipCards();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                break;
            case MotionEvent.ACTION_POINTER_UP:
                break;
            case MotionEvent.ACTION_MOVE:
                break;
        }
        view.invalidate();
        return true;
    }

    private Context getContext(Context context) {
        return this.mContext = context;
    }

    private int getTopMargin(CardView.LayoutParams layoutParams) {
        return layoutParams.topMargin;
    }

    private CardView getBackForm(CardView backForm) {
        return this.mBackForm = backForm;
    }

    private int getRelativeTop(View myView) {
        if (myView.getParent() == myView.getRootView())
            return myView.getTop();
        else
            return myView.getTop() + getRelativeTop((View) myView.getParent());
    }

    private void flipCards() {

        FlipAnimation flipAnimation = new FlipAnimation(mFrontForm, mBackForm);

        if (mFrontForm.getVisibility() == View.GONE)
        {
            flipAnimation.reverse();
        }

        mFrontForm.startAnimation(flipAnimation);

    }

    public class FlipAnimation extends Animation {
        private Camera camera;

        private View fromView;
        private View toView;

        private float centerX;
        private float centerY;

        private boolean forward = true;

        /**
         * Creates a 3D flip animation between two views.
         *
         * @param fromView First view in the transition.
         * @param toView   Second view in the transition.
         */
        public FlipAnimation(CardView fromView, CardView toView) {
            this.fromView = fromView;
            this.toView = toView;

            setDuration(700);
            setFillAfter(false);
            setInterpolator(new AccelerateDecelerateInterpolator());
        }

        public void reverse() {
            forward = false;
            View switchView = toView;
            toView = fromView;
            fromView = switchView;
        }

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
            centerX = width / 2;
            centerY = height / 2;
            camera = new Camera();
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            // Angle around the y-axis of the rotation at the given time
            // calculated both in radians and degrees.
            final double radians = Math.PI * interpolatedTime;
            float degrees = (float) (180.0 * radians / Math.PI);

            // Once we reach the midpoint in the animation, we need to hide the
            // source view and show the destination view. We also need to change
            // the angle by 180 degrees so that the destination does not come in
            // flipped around
            if (interpolatedTime >= 0.5f) {
                degrees -= 180.f;
                fromView.setVisibility(View.GONE);
                toView.setVisibility(View.VISIBLE);
            }

            if (forward)
                degrees = -degrees; //determines direction of rotation when flip begins

            final Matrix matrix = t.getMatrix();
            camera.save();
            camera.rotateY(degrees);
            camera.getMatrix(matrix);
            camera.restore();
            matrix.preTranslate(-centerX, -centerY);
            matrix.postTranslate(centerX, centerY);
        }
    }

}
