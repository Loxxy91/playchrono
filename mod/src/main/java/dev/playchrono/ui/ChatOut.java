package dev.playchrono.ui;

import dev.playchrono.reflect.R;

/** Client-side chat lines (only the local player sees them). Main thread only. */
public final class ChatOut {
    private ChatOut() {}

    public static boolean send(String text) {
        Object mc = R.callStatic("Minecraft.getInstance");
        if (mc == null) return false;
        Object player = R.get(mc, "Minecraft.player");
        if (player == null) return false;
        Object c = R.callStatic("Component.literal", "§b[PlayChrono]§r §7" + text);
        if (c == null) return false;
        Object chat = R.call(R.get(mc, "Minecraft.gui"), "Gui.getChat");
        if (chat != null) {
            if (R.has(chat, "ChatComponent.addMessage")) { R.call(chat, "ChatComponent.addMessage", c); return true; }
            if (R.has(chat, "ChatComponent.addClientSystemMessage")) { R.call(chat, "ChatComponent.addClientSystemMessage", c); return true; }
        }
        if (R.has(player, "Player.displayClientMessage")) { R.call(player, "Player.displayClientMessage", c, false); return true; }
        if (R.has(player, "Player.sendSystemMessage")) { R.call(player, "Player.sendSystemMessage", c); return true; }
        return false;
    }
}
