package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class DwarvenArtilleryVisualTest {
    boolean requested,done;int age;volatile String failure;final BlockPos pos=new BlockPos(0,65,15);
    public DwarvenArtilleryVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){failure=error.toString();error.printStackTrace();}});}
    void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==25){mc.options.hideGui=true;server(p->{var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);l.setDayTime(6000);l.setWeatherParameters(100000,0,false,false);MapAtmosphere.get(l).configure(l,MapAtmosphere.defaults());for(int x=-7;x<=7;x++)for(int z=8;z<=24;z++)for(int y=64;y<=75;y++)l.setBlock(new BlockPos(x,y,z),y==64?net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            l.setBlock(pos,MapDeviceSystem.ALL_TYPES.get("ambient_dca").get().defaultBlockState(),3);var d=(MapDeviceSystem.Device)l.getBlockEntity(pos);var n=d.config();n.putBoolean("requires_power",false);n.putBoolean("enabled",true);n.putInt("interval",80);n.getCompound("scenery").putString("dcaModel","dwarven");n.getCompound("scenery").putString("fireMode","regular");n.getCompound("scenery").putDouble("targetX",.5);n.getCompound("scenery").putDouble("targetY",73);n.getCompound("scenery").putDouble("targetZ",55);d.configure(n);p.teleportTo(l,5.2,67.3,22.4,147,17);});}
        if(age==70){var d=(MapDeviceSystem.Device)mc.level.getBlockEntity(pos);if(!d.scenery.getString("dcaModel").equals("dwarven"))throw new AssertionError("Model failed network synchronization");shot("dwarven-bombard-front");}
        if(age==72)server(p->p.teleportTo(p.serverLevel(),-4.2,67.1,10.3,-43,16));
        if(age==100)shot("dwarven-bombard-rear");
        if(age==102)server(p->{var d=(MapDeviceSystem.Device)p.serverLevel().getBlockEntity(pos);MapDevicePackets.open(p,d);});
        if(age==112){
            if(!(mc.screen instanceof me.cryo.zombierool.client.gui.AmbientSceneryScreen))throw new AssertionError("Missing scenery editor");
            String text=net.minecraft.network.chat.Component.translatable("gui.zombierool.scenery.model.dwarven").getString();
            if(!text.equals("Bombarde naine")||mc.screen.children().stream().noneMatch(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(text)))throw new AssertionError("Model missing from translated selector");
            shot("dwarven-bombard-menu");
            for(var widget:java.util.List.copyOf(mc.screen.children()))if(widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(text)){b.onPress();break;}
            String save=net.minecraft.network.chat.Component.translatable("gui.zombierool.device.save").getString();
            for(var widget:java.util.List.copyOf(mc.screen.children()))if(widget instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(save)){b.onPress();break;}
        }
        if(age==125){var d=(MapDeviceSystem.Device)mc.level.getBlockEntity(pos);if(!d.scenery.getString("dcaModel").equals("flak88"))throw new AssertionError("Other artillery styles cannot be selected");done=true;System.out.println("ATMOSPHERE VISUAL PASS: dwarven bombard rendering, synchronization, translated menu and original model selection");mc.stop();}
    }catch(Throwable error){done=true;error.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+error);mc.stop();}}
}
