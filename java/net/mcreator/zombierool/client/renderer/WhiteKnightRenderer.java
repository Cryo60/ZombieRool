package net.mcreator.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.HumanoidModel;
// Removed unnecessary imports for DefaultVertexFormat, VertexFormat, GameRenderer, RenderStateShard
// as we will use higher-level RenderType factory methods.


import net.mcreator.zombierool.entity.WhiteKnightEntity;

public class WhiteKnightRenderer extends HumanoidMobRenderer<WhiteKnightEntity, HumanoidModel<WhiteKnightEntity>> {

    public WhiteKnightRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer(this, new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidModel(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        this.addLayer(new EyesLayer<WhiteKnightEntity, HumanoidModel<WhiteKnightEntity>>(this) {
            @Override
            public RenderType renderType() {
                // Eyes can still use RenderType.eyes for a distinct glow
                return RenderType.eyes(new ResourceLocation("zombierool:textures/entities/knight_eyes.png"));
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(WhiteKnightEntity entity) {
        return new ResourceLocation("zombierool:textures/entities/13135c346c6ddc5e.png");
    }

    // Override the getRenderType method to return a translucent render type
    @Override
    protected RenderType getRenderType(WhiteKnightEntity entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        // Use the built-in translucent render type.
        // For a ghostly effect, this is typically sufficient.
        // It automatically handles transparency and appropriate rendering states.
        return RenderType.entityTranslucent(this.getTextureLocation(entity));

        // If you also need to disable back-face culling (to see both sides of the transparent model),
        // you could use:
        // return RenderType.entityTranslucentCull(this.getTextureLocation(entity));
    }
}