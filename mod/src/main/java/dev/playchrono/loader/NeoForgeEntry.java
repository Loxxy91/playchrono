package dev.playchrono.loader;

import dev.playchrono.PlayChrono;
import net.neoforged.fml.common.Mod;

/** NeoForge entry point (20.2 .. 26.x). Same reasoning as {@link ForgeEntry}. */
@Mod("playchrono")
public final class NeoForgeEntry {
    public NeoForgeEntry() {
        PlayChrono.boot("neoforge");
    }
}
