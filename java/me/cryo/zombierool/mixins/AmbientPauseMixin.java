package me.cryo.zombierool.mixins;

import java.util.Map;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public abstract class AmbientPauseMixin {
    @Shadow(aliases={"f_120226_"},remap=false) @Final private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method={"pause","m_120314_"}, at=@At("TAIL"), remap=false)
    private void zombierool$continueAmbient(CallbackInfo ci) {
        instanceToChannel.forEach((sound, channel) -> {
            if (me.cryo.zombierool.client.ZombieSoundHandler.continuesDuringPause(sound))
                channel.execute(Channel::unpause);
        });
    }
}
