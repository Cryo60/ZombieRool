package local.hotfixtest;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import me.cryo.zombierool.scripting.*;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.hotfix.HotfixGameplay;

public final class PrototypeSequenceTest {
    public record Sound(String id,float volume,float pitch) {}
    public static class SoundProbe extends ZombieroolAPI {
        public final List<Sound> sounds=new ArrayList<>();
        public SoundProbe(ServerLevel level){super(level);}
        @Override public void playGlobalSound(String id,float volume,float pitch){sounds.add(new Sound(id,volume,pitch));}
        @Override public void playDynamicSoundForPlayer(String uuid,String id,float volume,float pitch){sounds.add(new Sound(id,volume,pitch));}
        @Override public void playSoundForPlayer(String uuid,String id,float volume,float pitch){sounds.add(new Sound(id,volume,pitch));}
        @Override public void playSound(double x,double y,double z,String id,float volume,float pitch){sounds.add(new Sound(id,volume,pitch));}
    }
    static void check(boolean v,String message){HotfixRuntimeTest.check(v,message);}
    static void grenade(ServerLevel level,ServerPlayer player)throws Exception {
        var grenade=new me.cryo.zombierool.item.throwable.Grenade.GrenadeEntity(level,player,0);
        grenade.setPos(10.5,-60,16.5);
        var explode=grenade.getClass().getDeclaredMethod("explode");explode.setAccessible(true);explode.invoke(grenade);
        check(grenade.isRemoved(),"Real grenade explosion completes and discards its entity");
    }
    public static void run(ServerLevel level,ServerPlayer player)throws Exception {
        var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,false);
        WaveManager.startGame(level);
        var field=LuaScriptManager.class.getDeclaredField("globals");field.setAccessible(true);
        var globals=(me.cryo.zombierool.shadow.luaj.vm2.Globals)field.get(null);
        var probe=new SoundProbe(level);
        globals.set("ZombieroolAPI",me.cryo.zombierool.shadow.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(probe));
        globals.load("ZombieroolAPI:playGlobalSound('test'); ZombieroolAPI:playGlobalSound('test',0.4); ZombieroolAPI:playGlobalSound('test',0,1); ZombieroolAPI:playSound(0,0,0,'test')").call();
        check(probe.sounds.get(0).volume()==1&&probe.sounds.get(0).pitch()==1,"Lua sound without volume or pitch defaults to audible 1/1");
        check(Math.abs(probe.sounds.get(1).volume()-.4f)<.001&&probe.sounds.get(1).pitch()==1,"Lua sound with volume defaults only its pitch");
        check(probe.sounds.get(2).volume()==0,"Explicit zero sound volume is preserved");
        check(probe.sounds.get(3).volume()==1&&probe.sounds.get(3).pitch()==1,"Positional Lua sound also defaults to audible 1/1");
        var dynamic=new net.minecraft.resources.ResourceLocation("zombierool:sfx/misc/screaming");
        check(HotfixGameplay.resolveLegacySound(dynamic).equals(new net.minecraft.resources.ResourceLocation("zombierool:screaming")),"Screamer dynamic path resolves to the published built-in sound");
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        level.setBlock(new BlockPos(17,-55,-12),Blocks.STONE.defaultBlockState(),3);
        LuaScriptManager.callEvent("OnBlockInteract",player.getUUID().toString(),17.0,-55.0,-12.0);
        check(level.getBlockState(new BlockPos(17,-55,-12)).isAir(),"Prototype secret weapon interaction completes");
        check(probe.sounds.stream().anyMatch(s->s.id().equals("zombierool:mus_raygun_stinger")&&s.volume()==1&&s.pitch()==1),"Prototype Raygun stinger is requested at audible volume and pitch");
        LuaScriptManager.callEvent("OnLookTriggerActivated",player.getUUID().toString(),"richtofen_jumpscare");
        check(probe.sounds.stream().anyMatch(s->s.id().equals(dynamic.toString())&&s.volume()==1&&s.pitch()==1),"Prototype scope screamer requests an audible sound");
        for(var pos:List.of(new BlockPos(20,-51,-12),new BlockPos(-10,-55,8),new BlockPos(28,-54,19))) {
            level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            LuaScriptManager.callEvent("OnBlockShot",player.getUUID().toString(),(double)pos.getX(),(double)pos.getY(),(double)pos.getZ());
            check(level.getBlockState(pos).isAir(),"Prototype shootable step completes");
        }
        int cuts=0;
        for(var pos:List.of(new BlockPos(33,-59,-12),new BlockPos(15,-59,0),new BlockPos(37,-59,19))) {
            level.setBlock(pos,Blocks.SEA_LANTERN.defaultBlockState(),3);
            LuaScriptManager.callEvent("OnMeleeStrike",player.getUUID().toString(),(double)pos.getX(),(double)pos.getY(),(double)pos.getZ());
            check(level.getBlockState(pos).isAir(),"Full sequence Sea Lantern cut completes");
            if(++cuts==2){grenade(level,player);check(!WaveManager.UNLOCKED_CHANNELS.contains(7),"Grenade cannot unlock the secret after only 2/3 lanterns");}
        }
        grenade(level,player);
        check(WaveManager.UNLOCKED_CHANNELS.contains(7),"Real grenade unlocks channel 7 after all prototype prerequisites");
        check(level.getBlockState(new BlockPos(25,-57,34)).isAir(),"Prototype secret passage opens after the grenade");
        check(probe.sounds.stream().anyMatch(s->s.id().equals("minecraft:ui.toast.challenge_complete")&&s.volume()==1&&s.pitch()==1),"Grenade completion also requests an audible sound");
    }
}
