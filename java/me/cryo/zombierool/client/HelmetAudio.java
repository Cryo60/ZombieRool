package me.cryo.zombierool.client;

import me.cryo.zombierool.gameplay.LunarEquipment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import java.util.concurrent.atomic.AtomicInteger;

/** Decisions on the client thread; the channel implementation touches OpenAL only on its audio thread. */
public final class HelmetAudio {
    public interface FilteredChannel {void zombierool$helmetFilter(int profile);}
    public static final AtomicInteger FILTERED_SOURCES=new AtomicInteger();
    private static boolean helmetMuffled(SoundInstance sound){
        var player=Minecraft.getInstance().player;if(player==null||!LunarEquipment.equipped(player))return false;
        var id=sound.getLocation();if(id.getNamespace().equals("zombierool")&&id.getPath().startsWith("helmet_"))return false;
        return sound.getSource()!=SoundSource.MUSIC&&sound.getSource()!=SoundSource.RECORDS&&sound.getSource()!=SoundSource.MASTER;
    }
    public static boolean muffled(SoundInstance sound){return profile(sound)!=0;}
    public static int profile(SoundInstance sound){
        var id=sound.getLocation();boolean internal=id.getNamespace().equals("zombierool")&&(id.getPath().startsWith("injury_")||id.getPath().startsWith("helmet_"));
        if(internal||sound.getSource()==SoundSource.MUSIC||sound.getSource()==SoundSource.RECORDS||sound.getSource()==SoundSource.MASTER)return 0;
        int base=helmetMuffled(sound)?LunarEquipment.style(Minecraft.getInstance().player)==LunarEquipment.SuitStyle.HAZMAT?2:1:0;
        int step=InjuryFeedback.audioStep();return step==0?base:3+base*4+step-1;
    }
    private HelmetAudio(){}
}
