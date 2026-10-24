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
        this.quadSize = .28f + this.random.nextFloat() * .12f;
        this.setSize(0.3f, 0.3f);
        this.lifetime = 30 + this.random.nextInt(15);
        this.gravity = -.015f;
        this.hasPhysics = false;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.pickSprite(spriteSet);
        this.roll = (random.nextFloat()-.5f)*.5f;
        this.oRoll = roll;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    // One connected silhouette per animation frame; triangulated once, without
    // overlapping coplanar body/wing pieces that flicker in the particle depth pass.
    private static final java.util.List<float[]>[] FRAMES=frames();
    private final int phase=random.nextInt(9);
    @Override public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer,net.minecraft.client.Camera camera,float partial) {
        var at=camera.getPosition();
        float px=(float)(net.minecraft.util.Mth.lerp(partial,xo,x)-at.x);
        float py=(float)(net.minecraft.util.Mth.lerp(partial,yo,y)-at.y);
        float pz=(float)(net.minecraft.util.Mth.lerp(partial,zo,z)-at.z);
        var rotation=new org.joml.Quaternionf(camera.rotation()).rotateZ(roll);
        float fade=Math.max(0,Math.min(1,(lifetime-age-partial)/8f));
        float u=(sprite.getU0()+sprite.getU1())/2,v=(sprite.getV0()+sprite.getV1())/2;
        int light=getLightColor(partial);
        for(var triangle:FRAMES[(age/2+phase)%9]) for(int i=0;i<4;i++) {
            int index=Math.min(i,2);
            var point=new org.joml.Vector3f(triangle[index*2],triangle[index*2+1],0).mul(quadSize).rotate(rotation).add(px,py,pz);
            buffer.vertex(point.x,point.y,point.z).uv(u,v).color(.025f,.018f,.04f,fade).uv2(light).endVertex();
        }
    }
    @SuppressWarnings("unchecked") private static java.util.List<float[]>[] frames(){
        java.util.List<float[]>[] frames=new java.util.List[9];
        for(int frame=0;frame<9;frame++) {
            float tip=.15f+(float)Math.sin(frame*Math.PI*2/9)*.5f;
            float[] outline={-.13f,.65f, .15f,.68f, .42f,.51f, .18f,.45f, .14f,.25f,
                .65f,tip+.1f, 1.2f,tip-.1f, 1.12f,tip-.37f, .98f,tip-.23f,
                .94f,tip-.40f, .80f,tip-.25f, .75f,tip-.42f, .62f,tip-.25f, .28f,-.15f,
                .12f,-.35f, .2f,-.64f, -.24f,-.72f, -.08f,-.35f, -.15f,-.15f,
                -.62f,tip-.25f, -.75f,tip-.42f, -.80f,tip-.25f, -.94f,tip-.40f,
                -.98f,tip-.23f, -1.12f,tip-.37f, -1.2f,tip-.1f, -.65f,tip+.1f, -.12f,.25f};
            var remaining=new java.util.ArrayList<Integer>();for(int i=0;i<outline.length/2;i++)remaining.add(i);
            var triangles=new java.util.ArrayList<float[]>();
            while(remaining.size()>2) {
                boolean clipped=false;
                for(int i=0;i<remaining.size();i++) {
                    int a=remaining.get((i+remaining.size()-1)%remaining.size()),b=remaining.get(i),c=remaining.get((i+1)%remaining.size());
                    if(cross(outline,a,b,c)>=-.000001f)continue;
                    boolean contains=false;
                    for(int other:remaining)if(other!=a&&other!=b&&other!=c
                            &&cross(outline,a,b,other)<=0&&cross(outline,b,c,other)<=0&&cross(outline,c,a,other)<=0){contains=true;break;}
                    if(contains)continue;
                    triangles.add(new float[]{outline[a*2],outline[a*2+1],outline[b*2],outline[b*2+1],outline[c*2],outline[c*2+1]});
                    remaining.remove(i);clipped=true;break;
                }
                if(!clipped)throw new IllegalStateException("Invalid crow silhouette frame "+frame);
            }
            frames[frame]=java.util.List.copyOf(triangles);
        }
        return frames;
    }
    private static float cross(float[] points,int a,int b,int c){
        return (points[b*2]-points[a*2])*(points[c*2+1]-points[a*2+1])-(points[b*2+1]-points[a*2+1])*(points[c*2]-points[a*2]);
    }
}
