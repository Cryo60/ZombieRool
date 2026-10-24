package me.cryo.zombierool.gameplay;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;

/** Reusable bench rewards share one carried/deployed slot per player. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class PortableEquipment {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,"zombierool");
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(ForgeRegistries.ITEMS,"zombierool");
    private static final DeferredRegister<BlockEntityType<?>> BES=DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,"zombierool");
    public static final RegistryObject<Block> SHIELD_BLOCK=BLOCKS.register("deployed_zombie_shield",()->new Deployed(false));
    public static final RegistryObject<Block> TURBINE_BLOCK=BLOCKS.register("deployed_turbine",()->new Deployed(true));
    public static final RegistryObject<Block> SENTRY_BLOCK=BLOCKS.register("deployed_sentry_turret",()->new Deployed(false,true));
    public static final RegistryObject<Item> SENTRY=ITEMS.register("sentry_turret",()->new Portable(false,true));
    public static final java.util.List<RegistryObject<Item>> SENTRY_PARTS=java.util.stream.Stream.of("sentry_tripod","sentry_receiver","sentry_ammo").map(id->ITEMS.register(id,()->new Item(new Item.Properties()))).toList();
    public static final java.util.List<RegistryObject<Item>> PAP_PARTS=java.util.stream.Stream.of("pap_frame","pap_gears","pap_sign").map(id->ITEMS.register(id,()->new Item(new Item.Properties()))).toList();
    public static final RegistryObject<Item> SHIELD=ITEMS.register("zombie_shield",()->new Portable(false));
    public static final RegistryObject<Item> TURBINE=ITEMS.register("turbine",()->new Portable(true));
    public static final java.util.List<RegistryObject<Item>> PARTS=java.util.stream.Stream.of("shield_car_door","shield_hand_truck","turbine_mannequin","turbine_fan","turbine_tail").map(id->ITEMS.register(id,()->new Item(new Item.Properties()))).toList();
    public static final RegistryObject<BlockEntityType<Device>> DEVICE=BES.register("portable_equipment",()->BlockEntityType.Builder.of(Device::new,SHIELD_BLOCK.get(),TURBINE_BLOCK.get(),SENTRY_BLOCK.get()).build(null));
    public static void register(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);BES.register(bus);WardenLocks.register(bus);PortableEquipmentPackets.register();
        bus.addListener((net.minecraftforge.event.BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey().location().toString().equals("zombierool:zb_rct")){e.accept(SHIELD.get());e.accept(TURBINE.get());e.accept(SENTRY.get());PARTS.forEach(p->e.accept(p.get()));SENTRY_PARTS.forEach(p->e.accept(p.get()));PAP_PARTS.forEach(p->e.accept(p.get()));}
        });
    }
    @SubscribeEvent public static void remapLegacy(net.minecraftforge.registries.MissingMappingsEvent event){
        for(var mapping:event.getMappings(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES,"zombierool"))if(mapping.getKey().getPath().equals("tranzit_device"))mapping.remap(DEVICE.get());
    }
    public static boolean portable(ItemStack stack){return stack.getItem() instanceof Portable;}
    public static final String STATE="zr_secondary_equipment";
    public static CompoundTag data(Player p){var root=p.getPersistentData();if(!root.contains(STATE,10))root.put(STATE,new CompoundTag());return root.getCompound(STATE);}
    public static ItemStack carried(Player p){return ItemStack.of(data(p).getCompound("item"));}
    public static boolean inHands(Player p){return data(p).getBoolean("active")&&!carried(p).isEmpty();}
    public static boolean heldShield(Player p){return inHands(p)&&carried(p).is(SHIELD.get());}
    private static void store(ServerPlayer p,ItemStack stack){data(p).put("item",stack.save(new CompoundTag()));if(stack.isEmpty())data(p).putBoolean("active",false);PortableEquipmentPackets.sync(p);}
    public static void clearEquipment(ServerPlayer p){
        for(var level:p.server.getAllLevels()){
            var slots=slots(level);var pos=slots.owners.remove(p.getUUID());slots.setDirty();
            if(pos!=null&&level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&level.getBlockEntity(pos) instanceof Device)removeDevice(level,pos);
        }
        data(p).remove("item");data(p).putBoolean("active",false);PortableEquipmentPackets.sync(p);
    }
    public static boolean acquire(ServerPlayer p,ItemStack stack){
        if(!portable(stack)||!p.isAlive()||p.isSpectator()||PlayerDown(p))return false;
        if(carried(p).is(stack.getItem()))return false;
        var copy=stack.copyWithCount(1);p.stopUsingItem();EquipmentSystem.cancel(p);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(p);clearEquipment(p);store(p,copy);return true;
    }
    public static boolean toggle(ServerPlayer p){
        if(!p.isAlive()||p.isSpectator()||PlayerDown(p)||BuildableSystem.busy(p)||LunarEquipment.busy(p)||carried(p).isEmpty())return false;
        p.stopUsingItem();EquipmentSystem.cancel(p);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(p);me.cryo.zombierool.integration.TacZIntegration.stopTeleporterActions(p);
        data(p).putBoolean("active",!data(p).getBoolean("active"));PortableEquipmentPackets.sync(p);return true;
    }
    public record Placement(BlockPos pos,BlockState state,boolean valid){}
    public static final net.minecraft.tags.TagKey<Block> REPLACEABLE=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new net.minecraft.resources.ResourceLocation("zombierool","equipment_replaceable"));
    public static BlockHitResult deploymentHit(Player p){var eye=p.getEyePosition();return p.level().clip(new ClipContext(eye,eye.add(p.getViewVector(1).scale(4.5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));}
    private static boolean free(Level l,BlockPos p){var s=l.getBlockState(p);return s.canBeReplaced()||s.is(REPLACEABLE)&&s.getCollisionShape(l,p).isEmpty();}
    public static Placement placement(Level level,Player p,BlockHitResult hit,ItemStack stack){
        var pos=hit.getBlockPos().relative(hit.getDirection());boolean turbine=stack.is(TURBINE.get());
        var state=(stack.is(SENTRY.get())?SENTRY_BLOCK:turbine?TURBINE_BLOCK:SHIELD_BLOCK).get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,p.getDirection().getOpposite());
        boolean valid=portable(stack)&&p.getEyePosition().distanceToSqr(hit.getLocation())<=20.25&&hit.getDirection()==Direction.UP&&!level.isOutsideBuildHeight(pos.above())
            &&free(level,pos)&&free(level,pos.above())
            &&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
            &&level.isUnobstructed(state,pos,CollisionContext.of(p))&&level.getEntities(p,new AABB(pos).expandTowards(0,1,0),entity->entity.isAlive()&&(entity instanceof LivingEntity||entity.canBeCollidedWith())).isEmpty();
        return new Placement(pos,state,valid);
    }
    public static boolean place(ServerPlayer p,BlockHitResult requested){
        if(!inHands(p)||PlayerDown(p)||p.isSpectator()||BuildableSystem.busy(p))return false;
        var hit=deploymentHit(p);if(hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(requested.getBlockPos())||hit.getDirection()!=requested.getDirection())return false;
        var stack=carried(p);var spot=placement(p.level(),p,hit,stack);var pos=spot.pos();
        // This is a gameplay device, not a building item: matches use Adventure mode.
        if(!spot.valid()||!p.level().mayInteract(p,pos))return false;
        var level=p.serverLevel();var original=level.getBlockState(pos);MatchWorldJournal.note(level,pos);if(!level.setBlock(pos,spot.state(),3))return false;
        var d=(Device)level.getBlockEntity(pos);d.stack=stack.copy();d.owner=p.getUUID();d.underlay=original;d.setChanged();slots(level).owners.put(d.owner,pos.immutable());slots(level).setDirty();level.sendBlockUpdated(pos,spot.state(),spot.state(),3);
        store(p,ItemStack.EMPTY);refreshPower(level,pos);return true;
    }
    public static BlockPos deviceTarget(Player p,double range){
        var eye=p.getEyePosition();var end=eye.add(p.getViewVector(1).scale(range));BlockPos best=null;double distance=range*range;
        for(var pos:BlockPos.betweenClosed(p.blockPosition().offset(-4,-2,-4),p.blockPosition().offset(4,2,4))){var s=p.level().getBlockState(pos);if(!(s.getBlock() instanceof Deployed))continue;var point=s.getShape(p.level(),pos).bounds().move(pos).inflate(.03).clip(eye,end);if(point.isEmpty())continue;double d=eye.distanceToSqr(point.get());if(d>distance)continue;var obstruction=p.level().clip(new ClipContext(eye,point.get(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(obstruction.getType()==HitResult.Type.BLOCK&&!obstruction.getBlockPos().equals(pos))continue;best=pos.immutable();distance=d;}return best;
    }
    public static boolean retrieve(ServerPlayer p,BlockPos pos){
        if(!p.isAlive()||p.isSpectator()||PlayerDown(p)||BuildableSystem.busy(p)||!pos.equals(deviceTarget(p,3)))return false;
        if(!(p.serverLevel().getBlockEntity(pos) instanceof Device d)||d.owner!=null&&!d.owner.equals(p.getUUID())||d.dying())return false;
        var stack=d.stack.copy();if(stack.isEmpty())return false;if(d.ready()){residual.computeIfAbsent(p.serverLevel(),k->new java.util.HashMap<>()).put(pos.immutable(),p.level().getGameTime()+SHUTDOWN_TICKS);}removeDevice(p.serverLevel(),pos);boolean acquired=acquire(p,stack);p.containerMenu.broadcastChanges();return acquired;
    }
    private static void removeDevice(ServerLevel l,BlockPos pos){var original=l.getBlockEntity(pos) instanceof Device d?d.underlay:Blocks.AIR.defaultBlockState();l.setBlock(pos,original,3);refreshPower(l,pos);}
    public static final int STARTUP_TICKS=50, SHUTDOWN_TICKS=30;
    private static final java.util.Map<ServerLevel,java.util.Map<BlockPos,Long>> residual=new java.util.WeakHashMap<>();
    private static boolean reaches(Level l,BlockPos p,BlockPos target){
        int dx=p.getX()-target.getX(),dz=p.getZ()-target.getZ();if(dx*dx+dz*dz>9||target.getY()<p.getY()-1||target.getY()>p.getY()+3)return false;
        var hit=l.clip(new ClipContext(Vec3.atCenterOf(p).add(0,.5,0),Vec3.atCenterOf(target),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null){@Override public VoxelShape getBlockShape(BlockState state,BlockGetter world,BlockPos at){return at.equals(p)?Shapes.empty():super.getBlockShape(state,world,at);}});
        return hit.getType()==HitResult.Type.MISS||hit.getBlockPos().equals(target);
    }
    public static boolean localPower(Level l,BlockPos target){
        if(!(l instanceof ServerLevel server)||l.getBlockState(target).getBlock() instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlock)return false;
        for(var d:live.getOrDefault(server,java.util.Set.of()))if(!d.isRemoved()&&d.ready()&&!d.stack.isEmpty()&&l.getBlockState(d.getBlockPos()).is(TURBINE_BLOCK.get())&&reaches(l,d.getBlockPos(),target))return true;
        for(var e:residual.getOrDefault(server,java.util.Map.of()).entrySet())if(e.getValue()>l.getGameTime()&&reaches(l,e.getKey(),target))return true;
        return false;
    }
    @SubscribeEvent public static void powerTick(net.minecraftforge.event.TickEvent.LevelTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(event.level instanceof ServerLevel l))return;
        var sources=residual.get(l);if(sources==null)return;var expired=new java.util.ArrayList<BlockPos>();sources.entrySet().removeIf(e->{if(e.getValue()>l.getGameTime())return false;expired.add(e.getKey());return true;});expired.forEach(p->refreshPower(l,p));
    }
    private static void refreshPower(ServerLevel l,BlockPos source){
        MapPower.refreshSoon(l);l.getServer().tell(new net.minecraft.server.TickTask(l.getServer().getTickCount()+1,()->{for(var p:BlockPos.betweenClosed(source.offset(-3,-1,-3),source.offset(3,3,3))){var state=l.getBlockState(p);if(state.getBlock() instanceof DoorBlock)state.neighborChanged(l,p,TURBINE_BLOCK.get(),source,false);}}));
    }
    public static boolean occupied(ServerPlayer p){
        if(!carried(p).isEmpty())return true;
        return hasDeployed(p);
    }
    private static boolean hasDeployed(ServerPlayer p){
        for(var level:p.server.getAllLevels()){
            var slots=slots(level);var pos=slots.owners.get(p.getUUID());
            if(pos!=null){if(!level.hasChunkAt(pos)||level.getBlockEntity(pos) instanceof Device)return true;slots.owners.remove(p.getUUID());slots.setDirty();}
        }
        return false;
    }
    private static final java.util.Map<ServerLevel,java.util.Set<Device>> live=new java.util.WeakHashMap<>();
    private static final class Slots extends net.minecraft.world.level.saveddata.SavedData {
        final java.util.Map<java.util.UUID,BlockPos> owners=new java.util.HashMap<>();
        @Override public CompoundTag save(CompoundTag n){var list=new net.minecraft.nbt.ListTag();owners.forEach((id,p)->{var tag=new CompoundTag();tag.putUUID("id",id);tag.putLong("pos",p.asLong());list.add(tag);});n.put("slots",list);return n;}
    }
    private static Slots readSlots(CompoundTag n){var s=new Slots();for(var t:n.getList("slots",10)){var tag=(CompoundTag)t;s.owners.put(tag.getUUID("id"),BlockPos.of(tag.getLong("pos")));}return s;}
    private static Slots slots(ServerLevel l){var storage=l.getDataStorage();return storage.computeIfAbsent(PortableEquipment::readSlots,()->{var old=storage.get(PortableEquipment::readSlots,"zombierool_tranzit_slots");if(old!=null){old.setDirty();return old;}return new Slots();},"zombierool_portable_equipment_slots");}
    public static void reset(ServerLevel level){
        residual.remove(level);var slots=slots(level);for(var pos:java.util.List.copyOf(slots.owners.values())){if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null&&level.getBlockState(pos).getBlock() instanceof Deployed)removeDevice(level,pos);}slots.owners.clear();slots.setDirty();
        live.remove(level);
        for(var entity:level.getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity item&&(portable(item.getItem())||item.getItem().is(WardenLocks.KEY.get())))item.discard();
        for(var p:level.players()){for(int i=0;i<p.getInventory().getContainerSize();i++)if(portable(p.getInventory().getItem(i)))p.getInventory().setItem(i,ItemStack.EMPTY);}
        for(var p:level.players())clearEquipment(p);
        WardenLocks.reset(level);
    }
    public static final class Portable extends Item {
        public final boolean turbine,sentry;
        public Portable(boolean turbine){this(turbine,false);}
        public Portable(boolean turbine,boolean sentry){super(new Properties().stacksTo(1).durability(turbine?6000:sentry?150:60));this.turbine=turbine;this.sentry=sentry;}
        @Override public void inventoryTick(ItemStack stack,Level level,Entity entity,int slot,boolean selected){if(entity instanceof ServerPlayer p&&!p.isCreative()&&!stack.isEmpty()){acquire(p,stack);stack.shrink(1);p.containerMenu.broadcastChanges();}}
        @Override public InteractionResult useOn(UseOnContext c){
            if(c.getPlayer()!=null&&c.getPlayer().isCreative())return InteractionResult.PASS;
            if(c.getPlayer() instanceof ServerPlayer p){acquire(p,c.getItemInHand());c.getItemInHand().shrink(1);if(!inHands(p))toggle(p);}return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
        }
        @Override public boolean isBarVisible(ItemStack stack){return false;}
        @Override public void appendHoverText(ItemStack s,Level l,java.util.List<Component> lines,TooltipFlag f){lines.add(Component.translatable(sentry?"tooltip.zombierool.sentry_turret":turbine?"tooltip.zombierool.turbine":"tooltip.zombierool.zombie_shield"));}
    }
    public static final class Deployed extends BaseEntityBlock {
        final boolean turbine,sentry;
        Deployed(boolean turbine){this(turbine,false);}
        Deployed(boolean turbine,boolean sentry){super(Properties.of().strength(-1,3600000).noLootTable().noOcclusion().sound(SoundType.METAL));this.turbine=turbine;this.sentry=sentry;registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(BlockStateProperties.HORIZONTAL_FACING);}
        @Override public RenderShape getRenderShape(BlockState s){return turbine||sentry?RenderShape.ENTITYBLOCK_ANIMATED:RenderShape.MODEL;}
        @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter world,BlockPos pos,CollisionContext context){return sentry?Shapes.empty():super.getCollisionShape(state,world,pos,context);}
        @Override public VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return sentry?Block.box(1,0,1,15,28,15):turbine?Block.box(3,0,3,13,32,13):s.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis()==Direction.Axis.X?Block.box(6,0,0,10,24,16):Block.box(0,0,6,16,24,10);}
        @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new Device(p,s);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){return l.isClientSide?null:createTickerHelper(t,DEVICE.get(),Device::tick);}
        @Override public boolean isSignalSource(BlockState s){return turbine;}
        @Override public int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction dir){return turbine&&l.getBlockEntity(p) instanceof Device d&&d.ready()?15:0;}
        @Override public InteractionResult use(BlockState s,Level l,BlockPos pos,Player p,InteractionHand h,BlockHitResult hit){
            if(l instanceof ServerLevel server&&l.getBlockEntity(pos) instanceof Device d&&p instanceof ServerPlayer sp){
                return retrieve(sp,pos)?InteractionResult.SUCCESS:InteractionResult.FAIL;
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        @Override public void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
            if(s.getBlock()!=next.getBlock()&&l instanceof ServerLevel server){var slots=slots(server);slots.owners.values().removeIf(p::equals);slots.setDirty();MapPower.refreshSoon(server);}
            super.onRemove(s,l,p,next,moving);
        }
    }
    private static boolean PlayerDown(Player p){return me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID());}
    public static final class Device extends BlockEntity {
        ItemStack stack=ItemStack.EMPTY;java.util.UUID owner;BlockState underlay=Blocks.AIR.defaultBlockState();int ticks,startup;long hitTick=Long.MIN_VALUE,deathTick=-1;public float aimYaw,aimPitch;
        Device(BlockPos p,BlockState s){super(DEVICE.get(),p,s);aimYaw=s.getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot();}
        @Override public AABB getRenderBoundingBox(){return new AABB(worldPosition).expandTowards(0,1,0).inflate(.2);}
        @Override public void onLoad(){super.onLoad();if(level instanceof ServerLevel s){live.computeIfAbsent(s,k->new java.util.HashSet<>()).add(this);s.getServer().tell(new net.minecraft.server.TickTask(s.getServer().getTickCount()+1,()->{if(!isRemoved()&&owner!=null&&!worldPosition.equals(slots(s).owners.get(owner)))removeDevice(s,worldPosition);}));}}
        @Override public void setRemoved(){if(level instanceof ServerLevel s){var set=live.get(s);if(set!=null)set.remove(this);}super.setRemoved();}
        @Override protected void saveAdditional(CompoundTag n){super.saveAdditional(n);n.putLong("deathTick",deathTick);n.putFloat("aimYaw",aimYaw);n.putFloat("aimPitch",aimPitch);n.putInt("startup",startup);n.putLong("hitTick",hitTick);n.put("stack",stack.save(new CompoundTag()));n.put("underlay",net.minecraft.nbt.NbtUtils.writeBlockState(underlay));if(owner!=null)n.putUUID("owner",owner);}
        @Override public void load(CompoundTag n){super.load(n);deathTick=n.contains("deathTick")?n.getLong("deathTick"):-1;aimYaw=n.getFloat("aimYaw");aimPitch=n.getFloat("aimPitch");startup=Math.min(STARTUP_TICKS,n.getInt("startup"));hitTick=n.contains("hitTick")?n.getLong("hitTick"):Long.MIN_VALUE;stack=ItemStack.of(n.getCompound("stack"));owner=n.hasUUID("owner")?n.getUUID("owner"):null;underlay=n.contains("underlay",10)?net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),n.getCompound("underlay")):Blocks.AIR.defaultBlockState();}
        @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
        @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket(){return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);}
        public boolean dying(){return deathTick>=0;}
        public float deathProgress(float partial){return !dying()||level==null?0:Math.min(1,(level.getGameTime()+partial-deathTick)/20f);}
        public boolean ready(){return getBlockState().is(TURBINE_BLOCK.get())&&startup>=STARTUP_TICKS;}
        public float startupProgress(){return Math.min(1,startup/(float)STARTUP_TICKS);}
        public float damageMotion(float partial){if(level==null||hitTick==Long.MIN_VALUE)return 0;float age=level.getGameTime()+partial-hitTick;return age<0||age>=10?0:(float)(Math.sin(age*2.4)*(1-age/10));}
        public float damageFlash(float partial){if(level==null||hitTick==Long.MIN_VALUE)return 0;float age=level.getGameTime()+partial-hitTick;return age<0||age>=12?0:1-age/12;}
        private void syncVisual(){setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        private void wear(int amount){if(stack.isEmpty()||dying())return;stack.setDamageValue(stack.getDamageValue()+amount);setChanged();if(stack.getDamageValue()>=stack.getMaxDamage()&&level instanceof ServerLevel server){if(getBlockState().is(SENTRY_BLOCK.get())){deathTick=level.getGameTime();syncVisual();}else removeDevice(server,worldPosition);}}
        void damage(int amount){
            if(stack.isEmpty()||amount<=0||dying())return;if(getBlockState().is(TURBINE_BLOCK.get()))amount*=2;boolean shield=getBlockState().is(SHIELD_BLOCK.get()),broken=stack.getDamageValue()+amount>=stack.getMaxDamage();
            if(shield)shieldSound(level,worldPosition,broken);
            else if(level instanceof ServerLevel server){hitTick=level.getGameTime();syncVisual();server.sendParticles(getBlockState().is(SENTRY_BLOCK.get())?net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK:net.minecraft.core.particles.ParticleTypes.SMOKE,worldPosition.getX()+.5,worldPosition.getY()+1.2,worldPosition.getZ()+.5,5,.18,.25,.18,.02);level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.IRON_GOLEM_HURT,net.minecraft.sounds.SoundSource.BLOCKS,.45f,1.4f);}
            wear(amount);
        }
        static void tick(Level l,BlockPos pos,BlockState state,Device d){
            if(WaveManager.isPausedByPlayer())return;
            if(d.dying()){if(l.getGameTime()-d.deathTick>=20)removeDevice((ServerLevel)l,pos);return;}
            if(state.is(SENTRY_BLOCK.get())&&d.ticks%4==0)PortableTurret.fire((ServerLevel)l,d,d.owner);
            boolean turbine=state.is(TURBINE_BLOCK.get());d.ticks++;
            if(turbine){d.wear(1);if(!d.isRemoved()&&d.startup<STARTUP_TICKS){d.startup++;if(d.startup%5==0)d.syncVisual();if(d.ready()){refreshPower((ServerLevel)l,pos);l.updateNeighborsAt(pos,state.getBlock());((ServerLevel)l).sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,12,.35,.5,.35,0);l.playSound(null,pos,net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.get(),net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.5f);}}}
            if(d.isRemoved())return;
            if(d.ticks%20==0){
                if(turbine)((ServerLevel)l).sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,pos.getX()+.5,pos.getY()+1.1,pos.getZ()+.5,2,.2,.2,.2,0);

            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void protect(net.minecraftforge.event.entity.living.LivingAttackEvent e){
        if(!(e.getEntity() instanceof ServerPlayer p)||PlayerDown(p)||!(e.getSource().getDirectEntity() instanceof Mob mob)||!EquipmentEffects.enemy(mob))return;
        var shield=carried(p);if(!shield.is(SHIELD.get()))return;
        boolean front=p.getViewVector(1).multiply(1,0,1).dot(mob.position().subtract(p.position()).multiply(1,0,1))>0;
        boolean held=heldShield(p);
        if(front!=held)return;
        e.setCanceled(true);boolean broken=wearShield(shield,1);
        data(p).putLong("impactTick",p.level().getGameTime());
        store(p,shield);
        shieldSound(p.level(),p.blockPosition(),broken);
    }
    private static boolean wearShield(ItemStack shield,int damage){shield.setDamageValue(shield.getDamageValue()+damage);boolean broken=shield.getDamageValue()>=shield.getMaxDamage();if(broken)shield.shrink(1);return broken;}
    private static void shieldSound(Level level,BlockPos pos,boolean broken){level.playSound(null,pos,(broken?me.cryo.zombierool.init.ZombieroolModSounds.SHIELD_BREAK:me.cryo.zombierool.init.ZombieroolModSounds.SHIELD_HIT).get(),net.minecraft.sounds.SoundSource.PLAYERS,.8f,1);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void bash(net.minecraftforge.event.entity.player.AttackEntityEvent e){
        var p=e.getEntity();if(!heldShield(p))return;e.setCanceled(true);
        if(e.getTarget() instanceof LivingEntity target)bashTarget(p,target);
    }
    public static boolean bashHeld(Player p){
        if(!heldShield(p))return false;
        if(!p.level().isClientSide)p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(3),t->EquipmentEffects.enemy(t)&&p.hasLineOfSight(t)&&p.getViewVector(1).dot(t.getEyePosition().subtract(p.getEyePosition()).normalize())>.6).stream().min(java.util.Comparator.comparingDouble(p::distanceToSqr)).ifPresent(t->bashTarget(p,t));
        return true;
    }
    private static void bashTarget(Player p,LivingEntity target){
        var stack=carried(p);
        if(!(p instanceof ServerPlayer sp)||PlayerDown(p)||p.isSpectator()||!EquipmentEffects.enemy(target)||p.distanceToSqr(target)>9||!p.hasLineOfSight(target))return;
        long now=p.level().getGameTime();if(p.getPersistentData().getLong("zr_shield_bash_until")>now)return;p.getPersistentData().putLong("zr_shield_bash_until",now+15);
        var n=target.getPersistentData();n.putBoolean("zr_shield_damage",true);
        try{float amount=EquipmentEffects.instantKillImmune(target)?20:WaveManager.getCurrentWave()<=20?target.getMaxHealth():20;
            boolean hit=me.cryo.zombierool.core.manager.DamageManager.applyDamage(target,p.damageSources().playerAttack(p),amount);
            if(hit){if(target.isAlive())PointManager.awardEnemyPoints(sp,target,10);target.knockback(1,p.getX()-target.getX(),p.getZ()-target.getZ());if(wearShield(stack,1))shieldSound(p.level(),p.blockPosition(),true);store(sp,stack);}
        }finally{n.remove("zr_shield_damage");}
    }
    @SubscribeEvent public static void explosion(net.minecraftforge.event.level.ExplosionEvent.Detonate e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        blast(level,e.getExplosion().getPosition(),6);
    }
    public static void blast(ServerLevel level,Vec3 pos,float radius){for(var d:java.util.List.copyOf(live.getOrDefault(level,java.util.Set.of())))if(Vec3.atCenterOf(d.getBlockPos()).distanceToSqr(pos)<=radius*radius)d.damage(d.getBlockState().is(TURBINE_BLOCK.get())?1200:10);}
    public static void electricalPulse(ServerLevel level,Vec3 pos,double radius){if(!Double.isFinite(radius)||radius<0)return;radius=Math.min(64,radius);for(var d:java.util.List.copyOf(live.getOrDefault(level,java.util.Set.of())))if(d.getBlockState().is(TURBINE_BLOCK.get())&&Vec3.atCenterOf(d.getBlockPos()).distanceToSqr(pos)<=radius*radius)d.damage(6000);}
    @SubscribeEvent public static void slot(net.minecraftforge.event.TickEvent.PlayerTickEvent e){
        if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END||!(e.player instanceof ServerPlayer p))return;
        if((p.isSpectator()||PlayerDown(p))&&inHands(p)){data(p).putBoolean("active",false);PortableEquipmentPackets.sync(p);}
    }
    @SubscribeEvent public static void itemUse(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem e){if(inHands(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void addGoal(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide)return;
        if(e.loadedFromDisk()&&!WaveManager.isGameRunning()&&e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item&&(portable(item.getItem())||item.getItem().is(WardenLocks.KEY.get())))e.setCanceled(true);
        if(e.getEntity() instanceof Mob m&&EquipmentEffects.enemy(m))m.goalSelector.addGoal(1,new DeviceGoal(m));
    }
    /** Prevent the final player-target pass from replacing a device attack route. */
    public static boolean pursuingDevice(Mob mob){return mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.isRunning()&&g.getGoal() instanceof DeviceGoal goal&&goal.target!=null&&!goal.target.isRemoved());}
    private static final class DeviceGoal extends net.minecraft.world.entity.ai.goal.Goal {
        final Mob mob;Device target;int nextSearch,nextAttack;net.minecraft.world.level.pathfinder.Path route;
        DeviceGoal(Mob m){mob=m;setFlags(java.util.EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){
            if(!(mob.level() instanceof ServerLevel l)||mob.tickCount<nextSearch||EquipmentEffects.active(mob))return false;nextSearch=mob.tickCount+20;
            var candidates=live.getOrDefault(l,java.util.Set.of()).stream().filter(d->!d.isRemoved()&&!d.dying()&&(d.getBlockState().is(TURBINE_BLOCK.get())||d.getBlockState().is(SENTRY_BLOCK.get()))&&d.getBlockPos().distToCenterSqr(mob.position())<256).sorted(java.util.Comparator.comparingDouble(d->d.getBlockPos().distToCenterSqr(mob.position()))).toList();
            for(var device:candidates){route=route(device);if(route!=null||close(device)){target=device;return true;}}target=null;return false;
        }
        private net.minecraft.world.level.pathfinder.Path route(Device device){
            var pos=device.getBlockPos();var nearest=Direction.getNearest((float)(mob.getX()-pos.getX()-.5),0,(float)(mob.getZ()-pos.getZ()-.5));
            var path=mob.getNavigation().createPath(pos.relative(nearest),0);if(path!=null&&path.canReach())return path;
            for(var direction:Direction.Plane.HORIZONTAL){if(direction==nearest)continue;path=mob.getNavigation().createPath(pos.relative(direction),0);if(path!=null&&path.canReach())return path;}return null;
        }
        private boolean close(Device device){var pos=device.getBlockPos();double dx=mob.getX()-(pos.getX()+.5),dz=mob.getZ()-(pos.getZ()+.5);return dx*dx+dz*dz<2.25&&Math.abs(mob.getY()-pos.getY())<1.5;}
        @Override public void start(){mob.setTarget(null);nextAttack=mob.tickCount+10;if(route!=null)mob.getNavigation().moveTo(route,1.2);}
        @Override public boolean canContinueToUse(){return target!=null&&!target.isRemoved()&&!target.dying()&&mob.distanceToSqr(Vec3.atCenterOf(target.getBlockPos()))<324&&!EquipmentEffects.active(mob);}
        @Override public void tick(){var current=target;
            // GoalSelector may tick an every-tick goal again before its next continuation check.
            if(current==null||current.isRemoved()||current.dying()||!(mob.level() instanceof ServerLevel level)||current.getLevel()!=level||EquipmentEffects.active(mob)){stop();return;}
            var pos=current.getBlockPos();mob.getLookControl().setLookAt(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
            if(close(current)&&PortableTurret.visible(level,pos,mob.getEyePosition(),Vec3.atCenterOf(pos).add(0,.2,0))){mob.getNavigation().stop();if(mob.tickCount>=nextAttack){nextAttack=mob.tickCount+20;mob.swing(InteractionHand.MAIN_HAND);current.damage(current.getBlockState().is(TURBINE_BLOCK.get())?600:10);}}
            else if(mob.tickCount%20==0){route=route(current);if(route!=null)mob.getNavigation().moveTo(route,1.2);else stop();}
        }
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void stop(){target=null;route=null;mob.getNavigation().stop();}
    }
    @SubscribeEvent public static void pickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent e){if(e.getEntity() instanceof ServerPlayer p&&!p.isCreative()&&portable(e.getItem().getItem())){e.setCanceled(true);if(p.isAlive()&&!p.isSpectator()&&!PlayerDown(p)){acquire(p,e.getItem().getItem());e.getItem().discard();}}}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)clearEquipment(p);}
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)clearEquipment(p);}
}
