package local.hotfixtest;
import java.util.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.init.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

public final class Oct14RedistributionTest {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("OCT14 REDISTRIBUTION CHECK: "+message);}
    private static void awake(AbstractZombieRoolEntity mob){for(int i=0;i<SpawnEmergence.DURATION;i++)SpawnEmergence.tick(new LivingEvent.LivingTickEvent(mob));mob.setOnGround(true);}
    public static void run(ServerLevel level,ServerPlayer original)throws Exception {
        var pos=new BlockPos(1408,80,0);var config=WorldConfig.get(level);int oldLimit=config.getMaxPerks();long time=level.getGameTime();
        var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);boolean wasRunning=running.getBoolean(null);
        var wave=WaveManager.class.getDeclaredField("currentWave");wave.setAccessible(true);int oldWave=wave.getInt(null);
        var state=WaveManager.class.getDeclaredField("currentState");state.setAccessible(true);Object oldState=state.get(null);
        var remaining=WaveManager.class.getDeclaredField("zombiesToKill");remaining.setAccessible(true);int oldRemaining=remaining.getInt(null);
        var mobsField=WaveManager.class.getDeclaredField("activeMobs");mobsField.setAccessible(true);var active=(Set<UUID>)mobsField.get(null);var oldActive=new HashSet<>(active);
        var enabled=new HashSet<>(WaveManager.UNLOCKED_CHANNELS);var disabled=new HashSet<>(WaveManager.LOCKED_CHANNELS);
        var player=new net.minecraftforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"JourneyProbe"));level.addNewPlayer(player);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)managerField.get(level);
        for(int x=87;x<=91;x++)for(int z=-1;z<=0;z++){level.getChunk(x,z);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(x,z),FullChunkStatus.ENTITY_TICKING);}
        for(int x=-1;x<=32;x++)for(int z=-2;z<=2;z++){level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);level.setBlock(pos.offset(x,-2,z),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),3);for(int y=0;y<=3;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        try{
            running.setBoolean(null,true);wave.setInt(null,20);remaining.setInt(null,1);active.clear();
            Object combat=Arrays.stream(state.getType().getEnumConstants()).filter(e->e.toString().equals("WAITING_CLEAR")).findFirst().orElseThrow();state.set(null,combat);
            WaveManager.UNLOCKED_CHANNELS.clear();WaveManager.UNLOCKED_CHANNELS.addAll(Set.of(0,100));WaveManager.LOCKED_CHANNELS.clear();
            for(int offset:new int[]{0,4,12}){
                level.setBlock(pos.offset(offset,0,0),UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState(),3);
                var spawner=(UniversalSpawnerSystem.UniversalSpawnerBlockEntity)level.getBlockEntity(pos.offset(offset,0,0));
                spawner.setConfig(offset==0?UniversalSpawnerSystem.SpawnerMobType.CRAWLER:UniversalSpawnerSystem.SpawnerMobType.ZOMBIE,"",offset==0?"0":offset==4?"100":"1","",false,1);
            }
            var jump=pos.offset(24,0,0);level.setBlock(jump,TraversalSystem.JUMP.get().defaultBlockState(),3);var anchor=(TraversalSystem.Anchor)level.getBlockEntity(jump);anchor.exit=jump.offset(3,0,0);anchor.duration=20;
            var crawler=ZombieroolModEntities.CRAWLER.get().create(level);crawler.setPos(Vec3.atBottomCenterOf(pos));level.addFreshEntity(crawler);awake(crawler);active.add(crawler.getUUID());SpawnRedistribution.track(crawler,level,pos);
            TraversalMotion.start(crawler,anchor);check(TraversalMotion.active(crawler)&&crawler.isNoAi(),"The counted crawler is temporarily AI-locked mid-jump");
            var boss=ZombieroolModEntities.BRUTUS.get().create(level);boss.getPersistentData().putBoolean("zr_special_spawner",true);boss.setPos(Vec3.atBottomCenterOf(pos.offset(4,0,0)));level.addFreshEntity(boss);awake(boss);SpawnRedistribution.track(boss,level,pos.offset(4,0,0));
            var corpse=ZombieroolModEntities.ZOMBIE.get().create(level);corpse.setPos(Vec3.atBottomCenterOf(pos.offset(6,0,0)));corpse.setHealth(0);level.addFreshEntity(corpse);
            check(DebugConsole.lastEnemy(level)==crawler,"tp_last ignores the corpse and prioritizes the counted crawler over the boss");
            player.absMoveTo(pos.getX()+18.5,pos.getY(),.5,0,0);config.setMaxPerks(-1);
            for(var perk:PerksManager.ALL_PERKS.values())if(!perk.getId().equals("royal_beer"))perk.applyEffect(player);
            var actionsField=me.cryo.zombierool.bonuses.BonusManager.class.getDeclaredField("ACTIONS");actionsField.setAccessible(true);
            var actions=(Map<String,me.cryo.zombierool.bonuses.BonusManager.BonusAction>)actionsField.get(null);
            actions.get("on_the_house").apply(player,level,player.position(),me.cryo.zombierool.bonuses.BonusManager.getBonus("on_the_house"));
            var beer=ZombieroolModMobEffects.PERKS_EFFECT_ROYAL_BEER.get();check(player.hasEffect(beer),"On the House grants Royal Beer in the wave-20 scenario");beer.applyEffectTick(player,0);
            check(level.getEntitiesOfClass(WhiteKnightEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(32),e->e.getOwner()==player).size()==1,"Royal Beer creates one actual owned guardian");
            var settings=new CompoundTag();settings.putString("enableChannels","1");settings.putString("disableChannels","0,100");settings.putInt("channelDuration",2);settings.putBoolean("redistributeMobs",true);SpawnChannelActions.apply(level,settings);
            check(!TraversalMotion.active(crawler)&&SpawnRedistribution.pending(level),"Redistribution releases the active jump before saving enemies");
            for(int i=0;i<4&&SpawnRedistribution.pending(level);i++){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+10);SpawnRedistribution.tick(level);}
            var restored=level.getEntitiesOfClass(CrawlerEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(20),e->e.isAlive()).stream().filter(e->active.contains(e.getUUID())).findFirst().orElseThrow();awake(restored);
            check(!restored.isNoAi()&&!restored.getPersistentData().contains("zr_traversal_restore")&&!restored.getPersistentData().contains("zr_emerge_no_ai"),"A redistributed crawler regains AI after its emergence");
            check(restored.position().distanceToSqr(Vec3.atBottomCenterOf(pos.offset(12,0,0)))<1,"Crawler appears at channel 1 instead of snapping to its old jump start");
            var path=restored.getNavigation().createPath(player,0);System.out.println("REDISTRIBUTION PATH PROBE: mob="+restored.position()+" box="+restored.getBoundingBox()+" player="+player.position()+" onGround="+restored.onGround()+" path="+path+" end="+(path==null?null:path.getEndNode())+" reach="+(path!=null&&path.canReach()));check(RouteNavigation.reachesPlayer(path,player),"The remaining crawler has a usable route to the player");
            check(((RouteNavigation)restored.getNavigation()).lastPlanningRange()<256,"A normal chase uses the smaller navigation region");
            check(remaining.getInt(null)==1&&active.size()==1,"Redistribution preserves the last-enemy wave quota");
            level.getServer().getWorldData().overworldData().setGameTime(time+41);SpawnChannelActions.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,level.getServer()));
            for(int i=0;i<4&&SpawnRedistribution.pending(level);i++){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+10);SpawnRedistribution.tick(level);}
            var returned=level.getEntitiesOfClass(CrawlerEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(20),e->e.isAlive()).stream().filter(e->active.contains(e.getUUID())).findFirst().orElseThrow();awake(returned);
            check(!returned.isNoAi()&&returned.position().distanceToSqr(Vec3.atBottomCenterOf(pos))<1,"The programmed channel return restores an active crawler at the original spawner");
            check(WaveManager.UNLOCKED_CHANNELS.containsAll(Set.of(0,100))&&!WaveManager.UNLOCKED_CHANNELS.contains(1),"The channel lease restores 0 and 100 and disables 1");
        }finally{
            for(var mob:level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,new net.minecraft.world.phys.AABB(pos.offset(-2,-2,-3),pos.offset(34,8,4)))){TraversalMotion.cancel(mob);mob.discard();}
            level.removePlayerImmediately(player,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);SpawnChannelActions.reset();
            config.setMaxPerks(oldLimit);running.setBoolean(null,wasRunning);wave.setInt(null,oldWave);state.set(null,oldState);remaining.setInt(null,oldRemaining);active.clear();active.addAll(oldActive);
            WaveManager.UNLOCKED_CHANNELS.clear();WaveManager.UNLOCKED_CHANNELS.addAll(enabled);WaveManager.LOCKED_CHANNELS.clear();WaveManager.LOCKED_CHANNELS.addAll(disabled);level.getServer().getWorldData().overworldData().setGameTime(time);
            for(int x=-1;x<=32;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=3;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        System.out.println("OCT14 REDISTRIBUTION PASS: "+checks+" checks");
    }
}
