package local.atmospherevisual;

import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.client.*;
import me.cryo.zombierool.client.gui.*;
import me.cryo.zombierool.configuration.ZRClientConfig;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("atmospherevisual")
public final class FeedbackRevisionVisualTest {
    boolean requested,done;int age,checks;volatile String failure;MapDownloaderScreen catalogue;Object catalogueEntry;
    public FeedbackRevisionVisualTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;System.out.println("ATMOSPHERE VISUAL CHECK: "+label);}
    void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable e){e.printStackTrace();failure=e.toString();}});}
    void shot(String name)throws Exception{try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){image.writeToFile(java.nio.file.Path.of(System.getProperty("atmosphere.output"),name+".png"));}}
    Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void set(Object o,String name,Object value)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    void setup(net.minecraft.server.level.ServerPlayer p){
        var l=p.serverLevel();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.getInventory().clearContent();
        for(var at:BlockPos.betweenClosed(91,79,89,109,85,105))l.setBlock(at,at.getY()==79?Blocks.SMOOTH_STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        var door=(DefenseDoorSystem.DefenseDoorBlock)me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get();
        for(int i=0;i<2;i++){var at=new BlockPos(96+i,80,95);var state=door.defaultBlockState().setValue(DefenseDoorSystem.BaseDefenseDoor.CENTERED,true).setValue(DoorBlock.FACING,Direction.SOUTH).setValue(DoorBlock.HINGE,i==0?DoorHingeSide.LEFT:DoorHingeSide.RIGHT);l.setBlock(at,state,3);l.setBlock(at.above(),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),3);}
        var box=MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxSystem.MysteryBoxBlock.ACTIVE,true).setValue(MysteryBoxSystem.MysteryBoxBlock.OPEN,true);
        var pos=new BlockPos(103,80,95);l.setBlock(pos,box,3);l.setBlock(MysteryBoxManager.getOtherPartPos(pos,box.getValue(MysteryBoxSystem.MysteryBoxBlock.FACING)),box.setValue(MysteryBoxSystem.MysteryBoxBlock.PART,true),3);
        check(box.setValue(MysteryBoxSystem.MysteryBoxBlock.OPEN,false).getLightEmission(l,pos)==12,"Closed active mystery box uses a stable bright light level");
        check(box.getLightEmission(l,pos)==12,"Open active mystery box uses a stable bright light level");
        check(box.setValue(MysteryBoxSystem.MysteryBoxBlock.ACTIVE,false).getLightEmission(l,pos)==0,"Inactive mystery box does not illuminate an empty location");
        var planks=(FurnitureSystem.BarricadePlankBlock)FurnitureSystem.ALL.get("wall_barricade_planks").get();var plankPos=new BlockPos(106,80,95);var plankState=planks.defaultBlockState().setValue(FurnitureSystem.BarricadePlankBlock.VARIANT,9);l.setBlock(plankPos,plankState,3);
        planks.use(plankState,l,plankPos,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(plankPos),Direction.SOUTH,plankPos,false));
        check(l.getBlockState(plankPos).getValue(FurnitureSystem.BarricadePlankBlock.VARIANT)==0,"Creative right-click cycles the tenth plank variant back to the first");
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);planks.use(l.getBlockState(plankPos),l,plankPos,p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(plankPos),Direction.SOUTH,plankPos,false));
        check(l.getBlockState(plankPos).getValue(FurnitureSystem.BarricadePlankBlock.VARIANT)==0,"Survival interaction cannot change the mapper's plank arrangement");
        try{var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);boolean previous=running.getBoolean(null);try{running.setBoolean(null,true);var event=new net.minecraftforge.event.entity.living.LivingKnockBackEvent(p,.4f,1,0);me.cryo.zombierool.handlers.PlayerInjuryEvents.knockback(event);check(event.isCanceled(),"Combat knockback is removed for survival players during a match");p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);event=new net.minecraftforge.event.entity.living.LivingKnockBackEvent(p,.4f,1,0);me.cryo.zombierool.handlers.PlayerInjuryEvents.knockback(event);check(!event.isCanceled(),"Creative movement is unaffected");}finally{running.setBoolean(null,previous);}}catch(ReflectiveOperationException error){throw new RuntimeException(error);}
        p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);me.cryo.zombierool.config.WorldConfig.get(l).setDayNightMode("night");p.teleportTo(l,100.5,80,90,0,13);l.setDayTime(18000);
    }
    void repair()throws Exception{
        var mc=Minecraft.getInstance();RepairLoopClient.update(new BlockPos(96,80,95));var f=RepairLoopClient.class.getDeclaredField("active");f.setAccessible(true);var first=(net.minecraft.client.resources.sounds.AbstractTickableSoundInstance)f.get(null);
        check(first.getLocation().getPath().equals("boards_float")&&first.isLooping(),"Held repair uses the original boards_float loop");RepairLoopClient.update(new BlockPos(96,80,95));check(f.get(null)==first,"Repair holding reuses one loop instead of stacking sounds");RepairLoopClient.update(null);check(!first.isStopped(),"Releasing repair keeps the floating-board tail playing");
        for(int i=0;i<20;i++){RepairLoopClient.update(null);first.tick();}check(!first.isStopped(),"Repeated idle updates preserve the repair tail for at least one second");
        RepairLoopClient.update(new BlockPos(96,80,95));check(f.get(null)==first,"Resuming repair reuses the tail without overlapping sounds");first.tick();check(!first.isStopped(),"Resuming repair cancels its scheduled stop");
        RepairLoopClient.update(null);for(int i=0;i<24;i++)first.tick();check(!first.isStopped(),"Repair tail lasts 1.25 seconds");first.tick();check(first.isStopped(),"Repair loop stops after its tail expires");
        var state=mc.level.getBlockState(new BlockPos(103,80,95));var model=mc.getBlockRenderer().getBlockModel(state);var quads=new java.util.ArrayList<>(model.getQuads(state,null,net.minecraft.util.RandomSource.create(1)));for(var d:Direction.values())quads.addAll(model.getQuads(state,d,net.minecraft.util.RandomSource.create(1)));
        check(quads.stream().anyMatch(q->{var v=q.getVertices();return v.length>=8&&(v[6]&0x00F000F0)==0x00F000F0;}),"Mystery-box interior bakes with emissive lightmap data");
    }
    void maps(boolean community)throws Exception{
        var mc=Minecraft.getInstance();ZRClientConfig.ALLOW_NETWORK_REQUESTS.set(false);var screen=new MapDownloaderScreen(null);
        set(screen,"requested",true);set(screen,"tab",community?2:1);
        var json=new com.google.gson.JsonObject();json.addProperty("id","fixture");json.addProperty("name","Fixture map");json.addProperty("description","Local catalogue preview");json.addProperty("download_url","https://example.com/map.zip");json.addProperty("author","Fixture");json.addProperty("zr_version","26.7");json.addProperty("game_version","1.20.1");json.addProperty("version","1.0");
        var entry=new me.cryo.zombierool.client.MapCatalogue.Entry(community?"community":"official",json);
        var field=screen.getClass().getDeclaredField("maps");field.setAccessible(true);((java.util.List)field.get(screen)).add(entry);mc.setScreen(screen);
        catalogue=screen;catalogueEntry=entry;
        check(MapDownloaderScreen.validMapLink("https://example.com/map.zip")&&!MapDownloaderScreen.validMapLink("javascript:alert(1)"),"Catalogue actions accept only web links");
    }
    void link()throws Exception{
        var mc=Minecraft.getInstance();set(catalogue,"openingChild",true);mc.setScreen(new me.cryo.zombierool.client.gui.MapDetailsScreen(catalogue,(me.cryo.zombierool.client.MapCatalogue.Entry)catalogueEntry));
        var label=net.minecraft.network.chat.Component.translatable("gui.zombierool.downloader.get_link").getString();mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();
        check(mc.screen.children().stream().anyMatch(c->c instanceof net.minecraft.client.gui.components.EditBox box&&box.getValue().equals("https://example.com/map.zip")),"Get a link exposes a selectable map URL without installation");
    }
    void tick(TickEvent.ClientTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        try{
            if(failure!=null)throw new AssertionError(failure);
            if(!requested){requested=true;mc.options.pauseOnLostFocus=false;org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"AtmosphereFixture");return;}
            if(mc.level==null||mc.player==null)return;age++;
            if(age==20){MapAtmosphereClient.receive("preview",MapAtmosphere.defaults());ClientEnvironmentEffects.setFogPreset("none",0,0,0,.5f,512);server(this::setup);}
            if(age==45){shot("doors-and-box");repair();}
            if(age==50)maps(false);
            if(age==55)shot("maps-official");
            if(age==56)link();
            if(age==59)shot("map-link");
            if(age==60)maps(true);
            if(age==65)shot("maps-community");
            if(age==65){mc.setScreen(null);WaveManager.setClientGameRunning(true);ZRClientConfig.HIDE_HEALTH.set(false);}
            if(age>=70&&age<=95)mc.player.setHealth(mc.player.getMaxHealth()*.18f);
            if(age==85){check(InjuryFeedback.audioStep()>0,"Critical health enables progressive audio feedback");shot("injured-health-visible");ZRClientConfig.HIDE_HEALTH.set(true);}
            if(age==95){shot("injured-health-hidden");check(InjuryFeedback.audioStep()>0,"Hiding the health bar keeps injury feedback enabled");}
            if(age==100)mc.player.setHealth(mc.player.getMaxHealth());
            if(age==125){check(InjuryFeedback.audioStep()==0,"Regeneration restores normal hearing");shot("recovered");WaveManager.setClientGameRunning(false);InjuryFeedback.reset();check(!InjuryFeedback.active(),"Ending a match clears injury feedback");done=true;System.out.println("ATMOSPHERE VISUAL PASS: "+checks+" feedback, catalogue and barricade checks");mc.stop();}
        }catch(Throwable error){error.printStackTrace();done=true;System.out.println("ATMOSPHERE VISUAL FAILED: "+error);mc.stop();}
    }
}
