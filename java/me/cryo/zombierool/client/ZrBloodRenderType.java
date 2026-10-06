package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Stuck blood. Depth is not written, and each stack sits on its own polygon-offset
 * layer, so several puddles on the same face blend instead of flickering.
 */
public final class ZrBloodRenderType extends RenderType {
    private static final ResourceLocation TEXTURE = new ResourceLocation("zombierool", "textures/particle/blood_drop.png");
    private static final RenderType[] LAYERS = new RenderType[8];

    private ZrBloodRenderType() {
        super("zombierool_blood_dummy", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
    }

    static {
        for (int layer = 0; layer < LAYERS.length; layer++) {
            final float units = -2.0F - layer * 2.0F;
            LAYERS[layer] = create("zombierool_blood_decal_" + layer,
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    65536,
                    true,
                    true,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                            .setTextureState(new TextureStateShard(TEXTURE, false, false))
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(NO_CULL)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setWriteMaskState(COLOR_WRITE)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setLayeringState(new LayeringStateShard("zr_blood_offset_" + layer, () -> {
                                RenderSystem.polygonOffset(-1.0F, units);
                                RenderSystem.enablePolygonOffset();
                            }, () -> {
                                RenderSystem.polygonOffset(0.0F, 0.0F);
                                RenderSystem.disablePolygonOffset();
                            }))
                            .createCompositeState(true));
        }
    }

    public static final RenderType DECAL = LAYERS[0];

    public static RenderType layer(int layer) {
        return LAYERS[Math.floorMod(layer, LAYERS.length)];
    }

    public static int layerCount() {
        return LAYERS.length;
    }
}
