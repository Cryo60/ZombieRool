package local.hotfixtest;

import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Behavioral server checks against registered 26.7 blocks and actual navigation/movement. */
public final class Feature267Test {
    private static int assertions;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);assertions++;System.out.println("26.7 CHECK: "+message);}
    private static ZombieEntity zombie(ServerLevel level,Vec3 position){var mob=ZombieroolModEntities.ZOMBIE.get().create(level);mob.setPos(position);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.getPersistentData().remove("zr_emerge_no_ai");mob.setNoAi(false);mob.setOnGround(true);return mob;}
    private static TraversalSystem.Anchor link(ServerLevel level,BlockPos entry,BlockPos exit,net.minecraft.world.level.block.Block block){
        level.setBlock(entry.below(),Blocks.STONE.defaultBlockState(),3);level.setBlock(exit.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(entry,block.defaultBlockState(),3);level.setBlock(exit,block.defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
        var a=(TraversalSystem.Anchor)level.getBlockEntity(entry);var b=(TraversalSystem.Anchor)level.getBlockEntity(exit);a.root=entry;a.exit=exit;b.root=entry;a.duration=12;a.clearance=2;a.sync();b.sync();return a;
    }
    public static void run(ServerLevel level,ServerPlayer a,ServerPlayer b)throws Exception{
        Oct14DebugGameplayTest.run(level,a);
        Oct14RedistributionTest.run(level,a);
        Oct14FollowupGameplayTest.run(level,a);
        Oct14GameplayTest.run(level,a);
        Oct13RegressionTest.run(level,a);
        MiniBossMapConfigTest.run(level,a);
        BossBehaviorTest.run(level,a);
        check(me.cryo.zombierool.integration.TacZPauseClock.compensateTimestamp(1000,5000,10000)==6000,"TacZ preserves an action started before the pause");
        check(me.cryo.zombierool.integration.TacZPauseClock.compensateTimestamp(10000,5000,10000)==10000,"TacZ does not lock a fresh action for the whole previous pause");
        check(me.cryo.zombierool.integration.TacZPauseClock.compensateTimestamp(9950,5000,10000)==10000,"TacZ compensation is limited to the action's overlap with the pause");
        check(me.cryo.zombierool.integration.TacZPauseClock.compensateTimestamp(10100,5000,10000)==10100,"TacZ keeps a scheduled draw from being pushed further into the future");
        check(me.cryo.zombierool.integration.TacZPauseClock.compensateTimestamp(-1,5000,10000)==-1,"TacZ preserves the inactive action sentinel");
        for(int x=70;x<112;x++)for(int z=-3;z<=3;z++){level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);for(int y=79;y<97;y++)level.setBlock(new BlockPos(x,y,z),y==79?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
        var tabs=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB);
        check(tabs.keySet().stream().filter(id->id.getNamespace().equals("zombierool")).count()==1,"Exactly one registered ZombieRool creative tab");
        var tab=ZombieroolModTabs.ZB_RCT.get();
        var entries=new net.minecraftforge.common.util.MutableHashedLinkedMap<ItemStack,CreativeModeTab.TabVisibility>();
        var params=new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(),true,level.registryAccess());
        var contents=new net.minecraftforge.event.BuildCreativeModeTabContentsEvent(tab,net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,new ResourceLocation("zombierool","zb_rct")),params,entries);
        me.cryo.zombierool.core.registry.ZRRegistry.buildContents(contents);
        CreativeCatalog.fill(contents);
        var items=new ArrayList<ItemStack>();for(var entry:entries)items.add(entry.getKey());
        check(items.stream().noneMatch(s->ForgeRegistries.ITEMS.getKey(s.getItem()).getPath().startsWith("maptex_")),"Creative catalogue hides technical map texture slots");
        check(items.stream().anyMatch(s->ForgeRegistries.ITEMS.getKey(s.getItem()).getPath().equals("texture_kit")),"Texture Kit remains available in the creative catalogue");
        check(items.stream().anyMatch(s->s.is(TraversalSystem.JUMP_ITEM.get())),"Unified tab includes navigation");
        check(items.stream().anyMatch(s->s.is(BlindBuySystem.ITEM.get())),"Unified tab includes the old arsenal machines");
        check(items.stream().anyMatch(s->s.is(me.cryo.zombierool.core.registry.ZRBlocks.SANDBAGS.get().asItem())),"Unified tab includes decoration");
        check(items.stream().anyMatch(s->s.getItem() instanceof me.cryo.zombierool.api.ICustomWeapon),"Unified tab includes weapons");
        for(String channel:List.of("gamma","delta","epsilon","zeta","eta","theta","iota","kappa","lambda","sigma"))for(String role:List.of("activator","receptor"))check(ForgeRegistries.BLOCKS.containsKey(new ResourceLocation("zombierool",channel+"_"+role)),"Registered "+channel+" "+role);
        var jump=link(level,new BlockPos(73,80,0),new BlockPos(79,80,0),TraversalSystem.JUMP.get());
        for(int x=74;x<79;x++)level.setBlock(new BlockPos(x,79,0),Blocks.AIR.defaultBlockState(),3);
        var mob=zombie(level,new Vec3(72.5,80,.5));
        for(int x=70;x<82;x++)for(int z=-3;z<=3;z++)if(z!=0)for(int y=80;y<83;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
        check(jump.ready(),"Two-point Jump Link is ready across a gap");
        TraversalSystem.unload(level); // Simulate packed/restored markers before their Forge onLoad callback.
        var route=mob.getNavigation().createPath(new BlockPos(80,80,0),0);
        check(route!=null&&route.canReach(),"Actual pathfinding uses Jump Link across a gap");
        check(TraversalSystem.edge(mob,new Node(79,80,0))==null,"Unidirectional Jump Link rejects its reverse edge");
        jump.bidirectional=true;
        check(TraversalSystem.edge(mob,new Node(79,80,0)).destination().equals(jump.getBlockPos()),"Bidirectional Jump Link exposes its reverse edge");
        mob.setPos(79.5,80,.5);mob.getNavigation().stop();route=mob.getNavigation().createPath(new BlockPos(72,80,0),0);
        check(route!=null&&route.canReach(),"Actual pathfinding can use Jump Link in reverse");
        TraversalMotion.start(mob,new TraversalSystem.Route(jump,true));
        var motionTick=TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);motionTick.setAccessible(true);
        for(int i=0;i<12;i++){mob.setDeltaMovement(9,9,9);mob.push(3,4,5);motionTick.invoke(null,level);}
        check(mob.position().distanceToSqr(jump.start())<.0001&&!TraversalMotion.active(mob)&&!mob.isNoAi(),"Reverse jump reaches Entry and restores movement despite pushes");
        // The original three-point route must also work backwards.
        var vault=link(level,new BlockPos(82,80,-2),new BlockPos(82,80,2),TraversalSystem.NODE.get());
        var summit=new BlockPos(82,84,0);level.setBlock(summit,TraversalSystem.NODE.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,1),3);((TraversalSystem.Anchor)level.getBlockEntity(summit)).root=vault.getBlockPos();vault.apex=summit;vault.bidirectional=true;
        check(vault.ready()&&TraversalSystem.edge(mob,new Node(82,80,2)).reverse(),"Original three-point Traversal Link supports Bidirectional");
        TraversalMotion.start(mob,new TraversalSystem.Route(vault,true));for(int i=0;i<12;i++)motionTick.invoke(null,level);check(mob.position().distanceToSqr(vault.start())<.0001,"Reverse Traversal Link lands at its original Entry");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());var wire=new TransportPackets.Jump(mob.getId(),mob.getUUID(),vault.start(),vault.peak(),vault.end(),24,3,2);TransportPackets.Jump.encode(wire,buffer);check(TransportPackets.Jump.decode(buffer).equals(wire),"Movement packet preserves wall style, progress and route");buffer.release();
        var zip=link(level,new BlockPos(85,80,0),new BlockPos(91,80,0),TraversalSystem.ZIPLINE.get());zip.cost=100;zip.requiresPower=false;
        int[] corrections={0};
        a.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),a){
            @Override public void teleport(double x,double y,double z,float yaw,float pitch){corrections[0]++;a.setPos(x,y,z);a.setYRot(yaw);a.setXRot(pitch);}
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet){}
        };
        a.setGameMode(GameType.SURVIVAL);a.noPhysics=false;a.setNoGravity(false);a.setPos(zip.start());PointManager.setScore(a,50);
        ZiplineInteraction.use(a,zip.getBlockPos());check(!TraversalMotion.active(a)&&PointManager.getScore(a)==50,"Zipline rejects insufficient points without charging");
        PointManager.setScore(a,300);ZiplineInteraction.use(a,zip.getBlockPos());check(TraversalMotion.active(a)&&PointManager.getScore(a)==200,"Player Zipline begins and charges once");
        Vec3 beforeInput=a.position();a.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(400,200,400,37,12,false));
        check(a.position().equals(beforeInput)&&a.getYRot()==37&&a.getXRot()==12,"Zipline ignores competing movement packets but allows the player to look around");
        var floating=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("clientIsFloating");floating.setAccessible(true);floating.setBoolean(a.connection,true);
        var floatingTicks=net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");floatingTicks.setAccessible(true);floatingTicks.setInt(a.connection,80);a.connection.tick();
        check(!floating.getBoolean(a.connection)&&floatingTicks.getInt(a.connection)==0,"An authorized Zipline clears stale anti-flight counters instead of kicking its rider");
        for(int i=0;i<11;i++)motionTick.invoke(null,level);
        check(corrections[0]==0&&a.getYRot()==37&&a.getXRot()==12,"Zipline sends no repeated teleport corrections and preserves the camera direction");
        Vec3 frameA=TraversalMotion.renderPosition(a,.25f),frameB=TraversalMotion.renderPosition(a,.75f);
        check(Math.abs(frameA.distanceTo(frameB)-zip.start().distanceTo(zip.end())/24)<.00001,"Zipline render sampling progresses smoothly between ticks");
        motionTick.invoke(null,level);check(corrections[0]==1,"Zipline performs one final position synchronization on arrival");
        check(a.position().distanceToSqr(zip.end())<.0001&&!a.isNoGravity()&&!a.noPhysics,"Player Zipline reaches Exit and restores physics");
        mob.setPos(zip.start());int before=PointManager.getScore(a);TraversalMotion.start(mob,new TraversalSystem.Route(zip,false));for(int i=0;i<12;i++)motionTick.invoke(null,level);
        check(PointManager.getScore(a)==before&&mob.position().distanceToSqr(zip.end())<.0001,"Zombie rides Zipline without charging players");
        a.setPos(zip.start());PointManager.setScore(a,300);ZiplineInteraction.use(a,zip.getBlockPos());TraversalMotion.reset(level);check(!TraversalMotion.active(a)&&!a.noPhysics&&!a.isNoGravity(),"Match reset cancels a player Zipline and restores physics");
        var shared=link(level,new BlockPos(115,80,8),new BlockPos(140,80,8),TraversalSystem.ZIPLINE.get());var sharedExit=(TraversalSystem.Anchor)level.getBlockEntity(shared.exit);
        var settings=sharedExit.config();settings.putInt("duration",37);settings.putInt("cost",42);settings.putBoolean("bidirectional",true);settings.putBoolean("requiresPower",true);sharedExit.configure(settings);
        check(shared.duration==37&&shared.cost==42&&shared.bidirectional&&shared.requiresPower&&sharedExit.duration==37&&sharedExit.cost==42,"Editing Zipline Exit applies every setting to both ends even 25 blocks apart");
        settings.putInt("duration",41);settings.putInt("cost",17);shared.configure(settings);check(sharedExit.config().equals(shared.config())&&sharedExit.saveWithoutMetadata().getInt("duration")==41,"Editing Entry is reflected in Exit configuration and persistence");
        var summitAnchor=(TraversalSystem.Anchor)level.getBlockEntity(summit);var summitSettings=summitAnchor.config();summitSettings.putInt("duration",29);summitAnchor.configure(summitSettings);check(vault.duration==29&&summitAnchor.duration==29&&((TraversalSystem.Anchor)level.getBlockEntity(vault.exit)).duration==29,"Traversal Entry, Summit and Exit share one authoritative configuration");
        var wallPos=new BlockPos(97,90,0);for(int y=80;y<=92;y++)level.setBlock(new BlockPos(98,y,0),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(wallPos,TraversalSystem.WALL.get().defaultBlockState(),3);var wall=(TraversalSystem.Anchor)level.getBlockEntity(wallPos);wall.wallFace=Direction.WEST;
        var crawler=ZombieroolModEntities.CRAWLER.get().create(level);crawler.setPos(Vec3.atBottomCenterOf(wallPos));level.addFreshEntity(crawler);crawler.setEmergenceTicks(0);crawler.getPersistentData().remove("zr_emerge_no_ai");crawler.setNoAi(false);crawler.setOnGround(true);
        check(wall.ready()&&wall.exit.equals(new BlockPos(97,80,0)),"Crawler Wall Link discovers the first safe floor along a continuous wall");
        check(wall.wallFace==Direction.EAST&&wall.duration==134,"Wall Link automatically detects the correct wall and slows a ten-block descent to 6.7 seconds");
        level.setBlock(wallPos.east(),Blocks.AIR.defaultBlockState(),3);
        check(wall.ready()&&wall.wallFace==Direction.EAST&&wall.exit.getY()==80,"A roof-hole marker remains usable when the wall begins one block below Entry");
        check(wall.accepts(crawler)&&!wall.accepts(mob),"Wall Link accepts Nova Crawlers only");
        // An actual roof approach must select the virtual descent edge, not a ten-block fall.
        for(int x=93;x<=97;x++)for(int z=-2;z<=2;z++){level.setBlock(new BlockPos(x,88,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(new BlockPos(x,89,z),Blocks.STONE.defaultBlockState(),3);}
        for(int x=97;x<=97;x++)for(int z=0;z<=1;z++){level.setBlock(new BlockPos(x,89,z),Blocks.AIR.defaultBlockState(),3);level.setBlock(new BlockPos(x,88,z),Blocks.AIR.defaultBlockState(),3);}
        crawler.setPos(94.5,90,.5);crawler.setOnGround(true);
        var wallPath=crawler.getNavigation().createPath(new BlockPos(97,80,0),0);
        check(wallPath!=null&&wallPath.canReach(),"Actual Nova Crawler navigation reaches ground through the roof Wall Link");
        boolean wallEdge=false;for(int n=1;n<wallPath.getNodeCount();n++)if(wallPath.getNode(n-1).y==wallPos.getY()&&wallPath.getNode(n).asBlockPos().equals(wall.exit))wallEdge=true;
        if(!wallEdge){for(int n=0;n<wallPath.getNodeCount();n++)System.out.println("WALL PATH NODE: "+wallPath.getNode(n).asBlockPos());}
        check(wallEdge,"Crawler path includes wall descent instead of treating the roof opening as a normal fall");
        crawler.setPos(96.5,90,.5);wallPath.setNextNodeIndex(wallPath.getNodeCount()-1);crawler.getNavigation().moveTo(wallPath,1);TraversalSystem.approach(crawler);
        check(TraversalMotion.active(crawler)&&TraversalMotion.flight(crawler).style==2,"Roof approach automatically begins authoritative wall descent");
        for(int i=0;i<wall.duration;i++)motionTick.invoke(null,level);
        check(crawler.position().distanceToSqr(wall.end())<.0001&&!crawler.noPhysics,"Crawler descends to ground and restores collision");
        Vec3 previousPlayerPosition=a.position();a.setPos(95.5,94,.5);crawler.setTarget(a);crawler.setPos(96,90,.5);crawler.setOnGround(true);
        wallPath=crawler.getNavigation().createPath(wall.exit,0);
        check(wallPath!=null&&wallPath.canReach(),"Wall descent remains routable while the player is above and closer to the roof than its exit");
        crawler.getNavigation().moveTo(wallPath,1);TraversalSystem.approach(crawler);
        check(TraversalMotion.active(crawler)&&TraversalMotion.flight(crawler).style==2,"Crawler starts the planned wall animation before vanilla can skip the roof edge");
        for(int i=0;i<wall.duration;i++)motionTick.invoke(null,level);
        crawler.getNavigation().stop();crawler.setPos(97.15,89.9,1.4);crawler.setOnGround(false);crawler.setDeltaMovement(0,-.08,0);TraversalSystem.approach(crawler);
        check(TraversalMotion.active(crawler)&&TraversalMotion.flight(crawler).style==2,"The first falling step beside a roof opening is captured even with a nearby elevated target and no path");
        for(int i=0;i<wall.duration;i++)motionTick.invoke(null,level);
        a.setPos(previousPlayerPosition);crawler.setTarget(null);
        // A lethal hit during a cable ride must restore gravity, then settle a NoAI corpse.
        var highZip=link(level,new BlockPos(85,88,2),new BlockPos(91,88,2),TraversalSystem.ZIPLINE.get());highZip.duration=40;
        var airborne=zombie(level,highZip.start());airborne.getPersistentData().putBoolean("zombierool:no_gore",true);
        TraversalMotion.start(airborne,new TraversalSystem.Route(highZip,false));for(int n=0;n<20;n++)motionTick.invoke(null,level);
        double deathHeight=airborne.getY();airborne.hurt(level.damageSources().generic(),10000);
        check(!airborne.isAlive()&&!TraversalMotion.active(airborne)&&!airborne.isNoGravity()&&!airborne.noPhysics,"A real lethal hit interrupts Zipline and restores corpse gravity immediately");
        for(int n=0;n<8;n++)airborne.tick();check(airborne.getY()<deathHeight-1&&airborne.isNoAi(),"A NoAI zombie corpse falls during its actual death ticks");
        for(int n=0;n<60;n++)airborne.tick();check(Math.abs(airborne.getY()-80)<.01&&airborne.onGround()&&!airborne.isRemoved(),"Airborne corpse reaches the floor and remains as a grounded corpse");
        var gasBody=ZombieroolModEntities.CRAWLER.get().create(level);gasBody.setPos(87.5,87,2.5);level.addFreshEntity(gasBody);gasBody.setEmergenceTicks(0);gasBody.setNoAi(false);gasBody.getPersistentData().putBoolean("zombierool:no_gore",true);
        gasBody.hurt(level.damageSources().generic(),10000);double gasHeight=gasBody.getY();for(int n=0;n<8;n++)gasBody.tick();check(!gasBody.isAlive()&&gasBody.getY()<gasHeight-1,"A dying Nova Crawler falls during its gas fuse instead of floating");
        level.setBlock(new BlockPos(98,85,0),Blocks.AIR.defaultBlockState(),3);check(!wall.ready(),"An interrupted wall cannot become a false navigable shortcut");
        var gatePos=new BlockPos(104,80,0);level.setBlock(gatePos,MapAuthorSystem.GATE.get().defaultBlockState().setValue(OneWayGate.Block.FACING,Direction.EAST),3);
        mob.setPos(103.5,80,.5);OneWayGate.tick(mob);
        check(OneWayGate.allowed(mob,new Node(105,80,0),new Node(103,80,0)),"Before crossing, the gate has no stale restriction");
        mob.setPos(105.5,80,.5);OneWayGate.tick(mob);
        check(!OneWayGate.allowed(mob,new Node(105,80,0),new Node(103,80,0)),"Crossed gate rejects paths back outside");
        check(OneWayGate.allowed(mob,new Node(103,80,0),new Node(105,80,0)),"Gate still permits forward routes");
        check(OneWayGate.allowed(mob,new Node(105,80,8),new Node(103,80,8)),"Gate does not invalidate unrelated corridors");
        for(int x=101;x<=107;x++)for(int z=-3;z<=3;z++)if(z!=0)for(int y=80;y<=83;y++)level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
        mob.setOnGround(true);mob.getNavigation().stop();var backPath=mob.getNavigation().createPath(new BlockPos(102,80,0),0);
        check(backPath==null||!backPath.canReach(),"Actual zombie navigation rejects the previously crossed outside corridor");
        var saved=mob.getPersistentData().copy();check(!saved.getCompound("zr_passed_gates").isEmpty(),"Crossed gates persist with the entity");
        var born=ZombieroolModEntities.ZOMBIE.get().create(level);born.setPos(106.5,80,2.5);level.addFreshEntity(born);
        check(born.getEmergenceTicks()==SpawnEmergence.DURATION&&born.isNoAi(),"New Zombie begins its ground-emergence animation with AI paused");
        for(int i=0;i<SpawnEmergence.DURATION;i++)SpawnEmergence.tick(new LivingEvent.LivingTickEvent(born));check(born.getEmergenceTicks()==0&&!born.isNoAi()&&SpawnEmergence.DURATION==12,"Emergence restores the original AI after 0.6 seconds");
        var channels=new CompoundTag();channels.putString("enableChannels","6");channels.putString("disableChannels","0");channels.putInt("channelDuration",2);
        WaveManager.lockChannel(6);WaveManager.unlockChannel(0);WaveManager.UNLOCKED_CHANNELS.add(0);WaveManager.LOCKED_CHANNELS.remove(0);
        SpawnChannelActions.apply(level,channels);check(WaveManager.UNLOCKED_CHANNELS.contains(6)&&WaveManager.LOCKED_CHANNELS.contains(0),"Teleporter actions enable and disable spawner channels");
        level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+41);
        SpawnChannelActions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,level.getServer()));
        check(WaveManager.LOCKED_CHANNELS.contains(6)&&WaveManager.UNLOCKED_CHANNELS.contains(0)&&!WaveManager.LOCKED_CHANNELS.contains(0),"Expired actions restore exact original channel states");
        SpawnChannelActions.apply(level,channels);WaveManager.unlockChannel(6);level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+41);SpawnChannelActions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,level.getServer()));
        check(WaveManager.UNLOCKED_CHANNELS.contains(6),"New explicit script action supersedes an older temporary action");
        channels.putInt("channelDuration",0);SpawnChannelActions.apply(level,channels);level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+100);SpawnChannelActions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,level.getServer()));check(WaveManager.UNLOCKED_CHANNELS.contains(6)&&WaveManager.LOCKED_CHANNELS.contains(0),"Zero-duration actions remain active for the match");
        check(SpawnChannelActions.parse("6, bad,-1,10001,6,7").equals(Set.of(6,7)),"Channel list rejects malformed and out-of-range entries");
        check(!TeleporterSystem.Platform.rounded(0).bounds().contains(new Vec3(.02,.05,.02)),"Rounded Teleporter corner is trimmed in collision");
        check(!TeleporterSystem.Platform.rounded(4).isEmpty(),"Rounded Teleporter retains its central landing surface");
        var game=WaveManager.class.getDeclaredField("gameRunning");game.setAccessible(true);boolean wasRunning=game.getBoolean(null);game.setBoolean(null,true);
        BlockPos chestPos=new BlockPos(108,80,0);level.setBlock(chestPos,MapAuthorSystem.SOUL_CHEST.get().defaultBlockState(),3);var chest=(SoulChestSystem.Chest)level.getBlockEntity(chestPos);
        var cfg=chest.config();cfg.putInt("souls",2);cfg.putInt("rewardPoints",75);cfg.putString("rewardItem","minecraft:diamond");cfg.putInt("rewardCount",1);chest.configure(cfg);
        a.setPos(108.5,80,2.5);PointManager.setScore(a,0);int diamonds=a.getInventory().countItem(Items.DIAMOND);
        var tick=SoulChestSystem.Chest.class.getDeclaredMethod("tick");tick.setAccessible(true);
        check(!level.getBlockState(chestPos).getValue(SoulChestSystem.ChestBlock.OPEN),"An empty Soul Chest has a closed lid");
        int soulKillPoints=0;
        for(int i=0;i<2;i++){
            int beforeKill=PointManager.getScore(a);
            var victim=zombie(level,new Vec3(109.5,80,.5));victim.getPersistentData().putBoolean("zombierool:no_gore",true);victim.hurt(level.damageSources().playerAttack(a),10000);
            soulKillPoints+=PointManager.getScore(a)-beforeKill;
            var visible=level.getEntitiesOfClass(SoulOrbEntity.class,new net.minecraft.world.phys.AABB(chestPos).inflate(20));
            check(!victim.isAlive()&&visible.size()==1,"A real zombie death spawns one tracked Soul Orb");
            var orb=visible.get(0);check(orb.getAddEntityPacket()!=null&&orb.position().distanceToSqr(victim.position())<4,"Soul Orb provides a Forge spawn packet and starts at its corpse");
            var spawnData=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());orb.writeSpawnData(spawnData);var trackedCopy=ZombieroolModEntities.SOUL_ORB.get().create(level);trackedCopy.readSpawnData(spawnData);check(trackedCopy.sample(16).distanceToSqr(orb.sample(16))<.000001,"Tracking a soul synchronizes its complete curved route");spawnData.release();
            for(int n=0;n<16;n++)tick.invoke(chest);check(orb.position().distanceToSqr(orb.sample(16))<.00001&&chest.collected==i,"Soul stays visible in flight and counts only on arrival");
            for(int n=16;n<SoulOrbEntity.DURATION;n++)tick.invoke(chest);check(orb.isRemoved(),"Arriving soul removes its visual entity");
            if(i==0)check(chest.collected==1&&!chest.completed&&level.getBlockState(chestPos).getValue(SoulChestSystem.ChestBlock.OPEN),"Soul Chest opens on its first absorbed soul before completion");
        }
        check(chest.completed&&chest.collected==2,"Red soul arrivals complete the configured goal");
        check(!level.getBlockState(chestPos).getValue(SoulChestSystem.ChestBlock.OPEN)&&level.getBlockState(chestPos).getValue(SoulChestSystem.ChestBlock.COMPLETED),"Completed Soul Chest closes its lid and selects its finished appearance");
        check(!chest.auraActive(),"Completed Soul Chest stops its active aura");
        var extra=zombie(level,new Vec3(109.5,80,.5));SoulChestSystem.death(new LivingDeathEvent(extra,level.damageSources().playerAttack(a)));extra.discard();for(int n=0;n<20;n++)tick.invoke(chest);check(chest.collected==2,"Completed Soul Chest cannot count extra souls or issue another reward");
        check(PointManager.getScore(a)==soulKillPoints+75&&a.getInventory().countItem(Items.DIAMOND)==diamonds+1,"Chest grants configured points and item exactly once in addition to ordinary kill points");
        check(level.getBlockState(chestPos).getSignal(level,chestPos,Direction.NORTH)==15,"Completed chest emits redstone strength 15");
        var snapshot=chest.saveWithoutMetadata();chest.load(snapshot);check(chest.completed&&chest.collected==2,"Chest progress survives save / load");
        chest.reset();check(!chest.completed&&chest.collected==0&&level.getBlockState(chestPos).getSignal(level,chestPos,Direction.NORTH)==0,"Chest reset clears progress and redstone");
        check(!level.getBlockState(chestPos).getValue(SoulChestSystem.ChestBlock.COMPLETED)&&chest.auraActive(),"Reset restores an unfinished Soul Chest and its active aura");
        var distant=zombie(level,new Vec3(135.5,80,.5));SoulChestSystem.death(new LivingDeathEvent(distant,level.damageSources().playerAttack(a)));distant.discard();for(int n=0;n<20;n++)tick.invoke(chest);check(chest.collected==0,"Out-of-radius death does not fill Soul Chest");
        cfg.putInt("resetAfter",1);cfg.putBoolean("resetOnMatch",false);chest.configure(cfg);var lone=zombie(level,new Vec3(109.5,80,.5));SoulChestSystem.death(new LivingDeathEvent(lone,level.damageSources().playerAttack(a)));lone.discard();for(int n=0;n<32;n++)tick.invoke(chest);check(chest.collected==1&&!chest.completed,"Partial Soul Chest progress waits for more souls");level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+21);tick.invoke(chest);check(chest.collected==0&&!chest.completed,"Inactivity reset clears an unfinished Soul Chest");
        chest.collected=1;SoulChestSystem.reset(level);check(chest.collected==0&&!level.getBlockState(chestPos).getValue(SoulChestSystem.ChestBlock.OPEN)&&!chest.config().contains("resetOnMatch"),"Every match reset clears and closes Soul Chest even with an obsolete disabled reset flag");
        var pending=zombie(level,new Vec3(109.5,80,.5));pending.getPersistentData().putBoolean("zombierool:no_gore",true);pending.hurt(level.damageSources().playerAttack(a),10000);var inFlight=level.getEntitiesOfClass(SoulOrbEntity.class,new net.minecraft.world.phys.AABB(chestPos).inflate(20));check(inFlight.size()==1,"A new death reserves one soul before reset");SoulChestSystem.reset(level);check(inFlight.get(0).isRemoved()&&chest.collected==0,"Match reset removes souls in flight without awarding them");
        var soulWire=new TransportPackets.Soul(chestPos,new Vec3(3,4,5),32,false);var soulBuffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());TransportPackets.Soul.encode(soulWire,soulBuffer);check(TransportPackets.Soul.decode(soulBuffer).equals(soulWire),"Visible soul packet preserves the origin, chest and travel duration");soulBuffer.release();
        game.setBoolean(null,wasRunning);SpawnChannelActions.reset();
        check(MapAuthorSystem.SOUL_TYPE.get().isValid(MapAuthorSystem.SOUL_CHEST.get().defaultBlockState()),"Soul chest uses a valid registered block entity");
        check(BlindBuySystem.BE.get().isValid(BlindBuySystem.RACK.get().defaultBlockState()),"Secret Weapon Rack shares cabinet behavior and a valid block entity");
        check(me.cryo.zombierool.init.ZombieroolModExtraBlockEntities.DEFENSE_DOOR.get().isValid(me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get().defaultBlockState()),"Iron Defense Door shares repair and break behavior");
        check(me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get().defaultBlockState().is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new ResourceLocation("zombierool","allowed_blocks"))),"Iron Defense Door is navigable by default through the allowed blocks tag");
        Vec3 repairPlayerPosition=a.position();var repairPos=new BlockPos(106,80,6);
        level.setBlock(repairPos.below(),Blocks.STONE.defaultBlockState(),3);a.setPos(106.5,80,7.8);
        for(var repairBlock:List.of(me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get(),me.cryo.zombierool.core.registry.ZRBlocks.IRON_DEFENSE_DOOR.get())){
            var door=(DefenseDoorSystem.DefenseDoorBlock)repairBlock;
            level.setBlock(repairPos.above(),Blocks.AIR.defaultBlockState(),2);level.setBlock(repairPos,Blocks.AIR.defaultBlockState(),2);
            level.setBlock(repairPos,door.defaultBlockState(),2);level.setBlock(repairPos.above(),door.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
            for(int stage=0;stage<5;stage++){
                door.updateStage(level,repairPos,stage);me.cryo.zombierool.handlers.ServerInteractionHandler.resetAll();
                me.cryo.zombierool.handlers.ServerInteractionHandler.handleInteraction(a,stage%2==0?repairPos:repairPos.above(),me.cryo.zombierool.network.InteractionType.REPAIR_BARRICADE);
                check(level.getBlockState(repairPos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==stage+1&&level.getBlockState(repairPos.above()).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==stage+1,"Public F interaction repairs both halves of "+ForgeRegistries.BLOCKS.getKey(door)+" from stage "+stage);
            }
            level.setBlock(repairPos,level.getBlockState(repairPos).setValue(DefenseDoorSystem.DefenseDoorBlock.STAGE,0),2);
            level.setBlock(repairPos.above(),level.getBlockState(repairPos.above()).setValue(DefenseDoorSystem.DefenseDoorBlock.STAGE,4),2);
            me.cryo.zombierool.handlers.ServerInteractionHandler.resetAll();
            me.cryo.zombierool.handlers.ServerInteractionHandler.handleInteraction(a,repairPos.above(),me.cryo.zombierool.network.InteractionType.REPAIR_BARRICADE);
            check(level.getBlockState(repairPos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==1&&level.getBlockState(repairPos.above()).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==1,"Upper-half repair uses the authoritative lower stage even if halves were desynchronized");
        }
        a.setPos(repairPlayerPosition);
        var copiedWallPos=new BlockPos(108,80,6);level.setBlock(copiedWallPos,BuyWallWeaponSystem.BLOCK.get().defaultBlockState(),3);
        var copiedWall=(BuyWallWeaponSystem.BuyWallWeaponBlockEntity)level.getBlockEntity(copiedWallPos);
        copiedWall.setItem(0,new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool:m1911"))));copiedWall.setMimic(Blocks.STONE.defaultBlockState());
        var copiedNbt=copiedWall.saveWithFullMetadata();copiedWall.load(copiedNbt);copiedWall.setMimic(Blocks.OAK_PLANKS.defaultBlockState());
        check(copiedWall.getMimic().is(Blocks.OAK_PLANKS)&&!copiedWall.getItem(0).isEmpty(),"A copied wall weapon accepts another texture without clearing its saved gun");
        var receivingWall=new BuyWallWeaponSystem.BuyWallWeaponBlockEntity(copiedWallPos,level.getBlockState(copiedWallPos));receivingWall.load(copiedWall.getUpdateTag());
        check(receivingWall.getMimic().is(Blocks.OAK_PLANKS)&&receivingWall.getItem(0).getItem()==copiedWall.getItem(0).getItem(),"The replacement texture and saved gun are both present in the client update payload");
        var globalPos=new BlockPos(118,80,-8);level.setBlock(globalPos,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","activator")).defaultBlockState(),3);level.setBlock(globalPos.east(),Blocks.COMMAND_BLOCK.defaultBlockState(),3);
        var globalCommand=(net.minecraft.world.level.block.entity.CommandBlockEntity)level.getBlockEntity(globalPos.east());me.cryo.zombierool.config.GlobalSwitchState.setActivated(level,true);
        check(globalCommand.isPowered(),"Global Activator powers its command block on a rising edge");
        for(String channel:List.of("alpha","gamma")){
            var receptor=new BlockPos(channel.equals("alpha")?122:126,80,-8);level.setBlock(receptor,ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool",channel+"_receptor")).defaultBlockState(),3);level.setBlock(receptor.east(),Blocks.COMMAND_BLOCK.defaultBlockState(),3);
            me.cryo.zombierool.config.MultiReceptorState.setActivated(level,channel,true);var command=(net.minecraft.world.level.block.entity.CommandBlockEntity)level.getBlockEntity(receptor.east());check(command.isPowered(),"Named "+channel+" receptor powers its command block");
            MapPower.resetSignals(level);check(!globalCommand.isPowered()&&!command.isPowered()&&!me.cryo.zombierool.config.MultiReceptorState.isActivated(level,channel),"Reset sends a falling edge to global and "+channel+" command blocks");
            me.cryo.zombierool.config.GlobalSwitchState.setActivated(level,true);me.cryo.zombierool.config.MultiReceptorState.setActivated(level,channel,true);check(globalCommand.isPowered()&&command.isPowered(),"Global and "+channel+" command blocks receive a fresh rising edge after reset");
        }
        var signals=me.cryo.zombierool.config.MultiReceptorSavedData.get(level).save(new CompoundTag());var reloadedSignals=new me.cryo.zombierool.config.MultiReceptorSavedData();reloadedSignals.load(signals);check(reloadedSignals.isActivated("gamma")&&!reloadedSignals.getReceptorPositions("gamma").isEmpty(),"Additional signal channels survive save and load without null position sets");MapPower.resetSignals(level);
        // Pursuit must survive the airborne frame on a stair; random looking must not steal LOOK.
        level.getServer().getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
        b.setGameMode(GameType.CREATIVE);a.setGameMode(GameType.SURVIVAL);a.setPos(178.5,80,.5);
        a.setInvulnerable(false);a.getAbilities().invulnerable=false;
        a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);a.setHealth(100000);
        for(int x=149;x<=180;x++)for(int z=-2;z<=2;z++){
            level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);
            level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=80;y<=84;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        for(int type=0;type<2;type++){
            AbstractZombieRoolEntity hunter=type==0?zombie(level,new Vec3(150.5,80,.5)):ZombieroolModEntities.CRAWLER.get().create(level);
            if(type==1){hunter.setPos(150.5,80,.5);level.addFreshEntity(hunter);hunter.setEmergenceTicks(0);hunter.getPersistentData().remove("zr_emerge_no_ai");hunter.setNoAi(false);hunter.setOnGround(true);}
            hunter.setTarget(a);hunter.setOnGround(false);hunter.getPersistentData().putInt("zr_reachable_next_check",100000);
            me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.chooseAndSet(hunter,a);
            check(hunter.getTarget()==a,"Pursuit retains its player during an airborne stair frame, type "+type);
            hunter.setOnGround(true);int distracted=0;double beforeDistance=hunter.distanceToSqr(a);
            var huntingPath=hunter.getNavigation().createPath(a,0);System.out.println("PURSUIT INITIAL path="+huntingPath+" reaches="+(huntingPath!=null&&huntingPath.canReach())+" end="+(huntingPath==null?null:huntingPath.getEndNode())+" range="+hunter.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getValue()+" canAttack="+hunter.canAttack(a));
            for(int n=0;n<200;n++){
                level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+1);hunter.tickCount++;hunter.tick();
                for(var running:hunter.goalSelector.getAvailableGoals())if(running.isRunning()&&running.getGoal() instanceof net.minecraft.world.entity.ai.goal.RandomLookAroundGoal)distracted++;
                if(n%40==0)System.out.println("PURSUIT TICK "+n+" target="+hunter.getTarget()+" path="+hunter.getNavigation().getPath()+" player="+a.position()+" down="+me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(a.getUUID()));
            }
            System.out.println("PURSUIT RESULT type="+type+" distracted="+distracted+" target="+hunter.getTarget()+" before="+beforeDistance+" after="+hunter.distanceToSqr(a)+" pos="+hunter.position()+" alive="+hunter.isAlive()+" noAi="+hunter.isNoAi());
            check(distracted==0&&hunter.getTarget()==a&&hunter.distanceToSqr(a)<beforeDistance/2,"Zombie/Crawler keeps pursuing without random-look interruptions, type "+type);hunter.discard();
        }
        // Reproduce a wide crawler already touching the leaf of an open double door.
        for(int x=185;x<=188;x++)for(int z=-2;z<=3;z++){
            level.setBlock(new BlockPos(x,78,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=80;y<=83;y++)level.setBlock(new BlockPos(x,y,z),x==185||x==188?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
        }
        for(int x=186;x<=187;x++){
            var door=Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING,Direction.NORTH).setValue(net.minecraft.world.level.block.DoorBlock.OPEN,true).setValue(net.minecraft.world.level.block.DoorBlock.HINGE,x==186?net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT:net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
            level.setBlock(new BlockPos(x,80,0),door,2);level.setBlock(new BlockPos(x,81,0),door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        }
        a.setPos(187.5,80,2.5);var doorwayCrawler=ZombieroolModEntities.CRAWLER.get().create(level);doorwayCrawler.setPos(187.5,80,-.425);level.addFreshEntity(doorwayCrawler);doorwayCrawler.setEmergenceTicks(0);doorwayCrawler.getPersistentData().remove("zr_emerge_no_ai");doorwayCrawler.setNoAi(false);doorwayCrawler.setOnGround(true);
        for(int n=0;n<200;n++){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+1);doorwayCrawler.tickCount++;doorwayCrawler.tick();}
        check(doorwayCrawler.getZ()>1.5&&doorwayCrawler.distanceToSqr(a)<4,"A crawler touching an open double-door leaf aligns with its free opening and physically crosses it");doorwayCrawler.discard();
        cherryTest(level,a);
        environmentAndRouteTest(level,a);
        check(tab.getDisplayName().getString().equals("ZombieRool")&&tab.getIconItem().is(ZombieroolModItems.CREATIVE_ICON.get()),"Creative tab uses the ZombieRool name and dedicated Windows icon item");
        NavigationReviewTest.run(level,a);
        EquipmentGameplayTest.run(level,a,b);
        WonderWeaponGameplayTest.run(level,a,b);
        secretRackReset(level);
        SoulObjectiveGameplayTest.run(level,a);
        teleporterRevision(level);
        Oct12GameplayTest.run(level,a,b);
        SpecialEnemyGameplayTest.run(level,a,b);
        System.out.println("26.7 FEATURE PASS: "+assertions+" assertions");
    }

    private static void teleporterRevision(ServerLevel level)throws Exception{
        var positions=List.of(new BlockPos(700,80,0),new BlockPos(710,80,0),new BlockPos(720,80,0),new BlockPos(730,80,0));
        for(var pos:positions){level.getChunkAt(pos);level.setBlock(pos,TeleporterSystem.PLATFORM.get().defaultBlockState(),2);((TeleporterSystem.Pad)level.getBlockEntity(pos)).onLoad();}
        var source=(TeleporterSystem.Pad)level.getBlockEntity(positions.get(0));var receiver=(TeleporterSystem.Pad)level.getBlockEntity(positions.get(1));var other=(TeleporterSystem.Pad)level.getBlockEntity(positions.get(2));var sibling=(TeleporterSystem.Pad)level.getBlockEntity(positions.get(3));
        check(source.config.getString("id").matches("T[0-9]+")&&!source.config.getString("id").equals(receiver.config.getString("id")),"Teleporter automatic IDs are short and unique");
        String id=source.config.getString("id");source.onLoad();check(source.config.getString("id").equals(id),"Teleporter ID stays stable on reload");
        var c=source.config.copy();c.putString("destination",receiver.config.getString("id"));source.configure(c);
        c=receiver.config.copy();c.putString("role","RECEIVER");c.putBoolean("invisible",true);c.putString("enableChannels","42");receiver.configure(c);
        c=sibling.config.copy();c.putString("destination",receiver.config.getString("id"));sibling.configure(c);
        var registry=TeleporterRegistry.get(level);var unloadedPos=new BlockPos(32000,80,32000);var unloaded=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);unloaded.putString("id","remote");unloaded.putString("destination",receiver.config.getString("id"));registry.pads.put(unloadedPos,unloaded);
        var powerConfig=source.config.copy();powerConfig.putBoolean("requiresPower",false);source.configure(powerConfig);
        powerConfig=source.config.copy();powerConfig.putBoolean("requiresPower",true);powerConfig.putBoolean("invisible",true);source.configure(powerConfig);
        source.tick();source.onLoad();receiver.onLoad();
        check(source.config.getBoolean("requiresPower")&&receiver.config.getBoolean("requiresPower")&&registry.pads.get(unloadedPos).getBoolean("requiresPower"),"Power required On survives visibility updates, controller ticks, reload and linked-pad synchronization");
        var globals=me.cryo.zombierool.shadow.luaj.vm2.lib.jse.JsePlatform.standardGlobals();globals.set("api",me.cryo.zombierool.shadow.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(new me.cryo.zombierool.scripting.ZombieroolAPI(level)));globals.set("padId",source.config.getString("id"));
        globals.load("assert(api:configureTeleporter(padId,{enabled=false,requiresPower=true})); assert(api:getTeleporter(padId).enabled==false); assert(api:configureTeleporter(padId,{enabled=true})); api:setTeleporterCondition('lua','test',true); assert(api:getTeleporterCondition('lua','test'))","teleporter-api").call();
        check(source.config.getBoolean("requiresPower")&&source.config.getBoolean("enabled"),"Actual Lua can configure disabled teleporters and preserves omitted settings");
        var scriptPlayer=level.players().get(0);globals.set("playerId",scriptPlayer.getUUID().toString());
        globals.load("assert(api:equipEquipment(playerId,'c4')); assert(api:equipEquipment(playerId,'flash')); assert(api:setEquipmentCharges(playerId,99,-8)); local e=api:getEquipmentLoadout(playerId); assert(e.equipmentCharges==2 and e.tacticalCharges==0); assert(not api:equipEquipment(playerId,'unknown')); api:setSpawnerChannels('95','96',0)","equipment-api").call();
        check(WaveManager.UNLOCKED_CHANNELS.contains(95)&&WaveManager.LOCKED_CHANNELS.contains(96),"Actual Lua controls loadout charges safely and changes spawner channels");
        var nav=link(level,new BlockPos(748,80,0),new BlockPos(752,80,0),TraversalSystem.ZIPLINE.get());globals.load("assert(api:configureNavigationLink(748,80,0,{duration=36,allowPlayers=false})); assert(api:getNavigationLink(752,80,0).duration==36)","navigation-api").call();
        check(nav.duration==36&&!nav.allowPlayers,"Lua navigation patches synchronize both zipline endpoints");
        c=source.config.copy();c.putBoolean("requiresPower",false);c.putInt("cost",250);c.putInt("chargeTime",3);source.configure(c);
        check(!receiver.config.getBoolean("requiresPower")&&!sibling.config.getBoolean("requiresPower")&&receiver.config.getInt("cost")==250&&sibling.config.getInt("chargeTime")==3,"Linked teleporter component synchronizes power, price and charge duration in both directions");
        check(other.config.getBoolean("requiresPower")&&other.config.getInt("cost")==0,"Unrelated teleporters sharing the default channel keep their settings");
        check(receiver.config.getBoolean("invisible")&&receiver.role().equals("RECEIVER")&&receiver.config.getString("enableChannels").equals("42"),"Receiver visibility, role and local spawner actions remain independent");
        check(!registry.pads.get(unloadedPos).getBoolean("requiresPower")&&registry.pads.get(unloadedPos).getInt("cost")==250,"Synchronization updates an unloaded destination in saved data");
        level.getChunkAt(unloadedPos);level.setBlock(unloadedPos,TeleporterSystem.PLATFORM.get().defaultBlockState(),2);var remote=(TeleporterSystem.Pad)level.getBlockEntity(unloadedPos);remote.load(new CompoundTag());remote.onLoad();
        check(remote.config.getString("id").equals("remote")&&!remote.config.getBoolean("requiresPower")&&remote.config.getInt("cost")==250,"Reloading the old chunk restores the synchronized registry settings");
        c=receiver.config.copy();c.putBoolean("requiresPower",true);receiver.configure(c);check(source.config.getBoolean("requiresPower")&&remote.config.getBoolean("requiresPower"),"Editing the receiver also synchronizes incoming sources");
        c=receiver.config.copy();c.putString("id","HubA");receiver.configure(c);check(source.config.getString("destination").equals("HubA")&&TeleporterSystem.find(level,source.config.getString("destination"))==receiver,"Renaming a linked destination preserves incoming routes");
        var oldRegistry=new CompoundTag();var records=new net.minecraft.nbt.ListTag();var oldPos=new BlockPos(740,80,0);var legacy=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);legacy.putString("id","tp_740_80_0");var record=new CompoundTag();record.putLong("pos",oldPos.asLong());record.put("config",legacy);records.add(record);oldRegistry.put("pads",records);
        var load=TeleporterRegistry.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);var migrated=(TeleporterRegistry)load.invoke(null,oldRegistry);
        check(migrated.pads.get(oldPos).getString("id").matches("T[0-9]+")&&migrated.named("tp_740_80_0").equals(List.of(oldPos)),"Legacy coordinate teleporter IDs migrate while old destinations still resolve");
        migrated=(TeleporterRegistry)load.invoke(null,migrated.save(new CompoundTag()));check(migrated.named("tp_740_80_0").equals(List.of(oldPos)),"Teleporter aliases survive saved-data reloads");
        var switchPos=new BlockPos(745,80,0);level.setBlock(switchPos,TeleporterSystem.SWITCH.get().defaultBlockState(),2);var sw=(TeleporterSystem.Pad)level.getBlockEntity(switchPos);sw.onLoad();check(sw.config.getString("id").matches("D[0-9]+")&&sw.config.getString("condition").matches("[A-Za-z0-9]+"),"Switch IDs and default condition names contain only letters and numbers");
        registry.condition(sw.channel(),sw.config.getString("condition"),true);check(registry.conditionCount(sw.channel())>=1,"Short switch condition names still contribute to DEFCON");
        var doorPos=new BlockPos(750,80,0);var glass=me.cryo.zombierool.core.registry.ZRBlocks.GLASS_DEFENSE_DOOR.get();level.setBlock(doorPos.below(),Blocks.STONE.defaultBlockState(),2);var door=glass.defaultBlockState();level.setBlock(doorPos,door,2);level.setBlock(doorPos.above(),door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
        GlassDefenseDoorBlock.damage(level,doorPos.above());check(level.getBlockState(doorPos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==6,"Bullets still damage intact glass from either half");
        ((GlassDefenseDoorBlock)glass).updateStage(level,doorPos,0);((GlassDefenseDoorBlock)glass).updateStage(level,doorPos,5);GlassDefenseDoorBlock.damage(level,doorPos);GlassDefenseDoorBlock.damage(level,doorPos.above());check(level.getBlockState(doorPos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==5,"Bullets cannot destroy rebuilt glass-door barricades");
        ((GlassDefenseDoorBlock)glass).updateStage(level,doorPos,4);check(level.getBlockState(doorPos).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE)==4,"Enemy barricade damage remains functional");
        for(var pos:positions)level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);level.setBlock(unloadedPos,Blocks.AIR.defaultBlockState(),2);level.setBlock(switchPos,Blocks.AIR.defaultBlockState(),2);level.setBlock(doorPos,Blocks.AIR.defaultBlockState(),2);level.setBlock(doorPos.above(),Blocks.AIR.defaultBlockState(),2);
    }

    private static void secretRackReset(ServerLevel level)throws Exception{
        var positions=List.of(new BlockPos(600,80,0),new BlockPos(602,80,0));
        for(int i=0;i<positions.size();i++){
            var pos=positions.get(i);var block=i==0?BlindBuySystem.BLOCK.get():BlindBuySystem.RACK.get();
            level.setBlock(pos,block.defaultBlockState().setValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN,true),3);
            var cabinet=(BlindBuySystem.BlindBuyCabinetBlockEntity)level.getBlockEntity(pos);cabinet.onLoad();cabinet.setPrice(1800+i);
            cabinet.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(()->new AssertionError("cabinet inventory")).insertItem(0,new ItemStack(Items.DIAMOND),false);
            level.setBlock(pos,level.getBlockState(pos).setValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN,true),3);
            check(level.getBlockState(pos).getValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN),"Fixture begins with an opened cabinet before the match: "+pos);
        }
        var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,false);WaveManager.startGame(level);
        for(var pos:positions){
            var cabinet=(BlindBuySystem.BlindBuyCabinetBlockEntity)level.getBlockEntity(pos);
            check(!level.getBlockState(pos).getValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN),"Actual match start closes a cabinet bought during preparation: "+pos);
            check(cabinet.getWeapon().is(Items.DIAMOND)&&cabinet.getPrice()==1800+positions.indexOf(pos),"Match reset preserves the cabinet's configured item and price: "+pos);
        }
        var pos=positions.get(1);var cabinet=(BlindBuySystem.BlindBuyCabinetBlockEntity)level.getBlockEntity(pos);
        level.setBlock(pos,level.getBlockState(pos).setValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN,true),3);
        BlindBuySystem.unregisterCabinet(level,cabinet);BlindBuySystem.resetAllCabinets(level);
        check(level.getBlockState(pos).getValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN),"Unloaded rack is absent from the immediate reset iteration");
        cabinet.onLoad();
        check(!level.getBlockState(pos).getValue(BlindBuySystem.BlindBuyCabinetBlock.OPEN)&&cabinet.getWeapon().is(Items.DIAMOND),"Previously unloaded rack closes when its chunk returns after a new match");
    }
    private static void environmentAndRouteTest(ServerLevel level,ServerPlayer player)throws Exception {
        for(var type:List.of(ZombieroolModEntities.ZOMBIE.get(),ZombieroolModEntities.CRAWLER.get(),ZombieroolModEntities.HELLHOUND.get(),ZombieroolModEntities.WHITE_KNIGHT.get(),ZombieroolModEntities.DUMMY.get(),ZombieroolModEntities.MR_CHIEF.get())){
            var mob=(LivingEntity)type.create(level);float health=mob.getHealth();
            check(!mob.causeFallDamage(50,1,level.damageSources().fall())&&!mob.hurt(level.damageSources().fall(),10000)&&mob.getHealth()==health,"Fall damage is disabled for "+ForgeRegistries.ENTITY_TYPES.getKey(type));
            check(!mob.hurt(level.damageSources().drown(),10000)&&mob.getHealth()==health&&mob.canBreatheUnderwater(),"Drowning is disabled for "+ForgeRegistries.ENTITY_TYPES.getKey(type));mob.discard();
        }
        var pig=EntityType.PIG.create(level);check(pig.hurt(level.damageSources().fall(),1),"Vanilla creatures retain their environmental damage");pig.discard();
        for(int x=230;x<=263;x++)for(int z=0;z<=6;z++)for(int y=79;y<=83;y++)level.setBlock(new BlockPos(x,y,z),y==79?Blocks.STONE.defaultBlockState():Blocks.WATER.defaultBlockState(),3);
        var swimmer=ZombieroolModEntities.ZOMBIE.get().create(level);swimmer.setPos(231.5,80,1.5);swimmer.setSpeed(.3f);swimmer.setNoGravity(true);swimmer.setYRot(0);
        var knight=ZombieroolModEntities.WHITE_KNIGHT.get().create(level);knight.setPos(231.5,80,3.5);knight.setSpeed(.3f);knight.setNoGravity(true);knight.setYRot(0);
        player.setPos(231.5,80,5.5);player.setSpeed(.3f);player.setNoGravity(true);player.setYRot(0);player.setDeltaMovement(Vec3.ZERO);
        for(int tick=0;tick<60;tick++){swimmer.baseTick();knight.baseTick();player.baseTick();swimmer.travel(new Vec3(1,0,0));knight.travel(new Vec3(1,0,0));player.travel(new Vec3(1,0,0));}
        double enemyDistance=swimmer.getX()-231.5,playerDistance=player.getX()-231.5;
        check(swimmer.isInWater()&&player.isInWater()&&enemyDistance>0&&playerDistance>0,"Enemies and players keep moving underwater; water/land speed ratios are covered by WaterMapFixesTest");
        check(Math.abs(knight.getX()-swimmer.getX())<.1&&knight.getAirSupply()>0,"White Knight shares the faster swimming and does not lose its air supply");swimmer.discard();knight.discard();player.setNoGravity(false);
        BlockPos start=new BlockPos(220,80,-5),exit=new BlockPos(222,80,-5);var link=link(level,start,exit,TraversalSystem.NODE.get());link.apex=new BlockPos(221,82,-5);level.setBlock(link.apex,TraversalSystem.NODE.get().defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,1),3);((TraversalSystem.Anchor)level.getBlockEntity(link.apex)).root=start;link.bidirectional=true;link.sync();
        TraversalSystem.entry(level,start);var mob=zombie(level,new Vec3(222.5,80,-4.5));player.setPos(220.5,90,-4.5);mob.setTarget(player);mob.getNavigation().stop();TraversalSystem.approach(mob);
        check(!TraversalMotion.active(mob),"A link does not restart towards a directly closer player while the actual stair route is being recalculated");
        var away=new net.minecraft.world.level.pathfinder.Path(List.of(new Node(222,80,-5),new Node(223,80,-5),new Node(224,80,-5)),new BlockPos(224,80,-5),true);away.setNextNodeIndex(1);mob.getNavigation().moveTo(away,1);TraversalSystem.approach(mob);
        check(!TraversalMotion.active(mob),"A bidirectional link cannot override the planned detour back towards its entrance");
        var back=new net.minecraft.world.level.pathfinder.Path(List.of(new Node(222,80,-5),new Node(220,80,-5),new Node(219,80,-5)),new BlockPos(219,80,-5),true);back.setNextNodeIndex(1);mob.getNavigation().moveTo(back,1);TraversalSystem.approach(mob);
        check(TraversalMotion.active(mob),"An explicitly planned reverse traversal remains usable");TraversalMotion.cancel(mob);mob.discard();
        var zip=TraversalSystem.ZIPLINE.get();player.getInventory().selected=0;player.getInventory().setItem(0,new ItemStack(TraversalSystem.ZIPLINE_ITEM.get()));
        for(var facing:Direction.Plane.HORIZONTAL){player.setYRot(facing.toYRot());var context=new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(player,InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(new Vec3(225.5,80,-4.5),Direction.UP,new BlockPos(225,79,-5),false)));
            var state=zip.getStateForPlacement(context);check(state.getValue(TraversalSystem.TraversalBlock.FACING)==facing.getOpposite(),"Zipline placement faces its builder from "+facing);
            check(zip.rotate(state,net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(TraversalSystem.TraversalBlock.FACING)==facing.getOpposite().getClockWise(),"Zipline can rotate from "+facing);
        }
    }
    private static void cherryTest(ServerLevel level,ServerPlayer player){
        level.getChunk(-3,-3);player.setPos(-40.5,80,-40.5);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get(),200));
        int[] cries={0};java.util.function.Consumer<net.minecraftforge.event.PlayLevelSoundEvent.AtPosition> listener=e->{if(e.getSound()!=null&&e.getSound().value()==ZombieroolModSounds.ZOMBIE_ELEC.get())cries[0]++;};
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try{
            var dead=zombie(level,player.position().add(1,0,0));dead.getPersistentData().putBoolean("zombierool:no_gore",true);dead.setHealth(0);
            CherryDischarge.trigger(player);check(cries[0]==0,"Electric Cherry does not play zombie cries on existing corpses");dead.discard();
            var lethal=zombie(level,player.position().add(1,0,0));lethal.getPersistentData().putBoolean("zombierool:no_gore",true);lethal.setHealth(1);
            CherryDischarge.trigger(player);check(!lethal.isAlive()&&cries[0]==0,"A zombie killed by Electric Cherry does not scream after death");lethal.discard();
            var survivor=zombie(level,player.position().add(1,0,0));survivor.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000000);survivor.setHealth(1000000);
            player.getInventory().setItem(player.getInventory().selected,new ItemStack(Items.STICK));
            CherryDischarge.trigger(player);check(survivor.isAlive()&&cries[0]==1,"A living zombie hit by Electric Cherry still plays its electric reaction");survivor.discard();
        }finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);player.removeEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get());}
    }
}
