package com.giothedev.lumora.client;

import com.giothedev.lumora.BloomConfig;
import com.giothedev.lumora.client.Debug.LumoraProfiler;
import com.giothedev.lumora.client.Processor.BloomPostProcessor;
import com.giothedev.lumora.client.Renderer.BloomMaskRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public class LumoraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        /*
        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            LumoraProfiler.render(graphics);
        });*/
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ResourceLocation.fromNamespaceAndPath("lumora", "bloom_config");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                BloomConfig.load(manager);
                BloomPostProcessor.invalidate(); // reload shaders when resources reload
            }
        });

        // Ensure the post-processor is ready at the start of each world render frame
        WorldRenderEvents.START.register(context -> {
            BloomPostProcessor.ensure(Minecraft.getInstance());
        });

        // After all world rendering: draw the bloom mask and apply the blur
        WorldRenderEvents.LAST.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            BloomMaskRenderer.render(mc);
            BloomPostProcessor.applyToMain(mc);
        });
    }
}