package com.giothedev.lumora.client.Renderer;

import com.giothedev.lumora.BloomConfig;
import com.giothedev.lumora.client.Debug.LumoraProfiler;
import com.giothedev.lumora.client.Processor.BloomPostProcessor;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class BloomMaskRenderer {
    public static int sampleRange = 32;
    public static final float inflate = 0.02f;

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<BlockPos, Float> visibilityCache = new HashMap<>();


    private static boolean isActive(Level level, BlockPos pos, BlockState state, BloomConfig.BloomData data) {
        if (data.activation == null || "always".equals(data.activation.type)) return true;
        return switch(data.activation.type){
            case "property"->switch(data.activation.key){
                case "lit"->state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
                case "powered"->state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
                default->false;
            };
            case "redstone_power"->level.getBestNeighborSignal(pos)>=data.activation.min;
            default->false;
        };
    }


    public static void render(Minecraft mc) {
        RenderTarget mask = BloomPostProcessor.getBloomMask();
        if (mask == null || mc.level == null) return;

        if (BloomConfig.BLOOM_BLOCKS.isEmpty()) return;

        LumoraProfiler.begin("BloomMask");

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        Level level = mc.level;

        int w = mc.getMainRenderTarget().width;
        int h = mc.getMainRenderTarget().height;

        mask.setClearColor(0,0,0,0);
        mask.clear(Minecraft.ON_OSX);

        // Copy depth
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getMainRenderTarget().frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mask.frameBufferId);
        GL30.glBlitFramebuffer(0,0,w,h,0,0,w,h, GL30.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        mask.bindWrite(false);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();

        RenderSystem.polygonOffset(-1.0f, -1.0f);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);

        BufferBuilder buf = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR
        );

        boolean drew = false;

        int cx = Mth.floor(camPos.x);
        int cy = Mth.floor(camPos.y);
        int cz = Mth.floor(camPos.z);

        double rangeSq = sampleRange * sampleRange;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();

        for (int x = cx - sampleRange; x <= cx + sampleRange; x++) {
            for (int y = cy - sampleRange; y <= cy + sampleRange; y++) {
                for (int z = cz - sampleRange; z <= cz + sampleRange; z++) {
                    double distSq = camPos.distanceToSqr(x+0.5, y+0.5, z+0.5);
                    if (distSq > rangeSq) continue;

                    mp.set(x,y,z);
                    BlockState state = level.getBlockState(mp);
                    if (state.isAir()) continue;

                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    BloomConfig.BloomData data = BloomConfig.BLOOM_BLOCKS.get(id);
                    if (data == null) continue;
                    if(!isActive(level, mp, state, data)) continue;
                    float alpha = data.intensity;

                    float ox = (float)(x - camPos.x);
                    float oy = (float)(y - camPos.y);
                    float oz = (float)(z - camPos.z);

                    VoxelShape shape = state.getShape(level, mp);
                    if (shape.isEmpty()) shape = Shapes.block();
                    shape.forAllBoxes((x0,y0,z0,x1,y1,z1) -> {
                        x0 -= inflate;
                        y0 -= inflate;
                        z0 -= inflate;

                        x1 += inflate;
                        y1 += inflate;
                        z1 += inflate;
                        for (Direction dir : DIRECTIONS) {

                            if (!Block.shouldRenderFace(state, level, mp, dir, mp.relative(dir))) continue;

                            switch (dir) {
                                case UP -> addQuad(buf,
                                        ox+(float)x0, oy+(float)y1, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y1, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y1, oz+(float)z1,
                                        ox+(float)x0, oy+(float)y1, oz+(float)z1,
                                        alpha, data.r, data.g, data.b);

                                case DOWN -> addQuad(buf,
                                        ox+(float)x0, oy+(float)y0, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y0, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y0, oz+(float)z1,
                                        ox+(float)x0, oy+(float)y0, oz+(float)z1,
                                        alpha, data.r, data.g, data.b);

                                case NORTH -> addQuad(buf,
                                        ox+(float)x0, oy+(float)y0, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y0, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y1, oz+(float)z0,
                                        ox+(float)x0, oy+(float)y1, oz+(float)z0,
                                        alpha, data.r, data.g, data.b);

                                case SOUTH -> addQuad(buf,
                                        ox+(float)x0, oy+(float)y0, oz+(float)z1,
                                        ox+(float)x1, oy+(float)y0, oz+(float)z1,
                                        ox+(float)x1, oy+(float)y1, oz+(float)z1,
                                        ox+(float)x0, oy+(float)y1, oz+(float)z1,
                                        alpha, data.r, data.g, data.b);

                                case WEST -> addQuad(buf,
                                        ox+(float)x0, oy+(float)y0, oz+(float)z0,
                                        ox+(float)x0, oy+(float)y0, oz+(float)z1,
                                        ox+(float)x0, oy+(float)y1, oz+(float)z1,
                                        ox+(float)x0, oy+(float)y1, oz+(float)z0,
                                        alpha, data.r, data.g, data.b);

                                case EAST -> addQuad(buf,
                                        ox+(float)x1, oy+(float)y0, oz+(float)z0,
                                        ox+(float)x1, oy+(float)y0, oz+(float)z1,
                                        ox+(float)x1, oy+(float)y1, oz+(float)z1,
                                        ox+(float)x1, oy+(float)y1, oz+(float)z0,
                                        alpha, data.r, data.g, data.b);
                            }
                        }
                    });

                    drew = true;
                }
            }
        }

        if (drew) {
            MeshData mesh = buf.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        RenderSystem.polygonOffset(0.0f, 0.0f);

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);

        mc.getMainRenderTarget().bindWrite(false);

        LumoraProfiler.end("BloomMask");
    }

    // Simplified addQuad - no matrix transform
    private static void addQuad(BufferBuilder buffer,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float a, float r, float g, float b) {
        buffer.addVertex(x0,y0,z0).setColor(r,g,b,a);
        buffer.addVertex(x1,y1,z1).setColor(r,g,b,a);
        buffer.addVertex(x2,y2,z2).setColor(r,g,b,a);
        buffer.addVertex(x3,y3,z3).setColor(r,g,b,a);
    }

// Remove transform() method entirely
}