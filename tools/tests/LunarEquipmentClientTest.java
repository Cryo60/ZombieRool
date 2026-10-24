package local.skingalleryclient;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import me.cryo.zombierool.gameplay.LunarEquipment;
import me.cryo.zombierool.scripting.ZombieroolAPI;
import java.nio.file.Path;

/** Real integrated-client scenario: server authority, oxygen, zones, animations and rendering. */
@Mod("skingalleryclient")
public final class LunarEquipmentClientTest {
    private boolean requested,done;private int ticks;private volatile String failure;private volatile int checks;private float protectedHealth;
    public LunarEquipmentClientTest(){MinecraftForge.EVENT_BUS.addListener(this::tick);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Throwable t){t.printStackTrace();failure=t.toString();}});}
    private void shot(String name)throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("D:/Dev/ZombieRool/build/zombie-presets/lunar-"+name+".png"));}System.out.println("LUNAR FRAME: "+name);}
    private void tick(TickEvent.ClientTickEvent e){
        if(done||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        if(!requested){requested=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.fov().set(70);org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.createWorldOpenFlows().loadLevel(mc.screen,"LunarFixture");return;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;ticks++;
        try{
            if(failure!=null)throw new AssertionError(failure);
            if(ticks==80)server(p->{p.teleportTo(p.serverLevel(),18.5,65,-6,0,0);p.setGameMode(GameType.CREATIVE);me.cryo.zombierool.player.PlayerDownManager.resetAll();p.serverLevel().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_NATURAL_REGENERATION).set(false,p.server);LunarEquipment.revoke(p);me.cryo.zombierool.block.system.LunarStation.interact(p);check(LunarEquipment.data(p).getBoolean("changing"),"Station starts equip animation");check(!LunarEquipment.toggle(p),"No duplicate toggle during animation");});
            if(ticks==90){check(me.cryo.zombierool.client.ThirdPersonAnimHandler.isAnimationPlaying(mc.player.getUUID()),"Client receives equip animation");shot("equip-animation");}
            if(ticks==125){check(LunarEquipment.equipped(mc.player),"Client sees equipped state");mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);server(p->{check(LunarEquipment.equipped(p),"Server seal completed");check(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),"Secondary helmet preserves armor slot");});}
            if(ticks==145)shot("equipped-third-person");
            if(ticks==165){mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);server(p->check(new ZombieroolAPI(p.serverLevel()).setLunarHelmetEquipped(p.getUUID().toString(),false),"Lua API starts removal"));}
            if(ticks==181)shot("remove-animation");
            if(ticks==210){check(!LunarEquipment.equipped(mc.player),"Removal synchronized");server(p->{var api=new ZombieroolAPI(p.serverLevel());api.configureLunarEnvironment(1,false,1);p.setGameMode(GameType.SURVIVAL);p.setHealth(p.getMaxHealth());protectedHealth=p.getHealth();p.teleportTo(p.serverLevel(),70.5,65,.5,0,0);LunarEquipment.data(p).putInt("air",20);});}
            if(ticks==250){shot("vacuum-unprotected");server(p->{check(p.getHealth()<protectedHealth,"Vacuum inflicts asphyxia damage without helmet");check(LunarEquipment.data(p).getDouble("gravity")<.2,"Lunar zone changes server gravity");p.setHealth(p.getMaxHealth());check(LunarEquipment.setEquipped(p,true),"Can equip in vacuum");});}
            if(ticks==300)server(p->{check(LunarEquipment.equipped(p),"Helmet seals in vacuum");check(LunarEquipment.data(p).getInt("air")==20,"Helmet restores oxygen");protectedHealth=p.getHealth();});
            if(ticks==340)server(p->{check(p.getHealth()==protectedHealth,"Sealed helmet prevents ongoing asphyxia");p.teleportTo(p.serverLevel(),61.5,65,.5,0,0);check(LunarEquipment.setEquipped(p,false),"Can remove in pressurized room");});
            if(ticks==380){shot("pressurized-room");server(p->{check(!LunarEquipment.equipped(p)&&!LunarEquipment.data(p).getBoolean("vacuum"),"Pressurized room safe without helmet");check(LunarEquipment.data(p).getDouble("gravity")==1,"Room restores Earth gravity");check(p.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get()).getValue()==.08,"Gravity modifier removed");p.setGameMode(GameType.CREATIVE);p.teleportTo(p.serverLevel(),18.5,65,-6,0,0);});}
            if(ticks==415){shot("station");server(p->{check(p.serverLevel().getBlockState(new BlockPos(18,66,-3)).is(me.cryo.zombierool.block.system.LunarStation.STATION.get()),"Station upper half remains valid");});}
            if(ticks==445)server(p->p.teleportTo(p.serverLevel(),15.5,65,77.2,0,0));
            if(ticks==475)shot("penitencier-3");
            if(ticks==500){done=true;System.out.println("LUNAR CLIENT PASS: "+checks+" integrated checks");mc.stop();}
        }catch(Throwable t){t.printStackTrace();done=true;System.out.println("LUNAR CLIENT FAIL: "+t);mc.stop();}
    }
}
