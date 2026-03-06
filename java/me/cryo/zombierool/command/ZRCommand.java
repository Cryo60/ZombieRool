package me.cryo.zombierool.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import me.cryo.zombierool.MysteryBoxManager;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.MysteryBoxBlock;
import me.cryo.zombierool.block.PathBlock;
import me.cryo.zombierool.block.LimitBlock;
import me.cryo.zombierool.block.RestrictBlock;
import me.cryo.zombierool.block.ZombiePassBlock;
import me.cryo.zombierool.block.PlayerSpawnerBlock;
import me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerZombieBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerDogBlockEntity;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.SetEyeColorPacket;
import me.cryo.zombierool.network.packet.SetFogPresetPacket;
import me.cryo.zombierool.network.packet.OpenConfigMenuPacket;
import me.cryo.zombierool.network.packet.SyncWeatherPacket;
import me.cryo.zombierool.event.ServerEventHandler;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class ZRCommand {

	private static boolean isEnglishClient(ServerPlayer player) {
	    return true;
	}

	private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
	    if (player != null && isEnglishClient(player)) {
	        return Component.literal(englishMessage);
	    }
	    return Component.literal(frenchMessage);
	}

	private static SimpleCommandExceptionType createTranslatedError(String french, String english) {
	    return new SimpleCommandExceptionType(Component.literal(french)
	            .append(Component.literal(" / ").withStyle(ChatFormatting.GRAY)).append(Component.literal(english)));
	}

	private static final int REQUIRED_PERMISSION_LEVEL = 2;
	private static final int DEFAULT_PLAYER_SCORE = 500;
	private static final int SIDEBAR_DISPLAY_SLOT = 1; 
	private static final int MIN_WAVE_NUMBER = 0;
	private static final int OBSTACLE_SEARCH_RADIUS = 150;
	private static final int SPAWNER_LOCATE_RADIUS = 100;
	private static final int MIN_VERTICAL_SEARCH_OFFSET = -64;
	private static final int MAX_VERTICAL_SEARCH_OFFSET = 320;
	private static final double TELEPORT_CENTER_OFFSET = 0.5;
	private static final double TELEPORT_ABOVE_BLOCK_OFFSET = 1.0;

	private static final SimpleCommandExceptionType ERROR_INVALID_BONUS_TYPE = createTranslatedError("Type de bonus invalide !", "Invalid bonus type!");
    private static final SimpleCommandExceptionType ERROR_INVALID_PERK_TYPE = createTranslatedError("Type d'atout invalide !", "Invalid perk type!");
	private static final SimpleCommandExceptionType ERROR_INVALID_ITEM = createTranslatedError("Identifiant d'objet invalide ou l'objet n'existe pas !", "Invalid item ID or item does not exist!");
	private static final SimpleCommandExceptionType ERROR_INVALID_DAYNIGHT_MODE = createTranslatedError("Mode jour/nuit invalide ! Utilisez 'day', 'night' ou 'cycle'.", "Invalid day/night mode! Use 'day', 'night' or 'cycle'.");
	private static final SimpleCommandExceptionType ERROR_INVALID_WONDER_WEAPON = createTranslatedError("L'objet spécifié n'est pas une Wonder Weapon reconnue.", "The specified item is not a recognized Wonder Weapon.");
	private static final SimpleCommandExceptionType ERROR_GAME_NOT_RUNNING = createTranslatedError("Le jeu ZombieRool n'est pas en cours. Lancez d'abord '/zombierool start'.", "ZombieRool game is not running. Start with '/zombierool start' first.");
	private static final SimpleCommandExceptionType ERROR_NO_SPAWNER_BLOCKS = createTranslatedError("Aucun PlayerSpawnerBlock n'a été trouvé ou enregistré ! Placez-en d'abord.", "No PlayerSpawnerBlocks found or registered! Place some first.");
	private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_DENSITY = createTranslatedError("Densité de particule invalide !", "Invalid particle density!");
	private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_MODE = createTranslatedError("Mode de particule invalide !", "Invalid particle mode!");
	private static final SimpleCommandExceptionType ERROR_INVALID_MUSIC_PRESET = createTranslatedError("Preset de musique invalide !", "Invalid music preset!");
	private static final SimpleCommandExceptionType ERROR_GAME_NOT_RUNNING_END = createTranslatedError("Le jeu ZombieRool n'est pas en cours.", "ZombieRool game is not running.");
	private static final SimpleCommandExceptionType ERROR_INVALID_VOICE_PRESET = createTranslatedError("Preset de voix invalide !", "Invalid voice preset!");

	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
	    event.getDispatcher().register(
	        Commands.literal("zombierool")
	            .requires(src -> src.hasPermission(REQUIRED_PERMISSION_LEVEL))
                .then(Commands.literal("menu")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        ServerLevel level = player.serverLevel();
                        WorldConfig config = WorldConfig.get(level);
                        CompoundTag tag = new CompoundTag();
                        config.saveEditable(tag);

                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                            new OpenConfigMenuPacket(
                                tag,
                                WaveManager.isGameRunning(), 
                                WaveManager.getCurrentWave()
                            )
                        );
                        return 1;
                    })
                )
	            .then(Commands.literal("start")
	                .executes(ctx -> {
	                    ServerLevel level = ctx.getSource().getLevel();
	                    ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

	                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
	                         level.getServer().getWorldData().setDifficulty(Difficulty.NORMAL);
	                         ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "La difficulté était en Paisible, elle a été changée en Normal.", "Difficulty was Peaceful, changed to Normal.").withStyle(ChatFormatting.YELLOW), true);
	                    }

	                    if (WaveManager.isGameRunning()) {
	                        ctx.getSource().sendFailure(getTranslatedComponent(senderPlayer, "Le jeu est déjà en cours !", "The game is already running!").withStyle(ChatFormatting.YELLOW));
	                        return 0;
	                    }

	                    ServerScoreboard scoreboard = level.getServer().getScoreboard();
	                    Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID);
	                    if (objective == null) {
	                        objective = scoreboard.addObjective(
	                            ServerEventHandler.OBJECTIVE_ID,
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

	                    if (spawnerPositions.isEmpty()) {
	                        throw ERROR_NO_SPAWNER_BLOCKS.create();
	                    }

	                    if (spawnerPositions.size() < players.size()) {
	                        ctx.getSource().sendFailure(getTranslatedComponent(senderPlayer, "Pas assez de PlayerSpawnerBlock pour tous les joueurs en ligne !", "Not enough PlayerSpawnerBlocks for all online players!").withStyle(ChatFormatting.RED));
	                        return 0;
	                    }

	                    Collections.shuffle(spawnerPositions);
	                    WaveManager.PLAYER_RESPAWN_POINTS.clear();

	                    ResourceLocation starterItemId = worldConfig.getStarterItem();
	                    Item starterItem = BuiltInRegistries.ITEM.get(starterItemId);
	                    if (starterItem == null) {
	                        starterItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("zombierool", "m1911"));
	                        if (starterItem == null) {
	                            starterItem = net.minecraft.world.item.Items.WOODEN_SWORD;
	                        }
	                    }
	                    ItemStack starterItemStack = new ItemStack(starterItem);

	                    for (int i = 0; i < players.size(); i++) {
	                        ServerPlayer player = players.get(i);
	                        BlockPos spawnPos = spawnerPositions.get(i);
	                        WaveManager.PLAYER_RESPAWN_POINTS.put(player.getUUID(), spawnPos.immutable());
	                        player.teleportTo(spawnPos.getX() + TELEPORT_CENTER_OFFSET, spawnPos.getY() + TELEPORT_ABOVE_BLOCK_OFFSET, spawnPos.getZ() + TELEPORT_CENTER_OFFSET);
	                        player.setGameMode(GameType.ADVENTURE);
	                        PointManager.setScore(player, DEFAULT_PLAYER_SCORE);

	                        if (!player.getInventory().add(starterItemStack.copy())) {
	                            player.drop(starterItemStack.copy(), false);
	                        }
	                    }

	                    Set<BlockPos> powerSwitchPositions = new HashSet<>(worldConfig.getPowerSwitchPositions());
	                    if (!powerSwitchPositions.isEmpty()) {
	                        List<BlockPos> shuffledSwitches = new ArrayList<>(powerSwitchPositions);
	                        Collections.shuffle(shuffledSwitches);
	                        BlockPos chosenPowerSwitchPos = shuffledSwitches.get(0);

	                        for (BlockPos switchPos : powerSwitchPositions) {
	                            if (!switchPos.equals(chosenPowerSwitchPos)) {
	                                BlockState currentBlockState = level.getBlockState(switchPos);
	                                if (currentBlockState.getBlock() instanceof me.cryo.zombierool.block.PowerSwitchBlock) { 
	                                    level.removeBlock(switchPos, false); 
	                                    worldConfig.removePowerSwitchPosition(switchPos); 
	                                }
	                            } else {
	                                BlockState chosenState = level.getBlockState(chosenPowerSwitchPos);
	                                if (chosenState.getBlock() instanceof me.cryo.zombierool.block.PowerSwitchBlock && chosenState.getValue(me.cryo.zombierool.block.PowerSwitchBlock.POWERED)) {
	                                    level.setBlock(chosenPowerSwitchPos, chosenState.setValue(me.cryo.zombierool.block.PowerSwitchBlock.POWERED, false), 3);
	                                }
	                            }
	                        }
	                        worldConfig.setDirty(); 
	                    }

	                    Set<BlockPos> mysteryBoxPositions = worldConfig.getMysteryBoxPositions();
	                    if (!mysteryBoxPositions.isEmpty()) { 
	                        MysteryBoxManager mysteryBoxManager = MysteryBoxManager.get(level);
	                        mysteryBoxManager.setupInitialMysteryBox(level, 0); 
	                    }

	                    Set<BlockPos> wunderfizzPositions = worldConfig.getWunderfizzPositions();
	                    if (!wunderfizzPositions.isEmpty()) {
	                        List<BlockPos> wunderfizzList = new ArrayList<>(wunderfizzPositions);
	                        Collections.shuffle(wunderfizzList);
	                        BlockPos chosenWunderfizz = wunderfizzList.get(0);
	                        worldConfig.setActiveWunderfizzPosition(chosenWunderfizz, level);

	                        for (BlockPos wunderfizzPos : wunderfizzPositions) {
	                            BlockState wunderfizzState = level.getBlockState(wunderfizzPos);
	                            if (wunderfizzState.getBlock() instanceof me.cryo.zombierool.block.DerWunderfizzBlock) {
	                                me.cryo.zombierool.block.DerWunderfizzBlock.updatePerkTypeByIndex(level, wunderfizzPos, 0);
	                            }
	                        }
	                        worldConfig.setDirty();
	                    }

	                    WaveManager.startGame(level); 
	                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Jeu démarré ! Bonne chance, survivants !", "Game started! Good luck, survivors!").withStyle(ChatFormatting.GREEN), true);
	                    return 1;
	                })
	            )

	            .then(Commands.literal("scan")
	                .then(Commands.argument("radius", IntegerArgumentType.integer(10, 10000)) 
	                    .executes(ctx -> performScan(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
	                )
	                .executes(ctx -> performScan(ctx, 10000)) 
	            )

	            .then(Commands.literal("points")
	                 .then(Commands.literal("add")
	                    .then(Commands.argument("targets", EntityArgument.players())
	                        .then(Commands.argument("amount", IntegerArgumentType.integer())
	                            .executes(ctx -> {
	                                Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
	                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
	                                if (players.isEmpty()) return 0;

	                                ServerLevel level = ctx.getSource().getLevel();
	                                ServerScoreboard scoreboard = level.getServer().getScoreboard();
	                                Objective objective = scoreboard.getObjective(ServerEventHandler.OBJECTIVE_ID);
	                                 if (objective == null) {
	                                    objective = scoreboard.addObjective(
	                                        ServerEventHandler.OBJECTIVE_ID,
	                                        ObjectiveCriteria.DUMMY,
	                                        getTranslatedComponent(null, "Points ZombieRool", "ZombieRool Points").withStyle(style -> style.withColor(ChatFormatting.GOLD)),
	                                        ObjectiveCriteria.RenderType.INTEGER
	                                    );
	                                }
	                                scoreboard.setDisplayObjective(SIDEBAR_DISPLAY_SLOT, objective);

	                                int totalModified = 0;
	                                for (ServerPlayer player : players) {
	                                    PointManager.modifyScore(player, amount);
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
	                            if (players.isEmpty()) return 0;

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

	            .then(Commands.literal("end")
	                .executes(ctx -> {
	                    ServerLevel level = ctx.getSource().getLevel();
	                    ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;

	                    if (!WaveManager.isGameRunning()) {
	                        throw ERROR_GAME_NOT_RUNNING_END.create();
	                    }

	                    Component endMessage;
	                    if (ctx.getSource().isPlayer()) {
	                        endMessage = getTranslatedComponent(senderPlayer, "§cLa partie a été terminée par une commande.", "§cThe game was ended by a command.");
	                    } else {
	                        int currentWave = WaveManager.getCurrentWave();
	                        endMessage = getTranslatedComponent(senderPlayer, "§aVous avez survécu " + currentWave + " manches !", "§aYou survived " + currentWave + " rounds!");
	                    }

	                    WaveManager.endGame(level, endMessage);
	                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "La partie ZombieRool a été terminée.", "The ZombieRool game has ended."), true);
	                    return 1;
	                })
	            )

	            .then(Commands.literal("setWave")
	                .then(Commands.argument("waveNumber", IntegerArgumentType.integer(MIN_WAVE_NUMBER))
	                    .executes(ctx -> {
	                        ServerLevel level = ctx.getSource().getLevel();
	                        ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
	                        int waveNumber = IntegerArgumentType.getInteger(ctx, "waveNumber");

	                        if (!WaveManager.isGameRunning()) {
	                            throw ERROR_GAME_NOT_RUNNING.create();
	                        }

	                        WaveManager.forceSetWave(level, waveNumber);
	                        ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Vague définie sur " + waveNumber + ".", "Wave set to " + waveNumber + "."), true);
	                        return 1;
	                    })
	                )
	            )
	            
	            .then(Commands.literal("bonus")
	                 .then(Commands.argument("bonus_id", StringArgumentType.word())
	                    .suggests((ctx, builder) -> {
                            List<String> suggestions = new ArrayList<>(BonusManager.ALL_BONUSES.keySet());
                            suggestions.add("random");
                            return SharedSuggestionProvider.suggest(suggestions, builder);
                        })
	                    .executes(ctx -> {
	                        ServerLevel level = ctx.getSource().getLevel();
	                        ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
	                        Vec3 spawnPos = ctx.getSource().getPosition();
	                        String bonusIdString = StringArgumentType.getString(ctx, "bonus_id").toLowerCase(Locale.ROOT);

                            BonusManager.Bonus bonus = null;
                            if (bonusIdString.equals("random")) {
                                bonus = BonusManager.getRandomBonus(senderPlayer);
                            } else {
                                bonus = BonusManager.getBonus(bonusIdString);
                            }

	                        if (bonus == null) {
                                throw ERROR_INVALID_BONUS_TYPE.create();
                            }

                            BonusManager.spawnBonus(bonus, level, spawnPos);
                            final String finalName = bonus.id;
                            ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Bonus " + finalName + " apparu.", "Bonus " + finalName + " spawned."), true);
                            return 1;
	                    })
	                 )
	            )

                .then(Commands.literal("perk")
                    .then(Commands.argument("perk_id", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            List<String> suggestions = new ArrayList<>(PerksManager.ALL_PERKS.keySet());
                            suggestions.add("random");
                            return SharedSuggestionProvider.suggest(suggestions, builder);
                        })
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String perkIdString = StringArgumentType.getString(ctx, "perk_id").toLowerCase(Locale.ROOT);
                            
                            PerksManager.Perk perk = null;
                            if (perkIdString.equals("random")) {
                                List<PerksManager.Perk> unowned = PerksManager.ALL_PERKS.values().stream()
                                    .filter(p -> p.getAssociatedEffect() != null && !player.hasEffect(p.getAssociatedEffect()))
                                    .collect(Collectors.toList());
                                if (!unowned.isEmpty()) {
                                    perk = unowned.get(player.getRandom().nextInt(unowned.size()));
                                }
                            } else {
                                perk = PerksManager.ALL_PERKS.get(perkIdString);
                            }

                            if (perk == null) {
                                throw ERROR_INVALID_PERK_TYPE.create();
                            }

                            perk.applyEffect(player);
                            final String finalPerkName = perk.getName();
                            ctx.getSource().sendSuccess(() -> getTranslatedComponent(player, "Atout " + finalPerkName + " accordé.", "Perk " + finalPerkName + " granted."), true);
                            return 1;
                        })
                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> {
                                Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                String perkIdString = StringArgumentType.getString(ctx, "perk_id").toLowerCase(Locale.ROOT);
                                
                                int count = 0;
                                for (ServerPlayer player : targets) {
                                    PerksManager.Perk perk = null;
                                    if (perkIdString.equals("random")) {
                                        List<PerksManager.Perk> unowned = PerksManager.ALL_PERKS.values().stream()
                                            .filter(p -> p.getAssociatedEffect() != null && !player.hasEffect(p.getAssociatedEffect()))
                                            .collect(Collectors.toList());
                                        if (!unowned.isEmpty()) {
                                            perk = unowned.get(player.getRandom().nextInt(unowned.size()));
                                        }
                                    } else {
                                        perk = PerksManager.ALL_PERKS.get(perkIdString);
                                    }

                                    if (perk != null) {
                                        perk.applyEffect(player);
                                        count++;
                                    }
                                }
                                
                                final int finalCount = count;
                                ctx.getSource().sendSuccess(() -> getTranslatedComponent(null, "Atout accordé à " + finalCount + " joueur(s).", "Perk granted to " + finalCount + " player(s)."), true);
                                return finalCount;
                            })
                        )
                    )
                )

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

	            .then(Commands.literal("config")
	                .then(Commands.literal("fog")
                        .then(Commands.literal("preset")
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

                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetFogPresetPacket(
                                        preset, 0, 0, 0, 0.5f, 18.0f
                                    ));

                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Preset de brouillard défini sur '" + preset + "' pour tout le monde et sauvegardé.", "Fog preset set to '" + preset + "' for everyone and saved."), true);
                                    return 1;
                                })
                            )
                        )
                        .then(Commands.literal("custom")
                            .then(Commands.argument("r", FloatArgumentType.floatArg(0, 1))
                                .then(Commands.argument("g", FloatArgumentType.floatArg(0, 1))
                                    .then(Commands.argument("b", FloatArgumentType.floatArg(0, 1))
                                        .then(Commands.argument("near", FloatArgumentType.floatArg(-100, 100))
                                            .then(Commands.argument("far", FloatArgumentType.floatArg(1, 1000))
                                                .executes(ctx -> {
                                                    float r = FloatArgumentType.getFloat(ctx, "r");
                                                    float g = FloatArgumentType.getFloat(ctx, "g");
                                                    float b = FloatArgumentType.getFloat(ctx, "b");
                                                    float near = FloatArgumentType.getFloat(ctx, "near");
                                                    float far = FloatArgumentType.getFloat(ctx, "far");

                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    WorldConfig config = WorldConfig.get(level);
                                                    
                                                    config.setFogPreset("custom");
                                                    config.setCustomFogR(r);
                                                    config.setCustomFogG(g);
                                                    config.setCustomFogB(b);
                                                    config.setCustomFogNear(near);
                                                    config.setCustomFogFar(far);

                                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetFogPresetPacket(
                                                        "custom", r, g, b, near, far
                                                    ));

                                                    ctx.getSource().sendSuccess(() -> Component.literal("Custom fog set and saved."), true);
                                                    return 1;
                                                })
                                            )
                                        )
                                    )
                                )
                            )
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

	                            if (mode.equals("day")) level.setDayTime(6000);
                                else if (mode.equals("night")) level.setDayTime(18000);

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
	                                Set<ResourceLocation> disabled = new HashSet<>(worldConfig.getDisabledBoxWeapons());
                                    disabled.remove(itemId);
                                    worldConfig.setDisabledBoxWeapons(disabled);

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
	                                Set<ResourceLocation> disabled = new HashSet<>(worldConfig.getDisabledBoxWeapons());
                                    disabled.add(itemId);
                                    worldConfig.setDisabledBoxWeapons(disabled);

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
	                                        
	                                        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(true, particleId.toString(), density, mode));

	                                        ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Particules de type '" + particleId.toString() + "' activées avec densité '" + density + "' et mode '" + mode + "' et sauvegardées.", "Particles of type '" + particleId.toString() + "' enabled with density '" + density + "' and mode '" + mode + "' and saved."), true);
	                                        return 1;
	                                    })
	                                )
	                                .executes(ctx -> {
	                                    ServerLevel level = ctx.getSource().getLevel();
	                                    ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
	                                    ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
	                                    String density = StringArgumentType.getString(ctx, "density").toLowerCase(Locale.ROOT);
	                                    String defaultMode = "global"; 

	                                    if (!Arrays.asList("sparse", "normal", "dense", "very_dense").contains(density)) {
	                                        throw ERROR_INVALID_PARTICLE_DENSITY.create();
	                                    }

	                                    WorldConfig worldConfig = WorldConfig.get(level);
	                                    worldConfig.enableParticles(particleId, density, defaultMode);
                                        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(true, particleId.toString(), density, defaultMode));

	                                    ctx.getSource().sendSuccess(() -> getTranslatedComponent(senderPlayer, "Particules de type '" + particleId.toString() + "' activées avec densité '" + density + "' et mode '" + defaultMode + "' et sauvegardées.", "Particles of type '" + particleId.toString() + "' enabled with density '" + density + "' and mode '" + defaultMode + "' and saved."), true);
	                                    return 1;
	                                })
	                            )
	                            .executes(ctx -> {
	                                ServerLevel level = ctx.getSource().getLevel();
	                                ServerPlayer senderPlayer = ctx.getSource().isPlayer() ? ctx.getSource().getPlayerOrException() : null;
	                                ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
	                                String defaultDensity = "normal"; 
	                                String defaultMode = "global"; 

	                                WorldConfig worldConfig = WorldConfig.get(level);
	                                worldConfig.enableParticles(particleId, defaultDensity, defaultMode);
                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(true, particleId.toString(), defaultDensity, defaultMode));

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
                                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(false, "", "", ""));

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

	private static int performScan(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
	    ServerLevel level = ctx.getSource().getLevel();
	    ServerPlayer sender = ctx.getSource().getPlayerOrException();
	    BlockPos center = sender.blockPosition();

	    sender.sendSystemMessage(getTranslatedComponent(sender,
	        "Démarrage du scan du monde... (Rayon: " + radius + ")",
	        "Starting world scan... (Radius: " + radius + ")"));

	    WorldConfig config = WorldConfig.get(level);

	    int chunkRadius = (radius >> 4) + 1;
	    int centerChunkX = center.getX() >> 4;
	    int centerChunkZ = center.getZ() >> 4;

	    int foundWunderfizz = 0;
	    int foundMystery = 0;
	    int foundPath = 0;
	    int foundSwitch = 0;
	    int foundSpawners = 0;

	    int totalBlocksChecked = 0;

	    int minX = center.getX() - radius;
	    int maxX = center.getX() + radius;
	    int minZ = center.getZ() - radius;
	    int maxZ = center.getZ() + radius;
	    int minY = -64; 
	    int maxY = 320;

	    BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

	    for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
	        for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
	            if (level.hasChunk(centerChunkX + cx, centerChunkZ + cz)) {
	                int startBlockX = (centerChunkX + cx) << 4;
	                int startBlockZ = (centerChunkZ + cz) << 4;
	                int endBlockX = startBlockX + 15;
	                int endBlockZ = startBlockZ + 15;

	                int iterMinX = Math.max(startBlockX, minX);
	                int iterMaxX = Math.min(endBlockX, maxX);
	                int iterMinZ = Math.max(startBlockZ, minZ);
	                int iterMaxZ = Math.min(endBlockZ, maxZ);

	                if (iterMinX > iterMaxX || iterMinZ > iterMaxZ) continue;

	                for (int x = iterMinX; x <= iterMaxX; x++) {
	                    for (int z = iterMinZ; z <= iterMaxZ; z++) {
	                        for (int y = minY; y <= maxY; y++) {
	                            mutablePos.set(x, y, z);
	                            BlockState state = level.getBlockState(mutablePos);
	                            
	                            if (state.isAir()) continue; 

	                            Block block = state.getBlock();
	                            totalBlocksChecked++;

	                            if (block instanceof DerWunderfizzBlock) {
	                                config.addWunderfizzPosition(mutablePos.immutable());
	                                foundWunderfizz++;
	                            } else if (block instanceof MysteryBoxBlock) {
	                                config.addMysteryBoxPosition(mutablePos.immutable());
	                                foundMystery++;
	                            } else if (block instanceof PathBlock || block instanceof LimitBlock ||
	                                       block instanceof RestrictBlock || block instanceof ZombiePassBlock) {
	                                config.addPathPosition(mutablePos.immutable(), level);
	                                foundPath++;
	                            } else if (block instanceof me.cryo.zombierool.block.PowerSwitchBlock) {
	                                config.addPowerSwitchPosition(mutablePos.immutable());
	                                foundSwitch++;
	                            } else if (block instanceof PlayerSpawnerBlock) {
	                                config.addPlayerSpawnerPosition(mutablePos.immutable());
	                                foundSpawners++;
	                            }
	                        }
	                    }
	                }
	            }
	        }
	    }

	    int finalPath = foundPath;
	    int finalWunder = foundWunderfizz;
	    int finalBox = foundMystery;
	    int finalSwitch = foundSwitch;
	    int finalSpawn = foundSpawners;

	    ctx.getSource().sendSuccess(() -> getTranslatedComponent(sender,
	        "Scan terminé. Enregistré : " + finalPath + " chemins, " + finalWunder + " Wunderfizz, " + finalBox + " Boîtes, " + finalSwitch + " Leviers, " + finalSpawn + " Spawns.",
	        "Scan complete. Registered: " + finalPath + " paths, " + finalWunder + " Wunderfizz, " + finalBox + " Boxes, " + finalSwitch + " Switches, " + finalSpawn + " Spawns."
	    ).withStyle(ChatFormatting.GREEN), true);

	    return 1;
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