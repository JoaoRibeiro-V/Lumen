package com.giothedev.lumen.client.Processor;

import com.giothedev.lumen.Lumen;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class BloomPostProcessor {
    private static int lastWidth = -1, lastHeight = -1;

    private static Matrix4f savedProjection;
    private static Matrix4f savedModelView;

    private static Matrix4f bobBefore = new Matrix4f();
    private static Matrix4f bobDelta = new Matrix4f();
    private static RenderTarget depthMasked;

    public static void saveBobBefore(PoseStack poseStack) {
        bobBefore = new Matrix4f(poseStack.last().pose());
    }

    public static void saveBobAfter(PoseStack poseStack) {
        Matrix4f after = new Matrix4f(poseStack.last().pose());
        // delta = inverse(before) * after
        bobDelta = new Matrix4f(bobBefore).invert().mul(after);
    }

    public static void saveMatrices(Camera camera) {
        savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        savedModelView = new Matrix4f(bobDelta).mul(new Matrix4f()
                        .rotationX(camera.getXRot() * Mth.DEG_TO_RAD)
                        .rotateY((camera.getYRot() + 180f) * Mth.DEG_TO_RAD));
    }

    public static Matrix4f getSavedProjection() { return savedProjection; }
    public static Matrix4f getSavedModelView() { return savedModelView; }

    public static RenderTarget getBloomMask() {
        return bloomMask;
    }

    private static RenderTarget bloomMask;
    private static RenderTarget mip1; // w/2
    private static RenderTarget mip2; // w/4
    private static RenderTarget mip3; // w/8
    private static RenderTarget mip4; // w/16
    private static RenderTarget mip5; // w/32
    private static RenderTarget mip6; // w/64

    public static void ensureMask(Minecraft mc) {
        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;

        if (bloomMask == null || w != lastWidth || h != lastHeight) {
            if (bloomMask != null) bloomMask.destroyBuffers();
            if (mip1 != null) mip1.destroyBuffers();
            if (mip2 != null) mip2.destroyBuffers();
            if (mip3 != null) mip3.destroyBuffers();
            if (mip4 != null) mip4.destroyBuffers();
            if (mip5 != null) mip5.destroyBuffers();
            if (mip6 != null) mip6.destroyBuffers();
            if (depthMasked != null) depthMasked.destroyBuffers();
            bloomMask = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            bloomMask.setClearColor(0,0,0,0);
            bloomMask.clear(Minecraft.ON_OSX);

            mip1 = new TextureTarget(w / 2, h / 2, false, Minecraft.ON_OSX);
            mip2 = new TextureTarget(w / 4, h / 4, false, Minecraft.ON_OSX);
            mip3 = new TextureTarget(w / 8, h / 8, false, Minecraft.ON_OSX);
            mip4 = new TextureTarget(w / 16, h / 16, false, Minecraft.ON_OSX);
            mip5 = new TextureTarget(w / 32, h / 32, false, Minecraft.ON_OSX);
            mip6 = new TextureTarget(w / 64, h / 64, false, Minecraft.ON_OSX);
            depthMasked = new TextureTarget(w, h, false, Minecraft.ON_OSX);
            lastWidth = w;
            lastHeight = h;
        }
    }

    public static void applyToMain(Minecraft mc) {
        if (bloomMask == null || mip1 == null || mip2 == null || mip3 == null
                || mip4 == null || mip5 == null || mip6 == null) return;

        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;
        int w2 = w/2,   h2 = h/2;
        int w4 = w/4,   h4 = h/4;
        int w8 = w/8,   h8 = h/8;
        int w16 = w/16, h16 = h/16;
        int w32 = w/32, h32 = h/32;
        int w64 = w/64, h64 = h/64;

        // blur system
        blit(bloomMask, mip1, w, h, w2, h2);
        blit(mip1, mip2, w2, h2, w4, h4);
        blit(mip2, mip3, w4, h4, w8, h8);
        blit(mip3, mip4, w8, h8, w16, h16);
        blit(mip4, mip5, w16, h16, w32, h32);
        blit(mip5, mip6, w32, h32, w64, h64);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        blit(mip6, mip5, w64, h64, w32, h32);
        blit(mip5, mip4, w32, h32, w16, h16);
        blit(mip4, mip3, w16, h16, w8,  h8);
        blit(mip3, mip2, w8,  h8,  w4,  h4);
        blit(mip2, mip1, w4,  h4,  w2,  h2);
        blit(mip1, bloomMask, w2, h2, w, h);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

        bloomMask.blitToScreen(w, h, false);

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        mc.getMainRenderTarget().bindWrite(false);
    }

    // blit applier
    private static void blit(RenderTarget src, RenderTarget dst,
                             int srcW, int srcH, int dstW, int dstH) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, srcW, srcH, 0, 0, dstW, dstH,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
    }
}