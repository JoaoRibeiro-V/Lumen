package com.giothedev.lumora.client;

import net.fabricmc.loader.api.FabricLoader;

public class IrisCompat {

    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");

    public static boolean areShadersActive() {
        if (!IRIS_LOADED) return false;
        return net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse();
    }
}
