package net.mcreator.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.mcreator.zombierool.network.ResourcePackNetworkHandler;

import java.io.File;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class MultiplayerResourcePackHandler {
    
    private static boolean hasRequestedThisSession = false;
    private static String currentWorldId = null;

    @SubscribeEvent
    public static void onPlayerJoinWorld(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() == Minecraft.getInstance().player) {
            Minecraft mc = Minecraft.getInstance();
            
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
                        System.out.println("[ZombieRool] Requesting resource pack info from server...");
                        ResourcePackNetworkHandler.sendToServer(new ResourcePackNetworkHandler.RequestResourcePackMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
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
        
        System.out.println("[ZombieRool] Server has resource pack: " + name);
        
        File rpDir = new File(mc.gameDirectory, "resourcepacks");
        File rpFile = new File(rpDir, name + ".zip");
        
        if (rpFile.exists()) {
            System.out.println("[ZombieRool] Resource pack already installed, checking if applied...");
            
            boolean isApplied = mc.getResourcePackRepository().getSelectedIds()
                .contains("file/" + name + ".zip");
            
            if (!isApplied) {
                System.out.println("[ZombieRool] Resource pack not applied, prompting user...");
                mc.execute(() -> {
                    mc.setScreen(new MultiplayerRPPromptScreen(url, name, true));
                });
            } else {
                System.out.println("[ZombieRool] Resource pack already applied");
            }
        } else {
            System.out.println("[ZombieRool] Resource pack not found locally, prompting download...");
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
                    System.out.println("[ZombieRool] Applying resource pack: " + fileName);
                    
                    var packRepository = minecraft.getResourcePackRepository();
                    packRepository.reload();
                    
                    var selectedPacks = new java.util.ArrayList<>(packRepository.getSelectedIds());
                    String packId = "file/" + fileName;
                    
                    if (!selectedPacks.contains(packId)) {
                        selectedPacks.add(packId);
                        packRepository.setSelected(selectedPacks);
                        
                        minecraft.execute(() -> {
                            minecraft.reloadResourcePacks();
                        });
                        
                        System.out.println("[ZombieRool] Resource pack applied");
                    }
                } catch (Exception e) {
                    System.err.println("[ZombieRool] Error applying resource pack: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        private void downloadAndApplyResourcePack() {
            System.out.println("[ZombieRool] Downloading resource pack from: " + rpUrl);
            
            new Thread(() -> {
                try {
                    boolean success = downloadResourcePackDirect(rpUrl, rpName);
                    if (success) {
                        minecraft.execute(() -> {
                            applyResourcePack(rpName + ".zip");
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[ZombieRool] Error downloading RP: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
        }

        private boolean downloadResourcePackDirect(String url, String name) {
            try {
                System.out.println("[ZombieRool] Downloading: " + url);
                
                File rpDir = new File(minecraft.gameDirectory, "resourcepacks");
                rpDir.mkdirs();
                File targetFile = new File(rpDir, name + ".zip");
                
                // Vérifier si le fichier existe déjà
                if (targetFile.exists()) {
                    System.out.println("[ZombieRool] Resource pack already exists, skipping download");
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
                System.out.println("[ZombieRool] Download complete: " + targetFile.length() + " bytes");
                return true;
                
            } catch (Exception e) {
                System.err.println("[ZombieRool] Download failed: " + e.getMessage());
                e.printStackTrace();
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