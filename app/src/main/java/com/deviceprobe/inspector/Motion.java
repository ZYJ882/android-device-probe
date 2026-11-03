package com.deviceprobe.inspector;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.LinearLayout;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The app's single motion language. Everything here first checks the system
 * "animator duration scale", so users who turn animations off in accessibility
 * settings get instant state changes instead of movement. Durations are short
 * (200–280 ms) and easing is decelerating, which keeps the interface calm.
 */
final class Motion {
    private static final long ENTER_MS = 280L;
    private static final long PULSE_MS = 240L;

    private static final Interpolator EASE_OUT = new DecelerateInterpolator(1.8f);
    private static final Interpolator EASE_IN_OUT = new AccelerateDecelerateInterpolator();

    /** Tracks the running height animator per view so rapid toggles cannot stack. */
    private static final Map<View, ValueAnimator> RUNNING = new WeakHashMap<>();

    private Motion() {
    }

    static boolean enabled(Context context) {
        try {
            float scale = Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    // ---------------------------------------------------------------- entrances

    /** Fade in while rising a few dp into place. */
    static void enter(View view, long delayMs, int risePx) {
        if (view == null) return;
        if (!enabled(view.getContext())) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(risePx);
        view.animate().alpha(1f).translationY(0f)
                .setStartDelay(delayMs).setDuration(ENTER_MS)
                .setInterpolator(EASE_OUT).start();
    }

    /** Soft refresh for a value that just changed: it settles from translucent to full. */
    static void pulse(View view) {
        if (view == null || !enabled(view.getContext())) return;
        view.animate().cancel();
        view.setAlpha(0.35f);
        view.animate().alpha(1f).setDuration(PULSE_MS).setInterpolator(EASE_OUT).start();
    }

    static void fadeTo(View view, float target, long durationMs) {
        if (view == null) return;
        if (!enabled(view.getContext())) {
            view.setAlpha(target);
            return;
        }
        view.animate().alpha(target).setDuration(durationMs).setInterpolator(EASE_OUT).start();
    }

    /** A quick tactile bounce on tap, without swallowing the click. */
    static void tap(View view) {
        if (view == null || !enabled(view.getContext())) return;
        view.animate().cancel();
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(140).setInterpolator(EASE_OUT).start())
                .start();
    }

    // ---------------------------------------------------------------- expand / collapse

    static void expand(final View content, long durationMs) {
        if (content == null) return;
        cancel(content);
        final ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params == null) {
            content.setVisibility(View.VISIBLE);
            return;
        }
        content.setAlpha(0f);
        content.setVisibility(View.VISIBLE);
        final int target = measureHeight(content);
        if (target <= 0 || !enabled(content.getContext())) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.setLayoutParams(params);
            content.setAlpha(1f);
            return;
        }
        params.height = 0;
        content.setLayoutParams(params);

        final ValueAnimator animator = ValueAnimator.ofInt(0, target);
        animator.setDuration(durationMs).setInterpolator(EASE_OUT);
        animator.addUpdateListener(animation -> {
            params.height = (Integer) animation.getAnimatedValue();
            content.setLayoutParams(params);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                content.setLayoutParams(params);
                content.setAlpha(1f);
                RUNNING.remove(content);
            }
        });
        animator.start();
        content.animate().alpha(1f).setDuration(durationMs).setInterpolator(EASE_OUT).start();
        RUNNING.put(content, animator);
    }

    static void collapse(final View content, long durationMs) {
        if (content == null) return;
        cancel(content);
        final ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params == null) {
            content.setVisibility(View.GONE);
            return;
        }
        final int start = content.getHeight();
        if (start <= 0 || !enabled(content.getContext())) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.setLayoutParams(params);
            content.setAlpha(1f);
            content.setVisibility(View.GONE);
            return;
        }

        final ValueAnimator animator = ValueAnimator.ofInt(start, 0);
        animator.setDuration(durationMs).setInterpolator(EASE_IN_OUT);
        animator.addUpdateListener(animation -> {
            params.height = (Integer) animation.getAnimatedValue();
            content.setLayoutParams(params);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                content.setLayoutParams(params);
                content.setVisibility(View.GONE);
                content.setAlpha(1f);
                RUNNING.remove(content);
            }
        });
        content.animate().alpha(0f).setDuration(durationMs).setInterpolator(EASE_IN_OUT).start();
        animator.start();
        RUNNING.put(content, animator);
    }

    // ---------------------------------------------------------------- bars

    /** Grows a weighted bar from zero to its real share. */
    static void animateProgress(final View fill, final float targetWeight, long durationMs) {
        if (fill == null || !(fill.getLayoutParams() instanceof LinearLayout.LayoutParams)) return;
        final LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) fill.getLayoutParams();
        params.weight = 0f;
        fill.setLayoutParams(params);
        if (!enabled(fill.getContext())) {
            params.weight = targetWeight;
            fill.setLayoutParams(params);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, targetWeight);
        animator.setDuration(durationMs).setInterpolator(EASE_OUT);
        animator.addUpdateListener(animation -> {
            params.weight = (Float) animation.getAnimatedValue();
            fill.setLayoutParams(params);
        });
        animator.start();
    }

    // ---------------------------------------------------------------- helpers

    private static int measureHeight(View content) {
        View parent = content.getParent() instanceof View ? (View) content.getParent() : null;
        int width = parent == null ? 0
                : parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        int widthSpec = width > 0
                ? View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        content.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return content.getMeasuredHeight();
    }

    private static void cancel(View view) {
        ValueAnimator running = RUNNING.remove(view);
        if (running != null) {
            running.removeAllListeners();
            if (running.isRunning()) running.cancel();
        }
        view.animate().cancel();
    }
}
