package me.cryo.zombierool.block.system;

import java.util.UUID;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import me.cryo.zombierool.gameplay.TraversalMotion;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.config.MultiReceptorState;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** A wall objective takes one real corpse, then refuses further kills while chewing. */
public final class CerberusSystem {
    public static final int FLIGHT_TICKS=36;
    public static final int EMERGE_TICKS=24,WITHDRAW_TICKS=24,DEPARTURE_TICKS=80,BITE_TICKS=24;
    public static float jawAngle(int phase,double elapsed){return phase==1?.78f:phase==2?.015f+(float)Math.pow(.5-.5*Math.cos(elapsed*Math.PI*2/BITE_TICKS),.65)*.62f:phase==3?.75f:.08f;}
    public static float headPitch(int phase,double elapsed){return phase==3?-.38f*(float)Math.min(1,elapsed/12):phase==2?(float)Math.sin(elapsed*Math.PI*2/BITE_TICKS)*.12f:(float)Math.sin(elapsed*.045)*.045f;}
    public static float headYaw(int phase,double elapsed){return phase==2?(float)Math.sin(elapsed*.32)*.2f:(float)Math.sin(elapsed*.025)*.16f;}
    public static float barkMotion(double ticks,int axis){
        if(ticks<0||ticks>=8)return 0;
        double envelope=Math.sin(ticks*Math.PI/8);
        return (float)(envelope*(axis==0?-.10:axis==1?Math.sin(ticks*.4)*.025:.012));
    }
    /** Each closed bite pulls in another section; the last quarter is a continuous gulp. */
    public static double eaten(double ticks,int duration){
        double total=Math.max(20,duration),chewing=total*.72;
        if(chewing>=BITE_TICKS)chewing=Math.floor(chewing/BITE_TICKS)*BITE_TICKS;
        if(ticks>=chewing){double t=Math.min(1,Math.max(0,(ticks-chewing)/(total-chewing)));return .68+.32*t*t*(3-2*t);}
        double cycles=Math.max(1,chewing/BITE_TICKS),bite=ticks/chewing*cycles,whole=Math.floor(bite);
        double t=Math.max(0,Math.min(1,(bite-whole-.55)/.45));
        return Math.min(.68,.68*(whole+t*t*(3-2*t))/cycles);
    }
    public static class WallBlock extends SoulChestSystem.ChestBlock {
        public WallBlock(){super();}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getClickedFace().getAxis().isHorizontal()?c.getClickedFace():c.getHorizontalDirection().getOpposite());}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return switch(s.getValue(FACING)){case NORTH->box(0,0,14,16,16,16);case SOUTH->box(0,0,0,16,16,2);case EAST->box(0,0,0,2,16,16);default->box(14,0,0,16,16,16);};}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.empty();}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Head(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return t==MapAuthorSystem.CERBERUS_TYPE.get()?(level,p,state,be)->{if(level.isClientSide)((Head)be).clientTick();else ((Head)be).tick();}:null;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand h,BlockHitResult hit){if(player instanceof ServerPlayer sp&&sp.isCreative()&&l.getBlockEntity(p) instanceof Head head){TransportPackets.open(sp,p,"cerberus",head.config());return InteractionResult.SUCCESS;}return super.use(s,l,p,player,h,hit);}
    }
    public static class Head extends SoulChestSystem.Chest {
        public int chewTime=5;
        public boolean revealed;
        public long phaseStarted;
        public long appearedAt;
        public long idleStarted;
        public int phase; // 0: ready, 1: pulling a corpse, 2: chewing, 3: complete.
        private AbstractZombieRoolEntity corpse;
        private Vec3 source=Vec3.ZERO;
        private UUID killer;
        private int elapsed;
        private Vec3 feedingTarget;
        private boolean signalWasActive;
        private long nextIdleSound;
        public Head(BlockPos p,BlockState s){super(MapAuthorSystem.CERBERUS_TYPE.get(),p,s);}
        @Override protected String idPrefix(){return "cerberus";}
        @Override public Vec3 center(){return Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(getBlockState().getValue(WallBlock.FACING).getNormal()).scale(2.2));}
        public Vec3 mouth(double ticks){
            double pitch=headPitch(phase,ticks),yaw=headYaw(phase,ticks);
            Vec3 forward=Vec3.atLowerCornerOf(getBlockState().getValue(WallBlock.FACING).getNormal()),side=new Vec3(-forward.z,0,forward.x);
            // Same pivot, scale and rotations as CerberusRenderer; bite at the
            // middle of the fangs rather than an approximate world-space point.
            double y=2.5*Math.cos(pitch)+15*Math.sin(pitch),z=2.5*Math.sin(pitch)-15*Math.cos(pitch);
            return Vec3.atLowerCornerOf(worldPosition).add(.5,.8,.5).add(forward.scale(.15-z*Math.cos(yaw)*2.2/16)).add(side.scale(z*Math.sin(yaw)*2.2/16)).add(0,-y*2.2/16,0);
        }
        public Vec3 feedingPosition(AbstractZombieRoolEntity body,double ticks){
            double yaw=headYaw(phase,ticks),pitch=headPitch(phase,ticks);
            Vec3 forward=Vec3.atLowerCornerOf(getBlockState().getValue(WallBlock.FACING).getNormal()),side=new Vec3(-forward.z,0,forward.x);
            Vec3 axis=forward.scale(Math.cos(yaw)*Math.cos(pitch)).add(side.scale(-Math.sin(yaw)*Math.cos(pitch))).add(0,-Math.sin(pitch),0);
            return mouth(ticks).add(axis.scale((body instanceof me.cryo.zombierool.entity.CrawlerEntity?.55-eaten(ticks,chewTime*20)*1.25:body.getBbHeight()*(.85-eaten(ticks,chewTime*20)*.90))));
        }
        @Override public boolean canAccept(){return enabled&&!completed&&phase==0&&collected<required;}
        @Override public CompoundTag config(){var n=super.config();n.putInt("chewTime",chewTime);return n;}
        @Override public void configure(CompoundTag n){chewTime=Math.max(1,Math.min(60,n.getInt("chewTime")));super.configure(n);}
        @Override protected void accept(LivingDeathEvent event,Vec3 from,ServerLevel server){
            if(!canAccept()||!(event.getEntity() instanceof AbstractZombieRoolEntity z))return;
            corpse=z;source=z.position();z.getPersistentData().remove("zr_geist_gas_pending");z.setCerberusHead(worldPosition);killer=event.getSource().getEntity() instanceof ServerPlayer p?p.getUUID():null;
            TraversalMotion.cancel(z);z.setEmergenceTicks(0);z.setCerberusFeeding(true);z.getNavigation().stop();z.setTarget(null);z.setNoAi(true);z.setNoGravity(true);z.noPhysics=true;z.setDeltaMovement(Vec3.ZERO);
            if(!revealed){appearedAt=server.getGameTime();sound("cerberus_emerge",1);}
            revealed=true;phase=1;phaseStarted=server.getGameTime();elapsed=0;lastSoul=phaseStarted;feedingTarget=mouth(0);sync();
            me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnCerberusFeeding",objectiveId,z.getUUID().toString());
        }
        private void release(){if(corpse!=null&&!corpse.isRemoved()){corpse.setCerberusFeeding(false);corpse.setNoGravity(false);corpse.noPhysics=false;corpse.setDeltaMovement(Vec3.ZERO);}corpse=null;killer=null;}
        @Override public void reset(){release();phase=0;revealed=false;phaseStarted=0;appearedAt=0;idleStarted=0;nextIdleSound=0;elapsed=0;super.reset();}
        @Override protected void completionSound(ServerLevel server){sound("cerberus_complete",1);}
        private void sound(String id,float volume){if(level instanceof ServerLevel server){var sound=ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool",id));if(sound!=null)server.playSound(null,worldPosition,sound,SoundSource.BLOCKS,volume,1);}}
        @Override protected void tick(){
            if(!(level instanceof ServerLevel server))return;ensureEpoch(server);if(!enabled)return;
            boolean signal=!resetSignal.isBlank()&&MultiReceptorState.isActivated(level,resetSignal);if(signal&&!signalWasActive)reset();signalWasActive=signal;
            if(phase==0&&!completed&&collected>0&&resetAfter>0&&lastSoul>0&&server.getGameTime()-lastSoul>=resetAfter*20L)reset();
            if(!WaveManager.isGameRunning())return;
            if(phase==0&&revealed&&!completed&&server.getGameTime()>=nextIdleSound){sound("cerberus_idle",1.5f);idleStarted=server.getGameTime();nextIdleSound=idleStarted+10;sync();}
            if(phase==1){
                if(corpse==null||corpse.isRemoved()){release();phase=0;sync();return;}
                double t=Math.min(1,++elapsed/(double)FLIGHT_TICKS),eased=t*t*(3-2*t);
                Vec3 forward=Vec3.atLowerCornerOf(getBlockState().getValue(WallBlock.FACING).getNormal());
                Vec3 point=source.lerp(feedingTarget.add(forward.scale((corpse instanceof me.cryo.zombierool.entity.CrawlerEntity?.55:corpse.getBbHeight()*.85))),eased).add(0,Math.sin(t*Math.PI)*.7,0);
                corpse.teleportTo(point.x,point.y,point.z);corpse.setDeltaMovement(Vec3.ZERO);corpse.hurtMarked=true;
                corpse.setYRot(getBlockState().getValue(WallBlock.FACING).toYRot());corpse.setXRot((float)(90*eased));
                server.sendParticles(ParticleTypes.SMOKE,point.x,point.y+.35,point.z,2,.12,.12,.12,0);
                if(elapsed>=FLIGHT_TICKS){phase=2;phaseStarted=server.getGameTime();elapsed=0;sync();}
            }else if(phase==2){
                if(corpse==null||corpse.isRemoved()){release();phase=0;sync();return;}
                Vec3 forward=Vec3.atLowerCornerOf(getBlockState().getValue(WallBlock.FACING).getNormal());
                Vec3 point=feedingPosition(corpse,elapsed);
                corpse.teleportTo(point.x,point.y,point.z);corpse.setDeltaMovement(Vec3.ZERO);corpse.hurtMarked=true;
                corpse.setYRot(getBlockState().getValue(WallBlock.FACING).toYRot()-(float)Math.toDegrees(headYaw(phase,elapsed)));corpse.setXRot(90-(float)Math.toDegrees(headPitch(phase,elapsed)));
                if(elapsed%BITE_TICKS==0){sound("cerberus_chew",.7f);sound("cerberus_bite",.8f);if(chewTime*20-elapsed<=BITE_TICKS)sound("death_beurk1",.85f);server.sendParticles(me.cryo.zombierool.init.ZombieroolModParticleTypes.BLOOD_STAIN.get(),mouth(elapsed).x,mouth(elapsed).y,mouth(elapsed).z,10,.3,.22,.3,.08);}
                if(++elapsed>=chewTime*20){corpse.discard();corpse=null;collected=Math.min(required,collected+1);lastSoul=server.getGameTime();elapsed=0;phaseStarted=lastSoul;
                    if(killer!=null)me.cryo.zombierool.career.CareerManager.progressGameplay(server.getServer().getPlayerList().getPlayer(killer),me.cryo.zombierool.career.CareerManager.ChallengeType.CERBERUS_FED,1);
                    me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnCerberusMealFinished",objectiveId,collected,required,killer==null?"":killer.toString());
                    nextIdleSound=lastSoul+10;
                    if(collected>=required){phase=3;complete(server,killer);}else{phase=0;sound("cerberus_next",.9f);}killer=null;sync();}
            }
        }
        @Override protected void clientTick(){
            if(!enabled||!revealed||!WaveManager.isGameRunning()||level.getGameTime()%2!=0||completed&&level.getGameTime()-phaseStarted>=DEPARTURE_TICKS)return;
            var random=level.random;Vec3 forward=Vec3.atLowerCornerOf(getBlockState().getValue(WallBlock.FACING).getNormal()),side=new Vec3(-forward.z,0,forward.x);
            Vec3 origin=Vec3.atCenterOf(worldPosition).subtract(forward.scale(.42));
            for(int i=0;i<18;i++){
                double angle=random.nextDouble()*Math.PI*2,r=1.05+random.nextDouble()*.5;
                Vec3 p=origin.add(side.scale(Math.cos(angle)*r)).add(0,Math.sin(angle)*r+.35,0);
                if(i>=10)p=Vec3.atCenterOf(worldPosition).add(forward.scale(.3+random.nextDouble()*1.6)).add(side.scale(Math.cos(angle)*(.7+random.nextDouble()*.3))).add(0,Math.sin(angle)*.75,0);
                level.addParticle(i%4==0?ParticleTypes.SOUL_FIRE_FLAME:ParticleTypes.FLAME,true,p.x,p.y,p.z,forward.x*.025,.055,forward.z*.025);
                if(i%6==0)level.addParticle(ParticleTypes.LARGE_SMOKE,true,p.x,p.y,p.z,0,.04,0);
            }
        }
        @Override public void setRemoved(){release();super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putBoolean("revealed",revealed);n.putInt("phase",phase);n.putLong("phaseStarted",phaseStarted);n.putLong("appearedAt",appearedAt);n.putLong("idleStarted",idleStarted);}
        @Override public void load(CompoundTag n){super.load(n);chewTime=n.contains("chewTime")?Math.max(1,n.getInt("chewTime")):5;revealed=n.getBoolean("revealed");phase=n.getInt("phase");phaseStarted=n.getLong("phaseStarted");appearedAt=n.getLong("appearedAt");idleStarted=n.getLong("idleStarted");if(level==null||!level.isClientSide){phase=completed?3:0;corpse=null;elapsed=0;}}
    }
}
