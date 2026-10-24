package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.*;
import me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Full pursuit physics, rather than calling travel with a fixed input vector. */
@Mod("hotfixtests") public final class PursuitRegressionTest {
    public PursuitRegressionTest(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    private static void check(boolean ok,String message){System.out.println("PURSUIT CHECK: "+message+" = "+ok);if(!ok)throw new AssertionError(message);}
    private void started(ServerStartedEvent event){try{
        var server=event.getServer();var level=server.overworld();server.getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
        me.cryo.zombierool.config.WorldConfig.get(level).getAllowedMobs().addAll(Set.of("zombierool:zombie","zombierool:crawler"));
        for(int cx=19;cx<=23;cx++)for(int cz=-1;cz<=2;cz++)level.getChunk(cx,cz);
        var player=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"PursuitProbe"));level.addNewPlayer(player);player.setGameMode(GameType.SURVIVAL);player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);player.setHealth(100000);
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);((List<net.minecraft.server.level.ServerPlayer>)field.get(server.getPlayerList())).add(player);
        for(int variant=0;variant<4;variant++){
            boolean falling=variant>=2;boolean crawler=variant%2==1;
            for(int x=320;x<=349;x++)for(int z=0;z<=12;z++)for(int y=78;y<=86;y++){
                var state=y==78?ZombieroolModBlocks.PATH.get().defaultBlockState():y==79?Blocks.STONE.defaultBlockState():y<=82?(z==0||z==12?Blocks.STONE.defaultBlockState():falling&&y<82?Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,8):Blocks.WATER.defaultBlockState()):Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(x,y,z),state,3);
            }
            player.setPos(343.5,80,6.5);var mob=crawler?ZombieroolModEntities.CRAWLER.get().create(level):ZombieroolModEntities.ZOMBIE.get().create(level);
            mob.setPos(323.5,80,6.5);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.setNoAi(false);mob.setOnGround(true);mob.baseTick();
            var path=mob.getNavigation().createPath(player,0);System.out.println("PURSUIT POOL initial="+path+" reachable="+(path!=null&&path.canReach())+" end="+(path==null?null:path.getEndNode()));
            int lost=0;double sideways=0,highest=80;float turn=0,lastYaw=mob.getYRot();
            for(int tick=0;tick<220;tick++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);mob.tickCount++;mob.tick();if(mob.getTarget()!=player)lost++;sideways=Math.max(sideways,Math.abs(mob.getZ()-6.5));highest=Math.max(highest,mob.getY());turn+=Math.abs(net.minecraft.util.Mth.wrapDegrees(mob.getYRot()-lastYaw));lastYaw=mob.getYRot();if(mob.getX()>341)break;}
            System.out.println("PURSUIT POOL variant="+variant+" pos="+mob.position()+" lost="+lost+" sideways="+sideways+" rotation="+turn);
            check(mob.getX()>340&&sideways<2&&lost<5&&turn<540,"Pool chase stays straight with "+(falling?"top-only sources":"source water")+" and "+(crawler?"crawler":"zombie"));
            check(highest<80.15&&Math.abs(mob.getY()-80)<.15,"Pool pursuit remains on the bed rather than swimming upward");mob.discard();
        }
        for(boolean crawler:new boolean[]{false,true}){
            for(int x=320;x<=349;x++)for(int z=0;z<=12;z++)for(int y=78;y<=86;y++){
                int bed=Math.min(82,79+Math.max(0,x-333));
                var state=y==78?ZombieroolModBlocks.PATH.get().defaultBlockState():y<=bed?Blocks.STONE.defaultBlockState():y<=82?(y==82?Blocks.WATER.defaultBlockState():Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,8)):Blocks.AIR.defaultBlockState();
                level.setBlock(new BlockPos(x,y,z),state,3);
            }
            player.setPos(343.5,83,6.5);var swimmer=crawler?ZombieroolModEntities.CRAWLER.get().create(level):ZombieroolModEntities.ZOMBIE.get().create(level);
            swimmer.setPos(323.5,80,6.5);level.addFreshEntity(swimmer);swimmer.setEmergenceTicks(0);swimmer.setNoAi(false);swimmer.setOnGround(true);swimmer.baseTick();
            var bankPath=swimmer.getNavigation().createPath(player,0);System.out.println("PURSUIT BANK initial="+bankPath+" canReach="+(bankPath!=null&&bankPath.canReach()));
            for(int tick=0;tick<350;tick++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);swimmer.tickCount++;swimmer.tick();if(swimmer.getX()>341)break;}
            System.out.println("PURSUIT BANK crawler="+crawler+" position="+swimmer.position());
            check(swimmer.getX()>340&&swimmer.getY()>82.8,"Bottom-walking enemy climbs physical steps onto the dry bank: "+(crawler?"crawler":"zombie"));swimmer.discard();
        }
        // The exit cannot be reached by ascending freely along a sheer underwater wall.
        for(int x=320;x<=349;x++)for(int z=0;z<=12;z++)for(int y=78;y<=87;y++){
            var state=y==78?ZombieroolModBlocks.PATH.get().defaultBlockState():y==79||x>=337&&y<=84?Blocks.STONE.defaultBlockState():y<=84?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState();
            level.setBlock(new BlockPos(x,y,z),state,3);
        }
        player.setPos(343.5,85,6.5);var sunken=ZombieroolModEntities.ZOMBIE.get().create(level);sunken.setPos(323.5,82,6.5);level.addFreshEntity(sunken);sunken.setEmergenceTicks(0);sunken.setNoAi(false);sunken.baseTick();
        var sheer=sunken.getNavigation().createPath(player,0);
        check(sheer==null||!sheer.canReach(),"A sheer five-block bank is not a swimming route");
        for(int tick=0;tick<90;tick++){sunken.tickCount++;sunken.tick();}
        check(sunken.getY()<80.15,"An unsupported zombie sinks to the bottom");sunken.discard();
        for(int x=355;x<=367;x++)for(int z=20;z<=24;z++){level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);for(int y=80;y<=88;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        player.setPos(368.15,80,22.5);player.setShiftKeyDown(true);var mob=ZombieroolModEntities.ZOMBIE.get().create(level);mob.setPos(356.5,80,22.5);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.setNoAi(false);mob.setOnGround(true);
        for(int tick=0;tick<140;tick++){mob.tickCount++;mob.tick();if(mob.getX()>366)break;}
        check(mob.getTarget()==player&&mob.getX()>365,"Sneaking over the platform edge keeps a reachable pursuit target");mob.discard();player.setShiftKeyDown(false);
        var entry=new BlockPos(410,80,22);var exit=new BlockPos(366,80,22);var zip=me.cryo.zombierool.block.system.TraversalSystem.ZIPLINE.get();
        level.setBlock(entry.below(),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(entry,zip.defaultBlockState(),3);level.setBlock(exit,zip.defaultBlockState().setValue(me.cryo.zombierool.block.system.TraversalSystem.TraversalBlock.PHASE,2),3);
        var root=(me.cryo.zombierool.block.system.TraversalSystem.Anchor)level.getBlockEntity(entry);root.exit=exit;root.duration=200;((me.cryo.zombierool.block.system.TraversalSystem.Anchor)level.getBlockEntity(exit)).root=entry;
        player.setPos(root.start());me.cryo.zombierool.gameplay.TraversalMotion.start(player,new me.cryo.zombierool.block.system.TraversalSystem.Route(root,false));
        mob=ZombieroolModEntities.ZOMBIE.get().create(level);mob.setPos(356.5,80,22.5);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.setNoAi(false);mob.setOnGround(true);
        var travelTick=me.cryo.zombierool.gameplay.TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);travelTick.setAccessible(true);int lost=0;
        for(int tick=0;tick<140;tick++){mob.tickCount++;mob.tick();travelTick.invoke(null,level);if(mob.getTarget()!=player)lost++;if(mob.getX()>365)break;}
        check(mob.getTarget()==player&&mob.getX()>365&&lost<5,"While the player travels a zipline, enemies pursue its reachable exit");me.cryo.zombierool.gameplay.TraversalMotion.cancel(player);mob.discard();
        var chest=me.cryo.zombierool.block.system.MapAuthorSystem.SOUL_CHEST.get();var item=new net.minecraft.world.item.ItemStack(chest);
        for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL){player.setYRot(facing.toYRot());var context=new net.minecraft.world.item.context.BlockPlaceContext(level,player,net.minecraft.world.InteractionHand.MAIN_HAND,item,new net.minecraft.world.phys.BlockHitResult(new Vec3(355.5,80,22.5),net.minecraft.core.Direction.UP,new BlockPos(355,79,22),false));
            var state=chest.getStateForPlacement(context);check(state.getValue(me.cryo.zombierool.block.system.SoulChestSystem.ChestBlock.FACING)==facing.getOpposite(),"Soul Chest faces its builder from "+facing);
        }
        var allowed=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new net.minecraft.resources.ResourceLocation("zombierool","allowed_blocks"));
        for(var block:List.of(me.cryo.zombierool.block.system.TraversalSystem.NODE.get(),me.cryo.zombierool.block.system.TraversalSystem.JUMP.get(),zip,me.cryo.zombierool.block.system.TraversalSystem.WALL.get(),me.cryo.zombierool.block.system.MapAuthorSystem.GATE.get()))check(block.defaultBlockState().is(allowed),"Navigation marker is allowed by default: "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block));
        System.out.println("PURSUIT REGRESSION PASS");
    }catch(Throwable error){error.printStackTrace();}finally{event.getServer().halt(false);}}
}
