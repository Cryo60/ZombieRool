package me.cryo.zombierool.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** The original ten digit masks are sprite variants, rather than animated sequential numbers. */
public final class ThiefDigitsParticle extends TextureSheetParticle {
    private ThiefDigitsParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites){super(level,x,y,z,vx,vy,vz);pickSprite(sprites);lifetime=16+random.nextInt(14);quadSize=.12f+random.nextFloat()*.1f;rCol=1;gCol=.04f;bCol=.02f;gravity=-.015f;hasPhysics=false;}
    @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
    @Override protected int getLightColor(float partial){return 15728880;}
    @Override public void tick(){super.tick();alpha=Math.min(1,(lifetime-age)/8f);}
    public static ParticleProvider<SimpleParticleType> provider(SpriteSet sprites){return (type,level,x,y,z,vx,vy,vz)->new ThiefDigitsParticle(level,x,y,z,vx,vy,vz,sprites);}
}
