package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
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
    private static final List<ImpactSound> IMPACT_SOUNDS = new ArrayList<>();
    private static final float LARGE_PUDDLE_HALF_SIZE = 0.55F;
    private static final int IMPACT_SOUND_COOLDOWN = 50;
    private static long nextImpactSound;
    private static net.minecraft.client.multiplayer.ClientLevel soundLevel;
    private static final int MAX = 400;
    private static final int LIFETIME = 2400;
    private static int nextLayer;
    private static boolean sawMatch;

    private BloodSplatters() {}

    public static void add(Vec3 pos, Direction face, float speed, float red, float green, float blue) {
        if (HalloweenManager.isHalloweenPeriod() || ZRClientConfig.isGoreReduced() || face == null || pos == null) return;
        var random=net.minecraft.util.RandomSource.create();int seed=random.nextInt();
        boolean wall=face.getAxis()!=Direction.Axis.Y;
        float half = Mth.clamp((0.16F + speed * 1.6F)*(.55F+random.nextFloat()*.9F)
            *(wall?.45F:.7F), 0.09F, wall?.45F:.85F);
        float yaw = (float) (Math.random() * Math.PI * 2.0);
        int layer = nextLayer++ & 7;
        int light = sampleLight(pos, face);
        var pieces=me.cryo.zombierool.util.BloodAppearance.geometry(Minecraft.getInstance().level,pos,face,half,yaw,seed);
        if(pieces.isEmpty())return;
        int color=me.cryo.zombierool.util.BloodAppearance.color(seed);
        SPLATS.add(new Splat(pos, face, half, yaw, layer, light, ((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,seed,pieces));
        while (SPLATS.size() > MAX) SPLATS.remove(0);
        if (face == Direction.UP && half >= LARGE_PUDDLE_HALF_SIZE) playImpactSound(pos);
    }

    private static void playImpactSound(Vec3 pos) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if(!allowImpactSound(level,pos))return;
        level.playLocalSound(pos.x, pos.y, pos.z, ZombieroolModSounds.BLOOD_SPLAT.get(),
                SoundSource.HOSTILE, 0.65F, 0.95F + level.random.nextFloat() * 0.10F, false);
    }

    private static boolean allowImpactSound(net.minecraft.client.multiplayer.ClientLevel level,Vec3 pos){
        long tick = level.getGameTime();
        if(soundLevel!=level){IMPACT_SOUNDS.clear();nextImpactSound=Long.MIN_VALUE;soundLevel=level;}
        if(tick<nextImpactSound&&nextImpactSound-tick<=20)return false;
        IMPACT_SOUNDS.removeIf(impact -> tick < impact.tick || tick - impact.tick >= IMPACT_SOUND_COOLDOWN);
        // A spray creates many nearby drops; let them share one impact sound.
        for (ImpactSound impact : IMPACT_SOUNDS) {
            if (impact.pos.distanceToSqr(pos) < 64.0) return false;
        }
        IMPACT_SOUNDS.add(new ImpactSound(pos, tick));
        nextImpactSound=tick+20;
        return true;
    }

    private record ImpactSound(Vec3 pos, long tick) {}

    public static void clear() {
        SPLATS.clear();
        BloodSurfaceRenderer.clear();
        IMPACT_SOUNDS.clear();
        soundLevel=null;nextImpactSound=Long.MIN_VALUE;
        me.cryo.zombierool.client.particle.BloodStainParticle.expireAll();
    }

    public static void clientTick(boolean inWorld, boolean gameRunning) {
        if (!inWorld) {
            BloodSurfaceRenderer.clear();
            if (sawMatch || !SPLATS.isEmpty()) {
                me.cryo.zombierool.client.particle.BloodStainParticle.expireAll();
            }
            SPLATS.clear();
            IMPACT_SOUNDS.clear();
            sawMatch = false;
            return;
        }
        if (gameRunning != sawMatch) {
            BloodSurfaceRenderer.clear();
            SPLATS.clear();
            IMPACT_SOUNDS.clear();
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
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||HalloweenManager.isHalloweenPeriod()||ZRClientConfig.isGoreReduced())return;
        var stamps=new ArrayList<BloodSurfaceRenderer.Stamp>();
        for(var splat:SPLATS)stamps.add(splat.stamp);
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof me.cryo.zombierool.entity.BloodDecalEntity blood&&blood.isAlive())stamps.add(BloodSurfaceRenderer.stamp(blood));
        var pose=event.getPoseStack();var cam=event.getCamera().getPosition();pose.pushPose();pose.translate(-cam.x,-cam.y,-cam.z);
        BloodSurfaceRenderer.render(stamps,pose,mc.renderBuffers().bufferSource(),event.getFrustum());pose.popPose();
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
        pose.pushPose();
        pose.translate(splat.pos.x,splat.pos.y,splat.pos.z);
        drawPieces(pose,vc,splat.pieces,splat.face,splat.layer,splat.light,splat.red,splat.green,splat.blue,200);
        pose.popPose();
    }

    public static void drawPieces(PoseStack pose,VertexConsumer vc,List<me.cryo.zombierool.util.BloodSurfaceGeometry.Piece> pieces,Direction face,int layer,int light,int red,int green,int blue){
        drawPieces(pose,vc,pieces,face,layer,light,red,green,blue,240);
    }
    private static void drawPieces(PoseStack pose,VertexConsumer vc,List<me.cryo.zombierool.util.BloodSurfaceGeometry.Piece> pieces,Direction face,int layer,int light,int red,int green,int blue,int alpha){
        var level=Minecraft.getInstance().level;if(level==null)return;
        Matrix4f matrix=pose.last().pose();float lift=.001F+layer*.0001F;
        boolean flip=face==Direction.DOWN||face==Direction.SOUTH||face==Direction.WEST;
        for(var piece:pieces){if(!piece.present(level))continue;var vertices=piece.vertices();
            // RenderType uses quads; triangulate clipped polygons using a repeated final vertex.
            for(int i=1;i<vertices.size()-1;i++){
                int[] order=flip?new int[]{0,i+1,i,i}:new int[]{0,i,i+1,i+1};
                for(int index:order){var v=vertices.get(index);
                    vc.vertex(matrix,(float)v.x()+face.getStepX()*lift,(float)v.y()+face.getStepY()*lift,(float)v.z()+face.getStepZ()*lift)
                      .color(red,green,blue,alpha).uv(v.u(),v.v()).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                      .uv2(light).normal(pose.last().normal(),face.getStepX(),face.getStepY(),face.getStepZ()).endVertex();
                }
            }
        }
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
        private final int variant;
        private final List<me.cryo.zombierool.util.BloodSurfaceGeometry.Piece> pieces;
        private final BloodSurfaceRenderer.Stamp stamp;

        private Splat(Vec3 pos, Direction face, float half, float yaw, int layer, int light, float red, float green, float blue,int variant,List<me.cryo.zombierool.util.BloodSurfaceGeometry.Piece> pieces) {
            this.pieces=pieces;
            this.variant=variant;
            this.pos = pos;
            this.face = face;
            this.half = half;
            this.yaw = yaw;
            this.layer = layer;
            this.light = light;
            this.red = Math.round(red * 255.0F);
            this.green = Math.round(green * 255.0F);
            this.blue = Math.round(blue * 255.0F);
            this.stamp=new BloodSurfaceRenderer.Stamp(pos,face,pieces,(this.red<<16)|(this.green<<8)|this.blue,255);
        }
    }
}
