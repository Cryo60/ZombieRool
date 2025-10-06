package net.mcreator.zombierool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items; // Import for default starter item

import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

public class WorldConfig extends SavedData {

    private static final String DATA_NAME = "zombierool_world_config";
    private String fogPreset = "normal";
    private ResourceLocation starterItem = Items.WOODEN_SWORD.getDefaultInstance().getItem().builtInRegistryHolder().key().location(); // Default to wooden sword
    private String dayNightMode = "cycle"; // "day", "night", "cycle"
    
    private Set<ResourceLocation> disabledWonderWeapons = new HashSet<>(); 

    private boolean particlesEnabled = false;
    private ResourceLocation particleTypeId = null;
    private String particleDensity = "normal";
    private String particleMode = "global";

    private String musicPreset = "default";
    private String eyeColorPreset = "default";
    private String voicePreset = "uk"; // MODIFIÉ: Le preset de voix par défaut est maintenant "uk"

    private Set<BlockPos> playerSpawnerPositions = new HashSet<>();
    private Set<BlockPos> mysteryBoxPositions = new HashSet<>();
    private Set<BlockPos> derWunderfizzPositions = new HashSet<>();

    // NEW: Set to store PowerSwitchBlock positions (full BlockPos)
    private Set<BlockPos> powerSwitchPositions = new HashSet<>(); 

    private boolean superSprintersEnabled = false; 

    private boolean coldWaterEffectEnabled = false;

    private Map<String, String> bloodOverlays = new HashMap<>();

    public WorldConfig() {
        // [DEBUG] System.out.println("[DEBUG][WorldConfig][Constructor] New WorldConfig instance created. Hash: " + System.identityHashCode(this));
    }

    public static WorldConfig get(ServerLevel world) {
        // [DEBUG] System.out.println("[DEBUG][WorldConfig][Get] Attempting to retrieve WorldConfig instance for level: " + world.dimension().location());
        DimensionDataStorage storage = world.getDataStorage();
        WorldConfig config = storage.computeIfAbsent(WorldConfig::load, WorldConfig::new, DATA_NAME);
        // [DEBUG] System.out.println("[DEBUG][WorldConfig][Get] Retrieved WorldConfig instance. Hash: " + System.identityHashCode(config) + ". MysteryBoxPositions count: " + config.mysteryBoxPositions.size());
        return config;
    }

