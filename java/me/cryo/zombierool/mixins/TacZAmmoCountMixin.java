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
        if (stack == null || stack.isEmpty()) return;
        int bonus = 0;
        WeaponSystem.Definition def = WeaponFacade.getDefinition(stack);
        int total = cir.getReturnValue();
        if (def != null && (stack.getTag() == null || !stack.getTag().getBoolean("zombierool:unmapped"))) {
            total = def.ammo.clip_size + Math.max(0, total - gunData.getAmmoAmount());
        }
        if (WeaponFacade.isPackAPunched(stack) && def != null) {
            bonus = Math.max(0, def.pap.clip_bonus);
        } else if (WeaponFacade.isPackAPunched(stack) && stack.getTag() != null && stack.getTag().getBoolean("zombierool:unmapped")) {
            bonus = Math.max(0, cir.getReturnValue());
        }
        int chamber=gunData.getBolt()==com.tacz.guns.resource.pojo.data.gun.Bolt.CLOSED_BOLT?1:0;
        cir.setReturnValue(Math.max(0,total+bonus-chamber));
    }
}
