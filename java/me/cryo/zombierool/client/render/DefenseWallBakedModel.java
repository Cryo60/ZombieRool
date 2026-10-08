package me.cryo.zombierool.client.render;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.system.DefenseWallSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DefenseWallBakedModel implements BakedModel {
    private final BakedModel original;

    public DefenseWallBakedModel(BakedModel original) {
        this.original = original;
    }

    @SubscribeEvent
    public static void onBake(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        wrap(models, DefenseWallSystem.MAIN_BLOCK.get());
        wrap(models, DefenseWallSystem.DUMMY_BLOCK.get());
    }

    private static void wrap(Map<ResourceLocation, BakedModel> models, Block block) {
        Map<ResourceLocation, BakedModel> originals = new HashMap<>();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ResourceLocation visualKey = BlockModelShaper.stateToModelLocation(visualState(state));
            originals.putIfAbsent(visualKey, models.get(visualKey));
        }
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BakedModel original = originals.get(BlockModelShaper.stateToModelLocation(visualState(state)));
            if (original != null) {
                models.put(BlockModelShaper.stateToModelLocation(state), new DefenseWallBakedModel(original));
            }
        }
    }

    private static BlockState visualState(BlockState state) {
        if (state.hasProperty(DefenseWallSystem.DefenseWallBlock.HAS_MIMIC)) {
            state = state.setValue(DefenseWallSystem.DefenseWallBlock.HAS_MIMIC, false);
        }
        if (state.hasProperty(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)) {
            state = state.setValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN, false);
        }
        if (state.hasProperty(DefenseWallSystem.DefenseWallDummyBlock.HAS_MIMIC)) {
            state = state.setValue(DefenseWallSystem.DefenseWallDummyBlock.HAS_MIMIC, false);
        }
        return state;
    }

    /** Resolve the saved master even when an old world's dummy has no cached model data yet. */
    @Override
    public ModelData getModelData(net.minecraft.world.level.BlockAndTintGetter view,
            net.minecraft.core.BlockPos pos, BlockState state, ModelData incoming) {
        net.minecraft.core.BlockPos masterPos = pos;
        if (state.getBlock() instanceof DefenseWallSystem.DefenseWallDummyBlock dummy) {
            masterPos = dummy.getMainPos(pos, state);
        }
        if (view.getBlockEntity(masterPos) instanceof DefenseWallSystem.DefenseWallBlockEntity wall) {
            ModelData.Builder data = ModelData.builder();
            BlockState mimic = wall.getMimic();
            if (mimic != null) data.with(DefenseWallSystem.MIMIC, mimic);
            BlockState masterState = wall.getBlockState();
            data.with(DefenseWallSystem.HIDE, masterState.hasProperty(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)
                    && masterState.getValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN));
            return data.build();
        }
        return incoming;
    }
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable net.minecraft.client.renderer.RenderType renderType) {
        if (state == null || Boolean.TRUE.equals(data.get(DefenseWallSystem.HIDE))) return List.of();
        if (state.getBlock() instanceof DefenseWallSystem.DefenseWallBlock && state.getValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)) {
            return List.of();
        }
        BlockState mimic = data.get(DefenseWallSystem.MIMIC);
        if (mimic != null && isClosed(state)) {
            BakedModel mimicModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(mimic);
            ChunkRenderTypeSet types = mimicModel.getRenderTypes(mimic, rand, ModelData.EMPTY);
            if (renderType != null && !types.contains(renderType)) return List.of();
            return mimicModel.getQuads(mimic, side, rand);
        }
        int dx = 0;
        int dy = 0;
        Direction facing = Direction.NORTH;
        BakedModel source = this.original;
        BlockState sourceState = state;
        if (state.getBlock() instanceof DefenseWallSystem.DefenseWallDummyBlock) {
            DefenseWallSystem.WallPart part = state.getValue(DefenseWallSystem.DefenseWallDummyBlock.PART);
            dx = part.dx;
            dy = part.dy;
            facing = state.getValue(DefenseWallSystem.DefenseWallDummyBlock.FACING);
            sourceState = mainVisual(state);
            BakedModel lookedUp = Minecraft.getInstance().getBlockRenderer().getBlockModel(sourceState);
            source = lookedUp instanceof DefenseWallBakedModel wrapped ? wrapped.original : lookedUp;
        } else if (state.hasProperty(DefenseWallSystem.DefenseWallBlock.FACING)) {
            facing = state.getValue(DefenseWallSystem.DefenseWallBlock.FACING);
        }
        List<BakedQuad> quads = source.getQuads(sourceState, side, rand);
        if (quads.isEmpty()) return quads;
        Direction right = facing.getClockWise();
        float ox = right.getStepX() * dx;
        float oy = dy;
        float oz = right.getStepZ() * dx;
        TextureAtlasSprite sprite = mimic == null ? null : Minecraft.getInstance().getBlockRenderer().getBlockModel(mimic).getParticleIcon();
        List<BakedQuad> kept = new ArrayList<>();
        for (BakedQuad quad : quads) {
            BakedQuad shifted = shiftQuad(quad, ox, oy, oz);
            if (!intersectsCell(shifted)) continue;
            kept.add(sprite == null ? shifted : remapQuad(shifted, sprite));
        }
        return kept;
    }

    private static BlockState mainVisual(BlockState dummy) {
        return DefenseWallSystem.MAIN_BLOCK.get().defaultBlockState()
                .setValue(DefenseWallSystem.DefenseWallBlock.FACING, dummy.getValue(DefenseWallSystem.DefenseWallDummyBlock.FACING))
                .setValue(DefenseWallSystem.DefenseWallBlock.STAGE, dummy.getValue(DefenseWallSystem.DefenseWallDummyBlock.STAGE))
                .setValue(DefenseWallSystem.DefenseWallBlock.HAS_MIMIC, false)
                .setValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN, false);
    }

    private static BakedQuad shiftQuad(BakedQuad quad, float ox, float oy, float oz) {
        if (ox == 0.0F && oy == 0.0F && oz == 0.0F) return quad;
        int[] vertexData = java.util.Arrays.copyOf(quad.getVertices(), quad.getVertices().length);
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float x = Float.intBitsToFloat(vertexData[offset]) - ox;
            float y = Float.intBitsToFloat(vertexData[offset + 1]) - oy;
            float z = Float.intBitsToFloat(vertexData[offset + 2]) - oz;
            vertexData[offset] = Float.floatToRawIntBits(x);
            vertexData[offset + 1] = Float.floatToRawIntBits(y);
            vertexData[offset + 2] = Float.floatToRawIntBits(z);
        }
        return new BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), false);
    }

    private static boolean intersectsCell(BakedQuad quad) {
        float minX = 99.0F, minY = 99.0F, minZ = 99.0F;
        float maxX = -99.0F, maxY = -99.0F, maxZ = -99.0F;
        int[] vertexData = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float x = Float.intBitsToFloat(vertexData[offset]);
            float y = Float.intBitsToFloat(vertexData[offset + 1]);
            float z = Float.intBitsToFloat(vertexData[offset + 2]);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        return maxX > -0.02F && minX < 1.02F && maxY > -0.02F && minY < 1.02F && maxZ > -0.02F && minZ < 1.02F;
    }

    private static boolean isClosed(BlockState state) {
        if (state.hasProperty(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)
                && state.getValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)) {
            return false;
        }
        if (state.hasProperty(DefenseWallSystem.DefenseWallBlock.STAGE)) {
            return state.getValue(DefenseWallSystem.DefenseWallBlock.STAGE) >= 7;
        }
        if (state.hasProperty(DefenseWallSystem.DefenseWallDummyBlock.STAGE)) {
            return state.getValue(DefenseWallSystem.DefenseWallDummyBlock.STAGE) >= 7;
        }
        return false;
    }

    private static BakedQuad remapQuad(BakedQuad quad, TextureAtlasSprite newSprite) {
        TextureAtlasSprite oldSprite = quad.getSprite();
        float spanU = oldSprite.getU1() - oldSprite.getU0();
        float spanV = oldSprite.getV1() - oldSprite.getV0();
        if (spanU == 0.0F || spanV == 0.0F) return quad;
        int[] vertexData = java.util.Arrays.copyOf(quad.getVertices(), quad.getVertices().length);
        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float v = Float.intBitsToFloat(vertexData[offset + 5]);
            float normU = (u - oldSprite.getU0()) / spanU;
            float normV = (v - oldSprite.getV0()) / spanV;
            vertexData[offset + 4] = Float.floatToRawIntBits(newSprite.getU0() + normU * (newSprite.getU1() - newSprite.getU0()));
            vertexData[offset + 5] = Float.floatToRawIntBits(newSprite.getV0() + normV * (newSprite.getV1() - newSprite.getV0()));
        }
        return new BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), newSprite, false);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        if (Boolean.TRUE.equals(data.get(DefenseWallSystem.HIDE))) return ChunkRenderTypeSet.none();
        if (state.getBlock() instanceof DefenseWallSystem.DefenseWallBlock && state.getValue(DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)) {
            return ChunkRenderTypeSet.none();
        }
        BlockState mimic = data.get(DefenseWallSystem.MIMIC);
        if (mimic != null && isClosed(state)) {
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(mimic).getRenderTypes(mimic, rand, ModelData.EMPTY);
        }
        if (state.getBlock() instanceof DefenseWallSystem.DefenseWallDummyBlock) {
            BlockState main = mainVisual(state);
            BakedModel lookedUp = Minecraft.getInstance().getBlockRenderer().getBlockModel(main);
            BakedModel source = lookedUp instanceof DefenseWallBakedModel wrapped ? wrapped.original : lookedUp;
            return source.getRenderTypes(main, rand, ModelData.EMPTY);
        }
        return original.getRenderTypes(state, rand, ModelData.EMPTY);
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState mimic = data.get(DefenseWallSystem.MIMIC);
        if (mimic != null) {
            return Minecraft.getInstance().getBlockRenderer().getBlockModel(mimic).getParticleIcon(ModelData.EMPTY);
        }
        return original.getParticleIcon();
    }

    @Override public boolean useAmbientOcclusion() { return false; }
    @Override public boolean isGui3d() { return original.isGui3d(); }
    @Override public boolean usesBlockLight() { return original.usesBlockLight(); }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public TextureAtlasSprite getParticleIcon() { return original.getParticleIcon(); }
    @Override public net.minecraft.client.renderer.block.model.ItemTransforms getTransforms() { return original.getTransforms(); }
    @Override public ItemOverrides getOverrides() { return original.getOverrides(); }
}
