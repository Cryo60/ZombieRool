package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.ResourcePackNetworkHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class MultiplayerResourcePackHandler {
	private static boolean hasRequestedThisSession = false;
	private static String currentWorldId = null;
	private static List<String> appliedMultiplayerPacks = new ArrayList<>();
	private static boolean pendingRemoval = false;
	private static int ticksSinceDisconnect = 0;
	private static final int TICKS_BEFORE_REMOVAL = 20;
	@SubscribeEvent
	public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
	    if (event.getLevel().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
	        Minecraft mc = Minecraft.getInstance();
	        if (pendingRemoval) {
	            pendingRemoval = false;
	            ticksSinceDisconnect = 0;
	        }
	        new Thread(() -> {
	            try {
	                Thread.sleep(2000);
	                String worldId = generateWorldId(mc);
	                if (!worldId.equals(currentWorldId)) {
	                    currentWorldId = worldId;
	                    hasRequestedThisSession = false;
	                }
	                if (!hasRequestedThisSession) {
	                    hasRequestedThisSession = true;
	                    ResourcePackNetworkHandler.sendToServer(new ResourcePackNetworkHandler.RequestResourcePackMessage());
	                }
	            } catch (InterruptedException e) {
	                Thread.currentThread().interrupt();
	            }
	        }).start();
	    }
	}
	
	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
	    if (event.phase != TickEvent.Phase.END) return;
	    Minecraft mc = Minecraft.getInstance();
	
	    if (pendingRemoval) {
	        ticksSinceDisconnect++;
	        if (ticksSinceDisconnect >= TICKS_BEFORE_REMOVAL) {
	            removeResourcePacksSilently(appliedMultiplayerPacks);
	            appliedMultiplayerPacks.clear();
	            pendingRemoval = false;
	            ticksSinceDisconnect = 0;
	        }
	        return;
	    }
	
	    if (mc.level == null && mc.getSingleplayerServer() == null && currentWorldId != null) {
	        if (!appliedMultiplayerPacks.isEmpty()) {
	            pendingRemoval = true;
	            ticksSinceDisconnect = 0;
	        }
	        currentWorldId = null;
	        hasRequestedThisSession = false;
	    }
	}
	
	private static void removeResourcePacksSilently(List<String> packIds) {
	    try {
	        Minecraft mc = Minecraft.getInstance();
	        if (mc.level != null) return;
	        
	        PackRepository packRepository = mc.getResourcePackRepository();
	        Collection<String> selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
	        boolean changed = selectedPacks.removeAll(packIds);
	
	        if (changed) {
	            packRepository.setSelected(selectedPacks);
	            mc.execute(() -> {
	                try {
	                    mc.reloadResourcePacks();
	                } catch (Exception e) {
	                    // Ignoré
	                }
	            });
	        }
	    } catch (Exception e) {
	        // Ignoré
	    }
	}
	
	private static String generateWorldId(Minecraft mc) {
	    if (mc.getCurrentServer() != null) {
	        return mc.getCurrentServer().ip;
	    } else if (mc.getSingleplayerServer() != null) {
	        return "solo_" + mc.getSingleplayerServer().getWorldData().getLevelName();
	    }
	    return "unknown";
	}
	
	public static void showPrompt(String url, String name) {
	    Minecraft mc = Minecraft.getInstance();
	    File rpDir = new File(mc.gameDirectory, "resourcepacks");
	    File rpFile = new File(rpDir, name + ".zip");
	
	    if (rpFile.exists()) {
	        boolean isApplied = mc.getResourcePackRepository().getSelectedIds()
	            .contains("file/" + name + ".zip");
	        
	        if (!isApplied) {
	            mc.execute(() -> {
	                mc.setScreen(new MultiplayerRPPromptScreen(url, name, true));
	            });
	        } else {
	            String packId = "file/" + name + ".zip";
	            if (!appliedMultiplayerPacks.contains(packId)) {
	                appliedMultiplayerPacks.add(packId);
	            }
	        }
	    } else {
	        mc.execute(() -> {
	            mc.setScreen(new MultiplayerRPPromptScreen(url, name, false));
	        });
	    }
	}
	
	@OnlyIn(Dist.CLIENT)
	private static class MultiplayerRPPromptScreen extends Screen {
	    private final String rpUrl;
	    private final String rpName;
	    private final boolean alreadyDownloaded;
	
	    protected MultiplayerRPPromptScreen(String rpUrl, String rpName, boolean alreadyDownloaded) {
	        super(Component.literal("Resource Pack Required"));
	        this.rpUrl = rpUrl;
	        this.rpName = rpName;
	        this.alreadyDownloaded = alreadyDownloaded;
	    }
	
	    @Override
	    protected void init() {
	        int buttonWidth = 100;
	        int buttonHeight = 20;
	        int spacing = 10;
	        int totalWidth = (buttonWidth * 2) + spacing;
	        int startX = (this.width - totalWidth) / 2;
	        int buttonY = this.height / 2 + 30;
	
	        String yesLabel = alreadyDownloaded ? "Apply" : "Download";
	        Button yesButton = Button.builder(Component.literal(yesLabel), btn -> {
	            playSound();
	            this.onClose();
	            if (alreadyDownloaded) {
	                applyResourcePack(rpName + ".zip");
	            } else {
	                downloadAndApplyResourcePack();
	            }
	        }).bounds(startX, buttonY, buttonWidth, buttonHeight).build();
	
	        Button noButton = Button.builder(Component.literal("No Thanks"), btn -> {
	            playSound();
	            this.onClose();
	        }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build();
	
	        this.addRenderableWidget(yesButton);
	        this.addRenderableWidget(noButton);
	    }
	
	    private void playSound() {
	        if (minecraft != null) {
	            minecraft.getSoundManager().play(
	                SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)
	            );
	        }
	    }
	
	    private void applyResourcePack(String fileName) {
	        new Thread(() -> {
	            try {
	                var packRepository = minecraft.getResourcePackRepository();
	                packRepository.reload();
	                var selectedPacks = new ArrayList<>(packRepository.getSelectedIds());
	                String packId = "file/" + fileName;
	                
	                if (!selectedPacks.contains(packId)) {
	                    selectedPacks.add(packId);
	                    packRepository.setSelected(selectedPacks);
	                    appliedMultiplayerPacks.add(packId);
	                    minecraft.execute(() -> {
	                        minecraft.reloadResourcePacks();
	                    });
	                }
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }).start();
	    }
	
	    private void downloadAndApplyResourcePack() {
	        new Thread(() -> {
	            try {
	                boolean success = downloadResourcePackDirect(rpUrl, rpName);
	                if (success) {
	                    minecraft.execute(() -> {
	                        applyResourcePack(rpName + ".zip");
	                    });
	                }
	            } catch (Exception e) {
	                e.printStackTrace();
	            }
	        }).start();
	    }
	
	    private boolean downloadResourcePackDirect(String url, String name) {
	        try {
	            File rpDir = new File(minecraft.gameDirectory, "resourcepacks");
	            rpDir.mkdirs();
	            File targetFile = new File(rpDir, name + ".zip");
	
	            if (targetFile.exists()) {
	                return true;
	            }
	
	            java.net.URL urlObj = new java.net.URL(url);
	            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) urlObj.openConnection();
	            conn.setRequestMethod("GET");
	            conn.setConnectTimeout(30000);
	            conn.setReadTimeout(60000);
	            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
	            conn.connect();
	
	            try (java.io.InputStream in = conn.getInputStream();
	                 java.io.FileOutputStream out = new java.io.FileOutputStream(targetFile)) {
	                byte[] buffer = new byte[8192];
	                int bytesRead;
	                while ((bytesRead = in.read(buffer)) != -1) {
	                    out.write(buffer, 0, bytesRead);
	                }
	            }
	            conn.disconnect();
	            return true;
	        } catch (Exception e) {
	            return false;
	        }
	    }
	
	    @Override
	    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
	        this.renderBackground(graphics);
	        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
	        
	        String msg1 = alreadyDownloaded ? 
	            "This world uses a custom resource pack." :
	            "This world requires a custom resource pack.";
	        String msg2 = alreadyDownloaded ?
	            "Would you like to apply it?" :
	            "Would you like to download it?";
	        String msg3 = "Resource Pack: " + rpName;
	
	        graphics.drawCenteredString(this.font, msg1, this.width / 2, this.height / 2 - 20, 0xAAAAAA);
	        graphics.drawCenteredString(this.font, msg2, this.width / 2, this.height / 2 - 5, 0xAAAAAA);
	        graphics.drawCenteredString(this.font, msg3, this.width / 2, this.height / 2 + 10, 0x00FF00);
	
	        super.render(graphics, mouseX, mouseY, partialTick);
	    }
	
	    @Override
	    public void onClose() {
	        this.minecraft.setScreen(null);
	    }
	}
}