    public static WorldConfig load(CompoundTag nbt) {
        // [DEBUG] System.out.println("[DEBUG][WorldConfig][Load] Loading WorldConfig from NBT.");
        WorldConfig config = new WorldConfig();
        
        if (nbt.contains("fogPreset")) {
            config.fogPreset = nbt.getString("fogPreset");
        }
        
        if (nbt.contains("starterItem")) {
            config.starterItem = new ResourceLocation(nbt.getString("starterItem"));
        }
        
        if (nbt.contains("dayNightMode")) {
            config.dayNightMode = nbt.getString("dayNightMode");
        } else if (nbt.contains("permanentNight")) {
            config.dayNightMode = nbt.getBoolean("permanentNight") ? "night" : "cycle";
        }
        
        if (nbt.contains("disabledWonderWeapons", ListTag.TAG_LIST)) {
            ListTag listTag = nbt.getList("disabledWonderWeapons", StringTag.TAG_STRING);
            for (int i = 0; i < listTag.size(); i++) {
                config.disabledWonderWeapons.add(new ResourceLocation(listTag.getString(i)));
            }
        }

        if (nbt.contains("particlesEnabled")) {
            config.particlesEnabled = nbt.getBoolean("particlesEnabled");
        }
        if (nbt.contains("particleTypeId")) {
            config.particleTypeId = new ResourceLocation(nbt.getString("particleTypeId"));
        } else {
            config.particleTypeId = null;
        }
        if (nbt.contains("particleDensity")) {
            config.particleDensity = nbt.getString("particleDensity");
        } else {
            config.particleDensity = "normal";
        }
        if (nbt.contains("particleMode")) {
            config.particleMode = nbt.getString("particleMode");
        } else {
            config.particleMode = "global";
        }

        if (nbt.contains("musicPreset")) {
            config.musicPreset = nbt.getString("musicPreset");
        } else {
            config.musicPreset = "default";
        }
        
        if (nbt.contains("eyeColorPreset")) {
            config.eyeColorPreset = nbt.getString("eyeColorPreset");
        } else {
            config.eyeColorPreset = "default";
        }

        // NOUVEAU: Chargement du preset de voix
        if (nbt.contains("voicePreset")) {
            config.voicePreset = nbt.getString("voicePreset");
        } else {
            config.voicePreset = "uk"; // MODIFIÉ: Le preset de voix par défaut est maintenant "uk" lors du chargement
        }

        if (nbt.contains("playerSpawnerPositions", ListTag.TAG_LIST)) {
            ListTag posListTag = nbt.getList("playerSpawnerPositions", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < posListTag.size(); i++) {
                CompoundTag posTag = posListTag.getCompound(i);
                int x = posTag.getInt("x");
                int y = posTag.getInt("y");
                int z = posTag.getInt("z");
                config.playerSpawnerPositions.add(new BlockPos(x, y, z));
            }
        }

        if (nbt.contains("mysteryBoxPositions", ListTag.TAG_LIST)) {
            ListTag posListTag = nbt.getList("mysteryBoxPositions", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < posListTag.size(); i++) {
                CompoundTag posTag = posListTag.getCompound(i);
                int x = posTag.getInt("x");
                int y = posTag.getInt("y");
                int z = posTag.getInt("z");
                config.mysteryBoxPositions.add(new BlockPos(x, y, z));
            }
        }

        // NEW: Load DerWunderfizzBlock positions
        if (nbt.contains("derWunderfizzPositions", ListTag.TAG_LIST)) {
            ListTag posListTag = nbt.getList("derWunderfizzPositions", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < posListTag.size(); i++) {
                CompoundTag posTag = posListTag.getCompound(i);
                int x = posTag.getInt("x");
                // The Y coordinate is stored as 0, as per requirement (only X and Z matter for registration)
                int z = posTag.getInt("z");
                config.derWunderfizzPositions.add(new BlockPos(x, 0, z));
            }
        }
        
        // NEW: Load PowerSwitchBlock positions
        if (nbt.contains("powerSwitchPositions", ListTag.TAG_LIST)) {
            ListTag posListTag = nbt.getList("powerSwitchPositions", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < posListTag.size(); i++) {
                CompoundTag posTag = posListTag.getCompound(i);
                int x = posTag.getInt("x");
                int y = posTag.getInt("y");
                int z = posTag.getInt("z");
                config.powerSwitchPositions.add(new BlockPos(x, y, z));
            }
        }

        if (nbt.contains("superSprintersEnabled")) {
            config.superSprintersEnabled = nbt.getBoolean("superSprintersEnabled");
        }

        if (nbt.contains("coldWaterEffectEnabled")) {
            config.coldWaterEffectEnabled = nbt.getBoolean("coldWaterEffectEnabled");
        } else {
            config.coldWaterEffectEnabled = false;
        }

        if (nbt.contains("bloodOverlays", CompoundTag.TAG_COMPOUND)) {
		    CompoundTag overlaysTag = nbt.getCompound("bloodOverlays");
		    for (String key : overlaysTag.getAllKeys()) {
		        config.bloodOverlays.put(key, overlaysTag.getString(key));
		    }
		}

        return config;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        
        compound.putString("fogPreset", this.fogPreset);
        compound.putString("starterItem", this.starterItem.toString());
        compound.putString("dayNightMode", this.dayNightMode); 

        ListTag listTag = new ListTag();
        for (ResourceLocation itemId : disabledWonderWeapons) {
            listTag.add(StringTag.valueOf(itemId.toString()));
        }
        compound.put("disabledWonderWeapons", listTag);

        compound.putBoolean("particlesEnabled", this.particlesEnabled);
        if (this.particleTypeId != null) {
            compound.putString("particleTypeId", this.particleTypeId.toString());
        } else {
            compound.remove("particleTypeId");
        }
        compound.putString("particleDensity", this.particleDensity);
        compound.putString("particleMode", this.particleMode);

        compound.putString("musicPreset", this.musicPreset);
        compound.putString("eyeColorPreset", this.eyeColorPreset);
        compound.putString("voicePreset", this.voicePreset); // NOUVEAU: Sauvegarde du preset de voix

        ListTag playerSpawnerPosListTag = new ListTag();
        for (BlockPos pos : playerSpawnerPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            playerSpawnerPosListTag.add(posTag);
        }
        compound.put("playerSpawnerPositions", playerSpawnerPosListTag);

        ListTag mysteryBoxPosListTag = new ListTag();
        for (BlockPos pos : mysteryBoxPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            mysteryBoxPosListTag.add(posTag);
        }
        compound.put("mysteryBoxPositions", mysteryBoxPosListTag);

        // NEW: Save DerWunderfizzBlock positions
        ListTag derWunderfizzPosListTag = new ListTag();
        for (BlockPos pos : derWunderfizzPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            // The Y coordinate is stored as 0, as per requirement
            posTag.putInt("y", 0);
            posTag.putInt("z", pos.getZ());
            derWunderfizzPosListTag.add(posTag);
        }
        compound.put("derWunderfizzPositions", derWunderfizzPosListTag);

        // NEW: Save PowerSwitchBlock positions
        ListTag powerSwitchPosListTag = new ListTag();
        for (BlockPos pos : powerSwitchPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            powerSwitchPosListTag.add(posTag);
        }
        compound.put("powerSwitchPositions", powerSwitchPosListTag);

        compound.putBoolean("superSprintersEnabled", this.superSprintersEnabled);

        compound.putBoolean("coldWaterEffectEnabled", this.coldWaterEffectEnabled);

        CompoundTag overlaysTag = new CompoundTag();
		for (Map.Entry<String, String> entry : bloodOverlays.entrySet()) {
		    overlaysTag.putString(entry.getKey(), entry.getValue());
		}
		compound.put("bloodOverlays", overlaysTag);

        return compound;
    }

