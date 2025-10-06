package net.mcreator.zombierool.procedures;

import net.mcreator.zombierool.WaveManager;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.ScreenShakePacket;
import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.entity.MannequinEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.DustParticleOptions; 
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.client.ScreenShakeHandler;
import org.joml.Vector3f;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Comparator;

// NOUVEAU: Import de la classe PlayerVoiceManager
import net.mcreator.zombierool.util.PlayerVoiceManager;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MeleeAttackHandler {

    private static final ResourceLocation KNIFE_SLASH_BODY_SOUND = new ResourceLocation("zombierool", "knife_slash_body");
    private static final ResourceLocation KNIFE_HIT_MISC_MATERIAL_SOUND = new ResourceLocation("zombierool", "knife_hit_misc_material");

    private static final ConcurrentHashMap<java.util.UUID, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TICKS = 10;
    private static final double ATTACK_RANGE = 1.25;
    private static final float BASE_DAMAGE_PER_HIT = 20.0f;

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        COOLDOWN_MAP.remove(event.getEntity().getUUID());
        System.out.println("DEBUG: Cooldown réinitialisé pour " + event.getEntity().getName().getString() + " après respawn.");
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        COOLDOWN_MAP.remove(event.getEntity().getUUID());
        System.out.println("DEBUG: Cooldown réinitialisé pour " + event.getEntity().getName().getString() + " après déconnexion.");
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
        Vec3 endVec = startVec.add(lookVec.x * ATTACK_RANGE, lookVec.y * ATTACK_RANGE, lookVec.z * ATTACK_RANGE);

        LivingEntity targetEntity = null;
        HitResult rayTraceResult = level.clip(new net.minecraft.world.level.ClipContext(
                startVec, endVec,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
        ));

        if (rayTraceResult.getType() == HitResult.Type.ENTITY && rayTraceResult instanceof EntityHitResult entityHitResult) {
            if (entityHitResult.getEntity() instanceof LivingEntity livingHitEntity) {
                targetEntity = livingHitEntity;
            }
        }

        if (targetEntity == null) {
            AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(ATTACK_RANGE)).inflate(0.25, 0.25, 0.25);

            List<LivingEntity> entitiesInArea = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != player && !entity.isSpectator() && !entity.isDeadOrDying() && !(entity instanceof Player)
            );

            targetEntity = entitiesInArea.stream()
                    .filter(entity -> {
                        Vec3 dirToEntity = entity.position().subtract(player.position()).normalize();
                        return dirToEntity.dot(lookVec) > 0.5;
                    })
                    .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
                    .orElse(null);
        }

        if (targetEntity != null) {
            
            // --- GESTION DES POINTS ---
            // On vérifie si la cible est un de nos mobs custom.
            boolean isOurCustomMob = targetEntity instanceof ZombieEntity ||
                                     targetEntity instanceof CrawlerEntity ||
                                     targetEntity instanceof MannequinEntity ||
                                     targetEntity instanceof HellhoundEntity;
            
            // Si c'est le cas, on donne 10 points pour le coup.
            if(isOurCustomMob){
                PointManager.modifyScore(player, 10);
            }
            // --- FIN GESTION DES POINTS ---

            int currentWave = WaveManager.getCurrentWave();
            float waveNumberForDamage = Math.max(1.0f, (float) currentWave + 1);
            float finalDamage = BASE_DAMAGE_PER_HIT / waveNumberForDamage;
            finalDamage = Math.max(1.0f, finalDamage);

            System.out.println("DEBUG: Attaque sur " + targetEntity.getName().getString() + " (Vie avant: " + targetEntity.getHealth() + ")");
            targetEntity.hurt(player.damageSources().mobAttack(player), finalDamage);
            System.out.println("DEBUG: Vie après: " + targetEntity.getHealth() + ", Dégâts infligés: " + finalDamage);

            playLocalSound(player, KNIFE_SLASH_BODY_SOUND, 1.0f, 1.0f);
            spawnAttackParticles(level, targetEntity.position(), lookVec);

            if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new ScreenShakePacket(10, 0.3f, ScreenShakeHandler.ShakeType.MELEE));
                System.out.println("DEBUG: Envoi du ScreenShakePacket à " + player.getName().getString());
            }
            // NOUVEAU: Joue le son vocal "melee attack!"
            PlayerVoiceManager.playMeleeAttackSound(player, level);

            COOLDOWN_MAP.put(player.getUUID(), currentTime);
        } else {
            if (rayTraceResult.getType() == HitResult.Type.BLOCK) {
                playLocalSound(player, KNIFE_HIT_MISC_MATERIAL_SOUND, 1.0f, 1.0f);
                spawnBlockHitParticles(level, rayTraceResult.getLocation());
            }
            // NOUVEAU: Joue le son vocal "melee attack!" même si un bloc est touché
            PlayerVoiceManager.playMeleeAttackSound(player, level);
            COOLDOWN_MAP.put(player.getUUID(), currentTime);
        }
    }

    private static void playLocalSound(Player player, ResourceLocation soundRes, float volume, float pitch) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundRes);
        if (sound == null) {
            System.err.println("[MeleeAttackHandler] Son non trouvé: " + soundRes);
            return;
        }
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void spawnAttackParticles(ServerLevel level, Vec3 pos, Vec3 direction) {
        level.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.0F, 0.0F), 1.0F), pos.x, pos.y + 0.5, pos.z, 10, 0.2, 0.2, 0.2, 0.1);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y + 0.5, pos.z, 2, 0.1, 0.1, 0.1, 0.05);
    }

    private static void spawnBlockHitParticles(ServerLevel level, Vec3 pos) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                pos.x, pos.y, pos.z, 5, 0.1, 0.1, 0.1, 0.05);
    }
}
