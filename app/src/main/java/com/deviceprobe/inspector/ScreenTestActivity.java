package com.deviceprobe.inspector;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Full-screen display test patterns for phones, tablets, televisions and watches.
 * A pattern is an observation aid, never an automatic hardware-defect diagnosis.
 */
public final class ScreenTestActivity extends Activity {
    private static final int DARK = Color.rgb(7, 12, 22);
    private static final int PANEL = Color.argb(232, 12, 22, 38);
    private static final int WHITE = Color.rgb(245, 248, 252);
    private static final int MUTED = Color.rgb(172, 190, 211);
    private static final int MINT = Color.rgb(57, 216, 196);
    private static final int OFFICIAL_P3_PATTERN_INDEX = 13;

    private static final String[] NAMES = {
            "坏点 / 亮点 / 暗点", "灰阶", "对比度", "色彩饱和度", "触控", "几何 / 边缘",
            "RGB 渐变", "闪烁辅助（PWM）", "响应拖影", "残影老化", "色域参考", "视角", "色域检测", "官方 Display P3 红色机器人"
    };

    private static final String[] HINTS = {
            "点击画面切换黑、白、红、绿、蓝；观察固定异常点。",
            "观察从黑到白是否平滑，暗部和亮部是否丢失层次。",
            "观察黑白边界与多层灰阶块是否清晰可分。",
            "观察色彩是否均匀，有无断层、偏色或过饱和。",
            "用手指覆盖所有网格；观察轨迹是否连续、断触或跳点。",
            "观察直线、圆形、四角与边缘是否变形、弯曲或缺失。",
            "观察红、绿、蓝三条渐变是否连续，有无色带。",
            "软件仅提供动态辅助图案；PWM 需慢动作相机或光电仪器确认。",
            "观察移动方块边缘是否模糊、重影或响应迟滞。",
            "亮十字转为灰底后，观察轮廓是否持续残留。",
            "颜色块仅作主观参考，不等于仪器测得的色域。",
            "正面与不同角度观察亮度、颜色和对比度变化。",
            "轮换标准颜色；真实色域覆盖率必须使用校色仪测量。",
            "官方 Display P3 原图：机器人出现才表示本图片的 P3 显示路径可见；均匀红色通常表示未按广色域呈现。"
    };

