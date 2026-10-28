package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.MapActorEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Map-owned shootable props count impacts instead of weapon damage. Never alter blocks. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class MapExplosiveProps {
    private static final String AUTHORED="zr_authored_prop";
    public static boolean authored(Entity entity){return entity instanceof MapActorEntity actor&&active(actor)&&actor.getPersistentData().getBoolean(AUTHORED);}
    public static final class Placements extends net.minecraft.world.level.saveddata.SavedData {
        CompoundTag entries=new CompoundTag();long revision;
        static Placements load(CompoundTag n){var d=new Placements();d.entries=n.getCompound("entries");d.revision=n.getLong("revision");return d;}
        @Override public CompoundTag save(CompoundTag n){n.put("entries",entries);n.putLong("revision",revision);return n;}
    }
    private static Placements placements(ServerLevel level){return level.getDataStorage().computeIfAbsent(Placements::load,Placements::new,"zombierool_explosive_props");}
    private static void reset(MapActorEntity actor,CompoundTag row,long revision){
        var profile=MapActors.get((ServerLevel)actor.level()).profile(row.getString("profile"));if(profile.isEmpty())profile=actor.profile();
        actor.configure(profile,new Vec3(row.getDouble("x"),row.getDouble("y"),row.getDouble("z")),"");
        actor.moveTo(row.getDouble("x"),row.getDouble("y"),row.getDouble("z"),row.getFloat("yaw"),0);
        var tag=actor.getPersistentData();for(String key:java.util.List.of("zr_prop_hits","zr_prop_threshold","zr_prop_fuse","zr_prop_burning","zr_prop_owner"))tag.remove(key);
        tag.putBoolean(AUTHORED,true);tag.putBoolean("matchActor",false);actor.clearFire();actor.setHealth(actor.getMaxHealth());actor.setNoAi(true);BULLETS.remove(actor);row.putLong("spawned",revision);
    }
    @SubscribeEvent public static void placed(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof MapActorEntity actor)||!active(actor))return;
        var data=placements(level);String id=actor.getUUID().toString();
        if(!WaveManager.isGameRunning()&&!actor.getPersistentData().getBoolean("matchActor")&&actor.marker().isEmpty()&&!data.entries.contains(id)){
            var row=new CompoundTag();row.putString("profile",actor.profile().getString("id"));row.putDouble("x",actor.getX());row.putDouble("y",actor.getY());row.putDouble("z",actor.getZ());row.putFloat("yaw",actor.getYRot());row.putLong("spawned",data.revision);data.entries.put(id,row);actor.getPersistentData().putBoolean(AUTHORED,true);data.setDirty();
        }
        if(authored(actor)&&data.entries.contains(id)){var row=data.entries.getCompound(id);if(row.getLong("spawned")!=data.revision){reset(actor,row,data.revision);data.setDirty();}}
    }
    public static void begin(ServerLevel level){
        var data=placements(level);data.revision++;data.setDirty();
        for(var entity:level.getAllEntities())if(authored(entity)){var row=data.entries.getCompound(entity.getUUID().toString());if(!row.isEmpty())reset((MapActorEntity)entity,row,data.revision);}
        restore(level);
    }
    private static void restore(ServerLevel level){
        var data=placements(level);int remaining=16;
        for(String id:data.entries.getAllKeys()){var row=data.entries.getCompound(id);if(row.getLong("spawned")==data.revision||!level.hasChunkAt(net.minecraft.core.BlockPos.containing(row.getDouble("x"),row.getDouble("y"),row.getDouble("z"))))continue;
            var uuid=java.util.UUID.fromString(id);if(level.getEntity(uuid)!=null)continue;var profile=MapActors.get(level).profile(row.getString("profile"));if(profile.isEmpty())continue;
            var actor=me.cryo.zombierool.init.ZombieroolModEntities.MAP_ACTOR.get().create(level);if(actor==null)continue;actor.setUUID(uuid);reset(actor,row,data.revision);if(!level.addFreshEntity(actor)){row.putLong("spawned",data.revision-1);continue;}data.setDirty();if(--remaining==0)break;
        }
    }
    @SubscribeEvent public static void restoreLoaded(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&event.level instanceof ServerLevel level&&WaveManager.isGameRunning()&&level.getGameTime()%20==0)restore(level);}
    @SubscribeEvent public static void removedInEditor(net.minecraftforge.event.entity.living.LivingDeathEvent event){if(!WaveManager.isGameRunning()&&event.getEntity().level() instanceof ServerLevel level&&authored(event.getEntity())){var d=placements(level);d.entries.remove(event.getEntity().getUUID().toString());d.setDirty();}}
    private static final java.util.Map<MapActorEntity,java.util.Set<java.util.UUID>> BULLETS=new java.util.WeakHashMap<>();
    public static boolean active(MapActorEntity actor){return actor.profile().contains("explosiveProp",10);}
    public static CompoundTag settings(CompoundTag input){
        var n=new CompoundTag();int min=Math.max(1,Math.min(100,input.getInt("minHits")));
        n.putInt("minHits",min);n.putInt("maxHits",Math.max(min,Math.min(100,input.getInt("maxHits"))));
        n.putInt("fuseTicks",Math.max(10,Math.min(1200,input.getInt("fuseTicks"))));
        n.putDouble("radius",Math.max(1,Math.min(16,input.getDouble("radius"))));
        n.putDouble("damage",Math.max(1,Math.min(10000,input.getDouble("damage"))));return n;
    }
    public static boolean hit(MapActorEntity actor,Entity bullet,Entity shooter){
        if(!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||!active(actor)||MatchPause.frozen())return false;
        var data=actor.getPersistentData();
        if(bullet!=null){var seen=BULLETS.computeIfAbsent(actor,a->new java.util.LinkedHashSet<>());if(!seen.add(bullet.getUUID()))return true;if(seen.size()>64)seen.remove(seen.iterator().next());}
        var cfg=actor.profile().getCompound("explosiveProp");
        if(!data.contains("zr_prop_threshold"))data.putInt("zr_prop_threshold",cfg.getInt("minHits")+level.random.nextInt(cfg.getInt("maxHits")-cfg.getInt("minHits")+1));
        int hits=data.getInt("zr_prop_hits")+1;data.putInt("zr_prop_hits",hits);
        if(shooter instanceof Player)data.putUUID("zr_prop_owner",shooter.getUUID());
        actor.setHealth(Math.max(1,actor.getMaxHealth()*(1-(float)hits/data.getInt("zr_prop_threshold"))));
        level.playSound(null,actor.blockPosition(),net.minecraft.sounds.SoundEvents.ANVIL_HIT,net.minecraft.sounds.SoundSource.BLOCKS,.2f,1.8f);
        if(hits>=(data.getInt("zr_prop_threshold")+1)/2){data.putBoolean("zr_prop_burning",true);actor.setSecondsOnFire(2);}
        if(hits>=data.getInt("zr_prop_threshold"))explode(actor,level);
        return true;
    }
    @SubscribeEvent public static void tick(LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof MapActorEntity actor)||!(actor.level() instanceof ServerLevel level)||!actor.isAlive()||!active(actor)||MatchPause.frozen())return;
        actor.setNoAi(true);var data=actor.getPersistentData();
        // Legacy timed fuses become a burning, still shootable prop on upgrade.
        if(data.contains("zr_prop_fuse")){data.remove("zr_prop_fuse");data.putBoolean("zr_prop_burning",true);}
        if(data.getBoolean("zr_prop_burning")){actor.setSecondsOnFire(2);if(actor.tickCount%5==0)level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,actor.getX(),actor.getY()+actor.getBbHeight(),actor.getZ(),2,.18,.1,.18,.01);}
    }
    private static void explode(MapActorEntity actor,ServerLevel level){
        var data=actor.getPersistentData();
        var cfg=actor.profile().getCompound("explosiveProp");double radius=cfg.getDouble("radius");Vec3 center=actor.getBoundingBox().getCenter();
        Entity owner=data.hasUUID("zr_prop_owner")?level.getEntity(data.getUUID("zr_prop_owner")):null;
        actor.discard();
        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.NEAR.with(()->new net.minecraftforge.network.PacketDistributor.TargetPoint(center.x,center.y,center.z,96,level.dimension())),new me.cryo.zombierool.network.packet.S2CWeaponVfxPacket("EXPLOSION",center,center,false,false));
        var sound=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool","explosion_old"));
        if(sound!=null)level.playSound(null,center.x,center.y,center.z,sound,net.minecraft.sounds.SoundSource.BLOCKS,2,1);
        for(var target:level.getEntitiesOfClass(LivingEntity.class,actor.getBoundingBox().inflate(radius),LivingEntity::isAlive)){
            double distance=target.getBoundingBox().getCenter().distanceTo(center);if(distance>radius)continue;
            if(level.clip(new net.minecraft.world.level.ClipContext(center,target.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,target)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            float falloff=(float)Math.max(.2,1-distance/radius);
            if(target instanceof Player player){if(!player.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()))FixedPlayerDamage.apply(player,player.getMaxHealth()*.5f*falloff);}
            else me.cryo.zombierool.core.manager.DamageManager.applyDamage(target,level.damageSources().explosion(actor,owner),(float)cfg.getDouble("damage")*falloff);
        }
    }
    private MapExplosiveProps(){}
}
