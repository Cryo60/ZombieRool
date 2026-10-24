package local.skingalleryclient;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.server.level.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
public final class ActualWaterMapAiTest {
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var old=p.position();long originalTime=l.getGameTime();boolean previous=EnemyWaterMovement.enabled(l);var original=l.getDifficulty();
  try{
   for(int x=144;x<208;x++)for(int z=-32;z<32;z++)for(int y=48;y<96;y++)l.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);
   var arr=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of("D:/Dev/ZombieRool/build/zombie-presets/actual-water-map-blocks.json"))).getAsJsonArray();
   for(var entry:arr){var row=entry.getAsJsonArray();var xyz=row.get(0).getAsJsonArray();var state=row.get(1).getAsJsonObject();var n=new net.minecraft.nbt.CompoundTag();n.putString("Name",state.get("Name").getAsString());var props=new net.minecraft.nbt.CompoundTag();if(state.has("Properties"))for(var prop:state.getAsJsonObject("Properties").entrySet())props.putString(prop.getKey(),prop.getValue().getAsString());n.put("Properties",props);l.setBlock(new BlockPos(xyz.get(0).getAsInt(),xyz.get(1).getAsInt(),xyz.get(2).getAsInt()),net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),n),2);}
   EnemyWaterMovement.configure(l,true);p.setGameMode(GameType.ADVENTURE);p.teleportTo(l,165.5,70,.5,0,0);p.baseTick();p.setOnGround(true);p.setHealth(p.getMaxHealth());
   for(int scenario=0;scenario<3;scenario++){boolean room=scenario==1,roof=scenario==2;p.teleportTo(l,room?177:165.5,room?65:roof?74:70,roof?-9.5:.5,0,0);p.baseTick();
   var m=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);m.setPos(room?165.5:179.5,65,room?.5:4.5);l.addFreshEntity(m);m.setOnGround(true);
   try{for(int i=0;i<650;i++){l.getServer().getWorldData().overworldData().setGameTime(l.getGameTime()+1);m.tick();for(int z:new int[]{4,5}){var dp=new BlockPos(174,65,z);if(l.getBlockEntity(dp) instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlockEntity be)me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlockEntity.tick(l,dp,l.getBlockState(dp),be);}if(i%80==0)System.out.println("ACTUAL WATER MAP AI t="+i+" next="+(m.getNavigation().getPath()==null||m.getNavigation().getPath().isDone()?"done":m.getNavigation().getPath().getNextNode().asBlockPos())+" wet="+m.isInWater()+" ground="+m.onGround()+" pos="+m.position()+" target="+(m.getTarget()==p)+" noai="+m.isNoAi()+" speed="+m.getSpeed()+" path="+m.getNavigation().getPath());}
    var path=m.getNavigation().createPath(p,0);System.out.println("ACTUAL WATER MAP REACHES="+RouteNavigation.reachesPlayer(path,p)+" end="+(path==null?null:path.getEndNode()));
    if(roof&&m.distanceToSqr(p)>9)throw new AssertionError("Swimmer cannot leave aquarium through roof hole to reach player: "+m.position());
    if(m.position().distanceToSqr(new net.minecraft.world.phys.Vec3(room?165.5:179.5,65,room?.5:4.5))<4)throw new AssertionError("Real water map snapshot zombie never leaves its source");
   }finally{m.discard();}}
  }finally{l.getServer().getWorldData().overworldData().setGameTime(originalTime);EnemyWaterMovement.configure(l,previous);p.setGameMode(GameType.CREATIVE);p.teleportTo(l,old.x,old.y,old.z,0,0);}
  System.out.println("ACTUAL WATER MAP AI PASS");return 1;
 }
}
