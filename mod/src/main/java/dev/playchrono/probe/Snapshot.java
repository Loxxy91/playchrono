package dev.playchrono.probe;

/** Everything read from the game in one main-thread probe. Plain data, handed to the tracker thread. */
public final class Snapshot {
    public long nanos, wall;
    public boolean mcReady;
    public String state = State.BOOT;
    public String screen;            // readable screen class name, null when no screen
    public boolean inWorld;

    // context
    public String ctx;               // server:host | world:Name | realm:Name | lan:Name
    public String ctxName, serverBrand, serverVersion;
    public boolean lanOpen;
    public String dim, biome, gameMode, vehicle;

    // player
    public boolean hasPos;
    public double x, y, z;
    public boolean sprint, swim, sneak, ride, inWater, inLava, underwater, onFire, onGround, elytra, flying, sleeping;
    public float health = -1, maxHealth = -1;
    public int food = -1, xpLevel = -1, armor = -1, slot = -1;
    public long dayTime = -1;
    public boolean rain, thunder;
    public int ping = -1, players = -1;

    // client / window
    public int fps = -1;
    public boolean focused = true, iconified, fullscreen;
    public int winW, winH;
    public String lang, username, uuid, gpu, launchedVersion;
    public int renderDistance = -1, fov = -1;
    public String resourcePacks;

    // input since previous probe
    public boolean anyInput, enterPressed, enterDown, spacePressed, hotkeyPressed;
    public int keyPresses, clicksL, clicksR, clicksM, clicksX;
    public double mousePx;
    public int[] keys = new int[8];
    public int keyCount;
    public String chatText;          // current chat box content while the chat screen is open
    public String disconnectReason;
    public int chatIn;               // chat lines received since previous probe (counted, never stored)
    public java.util.List<String> deathMsgs; // death messages seen: "me|attack.lava" (yours) or "|fall" (someone else)

    void addKey(int code) {
        keyPresses++;
        if (keyCount == keys.length) keys = java.util.Arrays.copyOf(keys, keys.length * 2);
        keys[keyCount++] = code;
    }
}
