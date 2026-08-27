package com.deviceprobe.inspector;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * Classifies a device using public Android evidence. A manual selection is persisted locally and
 * always disclosed in the label; product/brand names are deliberately not used as system evidence.
 */
final class DeviceProfile {
    enum Kind { PHONE, TABLET, TELEVISION, WATCH }
    enum Confidence { CONFIRMED, SUSPECTED, STANDARD, MANUAL }

    private static final String PREFS = "device_profile";
    private static final String KEY_OVERRIDE = "type_override";
    private static final String AUTO = "AUTO";

    final Kind kind;
    final Kind automaticKind;
    final Confidence confidence;
    final Confidence automaticConfidence;
    final int widthPx;
    final int heightPx;
    final int densityDpi;
    final int smallestWidthDp;
    final int uiModeType;
    final boolean screenRound;
    final boolean manualOverride;
    final String evidence;

    private DeviceProfile(Kind kind, Kind automaticKind, Confidence confidence, Confidence automaticConfidence,
                          DisplayMetrics metrics, Configuration config, boolean screenRound,
                          boolean manualOverride, String evidence) {
        this.kind = kind;
        this.automaticKind = automaticKind;
        this.confidence = confidence;
        this.automaticConfidence = automaticConfidence;
        this.widthPx = metrics.widthPixels;
        this.heightPx = metrics.heightPixels;
        this.densityDpi = metrics.densityDpi;
        this.smallestWidthDp = config.smallestScreenWidthDp;
        this.uiModeType = config.uiMode & Configuration.UI_MODE_TYPE_MASK;
        this.screenRound = screenRound;
        this.manualOverride = manualOverride;
        this.evidence = evidence;
    }

