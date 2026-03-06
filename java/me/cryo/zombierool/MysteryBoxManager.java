package me.cryo.zombierool;

import me.cryo.zombierool.block.MysteryBoxBlock;
import me.cryo.zombierool.block.EmptymysteryboxBlock;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.item.IngotSaleItem;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MysteryBoxManager extends SavedData {
    private static final String DATA_NAME = "zombierool_mysterybox_manager";
    private static final Random STATIC_RANDOM = new Random();
    public static final Set<Item> WONDER_WEAPONS = new HashSet<>();

    private static boolean isEnglishClient(Player player) {
        return true; 
    }

    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    public static class MysteryBoxPair {
        public BlockPos mainPartPos;
        public BlockPos otherPartPos;
        public Direction facing;
        public int usesSinceLastMove;
        public int moveThreshold;

        public MysteryBoxPair(BlockPos mainPartPos, BlockPos otherPartPos, Direction facing) {
            this.mainPartPos = mainPartPos;
            this.otherPartPos = otherPartPos;
            this.facing = facing;
            this.usesSinceLastMove = 0;
            this.moveThreshold = generateRandomMoveThreshold();
        }

        public MysteryBoxPair(BlockPos mainPartPos, BlockPos otherPartPos, Direction facing, int usesSinceLastMove, int moveThreshold) {
            this(mainPartPos, otherPartPos, facing);
            this.usesSinceLastMove = usesSinceLastMove;
            this.moveThreshold = moveThreshold;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.put("mainPartPos", NbtUtils.writeBlockPos(mainPartPos));
            tag.put("otherPartPos", NbtUtils.writeBlockPos(otherPartPos));
            tag.putString("facing", facing.getName());
            tag.putInt("usesSinceLastMove", usesSinceLastMove);
            tag.putInt("moveThreshold", moveThreshold);
            return tag;
        }

        public static MysteryBoxPair load(CompoundTag tag) {
            return new MysteryBoxPair(
                NbtUtils.readBlockPos(tag.getCompound("mainPartPos")),
                NbtUtils.readBlockPos(tag.getCompound("otherPartPos")),
                Direction.byName(tag.getString("facing")),
                tag.getInt("usesSinceLastMove"),
                tag.getInt("moveThreshold")
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MysteryBoxPair that = (MysteryBoxPair) o;
            return Objects.equals(mainPartPos, that.mainPartPos) &&
                Objects.equals(otherPartPos, that.otherPartPos) &&
                facing == that.facing;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mainPartPos, otherPartPos, facing);
        }
    }

    private List<MysteryBoxPair> registeredMysteryBoxLocations = new ArrayList<>();
    private MysteryBoxPair currentActiveMysteryBoxPair = null;
    private Random random = new Random();

    public boolean isMysteryBoxMoving = false;
    private long moveStartTime = 0;
    public static final int MOVE_DELAY_TICKS = 13 * 20;

    public boolean isAwaitingWeapon = false;
    private long jingleStartTime = 0;
    public static final int JINGLE_DELAY_TICKS = 125; 

    private UUID playerWhoCausedActionUUID = null;
    private ItemStack weaponToGive = ItemStack.EMPTY;

    public MysteryBoxManager() {}

    public static MysteryBoxManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            MysteryBoxManager::load,
            MysteryBoxManager::new,
            DATA_NAME
        );
    }

    public static MysteryBoxManager load(CompoundTag nbt) {
        MysteryBoxManager manager = new MysteryBoxManager();
        if (nbt.contains("CurrentActiveMysteryBoxPair")) {
            manager.currentActiveMysteryBoxPair = MysteryBoxPair.load(nbt.getCompound("CurrentActiveMysteryBoxPair"));
        }
        manager.isMysteryBoxMoving = nbt.getBoolean("IsMysteryBoxMoving");
        manager.moveStartTime = nbt.getLong("MoveStartTime");
        manager.isAwaitingWeapon = nbt.getBoolean("IsAwaitingWeapon");
        manager.jingleStartTime = nbt.getLong("JingleStartTime");
        if (nbt.hasUUID("PlayerWhoCausedActionUUID")) {
            manager.playerWhoCausedActionUUID = nbt.getUUID("PlayerWhoCausedActionUUID");
        }
        if (nbt.contains("WeaponToGive")) {
            manager.weaponToGive = ItemStack.of(nbt.getCompound("WeaponToGive"));
        }

        if (nbt.contains("RegisteredMysteryBoxLocations", ListTag.TAG_LIST)) {
            ListTag listTag = nbt.getList("RegisteredMysteryBoxLocations", ListTag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                manager.registeredMysteryBoxLocations.add(MysteryBoxPair.load(listTag.getCompound(i)));
            }
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        if (currentActiveMysteryBoxPair != null) {
            compound.put("CurrentActiveMysteryBoxPair", currentActiveMysteryBoxPair.save());
        }
        compound.putBoolean("IsMysteryBoxMoving", isMysteryBoxMoving);
        compound.putLong("MoveStartTime", moveStartTime);
        compound.putBoolean("IsAwaitingWeapon", isAwaitingWeapon);
        compound.putLong("JingleStartTime", jingleStartTime);
        if (playerWhoCausedActionUUID != null) {
            compound.putUUID("PlayerWhoCausedActionUUID", playerWhoCausedActionUUID);
        }
        if (!weaponToGive.isEmpty()) {
            compound.put("WeaponToGive", weaponToGive.save(new CompoundTag()));
        }

        ListTag listTag = new ListTag();
        for (MysteryBoxPair pair : registeredMysteryBoxLocations) {
            listTag.add(pair.save());
        }
        compound.put("RegisteredMysteryBoxLocations", listTag);
        return compound;
    }

    public static BlockPos getOtherPartPos(BlockPos mainPartPos, Direction facing) {
        switch (facing) {
            case NORTH: return mainPartPos.west();
            case SOUTH: return mainPartPos.east();
            case EAST: return mainPartPos.north();
            case WEST: return mainPartPos.south();
            default: return mainPartPos.west();
        }
    }

    public static BlockPos getOppositeOtherPartPos(BlockPos otherPartPos, Direction facing) {
        switch (facing) {
            case NORTH: return otherPartPos.east();
            case SOUTH: return otherPartPos.west();
            case EAST: return otherPartPos.south();
            case WEST: return otherPartPos.north();
            default: return otherPartPos.east();
        }
    }

    private static int generateRandomMoveThreshold() {
        return STATIC_RANDOM.nextInt(9) + 4;
    }

    public void setupInitialMysteryBox(ServerLevel level, int initialScanRadius) {
        this.currentActiveMysteryBoxPair = null;
        this.isMysteryBoxMoving = false;
        this.isAwaitingWeapon = false;
        this.playerWhoCausedActionUUID = null;
        this.moveStartTime = 0;
        this.jingleStartTime = 0;

        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> registeredPositions = worldConfig.getMysteryBoxPositions();

        List<MysteryBoxPair> potentialBoxPairs = new ArrayList<>();
        Set<BlockPos> processedMainPositions = new HashSet<>();

        for (BlockPos pos : registeredPositions) {
            if (processedMainPositions.contains(pos)) continue;
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof MysteryBoxBlock || state.getBlock() instanceof EmptymysteryboxBlock) {
                boolean isOtherPart = state.getValue(MysteryBoxBlock.PART);
                BlockPos mainPartPos;
                BlockPos otherPartPos;
                Direction facing = state.getValue(MysteryBoxBlock.FACING);

                if (isOtherPart) {
                    otherPartPos = pos;
                    mainPartPos = getOppositeOtherPartPos(pos, facing);
                } else {
                    mainPartPos = pos;
                    otherPartPos = getOtherPartPos(pos, facing);
                }

                BlockState mainState = level.getBlockState(mainPartPos);
                BlockState otherState = level.getBlockState(otherPartPos);

                if ((mainState.getBlock() instanceof MysteryBoxBlock || mainState.getBlock() instanceof EmptymysteryboxBlock) &&
                    (otherState.getBlock() instanceof MysteryBoxBlock || otherState.getBlock() instanceof EmptymysteryboxBlock) &&
                    mainState.getValue(MysteryBoxBlock.FACING) == facing && !mainState.getValue(MysteryBoxBlock.PART) &&
                    otherState.getValue(MysteryBoxBlock.FACING) == facing && otherState.getValue(MysteryBoxBlock.PART)) {
                    MysteryBoxPair newPair = new MysteryBoxPair(mainPartPos.immutable(), otherPartPos.immutable(), facing);
                    if (!potentialBoxPairs.contains(newPair)) {
                        potentialBoxPairs.add(newPair);
                        processedMainPositions.add(mainPartPos);
                    }
                }
            }
        }

        this.registeredMysteryBoxLocations.clear();
        this.registeredMysteryBoxLocations.addAll(potentialBoxPairs);
        setDirty();

        if (this.registeredMysteryBoxLocations.isEmpty()) {
            return;
        }

        for (MysteryBoxPair pair : this.registeredMysteryBoxLocations) {
            level.setBlock(pair.mainPartPos, ZombieroolModBlocks.EMPTYMYSTERYBOX.get().defaultBlockState()
                .setValue(EmptymysteryboxBlock.FACING, pair.facing)
                .setValue(EmptymysteryboxBlock.PART, false), 3);
            level.setBlock(pair.otherPartPos, ZombieroolModBlocks.EMPTYMYSTERYBOX.get().defaultBlockState()
                .setValue(EmptymysteryboxBlock.FACING, pair.facing)
                .setValue(EmptymysteryboxBlock.PART, true), 3);
        }

        MysteryBoxPair chosenPairForActivation = this.registeredMysteryBoxLocations.get(random.nextInt(this.registeredMysteryBoxLocations.size()));
        
        level.setBlock(chosenPairForActivation.mainPartPos, ZombieroolModBlocks.MYSTERY_BOX.get().defaultBlockState()
            .setValue(MysteryBoxBlock.FACING, chosenPairForActivation.facing)
            .setValue(MysteryBoxBlock.PART, false), 3);
        level.setBlock(chosenPairForActivation.otherPartPos, ZombieroolModBlocks.MYSTERY_BOX.get().defaultBlockState()
            .setValue(MysteryBoxBlock.FACING, chosenPairForActivation.facing)
            .setValue(MysteryBoxBlock.PART, true), 3);

        this.currentActiveMysteryBoxPair = new MysteryBoxPair(
            chosenPairForActivation.mainPartPos,
            chosenPairForActivation.otherPartPos,
            chosenPairForActivation.facing
        );
        this.currentActiveMysteryBoxPair.usesSinceLastMove = 0;
        this.currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold();
        setDirty();

        level.getServer().getPlayerList().broadcastSystemMessage(
            getTranslatedComponent(null, "La Mystery Box est apparue à : ", "The Mystery Box has appeared at: ")
            .append(Component.literal(currentActiveMysteryBoxPair.mainPartPos.getX() + " " + currentActiveMysteryBoxPair.mainPartPos.getY() + " " + currentActiveMysteryBoxPair.mainPartPos.getZ()))
            .withStyle(ChatFormatting.GOLD), false
        );
    }

    private boolean isWonderWeapon(WeaponSystem.Definition def) {
        return def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type);
    }

    public boolean hasAvailableWeapons(Player player, ServerLevel level) {
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<String> requiredTags = worldConfig.getMysteryBoxTags();

        for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
            ResourceLocation defLoc = new ResourceLocation(def.id != null && def.id.contains(":") ? def.id : "zombierool:" + def.id);
            if (worldConfig.isBoxWeaponDisabled(defLoc)) continue;

            if (requiredTags != null && !requiredTags.isEmpty()) {
                boolean matchesTag = false;
                if (def.tags != null) {
                    for (String tag : requiredTags) {
                        if (def.tags.contains(tag)) {
                            matchesTag = true;
                            break;
                        }
                    }
                }
                if (!matchesTag) continue;
            }

            boolean owned = false;
            for (ItemStack stack : player.getInventory().items) {
                if (WeaponFacade.isWeapon(stack)) {
                    WeaponSystem.Definition d = WeaponFacade.getDefinition(stack);
                    if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                        owned = true;
                        break;
                    }
                }
            }
            if (owned) continue;

            if (isWonderWeapon(def)) {
                boolean ownedByAnyone = false;
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    for (ItemStack stack : p.getInventory().items) {
                        if (WeaponFacade.isWeapon(stack)) {
                            WeaponSystem.Definition d = WeaponFacade.getDefinition(stack);
                            if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                                ownedByAnyone = true;
                                break;
                            }
                        }
                    }
                    if (ownedByAnyone) break;
                }
                if (ownedByAnyone) continue;
            }
            return true;
        }

        for (ResourceLocation customId : worldConfig.getCustomBoxWeapons()) {
            Item customItem = ForgeRegistries.ITEMS.getValue(customId);
            if (customItem != null && customItem != net.minecraft.world.item.Items.AIR) {
                boolean owned = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() == customItem) { owned = true; break; }
                }
                if (owned) continue;

                if (worldConfig.getCustomWonderWeapons().contains(customId)) {
                    boolean ownedByAnyone = false;
                    for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                        for (ItemStack stack : p.getInventory().items) {
                            if (stack.getItem() == customItem) { ownedByAnyone = true; break; }
                        }
                    }
                    if (ownedByAnyone) continue;
                }
                return true;
            }
        }

        // UNMAPPED TACZ WEAPONS EVALUATION
        for (ResourceLocation unmappedId : WeaponFacade.getUnmappedTaczGuns()) {
            if (worldConfig.isBoxWeaponDisabled(unmappedId)) continue;
            
            boolean owned = false;
            for (ItemStack stack : player.getInventory().items) {
                if (WeaponFacade.isTaczWeapon(stack)) {
                    String stackGunId = stack.getOrCreateTag().getString("GunId");
                    if (stackGunId.equals(unmappedId.toString())) {
                        owned = true;
                        break;
                    }
                }
            }
            if (!owned) return true;
        }

        return false;
    }

    public ItemStack getRandomWeapon(Player player, ServerLevel level) {
        List<ItemStack> candidates = new ArrayList<>();
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<String> requiredTags = worldConfig.getMysteryBoxTags();

        for (WeaponSystem.Definition def : WeaponSystem.Loader.LOADED_DEFINITIONS.values()) {
            ResourceLocation defLoc = new ResourceLocation(def.id != null && def.id.contains(":") ? def.id : "zombierool:" + def.id);
            if (worldConfig.isBoxWeaponDisabled(defLoc)) continue;

            if (requiredTags != null && !requiredTags.isEmpty()) {
                boolean matchesTag = false;
                if (def.tags != null) {
                    for (String tag : requiredTags) {
                        if (def.tags.contains(tag)) {
                            matchesTag = true;
                            break;
                        }
                    }
                }
                if (!matchesTag) continue;
            }

            boolean owned = false;
            for (ItemStack stack : player.getInventory().items) {
                if (WeaponFacade.isWeapon(stack)) {
                    WeaponSystem.Definition d = WeaponFacade.getDefinition(stack);
                    if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                        owned = true;
                        break;
                    }
                }
            }
            if (owned) continue;

            if (isWonderWeapon(def)) {
                boolean ownedByAnyone = false;
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    for (ItemStack stack : p.getInventory().items) {
                        if (WeaponFacade.isWeapon(stack)) {
                            WeaponSystem.Definition d = WeaponFacade.getDefinition(stack);
                            if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                                ownedByAnyone = true;
                                break;
                            }
                        }
                    }
                    if (ownedByAnyone) break;
                }
                if (ownedByAnyone) continue;
            }

            ItemStack wep = WeaponFacade.createWeaponStack(def.id, false);
            if (wep != null && !wep.isEmpty()) candidates.add(wep);
        }

        for (ResourceLocation customId : worldConfig.getCustomBoxWeapons()) {
            Item customItem = ForgeRegistries.ITEMS.getValue(customId);
            if (customItem != null && customItem != net.minecraft.world.item.Items.AIR) {
                boolean owned = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (stack.getItem() == customItem) { owned = true; break; }
                }
                if (owned) continue;

                if (worldConfig.getCustomWonderWeapons().contains(customId)) {
                    boolean ownedByAnyone = false;
                    for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                        for (ItemStack stack : p.getInventory().items) {
                            if (stack.getItem() == customItem) { ownedByAnyone = true; break; }
                        }
                    }
                    if (ownedByAnyone) continue;
                }
                candidates.add(new ItemStack(customItem));
            }
        }

        // UNMAPPED TACZ WEAPONS POOL
        for (ResourceLocation unmappedId : WeaponFacade.getUnmappedTaczGuns()) {
            if (worldConfig.isBoxWeaponDisabled(unmappedId)) continue;
            
            boolean owned = false;
            for (ItemStack stack : player.getInventory().items) {
                if (WeaponFacade.isTaczWeapon(stack)) {
                    String stackGunId = stack.getOrCreateTag().getString("GunId");
                    if (stackGunId.equals(unmappedId.toString())) {
                        owned = true;
                        break;
                    }
                }
            }
            if (owned) continue;
            
            ItemStack wep = WeaponFacade.createUnmappedTaczWeaponStack(unmappedId, false);
            if (wep != null && !wep.isEmpty()) candidates.add(wep);
        }

        if (candidates.isEmpty()) {
            return ItemStack.EMPTY; 
        }

        return candidates.get(random.nextInt(candidates.size()));
    }

    public void startMysteryBoxInteraction(ServerLevel level, ServerPlayer player, boolean useIngot) {
        if (currentActiveMysteryBoxPair == null) return;
        if (isMysteryBoxMoving || isAwaitingWeapon) return;

        int cost = 950;
        if (useIngot) {
            for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof IngotSaleItem) {
                    stack.shrink(1);
                    break;
                }
            }
        } else {
            if (me.cryo.zombierool.PointManager.getScore(player) < cost) return;
            me.cryo.zombierool.PointManager.modifyScore(player, -cost);
        }

        level.playSound(
            null,
            currentActiveMysteryBoxPair.mainPartPos.getX() + 0.5,
            currentActiveMysteryBoxPair.mainPartPos.getY() + 0.5,
            currentActiveMysteryBoxPair.mainPartPos.getZ() + 0.5,
            ZombieroolModSounds.MYSTERY_BOX_JINGLE.get(),
            SoundSource.MASTER,
            1.0F,
            1.0F
        );

        isAwaitingWeapon = true;
        jingleStartTime = level.getGameTime();
        playerWhoCausedActionUUID = player.getUUID();
        
        try {
            weaponToGive = getRandomWeapon(player, level);
            if (weaponToGive.isEmpty()) {
                weaponToGive = WeaponFacade.createWeaponStack("m1911", false);
                if (weaponToGive.isEmpty()) weaponToGive = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);
            }
        } catch (Exception e) {
            e.printStackTrace();
            weaponToGive = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);
        }
        setDirty();
    }

    public void finalizeMysteryBoxInteraction(ServerLevel level, Player player) {
        if (currentActiveMysteryBoxPair == null || player == null) return;

        isAwaitingWeapon = false;
        jingleStartTime = 0;
        currentActiveMysteryBoxPair.usesSinceLastMove++;
        setDirty();

        if (currentActiveMysteryBoxPair.usesSinceLastMove >= currentActiveMysteryBoxPair.moveThreshold) {
            List<MysteryBoxPair> potentialMoveLocations = registeredMysteryBoxLocations.stream()
                .filter(pair -> !pair.equals(currentActiveMysteryBoxPair))
                .collect(Collectors.toList());

            if (potentialMoveLocations.isEmpty()) {
                currentActiveMysteryBoxPair.usesSinceLastMove = 0;
                currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold();
                giveWeapon(player);
            } else {
                startActualMysteryBoxMove(level, player);
            }
        } else {
            giveWeapon(player);
        }

        setDirty();
    }

    private void giveWeapon(Player player) {
        if (player == null) return;
        if (!weaponToGive.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, weaponToGive);
        }
        weaponToGive = ItemStack.EMPTY;
        setDirty();
    }

    private void startActualMysteryBoxMove(ServerLevel level, Player player) {
        isMysteryBoxMoving = true;
        moveStartTime = level.getGameTime();
        setDirty();

        if (currentActiveMysteryBoxPair.mainPartPos != null) {
            level.playSound(null, currentActiveMysteryBoxPair.mainPartPos, ZombieroolModSounds.MYSTERY_BOX_BYBYE.get(), SoundSource.MASTER, 1.0F, 1.0F);
        }

        if (currentActiveMysteryBoxPair != null) {
            level.setBlock(currentActiveMysteryBoxPair.otherPartPos, ZombieroolModBlocks.EMPTYMYSTERYBOX.get().defaultBlockState()
                .setValue(EmptymysteryboxBlock.FACING, currentActiveMysteryBoxPair.facing)
                .setValue(EmptymysteryboxBlock.PART, true), 3);
            level.setBlock(currentActiveMysteryBoxPair.mainPartPos, ZombieroolModBlocks.EMPTYMYSTERYBOX.get().defaultBlockState()
                .setValue(EmptymysteryboxBlock.FACING, currentActiveMysteryBoxPair.facing)
                .setValue(EmptymysteryboxBlock.PART, false), 3);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) return;

            MysteryBoxManager manager = MysteryBoxManager.get(overworld);
            long currentTime = overworld.getGameTime();

            if (manager.isAwaitingWeapon) {
                if (currentTime - manager.jingleStartTime >= JINGLE_DELAY_TICKS) {
                    try {
                        ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(manager.playerWhoCausedActionUUID);
                        manager.finalizeMysteryBoxInteraction(overworld, player);
                    } catch (Exception e) {
                        e.printStackTrace();
                        manager.isAwaitingWeapon = false;
                        manager.weaponToGive = ItemStack.EMPTY;
                        manager.setDirty();
                    }
                }
            }

            if (manager.isMysteryBoxMoving) {
                if (currentTime - manager.moveStartTime >= MOVE_DELAY_TICKS) {
                    manager.moveMysteryBox(overworld);
                }
            }
        }
    }

    public void moveMysteryBox(ServerLevel level) {
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> worldConfigBoxPositions = worldConfig.getMysteryBoxPositions();

        this.registeredMysteryBoxLocations.clear();
        Set<BlockPos> processedMainPositions = new HashSet<>();

        for (BlockPos pos : worldConfigBoxPositions) {
            if (processedMainPositions.contains(pos)) continue;
            BlockState state = level.getBlockState(pos);

            if ((state.getBlock() instanceof MysteryBoxBlock || state.getBlock() instanceof EmptymysteryboxBlock)) {
                boolean isOtherPart = state.getValue(MysteryBoxBlock.PART);
                BlockPos mainPartPos;
                BlockPos otherPartPos;
                Direction facing = state.getValue(MysteryBoxBlock.FACING);

                if (isOtherPart) {
                    otherPartPos = pos;
                    mainPartPos = getOppositeOtherPartPos(pos, facing);
                } else {
                    mainPartPos = pos;
                    otherPartPos = getOtherPartPos(pos, facing);
                }

                MysteryBoxPair newPair = new MysteryBoxPair(mainPartPos.immutable(), otherPartPos.immutable(), facing);
                if (!this.registeredMysteryBoxLocations.contains(newPair)) {
                    this.registeredMysteryBoxLocations.add(newPair);
                    processedMainPositions.add(mainPartPos);
                }
            }
        }

        List<MysteryBoxPair> availableLocations = new ArrayList<>(registeredMysteryBoxLocations);
        if (currentActiveMysteryBoxPair != null) {
            availableLocations.removeIf(pair -> pair.equals(currentActiveMysteryBoxPair));
        }

        MysteryBoxPair chosenNewLocationPair;
        if (availableLocations.isEmpty()) {
            chosenNewLocationPair = currentActiveMysteryBoxPair;
        } else {
            chosenNewLocationPair = availableLocations.get(random.nextInt(availableLocations.size()));
        }

        if (chosenNewLocationPair != null) {
            level.setBlock(chosenNewLocationPair.mainPartPos, ZombieroolModBlocks.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, chosenNewLocationPair.facing)
                .setValue(MysteryBoxBlock.PART, false), 3);
            level.setBlock(chosenNewLocationPair.otherPartPos, ZombieroolModBlocks.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, chosenNewLocationPair.facing)
                .setValue(MysteryBoxBlock.PART, true), 3);

            this.currentActiveMysteryBoxPair = new MysteryBoxPair(
                chosenNewLocationPair.mainPartPos,
                chosenNewLocationPair.otherPartPos,
                chosenNewLocationPair.facing
            );
            this.currentActiveMysteryBoxPair.usesSinceLastMove = 0;
            this.currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold();

            level.getServer().getPlayerList().broadcastSystemMessage(
                getTranslatedComponent(null, "La Mystery Box est réapparue à : ", "The Mystery Box has reappeared at: ")
                .append(Component.literal(currentActiveMysteryBoxPair.mainPartPos.getX() + " " + currentActiveMysteryBoxPair.mainPartPos.getY() + " " + currentActiveMysteryBoxPair.mainPartPos.getZ()))
                .withStyle(ChatFormatting.AQUA), false
            );
        }

        this.isMysteryBoxMoving = false;
        this.playerWhoCausedActionUUID = null;
        this.moveStartTime = 0;
        setDirty();
    }

    public BlockPos getCurrentActiveMysteryBoxLocation() {
        return currentActiveMysteryBoxPair != null ? currentActiveMysteryBoxPair.mainPartPos : null;
    }
}