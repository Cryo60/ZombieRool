package net.mcreator.zombierool.command;

import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.ScoreboardHandler;
import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.block.entity.ObstacleDoorBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;
import net.mcreator.zombierool.block.PlayerSpawnerBlock;
import net.mcreator.zombierool.bonuses.BonusManager;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.mcreator.zombierool.MysteryBoxManager;
import net.mcreator.zombierool.WunderfizzManager; // NOUVEAU: Import pour WunderfizzManager

import net.minecraftforge.network.PacketDistributor;
import net.mcreator.zombierool.network.NetworkHandler; // Import your NetworkHandler
import net.mcreator.zombierool.network.packet.SetEyeColorPacket; // CORRECTED IMPORT for SetEyeColorPacket
import net.mcreator.zombierool.network.packet.SyncColdWaterStatePacket; // Import for cold water sync packet

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType; // Import for boolean argument
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.WaveManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation; // Corrected import
import net.minecraft.core.registries.BuiltInRegistries;
import net.mcreator.zombierool.item.M1911WeaponItem;
import net.mcreator.zombierool.init.ZombieroolModItems;
import net.minecraft.world.level.GameType;
import net.minecraft.world.Difficulty; // Corrected import

import java.util.*;
import java.util.stream.Collectors;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.level.block.state.BlockState; // ADDED: Import for BlockState

