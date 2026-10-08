package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.BedrockGunModel;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=BedrockModel.class,remap=false)
public abstract class TacZPapGlintMixin {
    @Redirect(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIFFFF)V",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",remap=true))
    private VertexConsumer zr$foil(MultiBufferSource.BufferSource buffers,RenderType type){
        boolean foil=(Object)this instanceof BedrockGunModel gun && WeaponFacade.isPackAPunched(gun.getCurrentGunItem());
        return ItemRenderer.getFoilBufferDirect(buffers,type,true,foil);
    }
}
