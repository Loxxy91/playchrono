package dev.playchrono.probe;

import org.lwjgl.glfw.GLFW;

/**
 * Polls keyboard / mouse state straight from GLFW (identical API in every Minecraft version) instead of hooking
 * Minecraft's input classes, whose signatures changed several times since 1.20. Main (render) thread only.
 *
 * <p>Only transitions (press) and mouse movement count as activity: a key held down by a weight for hours is the
 * classic AFK trick and must not look like play.
 */
final class InputPoller {
    /** Every valid GLFW key code (querying an invalid one would raise a GLFW error in the game log). */
    static final int[] KEYS;

    static {
        int[][] ranges = {{32, 32}, {39, 39}, {44, 57}, {59, 59}, {61, 61}, {65, 93}, {96, 96}, {161, 162},
                {256, 269}, {280, 284}, {290, 314}, {320, 336}, {340, 348}};
        int n = 0;
        for (int[] r : ranges) n += r[1] - r[0] + 1;
        KEYS = new int[n];
        int i = 0;
        for (int[] r : ranges) for (int k = r[0]; k <= r[1]; k++) KEYS[i++] = k;
    }

    static final int KEY_SPACE = 32, KEY_ENTER = 257, KEY_KP_ENTER = 335, KEY_F1 = 290;

    private final boolean[] keyDown = new boolean[KEYS.length];
    private final boolean[] btnDown = new boolean[8];
    private final double[] cx = new double[1], cy = new double[1];
    private double lastX = Double.NaN, lastY = Double.NaN;

    void poll(long window, Snapshot s, int hotkey) {
        for (int i = 0; i < KEYS.length; i++) {
            boolean d = GLFW.glfwGetKey(window, KEYS[i]) == GLFW.GLFW_PRESS;
            if (d && !keyDown[i]) {
                s.anyInput = true;
                s.addKey(KEYS[i]);
                if (KEYS[i] == KEY_ENTER || KEYS[i] == KEY_KP_ENTER) s.enterPressed = true;
                if (KEYS[i] == KEY_SPACE) s.spacePressed = true;
                if (KEYS[i] == hotkey) s.hotkeyPressed = true;
            }
            if (d && (KEYS[i] == KEY_ENTER || KEYS[i] == KEY_KP_ENTER)) s.enterDown = true;
            keyDown[i] = d;
        }
        for (int b = 0; b < btnDown.length; b++) {
            boolean d = GLFW.glfwGetMouseButton(window, b) == GLFW.GLFW_PRESS;
            if (d && !btnDown[b]) {
                s.anyInput = true;
                if (b == 0) s.clicksL++;
                else if (b == 1) s.clicksR++;
                else if (b == 2) s.clicksM++;
                else s.clicksX++;
            }
            btnDown[b] = d;
        }
        GLFW.glfwGetCursorPos(window, cx, cy);
        if (!Double.isNaN(lastX)) {
            double d = Math.hypot(cx[0] - lastX, cy[0] - lastY);
            if (d > 0.5 && d < 5000) { // ignore sub-pixel jitter and cursor re-centering jumps
                s.mousePx += d;
                s.anyInput = true;
            }
        }
        lastX = cx[0];
        lastY = cy[0];
    }
}
