package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlockEntity;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class BrutusMachineLocks extends SavedData {
    private final Set<BlockPos> locked=new HashSet<>();
    public static BrutusMachineLocks get(ServerLevel level){return level.getDataStorage().computeIfAbsent(n->{var d=new BrutusMachineLocks();for(long p:n.getLongArray("positions"))d.locked.add(BlockPos.of(p));return d;},BrutusMachineLocks::new,"zombierool_brutus_locks");}
    @Override public CompoundTag save(CompoundTag n){n.putLongArray("positions",locked.stream().mapToLong(BlockPos::asLong).toArray());return n;}
    public static boolean isMachine(net.minecraft.world.level.block.entity.BlockEntity be){return be instanceof PerksAColaBlockEntity||be instanceof MysteryBoxBlockEntity;}
    private static CompoundTag snapshot(ServerLevel level){var n=get(level).save(new CompoundTag());n.putInt("unlockCost",me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getInt("brutusUnlockCost"));return n;}
    private static boolean eligible(net.minecraft.world.level.block.entity.BlockEntity be){if(be instanceof PerksAColaBlockEntity perk)return perk.isPowered();if(be instanceof MysteryBoxBlockEntity box)return box.getBlockState().getValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.ACTIVE)&&box.getBoxState()==0;return false;}
    public static boolean locked(ServerLevel level,BlockPos pos){return get(level).locked.contains(pos);}
    public static boolean lock(ServerLevel level,BlockPos pos){if(!isMachine(level.getBlockEntity(pos))||!get(level).locked.add(pos.immutable()))return false;get(level).setDirty();sync(level);me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnBrutusMachineLocked",pos.getX(),pos.getY(),pos.getZ());return true;}
    public static BlockPos machineRoot(net.minecraft.world.level.Level level,BlockPos pos){
        var state=level.getBlockState(pos);
        if(state.getBlock() instanceof me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock)
            return state.getValue(me.cryo.zombierool.block.system.PerksSystem.PerksAColaDummyBlock.PART)==me.cryo.zombierool.block.system.PerksSystem.DummyPart.LOWER?pos.above():pos.below();
        if(state.getBlock() instanceof me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock&&state.getValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.PART))
            return MysteryBoxManager.getOtherPartPos(pos,state.getValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING).getOpposite());
        return pos;
    }
    private static final class Unlock {
        final ServerLevel level;final BlockPos pos;final int duration;long lastHeld;int elapsed;
        Unlock(ServerPlayer p,BlockPos pos){level=p.serverLevel();this.pos=pos.immutable();duration=me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getInt("brutusUnlockTicks");lastHeld=level.getGameTime();}
    }
    private static final Map<UUID,Unlock> UNLOCKS=new HashMap<>();
    private static void progress(ServerPlayer player,Unlock action){
        var n=new CompoundTag();n.putBoolean("unlockAction",true);n.putInt("elapsed",action==null?-1:action.elapsed);n.putInt("duration",action==null?0:action.duration);if(action!=null)n.putLong("position",action.pos.asLong());
        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new me.cryo.zombierool.network.packet.S2CBrutusLocksPacket(n));
    }
    public static void cancelUnlock(ServerPlayer player){if(UNLOCKS.remove(player.getUUID())!=null){progress(player,null);EquipmentSystem.animate(player,"knuckle_cancel",0);}}
    public static boolean validUnlock(ServerPlayer player,BlockPos pos){
        if(!player.isAlive()||player.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())||!locked(player.serverLevel(),pos)||!isMachine(player.level().getBlockEntity(pos)))return false;
        var centre=net.minecraft.world.phys.Vec3.atCenterOf(pos);var eye=player.getEyePosition();
        if(eye.distanceToSqr(centre)>9)return false;
        var hit=player.level().clip(new net.minecraft.world.level.ClipContext(eye,centre,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS||machineRoot(player.level(),hit.getBlockPos()).equals(pos);
    }
    public static void holdUnlock(ServerPlayer player,BlockPos pos){
        pos=machineRoot(player.level(),pos);
        if(!validUnlock(player,pos)){cancelUnlock(player);return;}
        int cost=me.cryo.zombierool.config.SpecialEnemyConfig.get(player.serverLevel()).getInt("brutusUnlockCost");
        if(!player.isCreative()&&PointManager.getScore(player)<cost){cancelUnlock(player);player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.brutus.locked",cost),true);return;}
        Unlock action=UNLOCKS.get(player.getUUID());
        if(action==null||action.level!=player.level()||!action.pos.equals(pos)){cancelUnlock(player);action=new Unlock(player,pos);UNLOCKS.put(player.getUUID(),action);progress(player,action);EquipmentSystem.animate(player,"knuckle_crack",40);}
        action.lastHeld=player.level().getGameTime();
    }
    public static void tickUnlocks(net.minecraft.server.MinecraftServer server){
        for(var id:List.copyOf(UNLOCKS.keySet())){
            var player=server.getPlayerList().getPlayer(id);var action=UNLOCKS.get(id);
            if(player==null){UNLOCKS.remove(id);continue;}
            tickUnlock(player);
        }
    }
    public static void tickUnlock(ServerPlayer player){
        var action=UNLOCKS.get(player.getUUID());if(action==null)return;
        int cost=me.cryo.zombierool.config.SpecialEnemyConfig.get(action.level).getInt("brutusUnlockCost");
        if(player.level()!=action.level||action.level.getGameTime()-action.lastHeld>10||!validUnlock(player,action.pos)||!player.isCreative()&&PointManager.getScore(player)<cost){cancelUnlock(player);return;}
        action.elapsed++;
        if(action.elapsed>=action.duration){interact(player,action.pos);cancelUnlock(player);}
        else progress(player,action);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void unlockTick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END)tickUnlocks(e.getServer());}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)cancelUnlock(p);}
    public static void reset(ServerLevel level){for(var id:List.copyOf(UNLOCKS.keySet()))if(UNLOCKS.get(id).level==level){var p=level.getServer().getPlayerList().getPlayer(id);if(p!=null)cancelUnlock(p);else UNLOCKS.remove(id);}get(level).locked.clear();get(level).setDirty();sync(level);}
    public static void sync(ServerLevel level){me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension),new me.cryo.zombierool.network.packet.S2CBrutusLocksPacket(snapshot(level)));}
    /** Completes a server-validated hold; purchase requires a subsequent interaction. */
    private static boolean interact(ServerPlayer player,BlockPos pos){var level=player.serverLevel();if(!locked(level,pos))return false;int cost=me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getInt("brutusUnlockCost");if(!player.isCreative()&&PointManager.getScore(player)<cost){player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.brutus.locked",cost),true);return true;}if(!player.isCreative())PointManager.modifyScore(player,-cost,false);get(level).locked.remove(pos);get(level).setDirty();sync(level);level.playSound(null,pos,net.minecraft.sounds.SoundEvents.CHAIN_BREAK,net.minecraft.sounds.SoundSource.BLOCKS,1,1);player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.brutus.unlocked",cost),true);me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnBrutusMachineUnlocked",player.getUUID().toString(),pos.getX(),pos.getY(),pos.getZ());return true;}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)send(p);}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)send(p);}
    private static void send(ServerPlayer p){me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new me.cryo.zombierool.network.packet.S2CBrutusLocksPacket(snapshot(p.serverLevel())));}
    public static final class LockGoal extends Goal {
        private final SpecialEnemyEntity boss;
        private final Map<BlockPos,Long> rejected=new HashMap<>();
        private BlockPos target;
        private int ticks,timeout;
        private long nextSearch;
        private static final int DETECTION_RADIUS=6;
        private boolean completed;
        public LockGoal(SpecialEnemyEntity boss){this.boss=boss;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        private boolean canStrike(net.minecraft.world.phys.Vec3 feet,BlockPos machine){
            var parts=new ArrayList<BlockPos>();parts.add(machine);
            var state=boss.level().getBlockState(machine);
            if(state.getBlock() instanceof me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlock){parts.add(machine.below());parts.add(machine.above());}
            if(state.getBlock() instanceof me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock)
                parts.add(MysteryBoxManager.getOtherPartPos(machine,state.getValue(me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock.FACING)));
            var hand=feet.add(0,1.3,0);
            for(var part:parts){
                if(!machineRoot(boss.level(),part).equals(machine))continue;
                var centre=net.minecraft.world.phys.Vec3.atCenterOf(part);if(hand.distanceToSqr(centre)>6.25)continue;
                var hit=boss.level().clip(new net.minecraft.world.level.ClipContext(hand,centre,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,boss));
                if(hit.getType()==net.minecraft.world.phys.HitResult.Type.MISS||machineRoot(boss.level(),hit.getBlockPos()).equals(machine))return true;
            }
            return false;
        }
        private net.minecraft.world.entity.player.Player prey(){
            return boss.level().players().stream().filter(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())&&!me.cryo.zombierool.bonuses.BonusManager.isZombieBloodActive(p))
                .min(Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        }
        private boolean nearbyOpportunity(BlockPos machine){
            if(machine.distToCenterSqr(boss.position())>DETECTION_RADIUS*DETECTION_RADIUS||Math.abs(machine.getY()-boss.getY())>2)return false;
            var centre=net.minecraft.world.phys.Vec3.atCenterOf(machine);
            var player=prey();if(player==null)return false;
            var toMachine=centre.subtract(boss.position()).multiply(1,0,1);var toPlayer=player.position().subtract(boss.position()).multiply(1,0,1);
            return toMachine.lengthSqr()<=4||toMachine.dot(toPlayer)>0;
        }
        @Override public boolean canUse(){
            long now=boss.level().getGameTime();
            if(!boss.onGround()&&!boss.isInWater()||now<nextSearch||!WaveManager.isGameRunning()||!boss.canPerformBossAction()||boss.isPanzer()||boss.isEncounter()||!(boss.level() instanceof ServerLevel level))return false;
            nextSearch=now+10;rejected.entrySet().removeIf(e->e.getValue()<=now);target=null;
            var machines=new ArrayList<BlockPos>();var centre=boss.blockPosition();
            for(int cx=(centre.getX()-DETECTION_RADIUS)>>4;cx<=(centre.getX()+DETECTION_RADIUS)>>4;cx++)for(int cz=(centre.getZ()-DETECTION_RADIUS)>>4;cz<=(centre.getZ()+DETECTION_RADIUS)>>4;cz++){
                var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null)continue;
                for(var be:chunk.getBlockEntities().values())if(eligible(be)&&!locked(level,be.getBlockPos())&&!rejected.containsKey(be.getBlockPos())&&nearbyOpportunity(be.getBlockPos())&&canStrike(boss.position(),be.getBlockPos()))machines.add(be.getBlockPos());
            }
            machines.sort(Comparator.comparingDouble(p->p.distToCenterSqr(boss.position())));
            if(!machines.isEmpty()){target=machines.get(0).immutable();return true;}
            return false;
        }
        @Override public void start(){ticks=0;timeout=30;completed=false;boss.getPersistentData().putBoolean("zr_boss_machine",true);boss.getNavigation().stop();}
        @Override public boolean canContinueToUse(){return timeout>0&&target!=null&&!TraversalMotion.active(boss)&&boss.canPerformBossAction()&&canStrike(boss.position(),target)&&boss.level() instanceof ServerLevel level&&!locked(level,target)&&eligible(level.getBlockEntity(target));}
        @Override public void tick(){
            if(TraversalMotion.active(boss))return;
            timeout--;var centre=net.minecraft.world.phys.Vec3.atCenterOf(target);boss.getLookControl().setLookAt(centre.x,centre.y,centre.z,30,30);
            if(!canStrike(boss.position(),target)){timeout=0;return;}
            boss.getNavigation().stop();if(ticks++==0)boss.playBossAnimation("slam",30);
            if(ticks>=24){completed=lock((ServerLevel)boss.level(),target);if(completed)boss.level().playSound(null,target,net.minecraft.sounds.SoundEvents.CHAIN_PLACE,net.minecraft.sounds.SoundSource.HOSTILE,1,.7f);timeout=0;}
        }
        @Override public void stop(){
            if(target!=null&&!completed)rejected.put(target,boss.level().getGameTime()+600);
            boss.getNavigation().stop();boss.getPersistentData().remove("zr_boss_machine");
            me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(boss);target=null;nextSearch=boss.level().getGameTime()+(completed?600:100);
        }
    }
}
