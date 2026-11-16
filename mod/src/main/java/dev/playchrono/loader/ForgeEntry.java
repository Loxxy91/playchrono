package dev.playchrono.loader;

import dev.playchrono.PlayChrono;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge entry point (1.20 .. 26.x). FML constructs this class during mod loading; no event bus or Forge API is
 * needed because the mod only reads game state. On a dedicated server PlayChrono detects the missing client classes
 * and stays idle.
 */
@Mod("playchrono")
public final class ForgeEntry {
    public ForgeEntry() {
        PlayChrono.boot("forge");
    }
}
