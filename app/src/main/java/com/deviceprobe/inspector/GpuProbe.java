package com.deviceprobe.inspector;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;

/** Reads values exposed by the GPU driver after creating a real, off-screen GLES 2 context. */
final class GpuProbe {
    private GpuProbe() { }

    static String collect() {
        StringBuilder out = new StringBuilder();
        out.append("[GPU / OpenGL ES 驱动]\n");
        out.append("来源: 在本应用创建的离屏 EGL/OpenGL ES 上下文中，由图形驱动直接报告。\n");
        EGLDisplay display = EGL14.EGL_NO_DISPLAY;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        EGLContext context = EGL14.EGL_NO_CONTEXT;
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) return failure("eglGetDisplay", EGL14.eglGetError());
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return failure("eglInitialize", EGL14.eglGetError());

            int[] attrs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, configs.length, count, 0) || count[0] == 0) {
                return failure("eglChooseConfig", EGL14.eglGetError());
            }
            int[] surfaceAttrs = {EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE};
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttrs, 0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return failure("eglCreatePbufferSurface", EGL14.eglGetError());
            int[] contextAttrs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) return failure("eglCreateContext", EGL14.eglGetError());
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return failure("eglMakeCurrent", EGL14.eglGetError());

            append(out, "EGL 版本", version[0] + "." + version[1]);
            append(out, "GL_VENDOR", GLES20.glGetString(GLES20.GL_VENDOR));
            append(out, "GL_RENDERER", GLES20.glGetString(GLES20.GL_RENDERER));
            String glVersion = GLES20.glGetString(GLES20.GL_VERSION);
            append(out, "GL_VERSION", glVersion);
            append(out, "GLSL 版本", GLES20.glGetString(GLES20.GL_SHADING_LANGUAGE_VERSION));
            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            String[] values = extensions == null || extensions.trim().isEmpty() ? new String[0] : extensions.trim().split("\\s+");
            append(out, "GL 扩展数量", String.valueOf(values.length));
            append(out, "GL 纹理压缩", textureCompression(values, glVersion));
            append(out, "GL 扩展示例", firstExtensions(values, 12));
            append(out, "真实性说明", "这些是当前图形驱动在本应用 EGL 上下文中报告的值；若显示软件渲染器，也会如实显示。");
            return out.toString();
        } catch (Throwable error) {
            return out.append("采集失败: ").append(error.getClass().getSimpleName()).append(": ")
                    .append(error.getMessage() == null ? "无错误详情" : error.getMessage()).append('\n').toString();
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                EGL14.eglTerminate(display);
            }
        }
    }

    private static String failure(String step, int eglError) {
        return "[GPU / OpenGL ES 驱动]\n来源: 离屏 EGL/OpenGL ES 上下文。\n采集失败: " + step
                + "，EGL 错误 0x" + Integer.toHexString(eglError) + "\n";
    }

    private static void append(StringBuilder out, String name, String value) {
        out.append(name).append(": ").append(value == null || value.trim().isEmpty() ? "驱动未提供" : value.trim()).append('\n');
    }

    private static String firstExtensions(String[] values, int limit) {
        if (values.length == 0) return "驱动未提供";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length && i < limit; i++) {
            if (out.length() > 0) out.append("；");
            out.append(values[i]);
        }
        if (values.length > limit) out.append("；另有 ").append(values.length - limit).append(" 项");
        return out.toString();
    }

    /**
     * Common compressed-texture formats actually reported by the driver. This is what
     * decides whether a game can load its assets directly, which the raw extension
     * count cannot answer. ETC2 is core in OpenGL ES 3.0 and therefore never appears
     * in the extension list, so it is derived from GL_VERSION instead.
     */
    private static String textureCompression(String[] extensions, String glVersion) {
        StringBuilder out = new StringBuilder();
        if (glVersion != null && glVersion.contains("OpenGL ES 3")) appendToken(out, "ETC2");
        if (hasExtension(extensions, "GL_KHR_texture_compression_astc_ldr")
                || hasExtension(extensions, "GL_OES_texture_compression_astc")) appendToken(out, "ASTC");
        if (hasExtension(extensions, "GL_EXT_texture_compression_s3tc")) appendToken(out, "S3TC/DXT");
        if (hasExtension(extensions, "GL_AMD_compressed_ATC_texture")) appendToken(out, "ATC");
        if (hasExtension(extensions, "GL_OES_compressed_ETC1_RGB8_texture")) appendToken(out, "ETC1");
        return out.length() == 0 ? "驱动未报告常见压缩纹理扩展" : out.toString();
    }

    private static void appendToken(StringBuilder out, String value) {
        if (out.length() > 0) out.append(" / ");
        out.append(value);
    }

    private static boolean hasExtension(String[] extensions, String name) {
        for (String extension : extensions) {
            if (name.equals(extension)) return true;
        }
        return false;
    }
}
