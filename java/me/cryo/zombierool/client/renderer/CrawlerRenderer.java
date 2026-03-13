package me.cryo.zombierool.client.renderer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.client.model.ModelCrawler;
import me.cryo.zombierool.core.manager.DynamicResourceManager; 

public class CrawlerRenderer extends MobRenderer<CrawlerEntity, ModelCrawler<CrawlerEntity>> {
    public CrawlerRenderer(EntityRendererProvider.Context context) {
        super(context, new ModelCrawler<>(context.bakeLayer(ModelLayers.SPIDER)), 0.5f);
    }
    @Override
	public ResourceLocation getTextureLocation(CrawlerEntity entity) {
        String customSkin = entity.getCustomSkin();
        if (customSkin != null && !customSkin.isEmpty()) {
            ResourceLocation dyn = DynamicResourceManager.getClientSkin("crawler", customSkin);
            if (dyn != null) return dyn;
        }
	    if (entity.hasHalloweenSkin()) {
	        return new ResourceLocation("zombierool:textures/entities/halloween_crawler.png");
	    }
	    return new ResourceLocation("zombierool:textures/entities/crawler.png");
	}
    @Override
	public void render(CrawlerEntity entity, float yaw, float partialTicks, PoseStack matrixStack,
	                   MultiBufferSource buffer, int packedLight) {
	    if (entity.isDeadOrDying() && entity.isHeadshotDeath()) {
	        boolean headVisible = this.model.head.visible;
	        this.model.head.visible = false;
	        super.render(entity, yaw, partialTicks, matrixStack, buffer, packedLight);
	        this.model.head.visible = headVisible;
	    } else {
	        super.render(entity, yaw, partialTicks, matrixStack, buffer, packedLight);
	    }
	}
}