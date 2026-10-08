package me.cryo.zombierool.mixins;

import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=SoundPlayManager.class,remap=false)
public abstract class TacZPapSoundMixin {
    @Inject(method={"playShootSound","playSilenceSound"},at=@At("TAIL"))
    private static void zr$overlay(LivingEntity shooter,GunDisplayInstance display,GunData data,CallbackInfo ci){
        var stack=shooter.getMainHandItem();
        if(!WeaponFacade.isPackAPunched(stack))return;
        var definition=WeaponFacade.getDefinition(stack);
        if(definition!=null && java.util.Set.of("m40a3","deagle","kar98k","barret","fg42","ppsh41","intervention","usp45","m14","m1garand","gewehr43").contains(definition.id.replace("zombierool:","")))return;
        if(definition!=null && (java.util.Set.of("RAYGUN","ROCKET","PROJECTILE").contains(definition.ballistics.type.toUpperCase(java.util.Locale.ROOT)) || definition.id.contains("thundergun")))return;
        String id=definition!=null&&definition.sounds!=null?definition.sounds.fire_pap:null;
        if(id==null||id.isBlank())id="zombierool:gun_fire_upgraded";
        var sound=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
        if(sound!=null)net.minecraft.client.Minecraft.getInstance().getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(sound,net.minecraft.sounds.SoundSource.PLAYERS,1.0f,1.0f,net.minecraft.util.RandomSource.create(),shooter.getX(),shooter.getY(),shooter.getZ()));
    }
}
