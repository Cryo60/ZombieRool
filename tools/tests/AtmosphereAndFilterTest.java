package local.skingalleryclient;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import net.minecraft.server.level.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
public final class AtmosphereAndFilterTest {
 private static int checks;
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;System.out.println("ATMOSPHERE FILTER CHECK: "+text);}
 public static int run(ServerPlayer p)throws Exception {
  var l=p.serverLevel();var cfg=me.cryo.zombierool.config.WorldConfig.get(l);var old=cfg.save(new CompoundTag());var pos=new BlockPos(800,65,0);
  try{
   var method=me.cryo.zombierool.event.ServerEventHandler.class.getDeclaredMethod("shouldDespawn",net.minecraft.world.entity.Entity.class);method.setAccessible(true);
   var cow=net.minecraft.world.entity.EntityType.COW.create(l);check((Boolean)method.invoke(null,cow),"Vanilla cows remain blocked independently of the monster allow-list");
   var villager=net.minecraft.world.entity.EntityType.VILLAGER.create(l);check((Boolean)method.invoke(null,villager),"Vanilla villagers remain blocked");
   var vanillaZombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);check((Boolean)method.invoke(null,vanillaZombie),"Vanilla zombies cannot replace ZombieRool enemies");
   var stand=net.minecraft.world.entity.EntityType.ARMOR_STAND.create(l);check(!(Boolean)method.invoke(null,stand),"Map decorations remain allowed");
   var join=new net.minecraftforge.event.entity.EntityJoinLevelEvent(cow,l);me.cryo.zombierool.event.ServerEventHandler.onEntityJoinLevel(join);check(join.isCanceled()&&cow.isRemoved(),"Loaded vanilla wildlife is removed on joining the world");
   var zombie=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);var allowed=new java.util.HashSet<>(cfg.getAllowedMobs());allowed.remove("zombierool:zombie");cfg.setAllowedMobs(allowed);check((Boolean)method.invoke(null,zombie),"ZombieRool monsters still obey their own allow-list");
   l.setBlock(pos,LunarStation.ZONE.get().defaultBlockState(),2);var zone=(AtmosphereZoneBlock.Zone)l.getBlockEntity(pos);var n=zone.config();String generated=n.getString("id");check(generated.startsWith("zone_")&&!generated.equals(AtmosphereZoneBlock.Zone.defaults().getString("id")),"New atmosphere blocks receive distinct random IDs");zone.onLoad();check(generated.equals(zone.config().getString("id")),"Generated ID remains stable when the chunk reloads");n.putString("id","pressure_lab");n.putDouble("gravity",1);zone.configure(n);
   var env=LunarEnvironment.get(l);check(env.blockPosition("pressure_lab").equals(pos),"Named block atmosphere registers a Lua ID");
   var api=new me.cryo.zombierool.scripting.ZombieroolAPI(l);var patch=api.getAtmosphereZone("pressure_lab");patch.set("vacuum",patch.FALSE);check(api.configureAtmosphereZone("pressure_lab",patch),"Lua configures an existing named atmosphere block");check(!zone.config().getBoolean("vacuum")&&zone.config().getInt("radiusX")==8&&zone.config().getDouble("gravity")==1,"Pressurization preserves zone dimensions and gravity");
   check(!env.at(800.5,66,.5,new LunarEnvironment.Conditions(1,true)).vacuum(),"Updated block really provides breathable air");
   var saved=zone.saveWithFullMetadata();zone.load(saved);zone.onLoad();check(!zone.config().getBoolean("vacuum")&&env.blockPosition("pressure_lab").equals(pos),"Zone name and breathable state survive block reload");
   check(!zone.config().getString("id").equals(generated),"Manual zone ID remains supported");
   var loaded=LunarEnvironment.load(env.save(new CompoundTag()));check(loaded.blockPosition("pressure_lab").equals(pos),"Named zone lookup survives saved-data reload");
   check(!api.configureAtmosphereZone("missing_zone",patch),"Unknown atmosphere IDs fail without creating accidental volumes");
   var legacy=zone.saveWithFullMetadata();legacy.getCompound("Atmosphere").remove("id");zone.load(legacy);String migrated=zone.config().getString("id");check(migrated.startsWith("zone_")&&env.blockPosition(migrated).equals(pos),"Legacy blocks without an ID receive a registered random ID");zone.onLoad();check(zone.config().getString("id").equals(migrated),"Migrated IDs stay stable across subsequent loads");
  }finally{cfg.loadEditable(old);l.setBlock(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);}
  System.out.println("ATMOSPHERE FILTER PASS: "+checks+" checks");return checks;
 }
}
