package net.mcreator.zombierool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.mcreator.zombierool.init.ZombieroolModSounds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ResourcePackLoader {

    private static boolean isEnglishClient() {
        if (Minecraft.getInstance() == null) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    public static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            try {
                IModFile modFile = ModList.get().getModFileById("zombierool").getFile();
                Path resourcePath = modFile.findResource("resourcepacks/ZombieRool");
                
                event.addRepositorySource((packConsumer) -> {
                    String description = getTranslatedMessage(
                        "Resource pack officiel de ZombieRool",
                        "Official ZombieRool Resource Pack"
                    );
                    
                    Pack pack = Pack.readMetaAndCreate(
                        "builtin/zombierool_pack",
                        Component.literal(description),
                        true,
                        (path) -> new PathPackResources(path, resourcePath, true),
                        PackType.CLIENT_RESOURCES,
                        Pack.Position.TOP,
                        PackSource.BUILT_IN
                    );
                    
                    if (pack != null) {
                        packConsumer.accept(pack);
                    }
                });
            } catch (Exception e) {
                System.err.println("[ZombieRool] Error loading resource pack: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}

/**
 * Gère l'application automatique des resource packs pour les maps
 */
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
class MapResourcePackManager {
    
    private static String currentWorldName = null;
    private static List<String> appliedResourcePacks = new ArrayList<>();
    public static boolean waitingForConfirmation = false;
    private static String pendingWorldName = null;
    private static List<String> pendingResourcePacks = new ArrayList<>();
    private static Set<String> declinedWorlds = new HashSet<>();
    private static Set<String> permanentlyDeclinedWorlds = new HashSet<>();
    
    private static int ticksSinceWorldLoad = 0;
    private static boolean worldJustLoaded = false;
    private static final int TICKS_BEFORE_PROMPT = 40; // 2 secondes
    
    // ✅ NOUVEAU: Système de retrait retardé
    private static boolean pendingRemoval = false;
    private static int ticksSinceWorldUnload = 0;
    private static final int TICKS_BEFORE_REMOVAL = 20; // 1 seconde après déchargement
    
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/zombierool_declined_packs.json");
    private static boolean configLoaded = false;

    /**
     * Charge les refus permanents depuis le fichier de config
     */
    private static void loadConfig() {
        if (configLoaded) return;
        
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    Gson gson = new Gson();
                    Set<String> loaded = gson.fromJson(reader, new TypeToken<Set<String>>(){}.getType());
                    if (loaded != null) {
                        permanentlyDeclinedWorlds.addAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error loading declined packs config: " + e.getMessage());
        }
        
        configLoaded = true;
    }
    
    /**
     * Sauvegarde les refus permanents dans le fichier de config
     */
    private static void saveConfig() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(permanentlyDeclinedWorlds, writer);
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error saving declined packs config: " + e.getMessage());
        }
    }

    /**
     * Utilise le tick client au lieu de WorldLoad pour éviter les race conditions
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        loadConfig();
        
        Minecraft mc = Minecraft.getInstance();
        
        // ✅ Gérer le retrait retardé des packs
        if (pendingRemoval) {
            ticksSinceWorldUnload++;
            if (ticksSinceWorldUnload >= TICKS_BEFORE_REMOVAL) {
                System.out.println("[ZombieRool] Removing resource packs after world unload...");
                removeResourcePacksSilently(appliedResourcePacks);
                appliedResourcePacks.clear();
                pendingRemoval = false;
                ticksSinceWorldUnload = 0;
            }
            return; // Ne pas continuer pendant le retrait
        }
        
        // Vérifier si on est dans un monde solo
        if (mc.level != null && mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            
            // Nouveau monde détecté
            if (!worldName.equals(currentWorldName)) {
                System.out.println("[ZombieRool] World detected: " + worldName);
                currentWorldName = worldName;
                worldJustLoaded = true;
                ticksSinceWorldLoad = 0;
                
                // ✅ Annuler tout retrait en cours
                if (pendingRemoval) {
                    System.out.println("[ZombieRool] Cancelling pending removal (new world loaded)");
                    pendingRemoval = false;
                    ticksSinceWorldUnload = 0;
                }
                
                // Nettoyer les anciens packs appliqués (immédiatement si nouveau monde)
                if (!appliedResourcePacks.isEmpty()) {
                    System.out.println("[ZombieRool] Cleaning old packs for new world");
                    removeResourcePacksImmediately(appliedResourcePacks);
                    appliedResourcePacks.clear();
                }
            }
            
            // Attendre que le monde soit complètement chargé
            if (worldJustLoaded) {
                ticksSinceWorldLoad++;
                
                if (ticksSinceWorldLoad >= TICKS_BEFORE_PROMPT && !waitingForConfirmation) {
                    worldJustLoaded = false;
                    
                    System.out.println("[ZombieRool] Checking for resource packs for world: " + worldName);
                    
                    if (declinedWorlds.contains(worldName)) {
                        System.out.println("[ZombieRool] World temporarily declined");
                        return;
                    }
                    if (permanentlyDeclinedWorlds.contains(worldName)) {
                        System.out.println("[ZombieRool] World permanently declined");
                        return;
                    }
                    
                    List<String> mapResourcePacks = findResourcePacksForWorld(worldName);
                    
                    System.out.println("[ZombieRool] Found " + mapResourcePacks.size() + " resource packs");
                    for (String pack : mapResourcePacks) {
                        System.out.println("[ZombieRool]   - " + pack);
                    }
                    
                    if (!mapResourcePacks.isEmpty()) {
                        pendingWorldName = worldName;
                        pendingResourcePacks = mapResourcePacks;
                        waitingForConfirmation = true;
                        
                        System.out.println("[ZombieRool] Opening confirmation screen");
                        
                        mc.execute(() -> {
                            mc.setScreen(new ResourcePackConfirmationScreen(mapResourcePacks, worldName));
                        });
                    } else {
                        System.out.println("[ZombieRool] No matching resource packs found");
                    }
                }
            }
        } else if (mc.level == null && currentWorldName != null) {
            // ✅ Monde déchargé - retrait RETARDÉ
            System.out.println("[ZombieRool] World unloaded: " + currentWorldName);
            if (!appliedResourcePacks.isEmpty()) {
                System.out.println("[ZombieRool] Scheduling resource pack removal...");
                pendingRemoval = true;
                ticksSinceWorldUnload = 0;
            }
            currentWorldName = null;
            worldJustLoaded = false;
            declinedWorlds.clear();
        }
    }

    /**
     * Trouve les resource packs associés à un monde (logique améliorée)
     */
    private static List<String> findResourcePacksForWorld(String worldName) {
        List<String> foundPacks = new ArrayList<>();
        
        try {
            Minecraft mc = Minecraft.getInstance();
            File resourcePacksDir = new File(mc.gameDirectory, "resourcepacks");
            
            System.out.println("[ZombieRool] Scanning resourcepacks directory: " + resourcePacksDir.getAbsolutePath());
            
            if (!resourcePacksDir.exists()) {
                System.out.println("[ZombieRool] Resourcepacks directory does not exist!");
                return foundPacks;
            }
            
            String worldLower = worldName.toLowerCase().trim();
            System.out.println("[ZombieRool] Looking for packs matching world: '" + worldLower + "'");
            
            File[] rpFiles = resourcePacksDir.listFiles();
            if (rpFiles != null) {
                System.out.println("[ZombieRool] Found " + rpFiles.length + " files/folders in resourcepacks");
                
                for (File rpFile : rpFiles) {
                    if (!(rpFile.isDirectory() || rpFile.getName().endsWith(".zip"))) {
                        System.out.println("[ZombieRool]   Skipping (not dir/zip): " + rpFile.getName());
                        continue;
                    }
                    
                    String rpName = rpFile.getName().replace(".zip", "").toLowerCase().trim();
                    System.out.println("[ZombieRool]   Checking: " + rpFile.getName() + " (normalized: '" + rpName + "')");
                    
                    boolean exactMatch = rpName.equals(worldLower);
                    boolean worldInRP = rpName.contains(worldLower);
                    boolean rpSuffixMatch = rpName.equals(worldLower + "_rp") || rpName.equals(worldLower + " rp");
                    String rpWithoutSuffix = rpName.replaceAll("_rp$|\\s+rp$", "");
                    boolean matchWithoutSuffix = rpWithoutSuffix.equals(worldLower);
                    
                    System.out.println("[ZombieRool]     - exactMatch: " + exactMatch);
                    System.out.println("[ZombieRool]     - rpSuffixMatch: " + rpSuffixMatch);
                    System.out.println("[ZombieRool]     - matchWithoutSuffix: " + matchWithoutSuffix + " (rpWithoutSuffix: '" + rpWithoutSuffix + "')");
                    System.out.println("[ZombieRool]     - worldInRP: " + worldInRP + " (worldLength: " + worldLower.length() + ")");
                    
                    if (exactMatch || rpSuffixMatch || matchWithoutSuffix || (worldInRP && worldLower.length() > 5)) {
                        System.out.println("[ZombieRool]     ✓ MATCH! Adding: " + rpFile.getName());
                        foundPacks.add(rpFile.getName());
                    } else {
                        System.out.println("[ZombieRool]     ✗ No match");
                    }
                }
            } else {
                System.out.println("[ZombieRool] resourcepacks listFiles() returned null");
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error finding resource packs: " + e.getMessage());
            e.printStackTrace();
        }
        
        return foundPacks;
    }

    /**
     * Applique les resource packs de manière sécurisée
     */
    public static void applyResourcePacks(List<String> packNames) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PackRepository packRepository = mc.getResourcePackRepository();
            
            System.out.println("[ZombieRool] Applying resource packs: " + packNames);
            
            packRepository.reload();
            
            Collection<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
            System.out.println("[ZombieRool] Currently selected packs: " + selectedPacks);
            
            Collection<String> availablePacks = packRepository.getAvailableIds();
            System.out.println("[ZombieRool] Available packs: " + availablePacks);
            
            for (String packName : packNames) {
                String packId = "file/" + packName;
                System.out.println("[ZombieRool] Trying to add pack: " + packId);
                
                if (!selectedPacks.contains(packId)) {
                    if (availablePacks.contains(packId)) {
                        selectedPacks.add(packId);
                        appliedResourcePacks.add(packId);
                        System.out.println("[ZombieRool] Pack added successfully: " + packId);
                    } else {
                        System.err.println("[ZombieRool] Pack not found in available packs: " + packId);
                    }
                } else {
                    System.out.println("[ZombieRool] Pack already selected: " + packId);
                }
            }
            
            System.out.println("[ZombieRool] Final selected packs: " + selectedPacks);
            packRepository.setSelected(selectedPacks);
            
            mc.execute(() -> {
                try {
                    System.out.println("[ZombieRool] Reloading resource packs...");
                    mc.reloadResourcePacks();
                    System.out.println("[ZombieRool] Resource packs reloaded successfully");
                } catch (Exception e) {
                    System.err.println("[ZombieRool] Error reloading resource packs: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error applying resource packs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ NOUVEAU: Retire les packs immédiatement (lors du changement de monde)
     */
    private static void removeResourcePacksImmediately(List<String> packIds) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PackRepository packRepository = mc.getResourcePackRepository();
            
            Collection<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
            boolean changed = selectedPacks.removeAll(packIds);
            
            if (changed) {
                System.out.println("[ZombieRool] Removing packs immediately (world change): " + packIds);
                packRepository.setSelected(selectedPacks);
                // Pas de reload ici pour éviter les conflits
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error removing resource packs immediately: " + e.getMessage());
        }
    }

    /**
     * ✅ CORRIGÉ: Retire les packs après un délai (avec reload)
     */
    private static void removeResourcePacksSilently(List<String> packIds) {
        try {
            Minecraft mc = Minecraft.getInstance();
            
            // Vérifier qu'on est dans un état stable (menu principal)
            if (mc.level != null) {
                System.out.println("[ZombieRool] Cannot remove packs while in world, delaying...");
                return;
            }
            
            PackRepository packRepository = mc.getResourcePackRepository();
            Collection<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
            boolean changed = selectedPacks.removeAll(packIds);
            
            if (changed) {
                System.out.println("[ZombieRool] Removing resource packs: " + packIds);
                packRepository.setSelected(selectedPacks);
                
                // Reload uniquement si on est au menu principal
                mc.execute(() -> {
                    try {
                        System.out.println("[ZombieRool] Reloading resources after pack removal...");
                        mc.reloadResourcePacks();
                        System.out.println("[ZombieRool] Packs removed successfully");
                    } catch (Exception e) {
                        System.err.println("[ZombieRool] Error reloading after pack removal: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error removing resource packs: " + e.getMessage());
        }
    }

    /**
     * Confirme l'application des resource packs
     */
    public static void confirmResourcePacks() {
        if (!pendingResourcePacks.isEmpty() && pendingWorldName != null) {
            applyResourcePacks(pendingResourcePacks);
            declinedWorlds.remove(pendingWorldName);
            permanentlyDeclinedWorlds.remove(pendingWorldName);
        }
        waitingForConfirmation = false;
        pendingResourcePacks.clear();
        pendingWorldName = null;
    }

    /**
     * Refuse temporairement l'application des resource packs (pour cette session)
     */
    public static void declineResourcePacks() {
        if (pendingWorldName != null) {
            declinedWorlds.add(pendingWorldName);
        }
        waitingForConfirmation = false;
        pendingResourcePacks.clear();
        pendingWorldName = null;
    }
    
    /**
     * Refuse définitivement l'application des resource packs (sauvegardé)
     */
    public static void declineForever(String worldName) {
        permanentlyDeclinedWorlds.add(worldName);
        saveConfig();
        waitingForConfirmation = false;
        pendingResourcePacks.clear();
        pendingWorldName = null;
    }
    
    /**
     * Réinitialise les refus permanents (utile pour les tests ou paramètres)
     */
    public static void clearDeclinedWorlds() {
        declinedWorlds.clear();
        permanentlyDeclinedWorlds.clear();
        saveConfig();
    }
}

/**
 * Écran de confirmation pour l'application des resource packs
 */
@OnlyIn(Dist.CLIENT)
class ResourcePackConfirmationScreen extends Screen {
    private final List<String> resourcePacks;
    private final String worldName;
    private Button yesButton;
    private Button noButton;
    private Button neverButton;

    protected ResourcePackConfirmationScreen(List<String> resourcePacks, String worldName) {
        super(Component.literal(ResourcePackLoader.getTranslatedMessage(
            "Resource Pack Disponible",
            "Resource Pack Available"
        )));
        this.resourcePacks = resourcePacks;
        this.worldName = worldName;
    }

    @Override
    protected void init() {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 5;
        
        int totalWidth = (buttonWidth * 3) + (spacing * 2);
        int startX = (this.width - totalWidth) / 2;
        int buttonY = this.height / 2 + 40;

        this.yesButton = Button.builder(Component.literal(ResourcePackLoader.getTranslatedMessage("Oui", "Yes")), btn -> {
            playSound();
            MapResourcePackManager.confirmResourcePacks();
            this.onClose();
        }).bounds(startX, buttonY, buttonWidth, buttonHeight).build();

        this.noButton = Button.builder(Component.literal(ResourcePackLoader.getTranslatedMessage(
            "Pas maintenant", "Not now"
        )), btn -> {
            playSound();
            MapResourcePackManager.declineResourcePacks();
            this.onClose();
        }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build();
        
        this.neverButton = Button.builder(Component.literal(ResourcePackLoader.getTranslatedMessage("Jamais", "Never")), btn -> {
            playSound();
            MapResourcePackManager.declineForever(worldName);
            this.onClose();
        }).bounds(startX + (buttonWidth + spacing) * 2, buttonY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(yesButton);
        this.addRenderableWidget(noButton);
        this.addRenderableWidget(neverButton);
    }

    private void playSound() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)
            );
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        
        String message1 = ResourcePackLoader.getTranslatedMessage(
            "Cette carte contient un resource pack personnalisé.",
            "This map includes a custom resource pack."
        );
        String message2 = ResourcePackLoader.getTranslatedMessage(
            "Voulez-vous l'appliquer ?",
            "Would you like to apply it?"
        );
        String packCount = resourcePacks.size() + " pack" + (resourcePacks.size() > 1 ? "s" : "");
        String message3 = ResourcePackLoader.getTranslatedMessage(
            "(" + packCount + " trouvé" + (resourcePacks.size() > 1 ? "s" : "") + ")",
            "(" + packCount + " found)"
        );
        String message4 = ResourcePackLoader.getTranslatedMessage(
            "Le jeu va recharger les ressources.",
            "The game will reload resources."
        );
        
        graphics.drawCenteredString(this.font, message1, this.width / 2, this.height / 2 - 25, 0xAAAAAA);
        graphics.drawCenteredString(this.font, message2, this.width / 2, this.height / 2 - 10, 0xAAAAAA);
        graphics.drawCenteredString(this.font, message3, this.width / 2, this.height / 2 + 5, 0x888888);
        graphics.drawCenteredString(this.font, message4, this.width / 2, this.height / 2 + 20, 0xFFAA00);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (MapResourcePackManager.waitingForConfirmation) {
            MapResourcePackManager.declineResourcePacks();
        }
        this.minecraft.setScreen(null);
    }
}