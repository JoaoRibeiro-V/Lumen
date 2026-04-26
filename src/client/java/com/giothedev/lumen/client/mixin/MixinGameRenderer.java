package com.giothedev.lumen.client.mixin;

import com.giothedev.lumen.Lumen;
import com.giothedev.lumen.client.Processor.BloomPostProcessor;
import com.giothedev.lumen.client.Renderer.BloomMaskRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void afterLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null) return;
        BloomPostProcessor.saveMatrices(mc.gameRenderer.getMainCamera());
        BloomMaskRenderer.render(mc);
    }

    @Inject(method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.BEFORE
    ))
    private void beforeGui(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if(mc.level == null) return;
        BloomPostProcessor.applyToMain(mc);
    }
}