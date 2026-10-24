import me.cryo.zombierool.client.HelmetLowpass;
import org.lwjgl.openal.*;
import java.nio.*;
import java.nio.file.*;
import java.io.*;
import javax.sound.sampled.*;

/** Renders the exact game EFX filter to WAV without opening a window or outputting speaker audio. */
public final class HelmetAudioLoopbackTest {
    private static final int RATE=44100;
    private static byte[] render(Path source,int profile)throws Exception{
        byte[] pcm;try(var in=AudioSystem.getAudioInputStream(source.toFile())){pcm=in.readAllBytes();}
        long device=SOFTLoopback.alcLoopbackOpenDeviceSOFT((ByteBuffer)null);if(device==0)throw new AssertionError("No OpenAL loopback device");
        var caps=ALC.createCapabilities(device);long context=ALC10.alcCreateContext(device,new int[]{SOFTLoopback.ALC_FORMAT_CHANNELS_SOFT,SOFTLoopback.ALC_STEREO_SOFT,SOFTLoopback.ALC_FORMAT_TYPE_SOFT,SOFTLoopback.ALC_SHORT_SOFT,ALC10.ALC_FREQUENCY,RATE,0});
        if(context==0)throw new AssertionError("Loopback context failed");ALC10.alcMakeContextCurrent(context);AL.createCapabilities(caps);
        int filter=0,buffer=AL10.alGenBuffers(),sound=AL10.alGenSources();
        try{
            ByteBuffer raw=ByteBuffer.allocateDirect(pcm.length).order(ByteOrder.LITTLE_ENDIAN);raw.put(pcm).flip();AL10.alBufferData(buffer,AL10.AL_FORMAT_MONO16,raw,RATE);AL10.alSourcei(sound,AL10.AL_BUFFER,buffer);AL10.alSourcei(sound,AL10.AL_SOURCE_RELATIVE,AL10.AL_TRUE);AL10.alSourcef(sound,AL10.AL_ROLLOFF_FACTOR,0);
            if(profile!=0){filter=EXTEfx.alGenFilters();HelmetLowpass.configure(filter,profile==2);AL10.alSourcei(sound,EXTEfx.AL_DIRECT_FILTER,filter);}
            AL10.alSourcePlay(sound);int frames=pcm.length/2+RATE/5;short[] output=new short[frames*2];SOFTLoopback.alcRenderSamplesSOFT(device,output,frames);if(AL10.alGetError()!=AL10.AL_NO_ERROR)throw new AssertionError("OpenAL error");
            ByteBuffer bytes=ByteBuffer.allocate(output.length*2).order(ByteOrder.LITTLE_ENDIAN);for(short value:output)bytes.putShort(value);return bytes.array();
        }finally{AL10.alDeleteSources(sound);AL10.alDeleteBuffers(buffer);if(filter!=0)EXTEfx.alDeleteFilters(filter);ALC10.alcMakeContextCurrent(0);ALC10.alcDestroyContext(context);ALC10.alcCloseDevice(device);}
    }
    private static void write(Path p,byte[] bytes)throws Exception{var format=new AudioFormat(RATE,16,2,true,false);try(var stream=new AudioInputStream(new ByteArrayInputStream(bytes),format,bytes.length/4)){AudioSystem.write(stream,AudioFileFormat.Type.WAVE,p.toFile());}}
    public static void main(String[] args)throws Exception{
        Path input=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);byte[] normal=render(input,0),helmet=render(input,1),hazmat=render(input,2);write(out.resolve("sans-casque.wav"),normal);write(out.resolve("avec-casque.wav"),helmet);write(out.resolve("hazmat.wav"),hazmat);
        double plain=rms(normal),heavy=rms(helmet),light=rms(hazmat);if(light<plain*.7||light<heavy*1.4)throw new AssertionError("Hazmat must preserve clarity: "+plain+" / "+heavy+" / "+light);System.out.println("AUDIO RMS normal="+plain+" lunar="+heavy+" hazmat="+light);
        var combined=new ByteArrayOutputStream();combined.write(normal);combined.write(new byte[RATE*4]);combined.write(helmet);write(out.resolve("comparatif-sans-puis-avec-casque.wav"),combined.toByteArray());System.out.println("PASS: silent OpenAL rendering of normal and exact in-game helmet lowpass");
    }
    private static double rms(byte[] bytes){var b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);double sum=0;while(b.remaining()>=2){double v=b.getShort();sum+=v*v;}return Math.sqrt(sum/(bytes.length/2));}
}
