package me.cryo.zombierool.client.particle;

import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;

@OnlyIn(Dist.CLIENT)
public class BlackCrowParticle extends TextureSheetParticle {
    public static BlackCrowParticleProvider provider(SpriteSet spriteSet) {
        return new BlackCrowParticleProvider(spriteSet);
    }

    public static class BlackCrowParticleProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;
        public BlackCrowParticleProvider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }
        public Particle createParticle(SimpleParticleType typeIn, ClientLevel worldIn, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new BlackCrowParticle(worldIn, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet);
        }
    }

    protected BlackCrowParticle(ClientLevel world, double x, double y, double z, double vx, double vy, double vz, SpriteSet spriteSet) {
        super(world, x, y, z);
        this.quadSize = 0.8f + this.random.nextFloat() * 0.4f; 
        this.setSize(0.3f, 0.3f);
        this.lifetime = 20 + this.random.nextInt(20); 
        this.gravity = -0.1f; 
        this.hasPhysics = false; 
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.pickSprite(spriteSet); 
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected float getU0() {
        return this.sprite.getU0();
    }

    @Override
    protected float getU1() {
        return this.sprite.getU1();
    }

    @Override
    protected float getV0() {
        float v0 = this.sprite.getV0();
        float v1 = this.sprite.getV1();
        float frameHeight = (v1 - v0) / 9.0F;
        int currentFrame = (this.age / 2) % 9; 
        return v0 + currentFrame * frameHeight;
    }

    @Override
    protected float getV1() {
        float v0 = this.sprite.getV0();
        float v1 = this.sprite.getV1();
        float frameHeight = (v1 - v0) / 9.0F;
        int currentFrame = (this.age / 2) % 9;
        return v0 + (currentFrame + 1) * frameHeight;
    }
}