    static DeviceProfile detect(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        Configuration config = context.getResources().getConfiguration();
        PackageManager pm = context.getPackageManager();
        int uiMode = config.uiMode & Configuration.UI_MODE_TYPE_MASK;
        boolean round = Build.VERSION.SDK_INT >= 23 && config.isScreenRound();
        List<String> reasons = new ArrayList<>();

        Kind automaticKind;
        Confidence automaticConfidence;
        if (pm.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            automaticKind = Kind.WATCH;
            automaticConfidence = Confidence.CONFIRMED;
            reasons.add("系统特性 FEATURE_WATCH");
        } else if (uiMode == Configuration.UI_MODE_TYPE_WATCH) {
            automaticKind = Kind.WATCH;
            automaticConfidence = Confidence.CONFIRMED;
            reasons.add("系统 UI 模式为 WATCH");
        } else if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)) {
            automaticKind = Kind.TELEVISION;
            automaticConfidence = Confidence.CONFIRMED;
            reasons.add(pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                    ? "系统特性 FEATURE_LEANBACK" : "系统特性 FEATURE_TELEVISION");
        } else if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) {
            automaticKind = Kind.TELEVISION;
            automaticConfidence = Confidence.CONFIRMED;
            reasons.add("系统 UI 模式为 TELEVISION");
        } else {
            int shortPx = Math.min(metrics.widthPixels, metrics.heightPixels);
            int longPx = Math.max(metrics.widthPixels, metrics.heightPixels);
            float ratio = shortPx == 0 ? 99f : longPx / (float) shortPx;
            boolean verySmallDp = config.smallestScreenWidthDp > 0 && config.smallestScreenWidthDp <= 280;
            boolean verySmallPixels = shortPx <= 520;
            boolean nearSquare = ratio <= 1.38f;
            if (round && verySmallDp) {
                automaticKind = Kind.WATCH;
                automaticConfidence = Confidence.SUSPECTED;
                reasons.add("圆形屏幕");
                reasons.add("最小宽度 " + config.smallestScreenWidthDp + "dp ≤ 280dp");
            } else if (verySmallDp && verySmallPixels && nearSquare) {
                automaticKind = Kind.WATCH;
                automaticConfidence = Confidence.SUSPECTED;
                reasons.add("最小宽度 " + config.smallestScreenWidthDp + "dp ≤ 280dp");
                reasons.add("短边 " + shortPx + "px ≤ 520px");
                reasons.add("接近方形显示比例 " + String.format(java.util.Locale.US, "%.2f", ratio));
            } else if (config.smallestScreenWidthDp >= 600) {
                automaticKind = Kind.TABLET;
                automaticConfidence = Confidence.STANDARD;
                reasons.add("最小宽度 " + config.smallestScreenWidthDp + "dp ≥ 600dp");
            } else {
                automaticKind = Kind.PHONE;
                automaticConfidence = Confidence.STANDARD;
                reasons.add("未命中电视/手表公开特性或手表组合线索");
            }
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String override = preferences.getString(KEY_OVERRIDE, AUTO);
        if (override != null && !AUTO.equals(override)) {
            try {
                Kind selected = Kind.valueOf(override);
                return new DeviceProfile(selected, automaticKind, Confidence.MANUAL, automaticConfidence, metrics, config, round,
                        true, "用户手动选择 " + humanName(selected) + "；自动判断为 " + automaticLabel(automaticKind, automaticConfidence));
            } catch (IllegalArgumentException ignored) {
                preferences.edit().remove(KEY_OVERRIDE).apply();
            }
        }
        return new DeviceProfile(automaticKind, automaticKind, automaticConfidence, automaticConfidence, metrics, config, round,
                false, join(reasons));
    }

    static void saveOverride(Context context, Kind kindOrNull) {
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        if (kindOrNull == null) editor.putString(KEY_OVERRIDE, AUTO);
        else editor.putString(KEY_OVERRIDE, kindOrNull.name());
        editor.apply();
    }

    int selectedModeIndex() {
        if (!manualOverride) return 0;
        switch (kind) {
            case PHONE: return 1;
            case TABLET: return 2;
            case TELEVISION: return 3;
            case WATCH: return 4;
            default: return 0;
        }
    }

    boolean isWatch() { return kind == Kind.WATCH; }
    boolean isTelevision() { return kind == Kind.TELEVISION; }
    boolean isSuspectedWatch() { return kind == Kind.WATCH && confidence == Confidence.SUSPECTED; }

    String label() {
        if (confidence == Confidence.MANUAL) return humanName(kind) + "（用户已手动选择）";
        if (confidence == Confidence.CONFIRMED) return humanName(kind) + "（系统已确认）";
        if (confidence == Confidence.SUSPECTED) return "疑似 Android 手表（组合线索）";
        if (kind == Kind.TABLET) return "平板（最小宽度 ≥ 600dp）";
        return "手机/未确认设备";
    }

    String automaticLabel() { return automaticLabel(automaticKind, automaticConfidence); }

    String detectionDetails() {
        String roundText = Build.VERSION.SDK_INT >= 23 ? (screenRound ? "是" : "否") : "当前 API 未公开";
        return "判定：" + label() + "\n"
                + "自动判定：" + automaticLabel() + "\n"
                + "依据：" + evidence + "\n"
                + "屏幕：" + widthPx + " × " + heightPx + " px，" + densityDpi + " dpi，最小宽度 "
                + smallestWidthDp + "dp，圆形屏幕 " + roundText + "\n"
                + "说明：不依据品牌、机型或“和顺成”等方案名称断言系统类型。可点击“纠正设备类型”切换布局。";
    }

    String screenRule() {
        if (isWatch()) return isSuspectedWatch()
                ? "疑似手表规则：已启用小屏单列与简化控制；可手动纠正。"
                : "手表规则：单列、紧凑边距、大触控目标；屏幕测试使用小屏控制。";
        if (isTelevision()) return "电视规则：大屏焦点、横向布局与遥控器方向键/确认键控制。";
        if (kind == Kind.TABLET) return "平板规则：较宽边距与多列概览；屏幕测试按实际显示区域绘制。";
        return "手机规则：触摸优先布局；屏幕测试按实际显示区域绘制。";
    }

    private static String automaticLabel(Kind kind, Confidence confidence) {
        if (confidence == Confidence.CONFIRMED) return humanName(kind) + "（系统特性/UI 模式确认）";
        if (confidence == Confidence.SUSPECTED) return "疑似 Android 手表（非标准系统组合线索）";
        if (kind == Kind.TABLET) return "平板（屏幕宽度规则）";
        return "手机/未确认设备";
    }

    private static String humanName(Kind kind) {
        switch (kind) {
            case WATCH: return "手表";
            case TELEVISION: return "电视";
            case TABLET: return "平板";
            default: return "手机";
        }
    }

    private static String join(List<String> values) {
        if (values.isEmpty()) return "没有可用的设备分类线索";
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) out.append("；");
            out.append(value);
        }
        return out.toString();
    }
}
