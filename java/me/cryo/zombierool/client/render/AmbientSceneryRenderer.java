package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import me.cryo.zombierool.gameplay.AmbientScenery;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Low-cost world geometry: no emitted Particle objects or ticking aircraft entities. */
public final class AmbientSceneryRenderer {
    private static final ResourceLocation FIRE=new ResourceLocation("minecraft","textures/block/fire_0.png"),SMOKE=new ResourceLocation("minecraft","textures/particle/big_smoke_3.png");
    private static final ResourceLocation WHITE=new ResourceLocation("zombierool","textures/scenery/tracer_white.png");
    private record ShotKey(long cycle,int round){}
    private record Shot(double born,float yaw,float pitch){}
    private record AirShot(int plane,double born,Vec3 start,Vec3 end){}
    private static final class Salvo {long start=Long.MIN_VALUE;int config;final java.util.Map<ShotKey,Shot> shots=new java.util.HashMap<>();final java.util.Map<ShotKey,AirShot> air=new java.util.HashMap<>();long airCycle=Long.MIN_VALUE;}
    private static final java.util.Map<MapDeviceSystem.Device,Salvo> SALVOS=new java.util.WeakHashMap<>();
    public static void render(MapDeviceSystem.Device d,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(!me.cryo.zombierool.client.SceneryShaderPass.shadow()&&(d.kind()!=MapDeviceSystem.Kind.VEHICLE||d.scenery.getString("variant").equals("fire")))me.cryo.zombierool.client.AmbientSceneryEffects.enqueue(d,partial);
        renderPass(d,partial,pose,buffers,light,false);
    }
    public static void renderEffects(MapDeviceSystem.Device d,float partial,PoseStack pose,MultiBufferSource buffers){renderPass(d,partial,pose,buffers,15728880,true);}
    private static void renderPass(MapDeviceSystem.Device d,float partial,PoseStack pose,MultiBufferSource buffers,int light,boolean effects){double time=(d.getLevel().getGameTime()+partial-d.sceneryStart)/20.0;boolean active=d.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE);var n=d.scenery;boolean shadow=me.cryo.zombierool.client.SceneryShaderPass.shadow();
        if(shadow&&d.kind()==MapDeviceSystem.Kind.TORNADO)return;
        var camera=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var view=new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(pose.last().pose()).translate((float)(camera.x-d.getBlockPos().getX()),(float)(camera.y-d.getBlockPos().getY()),(float)(camera.z-d.getBlockPos().getZ()));
        var frustum=new net.minecraft.client.renderer.culling.Frustum(view,com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());frustum.prepare(camera.x,camera.y,camera.z);
        pose.pushPose();pose.translate(.5,0,.5);
        if(d.kind()==MapDeviceSystem.Kind.VEHICLE){if(active&&(shadow||frustum.isVisible(d.getRenderBoundingBox()))){if(!effects)VehicleDecorModels.vehicle(d,pose,buffers,light);else if(!shadow&&n.getString("variant").equals("fire"))vehicleFire(d,time,pose,buffers);}}
        else if(d.kind()==MapDeviceSystem.Kind.DCA)renderFlak(d,time,active,shadow,pose,buffers,light,effects);
        else if(active&&d.kind()==MapDeviceSystem.Kind.AIRCRAFT){
            int amount=n.getInt("amount");boolean necropolis=n.getString("variant").equals("necropolis"),airship=n.getString("variant").equals("airship"),passive=airship||necropolis;
            var volley=SALVOS.computeIfAbsent(d,key->new Salvo());int settings=n.hashCode();
            if(volley.start!=d.sceneryStart||volley.config!=settings){volley.air.clear();volley.airCycle=Long.MIN_VALUE;volley.start=d.sceneryStart;volley.config=settings;}
            if(effects&&!passive){
                volley.air.values().removeIf(shot->time<shot.born||time-shot.born>1.4);
                double period=Math.max(.09,Math.min(.22,d.interval/20.0));long cycle=(long)Math.floor(time/period);
                if(cycle!=volley.airCycle){volley.airCycle=cycle;double born=cycle*period;
                    for(int plane=0;plane<amount;plane++)if(AmbientScenery.aircraftFiring(d,plane,born)){
                        var launch=AmbientScenery.aircraftPosition(n,plane,born).add(AmbientScenery.aircraftDirection(n,plane,born).scale(2.8));
                        volley.air.put(new ShotKey(cycle,plane),new AirShot(plane,born,launch,AmbientScenery.aircraftOpponent(d,plane,born).point()));
                    }
                }
            }
            for(int i=0;i<amount;i++){
                if(!AmbientScenery.aircraftVisible(n,i,time))continue;
                Vec3 at=AmbientScenery.aircraftPosition(n,i,time),direction=AmbientScenery.aircraftDirection(n,i,time);
                double extent=(necropolis?1.2:airship?12:4)*n.getDouble("size");var center=at.add(d.getBlockPos().getX()+.5,d.getBlockPos().getY(),d.getBlockPos().getZ()+.5);
                if(!shadow&&!frustum.isVisible(new net.minecraft.world.phys.AABB(center,center).inflate(extent)))continue;
                float opacity=AmbientScenery.aircraftOpacity(n,i,time);
                if(airship&&!shadow){var projection=com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix();double far=Math.abs(projection.m32()/(projection.m22()+1));if(Double.isFinite(far))opacity*=Math.max(0,Math.min(1,(far-extent-center.distanceTo(camera))/Math.max(32,far*.18)));}
                if(opacity<.002f)continue;
                pose.pushPose();pose.translate(at.x,at.y,at.z);
                pose.mulPose(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(direction.x,direction.z))));
                if(!passive)pose.mulPose(Axis.ZP.rotationDegrees((float)Math.sin(time*.2+i)*12));
                float scale=(float)n.getDouble("size");pose.scale(scale,scale,scale);
                if(!effects){if(necropolis)AmbientSceneryModels.necropolis(pose,buffers,light);else if(airship)AmbientSceneryModels.airship(pose,buffers,light,time,shadow?1:opacity);else AmbientSceneryModels.plane(pose,buffers,light,time,i);}
                pose.popPose();
                if(effects&&!shadow&&!passive){
                    // Launch-time origins and directions keep each burst ballistic as aircraft bank.
                    for(var entry:volley.air.entrySet()){
                        var flight=entry.getValue();if(flight.plane!=i)continue;
                        double age=time-flight.born;var launch=flight.start;var enemy=flight.end;
                        var delta=enemy.subtract(launch);double distance=delta.length();var shot=delta.normalize();double travel=age*90;
                        if(travel<distance){Vec3 tail=launch.add(shot.scale(Math.max(0,travel-3))),head=launch.add(shot.scale(travel));beam(pose,buffers,tail,head,.065f,1,.6f,.12f,.7f);beam(pose,buffers,tail,head,.025f,1,1,.9f,1);}
                        else if(travel<distance+9&&Math.floorMod(entry.getKey().cycle+i,4)==0)smoke(pose,buffers,enemy,.35+(travel-distance)*.06,(float)(.6*(1-(travel-distance)/9)));
                    }
                }
            }
        }
        else if(active&&effects){int amount=Math.min(4,n.getInt("amount"));var at=AmbientScenery.tornadoPosition(n,d.getBlockPos(),time).subtract(Vec3.atLowerCornerOf(d.getBlockPos()).add(.5,0,.5));for(int tornado=0;tornado<amount;tornado++){pose.pushPose();pose.translate(at.x+(tornado==0?0:Math.cos(tornado*2.4)*8),at.y,at.z+(tornado==0?0:Math.sin(tornado*2.4)*8));float scale=(float)n.getDouble("size");pose.scale(scale,scale,scale);vortex(pose,buffers,time+tornado*7,n.getString("variant").equals("fire"));pose.popPose();}}
        pose.popPose();
    }
    private static void renderFlak(MapDeviceSystem.Device d,double time,boolean active,boolean shadow,PoseStack pose,MultiBufferSource buffers,int light,boolean effects){
        Vec3 target=AmbientScenery.dcaTarget(d).subtract(Vec3.atLowerCornerOf(d.getBlockPos()).add(.5,1.4,.5));
        var rotation=AmbientScenery.dcaAngles(d,net.minecraft.client.Minecraft.getInstance().getFrameTime());float yaw=rotation.yaw(),pitch=rotation.pitch();
        if(!effects)AmbientSceneryModels.dcaBase(pose,buffers,light,d.scenery.getString("dcaModel"));pose.pushPose();pose.translate(0,1.4,0);pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if(!effects)AmbientSceneryModels.dcaShield(pose,buffers,light,d.scenery.getString("dcaModel"));pose.mulPose(Axis.XP.rotationDegrees(pitch));
        double period=AmbientScenery.dcaPeriod(d),phase=AmbientScenery.dcaAge(d,time,AmbientScenery.dcaCycle(d,time)),recoil=0;
        int rounds=d.scenery.getString("fireMode").equals("regular")?(period>=.3?3:period>=.15?2:1):1;double spacing=Math.min(.075,period*.22);
        if(active&&AmbientScenery.dcaFires(d,AmbientScenery.dcaCycle(d,time)))for(int r=0;r<rounds;r++)if(phase>=r*spacing)recoil=Math.max(recoil,AmbientScenery.recoil(phase-r*spacing,period));
        pose.pushPose();pose.translate(0,0,-recoil);if(!effects)AmbientSceneryModels.dcaGun(pose,buffers,light,d.scenery.getString("dcaModel"));pose.popPose();
        if(active&&!shadow&&effects){
            // Short moving streaks: no beam connecting the muzzle to the configured target.
            double muzzle=AmbientScenery.muzzle(d.scenery);
            double min=d.scenery.getDouble("tracerMinHeight"),max=d.scenery.getDouble("tracerMaxHeight");
            var salvo=SALVOS.computeIfAbsent(d,key->new Salvo());int config=d.scenery.hashCode();
            if(salvo.start!=d.sceneryStart||salvo.config!=config){salvo.shots.clear();salvo.start=d.sceneryStart;salvo.config=config;}
            salvo.shots.values().removeIf(shot->time<shot.born||time-shot.born>3.6);
            long cycle=AmbientScenery.dcaCycle(d,time);
            for(int r=0;r<rounds;r++)if(phase>=r*spacing&&AmbientScenery.dcaFires(d,cycle))
                salvo.shots.putIfAbsent(new ShotKey(cycle,r),new Shot(time-phase+r*spacing,yaw,pitch));
            int generations=Math.min(80,1+(int)Math.ceil(3.6/period));
            for(int previous=0;previous<generations;previous++)for(int r=0;r<rounds;r++){
                double age=phase+previous*period-r*spacing;if(age<0)continue;
                long shot=cycle-previous;var frozen=salvo.shots.get(new ShotKey(shot,r));if(frozen==null)continue;double seed=Math.sin(shot*91.173+r*37.43+d.getBlockPos().asLong()*.00001)*43758.5453;seed-=Math.floor(seed);
                double vertical=Math.max(.25,-Math.sin(Math.toRadians(frozen.pitch)));
                double distance=Math.min(160,(min+(max-min)*seed)/vertical),velocity=48+seed*16,travel=age*velocity;
                if(travel>distance)continue;
                double dx=(seed-.5)*.018*travel,dy=Math.sin(shot+r*2.4)*.012*travel;
                float fade=(float)Math.min(1,(distance-travel)/4);
                pose.pushPose();pose.mulPose(Axis.XP.rotationDegrees(-pitch));pose.mulPose(Axis.YP.rotationDegrees(-yaw));
                pose.mulPose(Axis.YP.rotationDegrees(frozen.yaw));pose.mulPose(Axis.XP.rotationDegrees(frozen.pitch));
                if(travel>1.1){Vec3 end=new Vec3(dx,dy,muzzle+travel),start=new Vec3(dx,dy,muzzle+Math.max(.5,travel-2.2-seed));beam(pose,buffers,start,end,.095f,1,.59f,.13f,.7f*fade);beam(pose,buffers,start,end,.035f,1,1,.94f,fade);}
                pose.popPose();
                if(previous==0&&age<.065)muzzleFlash(pose,buffers,muzzle-recoil,(float)(1-age/.065),shot+r);
                if(previous<2&&age<.85){double growth=.12+age*.65;smoke(pose,buffers,new Vec3(Math.sin(seed*9)*age*.24,age*.3,muzzle+.13+age*.85),growth,(float)(.24*(1-age/.85)));}
            }
        }
        pose.popPose();
    }
    private static void vehicleFire(MapDeviceSystem.Device d,double time,PoseStack pose,MultiBufferSource b){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees((float)d.scenery.getDouble("yaw")));float size=(float)d.scenery.getDouble("size");pose.scale(size,size,size);
        var out=b.getBuffer(me.cryo.zombierool.client.AmbientSceneryEffects.material(FIRE,false));
        for(int i=0;i<8;i++){double a=i*2.39996,x=Math.sin(a)*1.05,z=Math.cos(a)*2.1,height=2.3+Math.sin(time*3.4+i)*.35;var origin=new org.joml.Matrix4d().rotateX(Math.toRadians(d.scenery.getDouble("pitch"))).rotateZ(Math.toRadians(d.scenery.getDouble("roll"))).transformPosition(new org.joml.Vector3d(x,.6,z));quadFrame(pose,out,origin.x-.45,origin.y,origin.z,origin.x+.45,origin.y,origin.z,origin.x+.3,origin.y+height,origin.z,origin.x-.3,origin.y+height,origin.z,1,1,1,.92f,15728880,Math.floorMod((int)(time*16)+i*3,32));}
        for(int i=0;i<6;i++){double age=(time*.28+i/6.0)%1;smoke(pose,b,new Vec3(Math.sin(time*.2+i)*age*.7,1.6+age*4,Math.cos(i)*age*.6),.65+age*1.3,(float)(.48*(1-age)));}pose.popPose();
    }
    private static void muzzleFlash(PoseStack p,MultiBufferSource b,double z,float fade,long seed){
        var out=b.getBuffer(me.cryo.zombierool.client.AmbientSceneryEffects.material(WHITE,true));double width=.22*fade;
        for(int i=0;i<3;i++){double a=i*Math.PI/3+seed*.7,dx=Math.cos(a)*width,dy=Math.sin(a)*width;
            quad(p,out,new Vec3(-dx,-dy,z),new Vec3(dx,dy,z),new Vec3(dx*.25,dy*.25,z+.65*fade),new Vec3(-dx*.25,-dy*.25,z+.65*fade),1,.77f,.28f,fade,15728880);}
        beam(p,b,new Vec3(0,0,z),new Vec3(0,0,z+.48*fade),.06f*fade,1,1,.9f,fade);
    }
    private static void smoke(PoseStack p,MultiBufferSource b,Vec3 c,double size,float alpha){
        var out=b.getBuffer(me.cryo.zombierool.client.AmbientSceneryEffects.material(SMOKE,false));
        quad(p,out,c.add(-size,-size,0),c.add(size,-size,0),c.add(size,size,0),c.add(-size,size,0),.42f,.41f,.38f,alpha,15728880);
        quad(p,out,c.add(0,-size,-size),c.add(0,-size,size),c.add(0,size,size),c.add(0,size,-size),.42f,.41f,.38f,alpha,15728880);
    }
    private static void vortex(PoseStack p,MultiBufferSource b,double t,boolean fire){
        var out=b.getBuffer(me.cryo.zombierool.client.AmbientSceneryEffects.material(fire?FIRE:SMOKE,false));int rings=24,sides=12;
        for(int ring=0;ring<rings;ring++){
            double y=ring*.7,radius=.35+ring*.16;
            for(int side=0;side<sides;side++){
                double a=side*Math.PI*2/sides+t*(1.6-ring*.025)+ring*.32,next=a+Math.PI*2/sides;
                float r=fire?1:.8f,g=fire?1:.82f,blue=fire?1:.85f,alpha=fire?.7f:.85f;
                quadFrame(p,out,Math.cos(a)*radius,y,Math.sin(a)*radius,Math.cos(next)*radius,y,Math.sin(next)*radius,Math.cos(next+.2)*(radius+.17),y+.9,Math.sin(next+.2)*(radius+.17),Math.cos(a+.2)*(radius+.17),y+.9,Math.sin(a+.2)*(radius+.17),r,g,blue,alpha,15728880,fire?Math.floorMod((int)(t*16)+ring*2,32):-1);
            }
        }
    }
    private static void beam(PoseStack p,MultiBufferSource b,Vec3 a,Vec3 z,float width,float r,float g,float blue,float alpha){
        var midpoint=new org.joml.Vector3f((float)((a.x+z.x)*.5),(float)((a.y+z.y)*.5),(float)((a.z+z.z)*.5));
        new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).mul(p.last().pose()).transformPosition(midpoint);
        float pixels=width<.05f?2f:4f,projection=Math.abs(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix().m11());
        width=Math.max(width,Math.min(.22f,midpoint.length()*pixels/Math.max(1,net.minecraft.client.Minecraft.getInstance().getMainRenderTarget().height*projection)));
        var direction=z.subtract(a).normalize();var side=direction.cross(new Vec3(0,1,0));if(side.lengthSqr()<.001)side=new Vec3(1,0,0);side=side.normalize().scale(width);var up=direction.cross(side).normalize().scale(width);var out=b.getBuffer(me.cryo.zombierool.client.AmbientSceneryEffects.material(WHITE,true));quad(p,out,a.add(side),a.subtract(side),z.subtract(side),z.add(side),r,g,blue,alpha,15728880);quad(p,out,a.add(up),a.subtract(up),z.subtract(up),z.add(up),r,g,blue,alpha,15728880);}
    private static void quadFrame(PoseStack p,VertexConsumer out,double ax,double ay,double az,double bx,double by,double bz,double cx,double cy,double cz,double dx,double dy,double dz,float r,float g,float blue,float alpha,int light,int frame){
        double ux=bx-ax,uy=by-ay,uz=bz-az,vx=cx-ax,vy=cy-ay,vz=cz-az,nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,length=Math.sqrt(nx*nx+ny*ny+nz*nz);if(length<1e-8)return;
        float fx=(float)(nx/length),fy=(float)(ny/length),fz=(float)(nz/length),v0=frame<0?0:frame/32f,v1=frame<0?1:(frame+1)/32f;
        if(frame>=0){float swap=v0;v0=v1;v1=swap;}
        vertex(p,out,ax,ay,az,0,v0,r,g,blue,alpha,light,fx,fy,fz);vertex(p,out,bx,by,bz,1,v0,r,g,blue,alpha,light,fx,fy,fz);vertex(p,out,cx,cy,cz,1,v1,r,g,blue,alpha,light,fx,fy,fz);vertex(p,out,dx,dy,dz,0,v1,r,g,blue,alpha,light,fx,fy,fz);
    }
    private static void vertex(PoseStack p,VertexConsumer out,double x,double y,double z,float u,float v,float r,float g,float b,float alpha,int light,float nx,float ny,float nz){out.vertex(p.last().pose(),(float)x,(float)y,(float)z).color(r,g,b,alpha).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.last().normal(),nx,ny,nz).endVertex();}
    private static void quad(PoseStack p,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,float r,float g,float blue,float alpha,int light){var normal=b.subtract(a).cross(c.subtract(a)).normalize();Vec3[] points={a,b,c,d};float[][] uv={{0,0},{1,0},{1,1},{0,1}};for(int i=0;i<4;i++){var v=points[i];out.vertex(p.last().pose(),(float)v.x,(float)v.y,(float)v.z).color(r,g,blue,alpha).uv(uv[i][0],uv[i][1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(p.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();}}
    private AmbientSceneryRenderer(){}
}
