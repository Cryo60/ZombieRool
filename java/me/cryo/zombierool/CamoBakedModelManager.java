package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CamoBakedModelManager {

    private static final Map<ResourceLocation, MaskData> OPAQUE_CACHE = new HashMap<>();

    private static class MaskData {
        boolean[][] opaque;
        int width, height;
    }

    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        for (Item weapon : ZRRegistry.GUN_ITEMS) {
            ResourceLocation reg = ForgeRegistries.ITEMS.getKey(weapon);
            if (reg != null) {
                ModelResourceLocation mrl = new ModelResourceLocation(reg, "inventory");
                BakedModel originalModel = event.getModels().get(mrl);
                if (originalModel != null) {
                    event.getModels().put(mrl, new CamoWrapperModel(originalModel));
                }
            }
        }
    }

    private static MaskData getOpaqueMap(ResourceLocation spriteName) {
        if (OPAQUE_CACHE.containsKey(spriteName)) return OPAQUE_CACHE.get(spriteName);
        
        MaskData data = new MaskData();
        ResourceLocation fileLoc = new ResourceLocation(spriteName.getNamespace(), "textures/" + spriteName.getPath() + ".png");

        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(fileLoc);
            if (resource.isPresent()) {
                try (NativeImage img = NativeImage.read(resource.get().open())) {
                    data.width = img.getWidth();
                    data.height = img.getHeight();
                    data.opaque = new boolean[data.width][data.height];

                    for (int y = 0; y < data.height; y++) {
                        for (int x = 0; x < data.width; x++) {
                            int alpha = (img.getPixelRGBA(x, y) >> 24) & 0xFF;
                            data.opaque[x][y] = alpha > 25;
                        }
                    }
                }
            } else {
                ZombieroolMod.LOGGER.warn("[ZombieRool] Impossible de trouver le fichier texture pour le masque: " + fileLoc);
                data.width = 0;
            }
        } catch (Exception e) {
            ZombieroolMod.LOGGER.error("[ZombieRool] Erreur de lecture de la texture: " + fileLoc, e);
            data.width = 0;
        }

        OPAQUE_CACHE.put(spriteName, data);
        return data;
    }

    public static class CamoWrapperModel implements BakedModel {
        private final BakedModel original;
        private final ItemOverrides overrides;

        public CamoWrapperModel(BakedModel original) {
            this.original = original;
            this.overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                    BakedModel resolved = original.getOverrides().resolve(original, stack, level, entity, seed);
                    if (resolved == null) resolved = original;

                    if (stack.hasTag()) {
                        String skinId = stack.getTag().getString("zr_skin");
                        if (!skinId.isEmpty()) {
                            return new ActiveTextureModel(resolved, skinId, "item/skins/", true);
                        }
                        String camoId = stack.getTag().getString("zr_camo");
                        if (!camoId.isEmpty()) {
                            return new ActiveTextureModel(resolved, camoId, "item/camos/", false);
                        }
                    }
                    return resolved;
                }
            };
        }

        @Override public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) { return original.getQuads(state, side, rand); }
        @Override public boolean useAmbientOcclusion() { return original.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return original.isGui3d(); }
        @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return original.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
        @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() { return original.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return overrides; }
        @Override
        public List<net.minecraft.client.renderer.RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
            return java.util.List.of(net.minecraft.client.renderer.RenderType.cutout());
        }
    }

    public static class ActiveTextureModel implements BakedModel {
        private final BakedModel base;
        private final String textureId;
        private final String pathPrefix;
        private final boolean isSkin;
        private final Map<Direction, List<BakedQuad>> quadCache = new HashMap<>();
        private List<BakedQuad> unculledQuadCache;

        public ActiveTextureModel(BakedModel base, String textureId, String pathPrefix, boolean isSkin) {
            this.base = base;
            this.textureId = textureId;
            this.pathPrefix = pathPrefix;
            this.isSkin = isSkin;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            if (side == null) {
                if (unculledQuadCache != null) return unculledQuadCache;
                unculledQuadCache = buildQuads(state, null, rand);
                return unculledQuadCache;
            } else {
                return quadCache.computeIfAbsent(side, s -> buildQuads(state, s, rand));
            }
        }

        private List<BakedQuad> buildQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            List<BakedQuad> quads = new ArrayList<>();
            String cleanName = textureId.replace("camo_", "");
            ResourceLocation spriteLocation = new ResourceLocation(ZombieroolMod.MODID, pathPrefix + cleanName);
            TextureAtlasSprite customSprite = Minecraft.getInstance().getModelManager()
                .getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .getSprite(spriteLocation);

            for (BakedQuad quad : base.getQuads(state, side, rand)) {
                if (isSkin) {
                    quads.add(remapQuad(quad, customSprite));
                } else {
                    ResourceLocation spriteName = quad.getSprite().contents().name();
                    if (quad.getDirection() == Direction.SOUTH || quad.getDirection() == Direction.NORTH) {
                        MaskData maskData = getOpaqueMap(spriteName);
                        if (maskData.width == 0) {
                            quads.add(quad);
                            continue;
                        }

                        float zDepth = Float.intBitsToFloat(quad.getVertices()[2]);
                        zDepth += quad.getDirection() == Direction.SOUTH ? 0.001f : -0.001f;

                        for (int py = 0; py < maskData.height; py++) {
                            int startX = -1;
                            for (int px = 0; px < maskData.width; px++) {
                                if (maskData.opaque[px][py]) {
                                    if (startX == -1) startX = px;
                                } else {
                                    if (startX != -1) {
                                        quads.add(createRectQuad(startX, py, px - startX, 1, maskData.width, maskData.height, zDepth, quad.getDirection(), customSprite, quad.getTintIndex()));
                                        startX = -1;
                                    }
                                }
                            }
                            if (startX != -1) {
                                quads.add(createRectQuad(startX, py, maskData.width - startX, 1, maskData.width, maskData.height, zDepth, quad.getDirection(), customSprite, quad.getTintIndex()));
                            }
                        }
                    } else {
                        quads.add(quad);
                    }
                }
            }
            return quads;
        }

        private BakedQuad remapQuad(BakedQuad quad, TextureAtlasSprite newSprite) {
            TextureAtlasSprite oldSprite = quad.getSprite();
            int[] vertexData = Arrays.copyOf(quad.getVertices(), quad.getVertices().length);
            
            for (int i = 0; i < 4; i++) {
                int offset = i * 8;
                float u = Float.intBitsToFloat(vertexData[offset + 4]);
                float v = Float.intBitsToFloat(vertexData[offset + 5]);

                float normU = (u - oldSprite.getU0()) / (oldSprite.getU1() - oldSprite.getU0());
                float normV = (v - oldSprite.getV0()) / (oldSprite.getV1() - oldSprite.getV0());

                float newU = newSprite.getU0() + normU * (newSprite.getU1() - newSprite.getU0());
                float newV = newSprite.getV0() + normV * (newSprite.getV1() - newSprite.getV0());

                vertexData[offset + 4] = Float.floatToRawIntBits(newU);
                vertexData[offset + 5] = Float.floatToRawIntBits(newV);
            }
            return new BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), newSprite, quad.isShade());
        }

        private BakedQuad createRectQuad(int px, int py, int rectW, int rectH, int totalW, int totalH, float z, Direction face, TextureAtlasSprite customSprite, int tintIndex) {
            int[] vertexData = new int[32];

            float x_min = px / (float) totalW;
            float x_max = (px + rectW) / (float) totalW;
            float y_max = (totalH - py) / (float) totalH;
            float y_min = (totalH - (py + rectH)) / (float) totalH;

            float u_min = customSprite.getU0() + x_min * (customSprite.getU1() - customSprite.getU0());
            float u_max = customSprite.getU0() + x_max * (customSprite.getU1() - customSprite.getU0());
            float v_min = customSprite.getV0() + (1.0f - y_max) * (customSprite.getV1() - customSprite.getV0());
            float v_max = customSprite.getV0() + (1.0f - y_min) * (customSprite.getV1() - customSprite.getV0());

            int color = -1;
            int packedNormal = calculatePackedNormal(face);

            if (face == Direction.SOUTH) {
                putVertex(vertexData, 0, x_min, y_max, z, color, u_min, v_min, packedNormal);
                putVertex(vertexData, 1, x_min, y_min, z, color, u_min, v_max, packedNormal);
                putVertex(vertexData, 2, x_max, y_min, z, color, u_max, v_max, packedNormal);
                putVertex(vertexData, 3, x_max, y_max, z, color, u_max, v_min, packedNormal);
            } else {
                putVertex(vertexData, 0, x_max, y_max, z, color, u_max, v_min, packedNormal);
                putVertex(vertexData, 1, x_max, y_min, z, color, u_max, v_max, packedNormal);
                putVertex(vertexData, 2, x_min, y_min, z, color, u_min, v_max, packedNormal);
                putVertex(vertexData, 3, x_min, y_max, z, color, u_min, v_min, packedNormal);
            }

            return new BakedQuad(vertexData, tintIndex, face, customSprite, false);
        }

        private void putVertex(int[] data, int index, float x, float y, float z, int color, float u, float v, int normal) {
            int offset = index * 8;
            data[offset]     = Float.floatToRawIntBits(x);
            data[offset + 1] = Float.floatToRawIntBits(y);
            data[offset + 2] = Float.floatToRawIntBits(z);
            data[offset + 3] = color;
            data[offset + 4] = Float.floatToRawIntBits(u);
            data[offset + 5] = Float.floatToRawIntBits(v);
            data[offset + 6] = 0;
            data[offset + 7] = normal;
        }

        private int calculatePackedNormal(Direction dir) {
            float x = dir.getStepX();
            float y = dir.getStepY();
            float z = dir.getStepZ();
            int nx = (int) (x * 127.0F) & 255;
            int ny = (int) (y * 127.0F) & 255;
            int nz = (int) (z * 127.0F) & 255;
            return nx | (ny << 8) | (nz << 16);
        }

        @Override public boolean useAmbientOcclusion() { return base.useAmbientOcclusion(); }
        @Override public boolean isGui3d() { return base.isGui3d(); }
        @Override public boolean usesBlockLight() { return base.usesBlockLight(); }
        @Override public boolean isCustomRenderer() { return base.isCustomRenderer(); }
        @Override public TextureAtlasSprite getParticleIcon() { return base.getParticleIcon(); }
        @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() { return base.getTransforms(); }
        @Override public ItemOverrides getOverrides() { return base.getOverrides(); }
        @Override
        public List<net.minecraft.client.renderer.RenderType> getRenderTypes(ItemStack itemStack, boolean fabulous) {
            return java.util.List.of(net.minecraft.client.renderer.RenderType.cutout());
        }
    }
}