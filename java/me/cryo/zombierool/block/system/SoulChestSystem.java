package me.cryo.zombierool.block.system;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import java.util.*;

@Mod.EventBusSubscriber(modid="zombierool")
public final class SoulChestSystem {
    private static final Map<Level,Map<BlockPos,Chest>> LOADED=new WeakHashMap<>();
    private static final DustParticleOptions RED=new DustParticleOptions(new Vector3f(1,.04f,.025f),1.5f);
    private SoulChestSystem(){}
    public static void unload(Level level){for(Chest chest:LOADED.getOrDefault(level,Map.of()).values())chest.clearOrbs();LOADED.remove(level);}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent event){
        if(event.isCanceled()||!WaveManager.isGameRunning()||!(event.getEntity().level() instanceof ServerLevel level)
                ||!(event.getEntity() instanceof ZombieEntity||me.cryo.zombierool.gameplay.CrawlerLeap.isCrawler(event.getEntity())))return;
        Vec3 from=event.getEntity().position().add(0,event.getEntity().getBbHeight()*.5,0);
        Chest nearest=LOADED.getOrDefault(level,Map.of()).values().stream().filter(c->c.canAccept()&&c.center().distanceToSqr(from)<=c.radius*c.radius)
            .min(Comparator.comparingDouble(c->c.center().distanceToSqr(from))).orElse(null);
        if(nearest!=null)nearest.accept(event,from,level);
    }
    public static void reset(Level level){if(level instanceof ServerLevel server)SoulObjectiveRegistry.get(server).reset();for(var chest:LOADED.getOrDefault(level,Map.of()).values())chest.reset();SoulCompletionSystem.refreshAll(level);}
    private static final class Orb { final SoulOrbEntity visual;final UUID killer;int tick;Orb(SoulOrbEntity visual,UUID killer){this.visual=visual;this.killer=killer;} }
    public static class ChestBlock extends Block implements EntityBlock, me.cryo.zombierool.block.WaterloggedElement {
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty OPEN=net.minecraft.world.level.block.state.properties.BooleanProperty.create("open");
        public static final net.minecraft.world.level.block.state.properties.BooleanProperty COMPLETED=net.minecraft.world.level.block.state.properties.BooleanProperty.create("completed");
        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING=HorizontalDirectionalBlock.FACING;
        public ChestBlock(){super(Properties.of().strength(-1,3600000).noOcclusion().sound(SoundType.METAL));registerDefaultState(stateDefinition.any().setValue(OPEN,false).setValue(COMPLETED,false).setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(OPEN,COMPLETED,FACING);}
        @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(2,0,2,14,12,14);}
        @Override public boolean isSignalSource(BlockState s){return true;}
        @Override public int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return l.getBlockEntity(p) instanceof Chest c&&c.completed&&c.redstone?15:0;}
        @Override public int getDirectSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return getSignal(s,l,p,d);}
        @Override public boolean canConnectRedstone(BlockState s,BlockGetter l,BlockPos p,Direction d){return true;}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(!s.is(next.getBlock())){if(l instanceof ServerLevel server)SoulObjectiveRegistry.get(server).remove(p);}super.onRemove(s,l,p,next,moving);}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Chest(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> type){return type==MapAuthorSystem.SOUL_TYPE.get()?(level,pos,state,be)->{if(level.isClientSide)((Chest)be).clientTick();else ((Chest)be).tick();}:null;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){
            if(!l.isClientSide&&player instanceof ServerPlayer sp&&l.getBlockEntity(p) instanceof Chest chest){
                if(sp.isCreative())TransportPackets.open(sp,p,"soul",chest.config());
                else sp.displayClientMessage(Component.translatable(chest.completed?"message.zombierool.soul.complete":"message.zombierool.soul.progress",chest.collected,chest.required),true);
            }return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    public static class Chest extends BlockEntity {
        public int required=20,collected=0,rewardPoints=0,rewardCount=1,resetAfter=0,rewardChannel=-1;
        public double radius=8;
        public boolean enabled=true,completed=false,redstone=true,rewardAll=true;
        public String rewardItem="",resetSignal="",objectiveId="";
        protected long resetEpoch;
        protected long lastSoul;
        private boolean signalWasActive;
        private final List<Orb> orbs=new ArrayList<>();
        public boolean auraActive(){return enabled&&!completed&&WaveManager.isGameRunning();}
        protected void clientTick(){
            if(!auraActive())return;
            long time=level.getGameTime();
            if(time%3!=0)return;
            double angle=time*.075;Vec3 p=center().add(Math.cos(angle)*.62,-.35,Math.sin(angle)*.62);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,true,p.x,p.y,p.z,Math.cos(angle)*.008,.018,Math.sin(angle)*.008);
            level.addParticle(new DustParticleOptions(new Vector3f(.13f,.015f,.19f),1.8f),true,p.x,p.y+.12,p.z,0,.01,0);
            if(time%12==0)level.addParticle(net.minecraft.core.particles.ParticleTypes.WITCH,true,p.x,p.y+.45,p.z,0,.025,0);
        }
        public Chest(BlockPos p,BlockState s){this(MapAuthorSystem.SOUL_TYPE.get(),p,s);}
        protected Chest(BlockEntityType<?> type,BlockPos p,BlockState s){super(type,p,s);}
        protected String idPrefix(){return "soul";}
        protected String legacyId(){return idPrefix()+"_"+worldPosition.getX()+"_"+worldPosition.getY()+"_"+worldPosition.getZ();}
        protected String defaultId(){return level instanceof ServerLevel server?SoulObjectiveRegistry.get(server).automaticId(worldPosition,this instanceof CerberusSystem.Head,objectiveId):"";}
        public boolean canAccept(){return enabled&&!completed&&collected+orbs.size()<required;}
        protected void accept(LivingDeathEvent event,Vec3 from,ServerLevel server){
            SoulOrbEntity visual=me.cryo.zombierool.init.ZombieroolModEntities.SOUL_ORB.get().create(server);visual.route(from,center());
            if(!server.addFreshEntity(visual))return;
            var killer=event.getSource().getEntity();orbs.add(new Orb(visual,killer instanceof ServerPlayer p?p.getUUID():null));lastSoul=server.getGameTime();setChanged();
            server.playSound(null,from.x,from.y,from.z,me.cryo.zombierool.init.ZombieroolModSounds.SOUL_RELEASE.get(),SoundSource.BLOCKS,.45f,1+server.random.nextFloat()*.15f);
            server.sendParticles(RED,from.x,from.y,from.z,16,.2,.15,.2,.025);
        }
        public Vec3 center(){return Vec3.atCenterOf(worldPosition).add(0,.2,0);}
        public CompoundTag config(){var n=new CompoundTag();n.putString("objectiveId",objectiveId);n.putBoolean("enabled",enabled);n.putInt("souls",required);n.putDouble("radius",radius);n.putInt("rewardPoints",rewardPoints);n.putString("rewardItem",rewardItem);n.putInt("rewardCount",rewardCount);n.putInt("rewardChannel",rewardChannel);n.putBoolean("redstone",redstone);n.putBoolean("rewardAll",rewardAll);n.putInt("resetAfter",resetAfter);n.putString("resetSignal",resetSignal);return n;}
        public void configure(CompoundTag n){
            objectiveId=SoulObjectiveRegistry.cleanId(n.getString("objectiveId"));if(objectiveId.isBlank())objectiveId=defaultId();
            enabled=n.getBoolean("enabled");required=Math.max(1,Math.min(100000,n.getInt("souls")));radius=Double.isFinite(n.getDouble("radius"))?Math.max(1,Math.min(128,n.getDouble("radius"))):8;
            rewardPoints=Math.max(0,Math.min(1000000,n.getInt("rewardPoints")));rewardCount=Math.max(1,Math.min(64,n.getInt("rewardCount")));rewardChannel=Math.max(-1,Math.min(10000,n.getInt("rewardChannel")));
            rewardItem=n.getString("rewardItem").trim();if(rewardItem.length()>128)rewardItem="";
            resetSignal=n.getString("resetSignal").trim();if(resetSignal.length()>64)resetSignal="";
            redstone=n.getBoolean("redstone");rewardAll=n.getBoolean("rewardAll");resetAfter=Math.max(0,Math.min(86400,n.getInt("resetAfter")));reset();
        }
        private void clearOrbs(){for(Orb orb:orbs)orb.visual.discard();orbs.clear();}
        public void reset(){if(level instanceof ServerLevel server)resetEpoch=SoulObjectiveRegistry.get(server).epoch;collected=0;completed=false;clearOrbs();lastSoul=0;sync();}
        protected void sync(){setChanged();if(level instanceof ServerLevel server)SoulObjectiveRegistry.get(server).update(this);if(level!=null){BlockState state=getBlockState();boolean open=collected>0&&!completed;if(!level.isClientSide&&(state.getValue(ChestBlock.OPEN)!=open||state.getValue(ChestBlock.COMPLETED)!=completed))level.setBlock(worldPosition,state.setValue(ChestBlock.OPEN,open).setValue(ChestBlock.COMPLETED,completed),3);level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());}}
        protected void tick(){
            if(!(level instanceof ServerLevel server))return;ensureEpoch(server);if(!enabled)return;
            boolean signal=!resetSignal.isBlank()&&me.cryo.zombierool.config.MultiReceptorState.isActivated(level,resetSignal);
            if(signal&&!signalWasActive)reset();signalWasActive=signal;
            if(!completed&&collected>0&&resetAfter>0&&lastSoul>0&&level.getGameTime()-lastSoul>=resetAfter*20L)reset();
            if(collected>0&&!completed&&level.getGameTime()%6==0){double angle=level.getGameTime()*.14;Vec3 p=center().add(Math.cos(angle)*.45,.15,Math.sin(angle)*.45);server.sendParticles(RED,p.x,p.y,p.z,2,.02,.02,.02,0);}
            for(Orb orb:List.copyOf(orbs)){
                if(orb.visual.isRemoved()){orbs.remove(orb);continue;}
                orb.visual.advance(++orb.tick);Vec3 p=orb.visual.position();
                server.sendParticles(RED,p.x,p.y,p.z,5,.045,.045,.045,0);
                if(orb.tick>=SoulOrbEntity.DURATION){
                    orb.visual.discard();orbs.remove(orb);if(completed)continue;
                    if(collected==0)server.playSound(null,worldPosition,me.cryo.zombierool.init.ZombieroolModSounds.SOUL_CHEST_OPEN.get(),SoundSource.BLOCKS,.65f,1);
                    collected=Math.min(required,collected+1);lastSoul=level.getGameTime();
                    if(orb.killer!=null)me.cryo.zombierool.career.CareerManager.progressGameplay(server.getServer().getPlayerList().getPlayer(orb.killer),me.cryo.zombierool.career.CareerManager.ChallengeType.SOULS_COLLECTED,1);
                    LuaScriptManager.callEvent("OnSoulCollected",objectiveId,collected,required,orb.killer==null?"":orb.killer.toString());
                    server.playSound(null,worldPosition,me.cryo.zombierool.init.ZombieroolModSounds.SOUL_ABSORB.get(),SoundSource.BLOCKS,.5f,.85f+.3f*collected/required);
                    server.sendParticles(RED,center().x,center().y+.2,center().z,22,.22,.15,.22,.03);
                    if(collected>=required)complete(server,orb.killer);sync();
                }
            }
        }
        protected void complete(ServerLevel server,UUID killer){
            completed=true;
            SoulObjectiveRegistry.get(server).update(this);
            List<ServerPlayer> players=server.players().stream().filter(TeleporterSystem::validPlayer).filter(p->rewardAll?p.position().distanceToSqr(center())<=radius*radius:killer!=null&&p.getUUID().equals(killer)).toList();
            for(ServerPlayer player:players){
                me.cryo.zombierool.career.CareerManager.progressGameplay(player,me.cryo.zombierool.career.CareerManager.ChallengeType.OBJECTIVES_COMPLETED,1);
                if(rewardPoints>0)PointManager.modifyScore(player,rewardPoints,true);
                if(!rewardItem.isBlank()){
                    ItemStack stack=me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack(rewardItem,false,player);
                    if(stack.isEmpty()){var id=ResourceLocation.tryParse(rewardItem);if(id!=null&&ForgeRegistries.ITEMS.containsKey(id))stack=new ItemStack(ForgeRegistries.ITEMS.getValue(id),rewardCount);}
                    if(!stack.isEmpty()){stack.setCount(Math.min(rewardCount,stack.getMaxStackSize()));if(!player.addItem(stack))player.drop(stack,false);}
                }
                player.displayClientMessage(Component.translatable("message.zombierool.soul.complete"),true);
            }
            if(rewardChannel>=0)WaveManager.unlockChannel(rewardChannel);
            completionSound(server);
            for(int i=0;i<32;i++){double angle=i*Math.PI/16;Vec3 p=center().add(Math.cos(angle)*.8,.25,Math.sin(angle)*.8);server.sendParticles(RED,p.x,p.y,p.z,3,.035,.06,.035,.02);}
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,center().x,center().y+.4,center().z,35,.5,.3,.5,.1);
            LuaScriptManager.callEvent(this instanceof CerberusSystem.Head?"OnCerberusCompleted":"OnSoulChestCompleted",worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),required);
            LuaScriptManager.callEvent("OnSoulObjectiveCompleted",objectiveId);
        }
        protected void completionSound(ServerLevel server){server.playSound(null,worldPosition,me.cryo.zombierool.init.ZombieroolModSounds.SOUL_COMPLETE.get(),SoundSource.BLOCKS,1,1);}
        @Override public void onLoad(){super.onLoad();if(level!=null){LOADED.computeIfAbsent(level,l->new HashMap<>()).put(worldPosition,this);if(level instanceof ServerLevel server){if(objectiveId.isBlank()||objectiveId.equals(legacyId()))objectiveId=defaultId();ensureEpoch(server);sync();}}}
        protected void ensureEpoch(ServerLevel server){if(resetEpoch!=SoulObjectiveRegistry.get(server).epoch)reset();}
        @Override public void setRemoved(){clearOrbs();if(level!=null)LOADED.getOrDefault(level,new HashMap<>()).remove(worldPosition);super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.merge(config());n.putLong("objectiveEpoch",resetEpoch);n.putInt("collected",collected);n.putBoolean("completed",completed);n.putLong("lastSoul",lastSoul);}
        @Override public void load(CompoundTag n){super.load(n);objectiveId=n.getString("objectiveId");resetEpoch=n.getLong("objectiveEpoch");enabled=!n.contains("enabled")||n.getBoolean("enabled");required=n.contains("souls")?Math.max(1,n.getInt("souls")):20;radius=n.contains("radius")?n.getDouble("radius"):8;collected=Math.max(0,n.getInt("collected"));completed=n.getBoolean("completed");rewardPoints=n.getInt("rewardPoints");rewardItem=n.getString("rewardItem");rewardCount=n.contains("rewardCount")?Math.max(1,n.getInt("rewardCount")):1;rewardChannel=n.contains("rewardChannel")?n.getInt("rewardChannel"):-1;resetAfter=n.getInt("resetAfter");resetSignal=n.getString("resetSignal");redstone=!n.contains("redstone")||n.getBoolean("redstone");rewardAll=!n.contains("rewardAll")||n.getBoolean("rewardAll");lastSoul=n.getLong("lastSoul");orbs.clear();}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}
