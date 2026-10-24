package me.cryo.zombierool.mixins;

import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import me.cryo.zombierool.client.TacZVisuals;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value=GunItemRendererWrapper.class,remap=false)
public class TacZCamoMixin {
    @ModifyVariable(method="lambda$renderFirstPerson$5",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1)
    private ItemStack zr$liveVisuals(ItemStack cached){
        return TacZVisuals.firstPersonStack(cached,net.minecraft.client.Minecraft.getInstance().player);
    }
}
