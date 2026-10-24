package me.cryo.zombierool.block.system;

import me.cryo.zombierool.config.MultiReceptorState;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
import java.util.*;

/** Physical landing markers; the map-wide sky controller lives in SavedData, outside the world. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class MachineDeliverySystem {
    public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    public static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> BLOCK=BLOCKS.register("machine_delivery",DeliveryBlock::new);
    public static final RegistryObject<Item> ITEM=ITEMS.register("machine_delivery",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final RegistryObject<BlockEntityType<Delivery>> ENTITY=ENTITIES.register("machine_delivery",()->BlockEntityType.Builder.of(Delivery::new,BLOCK.get()).build(null));
    public static final String PACK="pack_a_punch";
    public static final BlockPos SKY_POS=new BlockPos(0,-2048,0);
    private static final ThreadLocal<Boolean> MUTATING=ThreadLocal.withInitial(()->false);
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);ENTITIES.register(bus);MachineDeliveryPackets.register();bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey().location().toString().equals("zombierool:zb_rct"))e.accept(ITEM.get());});}

    public static List<String> catalog(){var ids=new TreeSet<>(PerksManager.ALL_PERKS.keySet());ids.add(PACK);return List.copyOf(ids);}
    public static int defaultPrice(String id){return switch(id){case PACK->5000;case "juggernog"->2500;case "speed_cola"->3000;case "quick_revive"->1500;default->2000;};}
    public static CompoundTag defaults(){
        var n=new CompoundTag();n.putString("role","landing");n.putString("mode","drop");n.putString("group","delivery");n.putString("trigger","signal");n.putString("channel","");
        n.putBoolean("enabled",true);n.putBoolean("noRepeats",true);n.putInt("firstRound",3);n.putInt("everyRounds",3);n.putInt("duration",100);n.putDouble("dropHeight",48);n.putDouble("hoverHeight",.6);
        n.putInt("baseOffset",0);n.putInt("layoutVersion",2);n.putInt("doorChannel",1);n.putInt("doorX",0);n.putInt("doorY",0);n.putInt("doorZ",0);n.putBoolean("doorAssigned",false);
        var pool=new ListTag();for(String id:List.of("juggernog","speed_cola","double_tap","quick_revive"))pool.add(StringTag.valueOf(id));n.put("pool",pool);
        var prices=new CompoundTag();for(String id:catalog())prices.putInt(id,defaultPrice(id));n.put("prices",prices);return n;
    }
    public static CompoundTag sanitize(CompoundTag input){
        var n=defaults();for(String key:n.getAllKeys()){var v=input.get(key);if(v!=null&&v.getId()==n.get(key).getId())n.put(key,v.copy());}
        if(!Set.of("controller","landing").contains(n.getString("role")))n.putString("role","controller");
        if(!Set.of("roll","drop").contains(n.getString("mode")))n.putString("mode","roll");
        if(!Set.of("signal","rounds","door","door_channel","manual").contains(n.getString("trigger")))n.putString("trigger","signal");
        for(String key:List.of("group","channel")){String s=n.getString(key).trim();n.putString(key,s.substring(0,Math.min(64,s.length())));}
        n.putInt("baseOffset",clamp(n.getInt("baseOffset"),0,1));n.putInt("doorChannel",clamp(n.getInt("doorChannel"),1,1000000));
        n.putInt("firstRound",clamp(n.getInt("firstRound"),1,100000));n.putInt("everyRounds",clamp(n.getInt("everyRounds"),1,100000));n.putInt("duration",clamp(n.getInt("duration"),20,1200));
        for(String key:List.of("dropHeight","hoverHeight")){double v=n.getDouble(key);n.putDouble(key,Double.isFinite(v)?Math.max(key.equals("dropHeight")?4:.1,Math.min(key.equals("dropHeight")?192:3,v)):1);}
        var pool=new ListTag();var seen=new HashSet<String>();for(Tag t:n.getList("pool",Tag.TAG_STRING)){String id=t.getAsString();if(id.length()<=128&&seen.add(id)&&pool.size()<128)pool.add(StringTag.valueOf(id));}n.put("pool",pool);
        var prices=new CompoundTag();for(String id:catalog())prices.putInt(id,clamp(n.getCompound("prices").contains(id)?n.getCompound("prices").getInt(id):defaultPrice(id),0,1000000));n.put("prices",prices);return n;
    }
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    public static final class Index extends SavedData {
        private final Set<BlockPos> positions=new HashSet<>();
        private final Map<BlockPos,CompoundTag> snapshots=new HashMap<>();
        private final Set<Integer> openedDoors=new HashSet<>();
        private CompoundTag skyTag=new CompoundTag();private Delivery sky;
        public static Index get(ServerLevel l){return l.getDataStorage().computeIfAbsent(n->{var d=new Index();for(long p:n.getLongArray("positions"))d.positions.add(BlockPos.of(p));for(Tag t:n.getList("sites",10)){var row=(CompoundTag)t;d.snapshots.put(BlockPos.of(row.getLong("pos")),row.getCompound("data").copy());}for(int c:n.getIntArray("openedDoors"))d.openedDoors.add(c);d.skyTag=n.getCompound("sky").copy();return d;},Index::new,"zombierool_machine_delivery");}
        @Override public CompoundTag save(CompoundTag n){n.putLongArray("positions",positions.stream().mapToLong(BlockPos::asLong).toArray());var sites=new ListTag();snapshots.forEach((p,data)->{var row=new CompoundTag();row.putLong("pos",p.asLong());row.put("data",data.copy());sites.add(row);});n.put("sites",sites);n.putIntArray("openedDoors",openedDoors.stream().mapToInt(Integer::intValue).toArray());n.put("sky",sky==null?skyTag:sky.saveWithoutMetadata());return n;}
        void add(BlockPos p){if(positions.add(p.immutable()))setDirty();}void remove(BlockPos p){positions.remove(p);snapshots.remove(p);setDirty();}
    }
    public static Delivery sky(ServerLevel l){var data=Index.get(l);if(data.sky==null){var d=new Delivery(SKY_POS,BLOCK.get().defaultBlockState());d.virtualSky=true;d.setLevel(l);var n=defaults();n.putString("role","controller");n.putString("mode","drop");n.putString("trigger","rounds");n.putInt("duration",200);n.putBoolean("enabled",false);n.getList("pool",8).add(StringTag.valueOf(PACK));d.config=n;if(!data.skyTag.isEmpty())d.load(data.skyTag);if(d.config.getInt("duration")==100){d.config.putInt("duration",200);data.setDirty();}data.sky=d;}return data.sky;}
    public static Delivery at(ServerLevel l,BlockPos p){
        if(p.equals(SKY_POS))return sky(l);if(!l.isInWorldBounds(p))return null;l.getChunkAt(p);if(l.getBlockEntity(p) instanceof Delivery d)return d;
        var saved=Index.get(l).snapshots.get(p);if(saved==null||saved.getString("Phase").equals("idle"))return null;
        var facing=Direction.byName(saved.getString("Facing"));if(facing==null||facing.getAxis().isVertical())facing=Direction.NORTH;var d=new Delivery(p,BLOCK.get().defaultBlockState().setValue(DeliveryBlock.FACING,facing));d.setLevel(l);d.load(saved);return d;
    }
    public static List<Delivery> all(ServerLevel l){var out=new ArrayList<Delivery>();for(BlockPos p:new ArrayList<>(Index.get(l).positions)){var d=at(l,p);if(d!=null)out.add(d);else Index.get(l).remove(p);}out.sort(Comparator.comparingLong(d->d.getBlockPos().asLong()));return out;}
    @net.minecraftforge.eventbus.api.SubscribeEvent public static void tickLevel(net.minecraftforge.event.TickEvent.LevelTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END&&e.level instanceof ServerLevel l&&Index.get(l).sky!=null)sky(l).tick();}
    public static void onWaveStart(ServerLevel l,int round){sky(l).round(round);for(var d:all(l))if(!d.config.getString("mode").equals("drop"))d.round(round);}
    public static void onDoorOpened(ServerLevel l,int channel){if(channel<=0||!WaveManager.isGameRunning())return;var data=Index.get(l);if(!data.openedDoors.add(channel))return;data.setDirty();for(var d:all(l))if(d.controller()&&d.config.getString("trigger").equals("door_channel")&&d.config.getInt("doorChannel")==channel)d.trigger("",null);}
    public static void reset(ServerLevel l){for(var d:all(l))d.reset();sky(l).reset();Index.get(l).openedDoors.clear();Index.get(l).setDirty();}

    public static final class DeliveryBlock extends HorizontalDirectionalBlock implements EntityBlock {
        public DeliveryBlock(){super(Properties.of().strength(-1,3600000).noOcclusion().noCollission().isRedstoneConductor((st,w,p)->false));registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING);}
        @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
        @Override public BlockState rotate(BlockState s,Rotation r){return s.setValue(FACING,r.rotate(s.getValue(FACING)));}
        @Override public BlockState mirror(BlockState s,Mirror m){return rotate(s,m.getRotation(s.getValue(FACING)));}
        @Override public RenderShape getRenderShape(BlockState s){return net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()&&me.cryo.zombierool.client.TechnicalBlockClient.shouldBeInvisible()?RenderShape.INVISIBLE:RenderShape.MODEL;}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(0,0,0,16,1,16);}
        @Override public boolean isPathfindable(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.level.pathfinder.PathComputationType t){return true;}
        @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(BlockState s,BlockGetter l,BlockPos p,net.minecraft.world.entity.Mob mob){return net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN;}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Delivery(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return t!=ENTITY.get()?null:l.isClientSide?(w,p,st,be)->me.cryo.zombierool.client.MachineDeliveryEffects.tick((Delivery)be):(w,p,st,be)->((Delivery)be).tick();}
        @Override public void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moving){if(l instanceof ServerLevel server)Index.get(server).add(p);super.onPlace(s,l,p,old,moving);}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand h,BlockHitResult hit){if(!l.isClientSide&&h==InteractionHand.MAIN_HAND&&player instanceof ServerPlayer sp&&sp.isCreative()&&l.getBlockEntity(p) instanceof Delivery d)MachineDeliveryPackets.open(sp,d);return InteractionResult.sidedSuccess(l.isClientSide);}
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){if(s.getBlock()!=next.getBlock()&&!MUTATING.get()&&l instanceof ServerLevel server){if(l.getBlockEntity(p) instanceof Delivery d)d.reset();Index.get(server).remove(p);}super.onRemove(s,l,p,next,moving);}
        @Override public void appendHoverText(ItemStack s,BlockGetter l,List<Component> text,TooltipFlag flags){text.add(Component.translatable("gui.zombierool.delivery.placement"));}
    }

    public static final class Delivery extends BlockEntity {
        private CompoundTag config=defaults();
        public boolean virtualSky;
        public String phase="idle",selected="",error="";
        public long started;
        public int animationDuration=100,lastRound,queued,deliveries;
        public boolean falling,lastSignal,doorSeen;
        public BlockPos controller;
        public UUID token;
        private ListTag replaced=new ListTag();
        private CompoundTag flightConfig=new CompoundTag();
        public Delivery(BlockPos p,BlockState s){super(ENTITY.get(),p,s);}
        public CompoundTag config(){return config.copy();}
        public boolean controller(){return virtualSky||config.getString("role").equals("controller");}
        public int baseOffset(){return config.getInt("baseOffset");}
        public void configure(CompoundTag n){var next=sanitize(n);if(virtualSky){next.putString("role","controller");next.putString("mode","drop");next.putString("trigger","rounds");}else if(next.getString("role").equals("controller")&&next.getString("mode").equals("drop")){next.putString("role","landing");}if((!phase.equals("idle")||deliveries>0)&&(!next.getString("role").equals(config.getString("role"))||!next.getString("group").equals(config.getString("group"))))throw new IllegalStateException("Reset deliveries before changing their role or group");config=next;error="";sync();}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel l){Index.get(l).add(worldPosition);if(!virtualSky&&controller()&&config.getString("mode").equals("drop")){var global=sky(l);if(!global.config.getBoolean("enabled"))global.configure(config);config.putString("role","landing");config.putBoolean("enabled",false);}sync();}}
        public CompoundTag status(){var n=config();n.putBoolean("skySettings",virtualSky);n.putString("phase",phase);n.putString("selected",selected);n.putString("error",error);n.putInt("deliveries",deliveries);n.putInt("queued",queued);n.putInt("lastRound",lastRound);n.putLong("started",started);if(controller!=null)n.putLong("controller",controller.asLong());return n;}
        public List<String> choices(){var result=new ArrayList<String>();for(Tag t:config.getList("pool",Tag.TAG_STRING))if(catalog().contains(t.getAsString()))result.add(t.getAsString());return result;}
        public void round(int round){if(!controller()||!config.getBoolean("enabled")||!config.getString("trigger").equals("rounds")||round<=lastRound)return;lastRound=round;int first=config.getInt("firstRound");if(round>=first&&(round-first)%config.getInt("everyRounds")==0){queued=Math.min(128,queued+1);sync();drain();}else sync();}
        private boolean busy(){if(!(level instanceof ServerLevel l))return false;for(var d:all(l))if(worldPosition.equals(d.controller)&&d.phase.equals("animating"))return true;return false;}
        private void drain(){if(queued>0&&!busy()){queued--;trigger("",null);sync();}}
        /** Returns an empty string on success. A forced destination must still be a valid free linked site. */
        public String trigger(String forced,BlockPos destination){
            if(!(level instanceof ServerLevel l)||!controller()||!config.getBoolean("enabled"))return fail("disabled");
            if(busy())return fail("busy");
            var group=all(l);
            var used=new HashSet<String>();if(config.getBoolean("noRepeats"))for(var d:group)if(!d.phase.equals("idle")&&d.falling==config.getString("mode").equals("drop"))used.add(d.selected);
            var choices=choices();choices.removeIf(used::contains);if(!forced.isEmpty())choices.removeIf(id->!id.equals(forced));if(choices.isEmpty())return fail("empty_pool");
            boolean drop=config.getString("mode").equals("drop");
            var sites=drop?new ArrayList<>(group.stream().filter(d->!d.controller()&&d.config.getBoolean("enabled")&&d.phase.equals("idle")).toList()):new ArrayList<>(List.of(this));
            if(!drop&&!phase.equals("idle"))return fail("occupied");if(destination!=null)sites.removeIf(d->!d.worldPosition.equals(destination));
            Collections.shuffle(choices,new Random(l.random.nextLong()));Collections.shuffle(sites,new Random(l.random.nextLong()));
            Delivery target=null;String choice="";outer:for(var site:sites)for(String id:choices)if(site.clearance(id)){target=site;choice=id;break outer;}
            if(target==null)return fail("no_site");
            // Capture the idle marker before changing its block-entity state. Otherwise the
            // world journal restores an animating marker at match end and it lands again.
            MatchWorldJournal.note(l,target.worldPosition);
            target.selected=choice;target.phase="animating";target.controller=worldPosition;target.token=UUID.randomUUID();target.started=l.getGameTime();target.animationDuration=config.getInt("duration");target.falling=drop;target.flightConfig=config();target.error="";error="";target.sync();sync();
            if(!drop)l.playSound(null,target.worldPosition,SoundEvents.BEACON_ACTIVATE,SoundSource.BLOCKS,2,.8f);
            target.event("OnMachineDeliveryStarted");return "";
        }
        private String fail(String reason){error=reason;sync();return reason;}
        public boolean clearance(String id){int height=id.equals(PACK)?2:3;for(int y=baseOffset();y<baseOffset()+height;y++){var p=worldPosition.above(y);if(!level.isInWorldBounds(p)||!level.hasChunkAt(p))return false;var state=level.getBlockState(p);if(!(p.equals(worldPosition)&&state.is(BLOCK.get()))&&!state.canBeReplaced()&&!state.isAir()&&!(state.getBlock() instanceof me.cryo.zombierool.block.PathBlock))return false;}return true;}
        public double height(float partial){double t=Math.max(0,Math.min(1,(level.getGameTime()+partial-started)/(double)Math.max(1,animationDuration)));if(falling)return flightConfig.getDouble("dropHeight")*(1-t*t);double settle=1-Math.pow(Math.max(0,(t-.8)/.2),2);return flightConfig.getDouble("hoverHeight")*settle+Math.sin(t*24)*.08*settle;}
        public String displayed(float partial){if(falling)return selected;double t=Math.max(0,Math.min(1,(level.getGameTime()+partial-started)/(double)Math.max(1,animationDuration)));var pool=flightConfig.getList("pool",Tag.TAG_STRING);if(t>=.9||pool.isEmpty())return selected;return pool.getString((int)(Math.sqrt(t)*35)%pool.size());}
        public void tick(){
            if(!(level instanceof ServerLevel l))return;
            if(phase.equals("animating")){
                if(!clearLandingPlayers()){if(l.getGameTime()-started>=animationDuration)return;}
                if(l.getGameTime()-started>=animationDuration){land();return;}
                if(l.getGameTime()%4==0){double y=worldPosition.getY()+baseOffset()+height(0);l.sendParticles(falling?ParticleTypes.CLOUD:ParticleTypes.ENCHANT,worldPosition.getX()+.5,y+.4,worldPosition.getZ()+.5,6,.5,.2,.5,.025);if(!falling&&l.getGameTime()%12==0)l.playSound(null,worldPosition,SoundEvents.NOTE_BLOCK_CHIME.value(),SoundSource.BLOCKS,.6f,1+(float)(l.getGameTime()-started)/animationDuration);}
            }
            if(!controller()||!config.getBoolean("enabled")||!WaveManager.isGameRunning()||l.getGameTime()%5!=0)return;
            String trigger=config.getString("trigger");
            if(trigger.equals("signal")){boolean powered=l.hasNeighborSignal(worldPosition)||!config.getString("channel").isEmpty()&&MultiReceptorState.isActivated(l,config.getString("channel"));if(powered&&!lastSignal)trigger("",null);lastSignal=powered;}
            if(trigger.equals("door")&&config.getBoolean("doorAssigned")){
                var p=new BlockPos(config.getInt("doorX"),config.getInt("doorY"),config.getInt("doorZ"));if(l.hasChunkAt(p)){var state=l.getBlockState(p);boolean closed=state.getBlock() instanceof ObstacleDoorSystem.ObstacleDoorBlock||state.getBlock() instanceof DoorBlock&& !state.getValue(DoorBlock.OPEN);if(closed)doorSeen=true;else if(doorSeen){doorSeen=false;trigger("",null);}}
            }
            drain();
        }
        /** Never lift an intersecting player onto a machine. Reserve its footprint throughout the animation. */
        public AABB landingVolume(){var base=worldPosition.above(baseOffset());return new AABB(base.getX()-.08,base.getY()-.02,base.getZ()-.08,base.getX()+1.08,base.getY()+(selected.equals(PACK)?1.5:3),base.getZ()+1.08);}
        public boolean clearLandingPlayers(){
            if(!(level instanceof ServerLevel l))return false;var volume=landingVolume();boolean clear=true;
            for(var player:l.getEntitiesOfClass(ServerPlayer.class,volume.inflate(.05),p->p.isAlive()&&!p.isSpectator())){
                var box=player.getBoundingBox();net.minecraft.world.phys.Vec3 safe=null;double best=Double.MAX_VALUE;
                // Search horizontally and downward, never upward: no route onto the machine's roof.
                int ground=(int)Math.floor(Math.min(player.getY(),worldPosition.getY()+baseOffset()));
                for(int radius=1;radius<=4;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
                    if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                    for(int down=0;down<=2;down++){
                        double x=worldPosition.getX()+.5+dx,y=ground-down,z=worldPosition.getZ()+.5+dz;
                        var candidate=box.move(x-player.getX(),y-player.getY(),z-player.getZ());
                        var feet=BlockPos.containing(x,y-.05,z);if(!l.hasChunkAt(feet)||candidate.intersects(volume)||!l.getWorldBorder().isWithinBounds(candidate)||!l.noCollision(player,candidate)||!l.getFluidState(BlockPos.containing(x,y,z)).isEmpty())continue;
                        var support=l.getBlockState(feet);if(support.getCollisionShape(l,feet).isEmpty()||support.is(Blocks.MAGMA_BLOCK)||support.is(Blocks.CACTUS)||support.is(Blocks.CAMPFIRE)||support.is(Blocks.SOUL_CAMPFIRE))continue;
                        if(!l.getEntities(player,candidate,e->e.isPickable()&&!e.isSpectator()).isEmpty())continue;
                        double distance=player.position().distanceToSqr(x,y,z);if(distance<best){best=distance;safe=new Vec3(x,y,z);}
                    }
                    if(safe!=null&&radius>1)break;
                }
                if(safe==null){clear=false;continue;}
                player.stopRiding();player.setPos(safe.x,safe.y,safe.z);player.connection.teleport(safe.x,safe.y,safe.z,player.getYRot(),player.getXRot());player.setDeltaMovement(0,Math.min(0,player.getDeltaMovement().y),0);player.fallDistance=0;player.hurtMarked=true;
            }
            return clear;
        }
        private void land(){
            var l=(ServerLevel)level;if(!clearLandingPlayers())return;if(!clearance(selected)){phase="idle";error="blocked_landing";event("OnMachineDeliveryFailed");selected="";controller=null;sync();return;}
            replaced=new ListTag();for(int i=baseOffset();i<baseOffset()+(selected.equals(PACK)?2:3);i++){var p=worldPosition.above(i);var row=new CompoundTag();row.putLong("pos",p.asLong());row.put("state",NbtUtils.writeBlockState(l.getBlockState(p)));replaced.add(row);}
            MUTATING.set(true);try{
            var base=worldPosition.above(baseOffset());var facing=getBlockState().getValue(DeliveryBlock.FACING);BlockEntity main;
            if(selected.equals(PACK)){l.setBlock(base,PackAPunchSystem.PACK_A_PUNCH.get().defaultBlockState().setValue(PackAPunchSystem.PackAPunchBlock.FACING,facing),3);main=l.getBlockEntity(base);if(main instanceof PackAPunchSystem.PackAPunchBlockEntity p)p.setPrice(price());}
            else{
                var p=base.above();l.setBlock(base,PerksSystem.DUMMY_BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaDummyBlock.PART,PerksSystem.DummyPart.LOWER).setValue(PerksSystem.PerksAColaDummyBlock.FACING,facing),3);
                l.setBlock(p.above(),PerksSystem.DUMMY_BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaDummyBlock.PART,PerksSystem.DummyPart.UPPER).setValue(PerksSystem.PerksAColaDummyBlock.FACING,facing),3);
                l.setBlock(p,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.FACING,facing).setValue(PerksSystem.PerksAColaBlock.PERK_TYPE,PerksSystem.PerkType.fromString(selected)),3);main=l.getBlockEntity(p);
                if(main instanceof PerksSystem.PerksAColaBlockEntity perk){perk.setSavedPerkId(selected);perk.setSavedPrice(price());MapPower.syncPerk(l,p);}
            }
            if(main==null){phase="idle";error="placement_failed";sync();return;}
            main.getPersistentData().putUUID("zr_delivery",token);main.setChanged();phase="landed";sync();var source=controller==null?null:at(l,controller);if(source!=null){source.deliveries++;source.sync();}
            MapPower.refreshSoon(l);l.sendParticles(ParticleTypes.POOF,base.getX()+.5,base.getY()+.1,base.getZ()+.5,35,.8,.15,.8,.08);l.playSound(null,base,falling?me.cryo.zombierool.init.ZombieroolModSounds.DELIVERY_IMPACT.get():SoundEvents.ANVIL_LAND,SoundSource.BLOCKS,2,falling?.7f:1.4f);event("OnMachineDeliveryLanded");
            }finally{MUTATING.set(false);}
        }
        private int price(){var n=flightConfig.getCompound("prices");return n.contains(selected)?n.getInt(selected):defaultPrice(selected);}
        private void event(String name){var c=controller==null?worldPosition:controller;LuaScriptManager.callEvent(name,c.getX(),c.getY(),c.getZ(),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),selected,falling?"drop":"roll",error);}
        public void reset(){
            if(level instanceof ServerLevel l&&!virtualSky&&phase.equals("landed")&&token!=null){
                var main=worldPosition.above(baseOffset()+(selected.equals(PACK)?0:1));var be=l.getBlockEntity(main);
                if(be!=null&&be.getPersistentData().hasUUID("zr_delivery")&&token.equals(be.getPersistentData().getUUID("zr_delivery"))){
                    MUTATING.set(true);try{l.removeBlock(main,false);for(Tag tag:replaced){var row=(CompoundTag)tag;var p=BlockPos.of(row.getLong("pos"));if(l.getBlockState(p).isAir())l.setBlock(p,NbtUtils.readBlockState(l.holderLookup(net.minecraft.core.registries.Registries.BLOCK),row.getCompound("state")),3);}}finally{MUTATING.set(false);}
                }
            }
            phase="idle";selected="";error="";controller=null;token=null;started=0;lastRound=0;queued=deliveries=0;lastSignal=doorSeen=false;flightConfig=new CompoundTag();replaced=new ListTag();
            if(!virtualSky&&level!=null&&level.getBlockEntity(worldPosition) instanceof Delivery actual&&actual!=this){actual.config=config.copy();actual.phase="idle";actual.selected="";actual.sync();}sync();
        }
        public int cancel(){if(!(level instanceof ServerLevel l))return 0;int count=0;for(var d:all(l))if(d.phase.equals("animating")&&(d==this||worldPosition.equals(d.controller))){d.error="cancelled";d.event("OnMachineDeliveryCancelled");d.reset();count++;}queued=0;sync();return count;}
        public void resetGroup(){if(level instanceof ServerLevel l){if(virtualSky){for(var d:all(l))if(d.falling||!d.controller())d.reset();reset();}else reset();}}
        private void sync(){if(!virtualSky)setChanged();if(level instanceof ServerLevel l){var data=Index.get(l);if(virtualSky)data.skyTag=saveWithoutMetadata();else{data.add(worldPosition);var saved=saveWithoutMetadata();data.snapshots.put(worldPosition.immutable(),saved);if(l.getBlockEntity(worldPosition) instanceof Delivery actual&&actual!=this){actual.load(saved);actual.setChanged();}var state=l.getBlockState(worldPosition);l.sendBlockUpdated(worldPosition,state,state,3);}data.setDirty();}}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.put("Config",config);n.putString("Facing",getBlockState().getValue(DeliveryBlock.FACING).getName());n.putString("Phase",phase);n.putString("Selected",selected);n.putString("Error",error);n.putLong("Started",started);n.putInt("Duration",animationDuration);n.putInt("LastRound",lastRound);n.putInt("Queued",queued);n.putInt("Deliveries",deliveries);n.putBoolean("Falling",falling);n.putBoolean("LastSignal",lastSignal);n.putBoolean("DoorSeen",doorSeen);if(controller!=null)n.putLong("Controller",controller.asLong());if(token!=null)n.putUUID("Token",token);n.put("Flight",flightConfig);n.put("Replaced",replaced);}
        @Override public void load(CompoundTag n){super.load(n);var raw=n.getCompound("Config");config=sanitize(raw);if(!raw.contains("layoutVersion")){config.putInt("baseOffset",1);config.putInt("layoutVersion",2);}phase=n.getString("Phase");if(!Set.of("idle","animating","landed").contains(phase))phase="idle";selected=n.getString("Selected");error=n.getString("Error");started=n.getLong("Started");animationDuration=Math.max(1,n.getInt("Duration"));lastRound=n.getInt("LastRound");queued=n.getInt("Queued");deliveries=n.getInt("Deliveries");falling=n.getBoolean("Falling");lastSignal=n.getBoolean("LastSignal");doorSeen=n.getBoolean("DoorSeen");controller=n.contains("Controller")?BlockPos.of(n.getLong("Controller")):null;token=n.hasUUID("Token")?n.getUUID("Token"):null;flightConfig=n.getCompound("Flight").copy();replaced=n.getList("Replaced",Tag.TAG_COMPOUND).copy();}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).inflate(3).expandTowards(0,200,0);}
    }
}
