package local.atmospherevisual;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;
import me.cryo.zombierool.entity.MapActorEntity;

@net.minecraftforge.fml.common.Mod("atmospherevisual")
public final class ArcherGripVisualTest {
    private boolean opened,done;private int age,checks;private volatile Throwable failure;
    private final String[] ids={"scarlet_zombie_archer","scarlet_zombie_archer_female"};
    public ArcherGripVisualTest(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void photo(Minecraft mc,String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+message);}
    private MapActorEntity actor(Minecraft mc){for(var e:mc.level.entitiesForRendering())if(e instanceof MapActorEntity a)return a;throw new AssertionError("Missing archer");}
    private void play(Minecraft mc,String clip){mc.getSingleplayerServer().execute(()->{for(var e:mc.getSingleplayerServer().overworld().getAllEntities())if(e instanceof MapActorEntity a)a.playAnimation(clip);});}
    private void verify(Minecraft mc,String label)throws Exception{
        var a=actor(mc);var renderer=(me.cryo.zombierool.client.renderer.MapActorRenderer)mc.getEntityRenderDispatcher().getRenderer(a);var model=renderer.getModel();
        var field=model.getClass().getDeclaredField("bones");field.setAccessible(true);var bones=(java.util.Map<?,?>)field.get(model);var path=new java.util.ArrayList<net.minecraft.client.model.geom.ModelPart>();String name="left_weapon";
        while(!name.isEmpty()){var bone=bones.get(name);if(bone==null)break;var part=bone.getClass().getDeclaredMethod("part");part.setAccessible(true);path.add(0,(net.minecraft.client.model.geom.ModelPart)part.invoke(bone));var parent=bone.getClass().getDeclaredMethod("parent");parent.setAccessible(true);name=(String)parent.invoke(bone);}
        var pose=new com.mojang.blaze3d.vertex.PoseStack();for(var part:path)part.translateAndRotate(pose);var string=new org.joml.Vector3f(1,0,0);pose.last().normal().transform(string);var limbs=new org.joml.Vector3f(0,1,0);pose.last().normal().transform(limbs);
        check(string.z>.5&&limbs.y>.85,"Bow string faces its owner and limbs stay vertical: "+label+" string="+string+" limbs="+limbs);
    }
    private void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!opened){opened=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.gameRenderer.setRenderHand(false);org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        for(int i=0;i<ids.length;i++){String id=ids[i];int start=40+i*90;
            if(age==start)mc.getSingleplayerServer().execute(()->{try{var level=mc.getSingleplayerServer().overworld();me.cryo.zombierool.gameplay.MapActors.end(level);level.setDayTime(6000);for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)for(int y=64;y<72;y++)level.setBlock(new net.minecraft.core.BlockPos(x,y,z),y==64?net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(level,.5,65,4.5,180,0);String uuid=me.cryo.zombierool.gameplay.MapActors.get(level).spawn(level,id,new net.minecraft.world.phys.Vec3(.5,65,.5),"");var a=(MapActorEntity)level.getEntity(java.util.UUID.fromString(uuid));a.setNoAi(true);a.setYRot(0);a.setYBodyRot(0);a.setYHeadRot(0);}catch(Throwable t){failure=t;}});
            if(age==start+12){verify(mc,id+" idle");photo(mc,id+"-idle");play(mc,"cast");}
            if(age==start+27){verify(mc,id+" draw");photo(mc,id+"-draw");play(mc,"attack");}
            if(age==start+42){verify(mc,id+" release");photo(mc,id+"-release");mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.teleportTo(p.serverLevel(),-3.5,65,.5,-90,0);});}
            if(age==start+55){photo(mc,id+"-side");}
        }
        if(age==220){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" native archer grip checks");mc.stop();}
    }catch(Throwable t){done=true;t.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
