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
import me.cryo.zombierool.integration.TacZIntegration;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;

public class BuyWallWeaponRenderer implements BlockEntityRenderer<BuyWallWeaponBlockEntity> {
	private static final Map<ResourceLocation, ResourceLocation> OUTLINE_CACHE = new HashMap<>();
	private static final Map<UUID, Set<String>> PURCHASED_ITEMS = new HashMap<>();
	private static int textureCounter = 0;
	private static final Set<ResourceLocation> FAILED_TEXTURES = new HashSet<>();

	public BuyWallWeaponRenderer(BlockEntityRendererProvider.Context context) {}

	private static class ColorOverrideVertexConsumer implements VertexConsumer {
		private final VertexConsumer delegate;
		private final int r, g, b, a;

		public ColorOverrideVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
			this.delegate = delegate;
			this.r = r;
			this.g = g;
			this.b = b;
			this.a = a;
		}

		@Override public VertexConsumer vertex(double x, double y, double z) { delegate.vertex(x, y, z); return this; }
		@Override public VertexConsumer color(int red, int green, int blue, int alpha) { delegate.color(r, g, b, a); return this; }
		@Override public VertexConsumer uv(float u, float v) { delegate.uv(u, v); return this; }
		@Override public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u, v); return this; }
		@Override public VertexConsumer uv2(int u, int v) { delegate.uv2(u, v); return this; }
		@Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
		@Override public void endVertex() { delegate.endVertex(); }
		@Override public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(this.r, this.g, this.b, this.a); }
		@Override public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
	}

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

	    ItemStack stack = ItemStack.EMPTY;
	    var opt = entity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve();
	    if (opt.isPresent()) {
	        stack = opt.get().getStackInSlot(0);
	    } else if (entity.getItemToSell() != null) {
	        var item = ForgeRegistries.ITEMS.getValue(entity.getItemToSell());
	        if (item != null && item != Items.AIR) {
	            stack = new ItemStack(item);
	        }
	    }

	    if (!stack.isEmpty()) {
	        Player player = Minecraft.getInstance().player;
	        boolean hasPurchased = player != null && hasPlayerPurchased(player, stack);

	        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, entity.getLevel(), null, 0);
	        TextureAtlasSprite sprite = model.getParticleIcon();
	        ResourceLocation originalTexture = sprite.contents().name();

	        boolean isTacz = WeaponFacade.isTaczWeapon(stack);
	        boolean isMissingTexture = originalTexture.getPath().equals("missingno") || isTacz;

	        if (isTacz) {
	            String gunIdStr = stack.getOrCreateTag().getString("GunId");
	            if (!gunIdStr.isEmpty()) {
	                ResourceLocation gunId = new ResourceLocation(gunIdStr);
	                originalTexture = TacZIntegration.getGunIcon(gunId);
	                isMissingTexture = false;
	            }
	        } else if (isMissingTexture) {
	            ResourceLocation itemReg = ForgeRegistries.ITEMS.getKey(stack.getItem());
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

	        if (outlineTexture == null && !isTacz) {
	            poseStack.pushPose();
	            poseStack.mulPose(Axis.YP.rotationDegrees(180)); 
	            float scaleXY = 0.5f;
	            float scaleZ = hasPurchased ? 0.05f : 0.001f; 
	            poseStack.scale(scaleXY, scaleXY, scaleZ);

				MultiBufferSource finalBuffer = buffer;
				if (!hasPurchased) {
					finalBuffer = rt -> new ColorOverrideVertexConsumer(buffer.getBuffer(rt), 255, 255, 255, 255);
				}

	            Minecraft.getInstance().getItemRenderer().renderStatic(
	                stack,
	                ItemDisplayContext.FIXED, 
	                frontLight, 
	                combinedOverlay,
	                poseStack,
	                finalBuffer,
	                entity.getLevel(),
	                0
	            );
	            poseStack.popPose();
	        } else {
	            float s = 0.6f;
	            poseStack.scale(s, s, 0.01f);
	            poseStack.mulPose(Axis.YP.rotationDegrees(180));

	            if (outlineTexture != null) {
	                if (hasPurchased && fullTexture != null) {
	                    poseStack.pushPose();
	                    poseStack.translate(0, 0, -0.002);
	                    renderChalkQuad(poseStack, buffer, outlineTexture, frontLight, 0, 1, 0, 1);
	                    poseStack.popPose();

	                    poseStack.pushPose();
	                    poseStack.scale(0.95f, 0.95f, 1.0f); 
	                    poseStack.translate(0, 0, 0.002);
	                    renderChalkQuad(poseStack, buffer, fullTexture, frontLight, 0, 1, 0, 1);
	                    poseStack.popPose();
	                } else {
	                    renderChalkQuad(poseStack, buffer, outlineTexture, frontLight, 0, 1, 0, 1);
	                }
	            } else {
	                TextureAtlasSprite iconSprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(originalTexture);
	                VertexConsumer consumer = buffer.getBuffer(RenderType.cutout());
	                Matrix4f matrix = poseStack.last().pose();
	                float size = 0.5f;
	                int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;
	                
	                consumer.vertex(matrix, -size, -size, 0).color(255, 255, 255, 255).uv(iconSprite.getU1(), iconSprite.getV1()).overlayCoords(overlay).uv2(frontLight).normal(0, 0, 1).endVertex();
	                consumer.vertex(matrix, size, -size, 0).color(255, 255, 255, 255).uv(iconSprite.getU0(), iconSprite.getV1()).overlayCoords(overlay).uv2(frontLight).normal(0, 0, 1).endVertex();
	                consumer.vertex(matrix, size, size, 0).color(255, 255, 255, 255).uv(iconSprite.getU0(), iconSprite.getV0()).overlayCoords(overlay).uv2(frontLight).normal(0, 0, 1).endVertex();
	                consumer.vertex(matrix, -size, size, 0).color(255, 255, 255, 255).uv(iconSprite.getU1(), iconSprite.getV0()).overlayCoords(overlay).uv2(frontLight).normal(0, 0, 1).endVertex();
	            }
	        }

	        poseStack.popPose();
	    }
	}

	private void renderChalkQuad(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int light, float u0, float u1, float v0, float v1) {
	    if (texture == null) return;
	    VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
	    Matrix4f matrix = poseStack.last().pose();

	    float size = 0.5f;
	    int overlay = net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

	    consumer.vertex(matrix, -size, -size, 0).color(255, 255, 255, 255)
	        .uv(u1, v1).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
	    consumer.vertex(matrix, size, -size, 0).color(255, 255, 255, 255)
	        .uv(u0, v1).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
	    consumer.vertex(matrix, size, size, 0).color(255, 255, 255, 255)
	        .uv(u0, v0).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
	    consumer.vertex(matrix, -size, size, 0).color(255, 255, 255, 255)
	        .uv(u1, v0).overlayCoords(overlay).uv2(light).normal(0, 0, 1).endVertex();
	}

	private static boolean hasPlayerPurchased(Player player, ItemStack weaponOnWall) {
	    String wId = WeaponFacade.getWeaponId(weaponOnWall);
	    Set<String> purchased = PURCHASED_ITEMS.get(player.getUUID());
	    if (purchased != null && purchased.contains(wId)) return true;

	    boolean isTacz = WeaponFacade.isTaczWeapon(weaponOnWall);
	    me.cryo.zombierool.core.system.WeaponSystem.Definition def = WeaponFacade.getDefinition(weaponOnWall);

	    for (ItemStack s : player.getInventory().items) {
	        if (isTacz && WeaponFacade.isTaczWeapon(s)) {
	            String wallId = weaponOnWall.getOrCreateTag().getString("GunId");
	            String invId = s.getOrCreateTag().getString("GunId");
	            if (wallId.equals(invId)) return true;
	        } else if (!isTacz && def != null && WeaponFacade.isWeapon(s)) {
	            me.cryo.zombierool.core.system.WeaponSystem.Definition d = WeaponFacade.getDefinition(s);
	            if (d != null && d.id.replace("zombierool:", "").equals(def.id.replace("zombierool:", ""))) return true;
	        } else if (!isTacz && def == null && s.getItem() == weaponOnWall.getItem()) {
	            return true;
	        }
	    }
	    return false;
	}

	public static void markAsPurchased(Player player, String weaponId) {
	    UUID playerId = player.getUUID();
	    PURCHASED_ITEMS.computeIfAbsent(playerId, k -> new HashSet<>()).add(weaponId);
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
	        var rm = Minecraft.getInstance().getResourceManager();
	        String namespace = location.getNamespace();
	        String path = location.getPath();
	        String cleanPath = path.replace("gun/icon/", "").replace("textures/", "").replace(".png", "");
	        
	        if (cleanPath.startsWith("icon/")) {
	            cleanPath = cleanPath.substring(5);
	        }

	        Set<ResourceLocation> attempts = new HashSet<>();
	        attempts.add(new ResourceLocation(namespace, "textures/gun/icon/" + cleanPath + ".png"));
	        attempts.add(new ResourceLocation(namespace, "textures/icon/" + cleanPath + ".png"));
	        attempts.add(new ResourceLocation(namespace, "textures/item/" + cleanPath + ".png"));
	        attempts.add(new ResourceLocation(namespace, "textures/" + cleanPath + ".png"));
	        attempts.add(new ResourceLocation(namespace, cleanPath + ".png"));
	        attempts.add(new ResourceLocation(namespace, "gun/icon/" + cleanPath + ".png"));

	        for (ResourceLocation p : attempts) {
	            var res = rm.getResource(p);
	            if (res.isPresent()) {
	                try (InputStream stream = res.get().open()) {
	                    return NativeImage.read(stream);
	                } catch (Exception e) {}
	            }
	        }
	    } catch (Exception e) {}
	    return null;
	}

	private static NativeImage createChalkOutline(NativeImage original) {
	    int width = original.getWidth();
	    int height = original.getHeight();
	    
	    int thickness = Math.max(1, width / 64); 

	    int maxSize = Math.max(width, height);
	    NativeImage outline = new NativeImage(maxSize, maxSize, true);

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

	@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
	public static class ClientEvents {
	    @SubscribeEvent
	    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
	        clearAllPurchases();
	    }
	}
}