package dev.playchrono.probe;

/**
 * What the player is doing right now. Stored verbatim in the data files and translated by the dashboard.
 * Groups: play (in-world, no UI) · ui (in-world UI) · menu (outside a world) · load (waiting on the game).
 */
public final class State {
    private State() {}

    // waiting on the game
    public static final String BOOT = "BOOT";             // JVM start -> first menu
    public static final String LOADING = "LOADING";       // connecting / generating / joining a world
    public static final String IG_LOADING = "IG_LOADING"; // dimension change, saving, receiving terrain
    public static final String RELOAD = "RELOAD";         // resource pack reload overlay
    public static final String LAG = "LAG";               // game frozen > 1 s while in a world
    // outside a world
    public static final String TITLE = "TITLE";
    public static final String SP_MENU = "SP_MENU";       // world list / create / edit world
    public static final String MP_MENU = "MP_MENU";       // server list / direct connect / edit server
    public static final String REALMS = "REALMS";
    public static final String OPTIONS = "OPTIONS";
    public static final String MODS = "MODS";             // mod list screens (Mod Menu, Forge/NeoForge mods)
    public static final String MENU = "MENU";             // any other screen outside a world
    public static final String DISCONNECTED = "DISCONNECTED";
    // in a world
    public static final String PLAY = "PLAY";
    public static final String PAUSE = "PAUSE";
    public static final String IG_OPTIONS = "IG_OPTIONS";
    public static final String INVENTORY = "INVENTORY";   // player / creative inventory
    public static final String CONTAINER = "CONTAINER";   // chests, furnaces, crafting tables, villagers...
    public static final String CHAT = "CHAT";
    public static final String DEAD = "DEAD";
    public static final String SLEEP = "SLEEP";
    public static final String BOOK = "BOOK";
    public static final String SIGN = "SIGN";
    public static final String ADVANCEMENTS = "ADVANCEMENTS";
    public static final String STATS = "STATS";
    public static final String CREDITS = "CREDITS";
    public static final String IG_MODS = "IG_MODS";
    public static final String IG_SCREEN = "IG_SCREEN";   // any other screen inside a world

    /** p = playing, u = in-world UI, m = menu, l = loading. */
    public static char group(String st) {
        if (st == null) return 'm';
        switch (st) {
            case PLAY: return 'p';
            case BOOT: case LOADING: case IG_LOADING: case RELOAD: case LAG: return 'l';
            case TITLE: case SP_MENU: case MP_MENU: case REALMS: case OPTIONS: case MODS: case MENU: case DISCONNECTED:
                return 'm';
            default: return 'u';
        }
    }

    public static boolean inWorld(String st) {
        char g = group(st);
        return g == 'p' || g == 'u' || IG_LOADING.equals(st) || LAG.equals(st);
    }
}
