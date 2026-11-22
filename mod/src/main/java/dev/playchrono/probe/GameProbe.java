package dev.playchrono.probe;

import dev.playchrono.reflect.R;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the game state. Runs ONLY on Minecraft's main thread (posted through Minecraft's own Executor), never
 * mutates anything, and never throws: a member missing in some version just leaves the matching field empty.
 */
public final class GameProbe {
    private final InputPoller input = new InputPoller();
    private final int hotkey;
    private Object mc;
    private long window;
    private Boolean screenOnMinecraft, overlayOnMinecraft;
    private boolean bootDone;

    // caches refreshed at low frequency
    private long lastSlow, lastVerySlow;
    private Object ctxLevel;
    private String ctx, ctxName, serverBrand, serverVersion, dim;
    private boolean lanOpen;
    private String biome, gameMode, vehicle, lang, username, uuid, gpu, launched, packs;
    private long dayTime = -1;
    private boolean rain, thunder, underwater, onFire, fullscreen;
    private int ping = -1, players = -1, food = -1, xp = -1, armor = -1, renderDistance = -1, fov = -1, winW, winH;
    private Object lastDisconnectScreen;
    private String disconnectReason;

    public GameProbe(int hotkey) { this.hotkey = hotkey; }

    public Snapshot probe() {
        Snapshot s = new Snapshot();
        s.nanos = System.nanoTime();
        s.wall = System.currentTimeMillis();
        if (mc == null) mc = R.callStatic("Minecraft.getInstance");
        if (mc == null) return s;
        s.mcReady = true;
        boolean slow = s.wall - lastSlow >= 1000;
        boolean verySlow = s.wall - lastVerySlow >= 10_000;
        if (slow) lastSlow = s.wall;
        if (verySlow) lastVerySlow = s.wall;

        long win = window();
        if (win != 0) {
            try {
                input.poll(win, s, hotkey);
                s.focused = GLFW.glfwGetWindowAttrib(win, GLFW.GLFW_FOCUSED) != 0;
                s.iconified = GLFW.glfwGetWindowAttrib(win, GLFW.GLFW_ICONIFIED) != 0;
                if (slow) {
                    fullscreen = GLFW.glfwGetWindowMonitor(win) != 0;
                    int[] w = new int[1], h = new int[1];
                    GLFW.glfwGetWindowSize(win, w, h);
                    winW = w[0];
                    winH = h[0];
                }
            } catch (Throwable t) {
                window = 0; // stale handle (window recreated): resolve again next time
            }
        } else {
            s.focused = R.getBool(mc, "Minecraft.isWindowActive", true);
        }

        Object screen = screen();
        Object overlay = overlay();
        Object level = R.get(mc, "Minecraft.level");
        Object player = R.get(mc, "Minecraft.player");
        s.inWorld = level != null && player != null;
        boolean reloading = overlay != null && R.isInstance("LoadingOverlay", overlay);
        if (!bootDone && !reloading && (screen != null || s.inWorld)) bootDone = true;
        s.screen = screen == null ? null : R.readableName(screen.getClass());
        s.state = classify(screen, s.inWorld, reloading);

        if (screen != null && s.state.equals(State.CHAT)) {
            Object box = R.get(screen, "ChatScreen.input");
            Object v = R.call(box, "EditBox.getValue");
            s.chatText = v == null ? null : v.toString();
        }
        if (screen != null && s.state.equals(State.DISCONNECTED) && screen != lastDisconnectScreen) {
            lastDisconnectScreen = screen;
            Object reason = R.get(screen, "DisconnectedScreen.reason");
            if (reason == null) reason = R.call(R.get(screen, "DisconnectedScreen.details"), "DisconnectionDetails.reason");
            disconnectReason = R.text(reason);
            s.disconnectReason = disconnectReason;
        }

        if (s.inWorld) world(level, player, s, slow, verySlow);
        else ctxLevel = null;

        Object fps = R.call(mc, "Minecraft.getFps");
        if (fps instanceof Integer) s.fps = (Integer) fps;
        if (verySlow || username == null) client();
        if (s.inWorld) chat(s);
        s.lang = lang;
        s.username = username;
        s.uuid = uuid;
        s.gpu = gpu;
        s.launchedVersion = launched;
        s.renderDistance = renderDistance;
        s.fov = fov;
        s.resourcePacks = packs;
        s.fullscreen = fullscreen;
        s.winW = winW;
        s.winH = winH;
        return s;
    }

