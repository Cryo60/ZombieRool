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

    @SubscribeEvent
    public static void registerAmmoModels(ModelEvent.RegisterAdditional event) {
        event.register(new ResourceLocation(ZombieroolMod.MODID, "item/rpg_empty"));
        for (int stage = 1; stage <= 4; stage++) {
            event.register(new ResourceLocation(ZombieroolMod.MODID, "item/needler_empty_" + stage));
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelEvent.ModifyBakingResult event) {
        SpriteCamoTextures.clear();
        for (Item weapon : ZRRegistry.GUN_ITEMS) {
            ResourceLocation reg = ForgeRegistries.ITEMS.getKey(weapon);
            if (reg != null) {
                ModelResourceLocation mrl = new ModelResourceLocation(reg, "inventory");
                BakedModel originalModel = event.getModels().get(mrl);
                if (originalModel != null) {
                    Map<Integer, BakedModel> ammoModels = new HashMap<>();
                    if (reg.getPath().equals("rpg")) {
                        BakedModel empty = event.getModels().get(new ResourceLocation(reg.getNamespace(), "item/rpg_empty"));
                        if (empty != null) ammoModels.put(4, empty);
                    } else if (reg.getPath().equals("needler")) {
                        for (int stage = 1; stage <= 4; stage++) {
                            BakedModel depleted = event.getModels().get(new ResourceLocation(reg.getNamespace(), "item/needler_empty_" + stage));
                            if (depleted != null) ammoModels.put(stage, depleted);
                        }
                    }
                    event.getModels().put(mrl, new CamoWrapperModel(originalModel, ammoModels));
                }
            }
        }
    }

    public static class CamoWrapperModel implements BakedModel {
        private final BakedModel original;
        private final ItemOverrides overrides;
        private record TextureKey(BakedModel base, String id, boolean skin) {}
        private final Map<TextureKey, BakedModel> textures = new LinkedHashMap<>(32, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<TextureKey, BakedModel> entry) { return size() > 128; }
        };
        private BakedModel textured(BakedModel base, String id, boolean skin) {
            return textures.computeIfAbsent(new TextureKey(base, id, skin),
                    key -> new ActiveTextureModel(base, id, skin ? "item/skins/" : "item/camos/", skin));
        }

        public CamoWrapperModel(BakedModel original, Map<Integer, BakedModel> ammoModels) {
            this.original = original;
            this.overrides = new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                    BakedModel map = me.cryo.zombierool.client.MapWeaponPackClient.model(stack,entity);if(map!=null)return map;
                    BakedModel resolved = original.getOverrides().resolve(original, stack, level, entity, seed);
                    if (resolved == null) resolved = original;
                    // Preserve resource-pack overrides; resolve the built-in ammo variants
                    // directly if the baked predicate table did not select one.
                    if (resolved == original && !ammoModels.isEmpty()
                            && stack.getItem() instanceof me.cryo.zombierool.api.IReloadable reloadable) {
                        int stage = me.cryo.zombierool.client.WeaponTextureState.depletedStage(
                                reloadable.getAmmo(stack), reloadable.getMaxAmmo(stack));
                        resolved = ammoModels.getOrDefault(stage, original);
                    }

                    if (stack.hasTag()) {
                        String skinId = stack.getTag().getString("zr_skin");
                        if (!skinId.isEmpty()) {
                            return textured(resolved, skinId, true);
                        }
                        String camoId = stack.getTag().getString("zr_camo");
                        if (!camoId.isEmpty()) {
                            return textured(resolved, camoId, false);
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

    private static final class SpritePass extends ActiveTextureModel {
        private final BakedModel original;private final String camo;private final ResourceLocation source;
        SpritePass(BakedModel original,String camo,ResourceLocation source){super(original,camo,"",false);this.original=original;this.camo=camo;this.source=source;}
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){return List.of(this);}
        @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random){
            var result=new ArrayList<BakedQuad>();
            for(var quad:original.getQuads(state,side,random)){
                boolean front=quad.getDirection()==Direction.NORTH||quad.getDirection()==Direction.SOUTH;
                if(source==null){if(!front)result.add(quad);continue;}
                if(!front||!quad.getSprite().contents().name().equals(source))continue;
                var old=quad.getSprite();int[] vertices=quad.getVertices().clone();
                for(int i=0;i<4;i++){int offset=i*8;float u=Float.intBitsToFloat(vertices[offset+4]),v=Float.intBitsToFloat(vertices[offset+5]);vertices[offset+4]=Float.floatToRawIntBits((u-old.getU0())/(old.getU1()-old.getU0()));vertices[offset+5]=Float.floatToRawIntBits((v-old.getV0())/(old.getV1()-old.getV0()));}
                result.add(new BakedQuad(vertices,quad.getTintIndex(),quad.getDirection(),quad.getSprite(),quad.isShade()));
            }
            return result;
        }
        @Override public List<net.minecraft.client.renderer.RenderType> getRenderTypes(ItemStack stack,boolean fabulous){
            if(source==null)return List.of(net.minecraft.client.renderer.RenderType.cutout());
            var texture=SpriteCamoTextures.texture(source,camo);
            return List.of(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture));
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

        private List<BakedModel> spritePasses;
        @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){
            if(isSkin)return List.of(this);
            if(spritePasses==null){
                Set<ResourceLocation> sprites=new LinkedHashSet<>();
                for(Direction face:Direction.values())for(var quad:base.getQuads(null,face,RandomSource.create(42)))if(quad.getDirection()==Direction.NORTH||quad.getDirection()==Direction.SOUTH)sprites.add(quad.getSprite().contents().name());
                for(var quad:base.getQuads(null,null,RandomSource.create(42)))if(quad.getDirection()==Direction.NORTH||quad.getDirection()==Direction.SOUTH)sprites.add(quad.getSprite().contents().name());
                var passes=new ArrayList<BakedModel>();passes.add(new SpritePass(base,textureId,null));
                for(var sprite:sprites){if(SpriteCamoTextures.texture(sprite,textureId)==null)return List.of(base);passes.add(new SpritePass(base,textureId,sprite));}
                spritePasses=List.copyOf(passes);
            }
            return spritePasses;
        }

        private List<BakedQuad> buildQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            List<BakedQuad> quads = new ArrayList<>();
            String cleanName = textureId.replace("camo_", "");
            ResourceLocation spriteLocation = new ResourceLocation(ZombieroolMod.MODID, pathPrefix + cleanName);
            TextureAtlasSprite customSprite = Minecraft.getInstance().getModelManager()
                .getAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)
                .getSprite(spriteLocation);

            for (BakedQuad quad : base.getQuads(state, side, rand)) {
                if (customSprite.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
                    quads.add(quad);
                    continue;
                }
                if (isSkin) {
                    quads.add(remapQuad(quad, customSprite));
                } else {
                    // Renderers using the Forge item passes receive the composed
                    // faces; direct quad consumers retain the original geometry.
                    quads.add(quad);
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
