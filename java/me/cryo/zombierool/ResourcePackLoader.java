package me.cryo.zombierool;
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
import me.cryo.zombierool.init.ZombieroolModSounds;
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

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            try {
                IModFile modFile = ModList.get().getModFileById("zombierool").getFile();
                Path resourcePath = modFile.findResource("resourcepacks/ZombieRool");
                event.addRepositorySource((packConsumer) -> {
                    String description = Component.translatable("resourcepack.zombierool.description").getString();
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

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
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
    private static final int TICKS_BEFORE_PROMPT = 40;

    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/zombierool_declined_packs.json");
    private static boolean configLoaded = false;

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

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        loadConfig();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.getSingleplayerServer() != null) {
            String worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            if (!worldName.equals(currentWorldName)) {
                removeAppliedPacks(mc);
                currentWorldName = worldName;
                worldJustLoaded = true;
                ticksSinceWorldLoad = 0;
            }

            if (worldJustLoaded) {
                ticksSinceWorldLoad++;
                if (ticksSinceWorldLoad >= TICKS_BEFORE_PROMPT && !waitingForConfirmation) {
                    worldJustLoaded = false;
                    if (declinedWorlds.contains(worldName) || permanentlyDeclinedWorlds.contains(worldName)) {
                        return;
                    }
                    List<String> mapResourcePacks = findResourcePacksForWorld(worldName);
                    if (!mapResourcePacks.isEmpty()) {
                        pendingWorldName = worldName;
                        pendingResourcePacks = mapResourcePacks;
                        waitingForConfirmation = true;
                        mc.execute(() -> mc.setScreen(new ResourcePackConfirmationScreen(mapResourcePacks, worldName)));
                    }
                }
            }
        } else if (mc.level == null && currentWorldName != null) {
            removeAppliedPacks(mc);
            currentWorldName = null;
            worldJustLoaded = false;
            declinedWorlds.clear();
        }
    }

    private static void removeAppliedPacks(Minecraft mc) {
        if (appliedResourcePacks.isEmpty()) return;
        PackRepository packRepository = mc.getResourcePackRepository();
        Collection<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
        boolean changed = false;
        for (String packId : appliedResourcePacks) {
            if (selectedPacks.contains(packId)) {
                selectedPacks.remove(packId);
                changed = true;
            }
            if (mc.options.resourcePacks.contains(packId)) {
                mc.options.resourcePacks.remove(packId);
                changed = true;
            }
        }
        appliedResourcePacks.clear();
        if (changed) {
            packRepository.setSelected(selectedPacks);
            mc.options.save();
            mc.execute(() -> {
                try {
                    mc.reloadResourcePacks();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private static List<String> findResourcePacksForWorld(String worldName) {
        List<String> foundPacks = new ArrayList<>();
        try {
            Minecraft mc = Minecraft.getInstance();
            File resourcePacksDir = new File(mc.gameDirectory, "resourcepacks");
            if (!resourcePacksDir.exists()) {
                return foundPacks;
            }
            String worldLower = worldName.toLowerCase().trim();
            File[] rpFiles = resourcePacksDir.listFiles();
            if (rpFiles != null) {
                for (File rpFile : rpFiles) {
                    if (!(rpFile.isDirectory() || rpFile.getName().endsWith(".zip"))) {
                        continue;
                    }
                    String rpName = rpFile.getName().replace(".zip", "").toLowerCase().trim();
                    boolean exactMatch = rpName.equals(worldLower);
                    boolean worldInRP = rpName.contains(worldLower);
                    boolean rpSuffixMatch = rpName.equals(worldLower + "_rp") || rpName.equals(worldLower + " rp");
                    String rpWithoutSuffix = rpName.replaceAll("_rp$|\\s+rp$", "");
                    boolean matchWithoutSuffix = rpWithoutSuffix.equals(worldLower);
                    if (exactMatch || rpSuffixMatch || matchWithoutSuffix || (worldInRP && worldLower.length() > 5)) {
                        foundPacks.add(rpFile.getName());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return foundPacks;
    }

    public static void applyResourcePacks(List<String> packNames) {
        try {
            Minecraft mc = Minecraft.getInstance();
            PackRepository packRepository = mc.getResourcePackRepository();
            packRepository.reload();
            Collection<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
            boolean changed = false;
            for (String packName : packNames) {
                String packId = "file/" + packName;
                if (!selectedPacks.contains(packId)) {
                    selectedPacks.add(packId);
                    appliedResourcePacks.add(packId); 
                    changed = true;
                }
            }
            if (changed) {
                packRepository.setSelected(selectedPacks);
                for (String packName : packNames) {
                    String packId = "file/" + packName;
                    if (!mc.options.resourcePacks.contains(packId)) {
                        mc.options.resourcePacks.add(packId);
                    }
                }
                mc.options.save();
                mc.execute(() -> {
                    try {
                        mc.reloadResourcePacks();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

    public static void declineResourcePacks() {
        if (pendingWorldName != null) {
            declinedWorlds.add(pendingWorldName);
        }
        waitingForConfirmation = false;
        pendingResourcePacks.clear();
        pendingWorldName = null;
    }

    public static void declineForever(String worldName) {
        permanentlyDeclinedWorlds.add(worldName);
        saveConfig();
        waitingForConfirmation = false;
        pendingResourcePacks.clear();
        pendingWorldName = null;
    }
}

@OnlyIn(Dist.CLIENT)
class ResourcePackConfirmationScreen extends Screen {
    private final List<String> resourcePacks;
    private final String worldName;
    private Button yesButton;
    private Button noButton;
    private Button neverButton;

    protected ResourcePackConfirmationScreen(List<String> resourcePacks, String worldName) {
        super(Component.translatable("gui.zombierool.resourcepack.title"));
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

        this.yesButton = Button.builder(Component.translatable("gui.zombierool.resourcepack.yes"), btn -> {
            playSound();
            MapResourcePackManager.confirmResourcePacks();
            this.onClose();
        }).bounds(startX, buttonY, buttonWidth, buttonHeight).build();

        this.noButton = Button.builder(Component.translatable("gui.zombierool.resourcepack.no"), btn -> {
            playSound();
            MapResourcePackManager.declineResourcePacks();
            this.onClose();
        }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build();

        this.neverButton = Button.builder(Component.translatable("gui.zombierool.resourcepack.never"), btn -> {
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

        String packCountStr = String.valueOf(resourcePacks.size());
        graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.resourcepack.msg1"), this.width / 2, this.height / 2 - 25, 0xAAAAAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.resourcepack.msg2"), this.width / 2, this.height / 2 - 10, 0xAAAAAA);
        graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.resourcepack.msg3", packCountStr), this.width / 2, this.height / 2 + 5, 0x888888);
        graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.resourcepack.msg4"), this.width / 2, this.height / 2 + 20, 0xFFAA00);

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