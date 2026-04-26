package com.giothedev.lumen.client.Renderer;

import com.giothedev.lumen.BloomConfig;
import com.giothedev.lumen.Lumen;
import com.giothedev.lumen.client.Debug.LumenProfiler;
import com.giothedev.lumen.client.Processor.BloomPostProcessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

@Environment(EnvType.CLIENT)
public class BloomMaskRenderer {

    public static int sampleCount = 12;
    public static int sampleRange = 30;
    public static double sampleRadius = 0.3d;
    public static double visibilityPow = 0.5d;

    private static final Direction[] DIRECTIONS = Direction.values();

    // just transforms a vec
    private static Vector4f transform(Matrix4f mat, float x, float y, float z) {
        Vector4f tmp = new Vector4f(x, y, z, 1.0f);
        mat.transform(tmp);
        return tmp;
    }

    private static void addQuad(BufferBuilder buffer, Matrix4f mv,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float alpha,
                                float r, float g, float b) {
        Vector4f v1 = transform(mv, x0, y0, z0);
        buffer.addVertex(v1.x, v1.y, v1.z).setColor(r, g, b, alpha);

        Vector4f v2 = transform(mv, x1, y1, z1);
        buffer.addVertex(v2.x, v2.y, v2.z).setColor(r, g, b, alpha);

        Vector4f v3 = transform(mv, x2, y2, z2);
        buffer.addVertex(v3.x, v3.y, v3.z).setColor(r, g, b, alpha);

        Vector4f v4 = transform(mv, x3, y3, z3);
        buffer.addVertex(v4.x, v4.y, v4.z).setColor(r, g, b, alpha);
    }

    private static boolean isActive(Level level, BlockPos pos, BlockState state, BloomConfig.BloomData data) {

        if(data.activation == null || "always".equals(data.activation.type)){
            return true;
        }

        return switch(data.activation.type){
            case "property"->{
                String key = data.activation.key;
                yield switch(key){
                    case "lit"->state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
                    case "powered"->state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
                    default->false;
                };
            }
            case "redstone_power"->level.getBestNeighborSignal(pos) >= data.activation.min;
            default->false;
        };
    }

    private static boolean isVisible(Level level, Vec3 from, Vec3 to, BlockPos target, Minecraft mc) {
        BlockHitResult hit = level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if(hit.getType() == HitResult.Type.MISS) return true;

        BlockState st = level.getBlockState(hit.getBlockPos());
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        BloomConfig.BloomData data = BloomConfig.BLOOM_BLOCKS.get(id);

        // quick override for non-occluding bloom blocks
        if(data != null && !data.occludes) return true;

        return hit.getBlockPos().equals(target);
    }

    private static float computeVisibility(Level level, Vec3 camPos, BlockPos pos, Vec3 center, int samples, Minecraft mc) {
        int visible = 0;
        if (isVisible(level, camPos, center, pos, mc)) visible++;
        for(int i=1;i<samples;i++){
            double phi = Math.acos(1-2.0 * i / samples);
            double theta = Math.PI * (1+Math.sqrt(5)) * i;
            double sx = Math.cos(theta) * Math.sin(phi);
            double sy = Math.sin(theta) * Math.sin(phi);
            double sz = Math.cos(phi);

            Vec3 samplePoint = center.add(sx * sampleRadius, sy * sampleRadius, sz * sampleRadius);
            if(isVisible(level, camPos, samplePoint, pos, mc)){
                visible++;
            }
        }
        return (float) visible/samples;
    }

