package local.strathprobe;

import java.util.*;
import com.mojang.authlib.GameProfile;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.*;
import me.cryo.zombierool.bonuses.BonusManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("strathprobe")
public final class StratholmeBalanceTest {
    public StratholmeBalanceTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("BALANCE CHECK: "+text);}
    private static <T extends Mob>T place(ServerLevel level,EntityType<T> type,int x){var mob=type.create(level);mob.moveTo(x+.5,200,.5,0,0);mob.getPersistentData().putBoolean("zr_debug_spawn",true);if(!level.addFreshEntity(mob))throw new AssertionError("Cannot place test mob");return mob;}
    @SuppressWarnings("unchecked") private void start(ServerStartedEvent event){var level=event.getServer().overworld();try{
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"BalanceProbe"));level.addNewPlayer(player);player.setPos(0,200,15);
        var response=MapActorAuthoring.request(player,"actor_list","","","");
        for(String id:response.getCompound("defaults").getAllKeys()){
            var p=response.getCompound("defaults").getCompound(id);
            if(p.contains("appearances")||p.contains("animations")||p.contains("boxes"))throw new AssertionError("Editor carries unused model payload");
        }
        // Recreate the previous packet and prove the default NBT network quota rejects it.
        var previous=response.copy();var actors=MapActors.get(level);
        for(String id:actors.ids()){var p=actors.profile(id);if(p.contains("appearances"))previous.getCompound("defaults").getCompound(id).put("appearances",p.get("appearances").copy());}
        var bad=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());boolean rejected=false;
        try{bad.writeNbt(previous);bad.readNbt();}catch(RuntimeException expected){rejected=true;}finally{bad.release();}
        check(rejected,"The old Risen catalogue reproduces the network NBT quota failure");
        var buffer=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{LuaWorkbenchPackets.Response.encode(new LuaWorkbenchPackets.Response(response),buffer);int bytes=buffer.readableBytes();var decoded=LuaWorkbenchPackets.Response.decode(buffer);check(decoded.data().equals(response)&&bytes<100000,"Spawner catalogue survives the real packet codec ("+bytes+" bytes)");}finally{buffer.release();}
        for(int x=-5;x<=35;x++)for(int z=-2;z<=2;z++)for(int y=199;y<=205;y++)level.setBlock(new BlockPos(x,y,z),y==199?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
        var field=WaveManager.class.getDeclaredField("activeMobs");field.setAccessible(true);var tracked=(Set<UUID>)field.get(null);tracked.clear();
        var entities=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE;
        var zombie=place(level,entities.get(),0);var crawler=place(level,me.cryo.zombierool.init.ZombieroolModEntities.CRAWLER.get(),3);var hound=place(level,me.cryo.zombierool.init.ZombieroolModEntities.HELLHOUND.get(),6);
        for(var mob:List.of(zombie,crawler,hound))tracked.add(mob.getUUID());
        var untracked=place(level,entities.get(),9);
        var resident=(LivingEntity)level.getEntity(UUID.fromString(actors.spawn(level,"builtin_scourge_zombie",new Vec3(12.5,200,.5),"")));
        var risen=(LivingEntity)level.getEntity(UUID.fromString(actors.spawn(level,"scarlet_zombie",new Vec3(15.5,200,.5),"")));
        var boss=(LivingEntity)level.getEntity(UUID.fromString(actors.spawn(level,"builtin_abomination",new Vec3(19.5,200,.5),"")));
        // A map resident must remain protected even if manually registered in a wave.
        tracked.add(resident.getUUID());tracked.add(boss.getUUID());
        BonusManager.loadBonuses();var collect=BonusManager.class.getDeclaredMethod("collectBonus",BonusManager.Bonus.class,net.minecraft.world.entity.player.Player.class,ServerLevel.class,Vec3.class,UUID.class);collect.setAccessible(true);
        collect.invoke(null,BonusManager.getBonus("nuke"),player,level,player.position(),UUID.randomUUID());
        check(!zombie.isAlive()&&!crawler.isAlive()&&!hound.isAlive(),"Nuke kills the actual tracked wave zombie, crawler and hound");
        check(untracked.isAlive()&&resident.isAlive()&&risen.isAlive()&&boss.isAlive(),"Nuke preserves untracked enemies, placed Scourge, Risen and bosses");
        LuaScriptManager.loadScripts(level);LuaScriptManager.callEvent("OnGameStart");
        var config=me.cryo.zombierool.config.WorldConfig.get(level);
        check(config.getSpawnIntensity().equals("normal")&&config.getZombieSprintWave()==10,"Exploration script replaces chaos and delays sprinting");
        var special=me.cryo.zombierool.config.SpecialEnemyConfig.get(level);
        check(special.getInt("firstWave")==6&&special.getInt("interval")==4,"Recurring bosses wait until wave six, with four waves between appearances");
        System.out.println("BALANCE PASS: reproduced editor failure, bounded packet, wave-only nuke and map exploration settings");
    }catch(Throwable error){error.printStackTrace();}finally{event.getServer().halt(false);}}
}
