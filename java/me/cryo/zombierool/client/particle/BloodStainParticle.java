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
    /** Overlapping puddles blend instead of fighting for the same depth. */
    private static final ParticleRenderType STUCK_SHEET = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "ZOMBIEROOL_BLOOD_STAIN";
        }
    };

    private static int session;

    private boolean stuck;
    private int stuckSession;
    private int stuckLight;
    private Direction face = Direction.UP;
    private float spin;
    private float lift = 0.03F;

    public static void expireAll() {
        session++;
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
            return new BloodStainParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }

    protected BloodStainParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
        super(level, x, y, z);
        this.pickSprite(spriteSet);
        this.lifetime = 16 + level.random.nextInt(10);
        this.gravity = 0.62F;
        this.hasPhysics = true;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.quadSize = 0.07F + level.random.nextFloat() * 0.05F;
        this.setColor(0.62F, 0.02F, 0.03F);
        this.setSize(0.12F, 0.12F);
        this.stuckSession = session;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.stuck ? STUCK_SHEET : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
        Vec3 start = from;
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-6) return null;
        dir = dir.scale(1.0 / len);
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = this.level.clip(new ClipContext(start, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
            if (hit.getType() != HitResult.Type.BLOCK) return null;
            net.minecraft.world.level.block.state.BlockState state = this.level.getBlockState(hit.getBlockPos());
            if (!(state.getBlock() instanceof me.cryo.zombierool.block.AbstractTechnicalBlock)
                    && state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE) {
                return hit;
            }
            start = hit.getLocation().add(dir.scale(0.06));
            if (start.distanceToSqr(to) <= 1.0E-6) return null;
        }
        return null;
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
