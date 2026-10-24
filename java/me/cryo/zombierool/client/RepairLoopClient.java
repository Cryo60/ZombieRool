package me.cryo.zombierool.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One held repair loop with a short tail after release or completed repair. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RepairLoopClient {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static BlockPos heldTarget;
    private RepairLoopClient() {}
    /** Keep repairing the same barricade while F is held, even when a stage changes its outline. */
    public static BlockPos selectTarget(boolean held,BlockPos candidate){
        if(!held){heldTarget=null;return null;}
        if(repairable(heldTarget))return heldTarget;
        heldTarget=repairable(candidate)?root(candidate):null;return heldTarget;
    }
    private static BlockPos root(BlockPos pos){
        var l=Minecraft.getInstance().level;if(l==null||pos==null)return pos;var s=l.getBlockState(pos);
        if(s.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallDummyBlock dummy)return dummy.getMainPos(pos,s);
        if(s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)&&s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)return pos.below();return pos;
    }
    private static boolean repairable(BlockPos pos){
        var mc=Minecraft.getInstance();if(pos==null||mc.player==null||mc.level==null)return false;pos=root(pos);
        if(mc.player.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>9)return false;
        var s=mc.level.getBlockState(pos);
        if(s.getBlock() instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.DefenseDoorBlock door)return door.canRepair(s);
        if(s.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock)return !s.getValue(me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock.PERMANENTLY_OPEN)&&s.getValue(me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock.STAGE)<7;
        return false;
    }
    private static final java.util.List<Sample> samples=new java.util.ArrayList<>();
    private static long nextSound;
    private static int releaseTicks;
    private static boolean holding;
    public static void update(BlockPos target) {
        var mc=Minecraft.getInstance();
        holding=target!=null;
        if(!holding)return;
        releaseTicks=25;
        long now=System.nanoTime();if(now<nextSound)return;nextSound=now+500_000_000L;
        var state=mc.level.getBlockState(target);
        boolean wall=state.getBlock() instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock;
        play(target,wall?"rock_slam":"boards_float");
        play(target,wall?"repairing_rock":"repairing_plank");
    }
    private static void play(BlockPos pos,String id){
        var event=net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.resources.ResourceLocation("zombierool",id));
        if(event==null)return;
        var sound=new Sample(pos,event);samples.add(sound);Minecraft.getInstance().getSoundManager().play(sound);
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||!mc.player.isAlive()||me.cryo.zombierool.handlers.KeyInputHandler.isLocalPlayerDown){clear();return;}
        if(!holding||!me.cryo.zombierool.init.KeyBindings.REPAIR_AND_PURCHASE_KEY.isDown()){
            if(releaseTicks>0)--releaseTicks;
            if(releaseTicks==0){clear();return;}
        }
        samples.removeIf(net.minecraft.client.resources.sounds.AbstractTickableSoundInstance::isStopped);
    }
    private static void clear(){for(var s:samples)s.finish();samples.clear();heldTarget=null;holding=false;nextSound=0;releaseTicks=0;}
    private static final class Sample extends AbstractTickableSoundInstance {
        private int age;
        Sample(BlockPos pos,net.minecraft.sounds.SoundEvent event){
            super(event,SoundSource.BLOCKS,RandomSource.create());x=pos.getX()+.5;y=pos.getY()+.5;z=pos.getZ()+.5;volume=.3f;
        }
        void finish(){stop();}
        @Override public void tick(){
            // Natural samples overlap every 500 ms, as in 1.6.0-stable and 1.6.6-hotfix1.
            // Finished OpenAL sources are removed by SoundEngine; also retire their tickable handles.
            if(++age>100)stop();
            if(!holding&&releaseTicks<5)volume=.3f*Math.max(0,releaseTicks)/5f;
        }
    }
}
