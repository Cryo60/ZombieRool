package me.cryo.zombierool.client;

import java.util.Locale;
import java.util.Set;

/** Semantic bone policy shared by Bedrock gunpacks; unknown children inherit their assembly. */
public final class TacZCamoParts {
    public enum Finish { PAINT, METAL, PROTECTED }
    private static final Set<String> METAL_ALIASES=Set.of(
        "augcharge", "pull", "pullhandle", "charginghandle", "chargehandle", "chargel", "charger",
        "jimiao", "rsight", "fsight", "magrelease", "magazinecatch", "magazinelatch", "maglatch",
        "magcatch", "magcather", "saftyswitch", "fireselect", "fireselector", "insurance",
        "releaser", "released", "release", "ejector", "strikignhammer", "firinghammer",
        "gastube", "gasblock", "gaslock", "piston", "spring", "striker", "pins", "pin",
        "screw", "screws", "pings", "bipoddmrsniper", "sighting", "sightt", "muzzlee", "tirgger");
    private TacZCamoParts() {}

    public static Finish resolve(String name,boolean illuminated,Finish inherited){
        if(inherited==Finish.PROTECTED)return inherited;
        String n=name==null?"":name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","").replaceAll("[0-9]+$","");
        // Animation groups combine unrelated pieces. Do not classify them by a
        // substring such as 'bullet' or 'righthand': inspect their children instead.
        if(name!=null&&name.toLowerCase(Locale.ROOT).matches(".*(?:_| )(?:and|with)(?:_| ).*"))return inherited;
        if(illuminated||n.contains("illuminated")||n.contains("lens")||n.contains("glass")
            ||n.contains("crosshair")||n.contains("reticle")||n.contains("screen")
            ||n.equals("display")||n.equals("ammocount")||n.equals("muzzleflash")
            ||n.matches("(?:left|right)handpos")
            ||n.contains("finger")||n.equals("palm")||n.equals("thumb"))return Finish.PROTECTED;
        // BO6/Warzone akimbo weapons and spare magazines live under 'lefthand'
        // and 'righthand'. Only the actual hand renderer (*hand_pos) is protected.
        // Controls on otherwise paintable assemblies take precedence (mag_release, body_rail...).
        if(METAL_ALIASES.contains(n)||n.contains("trigger")&&!n.contains("guard")
            ||n.contains("magrelease")||n.contains("boltrelease")||n.contains("selector")
            ||n.contains("rail")||n.contains("sight")||n.contains("scope")||n.contains("bipod")
            ||n.contains("silencer")||n.contains("suppressor")||n.contains("compensator")
            ||n.contains("muzzle")||n.contains("laser")||n.contains("flashlight"))return Finish.METAL;
        if(n.contains("bullet")||n.startsWith("ammo")||n.contains("casing")||n.startsWith("shell")
            ||n.contains("charging")||n.contains("chamber"))return Finish.METAL;
        // Explicit furniture can be a child of a barrel/bolt assembly in these packs.
        if(n.contains("handguard")||n.contains("handgraud")||n.contains("shroud")||n.contains("shround")
            ||n.contains("stock")||n.contains("receiver")||n.contains("body")||n.contains("wood")
            ||n.contains("grip")||n.contains("magazine")||n.contains("magzine")||n.contains("magezine")
            ||n.equals("handle")||n.equals("handgrip")||n.equals("pistolhandle")||n.equals("drummag")
            ||n.startsWith("magstandard")||n.startsWith("magextended")||n.startsWith("extdmag")
            ||n.equals("mag")||n.equals("magbox")||n.equals("dmag")||n.equals("upper")||n.equals("lower")||n.equals("forearm")
            ||n.equals("tacticalhandguard")||n.equals("defaulthg")||n.equals("tachg")||n.equals("hg"))return Finish.PAINT;
        // A pistol slide is a painted outer casing; its barrel and internals stay bare.
        if(n.equals("slide")||n.equals("slider"))return Finish.PAINT;
        if(n.contains("barrel")||n.contains("bolt")||n.contains("hammer")||n.contains("chamber")
            ||n.contains("charging")||n.contains("bullet")
            ||n.startsWith("ammo")||n.contains("casing")||n.startsWith("shell")
            ||n.contains("safety")||n.contains("cylinder")||n.contains("cyclinder")
            ||n.equals("maininternal")||n.equals("mount")||n.equals("mountdefault"))return Finish.METAL;
        return inherited;
    }
}
