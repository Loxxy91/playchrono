package dev.playchrono.loader;

import dev.playchrono.PlayChrono;
import net.fabricmc.api.ClientModInitializer;

/** Fabric and Quilt entry point (declared in fabric.mod.json, client environment only). */
public final class FabricEntry implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlayChrono.boot("fabric");
    }
}
