package me.cryo.zombierool.bonuses;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cryo.zombierool.api.IReloadable;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.PlayGlobalSoundPacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.system.WeaponFacade;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BonusManager {
	private static final Random RANDOM = new Random();
	private static final Gson GSON = new GsonBuilder().setLenient().create();

	public static final Map<String, Bonus> ALL_BONUSES = new HashMap<>();
	private static final Map<String, BonusAction> ACTIONS = new HashMap<>();

	private static final Map<UUID, Integer> INSTA_KILL_ACTIVE_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> DOUBLE_POINTS_ACTIVE_TICKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Integer> ZOMBIE_BLOOD_ACTIVE_TICKS = new ConcurrentHashMap<>();
	
	private static final Map<UUID, Integer> ON_THE_HOUSE_OVERRIDE_COUNT = new ConcurrentHashMap<>();
	private static final int MAX_ON_THE_HOUSE_OVERRIDES = 2; 

	private static final int BONUS_LIFESPAN_TICKS = 20 * 15; 
	private static final int LOOP_SOUND_INTERVAL_TICKS = 20;

	public static boolean bonusSpawnDisabledByNuke = false;
	private static long nukeSpawnDisableEndTime = 0;
	private static final Queue<NukeSoundEvent> NUKE_SOUND_QUEUE = new LinkedList<>();

	public static final SoundEvent POWER_UP_GRAB_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "power_up_grab"));
	public static final SoundEvent POWER_UP_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "power_up_loop"));
	public static final SoundEvent CARP_END_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "carp_end"));
	public static final SoundEvent ANN_CARPENTER_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_carpenter"));
	public static final SoundEvent CARP_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "carp_loop"));
	public static final SoundEvent DOUBLE_POINT_END_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "double_point_end"));
	public static final SoundEvent DOUBLE_POINT_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "double_point_loop"));
	public static final SoundEvent ANN_DOUBLEPOINTS_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_doublepoints"));
	public static final SoundEvent INSTA_KILL_END_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "insta_kill_end"));
	public static final SoundEvent INSTA_KILL_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "insta_kill_loop"));
	public static final SoundEvent ANN_INSTAKILL_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_instakill"));
	public static final SoundEvent ANN_MAXAMMO_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_maxammo"));
	public static final SoundEvent FULL_AMMO_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "full_ammo"));
	public static final SoundEvent ANN_NUKE_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_nuke"));
	public static final SoundEvent NUKE_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "nuke"));

	public static class BonusDefinition {
	    public String id;
	    public double chance = 0.0;
	    public int duration_ticks = 0;
	    public String action_id;
	    public String particle_id;
	}

	public static class Bonus {
	    public final String id;
	    public final double chance;
	    public final int duration;
	    public final String action_id;
	    public final SimpleParticleType particleType;

	    public Bonus(BonusDefinition def) {
	        this.id = def.id;
	        this.chance = def.chance;
	        this.duration = def.duration_ticks;
	        this.action_id = def.action_id;
	        if (def.particle_id != null && !def.particle_id.isEmpty()) {
	            this.particleType = (SimpleParticleType) ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(def.particle_id));
	        } else {
	            this.particleType = null;
	        }
	    }
	}

	@FunctionalInterface
	public interface BonusAction {
	    void apply(Player player, ServerLevel level, Vec3 pos, Bonus bonus);
	}

	private static class ActiveBonus {
	    public final Bonus bonus;
	    public final Vec3 position;
	    public final long spawnTime;
	    public final UUID bonusId;

	    public ActiveBonus(Bonus bonus, Vec3 position, long spawnTime) {
	        this.bonus = bonus;
	        this.position = position;
	        this.spawnTime = spawnTime;
	        this.bonusId = UUID.randomUUID();
	    }
	}

	private static final Map<UUID, ActiveBonus> ACTIVE_BONUSES_IN_WORLD = new ConcurrentHashMap<>();

	private static class NukeSoundEvent {
	    public final Vec3 position;
	    public final long playTime;

	    public NukeSoundEvent(Vec3 position, long playTime) {
	        this.position = position;
	        this.playTime = playTime;
	    }
	}

	@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class RegistryEvents {
	    @SubscribeEvent
	    public static void init(FMLCommonSetupEvent event) {
	        event.enqueueWork(() -> {
	            registerActions();
	            loadBonuses();
	        });
	    }
	}

	public static Bonus getBonus(String id) {
	    return ALL_BONUSES.get(id);
	}

	public static boolean isInstaKillActive(Player player) {
	    return INSTA_KILL_ACTIVE_TICKS.containsKey(player.getUUID()) && INSTA_KILL_ACTIVE_TICKS.get(player.getUUID()) > 0;
	}

	public static boolean isDoublePointsActive(Player player) {
	    return DOUBLE_POINTS_ACTIVE_TICKS.containsKey(player.getUUID()) && DOUBLE_POINTS_ACTIVE_TICKS.get(player.getUUID()) > 0;
	}

	public static boolean isZombieBloodActive(Player player) {
	    return ZOMBIE_BLOOD_ACTIVE_TICKS.containsKey(player.getUUID()) && ZOMBIE_BLOOD_ACTIVE_TICKS.get(player.getUUID()) > 0;
	}

	private static boolean isEnglishClient(Player player) {
	    if (FMLLoader.getDist().isClient()) {
	        return isEnglishClientSide();
	    }
	    return true; 
	}

	@OnlyIn(Dist.CLIENT)
	private static boolean isEnglishClientSide() {
	    if (Minecraft.getInstance() == null) return false;
	    return Minecraft.getInstance().options.languageCode.startsWith("en");
	}

	private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
	    if (isEnglishClient(player)) {
	        return Component.literal(englishMessage);
	    }
	    return Component.literal(frenchMessage);
	}

	private static void registerActions() {
	    ACTIONS.put("max_ammo", (player, level, pos, bonus) -> {
	        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(ANN_MAXAMMO_SOUND.getLocation(), 1.0f, 1.0f));
	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            for (ItemStack stack : targetPlayer.getInventory().items) {
	                if (stack.getItem() instanceof IReloadable reloadableWeapon) {
	                    reloadableWeapon.setAmmo(stack, reloadableWeapon.getMaxAmmo(stack));
	                    reloadableWeapon.setReserve(stack, reloadableWeapon.getMaxReserve(stack));
	                    reloadableWeapon.setReloadTimer(stack, 0);
	                }
	                if (stack.getItem() instanceof WeaponSystem.BaseGunItem gun && gun.hasDurability()) {
	                    gun.setDurability(stack, gun.getMaxDurability(stack));
	                }
	            }
	            WeaponFacade.refillAllTaczAmmo(targetPlayer);
	            targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Vos munitions ont été restaurées !", "Your ammo has been restored!")); 
	            targetPlayer.inventoryMenu.broadcastChanges();
	            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> targetPlayer), new PlayGlobalSoundPacket(FULL_AMMO_SOUND.getLocation(), 1.0f, 1.0f));
	        }
	    });

	    ACTIONS.put("nuke", (player, level, pos, bonus) -> {
	        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(ANN_NUKE_SOUND.getLocation(), 1.0f, 1.0f));
	        
	        int numNukes = RANDOM.nextInt(4) + 4; 
	        long currentScheduleTime = level.getGameTime();
	        
	        for (int i = 0; i < numNukes; i++) {
	            long delayTicks = (long) (RANDOM.nextDouble() * (20 * 0.6) + (20 * 0.2)); 
	            if (i == 0) delayTicks = 0; 
	            currentScheduleTime += delayTicks;
	            
	            NUKE_SOUND_QUEUE.offer(new NukeSoundEvent(pos, currentScheduleTime));
	        }

	        bonusSpawnDisabledByNuke = true;
	        nukeSpawnDisableEndTime = level.getGameTime() + 20 * 1; 

	        List<LivingEntity> mobsToKill = StreamSupport.stream(level.getAllEntities().spliterator(), false)
	            .filter(entity -> entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity)
	            .map(entity -> (LivingEntity) entity)
	            .collect(Collectors.toList());

	        for (LivingEntity livingEntity : mobsToKill) {
	            livingEntity.setSecondsOnFire(2);
	            livingEntity.setHealth(0); 
	            livingEntity.die(level.damageSources().magic()); 
	        }
	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            PointManager.modifyScore(targetPlayer, 400);
	            targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Vous avez gagné 400 points !", "You gained 400 points!"));
	        }
	    });

	    ACTIONS.put("insta_kill", (player, level, pos, bonus) -> {
	        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(ANN_INSTAKILL_SOUND.getLocation(), 1.0f, 1.0f));
	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            INSTA_KILL_ACTIVE_TICKS.put(targetPlayer.getUUID(), bonus.duration > 0 ? bonus.duration : 600);
	        }
	    });

	    ACTIONS.put("double_points", (player, level, pos, bonus) -> {
	        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(ANN_DOUBLEPOINTS_SOUND.getLocation(), 1.0f, 1.0f));
	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            DOUBLE_POINTS_ACTIVE_TICKS.put(targetPlayer.getUUID(), bonus.duration > 0 ? bonus.duration : 900);
	        }
	    });

	    ACTIONS.put("carpenter", (player, level, pos, bonus) -> {
	        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(ANN_CARPENTER_SOUND.getLocation(), 1.0f, 1.0f));
	        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(CARP_LOOP_SOUND.getLocation(), 1.0f, 1.0f));
	        
	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            PointManager.modifyScore(targetPlayer, 200);
	            targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Vous avez gagné 200 points pour Carpenter !", "You gained 200 points for Carpenter!"));
	        }
	        
	        int searchRadius = 100;
	        for (int x = (int)pos.x - searchRadius; x <= (int)pos.x + searchRadius; x++) {
	            for (int y = (int)pos.y - searchRadius; y <= (int)pos.y + searchRadius; y++) {
	                for (int z = (int)pos.z - searchRadius; z <= (int)pos.z + searchRadius; z++) {
	                    BlockPos currentBlockPos = new BlockPos(x, y, z);
	                    BlockState blockState = level.getBlockState(currentBlockPos);
	                    
	                    if (blockState.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock) {
	                        DefenseDoorSystem.DefenseDoorBlock defenseDoor = (DefenseDoorSystem.DefenseDoorBlock) blockState.getBlock();
	                        if (blockState.getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE) < DefenseDoorSystem.DefenseDoorBlock.MAX_STAGE) {
	                            defenseDoor.updateStage(level, currentBlockPos, DefenseDoorSystem.DefenseDoorBlock.MAX_STAGE);
	                        }
	                    }
	                }
	            }
	        }
	    });
		
	    ACTIONS.put("gold_rush", (player, level, pos, bonus) -> {
		    int randomMultiplier = RANDOM.nextInt(5) + 2;
		    int points = randomMultiplier * 100;
		    player.sendSystemMessage(getTranslatedComponent(player, "Bonus: Gold Rush! Vous avez gagné " + points + " points !", "Bonus: Gold Rush! You gained " + points + " points!"));
		    PointManager.modifyScore(player, points);
		});
		
		ACTIONS.put("zombie_blood", (player, level, pos, bonus) -> {
	        ZOMBIE_BLOOD_ACTIVE_TICKS.put(player.getUUID(), bonus.duration > 0 ? bonus.duration : 200);
	        player.sendSystemMessage(getTranslatedComponent(player, "Bonus: Zombie Blood! Les zombies vous ignorent !", "Bonus: Zombie Blood! Zombies ignore you!"));
	    });

	    ACTIONS.put("wish", (player, level, pos, bonus) -> {
	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            targetPlayer.setHealth(targetPlayer.getMaxHealth());
	            targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Bonus: Wish! Vous avez été soigné !", "Bonus: Wish! You have been healed!"));
	        }
	    });

	    ACTIONS.put("on_the_house", (player, level, pos, bonus) -> {
	        int currentOverrides = ON_THE_HOUSE_OVERRIDE_COUNT.getOrDefault(player.getUUID(), 0);
	        int currentPerkCount = PerksManager.getPerkCount(player);

	        if (currentPerkCount < PerksManager.MAX_PERKS_LIMIT || currentOverrides < MAX_ON_THE_HOUSE_OVERRIDES) {
	            List<PerksManager.Perk> unownedPerks = PerksManager.ALL_PERKS.values().stream()
	                .filter(perk -> !player.hasEffect(perk.getAssociatedEffect()))
	                .filter(perk -> !WorldConfig.get(level).isRandomPerkDisabled(perk.getId()))
	                .collect(Collectors.toList());

	            if (!unownedPerks.isEmpty()) {
	                PerksManager.Perk chosenPerk = unownedPerks.get(RANDOM.nextInt(unownedPerks.size()));
	                
	                if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
	                    ON_THE_HOUSE_OVERRIDE_COUNT.put(player.getUUID(), currentOverrides + 1);
	                    player.sendSystemMessage(getTranslatedComponent(player, "Bonus: On the House! Vous avez bypassé la limite et reçu la perk : " + chosenPerk.getName() + " !", "Bonus: On the House! You bypassed the limit and received the perk: " + chosenPerk.getName() + "!"));
	                    player.sendSystemMessage(getTranslatedComponent(player, "Vous pouvez bypasser la limite de perks encore " + (MAX_ON_THE_HOUSE_OVERRIDES - (currentOverrides + 1)) + " fois.", "You can bypass the perk limit " + (MAX_ON_THE_HOUSE_OVERRIDES - (currentOverrides + 1)) + " more times."));
	                } else {
	                    player.sendSystemMessage(getTranslatedComponent(player, "Bonus: On the House! Vous avez reçu la perk : " + chosenPerk.getName() + " !", "Bonus: On the House! You received the perk: " + chosenPerk.getName() + "!"));
	                }
	                
	                chosenPerk.applyEffect(player);
	            } else {
	                PointManager.modifyScore(player, 500);
	                player.sendSystemMessage(getTranslatedComponent(player, "Bonus: On the House! Vous avez déjà tous les perks, 500 points à la place !", "Bonus: On the House! You already have all perks, 500 points instead!"));
	            }
	        } else {
	            PointManager.modifyScore(player, 500);
	            player.sendSystemMessage(getTranslatedComponent(player, "Bonus: On the House! Limite de perks atteinte et plus de bypass disponibles, 500 points à la place !", "Bonus: On the House! Perk limit reached and no more bypasses available, 500 points instead!"));
	        }
	    });
	}

	public static void loadBonuses() {
	    ALL_BONUSES.clear();
	    String[] builtinBonuses = {"max_ammo", "nuke", "insta_kill", "double_points", "carpenter", "gold_rush", "zombie_blood", "wish", "on_the_house"};
	    
	    for (String bonusId : builtinBonuses) {
	        String path = "data/zombierool/gameplay/bonuses/" + bonusId + ".json";
	        try (InputStream stream = BonusManager.class.getClassLoader().getResourceAsStream(path)) {
	            if (stream != null) {
	                try (InputStreamReader reader = new InputStreamReader(stream)) {
	                    BonusDefinition def = GSON.fromJson(reader, BonusDefinition.class);
	                    if (def.id == null) def.id = bonusId;
	                    ALL_BONUSES.put(def.id, new Bonus(def));
	                }
	            }
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    File externalFolder = FMLPaths.GAMEDIR.get().resolve("data/zombierool/gameplay/bonuses/").toFile();
	    if (externalFolder.exists() && externalFolder.isDirectory()) {
	        File[] files = externalFolder.listFiles((dir, name) -> name.endsWith(".json"));
	        if (files != null) {
	            for (File file : files) {
	                try (FileReader reader = new FileReader(file)) {
	                    BonusDefinition def = GSON.fromJson(reader, BonusDefinition.class);
	                    if (def.id == null) def.id = file.getName().replace(".json", "");
	                    ALL_BONUSES.put(def.id, new Bonus(def));
	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	            }
	        }
	    }
	}

	public static Bonus getRandomBonus(Player player) {
	    WorldConfig config = player != null && player.level() instanceof ServerLevel ? WorldConfig.get((ServerLevel)player.level()) : null;
	    Set<String> disabled = config != null ? config.getDisabledBonuses() : new HashSet<>();
	    
	    Map<Bonus, Double> effectiveChances = new ConcurrentHashMap<>();
	    double totalChance = 0;

	    double vultureMultiplier = 3.0; 
	    boolean hasVulturePerk = player != null && player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get());

	    for (Bonus bonus : ALL_BONUSES.values()) {
	        if (bonus.chance <= 0 || disabled.contains(bonus.id)) continue;
	        
	        double currentChance = bonus.chance;

	        if (hasVulturePerk) {
	            if (bonus.id.equals("zombie_blood") || bonus.id.equals("on_the_house") || bonus.id.equals("gold_rush")) {
	                currentChance *= vultureMultiplier;
	            }
	        }

	        effectiveChances.put(bonus, currentChance);
	        totalChance += currentChance;
	    }

	    if (totalChance == 0) return null;

	    double roll = RANDOM.nextDouble() * totalChance;
	    double cumulativeChance = 0;
	    for (Bonus bonus : ALL_BONUSES.values()) {
	        if (!effectiveChances.containsKey(bonus)) continue;
	        cumulativeChance += effectiveChances.get(bonus);
	        if (roll <= cumulativeChance) {
	            return bonus;
	        }
	    }
	    return null;
	}

	public static void spawnBonus(Bonus bonus, ServerLevel level, Vec3 pos) {
	    if (bonus == null) return;
	    
	    boolean bonusTypeAlreadyActive = ACTIVE_BONUSES_IN_WORLD.values().stream()
	            .anyMatch(activeBonus -> activeBonus.bonus.id.equals(bonus.id));
	            
	    if (bonusTypeAlreadyActive) {
	        return;
	    }
	    
	    ActiveBonus newBonus = new ActiveBonus(bonus, pos, level.getGameTime());
	    ACTIVE_BONUSES_IN_WORLD.put(newBonus.bonusId, newBonus);
	}

	static void collectBonus(Bonus bonus, Player player, ServerLevel level, Vec3 pos, UUID bonusId) {
	    ACTIVE_BONUSES_IN_WORLD.remove(bonusId);
	    
	    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(POWER_UP_GRAB_SOUND.getLocation(), 1.0f, 1.0f));

	    BonusAction action = ACTIONS.get(bonus.action_id);
	    if (action != null) {
	        action.apply(player, level, pos, bonus);
	    }
	    spawnBonusParticles(level, pos, bonus);
	}

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
	    if (event.phase == TickEvent.Phase.END) {
	        ServerLevel serverLevel = event.getServer().overworld();
	        long currentTime = serverLevel.getGameTime();

	        if (bonusSpawnDisabledByNuke && currentTime >= nukeSpawnDisableEndTime) {
	            bonusSpawnDisabledByNuke = false;
	        }

	        boolean instaKillActive = false;
	        Iterator<Map.Entry<UUID, Integer>> instaKillIterator = INSTA_KILL_ACTIVE_TICKS.entrySet().iterator();
	        while (instaKillIterator.hasNext()) {
	            Map.Entry<UUID, Integer> entry = instaKillIterator.next();
	            int ticks = entry.getValue();
	            if (ticks <= 1) { 
	                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverLevel.getServer().getPlayerList().getPlayer(entry.getKey())), 
	                    new PlayGlobalSoundPacket(INSTA_KILL_END_SOUND.getLocation(), 1.0f, 1.0f));
	                instaKillIterator.remove();
	            } else {
	                if (ticks > 5) instaKillActive = true;
	                INSTA_KILL_ACTIVE_TICKS.put(entry.getKey(), ticks - 1);
	            }
	        }

	        if (instaKillActive && currentTime % LOOP_SOUND_INTERVAL_TICKS == 0) {
	            for (UUID id : INSTA_KILL_ACTIVE_TICKS.keySet()) {
	                ServerPlayer sp = serverLevel.getServer().getPlayerList().getPlayer(id);
	                if (sp != null) {
	                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), 
	                        new PlayGlobalSoundPacket(INSTA_KILL_LOOP_SOUND.getLocation(), 0.4f, 1.0f));
	                }
	            }
	        }

	        boolean doublePointsActive = false;
	        Iterator<Map.Entry<UUID, Integer>> doublePointsIterator = DOUBLE_POINTS_ACTIVE_TICKS.entrySet().iterator();
	        while (doublePointsIterator.hasNext()) {
	            Map.Entry<UUID, Integer> entry = doublePointsIterator.next();
	            int ticks = entry.getValue();
	            if (ticks <= 1) { 
	                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverLevel.getServer().getPlayerList().getPlayer(entry.getKey())), 
	                    new PlayGlobalSoundPacket(DOUBLE_POINT_END_SOUND.getLocation(), 1.0f, 1.0f));
	                doublePointsIterator.remove();
	            } else {
	                if (ticks > 5) doublePointsActive = true;
	                DOUBLE_POINTS_ACTIVE_TICKS.put(entry.getKey(), ticks - 1);
	            }
	        }

	        if (doublePointsActive && currentTime % LOOP_SOUND_INTERVAL_TICKS == 0) {
	            for (UUID id : DOUBLE_POINTS_ACTIVE_TICKS.keySet()) {
	                ServerPlayer sp = serverLevel.getServer().getPlayerList().getPlayer(id);
	                if (sp != null) {
	                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), 
	                        new PlayGlobalSoundPacket(DOUBLE_POINT_LOOP_SOUND.getLocation(), 0.4f, 1.0f));
	                }
	            }
	        }

	        Iterator<Map.Entry<UUID, Integer>> zombieBloodIterator = ZOMBIE_BLOOD_ACTIVE_TICKS.entrySet().iterator();
	        while (zombieBloodIterator.hasNext()) {
	            Map.Entry<UUID, Integer> entry = zombieBloodIterator.next();
	            UUID playerId = entry.getKey();
	            int ticks = entry.getValue();
	            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
	            
	            if (player != null) {
	                if (ticks <= 1) { 
	                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
	                        new PlayGlobalSoundPacket(DOUBLE_POINT_END_SOUND.getLocation(), 1.0f, 1.0f));
	                    player.sendSystemMessage(getTranslatedComponent(player, "L'effet Sang de Zombie a disparu.", "Zombie Blood effect has worn off."));
	                    zombieBloodIterator.remove();
	                } else { 
	                    ZOMBIE_BLOOD_ACTIVE_TICKS.put(playerId, ticks - 1);
	                }
	            } else {
	                zombieBloodIterator.remove();
	            }
	        }

	        Set<UUID> bonusesToRemove = new HashSet<>();
	        for (ActiveBonus activeBonus : ACTIVE_BONUSES_IN_WORLD.values()) {
	            boolean anyPlayerCloseToBonus = false;
	            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
	                if (player.position().distanceToSqr(activeBonus.position) < 25 * 25) { 
	                    anyPlayerCloseToBonus = true;
	                    break;
	                }
	            }

	            if (anyPlayerCloseToBonus) {
	                if (currentTime % (LOOP_SOUND_INTERVAL_TICKS * 2) == 0) { 
	                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), 
	                        new PlayGlobalSoundPacket(POWER_UP_LOOP_SOUND.getLocation(), 0.2f, 1.0f));
	                }
	            }

	            spawnContinuousBonusParticles(serverLevel, activeBonus.position, activeBonus.bonus);

	            double detectionRadiusSq = 1.5 * 1.5;
	            for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
	                if (player.position().distanceToSqr(activeBonus.position) < detectionRadiusSq) {
	                    collectBonus(activeBonus.bonus, player, serverLevel, activeBonus.position, activeBonus.bonusId);
	                    bonusesToRemove.add(activeBonus.bonusId);
	                    break;
	                }
	            }
	            
	            if (currentTime - activeBonus.spawnTime >= BONUS_LIFESPAN_TICKS) {
	                spawnBonusParticles(serverLevel, activeBonus.position, activeBonus.bonus); 
	                serverLevel.sendParticles(ParticleTypes.SMOKE, activeBonus.position.x(), activeBonus.position.y() + 0.5, activeBonus.position.z(), 10, 0.1, 0.1, 0.1, 0.02);
	                bonusesToRemove.add(activeBonus.bonusId);
	            }
	        }
	        bonusesToRemove.forEach(ACTIVE_BONUSES_IN_WORLD::remove);

	        while (!NUKE_SOUND_QUEUE.isEmpty() && NUKE_SOUND_QUEUE.peek().playTime <= currentTime) {
	            NukeSoundEvent nukeEvent = NUKE_SOUND_QUEUE.poll();
	            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), 
	                new PlayGlobalSoundPacket(NUKE_SOUND.getLocation(), 1.0f, 1.0f + (RANDOM.nextFloat() * 0.2f - 0.1f)));
	        }
	    }
	}

	private static void spawnContinuousBonusParticles(ServerLevel level, Vec3 pos, Bonus bonus) {
	    int count = 2;
	    double offset = 0.3;
	    double speed = 0.02;

	    if (bonus.particleType != null) {
	        level.sendParticles(bonus.particleType, pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
	    } else {
	        level.sendParticles(ParticleTypes.CLOUD, pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
	    }
	}

	public static void spawnBonusParticles(ServerLevel level, Vec3 pos, Bonus bonus) {
	    if (bonus.particleType != null) {
	        level.sendParticles(bonus.particleType, pos.x, pos.y + 0.5, pos.z, 30, 0.3, 0.3, 0.3, 0.05);
	        if (bonus.id.equals("nuke")) {
	            level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.5, pos.z, 50, 0.5, 0.5, 0.5, 0.05);
	        }
	    } else {
	        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 15, 0.2, 0.2, 0.2, 0.05);
	    }
	}
}