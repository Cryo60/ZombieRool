package me.cryo.zombierool.mixins;

import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AttachmentDataUtils.class, remap = false)
public class TacZAmmoCountMixin {

    @Inject(method = "getAmmoCountWithAttachment", at = @At("RETURN"), cancellable = true, remap = false)
    private static void zombierool_papMagazine(ItemStack stack, GunData gunData, CallbackInfoReturnable<Integer> cir) {
        if (stack == null || stack.isEmpty() || !WeaponFacade.isPackAPunched(stack)) return;
        int bonus = 0;
        WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
        if (def != null) {
            bonus = Math.max(0, def.pap.clip_bonus);
        } else if (stack.getTag() != null && stack.getTag().getBoolean("zombierool:unmapped")) {
            bonus = Math.max(0, cir.getReturnValue());
        }
        if (bonus > 0) {
            cir.setReturnValue(cir.getReturnValue() + bonus);
        }
    }
}