    // ------------------------------------------------------------------ received chat
    /** Chat history entries already seen, by identity (the game keeps the last 100, newest first). */
    private java.util.Set<Object> seenMessages;

    /**
     * Counts the chat lines that arrived since the last probe and spots death messages by their translation key
     * ("death.attack.lava"), which is the same in every language. Nothing else is read or kept: no text, no names.
     */
    private void chat(Snapshot s) {
        Object all = R.get(R.call(R.get(mc, "Minecraft.gui"), "Gui.getChat"), "ChatComponent.allMessages");
        if (!(all instanceof java.util.List)) return;
        java.util.List<?> list = (java.util.List<?>) all;
        if (seenMessages != null && (list.isEmpty() ? seenMessages.isEmpty() : seenMessages.contains(list.get(0)))) return; // nothing new
        java.util.Set<Object> now = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        now.addAll(list);
        if (seenMessages != null) {
            for (Object m : list) {
                if (seenMessages.contains(m)) continue;
                Object content = R.call(m, "GuiMessage.content");
                String text = R.text(content);
                if (text != null && text.startsWith("§b[PlayChrono]")) continue; // our own lines
                s.chatIn++;
                String[] death = deathKey(content, 0);
                if (death == null) continue;
                boolean mine = isMe(death[1]) || (death[1] == null && State.DEAD.equals(s.state));
                if (s.deathMsgs == null) s.deathMsgs = new java.util.ArrayList<>(2);
                s.deathMsgs.add((mine ? "me|" : "|") + death[0]);
            }
        } // first look: the history already on screen is not counted
        seenMessages = now;
    }

    /** The victim's displayed name is the account name, possibly with a server rank around it ("[VIP] Steve"). */
    private boolean isMe(String victim) { return isMe(victim, username); }

    public static boolean isMe(String victim, String username) {
        if (victim == null || username == null || username.isEmpty()) return false;
        String v = victim.replaceAll("§.", "");
        return v.equals(username) || java.util.regex.Pattern.compile("(^|[^A-Za-z0-9_])" + java.util.regex.Pattern.quote(username) + "([^A-Za-z0-9_]|$)").matcher(v).find();
    }

