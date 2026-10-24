package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.audio.Channel;
import me.cryo.zombierool.client.HelmetAudio;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class HelmetAudioChannelMixin implements HelmetAudio.FilteredChannel {
    @Shadow(aliases={"f_83642_"},remap=false) @Final private int source;
    @Unique private int zombierool$filter;
    @Unique private boolean zombierool$filtered;
    @Unique private int zombierool$profile;
    @Override public void zombierool$helmetFilter(int profile){
        if(profile==zombierool$profile)return;
        boolean enabled=profile!=0;
        if(AL.getCapabilities().alGenFilters==0)return;
        if(enabled){
            if(zombierool$filter==0){zombierool$filter=EXTEfx.alGenFilters();if(zombierool$filter==0)return;
            }
            me.cryo.zombierool.client.HelmetLowpass.configureProfile(zombierool$filter,profile);
            AL10.alSourcei(source,EXTEfx.AL_DIRECT_FILTER,zombierool$filter);if(!zombierool$filtered)HelmetAudio.FILTERED_SOURCES.incrementAndGet();zombierool$filtered=true;
        }else{AL10.alSourcei(source,EXTEfx.AL_DIRECT_FILTER,EXTEfx.AL_FILTER_NULL);zombierool$filtered=false;HelmetAudio.FILTERED_SOURCES.decrementAndGet();}
        zombierool$profile=profile;
    }
    @Inject(method={"destroy","m_83665_"},at=@At("HEAD"),remap=false)
    private void zombierool$releaseFilter(CallbackInfo ci){
        if(zombierool$filtered){zombierool$filtered=false;HelmetAudio.FILTERED_SOURCES.decrementAndGet();}
        if(zombierool$filter!=0){AL10.alSourcei(source,EXTEfx.AL_DIRECT_FILTER,EXTEfx.AL_FILTER_NULL);EXTEfx.alDeleteFilters(zombierool$filter);zombierool$filter=0;}
    }
}
