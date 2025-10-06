package net.mcreator.zombierool;

import net.minecraftforge.common.ForgeConfigSpec;

public class ZombieConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<Integer> MAX_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<Boolean> HIDE_HEALTH;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DISABLE_HUNGER;
    public static final ForgeConfigSpec.ConfigValue<Boolean> DISABLE_XP;

    static {
        BUILDER.push("zombierool Settings");
        
        MAX_HEALTH = BUILDER.comment("Maximum health (in half-hearts)")
                            .defineInRange("maxHealth", 8, 1, 20);
        HIDE_HEALTH = BUILDER.comment("Hide health bar completely")
                             .define("hideHealth", true);
        DISABLE_HUNGER = BUILDER.comment("Disable hunger system")
                                .define("disableHunger", true);
        DISABLE_XP = BUILDER.comment("Disable XP system")
                            .define("disableXP", true);
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}