    public String getFogPreset() {
        return fogPreset;
    }

    public void setFogPreset(String fogPreset) {
        this.fogPreset = fogPreset;
        this.setDirty();
    }

    public ResourceLocation getStarterItem() {
        return starterItem;
    }

    public void setStarterItem(ResourceLocation starterItem) {
        this.starterItem = starterItem;
        this.setDirty();
    }

    public String getDayNightMode() {
        return dayNightMode;
    }

    public void setDayNightMode(String dayNightMode) {
        this.dayNightMode = dayNightMode;
        this.setDirty();
    }

    public boolean isWonderWeaponDisabled(ResourceLocation wonderWeaponId) {
        return disabledWonderWeapons.contains(wonderWeaponId);
    }

    public void enableWonderWeapon(ResourceLocation wonderWeaponId) {
        if (disabledWonderWeapons.remove(wonderWeaponId)) {
            this.setDirty();
        }
    }

    public void disableWonderWeapon(ResourceLocation wonderWeaponId) {
        if (disabledWonderWeapons.add(wonderWeaponId)) {
            this.setDirty();
        }
    }

    public boolean areParticlesEnabled() {
        return particlesEnabled;
    }

    public ResourceLocation getParticleTypeId() {
        return particleTypeId;
    }

    public String getParticleDensity() {
        return particleDensity;
    }

    public String getParticleMode() {
        return particleMode;
    }

    public void enableParticles(ResourceLocation typeId, String density, String mode) {
        this.particlesEnabled = true;
        this.particleTypeId = typeId;
        this.particleDensity = density;
        this.particleMode = mode;
        this.setDirty();
    }

