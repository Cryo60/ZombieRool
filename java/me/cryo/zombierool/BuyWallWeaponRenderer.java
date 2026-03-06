package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;
import me.cryo.zombierool.block.BuyWallWeaponBlock;
import me.cryo.zombierool.block.system.MimicSystem;
import me.cryo.zombierool.core.system.WeaponFacade;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.AABB;

public class BuyWallWeaponRenderer implements BlockEntityRenderer<BuyWallWeaponBlockEntity> {

    private static final Map<ResourceLocation, ResourceLocation> OUTLINE_CACHE = new HashMap<>();
    private static final Map<UUID, Set<ResourceLocation>> PURCHASED_ITEMS = new HashMap<>();
    private static int textureCounter = 0;
    private static final Set<ResourceLocation> FAILED_TEXTURES = new HashSet<>();

    public BuyWallWeaponRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(BuyWallWeaponBlockEntity entity, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer,
                       int combinedLight, int combinedOverlay) {

        Direction face = entity.getBlockState().getValue(BuyWallWeaponBlock.FACING);
        BlockPos frontPos = entity.getBlockPos().relative(face);
        int frontLight = LevelRenderer.getLightColor(entity.getLevel(), frontPos);

        BlockState mimicState = entity.getMimic();
        if (mimicState != null) {
            poseStack.pushPose();
            MimicSystem.renderMimic(mimicState, entity.getBlockPos(), entity.getLevel(), poseStack, buffer, frontLight, combinedOverlay);
            poseStack.popPose();
        }

        if (entity.getItemToSell() != null) {
            var item = ForgeRegistries.ITEMS.getValue(entity.getItemToSell());
            if (item != null && item != Items.AIR) {
                var stack = new ItemStack(item);
                Player player = Minecraft.getInstance().player;
                boolean hasPurchased = player != null && hasPlayerPurchased(player, entity.getItemToSell());

                BakedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, entity.getLevel(), null, 0);

                TextureAtlasSprite sprite = model.getParticleIcon();
                ResourceLocation originalTexture = sprite.contents().name();

                boolean isMissingTexture = originalTexture.getPath().equals("missingno");

                if (isMissingTexture) {
                    ResourceLocation itemReg = ForgeRegistries.ITEMS.getKey(item);
                    if (itemReg != null) {
                        originalTexture = new ResourceLocation(itemReg.getNamespace(), "item/weapons/" + itemReg.getPath());
                        isMissingTexture = false;
                    }
                }

                ResourceLocation outlineTexture = null;
                ResourceLocation fullTexture = null;

                if (!isMissingTexture) {
                    outlineTexture = getOrCreateOutline(originalTexture);
                    fullTexture = hasPurchased ? getOrCreateFullTexture(originalTexture) : null;
                }

                poseStack.pushPose();

                double surfaceDepth = 0.5; 
                if (mimicState != null) {
                    VoxelShape shape = MimicSystem.getMimicShape(mimicState, entity.getLevel(), entity.getBlockPos(), CollisionContext.of(Minecraft.getInstance().player));
                    if (!shape.isEmpty()) {
                        AABB bounds = shape.bounds();
                        switch (face) {
                            case NORTH -> surfaceDepth = 0.5 - bounds.minZ;
                            case SOUTH -> surfaceDepth = bounds.maxZ - 0.5;
                            case WEST  -> surfaceDepth = 0.5 - bounds.minX;
                            case EAST  -> surfaceDepth = bounds.maxX - 0.5;
                        }
                    }
                }

                double offset = surfaceDepth + 0.003; 
                double x = 0.5 + face.getStepX() * offset;
                double y = 0.5;
                double z = 0.5 + face.getStepZ() * offset;

                poseStack.translate(x, y, z);

                float angle = switch(face) {
                    case NORTH -> 180f;
                    case SOUTH -> 0f;
                    case WEST  -> 90f;
                    case EAST  -> -90f;
                    default    -> 0f;
                };
                poseStack.mulPose(Axis.YP.rotationDegrees(angle));

                if (outlineTexture == null) {
                    poseStack.pushPose();
                    poseStack.mulPose(Axis.YP.rotationDegrees(180)); 
                    poseStack.scale(0.5f, 0.5f, 0.05f);
                    Minecraft.getInstance().getItemRenderer().renderStatic(
                        stack,
                        ItemDisplayContext.FIXED, 
                        frontLight, 
                        combinedOverlay,
                        poseStack,
                        buffer,
                        entity.getLevel(),
                        0
                    );
                    poseStack.popPose();
                } else {
                    float s = 0.6f;
                    poseStack.scale(s, s, 0.01f);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));

                    if (hasPurchased && fullTexture != null) {
                        poseStack.pushPose();
                        poseStack.translate(0, 0, -0.002);
                        renderChalkQuad(poseStack, buffer, outlineTexture, frontLight);
                        poseStack.popPose();

                        poseStack.pushPose();
                        poseStack.scale(0.95f, 0.95f, 1.0f); 
                        poseStack.translate(0, 0, 0.002);
                        renderChalkQuad(poseStack, buffer, fullTexture, frontLight);
                        poseStack.popPose();
                    } else {
                        renderChalkQuad(poseStack, buffer, outlineTexture, frontLight);
                    }
                }

