package local.hotfixtest;
import java.util.*;
import java.nio.file.*;
import net.minecraft.core.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import me.cryo.zombierool.hotfix.HotfixGameplay;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import me.cryo.zombierool.gameplay.WaveManager;
public final class HotfixExtendedTests {
 static void check(boolean v,String m){HotfixRuntimeTest.check(v,m);}
 public static void run(MinecraftServer server,ServerLevel level,ServerPlayer a,ServerPlayer b)throws Exception {
  var locked=me.cryo.zombierool.event.ServerEventHandler.class.getDeclaredMethod("isMatchInteractionLocked",Player.class);locked.setAccessible(true);
  var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,true);
  check(!(boolean)locked.invoke(null,a),"Vanilla interactions are unlocked even during a match");
  var hopper=new HopperBlockEntity(new BlockPos(35,80,0),Blocks.HOPPER.defaultBlockState());
  var drop=new ItemEntity(level,35.5,81,.5,new ItemStack(Items.DIAMOND));drop.getPersistentData().putUUID("zr_drop_owner",a.getUUID());
  check(HopperBlockEntity.addItem(hopper,drop)&&hopper.getItem(0).is(Items.DIAMOND),"Hopper consumes a private item drop");
  var restrict=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","restrict"));level.setBlock(new BlockPos(35,81,3),restrict.defaultBlockState(),3);level.setBlock(new BlockPos(35,81,5),Blocks.SEA_LANTERN.defaultBlockState(),3);
  a.setPos(35.5,80,1.5);var ray=new ClipContext(new Vec3(35.5,81.5,1.5),new Vec3(35.5,81.5,7.5),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,a);
  check(level.clip(ray).getBlockPos().equals(new BlockPos(35,81,3)),"Restriction block remains solid to a survival player");
  check(HotfixGameplay.clip(level,ray).getBlockPos().equals(new BlockPos(35,81,5)),"Weapon and melee ray reaches the shootable behind Restriction Block");
  var dir=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("zr_scripts");Files.createDirectories(dir);
  Files.writeString(dir.resolve("main.lua"),"runs=(runs or 0)+1; function OnGameStart() ZombieroolAPI:setData('fresh',tostring(runs)) end");
  running.setBoolean(null,false);
  var spawner=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","universal_spawner"));
  for(int x:new int[]{40,50}){level.setBlock(new BlockPos(x,79,0),Blocks.STONE.defaultBlockState(),3);level.setBlock(new BlockPos(x,80,0),spawner.defaultBlockState(),3);var be=(UniversalSpawnerBlockEntity)level.getBlockEntity(new BlockPos(x,80,0));be.setConfig(SpawnerMobType.PLAYER,"",x==40?"0":"6","0",false,1);}
  for(int repeat=0;repeat<4;repeat++){running.setBoolean(null,false);WaveManager.startGame(level);System.out.println("SPAWN TEST a="+a.position()+" b="+b.position()+" assignments="+WaveManager.PLAYER_RESPAWN_POINTS);check(WaveManager.PLAYER_RESPAWN_POINTS.get(a.getUUID()).getX()==40&&WaveManager.PLAYER_RESPAWN_POINTS.get(b.getUUID()).getX()==40,"Match start excludes the locked channel 6 player spawner");check(LuaScriptManager.getData("fresh").equals("1"),"Each match starts with fresh Lua globals");}
  var prototype=Path.of("D:/Dev/ZombieRool/build/hotfix-script-inspection/main.lua");
  for(int repeat=0;repeat<4;repeat++){
   Files.copy(prototype,dir.resolve("main.lua"),StandardCopyOption.REPLACE_EXISTING);running.setBoolean(null,false);WaveManager.startGame(level);
   for(var pos:List.of(new BlockPos(33,-59,-12),new BlockPos(15,-59,0),new BlockPos(37,-59,19))){level.setBlock(pos,Blocks.SEA_LANTERN.defaultBlockState(),3);check(level.getBlockState(pos).is(Blocks.SEA_LANTERN),"Sea Lantern fixture is placed before cutting");LuaScriptManager.callEvent("OnMeleeStrike",a.getUUID().toString(),(double)pos.getX(),(double)pos.getY(),(double)pos.getZ());check(level.getBlockState(pos).isAir(),"Prototype Sea Lantern cuts on retry "+repeat);}
  }
  PrototypeSequenceTest.run(level,a);
  var actual=Path.of("D:/Dev/ZombieRool/build/hotfix-script-inspection/runtime-symbols");
  Files.copy(actual.resolve("main.lua"),dir.resolve("main.lua"),StandardCopyOption.REPLACE_EXISTING);Files.copy(actual.resolve("bossfight.lua"),dir.resolve("bossfight.lua"),StandardCopyOption.REPLACE_EXISTING);
  var spawnedWardens=new ArrayList<net.minecraft.world.entity.monster.warden.Warden>();
  java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> observe=event->{if(event.getLevel()==level&&event.getEntity() instanceof net.minecraft.world.entity.monster.warden.Warden w)spawnedWardens.add(w);};
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(observe);
  running.setBoolean(null,true);LuaScriptManager.loadScripts(level);LuaScriptManager.callEvent("OnGameStart");
  level.setBlock(new BlockPos(969,-52,-1262),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
  check(LuaScriptManager.executeString("return ZombieroolAPI:getBlock(969,-52,-1262)").endsWith("minecraft:redstone_block"),"Boss trigger is present and visible to Lua");
  for(int i=0;i<160;i++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);LuaScriptManager.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,server));}
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observe);
  boolean warden=spawnedWardens.stream().anyMatch(w->w.isAlive()&&w.isAddedToWorld());
  check(warden,"Downloaded bossfight.lua creates a live Warden alongside main.lua");
  check(WaveManager.UNLOCKED_CHANNELS.contains(99),"Boss reaches active phase 2 after its Warden stabilizes");
  var active=WaveManager.class.getDeclaredField("activeMobs");active.setAccessible(true);
  check(active.get(null)==me.cryo.zombierool.WaveManager.activeMobs,"Legacy WaveManager shares the actual active mob set");
  check(me.cryo.zombierool.PerksManager.ALL_PERKS==me.cryo.zombierool.gameplay.PerksManager.ALL_PERKS,"Legacy PerksManager shares the actual perk registry");
 }
}
