package org.lwjgl.glfw;

/**
 * COMPILE-ONLY STUB. Never packaged: at runtime the real LWJGL 3 class shipped with every Minecraft version is used.
 * Only signatures that exist unchanged in LWJGL 3.2 .. 3.3.x are declared here.
 */
public final class GLFW {
    public static final int GLFW_PRESS = 1;
    public static final int GLFW_FOCUSED = 0x20001;
    public static final int GLFW_ICONIFIED = 0x20002;

    private GLFW() {}

    public static long glfwGetCurrentContext() { throw new UnsupportedOperationException("stub"); }

    public static int glfwGetKey(long window, int key) { throw new UnsupportedOperationException("stub"); }

    public static int glfwGetMouseButton(long window, int button) { throw new UnsupportedOperationException("stub"); }

    public static void glfwGetCursorPos(long window, double[] xpos, double[] ypos) { throw new UnsupportedOperationException("stub"); }

    public static int glfwGetWindowAttrib(long window, int attrib) { throw new UnsupportedOperationException("stub"); }

    public static long glfwGetWindowMonitor(long window) { throw new UnsupportedOperationException("stub"); }

    public static void glfwGetWindowSize(long window, int[] width, int[] height) { throw new UnsupportedOperationException("stub"); }
}
