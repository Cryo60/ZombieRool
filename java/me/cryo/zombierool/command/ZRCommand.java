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
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock;
import me.cryo.zombierool.block.PathBlock;
import me.cryo.zombierool.block.LimitBlock;
import me.cryo.zombierool.block.RestrictBlock;
import me.cryo.zombierool.block.ZombiePassBlock;
import me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSetEyeColorPacket;
import me.cryo.zombierool.network.packet.S2CSetFogPresetPacket;
import me.cryo.zombierool.network.packet.S2COpenConfigMenuPacket;
import me.cryo.zombierool.network.packet.S2CSyncWeatherPacket;
import me.cryo.zombierool.network.packet.S2CReloadWeaponsPacket;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber
public class ZRCommand {
    private static final int REQUIRED_PERMISSION_LEVEL = 2;
    private static final int DEFAULT_PLAYER_SCORE = 500;
    private static final int MIN_WAVE_NUMBER = 0;
    private static final int OBSTACLE_SEARCH_RADIUS = 150;
    private static final int SPAWNER_LOCATE_RADIUS = 100;
    private static final int MIN_VERTICAL_SEARCH_OFFSET = -64;
    private static final int MAX_VERTICAL_SEARCH_OFFSET = 320;
    private static final double TELEPORT_CENTER_OFFSET = 0.5;
    private static final double TELEPORT_ABOVE_BLOCK_OFFSET = 1.0;

