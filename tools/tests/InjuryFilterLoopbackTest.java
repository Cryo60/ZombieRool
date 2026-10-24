import java.nio.ByteBuffer;
import org.lwjgl.openal.*;
import me.cryo.zombierool.client.HelmetLowpass;

/** Checks the actual EFX profiles without opening an output device or playing audio. */
public class InjuryFilterLoopbackTest {
    public static void main(String[] args){
        long device=SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer)null);if(device==0)throw new AssertionError("No loopback device");
        var caps=ALC.createCapabilities(device);long context=ALC10.alcCreateContext(device,new int[]{SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT,SOFTLoopback.ALC_STEREO_SOFT,SOFTLoopback.ALC_FORMAT_TYPE_SOFT,SOFTLoopback.ALC_SHORT_SOFT,ALC10.ALC_FREQUENCY,44100,0});
        if(context==0)throw new AssertionError("No loopback context");ALC10.alcMakeContextCurrent(context);AL.createCapabilities(caps);
        int filter=EXTEfx.alGenFilters();float[] high=new float[15];
        try{
            for(int profile=1;profile<=14;profile++){HelmetLowpass.configureProfile(filter,profile);high[profile]=EXTEfx.alGetFilterf(filter,EXTEfx.AL_LOWPASS_GAINHF);if(AL10.alGetError()!=AL10.AL_NO_ERROR||high[profile]<=0||high[profile]>1)throw new AssertionError("Invalid profile "+profile);}
            if(!(high[3]>high[4]&&high[4]>high[5]&&high[5]>high[6]))throw new AssertionError("Injury must progressively attenuate high frequencies");
            HelmetLowpass.configureProfile(filter,2);if(Math.abs(EXTEfx.alGetFilterf(filter,EXTEfx.AL_LOWPASS_GAINHF)-HelmetLowpass.HAZMAT_HIGH_FREQUENCY_GAIN)>.001f)throw new AssertionError("Healthy hazmat profile changed");
            if(high[14]>=high[2])throw new AssertionError("Hazmat injury must also muffle hearing");
            System.out.println("INJURY AUDIO PASS: 14 real OpenAL filter profiles, progressive hearing loss and unchanged healthy hazmat; no speaker output");
        }finally{EXTEfx.alDeleteFilters(filter);ALC10.alcMakeContextCurrent(0);ALC10.alcDestroyContext(context);ALC10.alcCloseDevice(device);}
    }
}
