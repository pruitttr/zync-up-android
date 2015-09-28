package com.zync_up.zyncup;

import android.animation.Animator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Interpolator;

import static com.zync_up.zyncup.HamburgerDrawable.DEFAULT_COLOR;
import static com.zync_up.zyncup.HamburgerDrawable.DEFAULT_PRESSED_DURATION;
import static com.zync_up.zyncup.HamburgerDrawable.DEFAULT_SCALE;
import static com.zync_up.zyncup.HamburgerDrawable.DEFAULT_TRANSFORM_DURATION;
import static com.zync_up.zyncup.HamburgerDrawable.IconState;
import static com.zync_up.zyncup.HamburgerDrawable.Stroke;

public class HamburgerView extends View implements HamburgerInterface
{

    private HamburgerDrawable drawable;

    private IconState currentState = IconState.BURGER;

    public HamburgerView(Context context)
    {
        this(context, null);
    }

    public HamburgerView(Context context, AttributeSet attrs)
    {
        this(context, attrs, 0);
    }

    public HamburgerView(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attributeSet)
    {
        TypedArray attr = getTypedArray(context, attributeSet, R.styleable.HamburgerView);

        try
        {
            int color = attr.getColor(R.styleable.HamburgerView_line_color, DEFAULT_COLOR);
            int scale = attr.getInteger(R.styleable.HamburgerView_scale, DEFAULT_SCALE);
            int transformDuration = attr.getInteger(R.styleable.HamburgerView_transformation_duration, DEFAULT_TRANSFORM_DURATION);
            int pressedDuration = attr.getInteger(R.styleable.HamburgerView_pressed_duration, DEFAULT_PRESSED_DURATION);
            Stroke stroke = Stroke.valueOf(attr.getInteger(R.styleable.HamburgerView_stroke_width, 0));
            boolean rtlEnabled = attr.getBoolean(R.styleable.HamburgerView_right_to_left, false);

            drawable = new HamburgerDrawable(context, color, stroke, scale, transformDuration, pressedDuration);
            drawable.setRTLEnabled(rtlEnabled);
        }
        finally
        {
            attr.recycle();
        }

        drawable.setCallback(this);
    }

    @Override
    public void draw(@NonNull Canvas canvas)
    {
        super.draw(canvas);
        if (getPaddingLeft() != 0 || getPaddingTop() != 0)
        {
            int saveCount = canvas.getSaveCount();
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            drawable.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
        else
        {
            drawable.draw(canvas);
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom)
    {
        super.setPadding(left, top, right, bottom);
        adjustDrawablePadding();
    }

    @Override
    protected boolean verifyDrawable(Drawable who)
    {
        return who == drawable || super.verifyDrawable(who);
    }

    @Override
    public void setState(IconState state)
    {
        currentState = state;
        drawable.setIconState(state);
    }

    @Override
    public IconState getState()
    {
        return drawable.getIconState();
    }

    @Override
    public void animateState(IconState state)
    {
        currentState = state;
        drawable.animateIconState(state, false);
    }

    @Override
    public void animatePressedState(IconState state)
    {
        currentState = state;
        drawable.animateIconState(state, true);
    }

    @Override
    public void setColor(int color)
    {
        drawable.setColor(color);
    }

    @Override
    public void setTransformationDuration(int duration)
    {
        drawable.setTransformationDuration(duration);
    }

    @Override
    public void setPressedDuration(int duration)
    {
        drawable.setPressedDuration(duration);
    }

    @Override
    public void setInterpolator(Interpolator interpolator)
    {
        drawable.setInterpolator(interpolator);
    }

    @Override
    public void setAnimationListener(Animator.AnimatorListener listener)
    {
        drawable.setAnimationListener(listener);
    }

    @Override
    public void setRTLEnabled(boolean rtlEnabled)
    {
        drawable.setRTLEnabled(rtlEnabled);
    }

    @Override
    public void setTransformationOffset(HamburgerDrawable.AnimationState animationState, float value)
    {
        currentState = drawable.setTransformationOffset(animationState, value);
    }

    @Override
    public HamburgerDrawable getDrawable()
    {
        return drawable;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        int paddingX = getPaddingLeft() + getPaddingRight();
        int paddingY = getPaddingTop() + getPaddingBottom();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(drawable.getIntrinsicWidth() + paddingX, MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(drawable.getIntrinsicHeight() + paddingY, MeasureSpec.EXACTLY);
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec);
        }
        else
        {
            setMeasuredDimension(drawable.getIntrinsicWidth() + paddingX, drawable.getIntrinsicHeight() + paddingY);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh)
    {
        super.onSizeChanged(w, h, oldw, oldh);
        adjustDrawablePadding();
    }

    @Override
    public Parcelable onSaveInstanceState()
    {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);
        savedState.state = currentState;
        return savedState;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state)
    {
        SavedState savedState = (SavedState) state;
        super.onRestoreInstanceState(savedState.getSuperState());
        setState(savedState.state);
    }

    private void adjustDrawablePadding()
    {
        if (drawable != null)
        {
            drawable.setBounds(
                0, 0,
                drawable.getIntrinsicWidth() + getPaddingLeft() + getPaddingRight(),
                drawable.getIntrinsicHeight() + getPaddingTop() + getPaddingBottom()
            );
        }
    }

    private TypedArray getTypedArray(Context context, AttributeSet attributeSet, int[] attr)
    {
        return context.obtainStyledAttributes(attributeSet, attr, 0, 0);
    }

    private static class SavedState extends BaseSavedState
    {
        protected IconState state;

        SavedState(Parcelable superState)
        {
            super(superState);
        }

        private SavedState(Parcel in)
        {
            super(in);
            state = IconState.valueOf(in.readString());
        }

        @Override
        public void writeToParcel(@NonNull Parcel out, int flags)
        {
            super.writeToParcel(out, flags);
            out.writeString(state.name());
        }

        public static final Creator<SavedState> CREATOR = new Creator<SavedState>()
        {
            @Override
            public SavedState createFromParcel(Parcel in)
            {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size)
            {
                return new SavedState[size];
            }
        };
    }
}
