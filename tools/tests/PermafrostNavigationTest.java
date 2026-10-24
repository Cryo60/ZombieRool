package local.hotfixtest;

import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModEntities;

/** Read navigation in the user's supplied world COPY; no edits to the original archive. */
@Mod("hotfixtests") public final class PermafrostNavigationTest {
    public PermafrostNavigationTest(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    @SuppressWarnings("unchecked") private void started(ServerStartedEvent event){var server=event.getServer();try{
        var level=server.overworld();level.getServer().getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
        boolean towers="1".equals(System.getenv("ZR_NAV_NEW_WORLD"));
        boolean secondZipline="1".equals(System.getenv("ZR_NAV_SECOND_ZIPLINE"));
        for(int x=towers?-5:66;x<=(towers?3:72);x++)for(int z=towers?-3:-83;z<=(towers?5:-73);z++)level.getChunk(x,z);
        var blockTick=net.minecraft.world.level.Level.class.getDeclaredMethod("tickBlockEntities");blockTick.setAccessible(true);blockTick.invoke(level);
        var player=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"MapNavigationProbe"));level.addNewPlayer(player);
        var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");players.setAccessible(true);((List<ServerPlayer>)players.get(server.getPlayerList())).add(player);
        player.setGameMode(GameType.SURVIVAL);player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);player.setHealth(100000);player.setPos(1110.5,57,-1218.5);
        if(towers)player.setPos(-31.88,-56,38.42);
        // The reported route includes powered iron doors. Reproduce its playable state.
        me.cryo.zombierool.config.GlobalSwitchState.setActivated(level,true);
        System.out.println("PERMAFROST POWER DOOR "+level.getBlockState(new net.minecraft.core.BlockPos(1104,45,-1254)));
        if(!towers){
            // These are the exact two changes made by the map's power=5 secret-room commands.
            // The supplied archive starts with that secret doorway shut, unlike the video.
            level.setBlock(new net.minecraft.core.BlockPos(1106,57,-1219),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),3);
            level.setBlock(new net.minecraft.core.BlockPos(1106,58,-1219),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        }
        var blocks=new java.util.LinkedHashMap<String,String>();
        for(int x=towers?-45:1090;x<=(towers?-20:1125);x++)for(int z=towers?8:-1290;z<=(towers?43:-1200);z++)for(int y=towers?-62:43;y<=(towers?-44:70);y++){
            var pos=new net.minecraft.core.BlockPos(x,y,z);var state=level.getBlockState(pos);
            blocks.put(x+","+y+","+z,state.toString());
        }
        java.nio.file.Files.writeString(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("path-probe-blocks.json"),new com.google.gson.Gson().toJson(blocks));
        boolean success=true;
        for(int type=0;type<(towers?(secondZipline?8:6):2);type++){
            if(towers)player.setPos(type==4?new net.minecraft.world.phys.Vec3(-26.5,-49,14.5):new net.minecraft.world.phys.Vec3(-31.88,-56,38.42));
            AbstractZombieRoolEntity mob=type%2==0&&(type<4||type>=6)?ZombieroolModEntities.ZOMBIE.get().create(level):ZombieroolModEntities.CRAWLER.get().create(level);
            var start=towers?new net.minecraft.world.phys.Vec3(-1.5,-60,42.5):new net.minecraft.world.phys.Vec3(1103.5,45,-1281.5);
            if(towers&&type>=2)start=new net.minecraft.world.phys.Vec3(-27.5,-49,14.5);
            if(towers&&type>=4)start=new net.minecraft.world.phys.Vec3(-8.5,-50,-12.5);
            if(towers&&type>=6){start=new net.minecraft.world.phys.Vec3(-4.5,-61,-.5);var zip=me.cryo.zombierool.block.system.TraversalSystem.loaded(level).stream().filter(a->a.kind()==me.cryo.zombierool.block.system.TraversalSystem.Kind.ZIPLINE&&a.getBlockPos().getZ()==12).findFirst().orElseThrow();player.setPos(zip.end());}
            mob.setPos(start);mob.setOnGround(true);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.getPersistentData().remove("zr_emerge_no_ai");mob.setNoAi(false);
            var path=mob.getNavigation().createPath(player,0);
            boolean reachable=path!=null&&path.canReach();
            var evaluator=new net.minecraft.world.level.pathfinder.WalkNodeEvaluator();
            evaluator.prepare(new net.minecraft.world.level.PathNavigationRegion(level,mob.blockPosition().offset(-110,-30,-110),mob.blockPosition().offset(110,40,110)),mob);
            if(!towers)for(var pos:List.of(new net.minecraft.core.BlockPos(1104,57,-1220),new net.minecraft.core.BlockPos(1104,58,-1221),new net.minecraft.core.BlockPos(1104,59,-1222),new net.minecraft.core.BlockPos(1107,57,-1222))){
                var node=new net.minecraft.world.level.pathfinder.Node(pos.getX(),pos.getY(),pos.getZ());node.type=evaluator.getBlockPathType(level,pos.getX(),pos.getY(),pos.getZ(),mob);node.costMalus=mob.getPathfindingMalus(node.type);
                var neighbors=new net.minecraft.world.level.pathfinder.Node[32];int count=evaluator.getNeighbors(neighbors,node);
                System.out.println("PERMAFROST NEIGHBORS "+pos+" type="+node.type+" above="+evaluator.getBlockPathType(level,pos.getX(),pos.getY()+1,pos.getZ(),mob)+" nodes="+Arrays.toString(Arrays.copyOf(neighbors,count)));
            }
            evaluator.done();
            if(towers){
                for(var anchor:me.cryo.zombierool.block.system.TraversalSystem.loaded(level))System.out.println("PERMAFROST LINK "+anchor.getBlockPos()+" "+anchor.kind()+" end="+anchor.end()+" ready="+anchor.ready()+" accepts="+anchor.accepts(mob));
                var climb=mob.getNavigation().createPath(new net.minecraft.core.BlockPos(-27,-49,14),0);System.out.println("PERMAFROST CLIMB type="+type+" reachable="+(climb!=null&&climb.canReach())+" end="+(climb==null?null:climb.getEndNode()));
                if(climb!=null)for(int n=Math.max(0,climb.getNodeCount()-10);n<climb.getNodeCount();n++)System.out.println("PERMAFROST CLIMB NODE "+climb.getNode(n).asBlockPos());
            }
            System.out.println("PERMAFROST INITIAL type="+type+" start="+mob.position()+" target="+player.position()+" path="+path+" reachable="+(path!=null&&path.canReach())+" end="+(path==null?null:path.getEndNode()));
            if(path!=null)System.out.println("PERMAFROST DISTANCE walked="+path.getEndNode().walkedDistance+" cost="+path.getEndNode().g);
            if(path!=null)for(int n=0;n<path.getNodeCount();n++)System.out.println("PERMAFROST NODE "+path.getNode(n).asBlockPos());
            if(path==null||!path.canReach()){
                mob.getNavigation().setMaxVisitedNodesMultiplier(towers?16:4);var broad=mob.getNavigation().createPath(player,0);
                System.out.println("PERMAFROST EXTRA BUDGET type="+type+" path="+broad+" reachable="+(broad!=null&&broad.canReach())+" end="+(broad==null?null:broad.getEndNode()));
                mob.getNavigation().setMaxVisitedNodesMultiplier(4);
                for(int y=43;y<=59;y++)System.out.println("PERMAFROST PLAYER COLUMN "+y+" "+level.getBlockState(new net.minecraft.core.BlockPos(1110,y,-1219)));
                mob.setPos(player.position());mob.setOnGround(true);var back=mob.getNavigation().createPath(net.minecraft.core.BlockPos.containing(start),0);System.out.println("PERMAFROST REVERSE type="+type+" path="+back+" reachable="+(back!=null&&back.canReach())+" end="+(back==null?null:back.getEndNode()));
                if(back!=null)for(int n=0;n<back.getNodeCount();n++)System.out.println("PERMAFROST REVERSE NODE "+back.getNode(n).asBlockPos());mob.setPos(start);mob.getNavigation().stop();
            }
            int lost=0;double best=mob.distanceToSqr(player);boolean usedZipline=false,usedWall=false,usedNewZipline=false;int traversalStarts=0;boolean travelling=false;
            for(int n=0;n<1500&&!mob.isRemoved();n++){
                server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);mob.tickCount++;mob.tick();
                blockTick.invoke(level);
                // The probe holds ServerStartedEvent, so loaded chunks have no player ticking
                // tickets yet. Simulate the nearby barricades' normal block-entity ticks too.
                for(var near:net.minecraft.core.BlockPos.betweenClosed(mob.blockPosition().offset(-2,-2,-2),mob.blockPosition().offset(2,2,2))){
                    var state=level.getBlockState(near);
                    if(state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlock&&level.getBlockEntity(near) instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlockEntity door)
                        me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlockEntity.tick(level,near.immutable(),state,door);
                }
                var tick=me.cryo.zombierool.gameplay.TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);tick.setAccessible(true);tick.invoke(null,level);
                var flight=me.cryo.zombierool.gameplay.TraversalMotion.flight(mob);if(flight!=null&&flight.style==1)usedZipline=true;
                if(flight!=null&&flight.style==2)usedWall=true;
                if(flight!=null&&!travelling)traversalStarts++;travelling=flight!=null;
                if(flight!=null&&flight.style==1&&Math.abs(flight.start.z-12.5)<.1)usedNewZipline=true;
                if(mob.getTarget()==null)lost++;best=Math.min(best,mob.distanceToSqr(player));
                if(n%100==0)System.out.println("PERMAFROST TICK type="+type+" n="+n+" pos="+mob.position()+" ground="+mob.onGround()+" target="+mob.getTarget()+" path="+mob.getNavigation().getPath());
                if(mob.distanceToSqr(player)<4&&Math.abs(mob.getY()-player.getY())<1.25&&!me.cryo.zombierool.gameplay.TraversalMotion.active(mob))break;
            }
            System.out.println("PERMAFROST RESULT type="+type+" pos="+mob.position()+" distance="+Math.sqrt(best)+" lostTargetTicks="+lost+" usedZipline="+usedZipline+" usedWall="+usedWall+" usedNewZipline="+usedNewZipline+" traversalStarts="+traversalStarts);
            success&=reachable&&!me.cryo.zombierool.gameplay.TraversalMotion.active(mob)&&mob.distanceToSqr(player)<4&&Math.abs(mob.getY()-player.getY())<1.25&&(!towers||(type<4?usedZipline==(type>=2):type<6?usedWall||secondZipline:usedNewZipline&&traversalStarts<10));
            mob.discard();
        }
        if(!success)throw new AssertionError("Both actual mobs must reach the player through the map's real route");
        System.out.println("PERMAFROST PROBE COMPLETE");
    }catch(Throwable error){error.printStackTrace();}finally{server.halt(false);}}
}
