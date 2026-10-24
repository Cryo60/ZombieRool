package local.skingalleryclient;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Path;

public final class SwimmingZombieTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("SWIMMING CHECK: "+label);}
 private static ZombieEntity zombie(ServerLevel l,double x,double y,double z){var m=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);m.setPos(x,y,z);l.addFreshEntity(m);m.setEmergenceTicks(0);m.getPersistentData().remove("zr_emerge_no_ai");m.setNoAi(false);m.setOnGround(true);m.baseTick();return m;}
 private static void frame(ZombieEntity m){m.baseTick();m.getNavigation().tick();m.getMoveControl().tick();m.travel(new Vec3(m.xxa,m.yya,m.zza));var feet=m.blockPosition();for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){var at=feet.offset(dx,0,dz);var state=m.level().getBlockState(at);if(state.getBlock() instanceof BasePressurePlateBlock&&m.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(at)))state.entityInside(m.level(),at,m);}}
 private static void print(Path path){System.out.println("SWIM PATH: "+(path==null?"null":path.canReach()+" "+java.util.stream.IntStream.range(0,path.getNodeCount()).mapToObj(i->path.getNode(i).asBlockPos().toShortString()).toList()));}
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var oldPos=p.position();boolean previous=EnemyWaterMovement.enabled(l);ZombieEntity m=null;
  try{
   EnemyWaterMovement.configure(l,false);check(!EnemyWaterMovement.enabled(l),"Existing maps default to sinking mode");
   for(int x=500;x<=528;x++)for(int z=-3;z<=3;z++){
    l.setBlock(new BlockPos(x,63,z),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),2);
    for(int y=64;y<=77;y++){boolean solid=y==64||z==-3||z==3||x==500||x==528||y==77||(x>=520&&y<70);l.setBlock(new BlockPos(x,y,z),solid?Blocks.STONE.defaultBlockState():(x<=519?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState()),2);}
   }
   var ef=ServerLevel.class.getDeclaredField("entityManager");ef.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)ef.get(l);for(int cx=31;cx<=33;cx++)for(int cz=-1;cz<=0;cz++)manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cx,cz),net.minecraft.server.level.FullChunkStatus.ENTITY_TICKING);
   p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(l,510.5,72,.5,0,0);m=zombie(l,503.5,68,.5);m.setDeltaMovement(Vec3.ZERO);for(int i=0;i<10;i++){m.baseTick();m.travel(Vec3.ZERO);}check(m.getY()<67.5,"Default water movement still sinks");m.discard();m=null;
   new me.cryo.zombierool.scripting.ZombieroolAPI(l).setZombieSwimming(true);check(EnemyWaterMovement.enabled(l),"Lua enables swimming on this map");
   var config=me.cryo.zombierool.config.WorldConfig.get(l).getAddonConfig();check(config.getCompound(EnemyWaterMovement.ID).getBoolean("swimmingZombies"),"Map config stores swimming choice");
   m=zombie(l,503.5,65,.5);var path=m.getNavigation().createPath(p,0);print(path);check(RouteNavigation.reachesPlayer(path,p),"Swimmer plans toward player's actual height instead of pool floor");check(path.getEndNode().y>=71,"Swimming path has upward nodes");m.getNavigation().moveTo(path,1);for(int i=0;i<250&&m.distanceToSqr(p)>1;i++)frame(m);System.out.println("SWIM POSITION: "+m.position());check(m.getY()>70&&m.distanceToSqr(p)<4,"Real movement swims upward and reaches elevated player");
   p.teleportTo(l,505.5,65,.5,0,0);m.getNavigation().stop();path=m.getNavigation().createPath(p,0);check(RouteNavigation.reachesPlayer(path,p),"Swimming graph also finds a downward route");m.getNavigation().moveTo(path,1);for(int i=0;i<250&&m.distanceToSqr(p)>1;i++)frame(m);check(m.getY()<66.5&&m.distanceToSqr(p)<4,"Real swimming descends to lower player");
   for(int z=-2;z<=2;z++)for(int y=65;y<77;y++)l.setBlock(new BlockPos(508,y,z),Blocks.STONE.defaultBlockState(),2);
   p.teleportTo(l,511.5,65,.5,0,0);m.getNavigation().stop();path=m.getNavigation().createPath(p,0);check(!RouteNavigation.reachesPlayer(path,p),"Swimming route cannot cross a solid underwater wall");
   for(int z=-2;z<=2;z++)for(int y=65;y<77;y++)l.setBlock(new BlockPos(508,y,z),Blocks.WATER.defaultBlockState(),2);
   p.teleportTo(l,524.5,70,.5,0,0);m.setPos(503.5,65,.5);m.setDeltaMovement(Vec3.ZERO);m.baseTick();m.getNavigation().stop();path=m.getNavigation().createPath(p,0);print(path);check(RouteNavigation.reachesPlayer(path,p),"Swimmer finds route through open water-to-air opening");m.getNavigation().moveTo(path,1);for(int i=0;i<450&&m.distanceToSqr(p)>1;i++)frame(m);System.out.println("DRY POSITION: "+m.position());check(m.getX()>520&&m.getY()>69.5,"Swimmer physically leaves water for raised dry floor");m.discard();m=null;
   // Wall with a closed iron door, approached over a pressure plate on either side.
   for(int z=-2;z<=2;z++)for(int y=70;y<77;y++)l.setBlock(new BlockPos(520,y,z),Blocks.STONE.defaultBlockState(),2);
   var doorPos=new BlockPos(520,70,0);var door=Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.WEST);l.setBlock(doorPos,door,2);l.setBlock(doorPos.above(),door.setValue(DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
   // A dry two-block landing in the water gives the plate physical support.
   l.setBlock(new BlockPos(519,69,0),Blocks.STONE.defaultBlockState(),2);for(int y=70;y<=72;y++)l.setBlock(new BlockPos(519,y,0),Blocks.AIR.defaultBlockState(),2);
   m=zombie(l,503.5,65,.5);path=m.getNavigation().createPath(p,0);check(!RouteNavigation.reachesPlayer(path,p),"Closed iron door without a plate remains impassable");
   l.setBlock(new BlockPos(519,70,0),Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),2);l.setBlock(new BlockPos(521,70,0),Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE.defaultBlockState(),2);m.setPos(503.5,65,1.5);m.setDeltaMovement(Vec3.ZERO);m.baseTick();m.getNavigation().stop();path=m.getNavigation().createPath(p,0);print(path);check(RouteNavigation.reachesPlayer(path,p),"Closed iron door with accessible plates is planned before opening");m.getNavigation().moveTo(path,1);boolean opened=false;for(int i=0;i<500&&m.distanceToSqr(p)>1;i++){frame(m);opened|=l.getBlockState(doorPos).getValue(DoorBlock.OPEN);}System.out.println("DOOR POSITION: "+m.position());check(opened,"Zombie physically presses plate and opens iron door");check(m.getX()>521,"Zombie crosses pressure-operated iron door into dry room");
   check(!PressurePlateDoors.hasPlate(l,new BlockPos(518,70,0)),"Door probe tolerates non-door cells near an overhang");
   l.setBlock(doorPos,l.getBlockState(doorPos).setValue(DoorBlock.OPEN,false),2);l.setBlock(doorPos.above(),l.getBlockState(doorPos.above()).setValue(DoorBlock.OPEN,false),2);
   check(!PressurePlateDoors.canApproach(l,new BlockPos(519,70,1),doorPos)&&PressurePlateDoors.canApproach(l,new BlockPos(519,70,0),doorPos),"Closed-door planning requires crossing the actual plate cell");
   EnemyWaterMovement.configure(l,false);check(!EnemyWaterMovement.swims(m),"Disabling map option returns existing zombie to sinking behavior");
  }finally{if(m!=null)m.discard();EnemyWaterMovement.configure(l,previous);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);p.teleportTo(l,oldPos.x,oldPos.y,oldPos.z,0,0);}
  System.out.println("SWIMMING ZOMBIE PASS: "+checks+" checks");return checks;
 }
}
