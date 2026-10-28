package me.cryo.zombierool.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BloodStainParticle extends TextureSheetParticle {
    private static int session;
    private static boolean deferredPass;
    private static final java.util.Set<BloodStainParticle> LIVE=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static boolean hasDeferred(){for(var p:LIVE)if(p.isAlive()&&!p.stuck)return true;return false;}
    public static void renderDeferred(VertexConsumer out,Camera camera,float partial){
        var visible=new java.util.ArrayList<BloodStainParticle>();
        LIVE.removeIf(p->!p.isAlive());for(var drop:LIVE)if(!drop.stuck)visible.add(drop);
        visible.sort(java.util.Comparator.comparingDouble((BloodStainParticle p)->camera.getPosition().distanceToSqr(p.x,p.y,p.z)).reversed());
        deferredPass=true;try{for(var drop:visible)drop.render(out,camera,partial);}finally{deferredPass=false;}
    }


    private boolean stuck;
    private int stuckSession;
    private int stuckLight;
    private Direction face = Direction.UP;
    private float spin;
    private float lift = 0.03F;

    public static void expireAll() {
        session++;LIVE.clear();
    }

    public static BloodStainParticleProvider provider(SpriteSet spriteSet) {
            return new BloodStainParticleProvider(spriteSet);
    }

    public static class BloodStainParticleProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public BloodStainParticleProvider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            if (me.cryo.zombierool.client.HalloweenManager.isHalloweenPeriod()) return CandyParticle.create(level,x,y,z,xSpeed,ySpeed,zSpeed);
            return new BloodStainParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }

    protected BloodStainParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.pickSprite(spriteSet);
        this.lifetime = 16 + level.random.nextInt(10);
        this.gravity = 0.62F;
        // Collision is resolved by the visible-surface ray, so technical barriers cannot stop a drop.
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.quadSize = 0.07F + level.random.nextFloat() * 0.05F;
        this.setColor(0.30F, 0.008F, 0.015F);
        this.setSize(0.12F, 0.12F);
        this.stuckSession = session;
        LIVE.add(this);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        if (this.stuckSession != session) {
            this.remove();
            return;
        }
        if (this.stuck) {
            if (this.age++ >= this.lifetime) this.remove();
            return;
        }
        double px = this.x;
        double py = this.y;
        double pz = this.z;
        float speed = (float) Math.sqrt(this.xd * this.xd + this.yd * this.yd + this.zd * this.zd);
        super.tick();
        if (!this.isAlive()) return;
        Vec3 from = new Vec3(px, py, pz);
        Vec3 to = new Vec3(this.x, this.y, this.z);
        if (from.distanceToSqr(to) > 1.0E-8) {
            BlockHitResult hit = clipSolid(from, to);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                this.stick(hit.getLocation(), hit.getDirection(), Math.max(speed, 0.08F));
                return;
            }
        }
        if (this.onGround) {
            this.stick(new Vec3(this.x, this.y, this.z), Direction.UP, Math.max(speed, 0.08F));
        }
    }

    private BlockHitResult clipSolid(Vec3 from, Vec3 to) {
        var hit=me.cryo.zombierool.util.GoreCollision.clip(this.level,from,to);
        return hit.getType()==HitResult.Type.BLOCK?hit:null;
    }

    private void stick(Vec3 pos, Direction hitFace, float speed) {
        this.stuck = true;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.face = hitFace == null ? Direction.UP : hitFace;
        this.spin = this.random.nextFloat() * ((float) Math.PI * 2.0F);
        this.quadSize = Mth.clamp(0.16F + speed * 1.6F, 0.14F, 1.25F);
        this.lifetime = this.age + 2400;
        this.lift = 0.02F;
        this.alpha = 0.78F;
        this.setPos(
                pos.x + this.face.getStepX() * this.lift,
                pos.y + this.face.getStepY() * this.lift,
                pos.z + this.face.getStepZ() * this.lift);
        me.cryo.zombierool.client.BloodSplatters.add(pos, this.face, speed, this.rCol, this.gCol, this.bCol);
        this.remove();
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partial) {
        if(!deferredPass&&me.cryo.zombierool.client.MapAtmosphereClient.deferredBlood())return;
        if (!this.stuck) {
            super.render(buffer, camera, partial);
            return;
        }
        Vec3 cam = camera.getPosition();
        float cx = (float) (this.x - cam.x);
        float cy = (float) (this.y - cam.y);
        float cz = (float) (this.z - cam.z);
        float h = this.getQuadSize(partial);
        float cos = Mth.cos(this.spin);
        float sin = Mth.sin(this.spin);
        int light = this.stuckLight;
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        float[] us = {u0, u0, u1, u1};
        float[] vs = {v0, v1, v1, v0};
        for (int i = 0; i < 4; i++) {
            float lx = (i == 0 || i == 1) ? -h : h;
            float ly = (i == 0 || i == 3) ? -h : h;
            float rx = lx * cos - ly * sin;
            float ry = lx * sin + ly * cos;
            float x;
            float y;
            float z;
            if (this.face == Direction.UP || this.face == Direction.DOWN) {
                x = rx;
                y = 0.0F;
                z = ry;
            } else if (this.face == Direction.NORTH || this.face == Direction.SOUTH) {
                x = rx;
                y = ry;
                z = 0.0F;
            } else {
                x = 0.0F;
                y = ry;
                z = rx;
            }
            buffer.vertex(cx + x, cy + y, cz + z).uv(us[i], vs[i]).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        }
    }
}
