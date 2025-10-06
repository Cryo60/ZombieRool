package net.mcreator.zombierool.bonuses;

import net.mcreator.zombierool.api.IReloadable;
import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.block.DefenseDoorBlock;
import net.mcreator.zombierool.init.ZombieroolModMobEffects; // Import réel pour les effets des bonus et perks
import net.mcreator.zombierool.PerksManager; // Import de votre PerksManager
import net.mcreator.zombierool.potion.PerksEffectVultureMobEffect; // Import the Vulture Effect

import net.mcreator.zombierool.init.ZombieroolModSounds; // Gardez cet import si d'autres sons SONT dans ZombieroolModSounds
import net.mcreator.zombierool.item.PlasmaPistolWeaponItem; // AJOUTEZ CETTE LIGNE
import net.mcreator.zombierool.item.EnergySwordItem; // AJOUTEZ CETTE LIGNE
import net.mcreator.zombierool.item.OldSwordWeaponItem; // AJOUTEZ CETTE LIGNE

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects; // Peut-être encore utile pour d'autres effets vanilla
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent

import java.util.Random;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.Arrays;

// Correct import for your particle type registry
import net.mcreator.zombierool.init.ZombieroolModParticleTypes;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BonusManager {

    private static final Random RANDOM = new Random();
    // --- Variables pour Insta-Kill sans effet de potion ---
    private static final Map<UUID, Integer> INSTA_KILL_ACTIVE_TICKS = new ConcurrentHashMap<>();
    private static final int INSTA_KILL_DURATION_TICKS = 20 * 30; // 30 secondes

    public static boolean isInstaKillActive(Player player) {
        return INSTA_KILL_ACTIVE_TICKS.containsKey(player.getUUID()) && INSTA_KILL_ACTIVE_TICKS.get(player.getUUID()) > 0;
    }

    // --- Variables pour Double Points sans effet de potion ---
    private static final Map<UUID, Integer> DOUBLE_POINTS_ACTIVE_TICKS = new ConcurrentHashMap<>();
    private static final int DOUBLE_POINTS_DURATION_TICKS = 20 * 45; // 45 secondes pour Double Points

    public static boolean isDoublePointsActive(Player player) {
        return DOUBLE_POINTS_ACTIVE_TICKS.containsKey(player.getUUID()) && DOUBLE_POINTS_ACTIVE_TICKS.get(player.getUUID()) > 0;
    }

    // --- Variables pour Carpenter Sound Loop ---
    private static final int CARP_LOOP_DURATION_TICKS = 20 * 7;
    // 7 secondes (durée à titre indicatif pour l'effet)

    // --- Gestion des sons de Nuke multiples ---
    private static class NukeSoundEvent {
        public final Vec3 position;
        public final long playTime;

        public NukeSoundEvent(Vec3 position, long playTime) {
            this.position = position;
            this.playTime = playTime;
        }
    }
    private static final Queue<NukeSoundEvent> NUKE_SOUND_QUEUE = new LinkedList<>();
    // --- Variables for Zombie Blood (duration) ---
    private static final Map<UUID, Integer> ZOMBIE_BLOOD_ACTIVE_TICKS = new ConcurrentHashMap<>();
    private static final int ZOMBIE_BLOOD_DURATION_TICKS = 20 * 10; // 10 secondes

    // --- NOUVELLE METHODE POUR ZOMBIE BLOOD ---
    public static boolean isZombieBloodActive(Player player) {
        return ZOMBIE_BLOOD_ACTIVE_TICKS.containsKey(player.getUUID()) && ZOMBIE_BLOOD_ACTIVE_TICKS.get(player.getUUID()) > 0;
    }
    // -------------------------------------------

    // --- Variables for On the House (perk limit override) ---
    // Cette map suivra le nombre de perks "extra" accordés par On the House
    private static final Map<UUID, Integer> ON_THE_HOUSE_OVERRIDE_COUNT = new ConcurrentHashMap<>();
    private static final int MAX_ON_THE_HOUSE_OVERRIDES = 2; // Peut bypasser la limite de perks 2 fois

    // --- Gestion des bonus actifs dans le monde (pour loop sound et disparition) ---
    private static class ActiveBonus {
        public final BonusType type;
        public final Vec3 position;
        public final long spawnTime;
        public final UUID bonusId;
        public ActiveBonus(BonusType type, Vec3 position, long spawnTime) {
            this.type = type;
            this.position = position;
            this.spawnTime = spawnTime;
            this.bonusId = UUID.randomUUID();
        }
    }

    private static final Map<UUID, ActiveBonus> ACTIVE_BONUSES_IN_WORLD = new ConcurrentHashMap<>();
    private static final int BONUS_LIFESPAN_TICKS = 20 * 15; // Les bonus disparaissent après 15 secondes s'ils ne sont pas pris

    // Ajout d'une variable pour contrôler le délai des sons de loop
    private static final int LOOP_SOUND_INTERVAL_TICKS = 20;
    // Joue le son toutes les 20 ticks (1 seconde)

    // Flag pour désactiver temporairement le spawn de bonus lors d'une nuke
    public static boolean bonusSpawnDisabledByNuke = false;
    // --- NOUVELLE VARIABLE pour gérer la durée de désactivation du bonus spawn par la nuke ---
    private static long nukeSpawnDisableEndTime = 0;
    // Sons généraux des bonus
    public static final SoundEvent POWER_UP_GRAB_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "power_up_grab"));
    public static final SoundEvent POWER_UP_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "power_up_loop"));

    // Sons Carpenter
    public static final SoundEvent CARP_END_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "carp_end"));
    public static final SoundEvent ANN_CARPENTER_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_carpenter"));
    public static final SoundEvent CARP_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "carp_loop"));
    // Added this

    // Sons Double Points
    public static final SoundEvent DOUBLE_POINT_END_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "double_point_end"));
    public static final SoundEvent DOUBLE_POINT_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "double_point_loop"));
    public static final SoundEvent ANN_DOUBLEPOINTS_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_doublepoints"));
    // Sons Insta-Kill
    public static final SoundEvent INSTA_KILL_END_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "insta_kill_end"));
    public static final SoundEvent INSTA_KILL_LOOP_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "insta_kill_loop"));
    public static final SoundEvent ANN_INSTAKILL_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_instakill"));
    // Sons Max Ammo
    public static final SoundEvent ANN_MAXAMMO_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_maxammo"));
    public static final SoundEvent FULL_AMMO_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "full_ammo"));

    // Sons Nuke
    public static final SoundEvent ANN_NUKE_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "ann_nuke"));
    public static final SoundEvent NUKE_SOUND = SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool", "nuke"));

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(Player player) {
        // For server-side, we can't directly access client language settings.
        // This is a placeholder. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For this example, we'll assume English if a player context is available.
        return true; 
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    /**
     * Méthode appelée lorsque tu fais apparaître un bonus.
     * Avant de faire apparaître le bonus, elle vérifie si un bonus du même type n'est pas déjà présent dans le monde.
     */
    public static void spawnBonus(BonusType type, ServerLevel level, Vec3 pos) {
        // Vérifie si un bonus de ce type est déjà actif dans le monde
        boolean bonusTypeAlreadyActive = ACTIVE_BONUSES_IN_WORLD.values().stream()
                .anyMatch(activeBonus -> activeBonus.type == type);
        if (bonusTypeAlreadyActive) {
            // Si un bonus du même type est déjà actif, on ne fait rien pour éviter les doublons.
            // System.out.println("DEBUG: Tentative de spawn de " + type.name() + " annulée car déjà actif.");
            return;
        }

        ActiveBonus newBonus = new ActiveBonus(type, pos, level.getGameTime());
        ACTIVE_BONUSES_IN_WORLD.put(newBonus.bonusId, newBonus);
        // Les particules persistantes seront gérées par onServerTick
    }

    // La méthode collectBonus est appelée INTERNEMENT par le manager
    static void collectBonus(BonusType type, Player player, ServerLevel level, Vec3 pos, UUID bonusId) {
        ACTIVE_BONUSES_IN_WORLD.remove(bonusId);
        level.playSound(null, pos.x(), pos.y(), pos.z(), POWER_UP_GRAB_SOUND, SoundSource.MASTER, 1.0f, 1.0f);

        type.effect.apply(player, level, pos);
        // Appelle les particules UNIQUEMENT quand le bonus est ramassé (ou disparaît naturellement)
        spawnBonusParticles(level, pos, type);
    }


    // Méthode appelée à chaque tick du serveur pour gérer la durée des bonus et leurs sons
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel serverLevel = event.getServer().overworld();
            long currentTime = serverLevel.getGameTime();

            // --- Gérer la désactivation du spawn de bonus par la nuke ---
            if (bonusSpawnDisabledByNuke && currentTime >= nukeSpawnDisableEndTime) {
                bonusSpawnDisabledByNuke = false;
            }

            // Gérer Insta-Kill
            Iterator<Map.Entry<UUID, Integer>> instaKillIterator = INSTA_KILL_ACTIVE_TICKS.entrySet().iterator();
            while (instaKillIterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = instaKillIterator.next();
                UUID playerId = entry.getKey();
                int ticks = entry.getValue();
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (ticks <= 1) { // L'effet se termine (ou est déjà terminé)
                    if (player != null) {
                        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                            INSTA_KILL_END_SOUND, SoundSource.MASTER, 1.0f, 1.0f);
                    }
                    instaKillIterator.remove();
                } else { // L'effet est toujours actif
                    if (player != null) {
                        // Joue le son de loop seulement à certains intervalles
                        if (ticks > 5 && currentTime % LOOP_SOUND_INTERVAL_TICKS == 0) { // Ne joue pas si moins de 5 ticks restants
                            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                                INSTA_KILL_LOOP_SOUND, SoundSource.MASTER, 0.4f, 1.0f);
                        }
                    }
                    INSTA_KILL_ACTIVE_TICKS.put(playerId, ticks - 1);
                }
            }

            // Gérer Double Points
            Iterator<Map.Entry<UUID, Integer>> doublePointsIterator = DOUBLE_POINTS_ACTIVE_TICKS.entrySet().iterator();
            while (doublePointsIterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = doublePointsIterator.next();
                UUID playerId = entry.getKey();
                int ticks = entry.getValue();
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (ticks <= 1) { // L'effet se termine
                    if (player != null) {
                        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                            DOUBLE_POINT_END_SOUND, SoundSource.MASTER, 1.0f, 1.0f);
                    }
                    doublePointsIterator.remove();
                } else {
                    if (player != null) {
                        // Joue le son de loop seulement à certains intervalles
                        if (ticks > 5 && currentTime % LOOP_SOUND_INTERVAL_TICKS == 0) { // Ne joue pas si moins de 5 ticks restants
                            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                                DOUBLE_POINT_LOOP_SOUND, SoundSource.MASTER, 0.4f, 1.0f);
                        }
                    }
                    DOUBLE_POINTS_ACTIVE_TICKS.put(playerId, ticks - 1);
                }
            }

            // Gérer Zombie Blood (utilise les sons d'Insta-Kill loop et Double Points end)
            Iterator<Map.Entry<UUID, Integer>> zombieBloodIterator = ZOMBIE_BLOOD_ACTIVE_TICKS.entrySet().iterator();
            while (zombieBloodIterator.hasNext()) {
                Map.Entry<UUID, Integer> entry = zombieBloodIterator.next();
                UUID playerId = entry.getKey();
                int ticks = entry.getValue();
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    if (ticks <= 1) { // L'effet se termine
                        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                            DOUBLE_POINT_END_SOUND, SoundSource.MASTER, 1.0f, 1.0f); // Son de fin
                        player.sendSystemMessage(getTranslatedComponent(player, "L'effet Sang de Zombie a disparu.", "Zombie Blood effect has worn off."));
                        zombieBloodIterator.remove();
                    } else { // L'effet est toujours actif
                        // Joue le son de loop seulement à certains intervalles
                        if (ticks > 5 && currentTime % LOOP_SOUND_INTERVAL_TICKS == 0) { // Ne joue pas si moins de 5 ticks restants
                            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                                                INSTA_KILL_LOOP_SOUND, SoundSource.MASTER, 0.4f, 1.0f); // Son de loop
                        }
                        ZOMBIE_BLOOD_ACTIVE_TICKS.put(playerId, ticks - 1);
                    }
                } else {
                    zombieBloodIterator.remove();
                }
            }


            // Gérer les bonus actifs dans le monde (power_up_loop sound, disparition, et détection de collision)
            Set<UUID> bonusesToRemove = new HashSet<>();
            for (ActiveBonus activeBonus : ACTIVE_BONUSES_IN_WORLD.values()) {
                // Joue le son de loop si des joueurs sont à proximité
                boolean anyPlayerCloseToBonus = false;
                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                    if (player.position().distanceToSqr(activeBonus.position) < 25 * 25) { // 25 blocs de rayon
                        anyPlayerCloseToBonus = true;
                        break;
                    }
                }

                if (anyPlayerCloseToBonus) {
                    // Joue le son de loop seulement à certains intervalles et volume réduit
                    if (currentTime % (LOOP_SOUND_INTERVAL_TICKS * 2) == 0) { // Joue toutes les 2 secondes
                        serverLevel.playSound(null, activeBonus.position.x(), activeBonus.position.y(), activeBonus.position.z(),
                                        POWER_UP_LOOP_SOUND, SoundSource.MASTER, 0.2f, 1.0f); // Volume réduit à 0.2f
                    }
                }

                // Particules continues pour les bonus au sol
                spawnContinuousBonusParticles(serverLevel, activeBonus.position, activeBonus.type);


                // Détection de la "collision" avec le joueur
                double detectionRadiusSq = 1.5 * 1.5;
                for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
                    if (player.position().distanceToSqr(activeBonus.position) < detectionRadiusSq) {
                        collectBonus(activeBonus.type, player, serverLevel, activeBonus.position, activeBonus.bonusId);
                        bonusesToRemove.add(activeBonus.bonusId);
                        break;
                    }
                }

                // Vérifie si le bonus doit disparaître naturellement
                if (currentTime - activeBonus.spawnTime >= BONUS_LIFESPAN_TICKS) {
                    spawnBonusParticles(serverLevel, activeBonus.position, activeBonus.type); // Particules de disparition
                    serverLevel.sendParticles(ParticleTypes.SMOKE, activeBonus.position.x(), activeBonus.position.y() + 0.5, activeBonus.position.z(), 10, 0.1, 0.1, 0.1, 0.02);
                    bonusesToRemove.add(activeBonus.bonusId);
                }
            }
            bonusesToRemove.forEach(ACTIVE_BONUSES_IN_WORLD::remove);

            // Gérer la queue des sons de Nuke
            while (!NUKE_SOUND_QUEUE.isEmpty() && NUKE_SOUND_QUEUE.peek().playTime <= currentTime) {
                NukeSoundEvent nukeEvent = NUKE_SOUND_QUEUE.poll();
                serverLevel.playSound(null, nukeEvent.position.x(), nukeEvent.position.y(), nukeEvent.position.z(),
                                    NUKE_SOUND, SoundSource.MASTER, 1.0f, 1.0f + (RANDOM.nextFloat() * 0.2f - 0.1f));
            }
        }
    }
    // --------------------------------------------------------

    // Enum pour définir tes différents types de bonus
    public enum BonusType {
        // Anciennes valeurs: MAX_AMMO(0.10), NUKE(0.03), INSTA_KILL(0.08), DOUBLE_POINTS(0.08), CARPENTER(0.03)
        // Nouvelles valeurs réduites pour les rarifier
        MAX_AMMO(0.05, (player, level, pos) -> {
	        level.playSound(null, pos.x(), pos.y(), pos.z(), ANN_MAXAMMO_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f); // CORRIGÉ ICI

	        for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
	            for (ItemStack stack : targetPlayer.getInventory().items) {
	                // Restauration des munitions pour les armes IReloadable
	                if (stack.getItem() instanceof IReloadable reloadableWeapon) {
	                    reloadableWeapon.setAmmo(stack, reloadableWeapon.getMaxAmmo());
	                    reloadableWeapon.setReserve(stack, reloadableWeapon.getMaxReserve());
	                    reloadableWeapon.setReloadTimer(stack, 0);
	                    // Pas de message ici pour éviter la répétition si la durabilité est aussi restaurée
	                }
	                // Restauration de la durabilité pour le PlasmaPistolWeaponItem
	                if (stack.getItem() instanceof PlasmaPistolWeaponItem plasmaPistol) {
	                    plasmaPistol.setDurability(stack, plasmaPistol.getMaxDurability(stack));
	                }
	                // Restauration de la durabilité pour l'EnergySwordItem
	                if (stack.getItem() instanceof EnergySwordItem energySword) {
	                    energySword.setDurability(stack, energySword.getMaxDurability(stack));
	                }
	                // Restauration de la durabilité pour l'EnergySwordItem
	                if (stack.getItem() instanceof OldSwordWeaponItem oldSwordWeapon) {
	                    oldSwordWeapon.setDurability(stack, oldSwordWeapon.getMaxDurability(stack));
	                }
	            }
	            targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Vos munitions et durabilités ont été restaurées !", "Your ammo and durability have been restored!")); // Message général
	            targetPlayer.containerMenu.broadcastChanges();
	            level.playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(), FULL_AMMO_SOUND, SoundSource.MASTER, 1.0f, 1.0f); // CORRIGÉ ICI
	        }
	    }),
        NUKE(0.01, (player, level, pos) -> { // Réduit de 0.03 à 0.01 (très rare)
            level.playSound(null, pos.x(), pos.y(), pos.z(), ANN_NUKE_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);

            int numNukes = RANDOM.nextInt(4) + 4; // Entre 4 et 7 sons de nuke
            long currentScheduleTime = level.getGameTime();
            for (int i = 0; i < numNukes; i++) {
                long delayTicks = (long) (RANDOM.nextDouble() * (20 * 0.6) + (20 * 0.2)); // Délai entre 0.2s et 0.8s
                if (i == 0) delayTicks = 0; // Le premier son est immédiat
                currentScheduleTime += delayTicks;
                NUKE_SOUND_QUEUE.offer(new NukeSoundEvent(pos, currentScheduleTime));
            }

            // Active le flag pour désactiver le spawn de bonus temporairement
            bonusSpawnDisabledByNuke = true;
            // Définit le temps de fin de la désactivation (par exemple, 1 seconde = 20 ticks)
            nukeSpawnDisableEndTime = level.getGameTime() + 20 * 1; // Désactivé pendant 1 seconde (20 ticks)

            // Collectionne les entités à tuer avant de les modifier pour éviter ConcurrentModificationException
            List<LivingEntity> mobsToKill = StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(entity -> entity instanceof ZombieEntity || entity instanceof CrawlerEntity || entity instanceof HellhoundEntity)
                .map(entity -> (LivingEntity) entity)
                .collect(Collectors.toList());
            for (LivingEntity livingEntity : mobsToKill) {
                // Met l'entité en feu pendant 2 secondes (40 ticks)
                livingEntity.setSecondsOnFire(2);
                livingEntity.setHealth(0); // Tue l'entité
                livingEntity.die(level.damageSources().magic()); // Force la mort (nécessaire pour certains événements)
            }

            for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
                PointManager.modifyScore(targetPlayer, 900);
                targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Vous avez gagné 900 points !", "You gained 900 points!"));
            }
        }),
        INSTA_KILL(0.04, (player, level, pos) -> { // Réduit de 0.08 à 0.04
            level.playSound(null, pos.x(), pos.y(), pos.z(), ANN_INSTAKILL_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);

            for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
                INSTA_KILL_ACTIVE_TICKS.put(targetPlayer.getUUID(), INSTA_KILL_DURATION_TICKS);
            }
        }),
        DOUBLE_POINTS(0.04, (player, level, pos) -> { // Réduit de 0.08 à 0.04
            level.playSound(null, pos.x(), pos.y(), pos.z(), ANN_DOUBLEPOINTS_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);

            for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
                DOUBLE_POINTS_ACTIVE_TICKS.put(targetPlayer.getUUID(), DOUBLE_POINTS_DURATION_TICKS);
            }
        }),
        CARPENTER(0.01, (player, level, pos) -> { // Réduit de 0.03 à 0.01 (très rare)
            level.playSound(null, pos.x(), pos.y(), pos.z(), ANN_CARPENTER_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
            level.playSound(null, pos.x(), pos.y(), pos.z(), CARP_LOOP_SOUND, SoundSource.PLAYERS, 1.0f, 1.0f);

            for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
                PointManager.modifyScore(targetPlayer, 500);
                targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Vous avez gagné 500 points pour Carpenter !", "You gained 500 points for Carpenter!"));
            }

            int searchRadius = 100;
            for (int x = (int)pos.x - searchRadius; x <= (int)pos.x + searchRadius; x++) {
                for (int y = (int)pos.y - searchRadius; y <= (int)pos.y + searchRadius; y++) {
                    for (int z = (int)pos.z - searchRadius; z <= (int)pos.z + searchRadius; z++) {
                        BlockPos currentBlockPos = new BlockPos(x, y, z);
                        BlockState blockState = level.getBlockState(currentBlockPos);
                        if (blockState.getBlock() instanceof DefenseDoorBlock defenseDoor) {
                            if (blockState.getValue(DefenseDoorBlock.STAGE) < DefenseDoorBlock.MAX_STAGE) {
                                defenseDoor.updateStage(level, currentBlockPos, DefenseDoorBlock.MAX_STAGE);
                            }
                        }
                    }
                }
            }
        }),
        GOLD_RUSH(0.06, (player, level, pos) -> {
		    int randomMultiplier = RANDOM.nextInt(5) + 2;
		    int points = randomMultiplier * 100;
		    System.out.println("DEBUG: Gold Rush calculated points before modification: " + points);
		    player.sendSystemMessage(getTranslatedComponent(player, "Bonus: Gold Rush! Vous avez gagné " + points + " points !", "Bonus: Gold Rush! You gained " + points + " points!"));
		    PointManager.modifyScore(player, points);
		}),
        ZOMBIE_BLOOD(0.02, (player, level, pos) -> {
            ZOMBIE_BLOOD_ACTIVE_TICKS.put(player.getUUID(), ZOMBIE_BLOOD_DURATION_TICKS);
            player.sendSystemMessage(getTranslatedComponent(player, "Bonus: Zombie Blood! Les zombies vous ignorent !", "Bonus: Zombie Blood! Zombies ignore you!"));
        }),
        WISH(0.02, (player, level, pos) -> {
            for (ServerPlayer targetPlayer : level.getServer().getPlayerList().getPlayers()) {
                targetPlayer.setHealth(targetPlayer.getMaxHealth());
                targetPlayer.sendSystemMessage(getTranslatedComponent(targetPlayer, "Bonus: Wish! Vous avez été soigné !", "Bonus: Wish! You have been healed!"));
            }
        }),
        ON_THE_HOUSE(0.01, (player, level, pos) -> {
            int currentOverrides = ON_THE_HOUSE_OVERRIDE_COUNT.getOrDefault(player.getUUID(), 0);
            int currentPerkCount = PerksManager.getPerkCount(player);

            if (currentPerkCount < PerksManager.MAX_PERKS_LIMIT || currentOverrides < MAX_ON_THE_HOUSE_OVERRIDES) {
                List<PerksManager.Perk> unownedPerks = PerksManager.ALL_PERKS.values().stream()
                    .filter(perk -> !player.hasEffect(perk.getAssociatedEffect()))
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
        })
        ;

        private final double chance;
        private final BonusEffect effect;
        BonusType(double chance, BonusEffect effect) {
            this.chance = chance;
            this.effect = effect;
        }

        public double getChance() {
            return chance;
        }

        @FunctionalInterface
        public interface BonusEffect {
            void apply(Player player, ServerLevel level, Vec3 pos);
        }

        public static BonusType getRandomBonus(Player player) {
            Map<BonusType, Double> effectiveChances = new ConcurrentHashMap<>();
            double totalChance = 0;

            double vultureMultiplier = 3.0;

            boolean hasVulturePerk = player != null && player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get());

            for (BonusType bonus : values()) {
                double currentChance = bonus.getChance();

                if (hasVulturePerk) {
                    if (bonus == ZOMBIE_BLOOD || bonus == ON_THE_HOUSE || bonus == GOLD_RUSH) {
                        currentChance *= vultureMultiplier;
                    }
                }
                effectiveChances.put(bonus, currentChance);
                totalChance += currentChance;
            }

            double roll = RANDOM.nextDouble() * totalChance;
            double cumulativeChance = 0;
            for (BonusType bonus : values()) {
                cumulativeChance += effectiveChances.get(bonus);
                if (roll <= cumulativeChance) {
                    return bonus;
                }
            }
            return null;
        }
    }

    private static void spawnContinuousBonusParticles(ServerLevel level, Vec3 pos, BonusType type) {
        int count = 2;
        double offset = 0.3;
        double speed = 0.02;

        switch (type) {
            case MAX_AMMO:
                level.sendParticles(ZombieroolModParticleTypes.MAXAMMO.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case INSTA_KILL:
                level.sendParticles(ZombieroolModParticleTypes.INSTAKILL.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case DOUBLE_POINTS:
                level.sendParticles(ZombieroolModParticleTypes.DOUBLE_POINTS.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case CARPENTER:
                level.sendParticles(ZombieroolModParticleTypes.CARPENTER.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case NUKE:
                level.sendParticles(ZombieroolModParticleTypes.NUKE.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case GOLD_RUSH:
                level.sendParticles(ZombieroolModParticleTypes.GOLD_RUSH.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case ZOMBIE_BLOOD:
                level.sendParticles(ZombieroolModParticleTypes.ZOMBIE_BLOOD.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case WISH:
                level.sendParticles(ZombieroolModParticleTypes.WISH.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
            case ON_THE_HOUSE:
                level.sendParticles(ZombieroolModParticleTypes.ON_THE_HOUSE.get(), pos.x + RANDOM.nextDouble() * offset - offset / 2, pos.y + 0.5 + RANDOM.nextDouble() * offset - offset / 2, pos.z + RANDOM.nextDouble() * offset - offset / 2, count, offset, offset, offset, speed);
                break;
        }
    }
    public static void spawnBonusParticles(ServerLevel level, Vec3 pos, BonusType type) {
        switch (type) {
            case MAX_AMMO:
                level.sendParticles(ZombieroolModParticleTypes.MAXAMMO.get(), pos.x, pos.y + 0.5, pos.z, 20, 0.2, 0.2, 0.2, 0.1);
                break;
            case NUKE:
                level.sendParticles(ZombieroolModParticleTypes.NUKE.get(), pos.x, pos.y + 1.0, pos.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.5, pos.z, 50, 0.5, 0.5, 0.5, 0.05);
                break;
            case INSTA_KILL:
                level.sendParticles(ZombieroolModParticleTypes.INSTAKILL.get(), pos.x, pos.y + 0.5, pos.z, 50, 0.5, 0.5, 0.5, 0.1);
                break;
            case DOUBLE_POINTS:
                level.sendParticles(ZombieroolModParticleTypes.DOUBLE_POINTS.get(), pos.x, pos.y + 0.5, pos.z, 30, 0.3, 0.3, 0.3, 0.05);
                break;
            case CARPENTER:
                level.sendParticles(ZombieroolModParticleTypes.CARPENTER.get(), pos.x, pos.y + 0.5, pos.z, 40, 0.4, 0.4, 0.4, 0.05);
                break;
            case GOLD_RUSH:
                level.sendParticles(ZombieroolModParticleTypes.GOLD_RUSH.get(), pos.x, pos.y + 0.5, pos.z, 25, 0.3, 0.3, 0.3, 0.08);
                break;
            case ZOMBIE_BLOOD:
                level.sendParticles(ZombieroolModParticleTypes.ZOMBIE_BLOOD.get(), pos.x, pos.y + 0.5, pos.z, 35, 0.4, 0.4, 0.4, 0.1);
                break;
            case WISH:
                level.sendParticles(ZombieroolModParticleTypes.WISH.get(), pos.x, pos.y + 0.5, pos.z, 40, 0.5, 0.5, 0.5, 0.1);
                break;
            case ON_THE_HOUSE:
                level.sendParticles(ZombieroolModParticleTypes.ON_THE_HOUSE.get(), pos.x, pos.y + 0.5, pos.z, 30, 0.3, 0.3, 0.3, 0.07);
                break;
            default:
                level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.5, pos.z, 15, 0.2, 0.2, 0.2, 0.05);
        }
    }
}
