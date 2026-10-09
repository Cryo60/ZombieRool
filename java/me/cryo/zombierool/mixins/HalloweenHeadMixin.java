package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
@Mixin(net.minecraft.client.renderer.entity.layers.CustomHeadLayer.class)
public abstract class HalloweenHeadMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack zombierool_costume(LivingEntity entity,EquipmentSlot slot){return me.cryo.zombierool.client.HalloweenManager.visualEquipment(entity,slot);}
}
