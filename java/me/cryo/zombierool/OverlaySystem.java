package me.cryo.zombierool.core.system;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import me.cryo.zombierool.item.BloodBrushItem;
import me.cryo.zombierool.item.ChalkItem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.C2SUpdateChalkItemPacket;
import me.cryo.zombierool.network.packet.S2CSyncOverlaysPacket;
import me.cryo.zombierool.network.packet.S2CUpdateOverlayPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverlaySystem {

    private static class OverlayData {
        final String texturePath;
        final int rotation;
        OverlayData(String texturePath, int rotation) {
            this.texturePath = texturePath;
            this.rotation = rotation;
        }
    }

    private static class BlockFaceKey {
        final BlockPos pos;
        final Direction face;
        BlockFaceKey(BlockPos pos, Direction face) {
            this.pos = pos.immutable();
            this.face = face;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockFaceKey)) return false;
            BlockFaceKey that = (BlockFaceKey) o;
            return pos.equals(that.pos) && face == that.face;
        }
        @Override
        public int hashCode() {
            return 31 * pos.hashCode() + face.hashCode();
        }
    }

    private static final Map<BlockFaceKey, OverlayData> overlays = new HashMap<>();

    public static void addOverlay(BlockPos pos, Direction face, String texturePath, int rotation) {
        overlays.put(new BlockFaceKey(pos, face), new OverlayData(texturePath, rotation));
    }

    public static void removeOverlay(BlockPos pos, Direction face) {
        overlays.remove(new BlockFaceKey(pos, face));
    }

    public static void clearAll() {
        overlays.clear();
    }

    public static void setOverlays(Map<String, String> loadedOverlays) {
        overlays.clear();
        for (Map.Entry<String, String> entry : loadedOverlays.entrySet()) {
            String[] parts = entry.getKey().split("_");
            if (parts.length >= 4) {
                try {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    Direction face = Direction.byName(parts[3]);
                    
                    String[] valueParts = entry.getValue().split(";");
                    String texturePath = valueParts[0];
                    int rotation = valueParts.length > 1 ? Integer.parseInt(valueParts[1]) : 0;

                    if (face != null) {
                        overlays.put(new BlockFaceKey(new BlockPos(x, y, z), face), new OverlayData(texturePath, rotation));
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ServerEvents {
        @SubscribeEvent
        public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
            Player player = event.getEntity();
            ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);

            if (!(stack.getItem() instanceof BloodBrushItem) && !(stack.getItem() instanceof ChalkItem)) {
                return;
            }

            Level level = player.level();
            if (level.isClientSide) return;

            BlockPos pos = event.getPos();
            Direction face = event.getFace();

            if (level instanceof ServerLevel serverLevel) {
                WorldConfig config = WorldConfig.get(serverLevel);
                String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
                config.removeMapOverlay(key);
            }

            S2CUpdateOverlayPacket packet = new S2CUpdateOverlayPacket(pos, face, "", 0, false);
            NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(pos)), packet);

            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) syncOverlaysToPlayer(player);
        }

        @SubscribeEvent
        public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) syncOverlaysToPlayer(player);
        }

        @SubscribeEvent
        public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) syncOverlaysToPlayer(player);
        }

        private static void syncOverlaysToPlayer(ServerPlayer player) {
            if (player.level() instanceof ServerLevel serverLevel) {
                WorldConfig config = WorldConfig.get(serverLevel);
                Map<String, String> mapOverlays = config.getMapOverlays();
                if (!mapOverlays.isEmpty()) {
                    S2CSyncOverlaysPacket packet = new S2CSyncOverlaysPacket(mapOverlays);
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
                }
            }
        }
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {
        private static final float OFFSET = 0.002f;

        @SubscribeEvent
        public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            clearAll();
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;

            Minecraft mc = Minecraft.getInstance();
            Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            poseStack.pushPose();
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            for (Map.Entry<BlockFaceKey, OverlayData> entry : overlays.entrySet()) {
                BlockFaceKey key = entry.getKey();
                OverlayData data = entry.getValue();
                renderOverlay(poseStack, bufferSource, key.pos, key.face, data.texturePath, data.rotation);
            }

            poseStack.popPose();
            bufferSource.endBatch();
        }

        private static void renderOverlay(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos, Direction face, String texturePath, int rotation) {
            ResourceLocation texture = new ResourceLocation(texturePath);
            RenderType renderType = RenderType.entityCutout(texture);
            VertexConsumer consumer = buffer.getBuffer(renderType);

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            Matrix4f matrix = poseStack.last().pose();

            int light = 15728880; 
            int overlay = OverlayTexture.NO_OVERLAY;

            float[][] uvCoords = getRotatedUVs(rotation);

            switch (face) {
                case NORTH:
                    addVertex(consumer, matrix, 1, 0, -OFFSET, uvCoords[0][0], uvCoords[0][1], light, overlay);
                    addVertex(consumer, matrix, 0, 0, -OFFSET, uvCoords[1][0], uvCoords[1][1], light, overlay);
                    addVertex(consumer, matrix, 0, 1, -OFFSET, uvCoords[2][0], uvCoords[2][1], light, overlay);
                    addVertex(consumer, matrix, 1, 1, -OFFSET, uvCoords[3][0], uvCoords[3][1], light, overlay);
                    break;
                case SOUTH:
                    addVertex(consumer, matrix, 0, 0, 1 + OFFSET, uvCoords[0][0], uvCoords[0][1], light, overlay);
                    addVertex(consumer, matrix, 1, 0, 1 + OFFSET, uvCoords[1][0], uvCoords[1][1], light, overlay);
                    addVertex(consumer, matrix, 1, 1, 1 + OFFSET, uvCoords[2][0], uvCoords[2][1], light, overlay);
                    addVertex(consumer, matrix, 0, 1, 1 + OFFSET, uvCoords[3][0], uvCoords[3][1], light, overlay);
                    break;
                case WEST:
                    addVertex(consumer, matrix, -OFFSET, 0, 0, uvCoords[0][0], uvCoords[0][1], light, overlay);
                    addVertex(consumer, matrix, -OFFSET, 0, 1, uvCoords[1][0], uvCoords[1][1], light, overlay);
                    addVertex(consumer, matrix, -OFFSET, 1, 1, uvCoords[2][0], uvCoords[2][1], light, overlay);
                    addVertex(consumer, matrix, -OFFSET, 1, 0, uvCoords[3][0], uvCoords[3][1], light, overlay);
                    break;
                case EAST:
                    addVertex(consumer, matrix, 1 + OFFSET, 0, 1, uvCoords[0][0], uvCoords[0][1], light, overlay);
                    addVertex(consumer, matrix, 1 + OFFSET, 0, 0, uvCoords[1][0], uvCoords[1][1], light, overlay);
                    addVertex(consumer, matrix, 1 + OFFSET, 1, 0, uvCoords[2][0], uvCoords[2][1], light, overlay);
                    addVertex(consumer, matrix, 1 + OFFSET, 1, 1, uvCoords[3][0], uvCoords[3][1], light, overlay);
                    break;
                case DOWN:
                    addVertex(consumer, matrix, 0, -OFFSET, 0, uvCoords[0][0], uvCoords[0][1], light, overlay);
                    addVertex(consumer, matrix, 1, -OFFSET, 0, uvCoords[1][0], uvCoords[1][1], light, overlay);
                    addVertex(consumer, matrix, 1, -OFFSET, 1, uvCoords[2][0], uvCoords[2][1], light, overlay);
                    addVertex(consumer, matrix, 0, -OFFSET, 1, uvCoords[3][0], uvCoords[3][1], light, overlay);
                    break;
                case UP:
                    addVertex(consumer, matrix, 0, 1 + OFFSET, 1, uvCoords[0][0], uvCoords[0][1], light, overlay);
                    addVertex(consumer, matrix, 1, 1 + OFFSET, 1, uvCoords[1][0], uvCoords[1][1], light, overlay);
                    addVertex(consumer, matrix, 1, 1 + OFFSET, 0, uvCoords[2][0], uvCoords[2][1], light, overlay);
                    addVertex(consumer, matrix, 0, 1 + OFFSET, 0, uvCoords[3][0], uvCoords[3][1], light, overlay);
                    break;
            }
            poseStack.popPose();
        }

        private static float[][] getRotatedUVs(int rotation) {
            float[][] baseUVs = {
                {0, 1}, {1, 1}, {1, 0}, {0, 0}
            };
            int offset = (rotation / 90) % 4;
            float[][] rotatedUVs = new float[4][2];
            for (int i = 0; i < 4; i++) {
                int newIndex = (i + offset) % 4;
                rotatedUVs[i] = baseUVs[newIndex];
            }
            return rotatedUVs;
        }

        private static void addVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, int light, int overlay) {
            consumer.vertex(matrix, x, y, z)
                    .color(255, 255, 255, 255)
                    .uv(u, v)
                    .overlayCoords(overlay)
                    .uv2(light)
                    .normal(0, 1, 0)
                    .endVertex();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class ChalkSelectionScreen extends Screen {
        private final InteractionHand hand;
        private String selectedTexture;
        private int currentRotation;
        private final List<String> availableChalks = new ArrayList<>();
        private int scrollOffset = 0;

        public ChalkSelectionScreen(ItemStack chalkStack, InteractionHand hand) {
            super(Component.translatable("gui.zombierool.chalk.title"));
            this.hand = hand;
            this.selectedTexture = chalkStack.getOrCreateTag().getString("chalk_texture");
            if (this.selectedTexture.isEmpty()) {
                this.selectedTexture = "zombierool:textures/chalks/chalk_a.png"; 
            }
            this.currentRotation = chalkStack.getOrCreateTag().getInt("chalk_rotation");

            availableChalks.add("zombierool:textures/chalks/chalk_a.png");
            availableChalks.add("zombierool:textures/chalks/chalk_b.png");
            availableChalks.add("zombierool:textures/chalks/chalk_d.png");
            availableChalks.add("zombierool:textures/chalks/chalk_e.png");

            for (ResourceLocation loc : DynamicResourceManager.getAllClientChalks()) {
                availableChalks.add(loc.toString());
            }
        }

        @Override
        protected void init() {
            super.init();
            int centerX = this.width / 2;
            int bottomY = this.height - 30;

            this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.chalk.rotation", currentRotation), btn -> {
                currentRotation = (currentRotation + 90) % 360;
                btn.setMessage(Component.translatable("gui.zombierool.chalk.rotation", currentRotation));
                saveAndSync();
            }).bounds(centerX - 105, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.chalk.close"), btn -> {
                this.onClose();
            }).bounds(centerX + 5, bottomY, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("<"), btn -> {
                if (scrollOffset > 0) scrollOffset--;
            }).bounds(centerX - 120, this.height / 2 - 10, 20, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal(">"), btn -> {
                if (scrollOffset < Math.max(0, (availableChalks.size() - 1) / 4)) scrollOffset++;
            }).bounds(centerX + 100, this.height / 2 - 10, 20, 20).build());
        }

        private void saveAndSync() {
            NetworkHandler.INSTANCE.sendToServer(new C2SUpdateChalkItemPacket(selectedTexture, currentRotation, hand));
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
            this.renderBackground(g);
            super.render(g, mouseX, mouseY, pt);
            g.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFAA00);

            int startX = this.width / 2 - 80;
            int startY = this.height / 2 - 20;
            int iconSize = 32;
            int spacing = 8;
            int startIndex = scrollOffset * 4;

            for (int i = 0; i < 4; i++) {
                int index = startIndex + i;
                if (index >= availableChalks.size()) break;

                String chalkPath = availableChalks.get(index);
                int x = startX + i * (iconSize + spacing);
                int y = startY;

                if (chalkPath.equals(selectedTexture)) {
                    g.fill(x - 2, y - 2, x + iconSize + 2, y + iconSize + 2, 0xFF00FF00);
                } else {
                    g.fill(x - 1, y - 1, x + iconSize + 1, y + iconSize + 1, 0xFF555555);
                }
                g.fill(x, y, x + iconSize, y + iconSize, 0xFF222222);

                RenderSystem.enableBlend();
                g.blit(new ResourceLocation(chalkPath), x, y, 0, 0, iconSize, iconSize, iconSize, iconSize);
                RenderSystem.disableBlend();
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int startX = this.width / 2 - 80;
            int startY = this.height / 2 - 20;
            int iconSize = 32;
            int spacing = 8;
            int startIndex = scrollOffset * 4;

            for (int i = 0; i < 4; i++) {
                int index = startIndex + i;
                if (index >= availableChalks.size()) break;

                int x = startX + i * (iconSize + spacing);
                int y = startY;

                if (mouseX >= x && mouseX <= x + iconSize && mouseY >= y && mouseY <= y + iconSize) {
                    selectedTexture = availableChalks.get(index);
                    saveAndSync();
                    return true;
                }
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
}
