package me.cryo.zombierool.client.renderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import me.cryo.zombierool.PerksManager;
import me.cryo.zombierool.block.DerWunderfizzBlock;
import me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class DerWunderfizzRenderer implements BlockEntityRenderer<DerWunderfizzBlockEntity> {
    private static final ResourceLocation BEAM_LOCATION = new ResourceLocation("minecraft", "textures/entity/beacon_beam.png");

    public DerWunderfizzRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DerWunderfizzBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        // FIX: Visual electrical beam for the active Wunderfizz
        BlockPos activePos = me.cryo.zombierool.handlers.KeyInputHandler.getActiveWunderfizzPosition();
        if (activePos != null && activePos.equals(entity.getBlockPos())) {
            float time = (float)entity.getLevel().getGameTime() + partialTick;
            float flicker = (float) Math.abs(Math.sin(time * 0.5f)) * 0.5f + 0.5f; 
            renderBeaconBeam(poseStack, buffer, partialTick, entity.getLevel().getGameTime(), flicker, 0.5, 2.0, 0.5);
        }

        String perkId = entity.getCurrentlyDisplayedPerk();
        if (perkId == null || perkId.equals("idle")) return;

        PerksManager.Perk perk = PerksManager.ALL_PERKS.get(perkId);
        if (perk == null || perk.getTexturePath() == null) return;

        ResourceLocation texture = new ResourceLocation(perk.getTexturePath());
        Direction facing = entity.getBlockState().getValue(DerWunderfizzBlock.FACING);

        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);

        float rot = -facing.toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(rot));

        poseStack.translate(0, 0, 0.51);

        float size = 0.5f; 
        poseStack.scale(size, size, size);
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(texture));
        Matrix4f matrix = poseStack.last().pose();

        consumer.vertex(matrix, -0.5f, -0.5f, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix,  0.5f, -0.5f, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix,  0.5f,  0.5f, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0, 0, 1).endVertex();
        consumer.vertex(matrix, -0.5f,  0.5f, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(combinedLight).normal(0, 0, 1).endVertex();

        poseStack.popPose();
    }

    private void renderBeaconBeam(PoseStack poseStack, MultiBufferSource buffer, float partialTicks, long gameTime, float alpha, double cx, double cy, double cz) {
        if (buffer instanceof MultiBufferSource.BufferSource bs) {
            bs.endBatch(); 
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BEAM_LOCATION);

        float oldFogStart = RenderSystem.getShaderFogStart();
        float oldFogEnd = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);

        float time = (float)gameTime + partialTicks;
        float angle = time * 4.0f;
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        Matrix4f matrix = poseStack.last().pose();

        float width = 0.10f;
        float height = 512.0f;
        int r = 100, g = 200, b = 255; 
        int a = (int)(alpha * 120); 

        for (int i = 0; i < 4; i++) {
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
            builder.vertex(matrix, -width, 0, 0).uv(0, 0).color(r, g, b, a).endVertex();
            builder.vertex(matrix, width, 0, 0).uv(1, 0).color(r, g, b, a).endVertex();
            builder.vertex(matrix, width, height, 0).uv(1, height).color(r, g, b, 0).endVertex();
            builder.vertex(matrix, -width, height, 0).uv(0, height).color(r, g, b, 0).endVertex();
        }

        tesselator.end();
        poseStack.popPose();

        RenderSystem.setShaderFogStart(me.cryo.zombierool.client.ClientEnvironmentEffects.fogNearPlane);
        RenderSystem.setShaderFogEnd(me.cryo.zombierool.client.ClientEnvironmentEffects.fogFarPlane);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}