package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cryo.zombierool.configuration.ZRClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * One puddle per blood drop, stuck to the face it hit.
 * Bigger when the drop was moving faster.
 */
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BloodSplatters {
    private static final List<Splat> SPLATS = new ArrayList<>();
    private static final int MAX = 800;
    private static final int LIFETIME = 2400;
    private static int nextLayer;
    private static boolean sawMatch;

    private BloodSplatters() {}

    public static void add(Vec3 pos, Direction face, float speed, float red, float green, float blue) {
        if (HalloweenManager.isHalloweenPeriod() || ZRClientConfig.isGoreReduced() || face == null || pos == null) return;
        float half = Mth.clamp(0.16F + speed * 1.6F, 0.14F, 1.25F);
        float yaw = (float) (Math.random() * Math.PI * 2.0);
        int layer = nextLayer++ & 7;
        int light = sampleLight(pos, face);
        SPLATS.add(new Splat(pos, face, half, yaw, layer, light, red, green, blue));
        while (SPLATS.size() > MAX) SPLATS.remove(0);
    }

    public static void clear() {
        SPLATS.clear();
        me.cryo.zombierool.client.particle.BloodStainParticle.expireAll();
    }

    public static void clientTick(boolean inWorld, boolean gameRunning) {
        if (!inWorld) {
            if (sawMatch || !SPLATS.isEmpty()) {
                me.cryo.zombierool.client.particle.BloodStainParticle.expireAll();
            }
            SPLATS.clear();
            sawMatch = false;
            return;
        }
        if (gameRunning != sawMatch) {
            SPLATS.clear();
            if (!gameRunning) me.cryo.zombierool.client.particle.BloodStainParticle.expireAll();
            sawMatch = gameRunning;
            return;
        }
        if (!SPLATS.isEmpty()) {
            SPLATS.removeIf(splat -> ++splat.age >= LIFETIME);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPLATS.isEmpty()) return;
        if (HalloweenManager.isHalloweenPeriod() || ZRClientConfig.isGoreReduced()) return;
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (int layer = 0; layer < ZrBloodRenderType.layerCount(); layer++) {
            VertexConsumer vc = null;
            for (Splat splat : SPLATS) {
                if (splat.layer != layer) continue;
                if (vc == null) vc = buffers.getBuffer(ZrBloodRenderType.layer(layer));
                draw(pose, vc, splat);
            }
            if (vc != null) buffers.endBatch(ZrBloodRenderType.layer(layer));
        }
        pose.popPose();
    }

    private static int sampleLight(Vec3 pos, Direction face) {
        var level = Minecraft.getInstance().level;
        if (level == null) return 0xF000F0;
        var sample = net.minecraft.core.BlockPos.containing(
                pos.x + face.getStepX() * 0.08,
                pos.y + face.getStepY() * 0.08,
                pos.z + face.getStepZ() * 0.08);
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, sample);
    }

    private static void draw(PoseStack pose, VertexConsumer vc, Splat splat) {
        float lift = 0.02F + splat.layer * 0.0015F;
        float h = splat.half;
        float cos = Mth.cos(splat.yaw);
        float sin = Mth.sin(splat.yaw);
        float nx = splat.face.getStepX();
        float ny = splat.face.getStepY();
        float nz = splat.face.getStepZ();
        pose.pushPose();
        pose.translate(
                splat.pos.x + nx * lift,
                splat.pos.y + ny * lift,
                splat.pos.z + nz * lift);
        Matrix4f matrix = pose.last().pose();
        float[] xs = new float[4];
        float[] ys = new float[4];
        float[] zs = new float[4];
        float[] us = {0.0F, 0.0F, 1.0F, 1.0F};
        float[] vs = {0.0F, 1.0F, 1.0F, 0.0F};
        for (int i = 0; i < 4; i++) {
            float lx = (i == 0 || i == 1) ? -h : h;
            float ly = (i == 0 || i == 3) ? -h : h;
            float rx = lx * cos - ly * sin;
            float ry = lx * sin + ly * cos;
            switch (splat.face) {
                case UP, DOWN -> {
                    xs[i] = rx;
                    ys[i] = 0.0F;
                    zs[i] = ry;
                }
                case NORTH, SOUTH -> {
                    xs[i] = rx;
                    ys[i] = ry;
                    zs[i] = 0.0F;
                }
                default -> {
                    xs[i] = 0.0F;
                    ys[i] = ry;
                    zs[i] = rx;
                }
            }
        }
        boolean flip = splat.face == Direction.DOWN || splat.face == Direction.SOUTH || splat.face == Direction.WEST;
        int[] order = flip ? new int[] {0, 3, 2, 1} : new int[] {0, 1, 2, 3};
        for (int i : order) {
            vc.vertex(matrix, xs[i], ys[i], zs[i])
                    .color(splat.red, splat.green, splat.blue, 200)
                    .uv(us[i], vs[i])
                    .overlayCoords(0)
                    .uv2(splat.light)
                    .normal(nx, ny, nz)
                    .endVertex();
        }
        pose.popPose();
    }

    private static final class Splat {
        private final Vec3 pos;
        private final Direction face;
        private final float half;
        private final float yaw;
        private final int layer;
        private final int light;
        private final int red;
        private final int green;
        private final int blue;
        private int age;

        private Splat(Vec3 pos, Direction face, float half, float yaw, int layer, int light, float red, float green, float blue) {
            this.pos = pos;
            this.face = face;
            this.half = half;
            this.yaw = yaw;
            this.layer = layer;
            this.light = light;
            this.red = Math.round(red * 255.0F);
            this.green = Math.round(green * 255.0F);
            this.blue = Math.round(blue * 255.0F);
        }
    }
}
