package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class CarDecorVisualTest {
    boolean requested,done;int age,checks;volatile String failure;
    final BlockPos pos=new BlockPos(0,65,15);
    record Case(String model,String state){}
    final java.util.List<Case> cases=new java.util.ArrayList<>();
    public CarDecorVisualTest(){for(String model:AmbientScenery.carModels())for(String state:AmbientScenery.states("car"))cases.add(new Case(model,state));MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+text);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
    void press(String key){String text=net.minecraft.network.chat.Component.translatable(key).getString();for(var widget:java.util.List.copyOf(Minecraft.getInstance().screen.children()))if(widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(text)){b.onPress();return;}throw new AssertionError("Missing button: "+text);}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==30)server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);l.setDayTime(6000);for(var at:BlockPos.betweenClosed(-8,64,7,8,74,24))l.setBlock(at,at.getY()==64?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);p.teleportTo(l,5,68,21,140,21);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());});
        for(int i=0;i<cases.size();i++){int start=60+i*20;var c=cases.get(i);String id=c.model.equals("sedan")?"decor_car":"decor_car_"+c.model;
            if(age==start)server(p->{var l=p.serverLevel();l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);var block=MapDeviceSystem.ALL_TYPES.get(id).get();l.setBlock(pos,block.defaultBlockState(),3);var d=(MapDeviceSystem.Device)l.getBlockEntity(pos);
                check(d.scenery.getString("vehicle").equals("car")&&d.scenery.getString("carModel").equals(c.model),id+" inventory block defaults to the correct car model");
                var n=d.config();n.putBoolean("requires_power",false);n.getCompound("scenery").putString("variant",c.state);n.getCompound("scenery").putDouble("yaw",25);d.configure(n);d.setActive(true);VehicleDecorPhysics.invalidate(l);
                var geometry=VehicleDecorPhysics.geometry(l,pos);check(geometry!=null&&!geometry.boxes().isEmpty(),c+" has solid model-derived collision");
                var hit=VehicleDecorPhysics.pick(l,geometry.bounds().getCenter().add(0,0,8),geometry.bounds().getCenter(),BlockHitResult.miss(geometry.bounds().getCenter(),net.minecraft.core.Direction.NORTH,pos));check(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos),c+" can be selected through the visible body");
                var copy=new MapDeviceSystem.Device(pos,d.getBlockState());copy.load(d.saveWithoutMetadata());check(copy.scenery.equals(d.scenery),c+" survives save/reload");
                check(geometry.movement().stream().allMatch(b->b.maxY-b.minY>=1.99),c+" retains safe movement barriers");
            });
            if(age==start+15){var d=(MapDeviceSystem.Device)mc.level.getBlockEntity(pos);var stack=new net.minecraft.world.item.ItemStack(d.getBlockState().getBlock());
                check(mc.getItemRenderer().getModel(stack,mc.level,null,0)!=mc.getModelManager().getMissingModel(),id+" inventory model resolves");
                check(me.cryo.zombierool.init.CreativeCatalog.category(stack)==me.cryo.zombierool.init.CreativeCatalog.Category.AMBIENCE,id+" appears in the ambience inventory category");
                check(mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation("zombierool","scenery/vehicles/"+AmbientScenery.vehicleMesh(d.scenery)+".json")).isPresent(),c+" final surface resource resolves");shot("car-"+c.model+"-"+c.state);
            }
        }
        int finish=60+cases.size()*20;
        if(age==finish)server(p->{p.teleportTo(p.serverLevel(),0,66,19,180,10);MapDevicePackets.open(p,(MapDeviceSystem.Device)p.serverLevel().getBlockEntity(pos));});
        if(age==finish+8){check(mc.screen instanceof me.cryo.zombierool.client.gui.AmbientSceneryScreen,"Car opens shared decoration settings");press("gui.zombierool.scenery.carModel.police");check(mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).count()==9,"Car model picker lists eight models and Back");press("gui.zombierool.scenery.carModel.compact");press("gui.zombierool.device.save");}
        if(age==finish+18)server(p->{var d=(MapDeviceSystem.Device)p.serverLevel().getBlockEntity(pos);check(d.scenery.getString("carModel").equals("compact"),"Model picker saves through the real device packet");var n=d.config();n.getCompound("scenery").putString("carModel","invalid");d.configure(n);check(d.scenery.getString("carModel").equals("sedan"),"Unknown car models sanitize safely");});
        if(age==finish+25){check(org.lwjgl.opengl.GL11.glGetError()==0,"All car states render without OpenGL errors");done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" car decoration checks");mc.stop();}
    }catch(Throwable e){done=true;e.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+e);mc.stop();}}
}
