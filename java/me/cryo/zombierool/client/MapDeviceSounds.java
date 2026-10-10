package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.MapDevicePackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class MapDeviceSounds {
    public static void play(MapDevicePackets.Sound m) {
        var mc=Minecraft.getInstance(); if(mc.player==null) return;
        Vec3 pos=Vec3.atCenterOf(m.pos());
        float volume=m.volume() * Math.max(0,1-(float)mc.player.position().distanceTo(pos)/m.range());
        if(volume<=0) return;
        if(DynamicSoundLoader.hasDynamicSound(m.id())) {
            var file=DynamicSoundLoader.getDynamicSoundFile(m.id());
            if(file!=null) mc.getSoundManager().play(new DynamicSoundLoader.FileSoundInstance(file,SoundSource.BLOCKS,volume,m.pitch(),false,pos.x,pos.y,pos.z,null) {{ attenuation=SoundInstance.Attenuation.NONE; }});
        } else mc.getSoundManager().play(new SimpleSoundInstance(new ResourceLocation(m.id()),SoundSource.BLOCKS,volume,m.pitch(),RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,pos.x,pos.y,pos.z,false));
    }
}
