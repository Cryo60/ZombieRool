package net.mcreator.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import net.mcreator.zombierool.init.KeyBindings;
// Importe le NetworkHandler existant
import net.mcreator.zombierool.network.NetworkHandler; 
// Importe un nouveau paquet ou modifie l'existant
import net.mcreator.zombierool.network.PurchaseWallWeaponPacket; 
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.mcreator.zombierool.api.IPackAPunchable; // Assurez-vous que cette API est cohérente

import net.minecraft.world.item.ItemStack; // Ajouté pour la vérification de l'inventaire
import net.minecraft.network.chat.Component; // Import for Component
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent


@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WallPurchaseHandler {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final double MAX_DIST = 1.5;
    private static boolean keyWasDown = false;

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient() {
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(String frenchMessage, String englishMessage) {
        if (isEnglishClient()) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        boolean isDown = KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown();
        if (isDown && !keyWasDown) {
            keyWasDown = true;

            HitResult ray = mc.hitResult;
            if (ray instanceof BlockHitResult bhr) {
                BlockPos pos = bhr.getBlockPos();
                if (mc.level.getBlockEntity(pos) instanceof BuyWallWeaponBlockEntity be) { // Utilisez 'be' pour accéder aux données
                    double dx = Math.abs(player.getX() - (pos.getX() + .5));
                    double dz = Math.abs(player.getZ() - (pos.getZ() + .5));
                    if (dx <= MAX_DIST && dz <= MAX_DIST) {
                        // --- Logique pour déterminer le type d'achat à envoyer au serveur ---
                        ResourceLocation rl = be.getItemToSell();
                        if (rl == null) return; // Pas d'item à vendre
                        var itemToSell = ForgeRegistries.ITEMS.getValue(rl);
                        if (itemToSell == null) return; // Item non trouvé

                        boolean playerHasItem = player.getInventory().items.stream()
                            .anyMatch(s -> s.getItem() == itemToSell);
                        
                        boolean playerHasUpgradedItem = false;
                        if (itemToSell instanceof IPackAPunchable pap) {
                             playerHasUpgradedItem = player.getInventory().items.stream()
                                .anyMatch(s -> s.getItem() == itemToSell && pap.isPackAPunched(s));
                        }
                        NetworkHandler.INSTANCE.sendToServer(new PurchaseWallWeaponPacket(pos));
                    }
                }
            }
        }
        if (!isDown) {
            keyWasDown = false;
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) { // Changed to Post for compatibility with some UIs
        LocalPlayer player = mc.player;
        if (player == null || player.isCreative()) return;
        HitResult ray = mc.hitResult;
        if (!(ray instanceof BlockHitResult bhr)) return;
        BlockPos pos = bhr.getBlockPos();
        var te = mc.level.getBlockEntity(pos);
        if (!(te instanceof BuyWallWeaponBlockEntity be)) return;
    
        double dx = Math.abs(player.getX() - (pos.getX() + .5));
        double dz = Math.abs(player.getZ() - (pos.getZ() + .5));
        if (dx > MAX_DIST || dz > MAX_DIST) return;
    
        int basePrice = be.getPrice();
        int displayPrice = basePrice; // Utiliser une variable pour le prix affiché
        ResourceLocation rl = be.getItemToSell();
        if (displayPrice <= 0 || rl == null) return;
        var item = ForgeRegistries.ITEMS.getValue(rl);
        if (item == null) return;
    
        // ---- DÉTECTE SI L'INVENTAIRE CONTIENT DÉJÀ LA VERSION Pack-a-Punch ou l'item de base
        boolean playerHasBaseItem = player.getInventory().items.stream()
            .anyMatch(s -> s.getItem() == item && ! (s.getItem() instanceof IPackAPunchable pap && pap.isPackAPunched(s)));
        
        boolean playerHasUpgradedItem = false;
        if (item instanceof IPackAPunchable pap) {
            playerHasUpgradedItem = player.getInventory().items.stream()
                .anyMatch(s -> s.getItem() == item && pap.isPackAPunched(s));
            
            if (playerHasUpgradedItem) {
                // Si le joueur a la version Pack-a-Punch, le prix d'achat initial est surchargé
                // pour refléter le coût d'une nouvelle arme (base + 5000), mais cela ne doit pas être le prix de recharge
                // Ce calcul "price = 5000 + basePrice / 2" était potentiellement trompeur pour l'affichage initial.
                // Il faut distinguer prix d'achat d'une nouvelle arme VS prix de recharge d'une arme possédée.
                // Ici, c'est pour l'affichage de l'achat initial (si pas d'arme)
                // ou le prix de recharge (si arme déjà possédée).
            }
        }
        
        String msg;
        if (playerHasUpgradedItem) {
            // Le joueur a déjà l'arme ET elle est Pack-a-Punchée -> C'est une recharge d'arme PAP
            // Le coût de recharge d'une arme PAP est (prix de base / 2) + 5000
            int rechargePrice = (basePrice / 2) + 5000;
            msg = getTranslatedComponent(
                "Appuyer sur F pour recharger " + item.getDescription().getString() + " (PAP) pour " + rechargePrice + " pts",
                "Press F to reload " + item.getDescription().getString() + " (PAP) for " + rechargePrice + " pts"
            ).getString();
        } else if (playerHasBaseItem) {
            // Le joueur a déjà l'arme de base -> C'est une recharge d'arme de base
            // Le coût de recharge d'une arme de base est (prix de base / 2)
            int rechargePrice = Math.max(1, basePrice / 2); // Assure un prix minimum de 1
            msg = getTranslatedComponent(
                "Appuyer sur F pour recharger " + item.getDescription().getString() + " pour " + rechargePrice + " pts",
                "Press F to reload " + item.getDescription().getString() + " for " + rechargePrice + " pts"
            ).getString();
        } else {
            // Le joueur n'a pas l'arme du tout -> C'est un achat initial
            msg = getTranslatedComponent(
                "Appuyer sur F pour acheter " + item.getDescription().getString() + " pour " + basePrice + " pts",
                "Press F to purchase " + item.getDescription().getString() + " for " + basePrice + " pts"
            ).getString();
        }
    
        PoseStack ms = event.getGuiGraphics().pose(); // PoseStack est souvent passée directement dans RenderGuiOverlayEvent
        Font f = mc.font;
        int w = event.getWindow().getGuiScaledWidth(); // Utilisez event.getWindow()
        int h = event.getWindow().getGuiScaledHeight(); // Utilisez event.getWindow()
        event.getGuiGraphics().drawString(f, msg, (w - f.width(msg)) / 2, h / 2 + 10, 0xFFFFFF);
    }
}
