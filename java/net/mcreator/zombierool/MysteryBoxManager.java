package net.mcreator.zombierool;

import net.mcreator.zombierool.block.MysteryBoxBlock;
import net.mcreator.zombierool.block.EmptymysteryboxBlock;
import net.mcreator.zombierool.init.ZombieroolModBlocks;
import net.mcreator.zombierool.init.ZombieroolModItems;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.mcreator.zombierool.ZombieroolMod;
import net.mcreator.zombierool.init.ZombieroolModMobEffects; // Importez votre classe d'effets de mob

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
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

import net.mcreator.zombierool.item.IngotSaleItem;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MysteryBoxManager extends SavedData {

    private static final String DATA_NAME = "zombierool_mysterybox_manager";
    private static final Random STATIC_RANDOM = new Random();

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

    // --- Enum pour la rareté des armes ---
    public enum Rarity {
        COMMON(500),
        UNCOMMON(300),
        RARE(100),
        EPIC(20),
        WONDER_WEAPON(1);

        private final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }

        public int getWeight() {
            return weight;
        }
    }

    // --- Classe interne pour stocker l'arme et sa rareté ---
    public static class WeightedWeapon {
        public final Item weaponItem;
        public final Rarity rarity;

        public WeightedWeapon(Item weaponItem, Rarity rarity) {
            this.weaponItem = weaponItem;
            this.rarity = rarity;
        }
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

    public static List<WeightedWeapon> MYSTERY_BOX_WEAPONS_WEIGHTED = new ArrayList<>();
    public static Set<Item> WONDER_WEAPONS = new HashSet<>();
    public boolean isMysteryBoxMoving = false;
    private long moveStartTime = 0;
    public static final int MOVE_DELAY_TICKS = 13 * 20; // 13 secondes (20 ticks/seconde)

    public boolean isAwaitingWeapon = false;
    private long jingleStartTime = 0;
    public static final int JINGLE_DELAY_TICKS = 8 * 20; // 8 secondes pour le jingle

    private UUID playerWhoCausedActionUUID = null;
    private ItemStack weaponToGive = ItemStack.EMPTY;

    private Map<UUID, Item> playerWonderWeapons = new HashMap<>();

    public MysteryBoxManager() {
    }

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

        if (nbt.contains("PlayerWonderWeapons")) {
            CompoundTag wonderWeaponsTag = nbt.getCompound("PlayerWonderWeapons");
            for (String key : wonderWeaponsTag.getAllKeys()) {
                try {
                    UUID playerUUID = UUID.fromString(key);
                    Item weapon = BuiltInRegistries.ITEM.get(new ResourceLocation(wonderWeaponsTag.getString(key)));
                    if (weapon != null) {
                        manager.playerWonderWeapons.put(playerUUID, weapon);
                    }
                } catch (IllegalArgumentException e) {
                    System.err.println("Failed to parse UUID or ResourceLocation for player wonder weapon: " + key);
                }
            }
        }

        // Load registeredMysteryBoxLocations
        if (nbt.contains("RegisteredMysteryBoxLocations", ListTag.TAG_LIST)) {
            ListTag listTag = nbt.getList("RegisteredMysteryBoxLocations", ListTag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                manager.registeredMysteryBoxLocations.add(MysteryBoxPair.load(listTag.getCompound(i)));
            }
            System.out.println("[DEBUG] MysteryBoxManager: Loaded " + manager.registeredMysteryBoxLocations.size() + " registered Mystery Box locations from save.");
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

        CompoundTag wonderWeaponsTag = new CompoundTag();
        for (Map.Entry<UUID, Item> entry : playerWonderWeapons.entrySet()) {
            wonderWeaponsTag.putString(entry.getKey().toString(), BuiltInRegistries.ITEM.getKey(entry.getValue()).toString());
        }
        compound.put("PlayerWonderWeapons", wonderWeaponsTag);
        // Save registeredMysteryBoxLocations
        ListTag listTag = new ListTag();
        for (MysteryBoxPair pair : registeredMysteryBoxLocations) {
            listTag.add(pair.save());
        }
        compound.put("RegisteredMysteryBoxLocations", listTag);
        System.out.println("[DEBUG] MysteryBoxManager: Saved " + registeredMysteryBoxLocations.size() + " registered Mystery Box locations.");

        return compound;
    }

    public static BlockPos getOtherPartPos(BlockPos mainPartPos, Direction facing) {
        switch (facing) {
            case NORTH: return mainPartPos.west();
            case SOUTH: return mainPartPos.east();
            case EAST:  return mainPartPos.north();
            case WEST:  return mainPartPos.south();
            default: return mainPartPos.west();
        }
    }

    public static BlockPos getOppositeOtherPartPos(BlockPos otherPartPos, Direction facing) {
        switch (facing) {
            case NORTH: return otherPartPos.east();
            case SOUTH: return otherPartPos.west();
            case EAST:  return otherPartPos.south();
            case WEST:  return otherPartPos.north();
            default: return otherPartPos.east();
        }
    }

    private static int generateRandomMoveThreshold() {
        return STATIC_RANDOM.nextInt(9) + 4;
    }

    /**
     * Initializes the Mystery Box system at the start of the game.
     * It now retrieves Mystery Box locations from WorldConfig instead of scanning the world.
     * It transforms all registered MysteryBoxBlocks into EmptyMysteryBoxBlocks,
     * then activates one of them as the active Mystery Box.
     * @param level The ServerLevel.
     */
    public void setupInitialMysteryBox(ServerLevel level, int initialScanRadius) {
        System.out.println("[DEBUG] MysteryBoxManager: setupInitialMysteryBox started.");
        this.currentActiveMysteryBoxPair = null;
        this.isMysteryBoxMoving = false;
        this.isAwaitingWeapon = false;
        this.playerWhoCausedActionUUID = null;
        this.moveStartTime = 0;
        this.jingleStartTime = 0;
        this.playerWonderWeapons.clear();

        // Retrieve registered Mystery Box locations from WorldConfig
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> registeredPositions = worldConfig.getMysteryBoxPositions();
        System.out.println("[DEBUG] MysteryBoxManager: Raw positions from WorldConfig (before rebuild): " + registeredPositions.size());

        // Rebuild registeredMysteryBoxLocations from WorldConfig
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
                        System.out.println("[DEBUG] Found potential pair during setup: main=" + mainPartPos.toShortString() + ", other=" + otherPartPos.toShortString() + ", facing=" + facing.getName());
                    }
                }
            }
        }
        
        // Clear the existing list and populate it with the fresh data from WorldConfig
        this.registeredMysteryBoxLocations.clear();
        this.registeredMysteryBoxLocations.addAll(potentialBoxPairs);
        System.out.println("[DEBUG] MysteryBoxManager: Final registeredMysteryBoxLocations size after rebuild from WorldConfig: " + this.registeredMysteryBoxLocations.size());


        setDirty();
        if (this.registeredMysteryBoxLocations.isEmpty()) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                getTranslatedComponent(null, "§cAttention: Aucun emplacement de Mystery Box enregistré ou trouvé pour activer !", "§cWarning: No Mystery Box locations registered or found to activate!").withStyle(ChatFormatting.RED), false
            );
            this.currentActiveMysteryBoxPair = null;
            setDirty();
            return;
        }

        // Transform ALL registered MysteryBox locations (both MysteryBoxBlock and EmptyMysteryBoxBlock)
        // into EmptyMysteryBoxBlocks to prepare for a new active box.
        for (MysteryBoxPair pair : this.registeredMysteryBoxLocations) {
            level.setBlock(pair.mainPartPos, ZombieroolModBlocks.EMPTYMYSTERYBOX.get().defaultBlockState()
                .setValue(EmptymysteryboxBlock.FACING, pair.facing)
                .setValue(EmptymysteryboxBlock.PART, false), 3);
            level.setBlock(pair.otherPartPos, ZombieroolModBlocks.EMPTYMYSTERYBOX.get().defaultBlockState()
                .setValue(EmptymysteryboxBlock.FACING, pair.facing)
                .setValue(EmptymysteryboxBlock.PART, true), 3);
        }

        MysteryBoxPair chosenPairForActivation = this.registeredMysteryBoxLocations.get(random.nextInt(this.registeredMysteryBoxLocations.size()));
        // Activate the chosen pair by transforming them back into MysteryBoxBlocks
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
            getTranslatedComponent(null, "La Mystery Box est apparue à : ", "The Mystery Box has appeared at: ").append(Component.literal(currentActiveMysteryBoxPair.mainPartPos.getX() + " " + currentActiveMysteryBoxPair.mainPartPos.getY() + " " + currentActiveMysteryBoxPair.mainPartPos.getZ())).withStyle(ChatFormatting.GOLD), false
        );
    }

    public ItemStack getRandomWeapon(Player player, ServerLevel level) {
        if (MYSTERY_BOX_WEAPONS_WEIGHTED.isEmpty()) {
            System.err.println("MYSTERY_BOX_WEAPONS_WEIGHTED list is empty! This indicates a registry loading issue or incorrect setup.");
            return ItemStack.EMPTY;
        }

        List<WeightedWeapon> potentialWeapons = new ArrayList<>(MYSTERY_BOX_WEAPONS_WEIGHTED);
        Collections.shuffle(potentialWeapons);

        boolean hasVultureEffect = player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_VULTURE.get());

		int totalWeight = 0;
		for (WeightedWeapon ww : potentialWeapons) {
		    int effectiveWeight = 0; // Separated declaration and initialization
		    effectiveWeight = ww.rarity.getWeight(); // Separated declaration and initialization
		
		    if (hasVultureEffect) {
		        if (ww.rarity == Rarity.WONDER_WEAPON) {
		            effectiveWeight = (int) (effectiveWeight * 0.1); // Ex: rend 10x plus probable (poids passe de 1 à 0.1, forcé à 1)
		            effectiveWeight -= 5; // Ajoute un "boost" en réduisant encore plus le poids (plus petit = plus probable)
		            if (effectiveWeight < 1) effectiveWeight = 1; // S'assurer que le poids est au moins 1
		        } else if (ww.rarity == Rarity.EPIC) {
		            effectiveWeight = (int) (effectiveWeight * 0.2); // Ex: rend 5x plus probable (poids passe de 20 à 4)
		            effectiveWeight -= 10; // Réduit le poids de 10
		            if (effectiveWeight < 1) effectiveWeight = 1;
		        } else if (ww.rarity == Rarity.RARE) {
		            effectiveWeight = (int) (effectiveWeight * 1.5); // Rend 1.5x moins probable (poids passe de 100 à 150)
		        } else if (ww.rarity == Rarity.UNCOMMON) {
		            effectiveWeight = (int) (effectiveWeight * 2.0); // Rend 2x moins probable (poids passe de 300 à 600)
		        } else if (ww.rarity == Rarity.COMMON) {
		            effectiveWeight = (int) (effectiveWeight * 3.0); // Rend 3x moins probable (poids passe de 500 à 1500)
		        }
		    }
		    totalWeight += effectiveWeight;
		}

        WorldConfig worldConfig = WorldConfig.get(level);

        for (int attempts = 0; attempts < 50; attempts++) {
            int randomWeight = random.nextInt(totalWeight);
            int currentWeight = 0;

            for (WeightedWeapon weightedWeapon : potentialWeapons) {
                int effectiveWeaponWeight = weightedWeapon.rarity.getWeight();
                if (hasVultureEffect) {
                    if (weightedWeapon.rarity == Rarity.EPIC) {
                        effectiveWeaponWeight = (int) (effectiveWeaponWeight * 0.5);
                        if (effectiveWeaponWeight < 1) effectiveWeaponWeight = 1;
                    } else if (weightedWeapon.rarity == Rarity.WONDER_WEAPON) {
                        effectiveWeaponWeight = (int) (effectiveWeaponWeight * 0.2);
                        if (effectiveWeaponWeight < 1) effectiveWeaponWeight = 1;
                    } else {
                         effectiveWeaponWeight = (int) (effectiveWeaponWeight * 1.2);
                    }
                }

                currentWeight += effectiveWeaponWeight;
                if (randomWeight < currentWeight) {
                    ItemStack chosenWeaponStack = new ItemStack(weightedWeapon.weaponItem);
                    Item chosenWeaponItem = chosenWeaponStack.getItem();

                    if (playerHasWeaponInInventory(player, chosenWeaponItem)) {
                        //player.sendSystemMessage(getTranslatedComponent(player, "§eVous avez déjà " + chosenWeaponStack.getDisplayName().getString() + ". Tentons autre chose...", "§eYou already have " + chosenWeaponStack.getDisplayName().getString() + ". Trying something else...").withStyle(ChatFormatting.YELLOW));
                        continue;
                    }

                    if (WONDER_WEAPONS.contains(chosenWeaponItem)) {
                        if (worldConfig.isWonderWeaponDisabled(BuiltInRegistries.ITEM.getKey(chosenWeaponItem))) {
                            //player.sendSystemMessage(getTranslatedComponent(player, "§eCette Wonder Weapon est désactivée pour cette map. Tentons autre chose...", "§eThis Wonder Weapon is disabled for this map. Trying something else...").withStyle(ChatFormatting.YELLOW));
                            continue;
                        }

                        boolean thisWonderWeaponTakenByAnotherPlayer = false;
                        for (Map.Entry<UUID, Item> entry : playerWonderWeapons.entrySet()) {
                            if (!entry.getKey().equals(player.getUUID()) && entry.getValue().equals(chosenWeaponItem)) {
                                thisWonderWeaponTakenByAnotherPlayer = true;
                                break;
                            }
                        }

                        if (thisWonderWeaponTakenByAnotherPlayer) {
                            //player.sendSystemMessage(getTranslatedComponent(player, "§e" + chosenWeaponStack.getDisplayName().getString() + " est déjà possédée par un autre joueur. Tentons autre chose...", "§e" + chosenWeaponStack.getDisplayName().getString() + " is already owned by another player. Trying something else...").withStyle(ChatFormatting.YELLOW));
                            continue;
                        } else if (playerWonderWeapons.containsKey(player.getUUID()) && !playerWonderWeapons.get(player.getUUID()).equals(chosenWeaponItem)) {
                            //player.sendSystemMessage(getTranslatedComponent(player, "§eVous avez déjà une Wonder Weapon. Tentons autre chose...", "§eYou already have a Wonder Weapon. Trying something else...").withStyle(ChatFormatting.YELLOW));
                            continue;
                        }
                    }

                    return chosenWeaponStack;
                }
            }
        }

        System.err.println("Could not find a suitable weapon after multiple attempts. Returning a default weapon (M1911).");
        return new ItemStack(ZombieroolModItems.M_1911_WEAPON.get());
    }

    private boolean playerHasWeaponInInventory(Player player, Item weaponToCheck) {
        for (ItemStack itemStack : player.getInventory().items) {
            if (itemStack.getItem() == weaponToCheck) {
                return true;
            }
        }
        return false;
    }

    public void startMysteryBoxInteraction(ServerLevel level, ServerPlayer player, boolean useIngot) {
        if (currentActiveMysteryBoxPair == null) {
            System.err.println("Tentative d'interagir avec une Mystery Box, mais aucune n'est active !");
            return;
        }

        if (isMysteryBoxMoving) {
            player.sendSystemMessage(getTranslatedComponent(player, "§cLa Mystery Box est en train de se déplacer... attendez !", "§cThe Mystery Box is moving... wait!").withStyle(ChatFormatting.RED));
            return;
        }

        if (isAwaitingWeapon) {
            player.sendSystemMessage(getTranslatedComponent(player, "§cLa Mystery Box prépare déjà votre arme... un instant !", "§cThe Mystery Box is already preparing your weapon... just a moment!").withStyle(ChatFormatting.YELLOW));
            return;
        }

        int cost = 950;
        if (useIngot) {
            boolean ingotConsumed = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); ++i) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof IngotSaleItem) {
                    stack.shrink(1);
                    player.sendSystemMessage(getTranslatedComponent(player, "§aVous avez utilisé un Lingot de Vente pour la Mystery Box !", "§aYou used a Sale Ingot for the Mystery Box!").withStyle(ChatFormatting.GREEN));
                    ingotConsumed = true;
                    break;
                }
            }
            if (!ingotConsumed) {
                player.sendSystemMessage(getTranslatedComponent(player, "§cErreur: Lingot de Vente non trouvé. Opération annulée.", "§cError: Sale Ingot not found. Operation canceled.").withStyle(ChatFormatting.RED));
                return;
            }
        } else {
            if (PointManager.getScore(player) < cost) {
                player.sendSystemMessage(getTranslatedComponent(player, "§cVous n'avez pas assez de points pour utiliser la Mystery Box !", "§cYou don't have enough points to use the Mystery Box!").withStyle(ChatFormatting.RED));
                return;
            }
            PointManager.modifyScore(player, -cost);
            player.sendSystemMessage(getTranslatedComponent(player, "§aVous avez dépensé " + cost + " points pour la Mystery Box !", "§aYou spent " + cost + " points for the Mystery Box!").withStyle(ChatFormatting.GREEN));
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
        player.sendSystemMessage(getTranslatedComponent(player, "§6La Mystery Box joue son jingle...", "§6The Mystery Box is playing its jingle...").withStyle(ChatFormatting.GOLD));

        isAwaitingWeapon = true;
        jingleStartTime = level.getGameTime();
        playerWhoCausedActionUUID = player.getUUID();
        weaponToGive = getRandomWeapon(player, level);
        setDirty();
    }

    private void finalizeMysteryBoxInteraction(ServerLevel level, Player player) {
        if (currentActiveMysteryBoxPair == null || player == null) return;
        isAwaitingWeapon = false;
        jingleStartTime = 0;

        if (WONDER_WEAPONS.contains(weaponToGive.getItem())) {
            playerWonderWeapons.put(player.getUUID(), weaponToGive.getItem());
            this.setDirty();
        }

        currentActiveMysteryBoxPair.usesSinceLastMove++;
        setDirty();
        System.out.println("[SERVER] Mystery Box uses: " + currentActiveMysteryBoxPair.usesSinceLastMove + " / " + currentActiveMysteryBoxPair.moveThreshold + " at " + currentActiveMysteryBoxPair.mainPartPos.toShortString());

        if (currentActiveMysteryBoxPair.usesSinceLastMove >= currentActiveMysteryBoxPair.moveThreshold) {
            // Check if a move is truly possible (i.e., there's another location to move to)
            List<MysteryBoxPair> potentialMoveLocations = registeredMysteryBoxLocations.stream()
                .filter(pair -> !pair.equals(currentActiveMysteryBoxPair))
                .collect(Collectors.toList());

            if (potentialMoveLocations.isEmpty()) {
                // No other locations available for a physical move.
                // As per user's request: prevent all feedback, give weapon normally.
                // This means no "box stuck" messages, and no refund for this specific roll.
                System.out.println("[DEBUG] MysteryBoxManager: Move threshold met, but no other locations available. Remaining in place.");
                currentActiveMysteryBoxPair.usesSinceLastMove = 0; // Reset for next cycle
                currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold(); // Generate new threshold
                giveWeapon(player); // Give the weapon for the current interaction
                // No further action (no setting isMysteryBoxMoving, no specific messages)
            } else {
                // A physical move is possible, proceed with initiating the move sequence.
                // Refund the points now as the box is about to physically move.
                int pointsCost = 950; // Hardcoded, ensure this is consistent with startMysteryBoxInteraction
                if (player instanceof ServerPlayer) {
                    PointManager.modifyScore((ServerPlayer) player, pointsCost);
                    ((ServerPlayer) player).sendSystemMessage(getTranslatedComponent(player, "§a" + pointsCost + " points vous ont été rendus car la Mystery Box se déplace !", "§a" + pointsCost + " points have been refunded because the Mystery Box is moving!").withStyle(ChatFormatting.GREEN));
                }
                
                startActualMysteryBoxMove(level, player); // This will set isMysteryBoxMoving and handle sounds/block changes
            }
        } else {
            giveWeapon(player); // Normal weapon give if threshold not met
        }
        setDirty(); // Ensure state is saved after all modifications
    }

    private void giveWeapon(Player player) {
        if (player == null) return;
        if (!weaponToGive.isEmpty()) {
            player.sendSystemMessage(getTranslatedComponent(player, "§aVous avez tiré : " + weaponToGive.getDisplayName().getString(), "§aYou drew: " + weaponToGive.getDisplayName().getString()).withStyle(ChatFormatting.GREEN));
            ItemHandlerHelper.giveItemToPlayer(player, weaponToGive);
        } else {
            player.sendSystemMessage(getTranslatedComponent(player, "§cLa Mystery Box est vide ou n'a pas pu vous donner d'arme !", "§cThe Mystery Box is empty or could not give you a weapon!").withStyle(ChatFormatting.RED));
        }
        weaponToGive = ItemStack.EMPTY;
        setDirty();
    }

    // Renamed and refactored from initiateMysteryBoxMove
    private void startActualMysteryBoxMove(ServerLevel level, Player player) {
        // This method is only called if a move is confirmed to be possible.
        isMysteryBoxMoving = true;
        moveStartTime = level.getGameTime();
        setDirty();

        // Play sounds and broadcast message for actual move
        if (currentActiveMysteryBoxPair.mainPartPos != null) {
            if (STATIC_RANDOM.nextInt(10) == 0) {
                int vineBoomCount = STATIC_RANDOM.nextInt(4) + 3;
                for (int i = 0; i < vineBoomCount; i++) {
                    level.playSound(
                        null,
                        currentActiveMysteryBoxPair.mainPartPos.getX() + 0.5,
                        currentActiveMysteryBoxPair.mainPartPos.getY() + 0.5,
                        currentActiveMysteryBoxPair.mainPartPos.getZ() + 0.5,
                        ZombieroolModSounds.VINE_BOOM.get(),
                        SoundSource.MASTER,
                        1.0F,
                        1.0F
                    );
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                level.getServer().getPlayerList().broadcastSystemMessage(
                    getTranslatedComponent(null, "§4La Mystery Box s'est déplacée d'une manière... inattendue !", "§4The Mystery Box moved in an... unexpected way!").withStyle(ChatFormatting.DARK_RED), false
                );
            } else {
                level.playSound(
                    null,
                    currentActiveMysteryBoxPair.mainPartPos.getX() + 0.5,
                    currentActiveMysteryBoxPair.mainPartPos.getY() + 0.5,
                    currentActiveMysteryBoxPair.mainPartPos.getZ() + 0.5,
                    ZombieroolModSounds.MYSTERY_BOX_BYBYE.get(),
                    SoundSource.MASTER,
                    1.0F,
                    1.0F
                );
                level.getServer().getPlayerList().broadcastSystemMessage(
                    getTranslatedComponent(null, "§4La Mystery Box s'est déplacée !", "§4The Mystery Box has moved!").withStyle(ChatFormatting.DARK_RED), false
                );
            }
        }

        // Transform the old box into empty blocks (this is the physical removal part)
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
                    ServerPlayer player = overworld.getServer().getPlayerList().getPlayer(manager.playerWhoCausedActionUUID);
                    manager.finalizeMysteryBoxInteraction(overworld, player);
                }
            }

            if (manager.isMysteryBoxMoving) {
                if (currentTime - manager.moveStartTime >= MOVE_DELAY_TICKS) {
                    manager.moveMysteryBox(overworld);
                }
            }
        }
    }

    /**
     * Handles the movement of the Mystery Box to a new registered location.
     * If no other locations are available, it re-activates the current box at its location.
     * @param level The ServerLevel.
     */
    public void moveMysteryBox(ServerLevel level) {
        System.out.println("[DEBUG] MysteryBoxManager: moveMysteryBox started.");
        // Rebuild registeredMysteryBoxLocations from WorldConfig to ensure it's up-to-date
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> worldConfigBoxPositions = worldConfig.getMysteryBoxPositions();
        System.out.println("[DEBUG] MysteryBoxManager: Raw positions from WorldConfig: " + worldConfigBoxPositions.size());
        
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

                BlockState mainState = level.getBlockState(mainPartPos);
                BlockState otherState = level.getBlockState(otherPartPos);

                if ((mainState.getBlock() instanceof MysteryBoxBlock || mainState.getBlock() instanceof EmptymysteryboxBlock) &&
                    (otherState.getBlock() instanceof MysteryBoxBlock || otherState.getBlock() instanceof EmptymysteryboxBlock) &&
                    mainState.getValue(MysteryBoxBlock.FACING) == facing && !mainState.getValue(MysteryBoxBlock.PART) &&
                    otherState.getValue(MysteryBoxBlock.FACING) == facing && otherState.getValue(MysteryBoxBlock.PART)) {
                    MysteryBoxPair newPair = new MysteryBoxPair(mainPartPos.immutable(), otherPartPos.immutable(), facing);
                    if (!this.registeredMysteryBoxLocations.contains(newPair)) {
                        this.registeredMysteryBoxLocations.add(newPair);
                        processedMainPositions.add(mainPartPos);
                        System.out.println("[DEBUG] Rebuilt pair added to registeredMysteryBoxLocations: main=" + mainPartPos.toShortString() + ", other=" + otherPartPos.toShortString());
                    }
                }
            }
        }
        System.out.println("[DEBUG] MysteryBoxManager: registeredMysteryBoxLocations size after rebuild: " + this.registeredMysteryBoxLocations.size());
        System.out.println("[DEBUG] MysteryBoxManager: currentActiveMysteryBoxPair: " + (currentActiveMysteryBoxPair != null ? currentActiveMysteryBoxPair.mainPartPos.toShortString() : "null"));


        MysteryBoxPair chosenNewLocationPair;
        MutableComponent messageComponent; // Use MutableComponent for message
        ChatFormatting messageColor;
        List<MysteryBoxPair> availableLocations = new ArrayList<>(registeredMysteryBoxLocations);
        System.out.println("[DEBUG] MysteryBoxManager: availableLocations size BEFORE removeIf: " + availableLocations.size());
        if (currentActiveMysteryBoxPair != null) {
            availableLocations.removeIf(pair -> pair.equals(currentActiveMysteryBoxPair));
        }
        System.out.println("[DEBUG] MysteryBoxManager: availableLocations size AFTER removeIf: " + availableLocations.size());

        if (availableLocations.isEmpty()) {
            // Case: No other locations are available (only current one, or no other valid pairs).
            // The box "stays" at its current location.
            messageComponent = getTranslatedComponent(null, "La Mystery Box n'a nulle part où aller ! Elle reste à sa place.", "The Mystery Box has nowhere to go! It remains in place.");
            messageColor = ChatFormatting.RED;
            chosenNewLocationPair = currentActiveMysteryBoxPair; // Re-select the current location
        } else {
            // Case: Other locations are available, choose one randomly.
            messageComponent = getTranslatedComponent(null, "La Mystery Box est réapparue à : ", "The Mystery Box has reappeared at: ");
            messageColor = ChatFormatting.AQUA;
            chosenNewLocationPair = availableLocations.get(random.nextInt(availableLocations.size()));
        }

        // --- Activate the chosen box (new or current) ---
        if (chosenNewLocationPair != null) {
            // Transform the chosen location (new or current) into an active MysteryBoxBlock
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
            this.currentActiveMysteryBoxPair.moveThreshold = generateRandomMoveThreshold(); // Ensure the new box gets a fresh threshold

            level.getServer().getPlayerList().broadcastSystemMessage(
                messageComponent.copy().append(Component.literal(currentActiveMysteryBoxPair.mainPartPos.getX() + " " + currentActiveMysteryBoxPair.mainPartPos.getY() + " " + currentActiveMysteryBoxPair.mainPartPos.getZ())).withStyle(messageColor), false
            );
        } else {
            // This should only happen if `registeredMysteryBoxLocations` was somehow empty,
            // which should be caught by `setupInitialMysteryBox`.
            level.getServer().getPlayerList().broadcastSystemMessage(
                getTranslatedComponent(null, "§cErreur interne: Impossible de trouver un emplacement pour la Mystery Box.", "§cInternal error: Unable to find a location for the Mystery Box.").withStyle(ChatFormatting.RED), false
            );
        }
        
        // Reset moving state and mark as dirty
        this.isMysteryBoxMoving = false;
        this.playerWhoCausedActionUUID = null;
        this.moveStartTime = 0;
        setDirty();
    }

    public BlockPos getCurrentActiveMysteryBoxLocation() {
        return currentActiveMysteryBoxPair != null ? currentActiveMysteryBoxPair.mainPartPos : null;
    }

    public Item getPlayerWonderWeapon(UUID playerUUID) {
        return playerWonderWeapons.get(playerUUID);
    }

    public void removePlayerWonderWeapon(UUID playerUUID) {
        if (playerWonderWeapons.remove(playerUUID) != null) {
            setDirty();
        }
    }
}
