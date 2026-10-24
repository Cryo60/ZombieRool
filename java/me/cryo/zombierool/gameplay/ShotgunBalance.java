package me.cryo.zombierool.gameplay;
import me.cryo.zombierool.core.system.WeaponSystem;
/** Apply to the whole pellet damage, including headshot bonuses; Insta-Kill bypasses this. */
public final class ShotgunBalance {
    private ShotgunBalance(){}
    public static float multiplier(WeaponSystem.Definition def,double distance){
        if(def==null||!"SHOTGUN".equalsIgnoreCase(def.type))return 1;
        double start=Double.isFinite(def.stats.damage_falloff_start)?Math.max(0,def.stats.damage_falloff_start):6;
        double end=Double.isFinite(def.stats.damage_falloff_end)?Math.max(start+.01,def.stats.damage_falloff_end):Math.max(24,start+.01);
        double floor=Double.isFinite(def.stats.damage_falloff_min)?net.minecraft.util.Mth.clamp(def.stats.damage_falloff_min,0,1):.1;
        return (float)(1-(1-floor)*net.minecraft.util.Mth.clamp((distance-start)/(end-start),0,1));
    }
}
