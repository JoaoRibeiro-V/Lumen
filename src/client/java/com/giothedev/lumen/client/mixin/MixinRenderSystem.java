package com.giothedev.lumen.client.mixin;

import com.giothedev.lumen.Lumen;
import com.giothedev.lumen.client.Processor.BloomPostProcessor;
import com.giothedev.lumen.client.Renderer.BloomMaskRenderer;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void beforeSwap(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if(mc == null || mc.level == null || mc.screen != null) return;
        BloomPostProcessor.ensureMask(mc);
    }
}