package com.deviceprobe.inspector;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One shared visual language: a single dark palette, one spacing scale and one set
 * of shapes. Every screen derives its look from here, so colors, radii and text
 * styles stay consistent and can be tuned in one place.
 */
final class Ui {
    // Palette.
    static final int BACKGROUND = Color.rgb(9, 16, 29);
    static final int SURFACE = Color.rgb(17, 28, 47);
    static final int SURFACE_SOFT = Color.rgb(22, 37, 59);
    static final int SURFACE_PRESSED = Color.rgb(36, 72, 85);
    static final int SURFACE_FOCUS = Color.rgb(38, 73, 88);
    static final int TRACK = Color.rgb(31, 47, 70);
    static final int TEXT_PRIMARY = Color.rgb(240, 246, 252);
    static final int TEXT_SECONDARY = Color.rgb(161, 180, 205);
    static final int MINT = Color.rgb(57, 216, 196);
    static final int AMBER = Color.rgb(205, 164, 82);
    static final int RED = Color.rgb(214, 101, 101);

    private final Context context;
    private final DeviceProfile profile;
    private final float density;

    Ui(Context context, DeviceProfile profile) {
        this.context = context;
        this.profile = profile;
        this.density = context.getResources().getDisplayMetrics().density;
    }

    int dp(int value) {
        return (int) (profile.dp(value, density) + 0.5f);
    }

    android.content.Context context() { return context; }

    boolean watch() { return profile.isWatch(); }

    boolean tv() { return profile.isTelevision(); }

    // ---------------------------------------------------------------- text views

    TextView text(CharSequence value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    // ---------------------------------------------------------------- shapes

    Drawable rounded(int fill, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    Drawable stroked(int fill, int strokeColor, int radius, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    Drawable ripple(int fill, int pressed, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(pressed), rounded(fill, radius), null);
    }

    /** Interactive background: focus ring on televisions, ripple everywhere else. */
    Drawable interactive(int fill, int highlight, int radius) {
        if (tv()) {
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, stroked(highlight, MINT, radius, dp(2)));
            states.addState(new int[]{android.R.attr.state_pressed}, rounded(highlight, radius));
            states.addState(new int[]{}, rounded(fill, radius));
            return states;
        }
        return ripple(fill, highlight, radius);
    }

    // ---------------------------------------------------------------- layout params

    static int matchParent() { return ViewGroup.LayoutParams.MATCH_PARENT; }

    static int wrapContent() { return ViewGroup.LayoutParams.WRAP_CONTENT; }

    LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(matchParent(), wrapContent());
    }

    LinearLayout.LayoutParams fixed(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }
}
