package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.BedrockGunModel;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=BedrockModel.class,remap=false)
public abstract class TacZPapGlintMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF)V",at=@At("RETURN"),require=1)
    private void zr$surfaceCamo(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.world.item.ItemDisplayContext context,RenderType type,int light,int overlay,float red,float green,float blue,float alpha,CallbackInfo info){
        // TacZ flushes only its base material. Flush our additional glint while
        // the hand projection is still active; deferring it draws stray geometry
        // over the world on later frames, especially with shaders.
        if((Object)this instanceof BedrockGunModel gun && WeaponFacade.isPackAPunched(gun.getCurrentGunItem())){
            var buffers=net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource();
            buffers.endBatch(RenderType.glintDirect());buffers.endBatch(RenderType.entityGlintDirect());
        }
        me.cryo.zombierool.client.TacZCamoOverlay.render((BedrockModel)(Object)this,pose,context,light,overlay);
    }
    @Redirect(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",remap=true))
    private VertexConsumer zr$foil(MultiBufferSource.BufferSource buffers,RenderType type){
        return buffers.getBuffer(type);
    }
}
