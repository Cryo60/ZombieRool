package local.atmospherevisual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.client.gui.WorldConfigMenuScreen;
import me.cryo.zombierool.gameplay.MapAtmosphere;
import me.cryo.zombierool.gameplay.SkyObjects;

@Mod("atmospherevisual")
public final class RevisionMenuVisualTest {
    boolean requested,done;int age;
    public RevisionMenuVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try {
            if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
            if(mc.player==null||mc.level==null)return;age++;
            if(age==30){
                mc.player.setYRot(0);mc.player.setXRot(-35);me.cryo.zombierool.client.ClientEnvironmentEffects.setFogPreset("none",0,0,0,.5f,512);
                var settings=MapAtmosphere.preset("origins");settings.put("sources",new net.minecraft.nbt.ListTag());
                var objects=new net.minecraft.nbt.ListTag();var moon=SkyObjects.object("test_moon","moon",0,35,28);moon.putDouble("brightness",2);objects.add(moon);settings.put("skyObjects",objects);
                me.cryo.zombierool.client.MapAtmosphereClient.receive("preview",settings);
            }
            if(age==65){if(!me.cryo.zombierool.client.MapAtmosphereClient.deferredAmbient())throw new AssertionError("Sky compositor inactive");shot("moon-preview");if(me.cryo.zombierool.client.SkyObjectsRenderer.geometryCount(me.cryo.zombierool.client.MapAtmosphereClient.settings(),0)<1)throw new AssertionError("Moon geometry missing");
                var data=new me.cryo.zombierool.config.WorldConfig().save(new CompoundTag());mc.setScreen(new WorldConfigMenuScreen(new me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket(data,false,1)));
            }
            if(age==70){var screen=(WorldConfigMenuScreen)mc.screen;for(var child:screen.children())if(child instanceof EditBox box&&box.getMessage().equals(net.minecraft.network.chat.Component.translatable("config.zombierool.search"))){box.setValue("sprint");break;}}
            if(age==75){
                shot("config-search");var screen=(WorldConfigMenuScreen)mc.screen;var field=WorldConfigMenuScreen.class.getDeclaredField("listWidget");field.setAccessible(true);var list=(net.minecraft.client.gui.components.ContainerObjectSelectionList<?>)field.get(screen);
                boolean restored=false;
                for(var entry:list.children()){var method=entry.getClass().getSuperclass().getDeclaredMethod("searchText");method.setAccessible(true);if(((String)method.invoke(entry)).contains(net.minecraft.network.chat.Component.translatable("config.zombierool.sprintBgSounds").getString()))restored=true;}
                if(!restored)throw new AssertionError("Global search did not find restored sprint ambience");
                for(var child:screen.children())if(child instanceof EditBox box&&box.getMessage().equals(net.minecraft.network.chat.Component.translatable("config.zombierool.search"))){box.setValue("musique");break;}
            }
            if(age==80){shot("config-sprint");System.out.println("ATMOSPHERE VISUAL PASS: moon shader, global search and restored options");done=true;mc.stop();}
        }catch(Throwable error){error.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAILED: "+error);mc.stop();}
    }
}