@Mod.EventBusSubscriber
public class ZombieroolCommandCommand {

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(ServerPlayer player) {
        // This is a simplified check. In a real scenario, you might need to get the player's client language setting.
        // For server-side commands, this might be more complex as the server doesn't directly know client language.
        // A common approach is to send the client language to the server, or use a default.
        // For this example, we'll assume a simple check or a default if not a player context.
        return true; // Placeholder: Assume English for server-side messages for now, or implement client-side language sync.
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        // For server-side messages, we need to know the target player's language.
        // If player is null (e.g., command from console), default to French or a configurable language.
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    // --- Constants for Magic Numbers ---
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final int DEFAULT_PLAYER_SCORE = 500;
    private static final int SIDEBAR_DISPLAY_SLOT = 1; // Scoreboard display slot
    private static final int MIN_WAVE_NUMBER = 0;

    // Search radii for locate commands
    private static final int OBSTACLE_SEARCH_RADIUS = 150;
    private static final int SPAWNER_LOCATE_RADIUS = 100;
    private static final int MIN_VERTICAL_SEARCH_OFFSET = -64;
    private static final int MAX_VERTICAL_SEARCH_OFFSET = 320;

    // Teleportation offsets
    private static final double TELEPORT_CENTER_OFFSET = 0.5;
    private static final double TELEPORT_ABOVE_BLOCK_OFFSET = 1.0;

    // Error Messages - now dynamically translated
    private static SimpleCommandExceptionType createTranslatedError(String french, String english) {
        return new SimpleCommandExceptionType(Component.literal(french) // Default to French for server-side error creation
            .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY)).append(Component.literal(english))); // Include both for debugging or if client-side translation isn't perfect
    }

    private static final SimpleCommandExceptionType ERROR_INVALID_BONUS_TYPE = createTranslatedError("Type de bonus invalide !", "Invalid bonus type!");
    private static final SimpleCommandExceptionType ERROR_INVALID_ITEM = createTranslatedError("Identifiant d'objet invalide ou l'objet n'existe pas !", "Invalid item ID or item does not exist!");
    private static final SimpleCommandExceptionType ERROR_INVALID_DAYNIGHT_MODE = createTranslatedError("Mode jour/nuit invalide ! Utilisez 'day', 'night' ou 'cycle'.", "Invalid day/night mode! Use 'day', 'night' ou 'cycle'.");
    private static final SimpleCommandExceptionType ERROR_INVALID_WONDER_WEAPON = createTranslatedError("L'objet spécifié n'est pas une Wonder Weapon reconnue.", "The specified item is not a recognized Wonder Weapon.");
    private static final SimpleCommandExceptionType ERROR_GAME_NOT_RUNNING = createTranslatedError("Le jeu ZombieRool n'est pas en cours. Lancez d'abord '/zombierool start'.", "ZombieRool game is not running. Start with '/zombierool start' first.");
    private static final SimpleCommandExceptionType ERROR_NEGATIVE_WAVE = createTranslatedError("Le numéro de vague ne peut pas être négatif.", "Wave number cannot be negative.");
    private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_TYPE = createTranslatedError("Type de particule invalide !", "Invalid particle type!");
    private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_DENSITY = createTranslatedError("Densité de particule invalide ! Utilisez 'sparse', 'normal', 'dense', ou 'very_dense'.", "Invalid particle density! Use 'sparse', 'normal', 'dense', or 'very_dense'.");
    private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_MODE = createTranslatedError("Mode de particule invalide ! Utilisez 'global' ou 'atmospheric'.", "Invalid particle mode! Use 'global' or 'atmospheric'.");
    private static final SimpleCommandExceptionType ERROR_INVALID_MUSIC_PRESET = createTranslatedError("Preset de musique invalide ! Utilisez 'default', 'illusion', ou 'none'.", "Invalid music preset! Use 'default', 'illusion', or 'none'.");
    private static final SimpleCommandExceptionType ERROR_NO_SPAWNER_BLOCKS = createTranslatedError("Aucun PlayerSpawnerBlock n'a été trouvé ou enregistré ! Placez-en d'abord.", "No PlayerSpawnerBlocks found or registered! Place some first.");
    private static final SimpleCommandExceptionType ERROR_NO_MYSTERY_BOX_POSITIONS = createTranslatedError("Aucune position de Mystery Box n'a été trouvée ou enregistrée ! Placez-en d'abord.", "No Mystery Box positions found or registered! Place some first.");
    private static final SimpleCommandExceptionType ERROR_NO_WUNDERFIZZ_POSITIONS = createTranslatedError("Aucune position de Wunderfizz n'a été trouvée ou enregistrée ! Placez-en d'abord.", "No Wunderfizz positions found or registered! Place some first.");
    private static final SimpleCommandExceptionType ERROR_INVALID_EYE_COLOR_PRESET = createTranslatedError("Preset de couleur d'yeux invalide ! Utilisez 'red', 'blue', 'green', ou 'default'.", "Invalid eye color preset! Use 'red', 'blue', 'green', or 'default'.");
    private static final SimpleCommandExceptionType ERROR_GAME_NOT_RUNNING_END = createTranslatedError("Le jeu ZombieRool n'est pas en cours.", "ZombieRool game is not running.");
    private static final SimpleCommandExceptionType ERROR_INVALID_VOICE_PRESET = createTranslatedError("Preset de voix invalide ! Utilisez 'default', 'us', 'ru', 'fr', 'ger', ou 'none'.", "Invalid voice preset! Use 'default', 'us', 'ru', 'fr', 'ger', or 'none'.");


    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("zombierool")
                .requires(src -> src.hasPermission(REQUIRED_PERMISSION_LEVEL))

                // Points Management Commands
                .then(Commands.literal("points")
                    .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .then(Commands.argument("amount", IntegerArgumentType.integer())
                                .executes(ctx -> {
                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");

                                    if (players.isEmpty()) {
                                        ctx.getSource().sendFailure(getTranslatedComponent(null, "Aucun joueur trouvé pour ajouter des points.", "No player found to add points."));
                                        return 0;
                                    }

                                    ServerLevel level = ctx.getSource().getLevel();
                                    ServerScoreboard scoreboard = level.getServer().getScoreboard();
                                    Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID); 
                                    
                                    if (objective == null) {
                                        objective = scoreboard.addObjective(
                                            ScoreboardHandler.OBJECTIVE_ID,
                                            ObjectiveCriteria.DUMMY,
                                            getTranslatedComponent(null, "Points ZombieRool", "ZombieRool Points").withStyle(style -> style.withColor(ChatFormatting.GOLD)),
                                            ObjectiveCriteria.RenderType.INTEGER
                                        );
                                    }
                                    scoreboard.setDisplayObjective(SIDEBAR_DISPLAY_SLOT, objective);
                                    int totalModified = 0;
                                    for (ServerPlayer player : players) {
                                        PointManager.modifyScore(player, amount);
                                        int newScore = scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).getScore();
                                        player.sendSystemMessage(getTranslatedComponent(player, "Vous avez maintenant ", "You now have ")
                                            .append(Component.literal(String.valueOf(newScore)).withStyle(ChatFormatting.GOLD))
                                            .append(getTranslatedComponent(player, " points.", " points.")));
                                        totalModified++;
                                    }

                                    final int finalTotalModified = totalModified;
                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(null, "Ajout de " + amount + " points à " + finalTotalModified + " joueur(s).", "Added " + amount + " points to " + finalTotalModified + " player(s)."), true);
                                    return finalTotalModified;
                                })
                            )
                        )
                    )
                    .then(Commands.literal("reset")
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> {
                                Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                if (players.isEmpty()) {
                                    ctx.getSource().sendFailure(getTranslatedComponent(null, "Aucun joueur trouvé pour réinitialiser les points.", "No player found to reset points."));
                                    return 0;
                                }

                                int totalReset = 0;
                                for (ServerPlayer player : players) {
                                    PointManager.setScore(player, DEFAULT_PLAYER_SCORE);
                                    totalReset++;
                                }

                                final int finalTotalReset = totalReset;
                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(null, "Points de " + finalTotalReset + " joueur(s) réinitialisés à " + DEFAULT_PLAYER_SCORE + ".", "Points of " + finalTotalReset + " player(s) reset to " + DEFAULT_PLAYER_SCORE + "."), true);
                                return finalTotalReset;
                            })
                        )
                    )
                )

                // Start Game Command
                .then(Commands.literal("start")
                    .executes(ctx -> {
                        ServerLevel level = ctx.getSource().getLevel();
                        ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

                        // Prevent game start in peaceful difficulty
                        if (level.getDifficulty() == Difficulty.PEACEFUL) {
                            ctx.getSource().sendFailure(getTranslatedComponent(senderPlayer, "Impossible de démarrer le jeu en mode Peaceful.", "Cannot start the game in Peaceful mode.")
                                .withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        // Prevent multiple game starts
                        if (WaveManager.isGameRunning()) {
                            ctx.getSource().sendFailure(getTranslatedComponent(senderPlayer, "Le jeu est déjà en cours !", "The game is already running!").withStyle(ChatFormatting.YELLOW));
                            return 0;
                        }

                        // Scoreboard Setup
                        ServerScoreboard scoreboard = level.getServer().getScoreboard();
                        Objective objective = scoreboard.getObjective(ScoreboardHandler.OBJECTIVE_ID);
                        if (objective == null) {
                            objective = scoreboard.addObjective(
                                ScoreboardHandler.OBJECTIVE_ID,
                                ObjectiveCriteria.DUMMY,
                                getTranslatedComponent(null, "Points ZombieRool", "ZombieRool Points")
                                    .withStyle(style -> style.withColor(ChatFormatting.GOLD)),
                                ObjectiveCriteria.RenderType.INTEGER
                            );
                        }
                        scoreboard.setDisplayObjective(SIDEBAR_DISPLAY_SLOT, objective);
                        
                        WorldConfig worldConfig = WorldConfig.get(level);
                        List<BlockPos> spawnerPositions = new ArrayList<>(worldConfig.getPlayerSpawnerPositions());
                        
                        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
                        
                        // Check if any PlayerSpawnerBlocks are registered
                        if (spawnerPositions.isEmpty()) {
                            throw ERROR_NO_SPAWNER_BLOCKS.create();
                        }

                        // Check if enough spawners are available for all players
                        if (spawnerPositions.size() < players.size()) {
                            ctx.getSource().sendFailure(getTranslatedComponent(senderPlayer, "Pas assez de PlayerSpawnerBlock pour tous les joueurs en ligne !", "Not enough PlayerSpawnerBlocks for all online players!").withStyle(ChatFormatting.RED));
                            return 0;
                        }

                        Collections.shuffle(spawnerPositions);
                        // Randomize spawner positions
                        WaveManager.PLAYER_RESPAWN_POINTS.clear();
                        
                        // Get the configured starter item
                        ResourceLocation starterItemId = worldConfig.getStarterItem();
                        Item starterItem = BuiltInRegistries.ITEM.get(starterItemId);
                        
                        // Fallback to M1911 if configured item is invalid
                        if (starterItem == null) {
                            starterItem = ZombieroolModItems.M_1911_WEAPON.get(); // Assuming ZombieroolModItems is accessible
                            ctx.getSource().sendFailure(getTranslatedComponent(senderPlayer, "L'objet de démarrage configuré (" + starterItemId + ") est invalide. Utilisation du M1911 par défaut.", "Configured starter item (" + starterItemId + ") is invalid. Using default M1911.").withStyle(ChatFormatting.YELLOW));
                        }
                        ItemStack starterItemStack = new ItemStack(starterItem);
                        // Teleport players, set gamemode, assign points, give starter item
                        for (int i = 0; i < players.size(); i++) {
                            ServerPlayer player = players.get(i);
                            BlockPos spawnPos = spawnerPositions.get(i);
                            WaveManager.PLAYER_RESPAWN_POINTS.put(player.getUUID(), spawnPos.immutable());

                            player.teleportTo(spawnPos.getX() + TELEPORT_CENTER_OFFSET, spawnPos.getY() + TELEPORT_ABOVE_BLOCK_OFFSET, spawnPos.getZ() + TELEPORT_CENTER_OFFSET);
                            player.sendSystemMessage(getTranslatedComponent(player, "Vous avez été téléporté à votre position de départ !", "You have been teleported to your starting position!"));
                            
                            player.setGameMode(GameType.SURVIVAL);
                            PointManager.setScore(player, DEFAULT_PLAYER_SCORE);

                            // Give the configured starter item
                            if (!player.getInventory().add(starterItemStack.copy())) {
                                player.drop(starterItemStack.copy(), false);
                            }
                        }

                        // --- POWER SWITCH LOGIC ---
                        Set<BlockPos> powerSwitchPositions = new HashSet<>(worldConfig.getPowerSwitchPositions()); // Create a mutable copy
                        if (!powerSwitchPositions.isEmpty()) {
                            // Randomly select one power switch to keep
                            List<BlockPos> shuffledSwitches = new ArrayList<>(powerSwitchPositions);
                            Collections.shuffle(shuffledSwitches);
                            BlockPos chosenPowerSwitchPos = shuffledSwitches.get(0);

                            // Iterate over all registered power switches
                            for (BlockPos switchPos : powerSwitchPositions) {
                                if (!switchPos.equals(chosenPowerSwitchPos)) {
                                    // If it's not the chosen one, remove the block and unregister its position
                                    BlockState currentBlockState = level.getBlockState(switchPos);
                                    if (currentBlockState.getBlock() instanceof net.mcreator.zombierool.block.PowerSwitchBlock) { // Verify it's a PowerSwitchBlock
                                        level.removeBlock(switchPos, false); // Remove the block, don't drop items
                                        worldConfig.removePowerSwitchPosition(switchPos); // Unregister from WorldConfig
                                    }
                                } else {
                                    // Ensure the chosen power switch is unpowered at the start of the game
                                    BlockState chosenState = level.getBlockState(chosenPowerSwitchPos);
                                    if (chosenState.getBlock() instanceof net.mcreator.zombierool.block.PowerSwitchBlock && chosenState.getValue(net.mcreator.zombierool.block.PowerSwitchBlock.POWERED)) {
                                        level.setBlock(chosenPowerSwitchPos, chosenState.setValue(net.mcreator.zombierool.block.PowerSwitchBlock.POWERED, false), 3);
                                    }
                                }
                            }
                            worldConfig.setDirty(); // Mark WorldConfig as dirty after removals
                        }

                        // --- MYSTERY BOX LOGIC (maintenant facultative) ---
                        Set<BlockPos> mysteryBoxPositions = worldConfig.getMysteryBoxPositions();
                        if (!mysteryBoxPositions.isEmpty()) { // Vérifie si des Mystery Boxes sont enregistrées
                            MysteryBoxManager mysteryBoxManager = MysteryBoxManager.get(level);
                            mysteryBoxManager.setupInitialMysteryBox(level, 0); 
                        }

                        // NOUVEAU: WUNDERFIZZ LOGIC (maintenant facultative)
                        Set<BlockPos> wunderfizzPositions = worldConfig.getDerWunderfizzPositions();
                        if (!wunderfizzPositions.isEmpty()) { // Vérifie si des Wunderfizz sont enregistrées
                            WunderfizzManager wunderfizzManager = WunderfizzManager.get(level);
                            wunderfizzManager.setupInitialWunderfizz(level);
                        }
                        
                        // Remaining game start logic...
                        WaveManager.startGame(level); // Assuming this method exists and starts waves etc.
                        ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Jeu démarré ! Bonne chance, survivants !", "Game started! Good luck, survivors!").withStyle(ChatFormatting.GREEN), true);
                        return 1;
                    })
                )

                // End Game Command (NOUVEAU)
                .then(Commands.literal("end")
                    .executes(ctx -> {
                        ServerLevel level = ctx.getSource().getLevel();
                        ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

                        if (!WaveManager.isGameRunning()) {
                            throw ERROR_GAME_NOT_RUNNING_END.create();
                        }
                        
                        Component endMessage;
                        // Détecte si la commande a été exécutée par un joueur
                        if (ctx.getSource().isPlayer()) {
                            endMessage = getTranslatedComponent(senderPlayer, "§cLa partie a été terminée par une commande.", "§cThe game was ended by a command.");
                        } else {
                            // Si ce n'est pas un joueur (ex: command block, console), affiche la vague de survie
                            int currentWave = WaveManager.getCurrentWave();
                            endMessage = getTranslatedComponent(senderPlayer, "§aVous avez survécu " + currentWave + " manches !", "§aYou survived " + currentWave + " rounds!");
                        }

                        // Appelle la méthode endGame de WaveManager avec le message approprié
                        WaveManager.endGame(level, endMessage);
                        ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "La partie ZombieRool a été terminée.", "The ZombieRool game has ended."), true);
                        return 1;
                    })
                )

                // Set Wave Command
                .then(Commands.literal("setWave")
                    .then(Commands.argument("waveNumber", IntegerArgumentType.integer(MIN_WAVE_NUMBER))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                            int waveNumber = IntegerArgumentType.getInteger(ctx, "waveNumber");

                            if (!WaveManager.isGameRunning()) {
                                throw ERROR_GAME_NOT_RUNNING.create();
                            }
                            if (waveNumber < MIN_WAVE_NUMBER) {
                                throw ERROR_NEGATIVE_WAVE.create();
                            }

                            WaveManager.forceSetWave(level, waveNumber);

                            ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Vague définie sur " + waveNumber + ".", "Wave set to " + waveNumber + "."), true);
                            return 1;
                        })
                    )
                )

                // Spawn Bonus Command
                .then(Commands.literal("bonus")
                    .then(Commands.argument("bonus_type", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            Arrays.stream(BonusManager.BonusType.values())
                                .map(Enum::name)
                                .collect(Collectors.toList()),
                            builder))
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                            Vec3 spawnPos = ctx.getSource().getPosition();
                            String bonusTypeString = StringArgumentType.getString(ctx, "bonus_type").toUpperCase(Locale.ROOT);

                            try {
                                BonusManager.BonusType bonusType = BonusManager.BonusType.valueOf(bonusTypeString);
                                BonusManager.spawnBonus(bonusType, level, spawnPos);
                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Bonus de type " + bonusType.name() + " apparu à " + String.format("%.1f %.1f %.1f", spawnPos.x, spawnPos.y, spawnPos.z), "Bonus of type " + bonusType.name() + " spawned at " + String.format("%.1f %.1f %.1f", spawnPos.x, spawnPos.y, spawnPos.z)), true);
                                return 1;
                            } catch (IllegalArgumentException e) {
                                throw ERROR_INVALID_BONUS_TYPE.create();
                            }
                        })
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(ctx -> {
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                Vec3 spawnPos = Vec3Argument.getVec3(ctx, "pos");
                                String bonusTypeString = StringArgumentType.getString(ctx, "bonus_type").toUpperCase(Locale.ROOT);

                                try {
                                    BonusManager.BonusType bonusType = BonusManager.BonusType.valueOf(bonusTypeString);
                                    BonusManager.spawnBonus(bonusType, level, spawnPos);
                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Bonus de type " + bonusType.name() + " apparu à " + String.format("%.1f %.1f %.1f", spawnPos.x, spawnPos.y, spawnPos.z), "Bonus of type " + bonusType.name() + " spawned at " + String.format("%.1f %.1f %.1f", spawnPos.x, spawnPos.y, spawnPos.z)), true);
                                    return 1;
                            } catch (IllegalArgumentException e) {
                                throw ERROR_INVALID_BONUS_TYPE.create();
                            }
                        })
                        )
                    )
                )

                // Locate Commands
                .then(Commands.literal("locate")
                    .then(Commands.literal("obstacle")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            Player player = ctx.getSource().getPlayerOrException();
                            Set<BlockPos> visited = new HashSet<>();
                            for (BlockPos pos : BlockPos.betweenClosed(
                                player.blockPosition().offset(-OBSTACLE_SEARCH_RADIUS, MIN_VERTICAL_SEARCH_OFFSET, -OBSTACLE_SEARCH_RADIUS),
                                player.blockPosition().offset(OBSTACLE_SEARCH_RADIUS, MAX_VERTICAL_SEARCH_OFFSET, OBSTACLE_SEARCH_RADIUS))) {
                                if (!level.isLoaded(pos)) continue;
                                BlockEntity be = level.getBlockEntity(pos);
                                if (!(be instanceof ObstacleDoorBlockEntity obstacle) || visited.contains(pos)) continue;
                                findAllConnectedDoors(level, pos, visited);
                                String canal = obstacle.getCanal();
                                int prix = obstacle.getPrix();
                                MutableComponent msg = getTranslatedComponent((ServerPlayer)player,
                                    "Obstacle trouvé : " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + 
                                    ". Canal : " + canal + ". Prix : " + prix,
                                    "Obstacle found: " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + 
                                    ". Channel: " + canal + ". Price: " + prix
                                )
                                .withStyle(style -> style
                                    .withColor(ChatFormatting.YELLOW)
                                    .withClickEvent(new ClickEvent(
                                        ClickEvent.Action.RUN_COMMAND,
                                        "/execute at @s run tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ() 
                                    ))
                                    .withHoverEvent(new HoverEvent(
                                        HoverEvent.Action.SHOW_TEXT,
                                        getTranslatedComponent((ServerPlayer)player, "Clique pour te téléporter", "Click to teleport")
                                    ))
                                );
                                player.sendSystemMessage(msg);
                            }
                            return 1;
                        })
                    )
                    .then(Commands.literal("spawner")
                        .then(Commands.argument("type", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                new String[]{"zombie", "crawler", "dog"}, b))
                            .executes(ctx -> {
                                String type = StringArgumentType.getString(ctx, "type");
                                ServerLevel level = ctx.getSource().getLevel();
                                Player player = ctx.getSource().getPlayerOrException();
                                List<MutableComponent> spawnerList = new ArrayList<>();
                                for (BlockPos pos : BlockPos.betweenClosed(
                                    player.blockPosition().offset(-SPAWNER_LOCATE_RADIUS, MIN_VERTICAL_SEARCH_OFFSET, -SPAWNER_LOCATE_RADIUS),
                                    player.blockPosition().offset(SPAWNER_LOCATE_RADIUS, MAX_VERTICAL_SEARCH_OFFSET, SPAWNER_LOCATE_RADIUS))) {
                                    if (!level.isLoaded(pos)) continue;
                                    BlockEntity be = level.getBlockEntity(pos);
                                    boolean match = false;
                                    String canal = "?";
                                    if (type.equals("zombie") && be instanceof SpawnerZombieBlockEntity z) {
                                        match = true;
                                        canal = String.valueOf(z.getCanal());
                                    } else if (type.equals("crawler") && be instanceof SpawnerCrawlerBlockEntity c) {
                                        match = true;
                                        canal = String.valueOf(c.getCanal());
                                    } else if (type.equals("dog") && be instanceof SpawnerDogBlockEntity d) {
                                        match = true;
                                        canal = String.valueOf(d.getCanal());
                                    }
                                    if (match) {
                                        MutableComponent spawnerInfo = Component.literal(
                                            pos.getX() + " " + pos.getY() + " " + pos.getZ())
                                            .withStyle(style -> style
                                                .withColor(ChatFormatting.GREEN)
                                                .withClickEvent(new ClickEvent(
                                                    ClickEvent.Action.RUN_COMMAND,
                                                    String.format("/execute at @s run tp @s %d %d %d",
                                                        pos.getX(), pos.getY(), pos.getZ())
                                                ))
                                                .withHoverEvent(new HoverEvent(
                                                    HoverEvent.Action.SHOW_TEXT,
                                                    getTranslatedComponent((ServerPlayer)player, "Clique pour te téléporter", "Click to teleport")
                                                ))
                                            );
                                        spawnerList.add(
                                            Component.literal("- ")
                                                .append(spawnerInfo)
                                                .append(getTranslatedComponent((ServerPlayer)player, " | Canal : " + canal, " | Channel: " + canal)
                                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                        );
                                    }
                                }
                                if (spawnerList.isEmpty()) {
                                    player.sendSystemMessage(getTranslatedComponent((ServerPlayer)player, "Aucun spawner trouvé.", "No spawner found.")
                                        .withStyle(style -> style.withColor(ChatFormatting.RED)));
                                } else {
                                    player.sendSystemMessage(getTranslatedComponent((ServerPlayer)player, "§6Spawners trouvés (" + spawnerList.size() + ") :", "§6Spawners found (" + spawnerList.size() + "):"));
                                    for (MutableComponent spawner : spawnerList) {
                                        player.sendSystemMessage(spawner);
                                    }
                                }
                                return 1;
                            })
                        )
                    )
                )

                // Configuration Commands
                .then(Commands.literal("config")
                    .then(Commands.literal("fog")
                        .then(Commands.argument("preset", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.asList(
                                    "normal", "dense", "clear", "dark", "blood", "nightmare", "green_acid", "none",
                                    "sunrise", "sunset", "underwater", "swamp", "volcanic", "mystic", "toxic", "dreamy", "winter", "haunted",
                                    "arctic", "prehistoric", "radioactive", "desert", "ashstorm", "eldritch", "space", "corrupted", "celestial",
                                    "storm", "abyssal", "netherburn", "elderswamp", "nebula", "wasteland", "void", "festival", "temple",
                                    "stormy_night", "obsidian"
                                ),
                                builder))
                            .executes(ctx -> {
                                String preset = StringArgumentType.getString(ctx, "preset").toLowerCase(Locale.ROOT);
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                
                                WorldConfig worldConfig = WorldConfig.get(level);
                                worldConfig.setFogPreset(preset);
                                
                                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                    p.sendSystemMessage(Component.literal("ZOMBIEROOL_FOG_PRESET:" + preset), true);
                                });
                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Preset de brouillard défini sur '" + preset + "' pour tout le monde et sauvegardé.", "Fog preset set to '" + preset + "' for everyone and saved."), true);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("starter")
                        .then(Commands.argument("item_id", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                            .executes(ctx -> {
                                String itemIdString = StringArgumentType.getString(ctx, "item_id");
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                
                                ResourceLocation itemId = ResourceLocation.tryParse(itemIdString);
                                if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                                    throw ERROR_INVALID_ITEM.create();
                                }

                                WorldConfig worldConfig = WorldConfig.get(level);
                                worldConfig.setStarterItem(itemId);
                                
                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Objet de démarrage défini sur '" + itemId.toString() + "' et sauvegardé.", "Starter item set to '" + itemId.toString() + "' and saved."), true);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("daynight")
                        .then(Commands.argument("mode", StringArgumentType.word()) 
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.asList("day", "night", "cycle"), builder))
                            .executes(ctx -> {
                                String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

                                if (!Arrays.asList("day", "night", "cycle").contains(mode)) {
                                    throw ERROR_INVALID_DAYNIGHT_MODE.create();
                                }

                                WorldConfig worldConfig = WorldConfig.get(level);
                                worldConfig.setDayNightMode(mode);

                                String statusMessageFrench;
                                String statusMessageEnglish;
                                switch (mode) {
                                    case "day":
                                        statusMessageFrench = "jour permanent (6000)";
                                        statusMessageEnglish = "permanent day (6000)";
                                        break;
                                    case "night":
                                    default:
                                        statusMessageFrench = "nuit permanente (18000)";
                                        statusMessageEnglish = "permanent night (18000)";
                                        break;
                                    case "cycle":
                                        statusMessageFrench = "cycle jour/nuit normal";
                                        statusMessageEnglish = "normal day/night cycle";
                                        break;
                                }
                                
                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Cycle jour/nuit défini sur '" + statusMessageFrench + "' et sauvegardé.", "Day/night cycle set to '" + statusMessageEnglish + "' and saved."), true);
                                return 1;
                            })
                        )
                    )
                   .then(Commands.literal("wonderweapon")
                        .then(Commands.argument("item_id", ResourceLocationArgument.id())
                            .suggests((ctx, builder) -> {
                                return SharedSuggestionProvider.suggestResource(
                                    MysteryBoxManager.WONDER_WEAPONS.stream()
                                        .map(BuiltInRegistries.ITEM::getKey)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList()),
                                    builder
                                );
                            })
                            .then(Commands.literal("enable")
                                .executes(ctx -> {
                                    ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item_id");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                    Item wonderWeaponItem = BuiltInRegistries.ITEM.get(itemId);

                                    if (wonderWeaponItem == null || !MysteryBoxManager.WONDER_WEAPONS.contains(wonderWeaponItem)) {
                                        throw ERROR_INVALID_WONDER_WEAPON.create();
                                    }

                                    WorldConfig worldConfig = WorldConfig.get(level);
                                    worldConfig.enableWonderWeapon(itemId);

                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Wonder Weapon '" + itemId.toString() + "' activée pour cette map et sauvegardée.", "Wonder Weapon '" + itemId.toString() + "' enabled for this map and saved."), true);
                                    return 1;
                                })
                            )
                            .then(Commands.literal("disable")
                                .executes(ctx -> {
                                    ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item_id");
                                    ServerLevel level = ctx.getSource().getLevel();
                                    ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                    Item wonderWeaponItem = BuiltInRegistries.ITEM.get(itemId);

                                    if (wonderWeaponItem == null || !MysteryBoxManager.WONDER_WEAPONS.contains(wonderWeaponItem)) {
                                        throw ERROR_INVALID_WONDER_WEAPON.create();
                                    }

                                    WorldConfig worldConfig = WorldConfig.get(level);
                                    worldConfig.disableWonderWeapon(itemId);

                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Wonder Weapon '" + itemId.toString() + "' désactivée pour cette map et sauvegardée.", "Wonder Weapon '" + itemId.toString() + "' disabled for this map and saved."), true);
                                    return 1;
                                })
                            )
                        )
                    )
                    .then(Commands.literal("particles")
                        .then(Commands.literal("enable")
                            .then(Commands.argument("particle_type", ResourceLocationArgument.id()) 
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.PARTICLE_TYPE.keySet(), builder))
                                .then(Commands.argument("density", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.asList("sparse", "normal", "dense", "very_dense"),
                                        builder))
                                    .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                            Arrays.asList("global", "atmospheric"),
                                            builder))
                                        .executes(ctx -> {
                                            ServerLevel level = ctx.getSource().getLevel();
                                            ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                            ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
                                            String density = StringArgumentType.getString(ctx, "density").toLowerCase(Locale.ROOT);
                                            String mode = StringArgumentType.getString(ctx, "mode").toLowerCase(Locale.ROOT);

                                            if (!Arrays.asList("sparse", "normal", "dense", "very_dense").contains(density)) {
                                                throw ERROR_INVALID_PARTICLE_DENSITY.create();
                                            }
                                            if (!Arrays.asList("global", "atmospheric").contains(mode)) {
                                                throw ERROR_INVALID_PARTICLE_MODE.create();
                                            }
                                            
                                            WorldConfig worldConfig = WorldConfig.get(level);
                                            worldConfig.enableParticles(particleId, density, mode);

                                            level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                                p.sendSystemMessage(Component.literal("ZOMBIEROOL_PARTICLES_ENABLE:" + particleId.toString() + ":" + density + ":" + mode), true);
                                            });
                                            ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Particules de type '" + particleId.toString() + "' activées avec densité '" + density + "' et mode '" + mode + "' et sauvegardées.", "Particles of type '" + particleId.toString() + "' enabled with density '" + density + "' and mode '" + mode + "' and saved."), true);
                                            return 1;
                                        })
                                    )
                                    .executes(ctx -> {
                                        ServerLevel level = ctx.getSource().getLevel();
                                        ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                        ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
                                        String density = StringArgumentType.getString(ctx, "density").toLowerCase(Locale.ROOT);
                                        String defaultMode = "global"; // Déclaré ici

                                        if (!Arrays.asList("sparse", "normal", "dense", "very_dense").contains(density)) {
                                            throw ERROR_INVALID_PARTICLE_DENSITY.create();
                                        }

                                        WorldConfig worldConfig = WorldConfig.get(level);
                                        worldConfig.enableParticles(particleId, density, defaultMode);

                                        level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                            p.sendSystemMessage(Component.literal("ZOMBIEROOL_PARTICLES_ENABLE:" + particleId.toString() + ":" + density + ":" + defaultMode), true);
                                        });

                                        // Corrected line: use 'density' instead of 'defaultDensity' in the English message
                                        ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Particules de type '" + particleId.toString() + "' activées avec densité '" + density + "' et mode '" + defaultMode + "' et sauvegardées.", "Particles of type '" + particleId.toString() + "' enabled with density '" + density + "' and mode '" + defaultMode + "' and saved."), true);
                                        return 1;
                                    })
                                )
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                    ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
                                    String defaultDensity = "normal"; // Déclaré ici
                                    String defaultMode = "global"; // Déclaré ici

                                    WorldConfig worldConfig = WorldConfig.get(level);
                                    worldConfig.enableParticles(particleId, defaultDensity, defaultMode);

                                    level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                        p.sendSystemMessage(Component.literal("ZOMBIEROOL_PARTICLES_ENABLE:" + particleId.toString() + ":" + defaultDensity + ":" + defaultMode), true);
                                    });

                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Particules de type '" + particleId.toString() + "' activées avec densité '" + defaultDensity + "' et mode '" + defaultMode + "' et sauvegardées.", "Particles of type '" + particleId.toString() + "' enabled with density '" + defaultDensity + "' and mode '" + defaultMode + "' and saved."), true);
                                    return 1;
                                })
                            )
                        )
                        .then(Commands.literal("disable")
                            .executes(ctx -> {
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                
                                WorldConfig worldConfig = WorldConfig.get(level);
                                worldConfig.disableParticles();

                                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                    p.sendSystemMessage(Component.literal("ZOMBIEROOL_PARTICLES_DISABLE"), true);
                                });

                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Particules flottantes désactivées et sauvegardées.", "Floating particles disabled and saved."), true);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("music")
                        .then(Commands.argument("preset", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.asList("default", "illusion", "none"), builder))
                            .executes(ctx -> {
                                String preset = StringArgumentType.getString(ctx, "preset").toLowerCase(Locale.ROOT);
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

                                if (!Arrays.asList("default", "illusion", "none").contains(preset)) {
                                    throw ERROR_INVALID_MUSIC_PRESET.create();
                                }

                                WorldConfig worldConfig = WorldConfig.get(level);
                                worldConfig.setMusicPreset(preset);

                                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                    p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:" + preset), true);
                                });

                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Preset de musique défini sur '" + preset + "' et sauvegardé.", "Music preset set to '" + preset + "' and saved."), true);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("eyecolor")
	                    .then(Commands.argument("preset", StringArgumentType.string())
	                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.asList("red", "blue", "green", "default"), builder))
	                        .executes(ctx -> {
	                            String preset = StringArgumentType.getString(ctx, "preset");
	                            ServerLevel world = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
	                            WorldConfig config = WorldConfig.get(world);
	
	                            config.setEyeColorPreset(preset);
	
	                            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetEyeColorPacket(preset));
	
	                            ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Preset de couleur des yeux défini sur '" + preset + "' pour tout le monde.", "Eye color preset set to '" + preset + "' for everyone."), true);
	                            return 1;
	                        })
	                    )
	                )
                    .then(Commands.literal("supersprinters")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                WorldConfig worldConfig = WorldConfig.get(level);

                                worldConfig.setSuperSprintersEnabled(enabled);

                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Les super sprinters sont maintenant " + (enabled ? "activés" : "désactivés") + " et sauvegardés.", "Super sprinters are now " + (enabled ? "enabled" : "disabled") + " and saved."), true);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("coldwater")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(ctx -> {
                                boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
                                WorldConfig worldConfig = WorldConfig.get(level);

                                worldConfig.setColdWaterEffectEnabled(enabled);

                                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                    p.sendSystemMessage(Component.literal("ZOMBIEROOL_COLDWATER_EFFECT:" + enabled), true);
                                });

                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Effet de froid dans l'eau " + (enabled ? "activé" : "désactivé") + " et sauvegardé.", "Cold water effect " + (enabled ? "enabled" : "disabled") + " and saved."), true);
                                return 1;
                            })
                        )
                    )
                    // NOUVEAU: Commande 'voice'
                    .then(Commands.literal("voice")
                        .then(Commands.argument("preset", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.asList("uk", "us", "ru", "fr", "ger", "none"), builder))
                            .executes(ctx -> {
                                String preset = StringArgumentType.getString(ctx, "preset").toLowerCase(Locale.ROOT);
                                ServerLevel level = ctx.getSource().getLevel();
                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

                                if (!Arrays.asList("uk", "us", "ru", "fr", "ger", "none").contains(preset)) {
                                    throw ERROR_INVALID_VOICE_PRESET.create();
                                }

                                WorldConfig worldConfig = WorldConfig.get(level);
                                worldConfig.setVoicePreset(preset);

                                level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                    p.sendSystemMessage(Component.literal("ZOMBIEROOL_VOICE_PRESET:" + preset), true);
                                });

                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Preset de voix défini sur '" + preset + "' et sauvegardé.", "Voice preset set to '" + preset + "' and saved."), true);
                                return 1;
                            })
                        )
                    )
                )
        );
    }

    private static void findAllConnectedDoors(ServerLevel level, BlockPos startPos, Set<BlockPos> visited) {
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPos);
        visited.add(startPos);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = cur.relative(dir);
                if (level.isLoaded(n) && !visited.contains(n) && level.getBlockEntity(n) instanceof ObstacleDoorBlockEntity) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
    }
}
