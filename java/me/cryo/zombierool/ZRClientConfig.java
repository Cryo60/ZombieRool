package me.cryo.zombierool.configuration;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class ZRClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<HalloweenMode> HALLOWEEN_MODE;
    public static final ForgeConfigSpec.BooleanValue PREFER_ZR_WEAPONS;

    public enum HalloweenMode {
        AUTO,      
        FORCE_ON,  
        FORCE_OFF  
    }

    static {
        BUILDER.push("ZombieRool Client Settings");
        
        HALLOWEEN_MODE = BUILDER
            .comment("Halloween mode control (Server Operators only):",
                     "AUTO - Active only during Halloween period (Oct 20 - Nov 5)",
                     "FORCE_ON - Always active regardless of date",
                     "FORCE_OFF - Always disabled, even during Halloween period")
            .translation("zombierool.config.halloween_mode")
            .defineEnum("halloweenMode", HalloweenMode.AUTO);

        PREFER_ZR_WEAPONS = BUILDER
            .comment("Prefer Classic ZombieRool weapons over TacZ weapons.")
            .translation("zombierool.config.prefer_zr_weapons")
            .define("preferZrWeapons", false);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "zombierool-client.toml");
    }

    public static HalloweenMode getHalloweenMode() {
        return HALLOWEEN_MODE.get();
    }

    public static void setHalloweenMode(HalloweenMode mode) {
        if (HALLOWEEN_MODE.get() != mode) {
            HALLOWEEN_MODE.set(mode);
            SPEC.save();
        }
    }

    public static boolean prefersZrWeapons() {
        return PREFER_ZR_WEAPONS.get();
    }

    public static void setPreferZrWeapons(boolean prefer) {
        if (PREFER_ZR_WEAPONS.get() != prefer) {
            PREFER_ZR_WEAPONS.set(prefer);
            SPEC.save();
        }
    }
}