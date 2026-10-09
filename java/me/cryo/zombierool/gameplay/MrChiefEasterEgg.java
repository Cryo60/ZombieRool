package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.config.MrChiefConfig;
import me.cryo.zombierool.entity.MrChiefEntity;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class MrChiefEasterEgg {
    // Don't trust Mr.Chief. He may have betrayed the Burning Legion...
    private static final Map<ServerLevel,UUID> queued = new WeakHashMap<>();
    private static final Set<ServerLevel> due = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerLevel> naturallyEncountered = Collections.newSetFromMap(new WeakHashMap<>());
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide() && event.getEntity() instanceof MrChiefEntity cat && !cat.isSpawnAuthorized())event.setCanceled(true);
    }
    private static final Map<ServerLevel,Integer> lastWave = new WeakHashMap<>();
    public static void onWaveStart(ServerLevel level,int wave) {
        if(Objects.equals(lastWave.put(level,wave),wave))return;
        if(!WaveManager.isGameRunning())return;
        if(queued.containsKey(level)) {
            due.add(level); clearEncounter(level); tryQueued(level); return;
        }
        var config=MrChiefConfig.get(level);
        if(!config.enabled || naturallyEncountered.contains(level) || !cats(level).isEmpty()
                || level.random.nextInt(Math.max(MrChiefConfig.MIN_NATURAL_CHANCE,config.chance))!=0)return;
        if(spawnAtSpawner(level,false)!=null)naturallyEncountered.add(level);
    }
    public static void queueNextWave(ServerPlayer player) {
        // Match waves run in the overworld; a pre-match request survives the initial reset.
        queued.put(player.server.overworld(),player.getUUID());
    }
    public static void queueNextWave(ServerLevel level) {queued.put(level.getServer().overworld(),new UUID(0,0));}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void retry(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if(event.phase==net.minecraftforge.event.TickEvent.Phase.END && event.level instanceof ServerLevel level
                && due.contains(level) && WaveManager.isGameRunning() && !WaveManager.isPausedByPlayer())tryQueued(level);
    }
    private static void tryQueued(ServerLevel level) {
        if(!queued.containsKey(level)){due.remove(level);return;}
        var chief=spawnAtSpawner(level,true);
        if(chief==null) {
            UUID requested=queued.get(level);
            var players=new ArrayList<>(level.players());
            players.sort(Comparator.comparing(p->!p.getUUID().equals(requested)));
            for(var player:players) {
                if(!player.isAlive() || player.isSpectator())continue;
                chief=findNear(level,player.blockPosition(),true);
                // Exact player coordinates also work in narrow passages and on stairs.
                if(chief==null)chief=spawnAt(level,player.position(),true);
                if(chief!=null)break;
            }
        }
        if(chief!=null){queued.remove(level);due.remove(level);WaveManager.setCheatsUsed(true);}
    }
    private static MrChiefEntity spawnAtSpawner(ServerLevel level,boolean forced) {
        var candidates=SpawnerRegistry.getSpawners(level).stream().filter(be -> be.isActive(level) && switch(be.getMobType()) {
            case PLAYER, HELLHOUND, CRAWLER -> true;default -> false;
        }).toList();
        if(candidates.isEmpty())return null;
        int first=level.random.nextInt(candidates.size());
        for(int i=0;i<candidates.size();i++) {
            var chief=findNear(level,candidates.get((first+i)%candidates.size()).getBlockPos().above(),forced);
            if(chief!=null)return chief;
        }
        return null;
    }
    private static MrChiefEntity findNear(ServerLevel level,BlockPos origin,boolean forced) {
        for(int radius=0;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int dy=2;dy>=-3;dy--) {
                BlockPos pos=origin.offset(dx,dy,dz);
                if(!level.hasChunkAt(pos) || level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())continue;
                var chief=spawn(level,pos,forced);if(chief!=null)return chief;
            }
        }
        return null;
    }
    private static List<? extends MrChiefEntity> cats(ServerLevel level) { return level.getEntities(ZombieroolModEntities.MR_CHIEF.get(),e->!e.isRemoved()); }
    public static MrChiefEntity spawn(ServerLevel level,BlockPos pos,boolean forced) {
        return spawnAt(level,Vec3.atBottomCenterOf(pos),forced);
    }
    private static MrChiefEntity spawnAt(ServerLevel level,Vec3 position,boolean forced) {
        BlockPos pos=BlockPos.containing(position);
        if(!level.isInWorldBounds(pos) || !level.hasChunkAt(pos) || !cats(level).isEmpty())return null;
        var cat=ZombieroolModEntities.MR_CHIEF.get().create(level);if(cat==null)return null;
        cat.configure(MrChiefConfig.get(level),forced);
        cat.moveTo(position.x,position.y,position.z,level.random.nextFloat()*360,0);
        // Mobs sharing a spawner must not prevent this encounter; test blocks, not entities.
        if(level.getBlockCollisions(cat,cat.getBoundingBox()).iterator().hasNext() || !level.addFreshEntity(cat))return null;
        for(var player:level.players()) {
            player.sendSystemMessage(Component.translatable("message.zombierool.mr_chief.appeared").withStyle(net.minecraft.ChatFormatting.DARK_AQUA,net.minecraft.ChatFormatting.ITALIC));
        }
        LuaScriptManager.callEvent("OnMrChiefSpawned",cat.getUUID().toString(),cat.getX(),cat.getY(),cat.getZ(),forced);
        return cat;
    }
    private static void clearEncounter(ServerLevel level){for(var cat:cats(level)){cat.stopPartySound();cat.discard();}}
    public static void reset(ServerLevel level) {lastWave.remove(level);due.remove(level);naturallyEncountered.remove(level);clearEncounter(level);}
}
