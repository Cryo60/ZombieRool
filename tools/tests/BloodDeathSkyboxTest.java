package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.client.particle.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod("atmospherevisual")
public final class BloodDeathSkyboxTest {
    boolean requested,done;int age;volatile String failure;UUID victim;Map<String,Integer> counts=new TreeMap<>();int window;int[] background;int fixedFrames;boolean compared;java.util.List<Particle> fixed=new ArrayList<>();
    public BloodDeathSkyboxTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,this::frame);}
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    void frame(net.minecraftforge.client.event.RenderLevelStageEvent e){
        if(!fixed.isEmpty()&&e.getStage()==net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES)fixedFrames++;

    }
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();t.printStackTrace();}});}
    @SuppressWarnings("unchecked") void trace()throws Exception {
        var mc=Minecraft.getInstance();var field=ParticleEngine.class.getDeclaredField("providers");field.setAccessible(true);var providers=(Map<ResourceLocation,ParticleProvider<ParticleOptions>>)field.get(mc.particleEngine);
        var sprite=TextureSheetParticle.class.getDeclaredField("sprite");sprite.setAccessible(true);
        for(var e:new HashMap<>(providers).entrySet()){
            var original=e.getValue();var id=e.getKey();providers.put(id,(type,level,x,y,z,vx,vy,vz)->{
                var result=original.createParticle(type,level,x,y,z,vx,vy,vz);
                if(window!=0&&result!=null){String name=id+" -> "+result.getClass().getSimpleName();try{if(result instanceof TextureSheetParticle)name+=" / "+((net.minecraft.client.renderer.texture.TextureAtlasSprite)sprite.get(result)).contents().name();}catch(Exception ex){throw new RuntimeException(ex);}
                    counts.merge(name,1,Integer::sum);
                    if(id.getPath().contains("bubble")&&counts.get(name)==1){System.out.println("BLOOD BUBBLE SOURCE: "+name+" at "+x+","+y+","+z);new Exception("Particle origin").printStackTrace();}
                }return result;
            });
        }
    }
    void shot(String name)throws Exception {var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+name+".png"));}}
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null)return;age++;
        if(age==30){trace();server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().clearContent();p.getInventory().selected=0;LunarEquipment.revoke(p);for(var pos:BlockPos.betweenClosed(94,79,94,108,86,108))p.serverLevel().setBlock(pos,pos.getY()==79?net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);p.teleportTo(p.serverLevel(),100.5,80,105.5,180,5);p.serverLevel().setDayTime(6000);});}
        for(int phase=0;phase<2;phase++){
            int start=55+phase*65;final int mode=phase;
            if(age==start){window=0;counts.clear();me.cryo.zombierool.client.BloodSplatters.clear();var settings=phase%2==0?MapAtmosphere.defaults():MapAtmosphere.preset("die_rise");me.cryo.zombierool.client.MapAtmosphereClient.receive("state",settings);server(p->{for(var mob:p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,p.getBoundingBox().inflate(20)))mob.discard();var mob=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(p.serverLevel());mob.setNoAi(true);mob.setPos(100.5,80,101.5);mob.setYRot(0);p.serverLevel().addFreshEntity(mob);victim=mob.getUUID();});}
            if(age==start+15){window=phase+1;server(p->{var mob=(net.minecraft.world.entity.LivingEntity)p.serverLevel().getEntity(victim);System.out.println("BLOOD DEATH: mode="+mode+" fluid="+mob.level().getFluidState(mob.blockPosition())+" eye="+mob.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));mob.hurt(p.damageSources().playerAttack(p),10000);});}
            if(age==start+17||age==start+20||age==start+25)shot("death-"+phase+"-"+(age-start));
            if(age==start+48&&phase==1){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){background=new int[image.getWidth()*image.getHeight()];for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)background[y*image.getWidth()+x]=image.getPixelRGBA(x,y);image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/sky-background.png"));}}
            if(age==start+49&&phase==1){for(int i=0;i<8;i++){var drop=mc.particleEngine.createParticle(me.cryo.zombierool.init.ZombieroolModParticleTypes.BLOOD_STAIN.get(),99.45+i*.3,83,101.5,0,0,0);for(String name:java.util.List.of("gravity","xd","yd","zd")){var field=net.minecraft.client.particle.Particle.class.getDeclaredField(name);field.setAccessible(true);if(name.equals("gravity"))field.setFloat(drop,0);else field.setDouble(drop,0);}var size=net.minecraft.client.particle.SingleQuadParticle.class.getDeclaredField("quadSize");size.setAccessible(true);size.setFloat(drop,.15f);var life=net.minecraft.client.particle.Particle.class.getDeclaredField("lifetime");life.setAccessible(true);life.setInt(drop,200);fixed.add(drop);}}
            if(age>=start+52&&phase==1&&!compared&&fixedFrames>=2){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int bright=0,dark=0;for(int y=0;y<300;y++)for(int x=100;x<image.getWidth()-100;x++){int old=background[y*image.getWidth()+x],now=image.getPixelRGBA(x,y);int g=(now>>8&255)-(old>>8&255),b=(now>>16&255)-(old>>16&255);if(g>6||b>6)bright++;if(g< -12&&b< -12)dark++;}compared=true;System.out.println("BLOOD HALO PIXELS: bright="+bright+" blood="+dark);image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/sky-blood-after.png"));if(bright>8||dark<30)throw new AssertionError("Blood must remain visible without bright sky halos: "+bright+"/"+dark);}}
            if(age==start+45&&phase==1){System.out.println("BLOOD DEFERRED MODE: "+me.cryo.zombierool.client.MapAtmosphereClient.deferredBlood());}
            if(age==start+45){System.out.println("BLOOD PARTICLE COUNTS mode="+phase+" "+counts);window=0;}
        }
        if(age==180)server(p->{for(var pos:BlockPos.betweenClosed(97,81,103,104,86,103))p.serverLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3);});
        if(age==203){try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int red=0;for(int y=80;y<300;y++)for(int x=450;x<850;x++){int c=image.getPixelRGBA(x,y),r=c&255,g=c>>8&255,b=c>>16&255;if(r>15&&r>g*1.5&&r>b*1.5)red++;}image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/blood-occluded.png"));System.out.println("BLOOD THROUGH WALL PIXELS: "+red);if(red>8)throw new AssertionError("Blood rendered through an opaque wall: "+red);}}
        if(age==210){if(!compared)throw new AssertionError("Particle comparison did not render");done=true;System.out.println("ATMOSPHERE VISUAL PASS: actual deaths traced with and without skybox");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
