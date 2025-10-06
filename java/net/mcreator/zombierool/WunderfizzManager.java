package net.mcreator.zombierool;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.ChatFormatting;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player; // Import for Player

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.Random;

import net.minecraftforge.network.PacketDistributor;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.packet.SyncWunderfizzLocationPacket;

public class WunderfizzManager extends SavedData {

    private static final String DATA_NAME = "zombierool_wunderfizz_manager";
    private static final RandomSource RANDOM = RandomSource.create();

    private BlockPos currentActiveWunderfizzLocation = null;

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

    public WunderfizzManager() {
    }

    public static WunderfizzManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            WunderfizzManager::load,
            WunderfizzManager::new,
            DATA_NAME
        );
    }

    public static WunderfizzManager load(CompoundTag nbt) {
        WunderfizzManager manager = new WunderfizzManager();
        if (nbt.contains("CurrentActiveWunderfizzLocation")) {
            manager.currentActiveWunderfizzLocation = NbtUtils.readBlockPos(nbt.getCompound("CurrentActiveWunderfizzLocation"));
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag compound) {
        if (currentActiveWunderfizzLocation != null) {
            compound.put("CurrentActiveWunderfizzLocation", NbtUtils.writeBlockPos(currentActiveWunderfizzLocation));
        }
        return compound;
    }

    /**
     * Initialise la position active de la Wunderfizz.
     * Est appelé au démarrage du jeu ou lors d'un événement pertinent.
     * Sélectionne un DerWunderfizzBlock aléatoire enregistré dans WorldConfig comme actif.
     * @param level Le ServerLevel.
     */
    public void setupInitialWunderfizz(ServerLevel level) {
        WorldConfig worldConfig = WorldConfig.get(level);
        Set<BlockPos> registeredPositions = worldConfig.getDerWunderfizzPositions();

        if (registeredPositions.isEmpty()) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                getTranslatedComponent(null, "§cAttention: Aucun emplacement de Wunderfizz enregistré pour activer !", "§cWarning: No Wunderfizz location registered to activate!").withStyle(ChatFormatting.RED), false
            );
            this.currentActiveWunderfizzLocation = null;
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWunderfizzLocationPacket(null));
            setDirty();
            return;
        }

        List<BlockPos> availableLocations = new ArrayList<>(registeredPositions);
        
        BlockPos chosenPos = availableLocations.get(RANDOM.nextInt(availableLocations.size()));
        
        this.currentActiveWunderfizzLocation = chosenPos.immutable();
        setDirty();

        System.out.println("WunderfizzManager (Server): Setting active Wunderfizz to " + this.currentActiveWunderfizzLocation); // DEBUG PRINT

        level.getServer().getPlayerList().broadcastSystemMessage(
            getTranslatedComponent(null, "La machine Wunderfizz est apparue à : ", "The Wunderfizz machine has appeared at: ").append(Component.literal(currentActiveWunderfizzLocation.getX() + " " + currentActiveWunderfizzLocation.getY() + " " + currentActiveWunderfizzLocation.getZ())).withStyle(ChatFormatting.BLUE), false
        );

        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWunderfizzLocationPacket(currentActiveWunderfizzLocation));
    }

    /**
     * Retourne la position actuelle du DerWunderfizzBlock actif.
     * @return La BlockPos de la Wunderfizz active, ou null si aucune n'est active.
     */
    public BlockPos getCurrentActiveWunderfizzLocation() {
        return currentActiveWunderfizzLocation;
    }

    /**
     * Gère l'achat d'une boisson Wunderfizz par un joueur.
     * @param player Le joueur qui achète.
     * @param cost Le coût de la boisson.
     */
    public void purchaseWunderfizzDrink(ServerPlayer player, int cost) {
        if (currentActiveWunderfizzLocation == null) {
            player.displayClientMessage(getTranslatedComponent(player, "§cLa machine Wunderfizz n'est pas active !", "§cThe Wunderfizz machine is not active!").withStyle(ChatFormatting.RED), true);
            return;
        }

        int currentPerkCount = PerksManager.getPerkCount(player);
        if (currentPerkCount >= PerksManager.MAX_PERKS_LIMIT) {
            player.displayClientMessage(getTranslatedComponent(player, "§cVous possédez déjà " + PerksManager.MAX_PERKS_LIMIT + " atouts !", "§cYou already have " + PerksManager.MAX_PERKS_LIMIT + " perks!").withStyle(ChatFormatting.RED), true);
            return;
        }

        if (PointManager.getScore(player) < cost) {
            player.displayClientMessage(getTranslatedComponent(player, "§cPas assez de points ! (" + cost + " points requis)", "§cNot enough points! (" + cost + " points required)").withStyle(ChatFormatting.RED), true);
            return;
        }

        PointManager.modifyScore(player, -cost);
        player.sendSystemMessage(getTranslatedComponent(player, "§aVous avez dépensé " + cost + " points pour une boisson Wunderfizz !", "§aYou spent " + cost + " points for a Wunderfizz drink!").withStyle(ChatFormatting.GREEN));

        player.level().playSound(
            null,
            currentActiveWunderfizzLocation.getX() + 0.5,
            currentActiveWunderfizzLocation.getY() + 0.5,
            currentActiveWunderfizzLocation.getZ() + 0.5,
            ZombieroolModSounds.BUY.get(),
            SoundSource.BLOCKS,
            1.0F,
            1.0F
        );

        giveRandomPerk(player);
    }

    /**
     * Donne un atout aléatoire au joueur parmi ceux qu'il ne possède pas encore.
     * @param player Le joueur à qui donner l'atout.
     */
    private void giveRandomPerk(ServerPlayer player) {
        List<PerksManager.Perk> availablePerks = new ArrayList<>();
        for (PerksManager.Perk perk : PerksManager.ALL_PERKS.values()) {
            if (perk.getId().equals("quick_revive") && player.level().players().size() > 1) {
                continue;
            }
            if (perk.getId().equals("royal_beer") && PerksManager.getCurrentPerkPurchases("royal_beer", player) >= PerksManager.ROYAL_BEER_LIMIT) {
                continue;
            }
            if (perk.getId().equals("quick_revive") && player.level().players().size() == 1 && PerksManager.getCurrentPerkPurchases("quick_revive", player) >= PerksManager.QUICK_REVIVE_SOLO_LIMIT) {
                continue;
            }

            MobEffectInstance effectInstance = player.getEffect(perk.getAssociatedEffect());
            if (effectInstance == null || effectInstance.getDuration() <= 1) {
                 availablePerks.add(perk);
            }
        }
        
        if (availablePerks.isEmpty()) {
            player.displayClientMessage(getTranslatedComponent(player, "§eVous possédez déjà tous les atouts disponibles !", "§eYou already have all available perks!").withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        PerksManager.Perk chosenPerk = availablePerks.get(RANDOM.nextInt(availablePerks.size()));

        int speedColaAmplifier = 3;
        boolean dejaVuProcced = false;
        if ("speed_cola".equals(chosenPerk.getId())) {
            if (RANDOM.nextInt(20) == 0) {
                speedColaAmplifier = 4;
                dejaVuProcced = true;
                player.level().playSound(
                    null,
                    player.getX(), player.getY(), player.getZ(),
                    ZombieroolModSounds.DEJA_VU.get(),
                    SoundSource.PLAYERS,
                    1.0F,
                    1.0F + (RANDOM.nextFloat() * 0.2F - 0.1F)
                );
                player.displayClientMessage(getTranslatedComponent(player, "§bVous avez une sensation de déjà vu...", "§bYou have a sense of déjà vu..."), true);
            }
        }

        if (chosenPerk.getId().equals("speed_cola")) {
             player.addEffect(new MobEffectInstance(chosenPerk.getAssociatedEffect(), Integer.MAX_VALUE, speedColaAmplifier, false, false, true));
        } else {
             chosenPerk.applyEffect(player);
        }

        if (PerksManager.isPerkLimited(chosenPerk.getId(), player)) {
            PerksManager.incrementPerkPurchases(chosenPerk.getId(), player);
        }
        
        MutableComponent finalMessage = getTranslatedComponent(player, "§aAtout obtenu de la Wunderfizz : ", "§aPerk obtained from Wunderfizz: ")
            .append(chosenPerk.getName());
        if (dejaVuProcced) {
            finalMessage.append(getTranslatedComponent(player, " §e(Vitesse augmentée !)", " §e(Speed increased!)"));
        }
        player.displayClientMessage(finalMessage, true);
    }
}
