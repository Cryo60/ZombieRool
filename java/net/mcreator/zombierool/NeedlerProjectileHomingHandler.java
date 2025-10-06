package net.mcreator.zombierool; // Adjust package as needed

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent; // Correct import for TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.Level; // Already imported

import java.util.List;
import java.util.Comparator;

// Add your custom mob imports here if they are not already imported
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.entity.MannequinEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;


@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NeedlerProjectileHomingHandler {

    private static final String TAG_NEEDLER_HOMING = "zombierool:needler_homing"; // Must match tag in NeedlerWeaponItem
    // In NeedlerProjectileHomingHandler.java
	private static final double HOMING_RANGE = 4.0; // Keep this as you prefer a small range
	private static final float HOMING_STRENGTH = 5.0f; // Try this value for better "stickiness"
	private static final int HOMING_DELAY_TICKS = 0; // Keep this for immediate homing

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        // Ensure the event is on the server side and at the end of the tick phase
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
            Level level = event.level;

            // Define a large bounding box to query all loaded Arrow entities in the current level.
            // This AABB covers the typical loaded chunk area around players.
            // Adjust these values if your loaded area is different or if you have specific world limits.
            AABB searchLoadedArrowsBox = new AABB(
                level.getMinBuildHeight(), // Minimum Y-level (usually -64)
                level.getMinBuildHeight(), // Minimum X-Z values effectively cover the loaded chunks.
                level.getMinBuildHeight(),
                level.getMaxBuildHeight(), // Maximum Y-level (usually 320)
                level.getMaxBuildHeight(),
                level.getMaxBuildHeight()
            ).inflate(256); // Inflate by a large amount (e.g., 256 blocks) to ensure it covers loaded areas

            List<Arrow> loadedArrows = level.getEntitiesOfClass(Arrow.class, searchLoadedArrowsBox);

            for (Arrow arrow : loadedArrows) {
                // Check if this arrow is marked for Needler homing
                if (arrow.getPersistentData().getBoolean(TAG_NEEDLER_HOMING)) {
                    // Apply a small delay before homing starts for more natural initial spread
                    // With HOMING_DELAY_TICKS = 0, homing starts immediately.
                    if (arrow.tickCount < HOMING_DELAY_TICKS) {
                        continue;
                    }

                    LivingEntity owner = (LivingEntity) arrow.getOwner();
                    // If owner is null or not a LivingEntity (e.g., owner died or was not a player/mob),
                    // then this arrow cannot home effectively. Remove the tag and skip.
                    if (owner == null || !(owner instanceof LivingEntity)) {
                        arrow.getPersistentData().remove(TAG_NEEDLER_HOMING);
                        continue;
                    }

                    // Find the nearest target within homing range
                    LivingEntity target = findNearestTarget(arrow, owner, HOMING_RANGE);

                    if (target != null) {
                        Vec3 arrowPos = arrow.position();
                        Vec3 targetPos = target.position().add(0, target.getBbHeight() / 2, 0); // Aim for center of target

                        Vec3 directionToTarget = targetPos.subtract(arrowPos).normalize();
                        Vec3 currentVelocity = arrow.getDeltaMovement();

                        // Smoothly interpolate the current velocity towards the target direction
                        Vec3 newVelocity = currentVelocity.normalize().scale(1.0 - HOMING_STRENGTH)
                                         .add(directionToTarget.scale(HOMING_STRENGTH))
                                         .normalize()
                                         .scale(currentVelocity.length()); // Maintain current speed

                        arrow.setDeltaMovement(newVelocity);
                    }
                }
            }
        }
    }

    private static LivingEntity findNearestTarget(Arrow arrow, LivingEntity shooter, double range) {
        Level level = arrow.level();
        AABB searchBox = arrow.getBoundingBox().inflate(range, range, range);

        // Filter for valid targets (monsters, non-tamed animals, and your custom mobs)
        // Exclude the shooter and other players.
        List<LivingEntity> possibleTargets = level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> {
            return entity != shooter && entity.isAlive()
                   && !(entity instanceof Player) // Exclude players
                   && !(entity instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isTame()) // Exclude tamed animals
                   && (entity instanceof Monster // Vanilla monsters
                       || entity instanceof ZombieEntity // Your custom zombie
                       || entity instanceof CrawlerEntity // Your custom crawler
                       || entity instanceof MannequinEntity // Your custom mannequin
                       || entity instanceof HellhoundEntity); // Your custom hellhound
        });

        // Find the closest target
        return possibleTargets.stream()
                .min(Comparator.comparingDouble(arrow::distanceToSqr))
                .orElse(null);
    }
}