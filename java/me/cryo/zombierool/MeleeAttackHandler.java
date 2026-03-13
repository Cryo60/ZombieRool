package me.cryo.zombierool.procedures;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.ScreenShakePacket;
import me.cryo.zombierool.PointManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.client.ScreenShakeHandler;
import org.joml.Vector3f;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;
import me.cryo.zombierool.util.PlayerVoiceManager;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeleeAttackHandler {
	private static final ResourceLocation KNIFE_SLASH_BODY_SOUND = new ResourceLocation("zombierool", "knife_slash_body");
	private static final ResourceLocation KNIFE_HIT_MISC_MATERIAL_SOUND = new ResourceLocation("zombierool", "knife_hit_misc_material");
	private static final ConcurrentHashMap<java.util.UUID, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();
	private static final int COOLDOWN_TICKS = 10;
	private static final double ATTACK_RANGE = 3.0; 
	@SubscribeEvent
	public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
	    COOLDOWN_MAP.remove(event.getEntity().getUUID());
	}
	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
	    COOLDOWN_MAP.remove(event.getEntity().getUUID());
	}
    private static boolean isBlockedByWall(Level level, Vec3 start, Vec3 end) {
        BlockHitResult result = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (result.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(result.getBlockPos());
            if (state.getBlock() instanceof DefenseDoorSystem.BaseDefenseDoor || state.getBlock() instanceof me.cryo.zombierool.block.ObstacleDoorBlock) {
                Vec3 dir = end.subtract(start).normalize();
                Vec3 newStart = result.getLocation().add(dir.scale(0.1));
                if (newStart.distanceToSqr(end) < 0.01) return false;
                return isBlockedByWall(level, newStart, end);
            }
            return true;
        }
        return false;
    }
	public static void performMeleeAttack(Player player) {
	    if (player.level().isClientSide()) {
	        return;
	    }
	    long currentTime = player.level().getGameTime();
	    if (COOLDOWN_MAP.containsKey(player.getUUID()) && currentTime - COOLDOWN_MAP.get(player.getUUID()) < COOLDOWN_TICKS) {
	        return;
	    }
	    ServerLevel level = (ServerLevel) player.level();
	    Vec3 lookVec = player.getLookAngle();
	    Vec3 startVec = player.getEyePosition(1.0F);
	    Vec3 endVec = startVec.add(lookVec.scale(ATTACK_RANGE));
	    LivingEntity targetEntity = null;
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(ATTACK_RANGE)).inflate(1.0);
        List<LivingEntity> entitiesInArea = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != player && !entity.isSpectator() && !entity.isDeadOrDying() && !(entity instanceof Player)
        );
        double minDistance = Double.MAX_VALUE;
        for (LivingEntity e : entitiesInArea) {
            AABB bbox = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> clip = bbox.clip(startVec, endVec);
            if (clip.isPresent() || bbox.contains(startVec)) {
                double dist = startVec.distanceTo(clip.orElse(startVec));
                if (dist < minDistance) {
                    if (!isBlockedByWall(level, startVec, clip.orElse(startVec))) {
                        targetEntity = e;
                        minDistance = dist;
                    }
                }
            }
        }
	    if (targetEntity != null) {
	        int currentWave = Math.max(1, WaveManager.getCurrentWave());
            targetEntity.getPersistentData().remove(DamageManager.GUN_DAMAGE_TAG);
            targetEntity.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
            targetEntity.getPersistentData().remove("zombierool:explosive_damage");
	        float finalDamage = (targetEntity.getMaxHealth() / currentWave) + 0.01f;
            if (me.cryo.zombierool.bonuses.BonusManager.isInstaKillActive(player)) {
                finalDamage = 100000f;
            }
	        DamageManager.applyDamage(targetEntity, player.damageSources().mobAttack(player), finalDamage);
	        playLocalSound(player, KNIFE_SLASH_BODY_SOUND, 1.0f, 1.0f);
	        spawnAttackParticles(level, targetEntity.position().add(0, targetEntity.getBbHeight() / 2, 0), lookVec);
	        if (player instanceof ServerPlayer serverPlayer) {
	            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ScreenShakePacket(10, 0.3f, ScreenShakeHandler.ShakeType.MELEE));
	        }
	        PlayerVoiceManager.playMeleeAttackSound(player, level);
	        COOLDOWN_MAP.put(player.getUUID(), currentTime);
	    } else {
            BlockHitResult blockHit = level.clip(new ClipContext(startVec, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = level.getBlockState(blockHit.getBlockPos());
                if (!(state.getBlock() instanceof DefenseDoorSystem.BaseDefenseDoor) && !(state.getBlock() instanceof me.cryo.zombierool.block.ObstacleDoorBlock)) {
                    playLocalSound(player, KNIFE_HIT_MISC_MATERIAL_SOUND, 1.0f, 1.0f);
                    spawnBlockHitParticles(level, blockHit.getLocation());
                }
            }
	        PlayerVoiceManager.playMeleeAttackSound(player, level);
	        COOLDOWN_MAP.put(player.getUUID(), currentTime);
	    }
	}
	private static void playLocalSound(Player player, ResourceLocation soundRes, float volume, float pitch) {
	    SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundRes);
	    if (sound == null) {
	        return;
	    }
	    player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
	}
	private static void spawnAttackParticles(ServerLevel level, Vec3 pos, Vec3 direction) {
	    level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.0F), pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.1);
	    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.05);
	}
	private static void spawnBlockHitParticles(ServerLevel level, Vec3 pos) {
	    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
	            pos.x, pos.y, pos.z, 5, 0.1, 0.1, 0.1, 0.05);
	}
}