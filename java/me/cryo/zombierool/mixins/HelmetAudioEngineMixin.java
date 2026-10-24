package me.cryo.zombierool.mixins;

import me.cryo.zombierool.client.HelmetAudio;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class HelmetAudioEngineMixin {
    @Shadow(aliases={"f_120226_"},remap=false) @Final private Map<SoundInstance,ChannelAccess.ChannelHandle> instanceToChannel;
    @Unique private final Map<ChannelAccess.ChannelHandle,Integer> zombierool$profiles=new java.util.WeakHashMap<>();
    @Inject(method={"tick","m_120302_"},at=@At("TAIL"),remap=false)
    private void zombierool$updateHelmetAudio(boolean paused,CallbackInfo ci){
        instanceToChannel.forEach((sound,handle)->{int profile=HelmetAudio.profile(sound);Integer previous=zombierool$profiles.put(handle,profile);if(previous==null||previous!=profile)handle.execute(channel->((HelmetAudio.FilteredChannel)channel).zombierool$helmetFilter(profile));});
    }
}
