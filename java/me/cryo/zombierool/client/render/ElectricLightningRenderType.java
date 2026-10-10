package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/** Lightning tubes must also be visible from inside and behind the beam. */
public final class ElectricLightningRenderType extends RenderType {
    private ElectricLightningRenderType() {
        super("zr_lightning_unused", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, () -> {}, () -> {});
    }
    public static final RenderType BLUE_ARCS = create("zr_blue_lightning",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true,
            CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));
}
