package me.cookie.kwrg.gui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.lang.management.ManagementFactory;

public class HardwareMetricsTracker {

    private static long lastSampleTime = 0L;
    private static String cachedRamText = "RAM 0/0M";
    private static float cachedRamRatio = 0.0f;
    private static int cachedGpu = 0;
    private static int cachedCpu = 0;

    // GPU 渲染负荷估算计数器
    private static long frameStartNano = System.nanoTime();
    private static double estimatedGpuLoad = 0.0;

    private static com.sun.management.OperatingSystemMXBean osBean = null;
    static {
        try {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                osBean = sunBean;
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 每帧调用一次，用于累计帧渲染持续时间以准确推算 GPU 吞吐负荷
     */
    public static void onRenderFrame(Minecraft mc) {
        long nowNano = System.nanoTime();
        long frameDuration = nowNano - frameStartNano;
        frameStartNano = nowNano;
        if (frameDuration > 0) {
            double instantLoad = Math.min(100.0, (mc.getFrameTimeNs() / (double) frameDuration) * 100.0);
            estimatedGpuLoad = estimatedGpuLoad * 0.85 + instantLoad * 0.15;
        }

        // 500ms 采样节流，避免高频跳数字
        long now = System.currentTimeMillis();
        if (now - lastSampleTime > 500L) {
            lastSampleTime = now;
            updateHardwareMetrics(mc);
        }
    }

    private static void updateHardwareMetrics(Minecraft mc) {
        // 1. RAM：已用堆内存 / 虚拟机最大分配上限 (-Xmx)
        Runtime r = Runtime.getRuntime();
        long maxMb = r.maxMemory() / (1024 * 1024);
        long totalMb = r.totalMemory() / (1024 * 1024);
        long freeMb = r.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;
        cachedRamText = "RAM " + usedMb + "/" + maxMb + "M";
        cachedRamRatio = maxMb > 0 ? (float) usedMb / (float) maxMb : 0.0f;

        // 2. GPU：双模智能采样
        double nativeGpu = 0.0;
        try {
            nativeGpu = mc.getGpuUtilization();
        } catch (Throwable ignored) {}

        if (nativeGpu > 0.0) {
            cachedGpu = (int) Math.round(nativeGpu);
        } else {
            cachedGpu = (int) Math.round(estimatedGpuLoad);
        }
        cachedGpu = Mth.clamp(cachedGpu, 0, 100);

        // 3. CPU：操作系统进程负载
        if (osBean != null) {
            try {
                double load = osBean.getCpuLoad();
                if (load < 0) load = osBean.getProcessCpuLoad();
                cachedCpu = load >= 0 ? (int) Math.round(load * 100.0) : 0;
                cachedCpu = Mth.clamp(cachedCpu, 0, 100);
            } catch (Throwable ignored) {}
        }
    }

    public static String getRamText() {
        return cachedRamText;
    }

    public static float getRamRatio() {
        return cachedRamRatio;
    }

    public static int getGpuLoad() {
        return cachedGpu;
    }

    public static int getCpuLoad() {
        return cachedCpu;
    }
}