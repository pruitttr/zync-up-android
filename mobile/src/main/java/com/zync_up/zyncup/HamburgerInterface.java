package com.zync_up.zyncup;

import android.animation.Animator;
import android.view.animation.Interpolator;

import static com.zync_up.zyncup.HamburgerDrawable.IconState;

public interface HamburgerInterface
{

    //Set state without animation
    void setState(IconState state);

    //gets state
    IconState getState();

    //Set state with animation
    void animateState(IconState state);

    //Set state with animation and draw touch circle
    void animatePressedState(IconState state);

    //Set color of icon
    void setColor(int color);

    //Set duration of animation
    void setTransformationDuration(int duration);

    //Set duration of touch circle
    void setPressedDuration(int duration);

    //Set animation interpolator
    void setInterpolator(Interpolator interpolator);

    //Set animation listener
    void setAnimationListener(Animator.AnimatorListener listener);

    //Enable Right to Left Layout
    void setRTLEnabled(boolean rtlEnabled);

    //Set current state
    void setTransformationOffset(HamburgerDrawable.AnimationState animationState, float value);

    //Return HamburgerDrawable
    HamburgerDrawable getDrawable();
}
