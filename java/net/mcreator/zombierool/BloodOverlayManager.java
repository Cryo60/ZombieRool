package net.mcreator.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BloodOverlayManager {
    
    private static class OverlayData {
        final int textureIndex;
        final int rotation;

        OverlayData(int textureIndex, int rotation) {
            this.textureIndex = textureIndex;
            this.rotation = rotation;
        }
    }
    
    private static final Map<BlockFaceKey, OverlayData> overlays = new HashMap<>();
    private static final float OFFSET = 0.002f;

    public static void addOverlay(BlockPos pos, Direction face, int textureIndex, int rotation) {
        overlays.put(new BlockFaceKey(pos, face), new OverlayData(textureIndex, rotation));
    }

    public static void removeOverlay(BlockPos pos, Direction face) {
        overlays.remove(new BlockFaceKey(pos, face));
    }
    
    public static void clearAll() {
        overlays.clear();
        System.out.println("DEBUG OverlayManager: Cache client vidé");
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
            renderBloodOverlay(poseStack, bufferSource, key.pos, key.face, data.textureIndex, data.rotation);
        }

        poseStack.popPose();
        bufferSource.endBatch();
    }

    private static void renderBloodOverlay(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos, Direction face, int texIndex, int rotation) {
        ResourceLocation texture = new ResourceLocation("zombierool", "textures/block/blood_" + texIndex + ".png");
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
                addVertex(consumer, matrix, 0, 0, 1+OFFSET, uvCoords[0][0], uvCoords[0][1], light, overlay);
                addVertex(consumer, matrix, 1, 0, 1+OFFSET, uvCoords[1][0], uvCoords[1][1], light, overlay);
                addVertex(consumer, matrix, 1, 1, 1+OFFSET, uvCoords[2][0], uvCoords[2][1], light, overlay);
                addVertex(consumer, matrix, 0, 1, 1+OFFSET, uvCoords[3][0], uvCoords[3][1], light, overlay);
                break;
            case WEST:
                addVertex(consumer, matrix, -OFFSET, 0, 0, uvCoords[0][0], uvCoords[0][1], light, overlay);
                addVertex(consumer, matrix, -OFFSET, 0, 1, uvCoords[1][0], uvCoords[1][1], light, overlay);
                addVertex(consumer, matrix, -OFFSET, 1, 1, uvCoords[2][0], uvCoords[2][1], light, overlay);
                addVertex(consumer, matrix, -OFFSET, 1, 0, uvCoords[3][0], uvCoords[3][1], light, overlay);
                break;
            case EAST:
                addVertex(consumer, matrix, 1+OFFSET, 0, 1, uvCoords[0][0], uvCoords[0][1], light, overlay);
                addVertex(consumer, matrix, 1+OFFSET, 0, 0, uvCoords[1][0], uvCoords[1][1], light, overlay);
                addVertex(consumer, matrix, 1+OFFSET, 1, 0, uvCoords[2][0], uvCoords[2][1], light, overlay);
                addVertex(consumer, matrix, 1+OFFSET, 1, 1, uvCoords[3][0], uvCoords[3][1], light, overlay);
                break;
            case DOWN:
                addVertex(consumer, matrix, 0, -OFFSET, 0, uvCoords[0][0], uvCoords[0][1], light, overlay);
                addVertex(consumer, matrix, 1, -OFFSET, 0, uvCoords[1][0], uvCoords[1][1], light, overlay);
                addVertex(consumer, matrix, 1, -OFFSET, 1, uvCoords[2][0], uvCoords[2][1], light, overlay);
                addVertex(consumer, matrix, 0, -OFFSET, 1, uvCoords[3][0], uvCoords[3][1], light, overlay);
                break;
            case UP:
                addVertex(consumer, matrix, 0, 1+OFFSET, 1, uvCoords[0][0], uvCoords[0][1], light, overlay);
                addVertex(consumer, matrix, 1, 1+OFFSET, 1, uvCoords[1][0], uvCoords[1][1], light, overlay);
                addVertex(consumer, matrix, 1, 1+OFFSET, 0, uvCoords[2][0], uvCoords[2][1], light, overlay);
                addVertex(consumer, matrix, 0, 1+OFFSET, 0, uvCoords[3][0], uvCoords[3][1], light, overlay);
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
    
    public static Map<String, String> getOverlays() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<BlockFaceKey, OverlayData> entry : overlays.entrySet()) {
            BlockPos pos = entry.getKey().pos;
            Direction face = entry.getKey().face;
            OverlayData data = entry.getValue();
            String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
            String value = data.textureIndex + ":" + data.rotation;
            result.put(key, value);
        }
        return result;
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
                    
                    String[] valueParts = entry.getValue().split(":");
                    int textureIndex = Integer.parseInt(valueParts[0]);
                    int rotation = valueParts.length > 1 ? Integer.parseInt(valueParts[1]) : 0;
                    
                    if (face != null) {
                        overlays.put(new BlockFaceKey(new BlockPos(x, y, z), face), new OverlayData(textureIndex, rotation));
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}