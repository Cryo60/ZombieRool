package me.cryo.zombierool.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World-local settings for an encounter which deliberately has no editor UI. */
public final class MrChiefConfig extends SavedData {
    public static final int MIN_NATURAL_CHANCE=100000;
    public static final int PARTY_SECONDS=60;
    public int chance = MIN_NATURAL_CHANCE, fleeSeconds = 20, reward = 2500, partySeconds = PARTY_SECONDS;
    public float fleeRadius = 8;
    public boolean enabled = true;
    public static MrChiefConfig get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(MrChiefConfig::load,MrChiefConfig::new,"zombierool_mr_chief");
    }
    private static MrChiefConfig load(CompoundTag n) {
        var c=new MrChiefConfig();
        // Older test settings must not make the secret common after an update.
        if(n.contains("chance"))c.chance=Math.max(MIN_NATURAL_CHANCE,Math.min(1000000,n.getInt("chance")));
        if(n.contains("flee_seconds"))c.fleeSeconds=Math.max(1,Math.min(300,n.getInt("flee_seconds")));
        if(n.contains("reward"))c.reward=Math.max(0,Math.min(1000000,n.getInt("reward")));
        // Dance and bundled music share one duration, including worlds saved with the old 15s clip.
        c.partySeconds=PARTY_SECONDS;
        if(n.contains("flee_radius")){float r=n.getFloat("flee_radius");c.fleeRadius=Float.isFinite(r)?Math.max(1,Math.min(32,r)):8;}
        if(n.contains("enabled"))c.enabled=n.getBoolean("enabled");return c;
    }
    public void configure(CompoundTag n) {
        MrChiefConfig c=load(n);chance=c.chance;fleeSeconds=c.fleeSeconds;reward=c.reward;partySeconds=c.partySeconds;fleeRadius=c.fleeRadius;enabled=c.enabled;setDirty();
    }
    @Override public CompoundTag save(CompoundTag n) {
        n.putInt("chance",chance);n.putInt("flee_seconds",fleeSeconds);n.putInt("reward",reward);n.putInt("party_seconds",partySeconds);n.putFloat("flee_radius",fleeRadius);n.putBoolean("enabled",enabled);return n;
    }
}