    public void disableParticles() {
        this.particlesEnabled = false;
        this.particleTypeId = null;
        this.particleDensity = "normal";
        this.particleMode = "global";
        this.setDirty();
    }

    public String getMusicPreset() {
        return musicPreset;
    }

    public void setMusicPreset(String musicPreset) {
        this.musicPreset = musicPreset;
        this.setDirty();
    }

    public String getEyeColorPreset() {
        return eyeColorPreset;
    }

    public void setEyeColorPreset(String eyeColorPreset) {
        this.eyeColorPreset = eyeColorPreset;
        this.setDirty();
    }

    // NOUVEAU: Getter et Setter pour le preset de voix
    public String getVoicePreset() {
        return voicePreset;
    }

    public void setVoicePreset(String voicePreset) {
        this.voicePreset = voicePreset;
        this.setDirty();
    }

    public void addPlayerSpawnerPosition(BlockPos pos) {
        if (playerSpawnerPositions.add(pos)) {
            this.setDirty();
        }
    }

    public void removePlayerSpawnerPosition(BlockPos pos) {
        if (playerSpawnerPositions.remove(pos)) {
            this.setDirty();
        }
    }

    public Set<BlockPos> getPlayerSpawnerPositions() {
        return Collections.unmodifiableSet(playerSpawnerPositions);
    }

    public void addMysteryBoxPosition(BlockPos pos) {
        if (mysteryBoxPositions.add(pos)) {
            this.setDirty();
        }
    }

    public void removeMysteryBoxPosition(BlockPos pos) {
        if (mysteryBoxPositions.remove(pos)) {
            this.setDirty();
        }
    }

    public Set<BlockPos> getMysteryBoxPositions() {
        return Collections.unmodifiableSet(mysteryBoxPositions);
    }

    // NEW: Add/Remove/Get methods for DerWunderfizzBlock positions
    public void addDerWunderfizzPosition(BlockPos pos) {
        // Store only X and Z by creating a new BlockPos with Y=0
        if (derWunderfizzPositions.add(new BlockPos(pos.getX(), 0, pos.getZ()))) {
            this.setDirty();
        }
    }

    public void removeDerWunderfizzPosition(BlockPos pos) {
        // Remove using only X and Z
        if (derWunderfizzPositions.remove(new BlockPos(pos.getX(), 0, pos.getZ()))) {
            this.setDirty();
        }
    }

    public Set<BlockPos> getDerWunderfizzPositions() {
        return Collections.unmodifiableSet(derWunderfizzPositions);
    }

    // NEW: Add/Remove/Get methods for PowerSwitchBlock positions
    public void addPowerSwitchPosition(BlockPos pos) {
        if (powerSwitchPositions.add(pos)) {
            this.setDirty();
        }
    }

    public void removePowerSwitchPosition(BlockPos pos) {
        if (powerSwitchPositions.remove(pos)) {
            this.setDirty();
        }
    }

    public Set<BlockPos> getPowerSwitchPositions() {
        return Collections.unmodifiableSet(powerSwitchPositions);
    }

    public boolean areSuperSprintersEnabled() {
        return superSprintersEnabled;
    }

    public void setSuperSprintersEnabled(boolean superSprintersEnabled) {
        this.superSprintersEnabled = superSprintersEnabled;
        this.setDirty();
    }

    public boolean isColdWaterEffectEnabled() {
        return coldWaterEffectEnabled;
    }

    public void setColdWaterEffectEnabled(boolean coldWaterEffectEnabled) {
        this.coldWaterEffectEnabled = coldWaterEffectEnabled;
        this.setDirty();
    }

    public void addBloodOverlay(String key, String value) {
	    bloodOverlays.put(key, value);
	    this.setDirty();
	}
	
	public void removeBloodOverlay(String key) {
	    bloodOverlays.remove(key);
	    this.setDirty();
	}
	
	public Map<String, String> getBloodOverlays() {
	    return new HashMap<>(bloodOverlays);
	}
	
	public void clearAllBloodOverlays() {
	    bloodOverlays.clear();
	    this.setDirty();
	}
}