    private static final SimpleCommandExceptionType ERROR_INVALID_BONUS_TYPE = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_bonus"));
    private static final SimpleCommandExceptionType ERROR_INVALID_PERK_TYPE = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_perk"));
    private static final SimpleCommandExceptionType ERROR_INVALID_ITEM = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_item"));
    private static final SimpleCommandExceptionType ERROR_INVALID_DAYNIGHT_MODE = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_daynight"));
    private static final SimpleCommandExceptionType ERROR_INVALID_WONDER_WEAPON = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_wonder_weapon"));
    private static final SimpleCommandExceptionType ERROR_GAME_NOT_RUNNING = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.game_not_running"));
    private static final SimpleCommandExceptionType ERROR_NO_SPAWNER_BLOCKS = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.no_spawner"));
    private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_DENSITY = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_particle_density"));
    private static final SimpleCommandExceptionType ERROR_INVALID_PARTICLE_MODE = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_particle_mode"));
    private static final SimpleCommandExceptionType ERROR_INVALID_MUSIC_PRESET = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_music"));
    private static final SimpleCommandExceptionType ERROR_GAME_NOT_RUNNING_END = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.game_not_running_end"));
    private static final SimpleCommandExceptionType ERROR_INVALID_VOICE_PRESET = new SimpleCommandExceptionType(Component.translatable("command.zombierool.error.invalid_voice"));

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("zombierool")
                        .requires(src -> src.hasPermission(REQUIRED_PERMISSION_LEVEL))

                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    me.cryo.zombierool.core.system.WeaponSystem.Loader.loadWeapons();
                                    me.cryo.zombierool.integration.TacZIntegration.syncTaczGunData();
                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CReloadWeaponsPacket());
                                    me.cryo.zombierool.core.manager.DynamicResourceManager.loadWorldResources(level);

                                    for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                                        for (Map.Entry<String, Map<String, byte[]>> entry : me.cryo.zombierool.core.manager.DynamicResourceManager.getAllServerSkins().entrySet()) {
                                            String mobType = entry.getKey();
                                            for (Map.Entry<String, byte[]> skinEntry : entry.getValue().entrySet()) {
                                                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),
                                                        new me.cryo.zombierool.network.packet.S2CSyncDynamicSkinPacket(mobType, skinEntry.getKey(), skinEntry.getValue()));
                                            }
                                        }
                                        me.cryo.zombierool.core.manager.DynamicResourceManager.sendAudioToPlayer(p);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.reloaded").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("menu")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    WorldConfig config = WorldConfig.get(level);
                                    CompoundTag tag = new CompoundTag();
                                    config.saveEditable(tag);
                                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                                            new S2COpenConfigMenuPacket(
                                                    tag,
                                                    WaveManager.isGameRunning(), 
                                                    WaveManager.getCurrentWave()
                                            )
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("player_voice")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.asList("uk", "us", "ru", "fr", "ger", "default", "none"), builder))
                                                .executes(ctx -> {
                                                    Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
                                                    String preset = StringArgumentType.getString(ctx, "preset").toLowerCase(Locale.ROOT);
                                                    for (ServerPlayer p : targets) {
                                                        p.getPersistentData().putString("zr_voice_preset", preset);
                                                    }
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.voice_preset", preset, targets.size()), true);
                                                    return targets.size();
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("start")
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    if (level.getDifficulty() == Difficulty.PEACEFUL) {
                                        level.getServer().getWorldData().setDifficulty(Difficulty.NORMAL);
                                        ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.difficulty_changed").withStyle(ChatFormatting.YELLOW), true);
                                    }
                                    if (WaveManager.isGameRunning()) {
                                        ctx.getSource().sendFailure(Component.translatable("command.zombierool.error.already_running").withStyle(ChatFormatting.YELLOW));
                                        return 0;
                                    }
                                    WorldConfig worldConfig = WorldConfig.get(level);
                                    List<BlockPos> spawnerPositions = new ArrayList<>(worldConfig.getPlayerSpawnerPositions());
                                    List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
                                    if (spawnerPositions.isEmpty()) {
                                        throw ERROR_NO_SPAWNER_BLOCKS.create();
                                    }
                                    if (spawnerPositions.size() < players.size()) {
                                        ctx.getSource().sendFailure(Component.translatable("command.zombierool.error.not_enough_spawners").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    Collections.shuffle(spawnerPositions);
                                    WaveManager.PLAYER_RESPAWN_POINTS.clear();
                                    ResourceLocation starterItemId = worldConfig.getStarterItem();
                                    for (int i = 0; i < players.size(); i++) {
                                        ServerPlayer player = players.get(i);
                                        BlockPos spawnPos = spawnerPositions.get(i);
                                        player.getInventory().clearContent();
                                        player.removeAllEffects();
                                        ItemStack starterItemStack = me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack(starterItemId.toString(), false, player);
                                        if (starterItemStack.isEmpty()) {
                                            net.minecraft.world.item.Item starterItem = BuiltInRegistries.ITEM.get(starterItemId);
                                            if (starterItem == null) starterItem = net.minecraft.world.item.Items.WOODEN_SWORD;
                                            starterItemStack = new ItemStack(starterItem);
                                        }
                                        WaveManager.PLAYER_RESPAWN_POINTS.put(player.getUUID(), spawnPos.immutable());
                                        player.teleportTo(spawnPos.getX() + TELEPORT_CENTER_OFFSET, spawnPos.getY() + TELEPORT_ABOVE_BLOCK_OFFSET, spawnPos.getZ() + TELEPORT_CENTER_OFFSET);
                                        player.setGameMode(GameType.ADVENTURE);
                                        player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                                            cap.setPoints(DEFAULT_PLAYER_SCORE);
                                            cap.resetStats();
                                            cap.resetPerkPurchases();
                                            cap.sync(player);
                                        });
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
                                    Set<BlockPos> wunderfizzPositions = worldConfig.getWunderfizzPositions();
                                    if (!wunderfizzPositions.isEmpty()) {
                                        List<BlockPos> wunderfizzList = new ArrayList<>(wunderfizzPositions);
                                        Collections.shuffle(wunderfizzList);
                                        BlockPos chosenWunderfizz = wunderfizzList.get(0);
                                        worldConfig.setActiveWunderfizzPosition(chosenWunderfizz, level);
                                        for (BlockPos wunderfizzPos : wunderfizzPositions) {
                                            BlockState wunderfizzState = level.getBlockState(wunderfizzPos);
                                            if (wunderfizzState.getBlock() instanceof me.cryo.zombierool.block.DerWunderfizzBlock) {
                                                BlockEntity be = level.getBlockEntity(wunderfizzPos);
                                                if (be instanceof DerWunderfizzBlockEntity dbe) {
                                                    dbe.resetToIdleState();
                                                }
                                            }
                                        }
                                        worldConfig.setDirty();
                                    }
                                    WaveManager.startGame(level); 
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.game_started").withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("wallweapons")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ServerLevel level = player.serverLevel();
                                    Direction playerFacing = player.getDirection();
                                    Direction lineDirection = playerFacing.getClockWise();
                                    BlockPos startPos = player.blockPosition().relative(playerFacing, 2);
                                    List<String> weapons = new ArrayList<>(me.cryo.zombierool.core.system.WeaponSystem.Loader.LOADED_DEFINITIONS.keySet());
                                    Collections.sort(weapons);
                                    int count = 0;
                                    for (String weaponId : weapons) {
                                        BlockPos currentPos = startPos.relative(lineDirection, count);
                                        level.setBlock(currentPos, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 3);
                                        BlockPos weaponPos = currentPos.above();
                                        BlockState wallState = me.cryo.zombierool.block.system.BuyWallWeaponSystem.BLOCK.get().defaultBlockState()
                                                .setValue(me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlock.FACING, playerFacing.getOpposite());
                                        level.setBlock(weaponPos, wallState, 3);
                                        BlockEntity be = level.getBlockEntity(weaponPos);
                                        if (be instanceof me.cryo.zombierool.block.system.BuyWallWeaponSystem.BuyWallWeaponBlockEntity wallWeap) {
                                            wallWeap.setItemToSell(new ResourceLocation("zombierool", weaponId));
                                            wallWeap.setPrice(1);
                                            wallWeap.setMimic(net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState());
                                            wallWeap.setChanged();
                                            level.sendBlockUpdated(weaponPos, wallState, wallState, 3);
                                        }
                                        count++;
                                    }
                                    final int finalCount = count;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.wall_weapons", finalCount).withStyle(ChatFormatting.GREEN), true);
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
                                                            WaveManager.setCheatsUsed(true); 
                                                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            if (players.isEmpty()) return 0;
                                                            int totalModified = 0;
                                                            for (ServerPlayer player : players) {
                                                                PointManager.modifyScore(player, amount);
                                                                totalModified++;
                                                            }
                                                            final int finalTotalModified = totalModified;
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.points_added", amount, finalTotalModified), true);
                                                            return finalTotalModified;
                                                        })
                                                )
                                        )
                                )
                                .then(Commands.literal("reset")
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> {
                                                    WaveManager.setCheatsUsed(true); 
                                                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                                                    if (players.isEmpty()) return 0;
                                                    int totalReset = 0;
                                                    for (ServerPlayer player : players) {
                                                        PointManager.setScore(player, DEFAULT_PLAYER_SCORE);
                                                        totalReset++;
                                                    }
                                                    final int finalTotalReset = totalReset;
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.points_reset", finalTotalReset, DEFAULT_PLAYER_SCORE), true);
                                                    return finalTotalReset;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("end")
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    if (!WaveManager.isGameRunning()) {
                                        throw ERROR_GAME_NOT_RUNNING_END.create();
                                    }
                                    Component endMessage;
                                    if (ctx.getSource().isPlayer()) {
                                        endMessage = Component.translatable("command.zombierool.success.game_ended_cmd").withStyle(ChatFormatting.RED);
                                    } else {
                                        int currentWave = WaveManager.getCurrentWave();
                                        endMessage = Component.translatable("command.zombierool.success.game_ended_survived", currentWave).withStyle(ChatFormatting.GREEN);
                                    }
                                    WaveManager.endGame(level, endMessage);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.game_ended"), true);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("setWave")
                                .then(Commands.argument("waveNumber", IntegerArgumentType.integer(MIN_WAVE_NUMBER))
                                        .executes(ctx -> {
                                            WaveManager.setCheatsUsed(true); 
                                            ServerLevel level = ctx.getSource().getLevel();
                                            int waveNumber = IntegerArgumentType.getInteger(ctx, "waveNumber");
                                            WaveManager.forceSetWave(level, waveNumber);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.wave_set", waveNumber), true);
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
                                            WaveManager.setCheatsUsed(true); 
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
                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.bonus_spawned", finalName), true);
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
                                            WaveManager.setCheatsUsed(true); 
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
                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.perk_granted", finalPerkName), true);
                                            return 1;
                                        })
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(ctx -> {
                                                    WaveManager.setCheatsUsed(true); 
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
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.perk_granted_multiple", finalCount), true);
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
                                            for (BlockPos iterPos : BlockPos.betweenClosed(
                                                    player.blockPosition().offset(-OBSTACLE_SEARCH_RADIUS, MIN_VERTICAL_SEARCH_OFFSET, -OBSTACLE_SEARCH_RADIUS),
                                                    player.blockPosition().offset(OBSTACLE_SEARCH_RADIUS, MAX_VERTICAL_SEARCH_OFFSET, OBSTACLE_SEARCH_RADIUS))) {
                                                BlockPos pos = iterPos.immutable();
                                                if (!level.isLoaded(pos)) continue;
                                                BlockEntity be = level.getBlockEntity(pos);
                                                if (!(be instanceof ObstacleDoorBlockEntity obstacle) || visited.contains(pos)) continue;
                                                findAllConnectedDoors(level, pos, visited);
                                                String canal = obstacle.getCanal();
                                                int prix = obstacle.getPrix();
                                                MutableComponent msg = Component.translatable("command.zombierool.success.obstacle_found", pos.getX(), pos.getY(), pos.getZ(), canal, prix)
                                                        .withStyle(style -> style
                                                                .withColor(ChatFormatting.YELLOW)
                                                                .withClickEvent(new ClickEvent(
                                                                        ClickEvent.Action.RUN_COMMAND,
                                                                        "/execute at @s run tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ() 
                                                                ))
                                                                .withHoverEvent(new HoverEvent(
                                                                        HoverEvent.Action.SHOW_TEXT,
                                                                        Component.translatable("command.zombierool.success.click_teleport")
                                                                ))
                                                        );
                                                player.sendSystemMessage(msg);
                                            }
                                            return 1;
                                        })
                                )
                                .then(Commands.literal("spawner")
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"zombie", "crawler", "hellhound", "player"}, b))
                                                .executes(ctx -> {
                                                    String typeStr = StringArgumentType.getString(ctx, "type");
                                                    UniversalSpawnerSystem.SpawnerMobType targetType = UniversalSpawnerSystem.SpawnerMobType.fromString(typeStr);
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    Player player = ctx.getSource().getPlayerOrException();
                                                    List<MutableComponent> spawnerList = new ArrayList<>();
                                                    for (BlockPos iterPos : BlockPos.betweenClosed(
                                                            player.blockPosition().offset(-SPAWNER_LOCATE_RADIUS, MIN_VERTICAL_SEARCH_OFFSET, -SPAWNER_LOCATE_RADIUS),
                                                            player.blockPosition().offset(SPAWNER_LOCATE_RADIUS, MAX_VERTICAL_SEARCH_OFFSET, SPAWNER_LOCATE_RADIUS))) {
                                                        BlockPos pos = iterPos.immutable();
                                                        if (!level.isLoaded(pos)) continue;
                                                        BlockEntity be = level.getBlockEntity(pos);
                                                        if (be instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity sp && sp.getMobType() == targetType) {
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
                                                                                    Component.translatable("command.zombierool.success.click_teleport")
                                                                            ))
                                                                    );
                                                            spawnerList.add(
                                                                    Component.literal("- ")
                                                                            .append(spawnerInfo)
                                                                            .append(Component.translatable("command.zombierool.success.spawner_channels", sp.getStartChannels(), sp.getStopChannels())
                                                                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY)))
                                                            );
                                                        }
                                                    }
                                                    if (spawnerList.isEmpty()) {
                                                        player.sendSystemMessage(Component.translatable("command.zombierool.success.no_spawner_found")
                                                                .withStyle(style -> style.withColor(ChatFormatting.RED)));
                                                    } else {
                                                        player.sendSystemMessage(Component.translatable("command.zombierool.success.spawners_found", spawnerList.size()).withStyle(ChatFormatting.GOLD));
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
                                                            WorldConfig worldConfig = WorldConfig.get(level);
                                                            worldConfig.setFogPreset(preset);
                                                            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetFogPresetPacket(
                                                                    preset, 0, 0, 0, 0.5f, 18.0f
                                                            ));
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.fog_preset", preset), true);
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
                                                                                            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetFogPresetPacket(
                                                                                                    "custom", r, g, b, near, far
                                                                                            ));
                                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.custom_fog"), true);
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
                                                    ResourceLocation itemId = ResourceLocation.tryParse(itemIdString);
                                                    if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
                                                        throw ERROR_INVALID_ITEM.create();
                                                    }
                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.setStarterItem(itemId);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.starter_item", itemId.toString()), true);
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
                                                    if (!Arrays.asList("day", "night", "cycle").contains(mode)) {
                                                        throw ERROR_INVALID_DAYNIGHT_MODE.create();
                                                    }
                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.setDayNightMode(mode);
                                                    if (mode.equals("day")) level.setDayTime(6000);
                                                    else if (mode.equals("night")) level.setDayTime(18000);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.daynight", mode), true);
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
                                                            net.minecraft.world.item.Item wonderWeaponItem = BuiltInRegistries.ITEM.get(itemId);
                                                            if (wonderWeaponItem == null || !MysteryBoxManager.WONDER_WEAPONS.contains(wonderWeaponItem)) {
                                                                throw ERROR_INVALID_WONDER_WEAPON.create();
                                                            }
                                                            WorldConfig worldConfig = WorldConfig.get(level);
                                                            Set<ResourceLocation> disabled = new HashSet<>(worldConfig.getDisabledBoxWeapons());
                                                            disabled.remove(itemId);
                                                            worldConfig.setDisabledBoxWeapons(disabled);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.wonder_enabled", itemId.toString()), true);
                                                            return 1;
                                                        })
                                                )
                                                .then(Commands.literal("disable")
                                                        .executes(ctx -> {
                                                            ResourceLocation itemId = ResourceLocationArgument.getId(ctx, "item_id");
                                                            ServerLevel level = ctx.getSource().getLevel();
                                                            net.minecraft.world.item.Item wonderWeaponItem = BuiltInRegistries.ITEM.get(itemId);
                                                            if (wonderWeaponItem == null || !MysteryBoxManager.WONDER_WEAPONS.contains(wonderWeaponItem)) {
                                                                throw ERROR_INVALID_WONDER_WEAPON.create();
                                                            }
                                                            WorldConfig worldConfig = WorldConfig.get(level);
                                                            Set<ResourceLocation> disabled = new HashSet<>(worldConfig.getDisabledBoxWeapons());
                                                            disabled.add(itemId);
                                                            worldConfig.setDisabledBoxWeapons(disabled);
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.wonder_disabled", itemId.toString()), true);
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
                                                                            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncWeatherPacket(true, particleId.toString(), density, mode));

                                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.particles_enabled", particleId.toString(), density, mode), true);
                                                                            return 1;
                                                                        })
                                                                )
                                                                .executes(ctx -> {
                                                                    ServerLevel level = ctx.getSource().getLevel();
                                                                    ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
                                                                    String density = StringArgumentType.getString(ctx, "density").toLowerCase(Locale.ROOT);
                                                                    String defaultMode = "global"; 

                                                                    if (!Arrays.asList("sparse", "normal", "dense", "very_dense").contains(density)) {
                                                                        throw ERROR_INVALID_PARTICLE_DENSITY.create();
                                                                    }

                                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                                    worldConfig.enableParticles(particleId, density, defaultMode);
                                                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncWeatherPacket(true, particleId.toString(), density, defaultMode));

                                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.particles_enabled", particleId.toString(), density, defaultMode), true);
                                                                    return 1;
                                                                })
                                                        )
                                                        .executes(ctx -> {
                                                            ServerLevel level = ctx.getSource().getLevel();
                                                            ResourceLocation particleId = ResourceLocationArgument.getId(ctx, "particle_type");
                                                            String defaultDensity = "normal"; 
                                                            String defaultMode = "global"; 

                                                            WorldConfig worldConfig = WorldConfig.get(level);
                                                            worldConfig.enableParticles(particleId, defaultDensity, defaultMode);
                                                            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncWeatherPacket(true, particleId.toString(), defaultDensity, defaultMode));

                                                            ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.particles_enabled", particleId.toString(), defaultDensity, defaultMode), true);
                                                            return 1;
                                                        })
                                                )
                                        )
                                        .then(Commands.literal("disable")
                                                .executes(ctx -> {
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.disableParticles();
                                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncWeatherPacket(false, "", "", ""));
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.particles_disabled"), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("music")
                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.asList("default", "damned", "none"), builder))
                                                .executes(ctx -> {
                                                    String preset = StringArgumentType.getString(ctx, "preset").toLowerCase(Locale.ROOT);
                                                    ServerLevel level = ctx.getSource().getLevel();

                                                    if (!Arrays.asList("default", "damned", "none").contains(preset)) {
                                                        throw ERROR_INVALID_MUSIC_PRESET.create();
                                                    }

                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.setMusicPreset(preset);

                                                    level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                                        p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:" + preset), true);
                                                    });

                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.music_preset", preset), true);
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
                                                    WorldConfig config = WorldConfig.get(world);
                                                    config.setEyeColorPreset(preset);
                                                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSetEyeColorPacket(preset));
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.eye_color", preset), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("supersprinters")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.setSuperSprintersEnabled(enabled);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.super_sprinters", enabled ? "enabled" : "disabled"), true);
                                                    return 1;
                                                })
                                        )
                                )
                                .then(Commands.literal("coldwater")
                                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                                .executes(ctx -> {
                                                    boolean enabled = BoolArgumentType.getBool(ctx, "enabled");
                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.setColdWaterEffectEnabled(enabled);

                                                    level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                                        p.sendSystemMessage(Component.literal("ZOMBIEROOL_COLDWATER_EFFECT:" + enabled), true);
                                                    });

                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.cold_water", enabled ? "enabled" : "disabled"), true);
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

                                                    if (!Arrays.asList("uk", "us", "ru", "fr", "ger", "none").contains(preset)) {
                                                        throw ERROR_INVALID_VOICE_PRESET.create();
                                                    }

                                                    WorldConfig worldConfig = WorldConfig.get(level);
                                                    worldConfig.setVoicePreset(preset);

                                                    level.getServer().getPlayerList().getPlayers().forEach(p -> {
                                                        p.sendSystemMessage(Component.literal("ZOMBIEROOL_VOICE_PRESET:" + preset), true);
                                                    });

                                                    ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.success.voice_preset_saved", preset), true);
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

        sender.sendSystemMessage(Component.translatable("command.zombierool.scan.start", radius));

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
                                        block instanceof RestrictBlock || block instanceof ZombiePassBlock ||
                                        block instanceof UniversalSpawnerSystem.UniversalSpawnerBlock) {
                                    config.addPathPosition(mutablePos.immutable(), level);
                                    foundPath++;

                                    if (block instanceof UniversalSpawnerSystem.UniversalSpawnerBlock) {
                                        BlockEntity be = level.getBlockEntity(mutablePos);
                                        if (be instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity ube && ube.getMobType() == UniversalSpawnerSystem.SpawnerMobType.PLAYER) {
                                            config.addPlayerSpawnerPosition(mutablePos.immutable());
                                            foundSpawners++;
                                        }
                                    }
                                } else if (block instanceof me.cryo.zombierool.block.PowerSwitchBlock) {
                                    config.addPowerSwitchPosition(mutablePos.immutable());
                                    foundSwitch++;
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

        ctx.getSource().sendSuccess(() -> Component.translatable("command.zombierool.scan.complete", finalPath, finalWunder, finalBox, finalSwitch, finalSpawn).withStyle(ChatFormatting.GREEN), true);
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