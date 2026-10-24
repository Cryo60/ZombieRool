package me.cryo.zombierool.mixins;

import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.api.entity.ReloadState;
import me.cryo.zombierool.integration.TacZReloadSpeed;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntityReload.class, remap = false)
public class TacZReloadOperatorMixin {
    @Shadow @Final private LivingEntity shooter;
    @Shadow @Final private ShooterDataHolder data;
    @Unique private long zombierool$lastTick = Long.MIN_VALUE;
    @Unique private net.minecraft.world.item.ItemStack zombierool$reloadGun;
    @Unique private net.minecraft.resources.ResourceLocation zombierool$reloadGunId;
    @Unique private boolean zombierool$running,zombierool$cancelled;
    @Unique private int zombierool$initialAmmo;

    @Inject(method="reload()V",at=@At("HEAD"),remap=false)
    private void zombierool$remember(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
        zombierool$discardReplaced();
        if(data.reloadTimestamp!=-1)return;
        zombierool$running=false;zombierool$cancelled=false;
        zombierool$lastTick=Long.MIN_VALUE;
        zombierool$reloadGun=data.currentGunItem==null?null:data.currentGunItem.get();
        var gun=zombierool$reloadGun==null?null:com.tacz.guns.api.item.IGun.getIGunOrNull(zombierool$reloadGun);
        zombierool$reloadGunId=gun==null?null:gun.getGunId(zombierool$reloadGun);
        if(gun!=null)zombierool$initialAmmo=me.cryo.zombierool.core.system.WeaponFacade.getAmmo(zombierool$reloadGun)+me.cryo.zombierool.core.system.WeaponFacade.getReserve(zombierool$reloadGun);
    }
    @Inject(method="cancelReload()V",at=@At("HEAD"),remap=false)
    private void zombierool$cancel(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){zombierool$cancelled=true;}
    @Unique private boolean zombierool$discardReplaced(){
        if(data.reloadTimestamp==-1)return false;
        var stack=data.currentGunItem==null?null:data.currentGunItem.get();
        var gun=stack==null?null:com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
        if(stack==zombierool$reloadGun&&gun!=null&&java.util.Objects.equals(zombierool$reloadGunId,gun.getGunId(stack)))return false;
        zombierool$running=false;zombierool$cancelled=true;
        data.reloadTimestamp=-1;data.reloadStateType=new ReloadState().getStateType();return true;
    }
    @Inject(method="tickReloadState()Lcom/tacz/guns/api/entity/ReloadState;",at=@At("RETURN"),remap=false)
    private void zombierool$capacity(CallbackInfoReturnable<ReloadState> cir){
        if(!zombierool$running||zombierool$cancelled||data.currentGunItem==null)return;
        var stack=data.currentGunItem.get();
        if(!me.cryo.zombierool.integration.TacZManualReload.manual(stack))return;
        int ammo=me.cryo.zombierool.core.system.WeaponFacade.getAmmo(stack),max=me.cryo.zombierool.core.system.WeaponFacade.getMaxAmmo(stack);
        if(ammo>max){
            me.cryo.zombierool.core.system.WeaponFacade.setAmmo(stack,max);
            int reserve=me.cryo.zombierool.core.system.WeaponFacade.getReserve(stack);
            me.cryo.zombierool.core.system.WeaponFacade.setReserve(stack,Math.max(reserve,Math.min(zombierool$initialAmmo-max,reserve+ammo-max)));
        }
        if(!cir.getReturnValue().getStateType().isReloading()){
            if(!me.cryo.zombierool.integration.TacZBoltReloadPlan.supports(stack))me.cryo.zombierool.integration.TacZManualReload.complete(shooter);
            zombierool$running=false;
        }
    }

    @Inject(method = "tickReloadState()Lcom/tacz/guns/api/entity/ReloadState;", at = @At("HEAD"), cancellable=true, remap = false, require = 1)
    private void zombierool$speedReload(CallbackInfoReturnable<ReloadState> cir) {
        if(zombierool$discardReplaced()){cir.setReturnValue(new ReloadState());return;}
        if (shooter == null || data.reloadTimestamp == -1 || data.currentGunItem == null) return;
        zombierool$running=true;
        long tick = shooter.level().getGameTime();
        if (zombierool$lastTick == tick) return;
        zombierool$lastTick = tick;
        data.reloadTimestamp -= Math.round(50.0 * TacZReloadSpeed.bonus(shooter, data.currentGunItem.get()));
    }
}