    // MAIN RENDER
    public static void render(Minecraft mc) {
        RenderTarget mask = BloomPostProcessor.getBloomMask();
        if(mask == null) return;

        Matrix4f proj = BloomPostProcessor.getSavedProjection();
        Matrix4f mv = BloomPostProcessor.getSavedModelView();

        if(proj == null || mv == null || BloomConfig.BLOOM_BLOCKS.isEmpty() || mc.level == null){
            mc.getMainRenderTarget().bindWrite(false);
            return;
        }

        LumenProfiler.begin("Render");

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();

        int cx = Mth.floor(camPos.x);
        int cy = Mth.floor(camPos.y);
        int cz = Mth.floor(camPos.z);

        Level level = mc.level;

        Vec3 look = new Vec3(cam.getLookVector());

        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;

        mask.setClearColor(0f, 0f, 0f, 0f);
        mask.clear(Minecraft.ON_OSX);

        // copy depth over
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mask.frameBufferId);

        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL30.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        mask.bindWrite(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        boolean drewSomething = false;

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for(int x=cx-sampleRange;x<=cx+sampleRange;x++){
            for(int y=cy-sampleRange;y<=cy+sampleRange;y++){
                for(int z=cz-sampleRange;z<=cz+sampleRange;z++){
                    mp.set(x, y, z);
                    BlockState state = level.getBlockState(mp);
                    if(state.isAir()) continue;

                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    BloomConfig.BloomData data = BloomConfig.BLOOM_BLOCKS.get(id);
                    if(data == null) continue;

                    if(!isActive(level, mp, state, data)) continue;

                    Vec3 center = Vec3.atCenterOf(mp);

                    Vec3 to = center.subtract(camPos);
                    double len = Math.sqrt(to.x * to.x + to.y * to.y + to.z * to.z);
                    if(len < 0.0001) continue;

                    double dot = (to.x * look.x + to.y * look.y + to.z * look.z)/len;
                    float angleVis = (float) Mth.clamp((dot - 0.2)/(0.5 - 0.2), 0.0, 1.0);
                    angleVis = angleVis * angleVis * (3-2 * angleVis);

                    if(angleVis <= 0.01f) continue;
                    float vis = computeVisibility(level, camPos, mp, center, sampleCount, mc);
                    vis = (float) Math.pow(vis, visibilityPow);
                    vis *= angleVis;

                    if(vis <= 0.02f) continue;

                    drewSomething = true;

                    float ox = (float)(x-camPos.x);
                    float oy = (float)(y-camPos.y);
                    float oz = (float)(z-camPos.z);

                    float alpha = data.intensity * vis;

                    VoxelShape shape = state.getShape(level, mp);
                    if (shape.isEmpty()) shape = Shapes.block();

                    shape.forAllBoxes((x0, y0, z0, x1, y1, z1)->{
                        for(Direction dir : DIRECTIONS){
                            if(!Block.shouldRenderFace(state, level, mp, dir, mp.relative(dir))) continue;
                            switch(dir){
                                case UP->addQuad(buf, mv,
                                        ox + (float)x0, oy + (float)y1, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y1, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y1, oz + (float)z1,
                                        ox + (float)x0, oy + (float)y1, oz + (float)z1,
                                        alpha, data.r, data.g, data.b);

                                case DOWN->addQuad(buf, mv,
                                        ox + (float)x0, oy + (float)y0, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y0, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y0, oz + (float)z1,
                                        ox + (float)x0, oy + (float)y0, oz + (float)z1,
                                        alpha, data.r, data.g, data.b);

                                case NORTH->addQuad(buf, mv,
                                        ox + (float)x0, oy + (float)y0, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y0, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y1, oz + (float)z0,
                                        ox + (float)x0, oy + (float)y1, oz + (float)z0,
                                        alpha, data.r, data.g, data.b);

                                case SOUTH->addQuad(buf, mv,
                                        ox + (float)x0, oy + (float)y0, oz + (float)z1,
                                        ox + (float)x1, oy + (float)y0, oz + (float)z1,
                                        ox + (float)x1, oy + (float)y1, oz + (float)z1,
                                        ox + (float)x0, oy + (float)y1, oz + (float)z1,
                                        alpha, data.r, data.g, data.b);

                                case WEST->addQuad(buf, mv,
                                        ox + (float)x0, oy + (float)y0, oz + (float)z0,
                                        ox + (float)x0, oy + (float)y0, oz + (float)z1,
                                        ox + (float)x0, oy + (float)y1, oz + (float)z1,
                                        ox + (float)x0, oy + (float)y1, oz + (float)z0,
                                        alpha, data.r, data.g, data.b);

                                case EAST->addQuad(buf, mv,
                                        ox + (float)x1, oy + (float)y0, oz + (float)z0,
                                        ox + (float)x1, oy + (float)y0, oz + (float)z1,
                                        ox + (float)x1, oy + (float)y1, oz + (float)z1,
                                        ox + (float)x1, oy + (float)y1, oz + (float)z0,
                                        alpha, data.r, data.g, data.b);
                            }
                        }
                    });
                }
            }
        }

        if(drewSomething){
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);

        mc.getMainRenderTarget().bindWrite(false);

        LumenProfiler.end("Render");
    }
}