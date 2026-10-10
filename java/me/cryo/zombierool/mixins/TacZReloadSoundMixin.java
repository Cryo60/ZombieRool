package me.cryo.zombierool.mixins;

import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import me.cryo.zombierool.integration.TacZReloadSpeed;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** TacZ's full reload tracks bypass ObjectAnimationRunner's sound keyframes. */
@Mixin(value=SoundPlayManager.class,remap=false)
public class TacZReloadSoundMixin {
    @Redirect(method="playReloadSound", at=@At(value="INVOKE",target="Lcom/tacz/guns/client/sound/SoundPlayManager;playClientSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceLocation;FFI)Lcom/tacz/guns/client/sound/GunSoundInstance;"),remap=false,require=2)
    private static GunSoundInstance zombierool$reloadTrack(Entity shooter,ResourceLocation sound,float volume,float pitch,int distance) {
        if(shooter instanceof LivingEntity living) pitch *= (float)(1.0+TacZReloadSpeed.bonus(living,living.getMainHandItem()));
        return SoundPlayManager.playClientSound(shooter,sound,volume,pitch,distance);
    }
}
