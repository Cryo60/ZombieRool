package me.cryo.zombierool.configuration;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public class HalloweenConfig {
	public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
	public static final ForgeConfigSpec SPEC;
	public static final ForgeConfigSpec.EnumValue<HalloweenMode> HALLOWEEN_MODE;
	public enum HalloweenMode {
	    AUTO,      
	    FORCE_ON,  
	    FORCE_OFF  
	}
	
	static {
	    BUILDER.push("Halloween Settings");
	    HALLOWEEN_MODE = BUILDER
	        .comment("Halloween mode control:",
	                 "AUTO - Active only during Halloween period (Oct 20 - Nov 5)",
	                 "FORCE_ON - Always active regardless of date",
	                 "FORCE_OFF - Always disabled, even during Halloween period")
	        .translation("zombierool.config.halloween_mode")
	        .defineEnum("halloweenMode", HalloweenMode.AUTO);
	    BUILDER.pop();
	    SPEC = BUILDER.build();
	}
	
	public static void register() {
	    ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "zombierool-halloween.toml");
	}
	
	public static HalloweenMode getHalloweenMode() {
	    return HALLOWEEN_MODE.get();
	}
	
	public static void setHalloweenMode(HalloweenMode mode) {
	    // CORRECTION : On vérifie si la valeur a CHANGÉ avant de sauvegarder.
	    // C'est l'absence de cette vérification qui causait la boucle infinie de sauvegarde.
	    if (HALLOWEEN_MODE.get() != mode) {
	        HALLOWEEN_MODE.set(mode);
	        SPEC.save();
	    }
	}
	
	public static boolean isHalloweenForced() {
	    return HALLOWEEN_MODE.get() == HalloweenMode.FORCE_ON;
	}
	
	public static boolean isHalloweenDisabled() {
	    return HALLOWEEN_MODE.get() == HalloweenMode.FORCE_OFF;
	}
	
	public static boolean isHalloweenAuto() {
	    return HALLOWEEN_MODE.get() == HalloweenMode.AUTO;
	}
}