    private DeviceProfile profile;
    private PatternView patternView;
    private TextView titleView;
    private TextView hintView;
    private int patternIndex;
    private int solidIndex;
    private Bitmap officialP3Bitmap;
    private String wideColorModeStatus = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        profile = DeviceProfile.detect(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enterImmersive();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(DARK);
        patternView = new PatternView();
        patternView.setLongClickable(true);
        patternView.setOnLongClickListener(v -> {
            if (profile.isWatch()) {
                finish();
                return true;
            }
            return false;
        });
        root.addView(patternView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(createTopPanel(), new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        root.addView(createBottomPanel(), new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);
        loadOfficialP3Bitmap();
        configureWideColorModeForPattern();
        updateLabels();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private View createTopPanel() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        int inset = profile.isWatch() ? dp(7) : (profile.isTelevision() ? dp(22) : dp(14));
        top.setPadding(inset, profile.isWatch() ? dp(5) : dp(8), inset, profile.isWatch() ? dp(5) : dp(8));
        top.setBackgroundColor(PANEL);
        titleView = textView("", profile.isWatch() ? 12 : (profile.isTelevision() ? 20 : 16), WHITE, true);
        hintView = textView("", profile.isWatch() ? 10 : (profile.isTelevision() ? 15 : 12), MUTED, false);
        hintView.setMaxLines(profile.isWatch() ? 2 : 3);
        hintView.setPadding(0, dp(2), 0, 0);
        top.addView(titleView);
        top.addView(hintView);
        return top;
    }

    private View createBottomPanel() {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        int inset = profile.isWatch() ? dp(5) : (profile.isTelevision() ? dp(22) : dp(10));
        bottom.setPadding(inset, profile.isWatch() ? dp(4) : dp(7), inset, profile.isWatch() ? dp(4) : dp(7));
        bottom.setBackgroundColor(PANEL);

        TextView previous = action(profile.isWatch() ? "←" : "上一个");
        previous.setContentDescription("上一个测试");
        previous.setOnClickListener(v -> selectPattern(patternIndex - 1));
        TextView next = action(profile.isWatch() ? "→" : "下一个");
        next.setContentDescription("下一个测试");
        next.setOnClickListener(v -> selectPattern(patternIndex + 1));
        int height = profile.isWatch() ? dp(34) : (profile.isTelevision() ? dp(56) : dp(44));
        bottom.addView(previous, new LinearLayout.LayoutParams(0, height, 1f));
        addGap(bottom, profile.isWatch() ? dp(5) : dp(10));
        bottom.addView(next, new LinearLayout.LayoutParams(0, height, 1f));

        if (!profile.isWatch()) {
            addGap(bottom, dp(10));
            TextView exit = action("退出测试");
            exit.setContentDescription("退出屏幕测试");
            exit.setOnClickListener(v -> finish());
            bottom.addView(exit, new LinearLayout.LayoutParams(0, height, 1f));
        }
        return bottom;
    }

    private void addGap(LinearLayout row, int width) {
        View spacer = new View(this);
        row.addView(spacer, new LinearLayout.LayoutParams(width, 1));
    }

    private TextView action(String value) {
        TextView button = textView(value, profile.isWatch() ? 14 : (profile.isTelevision() ? 17 : 14), WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setFocusableInTouchMode(profile.isTelevision());
        button.setPadding(dp(4), 0, dp(4), 0);
        setActionBackground(button, false);
        button.setOnFocusChangeListener((v, focused) -> setActionBackground(button, focused));
        return button;
    }

    private void setActionBackground(TextView button, boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(profile.isWatch() ? 10 : 13));
        bg.setColor(focused ? Color.rgb(35, 86, 100) : Color.rgb(27, 46, 71));
        if (focused) bg.setStroke(dp(2), MINT);
        button.setBackground(bg);
    }

    private TextView textView(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void selectPattern(int index) {
        patternIndex = (index % NAMES.length + NAMES.length) % NAMES.length;
        configureWideColorModeForPattern();
        patternView.reset();
        updateLabels();
    }

    private void loadOfficialP3Bitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        officialP3Bitmap = BitmapFactory.decodeResource(getResources(), R.raw.android_p3, options);
    }

    private void configureWideColorModeForPattern() {
        boolean requestWideColor = patternIndex == OFFICIAL_P3_PATTERN_INDEX;
        if (Build.VERSION.SDK_INT < 26) {
            wideColorModeStatus = requestWideColor
                    ? "系统状态：API 26 以下无公开广色域窗口模式；原图按系统默认色彩链路显示。"
                    : "";
            return;
        }
        try {
            Class<?> activityInfo = Class.forName("android.content.pm.ActivityInfo");
            Field requestedField = activityInfo.getField(requestWideColor
                    ? "COLOR_MODE_WIDE_COLOR_GAMUT" : "COLOR_MODE_DEFAULT");
            int requestedMode = requestedField.getInt(null);
            Method setColorMode = Window.class.getMethod("setColorMode", int.class);
            Method getColorMode = Window.class.getMethod("getColorMode");
            setColorMode.invoke(getWindow(), requestedMode);
            int grantedMode = ((Integer) getColorMode.invoke(getWindow())).intValue();
            boolean displayWide = isDisplayWideColorGamut();
            if (requestWideColor) {
                wideColorModeStatus = displayWide && grantedMode == requestedMode
                        ? "系统状态：已请求广色域窗口；显示器报告支持广色域。"
                        : (displayWide ? "系统状态：已请求广色域窗口；系统未报告已授予该模式。"
                        : "系统状态：已请求广色域窗口；当前显示器未报告广色域支持。");
            } else {
                wideColorModeStatus = "";
            }
        } catch (Throwable error) {
            wideColorModeStatus = requestWideColor
                    ? "系统状态：广色域窗口请求不可用（" + error.getClass().getSimpleName() + "）。"
                    : "";
        }
    }

    private boolean isDisplayWideColorGamut() {
        try {
            Object display = getWindow().getDecorView().getDisplay();
            if (display == null) return false;
            Method wide = display.getClass().getMethod("isWideColorGamut");
            return Boolean.TRUE.equals(wide.invoke(display));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void updateLabels() {
        String controller = profile.isTelevision() ? " · 遥控器：←/→ 切换，确认切换纯色"
                : (profile.isWatch() ? " · 长按画面退出" : "");
        titleView.setText(String.format(Locale.US, "%02d/%02d  %s%s", patternIndex + 1, NAMES.length, NAMES[patternIndex], controller));
        String hint = HINTS[patternIndex];
        boolean officialP3Test = patternIndex == OFFICIAL_P3_PATTERN_INDEX;
        if (officialP3Test && !wideColorModeStatus.isEmpty()) {
            hint += "\n" + wideColorModeStatus;
        }
        hintView.setMaxLines(officialP3Test ? 4 : (profile.isWatch() ? 2 : 3));
        hintView.setText(hint);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (profile.isTelevision()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                selectPattern(patternIndex - 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                selectPattern(patternIndex + 1);
                return true;
            }
            if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && patternIndex == 0) {
                solidIndex = (solidIndex + 1) % 5;
                patternView.invalidate();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class PatternView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path touchPath = new Path();
        private boolean hasTouch;
        private long phaseStart = SystemClock.uptimeMillis();

        PatternView() {
            super(ScreenTestActivity.this);
            paint.setStrokeCap(Paint.Cap.ROUND);
            setFocusable(false);
        }

        void reset() {
            touchPath.reset();
            hasTouch = false;
            phaseStart = SystemClock.uptimeMillis();
            invalidate();
        }

        private int top() { return profile.isWatch() ? dp(55) : (profile.isTelevision() ? dp(84) : dp(78)); }
        private int bottom() { return getHeight() - (profile.isWatch() ? dp(48) : (profile.isTelevision() ? dp(72) : dp(62))); }
        private int contentHeight() { return Math.max(dp(40), bottom() - top()); }
        private float centerY() { return top() + contentHeight() / 2f; }
        private void text(float sp, int color) { paint.setTextSize(dp(sp)); paint.setColor(color); paint.setStyle(Paint.Style.FILL); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long elapsed = SystemClock.uptimeMillis() - phaseStart;
            switch (patternIndex) {
                case 0: solid(canvas); break;
                case 1: grayscale(canvas); break;
                case 2: contrast(canvas); break;
                case 3: saturation(canvas); break;
                case 4: touch(canvas); break;
                case 5: geometry(canvas); break;
                case 6: rgbGradient(canvas); break;
                case 7: flicker(canvas, elapsed); break;
                case 8: motion(canvas, elapsed); break;
                case 9: afterimage(canvas, elapsed); break;
                case 10: gamutReference(canvas); break;
                case 11: viewingAngle(canvas); break;
                case 12: gamutDetection(canvas, elapsed); break;
                default: officialP3Robot(canvas); break;
            }
            if (patternIndex == 7 || patternIndex == 8 || patternIndex == 9 || patternIndex == 12) postInvalidateDelayed(16);
        }

        private void solid(Canvas c) {
            int[] colors = {Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE};
            c.drawColor(colors[solidIndex]);
            text(profile.isWatch() ? 9 : 15, solidIndex == 1 ? Color.BLACK : Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            String[] words = {"黑色：亮点/漏光", "白色：暗点/色点", "红色：红色子像素", "绿色：绿色子像素", "蓝色：蓝色子像素"};
            c.drawText(words[solidIndex], getWidth() / 2f, centerY(), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void grayscale(Canvas c) {
            c.drawColor(Color.BLACK);
            int left = dp(12), right = getWidth() - dp(12), y1 = top() + contentHeight() / 4, y2 = bottom() - contentHeight() / 4;
            paint.setShader(new LinearGradient(left, 0, right, 0, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP));
            c.drawRect(left, y1, right, y2, paint);
            paint.setShader(null);
            int steps = profile.isWatch() ? 8 : 16;
            for (int i = 0; i < steps; i++) {
                int gray = Math.round(i * 255f / (steps - 1));
                paint.setColor(Color.rgb(gray, gray, gray));
                float x = left + (right - left) * i / (steps - 1f);
                c.drawRect(x - dp(1), y2 + dp(8), x + dp(1), y2 + dp(20), paint);
            }
        }

        private void contrast(Canvas c) {
            c.drawColor(Color.BLACK);
            int size = Math.min(getWidth() - dp(28), contentHeight() - dp(24));
            float cx = getWidth() / 2f, cy = centerY();
            for (int i = 8; i >= 1; i--) {
                int gray = Math.round(i * 255f / 8f);
                paint.setColor(Color.rgb(gray, gray, gray));
                float half = size * i / 16f;
                c.drawRect(cx - half, cy - half, cx + half, cy + half, paint);
            }
        }

        private void saturation(Canvas c) {
            c.drawColor(Color.BLACK);
            int bands = profile.isWatch() ? 6 : 12;
            for (int i = 0; i < bands; i++) {
                paint.setColor(Color.HSVToColor(new float[]{i * 360f / bands, 1f, 1f}));
                float y1 = top() + contentHeight() * i / (float) bands;
                float y2 = top() + contentHeight() * (i + 1) / (float) bands;
                c.drawRect(0, y1, getWidth(), y2, paint);
            }
        }

        private void touch(Canvas c) {
            c.drawColor(Color.rgb(12, 20, 32));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.rgb(66, 92, 120));
            int columns = profile.isWatch() ? 6 : 10;
            int rows = profile.isWatch() ? 6 : 12;
            for (int i = 0; i <= columns; i++) {
                float x = getWidth() * i / (float) columns;
                c.drawLine(x, top(), x, bottom(), paint);
            }
            for (int i = 0; i <= rows; i++) {
                float y = top() + contentHeight() * i / (float) rows;
                c.drawLine(0, y, getWidth(), y, paint);
            }
            paint.setColor(MINT);
            paint.setStrokeWidth(dp(profile.isWatch() ? 3 : 4));
            if (hasTouch) c.drawPath(touchPath, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void geometry(Canvas c) {
            c.drawColor(Color.BLACK);
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            float left = dp(10), right = getWidth() - dp(10), t = top() + dp(5), b = bottom() - dp(5);
            int rings = profile.isWatch() ? 4 : 7;
            for (int i = 0; i < rings; i++) {
                float insetX = (right - left) * i / (rings * 2.2f);
                float insetY = (b - t) * i / (rings * 2.2f);
                c.drawRect(left + insetX, t + insetY, right - insetX, b - insetY, paint);
            }
            float cx = getWidth() / 2f, cy = centerY(), r = Math.min(right - left, b - t) / 4f;
            c.drawCircle(cx, cy, r, paint);
            c.drawLine(left, cy, right, cy, paint);
            c.drawLine(cx, t, cx, b, paint);
            c.drawLine(left, t, right, b, paint);
            c.drawLine(right, t, left, b, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void rgbGradient(Canvas c) {
            c.drawColor(Color.BLACK);
            int row = contentHeight() / 3;
            gradientRow(c, top(), row, Color.BLACK, Color.RED);
            gradientRow(c, top() + row, row, Color.BLACK, Color.GREEN);
            gradientRow(c, top() + row * 2, bottom() - top() - row * 2, Color.BLACK, Color.BLUE);
        }

        private void gradientRow(Canvas c, int y, int h, int from, int to) {
            paint.setShader(new LinearGradient(0, 0, getWidth(), 0, from, to, Shader.TileMode.CLAMP));
            c.drawRect(0, y, getWidth(), y + h - dp(2), paint);
            paint.setShader(null);
        }

        private void flicker(Canvas c, long elapsed) {
            boolean light = (elapsed / 120) % 2 == 0;
            c.drawColor(light ? Color.WHITE : Color.BLACK);
            text(profile.isWatch() ? 9 : 15, light ? Color.BLACK : Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("PWM 辅助 · 请配合慢动作相机", getWidth() / 2f, centerY(), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        private void motion(Canvas c, long elapsed) {
            c.drawColor(Color.BLACK);
            int block = Math.max(dp(22), Math.min(getWidth(), contentHeight()) / 5);
            int range = Math.max(1, getWidth() - block - dp(20));
            float x = dp(10) + (elapsed / 4f % range);
            float y = centerY() - block / 2f;
            paint.setColor(Color.GRAY);
            for (int i = 5; i >= 1; i--) c.drawRect(x - dp(i * 8), y, x - dp(i * 8) + dp(2), y + block, paint);
            paint.setColor(Color.WHITE);
            c.drawRect(x, y, x + block, y + block, paint);
        }

        private void afterimage(Canvas c, long elapsed) {
            boolean bright = elapsed % 3600 < 1500;
            c.drawColor(bright ? Color.WHITE : Color.rgb(110, 110, 110));
            paint.setColor(bright ? Color.BLACK : Color.WHITE);
            paint.setStrokeWidth(Math.max(dp(7), Math.min(getWidth(), contentHeight()) / 11f));
            float cx = getWidth() / 2f, cy = centerY(), r = Math.min(getWidth(), contentHeight()) / 4f;
            c.drawLine(cx - r, cy, cx + r, cy, paint);
            c.drawLine(cx, cy - r, cx, cy + r, paint);
            paint.setStrokeWidth(1);
        }

        private void gamutReference(Canvas c) {
            c.drawColor(Color.BLACK);
            int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.WHITE, Color.GRAY};
            String[] labels = {"R", "G", "B", "C", "M", "Y", "W", "50%"};
            int gap = dp(5), columns = 2, width = (getWidth() - gap * 3) / 2;
            int height = Math.max(dp(15), (contentHeight() - gap * 5) / 4);
            for (int i = 0; i < colors.length; i++) {
                int x = gap + (i % columns) * (width + gap);
                int y = top() + gap + (i / columns) * (height + gap);
                paint.setColor(colors[i]);
                c.drawRect(x, y, x + width, y + height, paint);
                text(profile.isWatch() ? 8 : 13, i == 6 || i == 5 ? Color.BLACK : Color.WHITE);
                c.drawText(labels[i], x + dp(5), y + height - dp(5), paint);
            }
        }

        private void viewingAngle(Canvas c) {
            c.drawColor(Color.rgb(128, 128, 128));
            paint.setColor(Color.WHITE);
            int strips = profile.isWatch() ? 5 : 10;
            for (int i = 0; i < strips; i++) {
                int x = i * getWidth() / strips;
                c.drawRect(x, top(), x + getWidth() / (strips * 2), bottom(), paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.BLACK);
            c.drawRect(dp(6), top(), getWidth() - dp(6), bottom(), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void officialP3Robot(Canvas c) {
            c.drawColor(Color.BLACK);
            if (officialP3Bitmap == null) {
                drawP3AssetUnavailable(c);
                return;
            }
            float size = Math.min(getWidth(), contentHeight());
            float left = (getWidth() - size) / 2f;
            float top = top() + (contentHeight() - size) / 2f;
            paint.setFilterBitmap(false);
            c.drawBitmap(officialP3Bitmap, null, new RectF(left, top, left + size, top + size), paint);
            paint.setFilterBitmap(true);
        }

        private void drawP3AssetUnavailable(Canvas c) {
            c.drawColor(Color.rgb(7, 10, 18));
            float cx = getWidth() / 2f;
            float available = Math.min(getWidth() * 0.56f, contentHeight() * 0.62f);
            float robotW = Math.max(dp(42), available);
            float headH = robotW * 0.48f;
            float bodyH = robotW * 0.72f;
            float limbW = Math.max(dp(8), robotW * 0.15f);
            float limbH = bodyH * 0.72f;
            float gap = Math.max(dp(5), robotW * 0.07f);
            float headTop = centerY() - (headH + bodyH) / 2f - gap / 2f;
            float headBottom = headTop + headH;
            float bodyTop = headBottom + gap;
            float bodyBottom = bodyTop + bodyH;
            int red = Color.rgb(255, 23, 68);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(red);
            c.drawRoundRect(new RectF(cx - robotW / 2f, headTop, cx + robotW / 2f, headBottom), robotW * 0.22f, robotW * 0.22f, paint);
            c.drawRoundRect(new RectF(cx - robotW / 2f, bodyTop, cx + robotW / 2f, bodyBottom), robotW * 0.10f, robotW * 0.10f, paint);
            c.drawRoundRect(new RectF(cx - robotW / 2f - gap - limbW, bodyTop + dp(6), cx - robotW / 2f - gap, bodyBottom - dp(8)), limbW / 2f, limbW / 2f, paint);
            c.drawRoundRect(new RectF(cx + robotW / 2f + gap, bodyTop + dp(6), cx + robotW / 2f + gap + limbW, bodyBottom - dp(8)), limbW / 2f, limbW / 2f, paint);
            c.drawRoundRect(new RectF(cx - robotW * 0.31f, bodyBottom - dp(1), cx - robotW * 0.31f + limbW, bodyBottom + limbH), limbW / 2f, limbW / 2f, paint);
            c.drawRoundRect(new RectF(cx + robotW * 0.31f - limbW, bodyBottom - dp(1), cx + robotW * 0.31f, bodyBottom + limbH), limbW / 2f, limbW / 2f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(2), robotW * 0.035f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(cx - robotW * 0.26f, headTop + dp(2), cx - robotW * 0.40f, headTop - robotW * 0.20f, paint);
            c.drawLine(cx + robotW * 0.26f, headTop + dp(2), cx + robotW * 0.40f, headTop - robotW * 0.20f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            float eye = Math.max(dp(3), robotW * 0.055f);
            c.drawCircle(cx - robotW * 0.21f, headTop + headH * 0.48f, eye, paint);
            c.drawCircle(cx + robotW * 0.21f, headTop + headH * 0.48f, eye, paint);

            text(profile.isWatch() ? 9 : 15, Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("官方 P3 图片不可读取", cx, Math.min(bottom() - dp(12), bodyBottom + limbH + dp(30)), paint);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setStrokeCap(Paint.Cap.BUTT);
        }

        private void gamutDetection(Canvas c, long elapsed) {
            int page = (int) ((elapsed / 1400) % 8);
            int[] colors = {Color.BLACK, Color.WHITE, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW};
            String[] labels = {"黑", "白", "红", "绿", "蓝", "青", "品红", "黄"};
            c.drawColor(colors[page]);
            text(profile.isWatch() ? 9 : 16, page == 1 || page == 3 || page == 5 || page == 7 ? Color.BLACK : Color.WHITE);
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("色域参考 · " + labels[page], getWidth() / 2f, centerY(), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            if (patternIndex == 4) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    touchPath.reset();
                    touchPath.moveTo(x, y);
                    hasTouch = true;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                    touchPath.lineTo(x, y);
                }
                invalidate();
                return true;
            }
            if (patternIndex == 0 && event.getAction() == MotionEvent.ACTION_UP) {
                solidIndex = (solidIndex + 1) % 5;
                invalidate();
                performClick();
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }
}
