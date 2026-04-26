package com.giothedev.lumen.client.mixin;

import com.giothedev.lumen.client.Processor.BloomPostProcessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRendererBob {

    @Inject(method = "bobView", at = @At("HEAD"))
    private void beforeBobView(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        BloomPostProcessor.saveBobBefore(poseStack);
    }

    @Inject(method = "bobView", at = @At("RETURN"))
    private void afterBobView(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        BloomPostProcessor.saveBobAfter(poseStack);
    }
}
