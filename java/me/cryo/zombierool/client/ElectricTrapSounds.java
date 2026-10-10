package me.cryo.zombierool.client;

import java.util.HashMap;
import java.util.Map;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEngineLoadEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A single positional loop per active pad, regardless of camera visibility. */
@Mod.EventBusSubscriber(modid="zombierool", value=Dist.CLIENT)
public final class ElectricTrapSounds {
    private static final Map<BlockPos,Loop> LOOPS=new HashMap<>();
    private static ClientLevel world;
    private ElectricTrapSounds() {}
    public static void tickDevice(MapDeviceSystem.Device device) {
        if(device.kind()!=MapDeviceSystem.Kind.ELECTRIC)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=world){clear();world=mc.level;}
        if(mc.level==null||device.getLevel()!=mc.level)return;
        BlockPos pos=device.getBlockPos();
        Loop loop=LOOPS.get(pos);
        if(!device.getBlockState().getValue(MapDeviceSystem.DeviceBlock.ACTIVE)){
            if(loop!=null){loop.end();LOOPS.remove(pos);}return;
        }
        if(loop==null||loop.isStopped()){
            loop=new Loop(mc.level,pos);LOOPS.put(pos.immutable(),loop);
            mc.getSoundManager().play(loop);
        }
    }
    private static void clear(){for(Loop loop:java.util.List.copyOf(LOOPS.values()))loop.end();LOOPS.clear();world=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void reload(SoundEngineLoadEvent event){clear();}
    private static final class Loop extends AbstractTickableSoundInstance {
        private final ClientLevel level;
        private final BlockPos pos;
        Loop(ClientLevel level,BlockPos pos){
            super(ZombieroolModSounds.ELECTRIC_TRAP_LOOP.get(),SoundSource.BLOCKS,SoundInstance.createUnseededRandom());
            this.level=level;this.pos=pos.immutable();x=pos.getX()+.5;y=pos.getY()+.5;z=pos.getZ()+.5;
            looping=true;delay=0;volume=.65f;pitch=1;attenuation=Attenuation.LINEAR;
        }
        void end(){stop();LOOPS.remove(pos,this);}
        @Override public void tick(){
            if(Minecraft.getInstance().level!=level||!level.hasChunkAt(pos)){end();return;}
            var state=level.getBlockState(pos);
            if(!(state.getBlock() instanceof MapDeviceSystem.DeviceBlock block)
                    ||block.kind!=MapDeviceSystem.Kind.ELECTRIC||!state.getValue(MapDeviceSystem.DeviceBlock.ACTIVE))end();
            if(isStopped())LOOPS.remove(pos,this);
        }
    }
}
