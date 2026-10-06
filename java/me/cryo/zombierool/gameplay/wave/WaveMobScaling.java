package me.cryo.zombierool.gameplay.wave;

import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Random;
import java.util.UUID;

public final class WaveMobScaling {
    private static final double SUPER_SPRINTER_DAMAGE_MULTIPLIER = 2.0;
    private static final UUID SUPER_SPRINTER_DAMAGE_ID = UUID.fromString("9381c8b3-3a56-42d4-a1f9-0c6a51d4e0e5");

    private WaveMobScaling() {}

    public static void applyHealth(Mob mob, int wave, ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        float baseHealth;
        float maxCap;

        if (mob instanceof ZombieEntity) {
            baseHealth = config.getZombieBaseHealth();
            maxCap = config.getZombieMaxHealth();
            if (wave < 2) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth);
                mob.setHealth(baseHealth);
                return;
            }
        } else if (mob instanceof HellhoundEntity) {
            if (wave < 6) {
                return;
            }
            baseHealth = config.getHellhoundBaseHealth();
            maxCap = config.getHellhoundMaxHealth();
        } else if (mob instanceof CrawlerEntity) {
            baseHealth = config.getCrawlerBaseHealth();
            maxCap = config.getCrawlerMaxHealth();
            if (wave < 2) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth);
                mob.setHealth(baseHealth);
                return;
            }
        } else {
            baseHealth = 20f;
            maxCap = 40f;
        }

        float newMaxHealth = baseHealth;
        if (wave <= 9) {
            newMaxHealth += wave * 4.0f;
        } else {
            newMaxHealth = baseHealth + (9 * 4.0f);
            newMaxHealth *= (float) Math.pow(1.15, wave - 9);
        }
        newMaxHealth = Math.min(newMaxHealth, maxCap);

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
        mob.setHealth(newMaxHealth);
    }

    public static void applySpeed(ZombieEntity zombie, int wave, Random rand, ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        boolean isSuperSprinter = false;

        if (config.areSuperSprintersEnabled() && wave >= config.getSuperSprinterActivationWave()) {
            double baseSuperChance = config.getSuperSprinterChance();
            double scaledSuperChance = baseSuperChance + (wave - config.getSuperSprinterActivationWave()) * 0.015;
            double finalSuperChance = Math.min(0.25, scaledSuperChance);

            if (rand.nextDouble() < finalSuperChance) {
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.getSuperSprinterSpeed());
                isSuperSprinter = true;
                zombie.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(SUPER_SPRINTER_DAMAGE_ID);
                zombie.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(
                        SUPER_SPRINTER_DAMAGE_ID,
                        "Super Sprinter Damage",
                        zombie.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * (SUPER_SPRINTER_DAMAGE_MULTIPLIER - 1.0),
                        AttributeModifier.Operation.ADDITION
                ));
            }
        }

        zombie.setSuperSprinter(isSuperSprinter);

        if (!isSuperSprinter && config.isZombiesCanSprint() && wave >= config.getZombieSprintWave()) {
            double sprintChance = Math.min(1.0, config.getZombieSprintChance() + (wave - config.getZombieSprintWave()) * 0.05);
            if (rand.nextDouble() <= sprintChance) {
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.getZombieSprintSpeed());
            }
        }
    }
}
