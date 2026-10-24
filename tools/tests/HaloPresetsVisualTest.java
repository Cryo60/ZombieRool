package local.atmospherevisual;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.client.MapAtmosphereClient;
import me.cryo.zombierool.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.nbt.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class HaloPresetsVisualTest {
    boolean requested,done;int age,checks;volatile String failure;String[] ids={"halo_blizzard","halo_zenith"};String[] names={"Blizzard","Zenith"};
    public HaloPresetsVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+label);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){failure=t.toString();t.printStackTrace();}});}
    void button(String label){for(var child:java.util.List.copyOf(Minecraft.getInstance().screen.children()))if(child instanceof Button b&&b.getMessage().getString().equals(label)){b.onPress();return;}throw new AssertionError("Button missing: "+label);}
    void tick(TickEvent.ClientTickEvent e){if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;try{
        if(failure!=null)throw new AssertionError(failure);
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;age++;
        if(age==40)server(p->{p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(p.serverLevel(),.5,65,28.5,180,-25);MapAtmosphere.get(p.serverLevel()).configure(p.serverLevel(),MapAtmosphere.defaults());});
        for(int i=0;i<ids.length;i++){int start=60+i*85;String id=ids[i],name=names[i];
            if(age==start)MapAtmosphereScreen.request(null);
            if(age==start+15){boolean opened=false;for(var child:java.util.List.copyOf(mc.screen.children()))if(child instanceof Button b&&b.getMessage().getString().endsWith(" ▾")){b.onPress();opened=true;break;}check(opened,"Preset selector opens for "+name);}
            if(age==start+18){boolean found=false;for(var child:mc.screen.children())if(child instanceof ObjectSelectionList<?> list)for(var row:list.children())if(row.getNarration().getString().equals(name)){row.mouseClicked(180,80,0);found=true;break;}check(found,"Menu lists "+name);}
            if(age==start+30){check(MapAtmosphereClient.settings().getString("presetId").equals(id),"Menu previews "+name);button("Terminé");}
            if(age==start+45)server(p->{var d=MapAtmosphere.get(p.serverLevel());check(d.settings().getString("presetId").equals(id)&&MapAtmosphere.load(d.save(new CompoundTag())).settings().getString("presetId").equals(id),name+" saves and survives reloading");});
            if(age==start+65){var n=MapAtmosphereClient.settings();check(n.getString("skyLayout").equals("panorama")&&mc.getResourceManager().getResource(new net.minecraft.resources.ResourceLocation(n.getString("skyTexture"))).isPresent(),name+" uses its extracted panorama");try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output")+"/"+id+".png"));int ground=image.getPixelRGBA(100,550);check((ground&255)+(ground>>8&255)+(ground>>16&255)>60,"Foreground remains visible with "+name);}}
        }
        if(age==305){var list=new ListTag();for(int i=0;i<ids.length;i++){var row=new CompoundTag();row.putString("id",ids[i]);row.putString("name",names[i]);list.add(row);}mc.setScreen(new AtmospherePresetPickerScreen(null,list,"",id->{}));for(String name:names){boolean found=false;for(var child:mc.screen.children())if(child instanceof ObjectSelectionList<?> entries)for(var row:entries.children())if(row.getNarration().getString().equals(name))found=true;check(found,"Teleporter selector lists "+name);}mc.setScreen(null);}
        if(age==315){done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" extracted Halo preset checks");mc.stop();}
    }catch(Throwable t){t.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAIL: "+t);mc.stop();}}
}
