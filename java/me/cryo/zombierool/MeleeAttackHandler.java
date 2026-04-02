package me.cryo.zombierool.procedures;

import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.util.PlayerVoiceManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.S2CScreenShakePacket;
import me.cryo.zombierool.network.packet.S2CSyncThirdPersonAnimPacket;
import me.cryo.zombierool.client.ScreenShakeHandler;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.DefenseWallSystem;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.scripting.LuaScriptManager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeleeAttackHandler {

	private static final ResourceLocation KNIFE_SLASH_BODY_SOUND = new ResourceLocation("zombierool", "knife_slash_body");
	private static final ResourceLocation KNIFE_HIT_MISC_MATERIAL_SOUND = new ResourceLocation("zombierool", "knife_hit_misc_material");

	private static final ConcurrentHashMap<java.util.UUID, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();
	private static final int COOLDOWN_TICKS = 20;
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
            boolean passThrough = false;

            if (state.getBlock() instanceof DefenseDoorSystem.BaseDefenseDoor || state.getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock) {
                passThrough = true;
            } else if (state.getBlock() instanceof DefenseWallSystem.DefenseWallBlock && state.getValue(DefenseWallSystem.DefenseWallBlock.STAGE) < 7) {
                passThrough = true;
            } else if (state.getBlock() instanceof DefenseWallSystem.DefenseWallDummyBlock && state.getValue(DefenseWallSystem.DefenseWallDummyBlock.STAGE) < 7) {
                DefenseWallSystem.WallPart part = state.getValue(DefenseWallSystem.DefenseWallDummyBlock.PART);
                if (part == DefenseWallSystem.WallPart.BOTTOM_CENTER || part == DefenseWallSystem.WallPart.TOP_CENTER) {
                    passThrough = true;
                }
            }
            
            if (passThrough) {
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

        player.swing(InteractionHand.MAIN_HAND, true);
        NetworkHandler.INSTANCE.send(
            PacketDistributor.TRACKING_ENTITY.with(() -> player),
            new S2CSyncThirdPersonAnimPacket(player.getUUID(), "melee", 15)
        );

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

        Vec3 strikePos = endVec;

	    if (targetEntity != null) {
            strikePos = targetEntity.position().add(0, targetEntity.getBbHeight() / 2, 0);
            targetEntity.getPersistentData().remove(DamageManager.GUN_DAMAGE_TAG);
            targetEntity.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
            targetEntity.getPersistentData().remove("zombierool:explosive_damage");
            
            boolean hasBowie = player.getPersistentData().getBoolean("zr_has_bowie_knife");
            float multiplier = hasBowie ? 3.0f : 1.0f;
	        float finalDamage = 6.0f * multiplier;
            if (BonusManager.isInstaKillActive(player)) {
                finalDamage = 100000f;
            }

	        DamageManager.applyDamage(targetEntity, player.damageSources().mobAttack(player), finalDamage);
	        ResourceLocation slashSound = hasBowie ? new ResourceLocation("zombierool", "bowie_stab") : KNIFE_SLASH_BODY_SOUND;
	        playLocalSound(player, slashSound, 1.0f, 1.0f);
	        spawnAttackParticles(level, strikePos, lookVec);

	        if (player instanceof ServerPlayer serverPlayer) {
	            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new S2CScreenShakePacket(10, 0.3f, ScreenShakeHandler.ShakeType.MELEE));
	        }
	        PlayerVoiceManager.playMeleeAttackSound(player, level);
	        COOLDOWN_MAP.put(player.getUUID(), currentTime);

	    } else {
            BlockHitResult blockHit = level.clip(new ClipContext(startVec, endVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = level.getBlockState(blockHit.getBlockPos());
                BlockPos bPos = blockHit.getBlockPos();
                strikePos = new Vec3(bPos.getX(), bPos.getY(), bPos.getZ());
                
                if (!(state.getBlock() instanceof DefenseDoorSystem.BaseDefenseDoor) && !(state.getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock)) {
                    boolean hasBowie = player.getPersistentData().getBoolean("zr_has_bowie_knife");
                    ResourceLocation missSound = hasBowie ? new ResourceLocation("zombierool", "bowie_stab") : KNIFE_HIT_MISC_MATERIAL_SOUND;
                    playLocalSound(player, missSound, 1.0f, 1.0f);
                    spawnBlockHitParticles(level, blockHit.getLocation());
                }
            }
	        PlayerVoiceManager.playMeleeAttackSound(player, level);
	        COOLDOWN_MAP.put(player.getUUID(), currentTime);
	    }
        
        LuaScriptManager.callEvent("OnMeleeStrike", player.getUUID().toString(), strikePos.x, strikePos.y, strikePos.z);
	}

	private static void playLocalSound(Player player, ResourceLocation soundRes, float volume, float pitch) {
	    SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundRes);
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