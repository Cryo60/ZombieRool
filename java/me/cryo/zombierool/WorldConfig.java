package me.cryo.zombierool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class WorldConfig extends SavedData {
    private static final String DATA_NAME = "zombierool_world_config";
    public static final TicketType<ChunkPos> PATH_TICKET = TicketType.create("zombierool_path", Comparator.comparingLong(ChunkPos::toLong));

    private Set<BlockPos> playerSpawnerPositions = new HashSet<>();
    private Set<BlockPos> mysteryBoxPositions = new HashSet<>();
    private Set<BlockPos> wunderfizzPositions = new HashSet<>();
    private BlockPos activeWunderfizzPosition = null;
    private Set<BlockPos> powerSwitchPositions = new HashSet<>(); 
    private Set<BlockPos> pathPositions = new HashSet<>(); 
    private transient Map<ChunkPos, Integer> chunkPathCounts = new HashMap<>();
    private Map<String, String> bloodOverlays = new HashMap<>();

    private String fogPreset = "normal";
    private float customFogR = 0f, customFogG = 0f, customFogB = 0f;
    private float customFogNear = 0.5f, customFogFar = 18.0f;
    private ResourceLocation starterItem = new ResourceLocation("zombierool", "m1911");
    private String dayNightMode = "night"; 
    
    private boolean particlesEnabled = false;
    private ResourceLocation particleTypeId = null;
    private String particleDensity = "normal";
    private String particleMode = "global";

    private String musicPreset = "none"; 
    private String eyeColorPreset = "default";
    private String voicePreset = "none"; 
    private boolean coldWaterEffectEnabled = false;
    private String deathPenalty = "respawn"; 

    private int baseZombies = 6;
    private int maxActiveMobsPerPlayer = 50;
    private float zombieBaseHealth = 4f;
    private float zombieMaxHealth = 1050f;
    private float hellhoundBaseHealth = 8f;
    private float hellhoundMaxHealth = 250f;
    private float crawlerBaseHealth = 5f;
    private float crawlerMaxHealth = 800f;

    private boolean sprintBgSounds = true;
    private boolean zombiesCanSprint = true;
    private int zombieSprintWave = 5;
    private float zombieSprintChance = 0.5f; 
    private float zombieSprintSpeed = 0.25f;

    private boolean superSprintersEnabled = false;
    private int superSprinterActivationWave = 6;
    private float superSprinterChance = 0.033f;
    private float superSprinterSpeed = 0.35f;

    private boolean hellhoundFireVariant = true;
    private boolean crawlerGasExplosion = true;

    private int specialWaveStart = 6;
    private int specialWaveInterval = 6;
    private boolean hellhoundsInNormalWaves = false;
    private int hellhoundsInNormalWavesStart = 15;

    private boolean bonusDropsEnabled = true;
    private Set<String> disabledBonuses = new HashSet<>();
    private Set<String> disabledRandomPerks = new HashSet<>();
    private Set<ResourceLocation> disabledBoxWeapons = new HashSet<>();
    private Set<ResourceLocation> customBoxWeapons = new HashSet<>();
    private Set<ResourceLocation> customWonderWeapons = new HashSet<>();
    private Set<String> mysteryBoxTags = new HashSet<>();

    public WorldConfig() {}

    public static WorldConfig get(ServerLevel world) {
        DimensionDataStorage storage = world.getDataStorage();
        return storage.computeIfAbsent(WorldConfig::load, WorldConfig::new, DATA_NAME);
    }

    public static WorldConfig load(CompoundTag nbt) {
        WorldConfig config = new WorldConfig();
        loadPositions(nbt, "playerSpawnerPositions", config.playerSpawnerPositions);
        loadPositions(nbt, "mysteryBoxPositions", config.mysteryBoxPositions);
        loadPositions(nbt, "powerSwitchPositions", config.powerSwitchPositions);
        loadPositions(nbt, "wunderfizzPositions", config.wunderfizzPositions);
        loadPositions(nbt, "pathPositions", config.pathPositions); 
        
        if (nbt.contains("activeWunderfizzPosition", 10)) {
            CompoundTag posTag = nbt.getCompound("activeWunderfizzPosition");
            config.activeWunderfizzPosition = new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z"));
        }

        if (nbt.contains("bloodOverlays", 10)) {
            CompoundTag overlaysTag = nbt.getCompound("bloodOverlays");
            for (String key : overlaysTag.getAllKeys()) {
                config.bloodOverlays.put(key, overlaysTag.getString(key));
            }
        }

        config.loadEditable(nbt);
        config.rebuildChunkCounts();
        return config;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        savePositions(compound, "playerSpawnerPositions", playerSpawnerPositions);
        savePositions(compound, "mysteryBoxPositions", mysteryBoxPositions);
        savePositions(compound, "powerSwitchPositions", powerSwitchPositions);
        savePositions(compound, "wunderfizzPositions", wunderfizzPositions);
        savePositions(compound, "pathPositions", pathPositions); 

        if (activeWunderfizzPosition != null) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", activeWunderfizzPosition.getX());
            posTag.putInt("y", activeWunderfizzPosition.getY());
            posTag.putInt("z", activeWunderfizzPosition.getZ());
            compound.put("activeWunderfizzPosition", posTag);
        }

        CompoundTag overlaysTag = new CompoundTag();
        for (Map.Entry<String, String> entry : bloodOverlays.entrySet()) {
            overlaysTag.putString(entry.getKey(), entry.getValue());
        }
        compound.put("bloodOverlays", overlaysTag);

        saveEditable(compound);
        return compound;
    }

    public void loadEditable(CompoundTag nbt) {
        if (nbt.contains("fogPreset")) fogPreset = nbt.getString("fogPreset");
        if (nbt.contains("customFogR")) customFogR = nbt.getFloat("customFogR");
        if (nbt.contains("customFogG")) customFogG = nbt.getFloat("customFogG");
        if (nbt.contains("customFogB")) customFogB = nbt.getFloat("customFogB");
        if (nbt.contains("customFogNear")) customFogNear = nbt.getFloat("customFogNear");
        if (nbt.contains("customFogFar")) customFogFar = nbt.getFloat("customFogFar");
        if (nbt.contains("starterItem")) starterItem = new ResourceLocation(nbt.getString("starterItem"));
        if (nbt.contains("dayNightMode")) dayNightMode = nbt.getString("dayNightMode");

        if (nbt.contains("particlesEnabled")) particlesEnabled = nbt.getBoolean("particlesEnabled");
        if (nbt.contains("particleTypeId")) particleTypeId = new ResourceLocation(nbt.getString("particleTypeId"));
        if (nbt.contains("particleDensity")) particleDensity = nbt.getString("particleDensity");
        if (nbt.contains("particleMode")) particleMode = nbt.getString("particleMode");

        if (nbt.contains("musicPreset")) musicPreset = nbt.getString("musicPreset");
        if (nbt.contains("eyeColorPreset")) eyeColorPreset = nbt.getString("eyeColorPreset");
        if (nbt.contains("voicePreset")) voicePreset = nbt.getString("voicePreset");
        if (nbt.contains("coldWaterEffectEnabled")) coldWaterEffectEnabled = nbt.getBoolean("coldWaterEffectEnabled");
        if (nbt.contains("deathPenalty")) deathPenalty = nbt.getString("deathPenalty");

        if (nbt.contains("baseZombies")) baseZombies = nbt.getInt("baseZombies");
        if (nbt.contains("maxActiveMobsPerPlayer")) maxActiveMobsPerPlayer = nbt.getInt("maxActiveMobsPerPlayer");
        
        if (nbt.contains("zombieBaseHealth")) zombieBaseHealth = nbt.getFloat("zombieBaseHealth");
        if (nbt.contains("zombieMaxHealth")) zombieMaxHealth = nbt.getFloat("zombieMaxHealth");
        if (nbt.contains("hellhoundBaseHealth")) hellhoundBaseHealth = nbt.getFloat("hellhoundBaseHealth");
        if (nbt.contains("hellhoundMaxHealth")) hellhoundMaxHealth = nbt.getFloat("hellhoundMaxHealth");
        if (nbt.contains("crawlerBaseHealth")) crawlerBaseHealth = nbt.getFloat("crawlerBaseHealth");
        if (nbt.contains("crawlerMaxHealth")) crawlerMaxHealth = nbt.getFloat("crawlerMaxHealth");

        if (nbt.contains("sprintBgSounds")) sprintBgSounds = nbt.getBoolean("sprintBgSounds");
        if (nbt.contains("zombiesCanSprint")) zombiesCanSprint = nbt.getBoolean("zombiesCanSprint");
        if (nbt.contains("zombieSprintWave")) zombieSprintWave = nbt.getInt("zombieSprintWave");
        if (nbt.contains("zombieSprintChance")) zombieSprintChance = nbt.getFloat("zombieSprintChance");
        if (nbt.contains("zombieSprintSpeed")) zombieSprintSpeed = nbt.getFloat("zombieSprintSpeed");

        if (nbt.contains("superSprintersEnabled")) superSprintersEnabled = nbt.getBoolean("superSprintersEnabled");
        if (nbt.contains("superSprinterActivationWave")) superSprinterActivationWave = nbt.getInt("superSprinterActivationWave");
        if (nbt.contains("superSprinterChance")) superSprinterChance = nbt.getFloat("superSprinterChance");
        if (nbt.contains("superSprinterSpeed")) superSprinterSpeed = nbt.getFloat("superSprinterSpeed");

        if (nbt.contains("hellhoundFireVariant")) hellhoundFireVariant = nbt.getBoolean("hellhoundFireVariant");
        if (nbt.contains("crawlerGasExplosion")) crawlerGasExplosion = nbt.getBoolean("crawlerGasExplosion");

        if (nbt.contains("specialWaveStart")) specialWaveStart = nbt.getInt("specialWaveStart");
        if (nbt.contains("specialWaveInterval")) specialWaveInterval = nbt.getInt("specialWaveInterval");
        if (nbt.contains("hellhoundsInNormalWaves")) hellhoundsInNormalWaves = nbt.getBoolean("hellhoundsInNormalWaves");
        if (nbt.contains("hellhoundsInNormalWavesStart")) hellhoundsInNormalWavesStart = nbt.getInt("hellhoundsInNormalWavesStart");

        if (nbt.contains("bonusDropsEnabled")) bonusDropsEnabled = nbt.getBoolean("bonusDropsEnabled");

        if (nbt.contains("disabledBoxWeapons", 9)) {
            disabledBoxWeapons.clear();
            ListTag list = nbt.getList("disabledBoxWeapons", 8);
            for (int i = 0; i < list.size(); i++) disabledBoxWeapons.add(new ResourceLocation(list.getString(i)));
        }
        if (nbt.contains("customBoxWeapons", 9)) {
            customBoxWeapons.clear();
            ListTag list = nbt.getList("customBoxWeapons", 8);
            for (int i = 0; i < list.size(); i++) customBoxWeapons.add(new ResourceLocation(list.getString(i)));
        }
        if (nbt.contains("customWonderWeapons", 9)) {
            customWonderWeapons.clear();
            ListTag list = nbt.getList("customWonderWeapons", 8);
            for (int i = 0; i < list.size(); i++) customWonderWeapons.add(new ResourceLocation(list.getString(i)));
        }
        if (nbt.contains("disabledBonuses", 9)) {
            disabledBonuses.clear();
            ListTag list = nbt.getList("disabledBonuses", 8);
            for (int i = 0; i < list.size(); i++) disabledBonuses.add(list.getString(i));
        }
        if (nbt.contains("disabledRandomPerks", 9)) {
            disabledRandomPerks.clear();
            ListTag list = nbt.getList("disabledRandomPerks", 8);
            for (int i = 0; i < list.size(); i++) disabledRandomPerks.add(list.getString(i));
        }
        if (nbt.contains("mysteryBoxTags", 9)) {
            mysteryBoxTags.clear();
            ListTag list = nbt.getList("mysteryBoxTags", 8);
            for (int i = 0; i < list.size(); i++) mysteryBoxTags.add(list.getString(i));
        }
    }

    public void saveEditable(CompoundTag compound) {
        compound.putString("fogPreset", fogPreset);
        compound.putFloat("customFogR", customFogR);
        compound.putFloat("customFogG", customFogG);
        compound.putFloat("customFogB", customFogB);
        compound.putFloat("customFogNear", customFogNear);
        compound.putFloat("customFogFar", customFogFar);
        compound.putString("starterItem", starterItem.toString());
        compound.putString("dayNightMode", dayNightMode);

        compound.putBoolean("particlesEnabled", particlesEnabled);
        if (particleTypeId != null) compound.putString("particleTypeId", particleTypeId.toString());
        compound.putString("particleDensity", particleDensity);
        compound.putString("particleMode", particleMode);

        compound.putString("musicPreset", musicPreset);
        compound.putString("eyeColorPreset", eyeColorPreset);
        compound.putString("voicePreset", voicePreset);
        compound.putBoolean("coldWaterEffectEnabled", coldWaterEffectEnabled);
        compound.putString("deathPenalty", deathPenalty);

        compound.putInt("baseZombies", baseZombies);
        compound.putInt("maxActiveMobsPerPlayer", maxActiveMobsPerPlayer);
        
        compound.putFloat("zombieBaseHealth", zombieBaseHealth);
        compound.putFloat("zombieMaxHealth", zombieMaxHealth);
        compound.putFloat("hellhoundBaseHealth", hellhoundBaseHealth);
        compound.putFloat("hellhoundMaxHealth", hellhoundMaxHealth);
        compound.putFloat("crawlerBaseHealth", crawlerBaseHealth);
        compound.putFloat("crawlerMaxHealth", crawlerMaxHealth);

        compound.putBoolean("sprintBgSounds", sprintBgSounds);
        compound.putBoolean("zombiesCanSprint", zombiesCanSprint);
        compound.putInt("zombieSprintWave", zombieSprintWave);
        compound.putFloat("zombieSprintChance", zombieSprintChance);
        compound.putFloat("zombieSprintSpeed", zombieSprintSpeed);

        compound.putBoolean("superSprintersEnabled", superSprintersEnabled);
        compound.putInt("superSprinterActivationWave", superSprinterActivationWave);
        compound.putFloat("superSprinterChance", superSprinterChance);
        compound.putFloat("superSprinterSpeed", superSprinterSpeed);

        compound.putBoolean("hellhoundFireVariant", hellhoundFireVariant);
        compound.putBoolean("crawlerGasExplosion", crawlerGasExplosion);

        compound.putInt("specialWaveStart", specialWaveStart);
        compound.putInt("specialWaveInterval", specialWaveInterval);
        compound.putBoolean("hellhoundsInNormalWaves", hellhoundsInNormalWaves);
        compound.putInt("hellhoundsInNormalWavesStart", hellhoundsInNormalWavesStart);

        compound.putBoolean("bonusDropsEnabled", bonusDropsEnabled);

        ListTag wpns = new ListTag();
        for (ResourceLocation id : disabledBoxWeapons) wpns.add(StringTag.valueOf(id.toString()));
        compound.put("disabledBoxWeapons", wpns);

        ListTag customWpns = new ListTag();
        for (ResourceLocation id : customBoxWeapons) customWpns.add(StringTag.valueOf(id.toString()));
        compound.put("customBoxWeapons", customWpns);

        ListTag customWonder = new ListTag();
        for (ResourceLocation id : customWonderWeapons) customWonder.add(StringTag.valueOf(id.toString()));
        compound.put("customWonderWeapons", customWonder);

        ListTag bons = new ListTag();
        for (String id : disabledBonuses) bons.add(StringTag.valueOf(id));
        compound.put("disabledBonuses", bons);

        ListTag perks = new ListTag();
        for (String id : disabledRandomPerks) perks.add(StringTag.valueOf(id));
        compound.put("disabledRandomPerks", perks);

        ListTag boxTags = new ListTag();
        for (String id : mysteryBoxTags) boxTags.add(StringTag.valueOf(id));
        compound.put("mysteryBoxTags", boxTags);
    }

    public void resetGeneral() {
        dayNightMode = "night";
        musicPreset = "none";
        eyeColorPreset = "default";
        voicePreset = "none";
        particlesEnabled = false;
        particleTypeId = null;
        particleDensity = "normal";
        particleMode = "global";
        deathPenalty = "respawn";
        coldWaterEffectEnabled = false;
        sprintBgSounds = true;
        setDirty();
    }

    public void resetMobs() {
        zombieBaseHealth = 4f;
        zombieMaxHealth = 1050f;
        crawlerBaseHealth = 5f;
        crawlerMaxHealth = 800f;
        hellhoundBaseHealth = 8f;
        hellhoundMaxHealth = 250f;
        zombiesCanSprint = true;
        zombieSprintWave = 5;
        zombieSprintChance = 0.5f;
        zombieSprintSpeed = 0.25f;
        superSprintersEnabled = false;
        superSprinterActivationWave = 6;
        superSprinterChance = 0.033f;
        superSprinterSpeed = 0.35f;
        hellhoundFireVariant = true;
        crawlerGasExplosion = true;
        setDirty();
    }

    public void resetWaves() {
        baseZombies = 6;
        maxActiveMobsPerPlayer = 50;
        specialWaveStart = 6;
        specialWaveInterval = 6;
        hellhoundsInNormalWaves = false;
        hellhoundsInNormalWavesStart = 15;
        setDirty();
    }

    public void resetLoot() {
        bonusDropsEnabled = true;
        disabledBonuses.clear();
        disabledRandomPerks.clear();
        disabledBoxWeapons.clear();
        customBoxWeapons.clear();
        customWonderWeapons.clear();
        mysteryBoxTags.clear();
        setDirty();
    }

    public void resetFog() {
        fogPreset = "normal";
        customFogR = 0f; customFogG = 0f; customFogB = 0f;
        customFogNear = 0.5f; customFogFar = 18.0f;
        setDirty();
    }

    private static void loadPositions(CompoundTag nbt, String key, Set<BlockPos> set) {
        if (nbt.contains(key, 9)) {
            ListTag list = nbt.getList(key, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                set.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
            }
        }
    }

    private void savePositions(CompoundTag nbt, String key, Set<BlockPos> set) {
        ListTag list = new ListTag();
        for (BlockPos pos : set) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            list.add(posTag);
        }
        nbt.put(key, list);
    }

    private void rebuildChunkCounts() {
        if (chunkPathCounts == null) chunkPathCounts = new HashMap<>();
        else chunkPathCounts.clear();
        for (BlockPos pos : pathPositions) {
            ChunkPos cp = new ChunkPos(pos);
            chunkPathCounts.put(cp, chunkPathCounts.getOrDefault(cp, 0) + 1);
        }
    }

    public void refreshAllChunkTickets(ServerLevel level) {
        if (level == null) return;
        if (chunkPathCounts == null) rebuildChunkCounts();
        for (ChunkPos chunkPos : chunkPathCounts.keySet()) {
            level.getChunkSource().addRegionTicket(PATH_TICKET, chunkPos, 2, chunkPos);
        }
    }

    public String getFogPreset() { return fogPreset; }
    public void setFogPreset(String fogPreset) { this.fogPreset = fogPreset; this.setDirty(); }
    public float getCustomFogR() { return customFogR; }
    public void setCustomFogR(float customFogR) { this.customFogR = customFogR; this.setDirty(); }
    public float getCustomFogG() { return customFogG; }
    public void setCustomFogG(float customFogG) { this.customFogG = customFogG; this.setDirty(); }
    public float getCustomFogB() { return customFogB; }
    public void setCustomFogB(float customFogB) { this.customFogB = customFogB; this.setDirty(); }
    public float getCustomFogNear() { return customFogNear; }
    public void setCustomFogNear(float customFogNear) { this.customFogNear = customFogNear; this.setDirty(); }
    public float getCustomFogFar() { return customFogFar; }
    public void setCustomFogFar(float customFogFar) { this.customFogFar = customFogFar; this.setDirty(); }
    
    public ResourceLocation getStarterItem() { return starterItem; }
    public void setStarterItem(ResourceLocation starterItem) { this.starterItem = starterItem; this.setDirty(); }
    public String getDayNightMode() { return dayNightMode; }
    public void setDayNightMode(String dayNightMode) { this.dayNightMode = dayNightMode; this.setDirty(); }

    public Set<ResourceLocation> getDisabledBoxWeapons() { return disabledBoxWeapons; }
    public boolean isBoxWeaponDisabled(ResourceLocation id) { return disabledBoxWeapons.contains(id); }
    public void setDisabledBoxWeapons(Set<ResourceLocation> set) { disabledBoxWeapons = new HashSet<>(set); setDirty(); }

    public Set<ResourceLocation> getCustomBoxWeapons() { return customBoxWeapons; }
    public void setCustomBoxWeapons(Set<ResourceLocation> set) { customBoxWeapons = new HashSet<>(set); setDirty(); }

    public Set<ResourceLocation> getCustomWonderWeapons() { return customWonderWeapons; }
    public void setCustomWonderWeapons(Set<ResourceLocation> set) { customWonderWeapons = new HashSet<>(set); setDirty(); }

    public Set<String> getDisabledBonuses() { return disabledBonuses; }
    public boolean isBonusDisabled(String id) { return disabledBonuses.contains(id); }
    public void setDisabledBonuses(Set<String> set) { disabledBonuses = new HashSet<>(set); setDirty(); }

    public Set<String> getDisabledRandomPerks() { return disabledRandomPerks; }
    public boolean isRandomPerkDisabled(String id) { return disabledRandomPerks.contains(id); }
    public void setDisabledRandomPerks(Set<String> set) { disabledRandomPerks = new HashSet<>(set); setDirty(); }

    public Set<String> getMysteryBoxTags() { return mysteryBoxTags; }
    public void setMysteryBoxTags(Set<String> set) { mysteryBoxTags = new HashSet<>(set); setDirty(); }

    public boolean areParticlesEnabled() { return particlesEnabled; }
    public ResourceLocation getParticleTypeId() { return particleTypeId; }
    public String getParticleDensity() { return particleDensity; }
    public String getParticleMode() { return particleMode; }
    public void enableParticles(ResourceLocation typeId, String density, String mode) { this.particlesEnabled = true; this.particleTypeId = typeId; this.particleDensity = density; this.particleMode = mode; this.setDirty(); }
    public void disableParticles() { this.particlesEnabled = false; this.particleTypeId = null; this.particleDensity = "normal"; this.particleMode = "global"; this.setDirty(); }
    
    public String getMusicPreset() { return musicPreset; }
    public void setMusicPreset(String musicPreset) { this.musicPreset = musicPreset; this.setDirty(); }

    public String getEyeColorPreset() { return eyeColorPreset; }
    public void setEyeColorPreset(String eyeColorPreset) { this.eyeColorPreset = eyeColorPreset; this.setDirty(); }

    public String getVoicePreset() { return voicePreset; }
    public void setVoicePreset(String voicePreset) { this.voicePreset = voicePreset; this.setDirty(); }

    public boolean isColdWaterEffectEnabled() { return coldWaterEffectEnabled; }
    public void setColdWaterEffectEnabled(boolean enabled) { this.coldWaterEffectEnabled = enabled; this.setDirty(); }

    public void addPlayerSpawnerPosition(BlockPos pos) { if (playerSpawnerPositions.add(pos)) this.setDirty(); }
    public void removePlayerSpawnerPosition(BlockPos pos) { if (playerSpawnerPositions.remove(pos)) this.setDirty(); }
    public Set<BlockPos> getPlayerSpawnerPositions() { return Collections.unmodifiableSet(playerSpawnerPositions); }

    public void addMysteryBoxPosition(BlockPos pos) { if (mysteryBoxPositions.add(pos)) this.setDirty(); }
    public void removeMysteryBoxPosition(BlockPos pos) { if (mysteryBoxPositions.remove(pos)) this.setDirty(); }
    public Set<BlockPos> getMysteryBoxPositions() { return Collections.unmodifiableSet(mysteryBoxPositions); }

    public void addPowerSwitchPosition(BlockPos pos) { if (powerSwitchPositions.add(pos)) this.setDirty(); }
    public void removePowerSwitchPosition(BlockPos pos) { if (powerSwitchPositions.remove(pos)) this.setDirty(); }
    public Set<BlockPos> getPowerSwitchPositions() { return Collections.unmodifiableSet(powerSwitchPositions); }

    public void addWunderfizzPosition(BlockPos pos) { if (wunderfizzPositions.add(pos)) this.setDirty(); }
    public void removeWunderfizzPosition(BlockPos pos) { if (wunderfizzPositions.remove(pos)) { if (pos.equals(activeWunderfizzPosition)) activeWunderfizzPosition = null; this.setDirty(); } }
    public Set<BlockPos> getWunderfizzPositions() { return Collections.unmodifiableSet(wunderfizzPositions); }

    public void addPathPosition(BlockPos pos, ServerLevel level) {
        if (chunkPathCounts == null) rebuildChunkCounts();
        ChunkPos chunkPos = new ChunkPos(pos);
        if (pathPositions.add(pos)) {
            this.setDirty();
            int newCount = chunkPathCounts.getOrDefault(chunkPos, 0) + 1;
            chunkPathCounts.put(chunkPos, newCount);
            if (newCount == 1 && level != null) level.getChunkSource().addRegionTicket(PATH_TICKET, chunkPos, 2, chunkPos);
        }
    }
    
    public void removePathPosition(BlockPos pos, ServerLevel level) {
        if (chunkPathCounts == null) rebuildChunkCounts();
        ChunkPos chunkPos = new ChunkPos(pos);
        if (pathPositions.remove(pos)) {
            this.setDirty();
            int newCount = chunkPathCounts.getOrDefault(chunkPos, 1) - 1;
            if (newCount <= 0) {
                chunkPathCounts.remove(chunkPos);
                if (level != null) level.getChunkSource().removeRegionTicket(PATH_TICKET, chunkPos, 2, chunkPos);
            } else {
                chunkPathCounts.put(chunkPos, newCount);
            }
        }
    }

    public Set<BlockPos> getPathPositions() { return Collections.unmodifiableSet(pathPositions); }

    public void setActiveWunderfizzPosition(BlockPos pos, ServerLevel level) {
        this.activeWunderfizzPosition = pos;
        this.setDirty();
        me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
            net.minecraftforge.network.PacketDistributor.DIMENSION.with(() -> level.dimension()),
            new me.cryo.zombierool.network.SyncActiveWunderfizzPositionPacket(pos)
        );
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerJoin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            WorldConfig config = WorldConfig.get(level);
            BlockPos activePos = config.getActiveWunderfizzPosition();
            me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new me.cryo.zombierool.network.SyncActiveWunderfizzPositionPacket(activePos)
            );
        }
    }

    public BlockPos getActiveWunderfizzPosition() { return activeWunderfizzPosition; }
    public boolean isWunderfizzActive(BlockPos pos) { return pos.equals(activeWunderfizzPosition); }
    public void clearActiveWunderfizz() { this.activeWunderfizzPosition = null; this.setDirty(); }

    public void addBloodOverlay(String key, String value) { bloodOverlays.put(key, value); this.setDirty(); }
    public void removeBloodOverlay(String key) { bloodOverlays.remove(key); this.setDirty(); }
    public Map<String, String> getBloodOverlays() { return new HashMap<>(bloodOverlays); }
    public void clearAllBloodOverlays() { bloodOverlays.clear(); this.setDirty(); }

    public int getBaseZombies() { return baseZombies; }
    public void setBaseZombies(int v) { this.baseZombies = v; this.setDirty(); }

    public int getMaxActiveMobsPerPlayer() { return maxActiveMobsPerPlayer; }
    public void setMaxActiveMobsPerPlayer(int v) { this.maxActiveMobsPerPlayer = v; this.setDirty(); }

    public float getZombieBaseHealth() { return zombieBaseHealth; }
    public void setZombieBaseHealth(float v) { this.zombieBaseHealth = v; this.setDirty(); }

    public float getZombieMaxHealth() { return zombieMaxHealth; }
    public void setZombieMaxHealth(float v) { this.zombieMaxHealth = v; this.setDirty(); }

    public float getHellhoundBaseHealth() { return hellhoundBaseHealth; }
    public void setHellhoundBaseHealth(float v) { this.hellhoundBaseHealth = v; this.setDirty(); }

    public float getHellhoundMaxHealth() { return hellhoundMaxHealth; }
    public void setHellhoundMaxHealth(float v) { this.hellhoundMaxHealth = v; this.setDirty(); }

    public float getCrawlerBaseHealth() { return crawlerBaseHealth; }
    public void setCrawlerBaseHealth(float v) { this.crawlerBaseHealth = v; this.setDirty(); }

    public float getCrawlerMaxHealth() { return crawlerMaxHealth; }
    public void setCrawlerMaxHealth(float v) { this.crawlerMaxHealth = v; this.setDirty(); }

    public boolean isSprintBgSoundsEnabled() { return sprintBgSounds; }
    public void setSprintBgSounds(boolean v) { this.sprintBgSounds = v; this.setDirty(); }

    public boolean isZombiesCanSprint() { return zombiesCanSprint; }
    public void setZombiesCanSprint(boolean v) { this.zombiesCanSprint = v; this.setDirty(); }

    public int getZombieSprintWave() { return zombieSprintWave; }
    public void setZombieSprintWave(int v) { this.zombieSprintWave = v; this.setDirty(); }

    public float getZombieSprintChance() { return zombieSprintChance; }
    public void setZombieSprintChance(float v) { this.zombieSprintChance = v; this.setDirty(); }

    public float getZombieSprintSpeed() { return zombieSprintSpeed; }
    public void setZombieSprintSpeed(float v) { this.zombieSprintSpeed = v; this.setDirty(); }

    public boolean areSuperSprintersEnabled() { return superSprintersEnabled; }
    public void setSuperSprintersEnabled(boolean enabled) { this.superSprintersEnabled = enabled; this.setDirty(); }

    public int getSuperSprinterActivationWave() { return superSprinterActivationWave; }
    public void setSuperSprinterActivationWave(int v) { this.superSprinterActivationWave = v; this.setDirty(); }

    public float getSuperSprinterChance() { return superSprinterChance; }
    public void setSuperSprinterChance(float v) { this.superSprinterChance = v; this.setDirty(); }

    public float getSuperSprinterSpeed() { return superSprinterSpeed; }
    public void setSuperSprinterSpeed(float v) { this.superSprinterSpeed = v; this.setDirty(); }

    public boolean isHellhoundFireVariant() { return hellhoundFireVariant; }
    public void setHellhoundFireVariant(boolean v) { this.hellhoundFireVariant = v; this.setDirty(); }

    public boolean isCrawlerGasExplosion() { return crawlerGasExplosion; }
    public void setCrawlerGasExplosion(boolean v) { this.crawlerGasExplosion = v; this.setDirty(); }

    public int getSpecialWaveStart() { return specialWaveStart; }
    public void setSpecialWaveStart(int v) { this.specialWaveStart = v; this.setDirty(); }

    public int getSpecialWaveInterval() { return specialWaveInterval; }
    public void setSpecialWaveInterval(int v) { this.specialWaveInterval = v; this.setDirty(); }

    public boolean isHellhoundsInNormalWaves() { return hellhoundsInNormalWaves; }
    public void setHellhoundsInNormalWaves(boolean v) { this.hellhoundsInNormalWaves = v; this.setDirty(); }

    public int getHellhoundsInNormalWavesStart() { return hellhoundsInNormalWavesStart; }
    public void setHellhoundsInNormalWavesStart(int v) { this.hellhoundsInNormalWavesStart = v; this.setDirty(); }

    public boolean isBonusDropsEnabled() { return bonusDropsEnabled; }
    public void setBonusDropsEnabled(boolean v) { this.bonusDropsEnabled = v; this.setDirty(); }

    public String getDeathPenalty() { return deathPenalty; }
    public void setDeathPenalty(String v) { this.deathPenalty = v; this.setDirty(); }
}