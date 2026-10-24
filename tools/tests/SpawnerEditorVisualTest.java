package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.*;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class SpawnerEditorVisualTest {
    boolean requested,done;int age;volatile String failure;final BlockPos pos=new BlockPos(0,65,15);
    public SpawnerEditorVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable error){failure=error.toString();error.printStackTrace();}});}
    void open(){server(p->{var data=new CompoundTag();data.putString("action","actor_open");data.putLong("pos",pos.asLong());NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new LuaWorkbenchPackets.Response(data));});}
    void tick(TickEvent.ClientTickEvent event){if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==20)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);var l=p.serverLevel();p.teleportTo(l,0,66,18,180,10);l.setBlock(pos,me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get().defaultBlockState(),3);var rule=MapActorTemplates.marker();rule.putString("profile","scarlet_zombie_archer");MapActors.get(l).marker(pos,rule);});
        if(age==30||age==90)open();
        if(age==70||age==130){
            if(!(mc.screen instanceof me.cryo.zombierool.client.gui.MapActorStudioScreen))throw new AssertionError("Spawner editor disconnected or failed to open");
            var f=mc.screen.getClass().getDeclaredField("defaults");f.setAccessible(true);var defaults=(CompoundTag)f.get(mc.screen);
            long roles=defaults.getAllKeys().stream().filter(id->id.startsWith("scarlet_zombie")).count();
            if(roles!=6||defaults.getAllKeys().stream().anyMatch(id->id.startsWith("scarlet_zombie")&&id.endsWith("_female")))throw new AssertionError("Wrong unified role list");
            for(String key:defaults.getAllKeys())if(defaults.getCompound(key).contains("appearances"))throw new AssertionError("Heavy appearance leaked into defaults");
            if(age==70)try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),"spawner-editor-fixed.png"));}
            mc.setScreen(null);
        }
        if(age==140){done=true;System.out.println("ATMOSPHERE VISUAL PASS: real spawner editor opened twice with all six unified Risen roles and no disconnect");mc.stop();}
    }catch(Throwable error){done=true;error.printStackTrace();System.out.println("ATMOSPHERE VISUAL FAIL: "+error);mc.stop();}}
}
