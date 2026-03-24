package me.cryo.zombierool;
import me.cryo.zombierool.block.system.MysteryBoxSystem.MysteryBoxBlock;
import me.cryo.zombierool.block.system.MysteryBoxSystem;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.stream.Collectors;

public class MysteryBoxManager extends SavedData {
    private static final String DATA_NAME = "zombierool_mysterybox_manager";
    private static final Random STATIC_RANDOM = new Random();
    public static final Set<Item> WONDER_WEAPONS = new HashSet<>();

    public boolean isLocked = false; 

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
    public MysteryBoxPair currentActiveMysteryBoxPair = null;
    private Random random = new Random();

    public MysteryBoxManager() {}

    public static MysteryBoxManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MysteryBoxManager::load, MysteryBoxManager::new, DATA_NAME);
    }

    public static MysteryBoxManager load(CompoundTag nbt) {
        MysteryBoxManager manager = new MysteryBoxManager();
        manager.isLocked = nbt.getBoolean("IsLocked");
        if (nbt.contains("CurrentActiveMysteryBoxPair")) {
            manager.currentActiveMysteryBoxPair = MysteryBoxPair.load(nbt.getCompound("CurrentActiveMysteryBoxPair"));
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
        compound.putBoolean("IsLocked", isLocked);
        if (currentActiveMysteryBoxPair != null) {
            compound.put("CurrentActiveMysteryBoxPair", currentActiveMysteryBoxPair.save());
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

    public int getRegisteredLocationsCount() {
        return this.registeredMysteryBoxLocations.size();
    }

    public void forceLocation(ServerLevel level, BlockPos mainPos, boolean locked) {
        for (MysteryBoxPair pair : registeredMysteryBoxLocations) {
            if (pair.mainPartPos.equals(mainPos)) {
                if (currentActiveMysteryBoxPair != null && !currentActiveMysteryBoxPair.equals(pair)) {
                    level.setBlock(currentActiveMysteryBoxPair.mainPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                        .setValue(MysteryBoxBlock.FACING, currentActiveMysteryBoxPair.facing)
                        .setValue(MysteryBoxBlock.PART, false)
                        .setValue(MysteryBoxBlock.ACTIVE, false), 3);
                    level.setBlock(currentActiveMysteryBoxPair.otherPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                        .setValue(MysteryBoxBlock.FACING, currentActiveMysteryBoxPair.facing)
                        .setValue(MysteryBoxBlock.PART, true)
                        .setValue(MysteryBoxBlock.ACTIVE, false), 3);
                }
                
                level.setBlock(pair.mainPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                    .setValue(MysteryBoxBlock.FACING, pair.facing)
                    .setValue(MysteryBoxBlock.PART, false)
                    .setValue(MysteryBoxBlock.OPEN, false)
                    .setValue(MysteryBoxBlock.ACTIVE, true), 3);
                level.setBlock(pair.otherPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                    .setValue(MysteryBoxBlock.FACING, pair.facing)
                    .setValue(MysteryBoxBlock.PART, true)
                    .setValue(MysteryBoxBlock.OPEN, false)
                    .setValue(MysteryBoxBlock.ACTIVE, true), 3);
                
                this.currentActiveMysteryBoxPair = pair;
                this.isLocked = locked;
                this.setDirty();
                return;
            }
        }
    }

    public void setupInitialMysteryBox(ServerLevel level, int initialScanRadius) {
        this.currentActiveMysteryBoxPair = null;
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> registeredPositions = worldConfig.getMysteryBoxPositions();
        
        List<MysteryBoxPair> potentialBoxPairs = new ArrayList<>();
        Set<BlockPos> processedMainPositions = new HashSet<>();

        for (BlockPos pos : registeredPositions) {
            if (processedMainPositions.contains(pos)) continue;
            
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MysteryBoxBlock) {
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

                if ((mainState.getBlock() instanceof MysteryBoxBlock) &&
                    (otherState.getBlock() instanceof MysteryBoxBlock) &&
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
            level.setBlock(pair.mainPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, pair.facing)
                .setValue(MysteryBoxBlock.PART, false)
                .setValue(MysteryBoxBlock.ACTIVE, false), 3);
            level.setBlock(pair.otherPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, pair.facing)
                .setValue(MysteryBoxBlock.PART, true)
                .setValue(MysteryBoxBlock.ACTIVE, false), 3);
        }

        List<MysteryBoxPair> availableLocations = new ArrayList<>();
        for(MysteryBoxPair pair : this.registeredMysteryBoxLocations) {
            if(!worldConfig.isMysteryBoxExcluded(pair.mainPartPos)) {
                availableLocations.add(pair);
            }
        }
        if (availableLocations.isEmpty()) availableLocations.addAll(this.registeredMysteryBoxLocations);

        MysteryBoxPair chosenPairForActivation = availableLocations.get(random.nextInt(availableLocations.size()));

        level.setBlock(chosenPairForActivation.mainPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
            .setValue(MysteryBoxBlock.FACING, chosenPairForActivation.facing)
            .setValue(MysteryBoxBlock.PART, false)
            .setValue(MysteryBoxBlock.OPEN, false)
            .setValue(MysteryBoxBlock.ACTIVE, true), 3);
        level.setBlock(chosenPairForActivation.otherPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
            .setValue(MysteryBoxBlock.FACING, chosenPairForActivation.facing)
            .setValue(MysteryBoxBlock.PART, true)
            .setValue(MysteryBoxBlock.OPEN, false)
            .setValue(MysteryBoxBlock.ACTIVE, true), 3);

        this.currentActiveMysteryBoxPair = new MysteryBoxPair(
            chosenPairForActivation.mainPartPos,
            chosenPairForActivation.otherPartPos,
            chosenPairForActivation.facing
        );
        this.currentActiveMysteryBoxPair.usesSinceLastMove = 0;
        this.currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold();
        this.isLocked = false;
        
        setDirty();

        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("message.zombierool.mystery_box.appeared", 
                currentActiveMysteryBoxPair.mainPartPos.getX(), 
                currentActiveMysteryBoxPair.mainPartPos.getY(), 
                currentActiveMysteryBoxPair.mainPartPos.getZ()).withStyle(ChatFormatting.GOLD), false
        );
    }

    private boolean isWonderWeapon(WeaponSystem.Definition def) {
        return def.is_wonder_weapon || "WONDER".equalsIgnoreCase(def.type);
    }

    private boolean isWeaponOwnedBy(WeaponSystem.Definition def, Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (WeaponFacade.isWeapon(stack)) {
                WeaponSystem.Definition d = WeaponFacade.getDefinition(stack);
                if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isWeaponOwnedByAnyone(WeaponSystem.Definition def, ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (isWeaponOwnedBy(def, p)) return true;
        }
        if (this.currentActiveMysteryBoxPair != null) {
            BlockEntity be = level.getBlockEntity(this.currentActiveMysteryBoxPair.mainPartPos);
            if (be instanceof MysteryBoxSystem.MysteryBoxBlockEntity box) {
                ItemStack finalWep = box.getFinalWeapon();
                if (!finalWep.isEmpty() && WeaponFacade.isWeapon(finalWep)) {
                    WeaponSystem.Definition d = WeaponFacade.getDefinition(finalWep);
                    if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isCustomWeaponOwnedBy(Item customItem, Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == customItem) return true;
        }
        return false;
    }

    private boolean isCustomWeaponOwnedByAnyone(Item customItem, ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (isCustomWeaponOwnedBy(customItem, p)) return true;
        }
        if (this.currentActiveMysteryBoxPair != null) {
            BlockEntity be = level.getBlockEntity(this.currentActiveMysteryBoxPair.mainPartPos);
            if (be instanceof MysteryBoxSystem.MysteryBoxBlockEntity box) {
                ItemStack finalWep = box.getFinalWeapon();
                if (!finalWep.isEmpty() && finalWep.getItem() == customItem) return true;
            }
        }
        return false;
    }

    private boolean isUnmappedTaczOwnedBy(ResourceLocation unmappedId, Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (WeaponFacade.isTaczWeapon(stack)) {
                if (stack.getOrCreateTag().getString("GunId").equals(unmappedId.toString())) return true;
            }
        }
        return false;
    }

    private boolean isUnmappedTaczOwnedByAnyone(ResourceLocation unmappedId, ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (isUnmappedTaczOwnedBy(unmappedId, p)) return true;
        }
        if (this.currentActiveMysteryBoxPair != null) {
            BlockEntity be = level.getBlockEntity(this.currentActiveMysteryBoxPair.mainPartPos);
            if (be instanceof MysteryBoxSystem.MysteryBoxBlockEntity box) {
                ItemStack finalWep = box.getFinalWeapon();
                if (!finalWep.isEmpty() && WeaponFacade.isTaczWeapon(finalWep)) {
                    if (finalWep.getOrCreateTag().getString("GunId").equals(unmappedId.toString())) return true;
                }
            }
        }
        return false;
    }

    private boolean playerHasAnyWonderWeapon(Player player, ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (WeaponFacade.isWeapon(stack)) {
                WeaponSystem.Definition d = WeaponFacade.getDefinition(stack);
                if (d != null && isWonderWeapon(d)) {
                    return true;
                }
            }
            for (ResourceLocation customId : config.getCustomWonderWeapons()) {
                Item customItem = ForgeRegistries.ITEMS.getValue(customId);
                if (customItem != null && stack.getItem() == customItem) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean hasAvailableWeapons(Player player, ServerLevel level) {
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<String> requiredTags = worldConfig.getMysteryBoxTags();
        boolean playerHasWW = playerHasAnyWonderWeapon(player, level);
        boolean preferZr = player != null && player.getPersistentData().getBoolean("zr_prefer_zr_weapons");

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
            
            if (isWeaponOwnedBy(def, player)) continue;
            
            if (isWonderWeapon(def)) {
                if (playerHasWW) continue;
                if (isWeaponOwnedByAnyone(def, level)) continue;
            }
            
            return true;
        }

        for (ResourceLocation customId : worldConfig.getCustomBoxWeapons()) {
            Item customItem = ForgeRegistries.ITEMS.getValue(customId);
            if (customItem != null && customItem != net.minecraft.world.item.Items.AIR) {
                if (isCustomWeaponOwnedBy(customItem, player)) continue;
                
                if (worldConfig.getCustomWonderWeapons().contains(customId)) {
                    if (playerHasWW) continue;
                    if (isCustomWeaponOwnedByAnyone(customItem, level)) continue;
                }
                
                return true;
            }
        }

        if (!preferZr) {
            for (ResourceLocation unmappedId : WeaponFacade.getUnmappedTaczGuns()) {
                if (worldConfig.isBoxWeaponDisabled(unmappedId)) continue;
                if (!worldConfig.getEnabledUnmappedWeapons().contains(unmappedId)) continue;
                if (isUnmappedTaczOwnedBy(unmappedId, player)) continue;
                
                return true;
            }
        }

        return false;
    }

    public ItemStack getRandomWeapon(Player player, ServerLevel level) {
        List<ItemStack> candidates = new ArrayList<>();
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<String> requiredTags = worldConfig.getMysteryBoxTags();
        boolean playerHasWW = playerHasAnyWonderWeapon(player, level);
        boolean preferZr = player != null && player.getPersistentData().getBoolean("zr_prefer_zr_weapons");

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

            if (isWeaponOwnedBy(def, player)) continue;

            if (isWonderWeapon(def)) {
                if (playerHasWW) continue;
                if (isWeaponOwnedByAnyone(def, level)) continue;
            }

            ItemStack wep = WeaponFacade.createWeaponStack(def.id, false, player);
            if (wep != null && !wep.isEmpty()) candidates.add(wep);
        }

        for (ResourceLocation customId : worldConfig.getCustomBoxWeapons()) {
            Item customItem = ForgeRegistries.ITEMS.getValue(customId);
            if (customItem != null && customItem != net.minecraft.world.item.Items.AIR) {
                if (isCustomWeaponOwnedBy(customItem, player)) continue;
                if (worldConfig.getCustomWonderWeapons().contains(customId)) {
                    if (playerHasWW) continue;
                    if (isCustomWeaponOwnedByAnyone(customItem, level)) continue;
                }
                candidates.add(new ItemStack(customItem));
            }
        }

        if (!preferZr) {
            for (ResourceLocation unmappedId : WeaponFacade.getUnmappedTaczGuns()) {
                if (worldConfig.isBoxWeaponDisabled(unmappedId)) continue;
                if (!worldConfig.getEnabledUnmappedWeapons().contains(unmappedId)) continue;
                if (isUnmappedTaczOwnedBy(unmappedId, player)) continue;
                
                ItemStack wep = WeaponFacade.createUnmappedTaczWeaponStack(unmappedId, false);
                if (wep != null && !wep.isEmpty()) candidates.add(wep);
            }
        }

        if (candidates.isEmpty()) {
            return ItemStack.EMPTY; 
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    public void moveMysteryBox(ServerLevel level, boolean usedIngot, int cost, UUID buyerId) {
        if (buyerId != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(buyerId);
            if (player != null) {
                if (usedIngot) {
                    if (!player.getInventory().add(new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.INGOT_SALE.get()))) {
                        player.drop(new ItemStack(me.cryo.zombierool.init.ZombieroolModItems.INGOT_SALE.get()), false);
                    }
                } else {
                    me.cryo.zombierool.PointManager.modifyScore(player, cost);
                }
                player.sendSystemMessage(Component.translatable("message.zombierool.mystery_box.refunded").withStyle(ChatFormatting.GREEN));
            }
        }

        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> worldConfigBoxPositions = worldConfig.getMysteryBoxPositions();
        
        this.registeredMysteryBoxLocations.clear();
        Set<BlockPos> processedMainPositions = new HashSet<>();

        for (BlockPos pos : worldConfigBoxPositions) {
            if (processedMainPositions.contains(pos)) continue;
            
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof MysteryBoxBlock) {
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

        List<MysteryBoxPair> availableLocations = new ArrayList<>();
        for(MysteryBoxPair pair : registeredMysteryBoxLocations) {
            if(!worldConfig.isMysteryBoxExcluded(pair.mainPartPos) && (currentActiveMysteryBoxPair == null || !pair.equals(currentActiveMysteryBoxPair))) {
                availableLocations.add(pair);
            }
        }
        
        if (availableLocations.isEmpty()) {
            availableLocations.addAll(registeredMysteryBoxLocations);
            if (currentActiveMysteryBoxPair != null) availableLocations.remove(currentActiveMysteryBoxPair);
        }

        if (currentActiveMysteryBoxPair != null) {
            level.setBlock(currentActiveMysteryBoxPair.mainPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, currentActiveMysteryBoxPair.facing)
                .setValue(MysteryBoxBlock.PART, false)
                .setValue(MysteryBoxBlock.ACTIVE, false), 3);
            level.setBlock(currentActiveMysteryBoxPair.otherPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, currentActiveMysteryBoxPair.facing)
                .setValue(MysteryBoxBlock.PART, true)
                .setValue(MysteryBoxBlock.ACTIVE, false), 3);
        }

        MysteryBoxPair chosenNewLocationPair;
        if (availableLocations.isEmpty()) {
            chosenNewLocationPair = currentActiveMysteryBoxPair;
        } else {
            chosenNewLocationPair = availableLocations.get(random.nextInt(availableLocations.size()));
        }

        if (chosenNewLocationPair != null) {
            level.setBlock(chosenNewLocationPair.mainPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, chosenNewLocationPair.facing)
                .setValue(MysteryBoxBlock.PART, false)
                .setValue(MysteryBoxBlock.OPEN, false)
                .setValue(MysteryBoxBlock.ACTIVE, true), 3);
            level.setBlock(chosenNewLocationPair.otherPartPos, MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState()
                .setValue(MysteryBoxBlock.FACING, chosenNewLocationPair.facing)
                .setValue(MysteryBoxBlock.PART, true)
                .setValue(MysteryBoxBlock.OPEN, false)
                .setValue(MysteryBoxBlock.ACTIVE, true), 3);
            
            this.currentActiveMysteryBoxPair = new MysteryBoxPair(
                chosenNewLocationPair.mainPartPos,
                chosenNewLocationPair.otherPartPos,
                chosenNewLocationPair.facing
            );
            this.currentActiveMysteryBoxPair.usesSinceLastMove = 0;
            this.currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold();
            
            level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.zombierool.mystery_box.reappeared", 
                    currentActiveMysteryBoxPair.mainPartPos.getX(), 
                    currentActiveMysteryBoxPair.mainPartPos.getY(), 
                    currentActiveMysteryBoxPair.mainPartPos.getZ()).withStyle(ChatFormatting.AQUA), false
            );
        }
        
        setDirty();
    }
}
