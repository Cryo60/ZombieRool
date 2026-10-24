package me.cryo.zombierool.block.system;

import java.util.*;
import me.cryo.zombierool.block.AbstractTechnicalBlock;
import me.cryo.zombierool.config.GlobalSwitchState;
import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.gameplay.MapPower;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Nine physical tiles share a single controller, price and trip state. */
public final class TeleporterSystem {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> PLATFORM=BLOCKS.register("teleporter",Platform::new);
    public static final RegistryObject<Block> SWITCH=BLOCKS.register("teleporter_switch",ConditionSwitch::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("teleporter",()->new PlatformItem());
    public static final RegistryObject<Item> SWITCH_ITEM=ITEMS.register("teleporter_switch",()->new BlockItem(SWITCH.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<Pad>> TYPE=ENTITIES.register("teleporter",()->BlockEntityType.Builder.of(Pad::new,PLATFORM.get(),SWITCH.get()).build(null));
    private static final Map<Level,Map<BlockPos,Pad>> LOADED=new WeakHashMap<>();
    private static final Map<Level,Set<BlockPos>> REMOVING=new WeakHashMap<>();
    private TeleporterSystem(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().equals(new ResourceLocation("zombierool","zb_rct"))){e.accept(ITEM.get());e.accept(SWITCH_ITEM.get());}});}
    public static void unload(Level level){LOADED.remove(level);REMOVING.remove(level);}
    public static List<Pad> loaded(Level level){return List.copyOf(LOADED.getOrDefault(level,Map.of()).values());}
    public static BlockPos center(BlockState s,BlockPos p){if(!(s.getBlock() instanceof Platform))return p;int part=s.getValue(Platform.PART);return p.offset(1-part%3,0,1-part/3);}
    public static Pad pad(Level level,BlockPos p){return level.getBlockEntity(center(level.getBlockState(p),p)) instanceof Pad pad?pad:null;}
    public static Pad find(ServerLevel level,String id){
        if(id.isBlank())return null;
        List<BlockPos> positions=TeleporterRegistry.get(level).named(id);
        if(positions.size()!=1)return null; // Duplicate IDs must not select an arbitrary destination.
        BlockPos pos=positions.get(0);level.getChunkAt(pos);
        Pad pad=pad(level,pos);
        if(pad==null)TeleporterRegistry.get(level).remove(pos);
        return pad!=null&&pad.config.getBoolean("enabled")&&!pad.role().equals("SWITCH")?pad:null;
    }
    public static void reset(ServerLevel level){TeleporterTravel.reset(level);TeleporterRegistry.get(level).reset();for(Pad p:loaded(level)){p.linked=false;p.pendingUntil=p.cooldownUntil=0;p.busy=false;p.sync();}}
    private static void sound(ServerLevel level,BlockPos p){level.playSound(null,p,SoundEvents.BEACON_ACTIVATE,SoundSource.BLOCKS,1,1);}
    public static void interact(ServerPlayer player,BlockPos pos){Pad p=pad(player.level(),pos);if(p!=null){String error=player.isCreative()&&!p.role().equals("SWITCH")?TeleporterTravel.creativePreview(player,p):p.activate(player);if(!error.isEmpty())player.displayClientMessage(p.failureMessage(error),true);}}
    public static boolean validPlayer(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!p.isCreative();}
    public static class PlatformItem extends BlockItem {
        PlatformItem(){super(PLATFORM.get(),new Item.Properties());}
        @Override public InteractionResult place(BlockPlaceContext c){
            BlockPos center=c.getClickedPos();
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                BlockPos part=center.offset(x,0,z);
                if(!c.getLevel().getWorldBorder().isWithinBounds(part)||!c.getLevel().getBlockState(part).canBeReplaced() || c.getPlayer()!=null&&!c.getPlayer().mayUseItemAt(part,c.getClickedFace(),c.getItemInHand()))return InteractionResult.FAIL;
                if(c.getLevel().getBlockState(part.below()).getCollisionShape(c.getLevel(),part.below()).isEmpty())return InteractionResult.FAIL;
            }
            return super.place(c);
        }
    }
    public static class Platform extends Block implements EntityBlock, me.cryo.zombierool.block.WaterloggedElement {
        public static final IntegerProperty PART=IntegerProperty.create("part",0,8);
        public static final BooleanProperty INVISIBLE=BooleanProperty.create("invisible");
        public static final BooleanProperty LIT=BlockStateProperties.LIT;
        Platform(){super(Properties.of().strength(-1,3600000).noOcclusion().lightLevel(s->s.getValue(LIT)&&!s.getValue(INVISIBLE)?7:0));registerDefaultState(stateDefinition.any().setValue(PART,4).setValue(LIT,false).setValue(INVISIBLE,false));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(PART,LIT,INVISIBLE);}
        private static final VoxelShape[] ROUNDED=java.util.stream.IntStream.range(0,9).mapToObj(Platform::buildRounded).toArray(VoxelShape[]::new);
        public static VoxelShape rounded(int part){return ROUNDED[part];}
        private static VoxelShape buildRounded(int part){
            VoxelShape shape=Shapes.empty();int ox=part%3*16,oz=part/3*16;
            for(int x=0;x<16;x++){double gx=ox+x+.5-24,span=Math.sqrt(Math.max(0,576-gx*gx)),lo=Math.max(0,24-span-oz),hi=Math.min(16,24+span-oz);if(hi>lo)shape=Shapes.or(shape,Block.box(x,0,lo,x+1,2,hi));}
            return shape.optimize();
        }

        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(INVISIBLE)&&!(c instanceof EntityCollisionContext ec&&ec.getEntity() instanceof Player player&&player.isCreative())?Shapes.empty():rounded(s.getValue(PART));}
        @Override public VoxelShape getCollisionShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return s.getValue(INVISIBLE)?Shapes.empty():getShape(s,l,p,c);}
        @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.level.pathfinder.PathComputationType type){return type==net.minecraft.world.level.pathfinder.PathComputationType.LAND;}
        @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.entity.Mob mob){return net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return s.getValue(PART)==4?new Pad(p,s):null;}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide&&t==TYPE.get()?(world,p,state,be)->((Pad)be).tick():null;}
        @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity e,ItemStack stack){if(l.isClientSide)return;for(int i=0;i<9;i++)if(i!=4)l.setBlock(p.offset(i%3-1,0,i/3-1),s.setValue(PART,i),3);if(l.getBlockEntity(p) instanceof Pad pad)pad.sync();}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return usePad(l,p,player,hand);}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
            if(s.getBlock()!=next.getBlock()&&!l.isClientSide){
                BlockPos root=center(s,p);Set<BlockPos> removing=REMOVING.computeIfAbsent(l,k->new HashSet<>());
                if(removing.add(root)){
                    if(l instanceof ServerLevel server)TeleporterRegistry.get(server).remove(root);
                    for(int i=0;i<9;i++){BlockPos q=root.offset(i%3-1,0,i/3-1);if(!q.equals(p)&&l.getBlockState(q).getBlock()==this)l.removeBlock(q,false);}
                    removing.remove(root);
                }
            }
            super.onRemove(s,l,p,next,moving);
        }
    }
    private static InteractionResult usePad(Level l,BlockPos p,Player player,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(!l.isClientSide&&player instanceof ServerPlayer sp){Pad pad=pad(l,p);if(pad!=null){if(sp.isCreative())TransportPackets.open(sp,pad.getBlockPos(),"teleporter",pad.config.copy());else interact(sp,p);}}
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
    public static class ConditionSwitch extends Block implements EntityBlock, me.cryo.zombierool.block.WaterloggedElement {
        ConditionSwitch(){super(Properties.of().strength(-1,3600000).noCollission().noOcclusion());}
        @Override public void appendHoverText(ItemStack stack,BlockGetter level,List<Component> list,TooltipFlag flag){list.add(Component.translatable("tooltip.zombierool.teleporter_switch"));}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(4,0,4,12,12,12);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return !l.isClientSide&&t==TYPE.get()?(world,p,state,be)->((Pad)be).tick():null;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Pad(p,s);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){return usePad(l,p,player,hand);}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
            if(s.getBlock()!=next.getBlock()&&l instanceof ServerLevel server){
                if(l.getBlockEntity(p) instanceof Pad pad)TeleporterRegistry.get(server).condition(pad.channel(),pad.config.getString("condition"),false);
                TeleporterRegistry.get(server).remove(p);
            }
            super.onRemove(s,l,p,next,moving);
        }
    }
    public static final class Pad extends BlockEntity {
        public CompoundTag config=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);
        public boolean linked,busy,powered,canConfirm;
        public long pendingUntil,cooldownUntil,chargeUntil;
        public int pendingSeconds,cooldownSeconds,unlockSeconds;
        public String status="";
        public Component interactionHint(boolean creative,String actionKey){
                if(creative&&!role().equals("SWITCH"))return Component.translatable("message.zombierool.teleporter.preview",actionKey,config.getString("destination"));
                else if(!config.getBoolean("enabled"))return Component.translatable("message.zombierool.teleporter.disabled");
                else if(readyInSeconds()>0)return Component.translatable("message.zombierool.teleporter.ready_in",readyInSeconds());
                else if(!powered && config.getBoolean("requiresPower"))return Component.translatable("message.zombierool.power_required");
                else if(busy)return Component.translatable("message.zombierool.teleporter.busy");
                else if(cooldownSeconds>0)return Component.translatable("message.zombierool.teleporter.cooldown_seconds",cooldownSeconds);
                else if(!status.isBlank())return Component.translatable(status);
                else if(role().equals("RECEIVER"))return Component.translatable("message.zombierool.teleporter.confirm",actionKey);
                else if(role().equals("SWITCH"))return Component.translatable("message.zombierool.teleporter.switch",actionKey);
                else if(pendingSeconds>0)return Component.translatable("message.zombierool.teleporter.countdown",config.getString("id"),pendingSeconds);
                else if(config.getBoolean("requiresLink")&&!linked)return Component.translatable("message.zombierool.teleporter.link",actionKey);
                else return config.getInt("cost")>0?Component.translatable("message.zombierool.teleporter.use",actionKey,config.getInt("cost")):Component.translatable("message.zombierool.teleporter.use_free",actionKey);
        }
        public int readyInSeconds(){int cooldown=level==null?0:(int)Math.max(0,(cooldownUntil-level.getGameTime()+19)/20);int unlock=level instanceof ServerLevel?(me.cryo.zombierool.gameplay.WaveManager.isServerGameRunning()?(int)Math.max(0,(config.getInt("unlockAfterSeconds")*20L-me.cryo.zombierool.gameplay.WaveManager.getMatchElapsedTicks()+19)/20):0):unlockSeconds;return Math.max(cooldown,unlock);}
        public Component failureMessage(String reason){return reason.equals("message.zombierool.teleporter.cooldown")||reason.equals("message.zombierool.teleporter.wait_start")?Component.translatable("message.zombierool.teleporter.ready_in",readyInSeconds()):Component.translatable(reason);}
        public Pad(BlockPos p,BlockState s){super(TYPE.get(),p,s);if(s.getBlock() instanceof ConditionSwitch)config.putString("role","SWITCH");}
        @Override public void setLevel(Level world){
            super.setLevel(world);
            // Client chunk packets attach block entities without the server's onLoad
            // path. Observers must register the same pad whose countdown is updated.
            if(world.isClientSide)LOADED.computeIfAbsent(world,k->new HashMap<>()).put(worldPosition,this);
        }
        public String role(){return config.getString("role");}
        public String channel(){return config.getString("channel");}
        public boolean hasPower(){return level!=null&&me.cryo.zombierool.gameplay.BlockPowerSettings.required(level,worldPosition,config.getBoolean("requiresPower"),GlobalSwitchState.isActivated(level)||MapPower.receives(level,worldPosition));}
        public boolean onPlatform(ServerPlayer p){return p.serverLevel()==level&&new AABB(worldPosition).inflate(1,0,1).expandTowards(0,2,0).contains(p.position())&&Math.pow(p.getX()-worldPosition.getX()-.5,2)+Math.pow(p.getZ()-worldPosition.getZ()-.5,2)<=2.25;}
        public boolean cooling(){return level!=null&&cooldownUntil>level.getGameTime();}
        public String readiness(){return readiness(false);}
        public String readiness(boolean ignoreBusy){
            if(!config.getBoolean("enabled"))return "message.zombierool.teleporter.disabled";
            if(!hasPower())return "message.zombierool.power_required";
            if(busy&&!ignoreBusy)return "message.zombierool.teleporter.busy";
            if(cooling())return "message.zombierool.teleporter.cooldown";
            if(!(level instanceof ServerLevel server))return "";
            if(me.cryo.zombierool.gameplay.WaveManager.isServerGameRunning()&&me.cryo.zombierool.gameplay.WaveManager.getMatchElapsedTicks()<config.getInt("unlockAfterSeconds")*20L)return "message.zombierool.teleporter.wait_start";
            TeleporterRegistry registry=TeleporterRegistry.get(server);
            String required=config.getString("condition"),blocked=config.getString("blockedCondition");
            if(!required.isBlank()&&!role().equals("SWITCH")&&!registry.condition(channel(),required))return "message.zombierool.teleporter.condition";
            if(!blocked.isBlank()&&registry.condition(channel(),blocked))return "message.zombierool.teleporter.blocked";
            if(config.getString("mode").equals("DENIZEN")&&(!registry.condition(channel(),"energy")||!registry.condition(channel(),"denizen")))return "message.zombierool.teleporter.condition";
            return "";
        }
        public boolean pendingLink(){return level instanceof ServerLevel server && TeleporterRegistry.get(server).pads.values().stream().anyMatch(n->n.getBoolean("requiresLink")&&n.getString("channel").equals(channel())&&n.getLong("pendingUntil")>level.getGameTime()&&(n.getString("returnId").isBlank()?n.getString("destination"):n.getString("returnId")).equals(config.getString("id")));}
        public String activate(ServerPlayer player){
            if(!(level instanceof ServerLevel server)||!validPlayer(player))return "message.zombierool.teleporter.disabled";
            String error=readiness();if(!error.isEmpty())return error;
            if(role().equals("SWITCH")){
                String key=config.getString("condition");if(key.isBlank())return "message.zombierool.teleporter.no_destination";
                TeleporterRegistry r=TeleporterRegistry.get(server);r.condition(channel(),key,!r.condition(channel(),key));sound(server,worldPosition);return "";
            }
            if(role().equals("RECEIVER")) {
                boolean found=false;
                List<BlockPos> pending=TeleporterRegistry.get(server).pads.entrySet().stream()
                        .filter(e->e.getValue().getString("channel").equals(channel())&&e.getValue().getLong("pendingUntil")>level.getGameTime())
                        .map(Map.Entry::getKey).toList();
                for(BlockPos pos:pending){server.getChunkAt(pos);Pad source=pad(server,pos);
                    if(source!=null&&source!=this&&source.role().equals("SOURCE")&&source.pendingUntil>level.getGameTime()
                            &&source.linkReceiver().equals(config.getString("id"))&&source.hasPower()){
                        source.linked=true;source.pendingUntil=0;source.sync();found=true;
                        LuaScriptManager.callEvent("OnTeleporterLinked",source.config.getString("id"),config.getString("id"));
                    }
                }
                if(found){sound(server,worldPosition);player.displayClientMessage(Component.translatable("message.zombierool.teleporter.linked"),true);return "";}
                return "";
            }
            if(config.getBoolean("requiresLink")&&!linked){
                if(pendingUntil>level.getGameTime())return "message.zombierool.teleporter.linking";
                Pad receiver=find(server,linkReceiver());if(receiver==null||!receiver.role().equals("RECEIVER"))return "message.zombierool.teleporter.no_destination";
                pendingUntil=level.getGameTime()+config.getInt("linkTimeout")*20L;sync();sound(server,worldPosition);
                LuaScriptManager.callEvent("OnTeleporterLinkStarted",config.getString("id"),receiver.config.getString("id"));return "";
            }
            return TeleporterTravel.depart(player,this);
        }
        public String linkReceiver(){return config.getString("returnId").isBlank()?config.getString("destination"):config.getString("returnId");}
        public void configure(CompoundTag n){
            CompoundTag previous=config.copy();
            CompoundTag next=TeleporterConfig.sanitize(n);
            if(getBlockState().getBlock() instanceof ConditionSwitch)next.putString("role","SWITCH");
            if(next.getString("id").isBlank())next.putString("id",config.getString("id"));
            if(!next.getString("destination").equals(config.getString("destination"))||!next.getString("returnId").equals(config.getString("returnId"))||!next.getString("channel").equals(channel())||!next.getString("role").equals(role()))linked=false;
            if(role().equals("SWITCH")&&level instanceof ServerLevel server)TeleporterRegistry.get(server).condition(channel(),config.getString("condition"),false);
            config=next;pendingUntil=0;
            // Publish the draft before block updates can reload a controller from the registry.
            sync();if(level instanceof ServerLevel server)TeleporterRegistry.get(server).synchronize(server,this,previous);
            if(level instanceof ServerLevel && getBlockState().getBlock() instanceof Platform)for(int i=0;i<9;i++){
                BlockPos pos=worldPosition.offset(i%3-1,0,i/3-1);BlockState state=level.getBlockState(pos);
                if(state.getBlock() instanceof Platform&&state.getValue(Platform.INVISIBLE)!=config.getBoolean("invisible"))level.setBlock(pos,state.setValue(Platform.INVISIBLE,config.getBoolean("invisible")),3);
            }
        }
        public void tick(){
            if(!(level instanceof ServerLevel server))return;
            if(level.getGameTime()%20==0){
                if(pendingUntil>0&&(pendingUntil<=level.getGameTime()||!hasPower())){pendingUntil=0;sync();}
                canConfirm=role().equals("RECEIVER")&&pendingLink();powered=hasPower();pendingSeconds=(int)Math.max(0,(pendingUntil-level.getGameTime()+19)/20);cooldownSeconds=(int)Math.max(0,(cooldownUntil-level.getGameTime()+19)/20);
                boolean lit=role().equals("SOURCE")&&readiness().isEmpty()&&(!config.getBoolean("requiresLink")||linked);
                if(getBlockState().getBlock() instanceof Platform)for(int i=0;i<9;i++){BlockPos p=worldPosition.offset(i%3-1,0,i/3-1);BlockState s=level.getBlockState(p);if(s.getBlock() instanceof Platform&&(s.getValue(Platform.LIT)!=lit||s.getValue(Platform.INVISIBLE)!=config.getBoolean("invisible")))level.setBlock(p,s.setValue(Platform.LIT,lit).setValue(Platform.INVISIBLE,config.getBoolean("invisible")),3);}
                unlockSeconds=me.cryo.zombierool.gameplay.WaveManager.isServerGameRunning()?(int)Math.max(0,(config.getInt("unlockAfterSeconds")*20L-me.cryo.zombierool.gameplay.WaveManager.getMatchElapsedTicks()+19)/20):0;status=readiness();
                sync();
                if(pendingSeconds>0)for(ServerPlayer player:server.players())player.displayClientMessage(Component.translatable("message.zombierool.teleporter.countdown",config.getString("id"),pendingSeconds),true);
            }
            if(config.getBoolean("automatic")&&role().equals("SOURCE")&&readiness().isEmpty()&&(!config.getBoolean("requiresLink")||linked)){
                for(ServerPlayer p:server.players())if(validPlayer(p)&&onPlatform(p)&&TeleporterTravel.arrivalLock(p)<=level.getGameTime()&&(!config.getBoolean("jumpOnly")||!p.onGround())){
                    TeleporterTravel.depart(p,this);break;
                }
            }
        }
        public void sync(){setChanged();if(level instanceof ServerLevel server&&!isRemoved()&&server.getBlockEntity(worldPosition)==this){LOADED.computeIfAbsent(level,k->new HashMap<>()).put(worldPosition,this);TeleporterRegistry.get(server).update(this);level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}}
        @Override public void onLoad(){super.onLoad();if(level!=null&&!isRemoved()&&level.getBlockEntity(worldPosition)==this){LOADED.computeIfAbsent(level,k->new HashMap<>()).put(worldPosition,this);if(level instanceof ServerLevel server){CompoundTag old=TeleporterRegistry.get(server).pads.get(worldPosition);if(old!=null){config=TeleporterConfig.sanitize(old);linked=old.getBoolean("linked");cooldownUntil=old.getLong("cooldownUntil");pendingUntil=old.getLong("pendingUntil");}busy=TeleporterTravel.busy(server,worldPosition);sync();}}}
        @Override public void setRemoved(){if(level!=null)LOADED.getOrDefault(level,new HashMap<>()).remove(worldPosition,this);super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.put("config",config.copy());n.putBoolean("linked",linked);n.putBoolean("busy",busy);n.putLong("chargeUntil",chargeUntil);n.putBoolean("powered",powered);n.putBoolean("canConfirm",canConfirm);n.putLong("pendingUntil",pendingUntil);n.putLong("cooldownUntil",cooldownUntil);n.putInt("pendingSeconds",pendingSeconds);n.putInt("cooldownSeconds",cooldownSeconds);n.putInt("unlockSeconds",unlockSeconds);n.putString("status",status);}
        @Override public void load(CompoundTag n){super.load(n);if(n.contains("config"))config=TeleporterConfig.sanitize(n.getCompound("config"));linked=n.getBoolean("linked");busy=n.getBoolean("busy");chargeUntil=n.getLong("chargeUntil");powered=n.getBoolean("powered");canConfirm=n.getBoolean("canConfirm");pendingUntil=n.getLong("pendingUntil");cooldownUntil=n.getLong("cooldownUntil");pendingSeconds=n.getInt("pendingSeconds");cooldownSeconds=n.getInt("cooldownSeconds");unlockSeconds=n.getInt("unlockSeconds");status=n.getString("status");}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    }
}