                poseStack.popPose();
            }
        }
    }

    private void renderChalkQuad(PoseStack poseStack, MultiBufferSource buffer, 
                                  ResourceLocation texture, int light) {
        if (texture == null) return;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        Matrix4f matrix = poseStack.last().pose();
        float size = 0.5f;
        int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

        consumer.vertex(matrix, -size, -size, 0).color(255, 255, 255, 255)
            .uv(1, 1).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, size, -size, 0).color(255, 255, 255, 255)
            .uv(0, 1).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, size, size, 0).color(255, 255, 255, 255)
            .uv(0, 0).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, -size, size, 0).color(255, 255, 255, 255)
            .uv(1, 0).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
    }

    private static boolean hasPlayerPurchased(Player player, ResourceLocation itemRL) {
        UUID playerId = player.getUUID();
        Set<ResourceLocation> purchased = PURCHASED_ITEMS.get(playerId);

        if (purchased != null && purchased.contains(itemRL)) return true;

        // Check actual inventory for corresponding item (supports TacZ)
        for (ItemStack stack : player.getInventory().items) {
            if (WeaponFacade.isWeapon(stack)) {
                me.cryo.zombierool.core.system.WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
                if (def != null && def.id.equals(itemRL.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void markAsPurchased(Player player, ResourceLocation itemRL) {
        UUID playerId = player.getUUID();
        PURCHASED_ITEMS.computeIfAbsent(playerId, k -> new HashSet<>()).add(itemRL);
    }

    public static void clearAllPurchases() {
        PURCHASED_ITEMS.clear();
    }

    @Nullable
    private static ResourceLocation getOrCreateOutline(ResourceLocation originalTexture) {
        if (OUTLINE_CACHE.containsKey(originalTexture)) {
            return OUTLINE_CACHE.get(originalTexture);
        }
        if (FAILED_TEXTURES.contains(originalTexture)) {
            return null;
        }

        try {
            NativeImage original = loadTexture(originalTexture);
            if (original == null) {
                String path = originalTexture.getPath();
                if (path.contains("/weapons/")) {
                     ResourceLocation fallback = new ResourceLocation(originalTexture.getNamespace(), path.replace("/weapons/", "/"));
                     original = loadTexture(fallback);
                }
            }
            if (original == null) {
                FAILED_TEXTURES.add(originalTexture);
                return null;
            }

            NativeImage outline = createChalkOutline(original);
            DynamicTexture dynamicTexture = new DynamicTexture(outline);
            ResourceLocation outlineLocation = new ResourceLocation("zombierool", "dynamic/chalk_outline_" + textureCounter++);
            Minecraft.getInstance().getTextureManager().register(outlineLocation, dynamicTexture);
            
            OUTLINE_CACHE.put(originalTexture, outlineLocation);
            original.close(); 
            return outlineLocation;
        } catch (Exception e) {
            FAILED_TEXTURES.add(originalTexture);
            return null;
        }
    }

    @Nullable
    private static ResourceLocation getOrCreateFullTexture(ResourceLocation originalTexture) {
        String cacheKey = originalTexture.toString() + "_full";
        ResourceLocation lookupKey = new ResourceLocation("zombierool", cacheKey.replace(":", "_").replace("/", "_"));

        if (OUTLINE_CACHE.containsKey(lookupKey)) {
            return OUTLINE_CACHE.get(lookupKey);
        }
        if (FAILED_TEXTURES.contains(originalTexture)) {
            return null;
        }

        try {
            NativeImage original = loadTexture(originalTexture);
             if (original == null) {
                String path = originalTexture.getPath();
                if (path.contains("/weapons/")) {
                     ResourceLocation fallback = new ResourceLocation(originalTexture.getNamespace(), path.replace("/weapons/", "/"));
                     original = loadTexture(fallback);
                }
            }
            if (original == null) {
                FAILED_TEXTURES.add(originalTexture);
                return null;
            }

            int width = original.getWidth();
            int height = original.getHeight();
            int maxSize = Math.max(width, height);

            NativeImage squared = new NativeImage(maxSize, maxSize, true);
            for (int y = 0; y < maxSize; y++) {
                for (int x = 0; x < maxSize; x++) {
                    squared.setPixelRGBA(x, y, 0);
                }
            }

            int offsetX = (maxSize - width) / 2;
            int offsetY = (maxSize - height) / 2;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    squared.setPixelRGBA(x + offsetX, y + offsetY, original.getPixelRGBA(x, y));
                }
            }

            DynamicTexture dynamicTexture = new DynamicTexture(squared);
            ResourceLocation fullLocation = new ResourceLocation("zombierool", "dynamic/full_texture_" + textureCounter++);
            Minecraft.getInstance().getTextureManager().register(fullLocation, dynamicTexture);

            OUTLINE_CACHE.put(lookupKey, fullLocation);
            original.close();
            return fullLocation;
        } catch (Exception e) {
            FAILED_TEXTURES.add(originalTexture);
            return null;
        }
    }

    @Nullable
    private static NativeImage loadTexture(ResourceLocation location) {
        try {
            ResourceLocation texturePath = new ResourceLocation(location.getNamespace(), "textures/" + location.getPath() + ".png");
            InputStream stream = Minecraft.getInstance().getResourceManager().getResource(texturePath).orElseThrow().open();
            NativeImage image = NativeImage.read(stream);
            stream.close();
            return image;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static NativeImage createChalkOutline(NativeImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        int thickness = Math.max(1, width / 64); 

        NativeImage outline = new NativeImage(maxSize(width, height), maxSize(width, height), true);
        int maxSize = Math.max(width, height);
        int offsetX = (maxSize - width) / 2;
        int offsetY = (maxSize - height) / 2;

        for (int y = 0; y < maxSize; y++) {
            for (int x = 0; x < maxSize; x++) {
                outline.setPixelRGBA(x, y, 0);
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (getAlpha(original, x, y) > 0) {
                    if (isEdgePixel(original, x, y, width, height)) {
                        drawThickPixel(outline, x + offsetX, y + offsetY, maxSize, maxSize, thickness);
                    }
                }
            }
        }

        return outline;
    }

    private static int maxSize(int w, int h) { return Math.max(w, h); }

    private static void drawThickPixel(NativeImage image, int centerX, int centerY, int width, int height, int thickness) {
        int halfThickness = thickness / 2;
        for (int dy = -halfThickness; dy <= halfThickness; dy++) {
            for (int dx = -halfThickness; dx <= halfThickness; dx++) {
                int nx = centerX + dx;
                int ny = centerY + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    image.setPixelRGBA(nx, ny, 0xFFFFFFFF); 
                }
            }
        }
    }

    private static boolean isEdgePixel(NativeImage image, int x, int y, int width, int height) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                    return true;
                }
                
                if (getAlpha(image, nx, ny) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int getAlpha(NativeImage image, int x, int y) {
        int pixel = image.getPixelRGBA(x, y);
        return (pixel >> 24) & 0xFF;
    }

    public static void clearCache() {
        OUTLINE_CACHE.clear();
        FAILED_TEXTURES.clear();
    }

    public static void clearPlayerPurchases(UUID playerId) {
        PURCHASED_ITEMS.remove(playerId);
    }

    @Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            clearAllPurchases();
        }
    }
}