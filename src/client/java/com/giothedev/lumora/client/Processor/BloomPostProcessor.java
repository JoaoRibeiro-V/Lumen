package com.giothedev.lumora.client.Processor;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL30;

public final class BloomPostProcessor {

    private static RenderTarget bloomMask;
    private static PostChain blurChain;

    private static int lastW = -1, lastH = -1;

    public static void ensure(Minecraft mc) {
        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;

        if (bloomMask != null && w == lastW && h == lastH) return;

        invalidate();

        bloomMask = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        bloomMask.setClearColor(0, 0, 0, 0);

        try {
            // Create chain with bloomMask as input
            blurChain = new PostChain(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    bloomMask,
                    ResourceLocation.fromNamespaceAndPath("lumora", "shaders/post/bloom.json")
            );

            blurChain.resize(w, h);

        } catch (Exception e) {
            throw new RuntimeException("Bloom chain failed", e);
        }

        lastW = w;
        lastH = h;
    }

    public static RenderTarget getBloomMask() {
        return bloomMask;
    }

    public static void applyToMain(Minecraft mc) {
        if (blurChain == null || bloomMask == null) return;

        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;

        // Process the bloom chain (blur the bloom mask)

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        blurChain.process(mc.getFrameTimeNs());

        // Get the output and composite it onto main
        RenderTarget output = blurChain.getTempTarget("output");
        if (output == null) {
            System.err.println("Bloom output is NULL!");
            return;
        }

        // Additive blend the bloom result onto the main render target
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        output.blitToScreen(w, h, false);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        mc.getMainRenderTarget().bindWrite(false);
    }

    public static void invalidate() {
        if (blurChain != null) {
            blurChain.close();
            blurChain = null;
        }
        if (bloomMask != null) {
            bloomMask.destroyBuffers();
            bloomMask = null;
        }
        lastW = -1;
        lastH = -1;
    }
}