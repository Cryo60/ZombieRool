package me.cryo.zombierool.client;

import org.lwjgl.openal.EXTEfx;

/** Shared by the game and the silent OpenAL loopback comparison. */
public final class HelmetLowpass {
    public static final float GAIN=.55f, HIGH_FREQUENCY_GAIN=.015f;
    public static final float HAZMAT_GAIN=.95f, HAZMAT_HIGH_FREQUENCY_GAIN=.75f;
    public static void configure(int filter){
        configure(filter,false);
    }
    public static void configure(int filter,boolean hazmat){
        EXTEfx.alFilteri(filter,EXTEfx.AL_FILTER_TYPE,EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alFilterf(filter,EXTEfx.AL_LOWPASS_GAIN,hazmat?HAZMAT_GAIN:GAIN);
        EXTEfx.alFilterf(filter,EXTEfx.AL_LOWPASS_GAINHF,hazmat?HAZMAT_HIGH_FREQUENCY_GAIN:HIGH_FREQUENCY_GAIN);
    }
    public static void configureProfile(int filter,int profile){
        if(profile<3){configure(filter,profile==2);return;}
        int base=(profile-3)/4;float injury=((profile-3)%4+1)/4f;
        float gain=base==1?GAIN:base==2?HAZMAT_GAIN:1;
        float high=base==1?HIGH_FREQUENCY_GAIN:base==2?HAZMAT_HIGH_FREQUENCY_GAIN:1;
        EXTEfx.alFilteri(filter,EXTEfx.AL_FILTER_TYPE,EXTEfx.AL_FILTER_LOWPASS);
        EXTEfx.alFilterf(filter,EXTEfx.AL_LOWPASS_GAIN,gain*(1-.22f*injury));
        EXTEfx.alFilterf(filter,EXTEfx.AL_LOWPASS_GAINHF,Math.max(.01f,high*(1-.94f*injury)));
    }
    private HelmetLowpass(){}
}