    /** {cause, victim name} when the component (or one of its direct parts) is a vanilla death message. */
    private static String[] deathKey(Object component, int depth) {
        if (component == null || depth > 1) return null;
        Object c = R.call(component, "Component.getContents");
        if (R.isInstance("TranslatableContents", c)) {
            Object k = R.call(c, "TranslatableContents.getKey");
            if (k instanceof String && ((String) k).startsWith("death.")) {
                String cause = ((String) k).substring(6).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._]", "");
                if (cause.length() > 48) cause = cause.substring(0, 48);
                Object args = R.call(c, "TranslatableContents.getArgs");
                String victim = args instanceof Object[] && ((Object[]) args).length > 0 ? R.text(((Object[]) args)[0]) : null;
                return cause.isEmpty() ? null : new String[]{cause, victim};
            }
        }
        Object sib = R.call(component, "Component.getSiblings");
        if (sib instanceof java.util.List) {
            for (Object o : (java.util.List<?>) sib) {
                String[] r = deathKey(o, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ screen classification
    private Object screen() {
        if (screenOnMinecraft == null) screenOnMinecraft = R.has(mc, "Minecraft.screen");
        if (screenOnMinecraft) return R.get(mc, "Minecraft.screen");
        return R.call(R.get(mc, "Minecraft.gui"), "Gui.screen"); // 26.2+
    }

    private Object overlay() {
        if (overlayOnMinecraft == null) overlayOnMinecraft = R.has(mc, "Minecraft.overlay");
        if (overlayOnMinecraft) return R.get(mc, "Minecraft.overlay");
        return R.call(R.get(mc, "Minecraft.gui"), "Gui.overlay"); // 26.2+
    }

    private String classify(Object sc, boolean inWorld, boolean reloading) {
        if (!bootDone) return State.BOOT;
        if (reloading) return State.RELOAD;
        if (sc == null) return inWorld ? State.PLAY : State.LOADING;
        if (is(sc, "DeathScreen")) return State.DEAD;
        if (is(sc, "WinScreen")) return State.CREDITS;
        if (is(sc, "InBedChatScreen")) return State.SLEEP;
        if (is(sc, "ChatScreen")) return State.CHAT;
        if (is(sc, "InventoryScreen") || is(sc, "CreativeModeInventoryScreen")) return State.INVENTORY;
        if (is(sc, "AbstractContainerScreen")) return State.CONTAINER;
        if (is(sc, "BookViewScreen") || is(sc, "BookEditScreen")) return State.BOOK;
        if (is(sc, "AbstractSignEditScreen")) return State.SIGN;
        if (is(sc, "AdvancementsScreen")) return State.ADVANCEMENTS;
        if (is(sc, "StatsScreen")) return State.STATS;
        if (is(sc, "PauseScreen")) return State.PAUSE;
        if (is(sc, "OptionsScreen") || is(sc, "OptionsSubScreen") || is(sc, "PackSelectionScreen")
                || is(sc, "AccessibilityOnboardingScreen")) return inWorld ? State.IG_OPTIONS : State.OPTIONS;
        if (is(sc, "ConnectScreen") || is(sc, "ProgressScreen") || is(sc, "LevelLoadingScreen")
                || is(sc, "ReceivingLevelScreen") || is(sc, "GenericMessageScreen") || is(sc, "GenericWaitingScreen"))
            return inWorld ? State.IG_LOADING : State.LOADING;
        if (is(sc, "DisconnectedScreen")) return State.DISCONNECTED;
        String n = sc.getClass().getName().toLowerCase(Locale.ROOT);
        if (n.contains("modlist") || n.contains("modsscreen") || n.contains("modmenu")) return inWorld ? State.IG_MODS : State.MODS;
        if (inWorld) return State.IG_SCREEN;
        if (is(sc, "TitleScreen")) return State.TITLE;
        if (is(sc, "JoinMultiplayerScreen") || is(sc, "DirectJoinServerScreen") || is(sc, "EditServerScreen")) return State.MP_MENU;
        if (is(sc, "SelectWorldScreen") || is(sc, "CreateWorldScreen") || is(sc, "EditWorldScreen")) return State.SP_MENU;
        if (is(sc, "RealmsMainScreen") || is(sc, "RealmsScreen") || n.startsWith("com.mojang.realmsclient")) return State.REALMS;
        return State.MENU;
    }

    private static boolean is(Object o, String key) { return R.isInstance(key, o); }

    // ------------------------------------------------------------------ world
    private void world(Object level, Object player, Snapshot s, boolean slow, boolean verySlow) {
        if (level != ctxLevel) { // world, server or dimension changed
            ctxLevel = level;
            context();
            dim = key(R.call(level, "Level.dimension"));
            biome = null;
            dayTime = -1;
        } else if (verySlow) {
            context(); // server brand / LAN state arrive a bit after joining
        }
        s.ctx = ctx;
        s.ctxName = ctxName;
        s.serverBrand = serverBrand;
        s.serverVersion = serverVersion;
        s.lanOpen = lanOpen;
        s.dim = dim;

        Object x = R.call(player, "Entity.getX");
        if (x instanceof Double) {
            s.hasPos = true;
            s.x = (Double) x;
            s.y = R.getNum(player, "Entity.getY", 0);
            s.z = R.getNum(player, "Entity.getZ", 0);
        }
        s.sprint = R.getBool(player, "Entity.isSprinting", false);
        s.swim = R.getBool(player, "Entity.isSwimming", false);
        s.sneak = R.getBool(player, "Entity.isShiftKeyDown", false);
        s.ride = R.getBool(player, "Entity.isPassenger", false);
        s.inWater = R.getBool(player, "Entity.isInWater", false);
        s.inLava = R.getBool(player, "Entity.isInLava", false);
        s.onGround = R.getBool(player, "Entity.onGround", false);
        s.elytra = R.getBool(player, "LivingEntity.isFallFlying", false);
        s.sleeping = R.getBool(player, "LivingEntity.isSleeping", false);
        s.flying = R.getBool(R.call(player, "Player.getAbilities"), "Abilities.flying", false);
        s.health = (float) R.getNum(player, "LivingEntity.getHealth", -1);
        s.maxHealth = (float) R.getNum(player, "LivingEntity.getMaxHealth", -1);
        Object inv = R.call(player, "Player.getInventory");
        Object slot = R.call(inv, "Inventory.getSelectedSlot");
        if (slot == null) slot = R.get(inv, "Inventory.selected");
        s.slot = slot instanceof Integer ? (Integer) slot : -1;

        if (slow) {
            underwater = R.getBool(player, "Entity.isUnderWater", false);
            onFire = R.getBool(player, "Entity.isOnFire", false);
            food = (int) R.getNum(R.call(player, "Player.getFoodData"), "FoodData.getFoodLevel", -1);
            xp = (int) R.getNum(player, "Player.experienceLevel", -1);
            armor = (int) R.getNum(player, "LivingEntity.getArmorValue", -1);
            Object mode = R.call(R.get(mc, "Minecraft.gameMode"), "MultiPlayerGameMode.getPlayerMode");
            gameMode = mode instanceof Enum ? ((Enum<?>) mode).name().toLowerCase(Locale.ROOT) : null;
            vehicle = s.ride ? String.valueOf(R.call(R.call(R.call(player, "Entity.getVehicle"), "Entity.getType"), "EntityType.toShortString")) : null;
            Object pos = R.call(player, "Entity.blockPosition");
            Object holder = pos == null ? null : R.call(level, "LevelReader.getBiome", pos);
            String b = holder == null ? null : key(R.call(holder, "Holder.unwrapKey"));
            if (b == null && holder != null) b = R.getStr(holder, "Holder.getRegisteredName");
            biome = b;
            Object t = R.call(level, "Level.getDayTime");
            if (t == null) t = R.call(level, "Level.getDefaultClockTime");
            dayTime = t instanceof Long ? (Long) t : -1;
            rain = R.getBool(level, "Level.isRaining", false);
            thunder = R.getBool(level, "Level.isThundering", false);
            if (verySlow || ping < 0) network(player);
        }
        s.underwater = underwater;
        s.onFire = onFire;
        s.food = food;
        s.xpLevel = xp;
        s.armor = armor;
        s.gameMode = gameMode;
        s.vehicle = vehicle;
        s.biome = biome;
        s.dayTime = dayTime;
        s.rain = rain;
        s.thunder = thunder;
        s.ping = ping;
        s.players = players;
    }

    private void context() {
        lanOpen = false;
        serverBrand = null;
        serverVersion = null;
        Object sp = R.call(mc, "Minecraft.getSingleplayerServer");
        if (sp != null) {
            String name = R.getStr(R.call(sp, "MinecraftServer.getWorldData"), "WorldData.getLevelName");
            ctxName = name == null ? "?" : name;
            ctx = "world:" + ctxName;
            lanOpen = R.getBool(sp, "MinecraftServer.isPublished", false);
            serverBrand = "integrated";
            return;
        }
        Object data = R.call(mc, "Minecraft.getCurrentServer");
        Object conn = R.call(mc, "Minecraft.getConnection");
        Object brand = R.call(conn, "ClientCommonPacketListenerImpl.serverBrand");
        serverBrand = brand == null ? null : brand.toString();
        if (data == null) {
            ctx = "server:unknown";
            ctxName = "?";
            return;
        }
        String name = R.getStr(data, "ServerData.name");
        String ip = R.getStr(data, "ServerData.ip");
        serverVersion = R.text(R.get(data, "ServerData.version"));
        ctxName = name;
        if (R.getBool(data, "ServerData.isRealm", false)) ctx = "realm:" + name;
        else if (R.getBool(data, "ServerData.isLan", false)) ctx = "lan:" + name;
        else ctx = "server:" + normalizeHost(ip);
    }

    private void network(Object player) {
        Object conn = R.call(mc, "Minecraft.getConnection");
        if (conn == null || ctx == null || ctx.startsWith("world:")) { ping = -1; players = -1; return; }
        Object id = R.call(player, "Entity.getUUID");
        Object info = id instanceof UUID ? R.call(conn, "ClientPacketListener.getPlayerInfo", id) : null;
        Object lat = R.call(info, "PlayerInfo.getLatency");
        ping = lat instanceof Integer ? (Integer) lat : -1;
        Object online = R.call(conn, "ClientPacketListener.getOnlinePlayers");
        players = online instanceof Collection ? ((Collection<?>) online).size() : -1;
    }

    private void client() {
        Object user = R.call(mc, "Minecraft.getUser");
        if (user != null) {
            username = R.getStr(user, "User.getName");
            Object id = R.call(user, "User.getProfileId");
            uuid = id == null ? null : id.toString();
        }
        Object opts = R.get(mc, "Minecraft.options");
        if (opts != null) {
            lang = R.getStr(opts, "Options.languageCode");
            Object rd = R.call(R.call(opts, "Options.renderDistance"), "OptionInstance.get");
            renderDistance = rd instanceof Integer ? (Integer) rd : -1;
            Object f = R.call(R.call(opts, "Options.fov"), "OptionInstance.get");
            fov = f instanceof Integer ? (Integer) f : -1;
            Object p = R.get(opts, "Options.resourcePacks");
            packs = p == null ? null : p.toString();
        }
        if (launched == null) launched = R.getStr(mc, "Minecraft.getLaunchedVersion");
        if (gpu == null) gpu = glRenderer();
    }

    private long window() {
        if (window != 0) return window;
        Object w = R.call(mc, "Minecraft.getWindow");
        Object h = R.call(w, "Window.getWindow");
        if (h == null) h = R.call(w, "Window.handle");
        if (h instanceof Long && (Long) h != 0) return window = (Long) h;
        try {
            return window = GLFW.glfwGetCurrentContext();
        } catch (Throwable t) {
            return 0;
        }
    }

    private String glRenderer() {
        try {
            if (GLFW.glfwGetCurrentContext() == 0) return "n/a";
            Class<?> gl = Class.forName("org.lwjgl.opengl.GL11");
            Object r = gl.getMethod("glGetString", int.class).invoke(null, 0x1F01); // GL_RENDERER
            return r == null ? "n/a" : r.toString();
        } catch (Throwable t) {
            return "n/a";
        }
    }

    // ------------------------------------------------------------------ helpers
    /** "ResourceKey[minecraft:dimension / minecraft:the_nether]" (or Optional of it) -> "minecraft:the_nether". */
    static String key(Object k) {
        if (k instanceof Optional) k = ((Optional<?>) k).orElse(null);
        if (k == null) return null;
        String s = k.toString();
        int i = s.lastIndexOf(" / ");
        if (i >= 0) {
            int end = s.indexOf(']', i);
            return s.substring(i + 3, end > 0 ? end : s.length()).trim();
        }
        return s;
    }

    static String normalizeHost(String ip) {
        if (ip == null || ip.isEmpty()) return "unknown";
        String h = ip.trim().toLowerCase(Locale.ROOT);
        if (h.endsWith(":25565")) h = h.substring(0, h.length() - 6);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }
}
