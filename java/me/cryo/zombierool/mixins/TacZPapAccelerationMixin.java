package me.cryo.zombierool.mixins;

import com.tacz.guns.client.model.BedrockGunModel;
import me.cryo.zombierool.core.system.WeaponFacade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Accelerated gun rendering skips vanilla glint; use its normal path for upgraded guns. */
@Mixin(value=BedrockGunModel.class,remap=false)
public abstract class TacZPapAccelerationMixin {
    @Redirect(method="render",at=@At(value="INVOKE",target="Lcom/tacz/guns/compat/ar/ARCompat;shouldAccelerate()Z"))
    private boolean zr$normalForFoil(){
        if(WeaponFacade.isPackAPunched(((BedrockGunModel)(Object)this).getCurrentGunItem()))return false;
        return com.tacz.guns.compat.ar.ARCompat.shouldAccelerate();
    }
}
