package me.cryo.zombierool.block.system;

import com.mojang.brigadier.StringReader;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.core.manager.DynamicResourceManager;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.*;
import java.util.*;

/** World-local map devices. Configuration persists, paid activations deliberately do not. */
public final class MapDeviceSystem {
    public enum Kind { SOUND, PARTICLE, CONTROL, ELECTRIC, FIRE, TURRET }
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "zombierool");
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "zombierool");
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "zombierool");
    public static final Map<Kind, RegistryObject<Block>> TYPES = new EnumMap<>(Kind.class);
    static {
        add("sound_emitter", Kind.SOUND); add("particle_emitter", Kind.PARTICLE);
        add("trap_controller", Kind.CONTROL); add("electric_trap", Kind.ELECTRIC);
        add("fire_pit", Kind.FIRE); add("turret", Kind.TURRET);
    }
    public static final RegistryObject<BlockEntityType<Device>> DEVICE = ENTITIES.register("map_device",
            () -> BlockEntityType.Builder.of(Device::new, TYPES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    private static final Map<Level, Set<Device>> LOADED = new WeakHashMap<>();
    private static final ThreadLocal<ServerPlayer> DAMAGE_OWNER = new ThreadLocal<>();
    public static ServerPlayer damageOwner() { return DAMAGE_OWNER.get(); }
    public static boolean isTrapDamage() { return DAMAGE_OWNER.get() != null; }
    private static void add(String id, Kind kind) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new DeviceBlock(kind));
        TYPES.put(kind, block);
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
        bus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey().location().equals(new ResourceLocation("zombierool", "zb_rct")))
                TYPES.values().forEach(b -> e.accept(b.get()));
        });
    }
    public static List<Device> devices(Level level) { return new ArrayList<>(LOADED.getOrDefault(level, Set.of())); }
    public static void reset(ServerLevel level) {
        for (Device device : devices(level)) { device.remaining = device.cooldown = 0; device.owner = null; device.counter = 0; device.targetHits.clear(); device.setActive(false); device.syncRuntime(); }
    }
    public static boolean isEmitter(Kind kind) { return kind == Kind.SOUND || kind == Kind.PARTICLE; }
    public static final class DeviceBlock extends Block implements EntityBlock {
        public static final DirectionProperty FACING = BlockStateProperties.FACING;
        public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;
        public final Kind kind;
        DeviceBlock(Kind kind) {
            super(Properties.of().strength(-1, 3600000).noOcclusion().dynamicShape().lightLevel(s -> s.getValue(ACTIVE) ? 8 : 0));
            this.kind = kind;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ACTIVE); }
        @Override public BlockState getStateForPlacement(BlockPlaceContext c) {
            Direction facing = kind == Kind.CONTROL ? c.getHorizontalDirection().getOpposite() : c.getClickedFace();
            if (kind == Kind.TURRET || isEmitter(kind)) facing = c.getHorizontalDirection().getOpposite();
            return defaultBlockState().setValue(FACING, facing);
        }
        @Override public BlockState rotate(BlockState s, Rotation r) { return s.setValue(FACING, r.rotate(s.getValue(FACING))); }
        @Override public BlockState mirror(BlockState s, Mirror m) { return rotate(s, m.getRotation(s.getValue(FACING))); }
        private boolean invisible() {
            return isEmitter(kind) && net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()
                    && me.cryo.zombierool.client.TechnicalBlockClient.shouldBeInvisible();
        }
        @Override public RenderShape getRenderShape(BlockState s) { return invisible() ? RenderShape.INVISIBLE : RenderShape.MODEL; }
        @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            if (invisible()) return Shapes.empty();
            if (isEmitter(kind)) return Block.box(3,3,3,13,13,13);
            if (kind == Kind.TURRET) return Block.box(2,0,2,14,15,14);
            Direction d = s.getValue(FACING);
            return switch (d) {
                case UP -> Block.box(0,0,0,16,4,16); case DOWN -> Block.box(0,12,0,16,16,16);
                case NORTH -> Block.box(0,0,12,16,16,16); case SOUTH -> Block.box(0,0,0,16,16,4);
                case EAST -> Block.box(0,0,0,4,16,16); case WEST -> Block.box(12,0,0,16,16,16);
            };
        }
        @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return isEmitter(kind) || kind == Kind.CONTROL ? Shapes.empty() : getShape(s,l,p,c);
        }
        @Override public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, net.minecraft.world.level.pathfinder.PathComputationType type) { return kind == Kind.CONTROL || isEmitter(kind); }
        @Override public net.minecraft.world.level.pathfinder.BlockPathTypes getBlockPathType(BlockState s, BlockGetter l, BlockPos p, net.minecraft.world.entity.Mob mob) { return kind == Kind.CONTROL || isEmitter(kind) ? net.minecraft.world.level.pathfinder.BlockPathTypes.OPEN : null; }
        @Override public BlockEntity newBlockEntity(BlockPos p, BlockState s) { return new Device(p,s); }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
            return !l.isClientSide && t == DEVICE.get() ? (world,p,state,be) -> ((Device)be).tick() : null;
        }
        @Override public InteractionResult use(BlockState s, Level l, BlockPos p, Player player, InteractionHand hand, BlockHitResult hit) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!l.isClientSide && player instanceof ServerPlayer sp && l.getBlockEntity(p) instanceof Device device) {
                if (sp.isCreative()) MapDevicePackets.open(sp, device);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
    }
    public static final class Device extends BlockEntity {
        public String channel = "", effect = "";
        public boolean enabled = true, requiresPower = true;
        public int interval = 40, count = 8, cost = 1000, duration = 600, recharge = 1200;
        public float volume = 1, pitch = 1, range = 16, spread = .3f, speed = .03f, damage = 20;
        public int remaining, cooldown, counter;
        public UUID owner;
        public String lastError = "";
        public float aimYaw, aimPitch;
        public int linkedActive, linkedCooldown;
        public int linkedTrapKinds;
        public Component trapName() {
            if (kind() != Kind.CONTROL) return getBlockState().getBlock().getName();
            Component name = Component.empty();
            for (Kind kind : new Kind[]{Kind.ELECTRIC, Kind.FIRE, Kind.TURRET}) {
                if ((linkedTrapKinds & (1 << kind.ordinal())) != 0) {
                    if (!name.getString().isEmpty()) name = name.copy().append(" / ");
                    name = name.copy().append(TYPES.get(kind).get().getName());
                }
            }
            return linkedTrapKinds == 0 ? getBlockState().getBlock().getName() : name;
        }
        public boolean powerAvailable = true;
        private String lastRuntimeState = "";
        private final Map<UUID,Long> targetHits = new HashMap<>();
        private ParticleOptions parsedParticle;
        private String parsedEffect = "";
        public Device(BlockPos p, BlockState s) {
            super(DEVICE.get(),p,s);
            if (kind() == Kind.PARTICLE) effect = "minecraft:flame";
            if (kind() == Kind.SOUND) effect = "minecraft:ambient.cave";
            if (kind() == Kind.ELECTRIC || kind() == Kind.FIRE) { range = 3; damage = 100000; }
            if (kind() == Kind.TURRET) interval = 5;
        }
        public Kind kind() { return ((DeviceBlock)getBlockState().getBlock()).kind; }
        @Override public void onLoad() { super.onLoad(); if (level != null && !level.isClientSide) LOADED.computeIfAbsent(level,k -> new HashSet<>()).add(this); }
        @Override public void setRemoved() { if (level != null) { var entries = LOADED.get(level); if (entries != null) entries.remove(this); } super.setRemoved(); }
        public CompoundTag config() {
            CompoundTag n = new CompoundTag(); n.putString("channel",channel); n.putString("effect",effect); n.putBoolean("enabled",enabled);
            n.putInt("interval",interval); n.putInt("count",count); n.putInt("cost",cost); n.putInt("duration",duration); n.putInt("recharge",recharge);
            n.putFloat("volume",volume); n.putFloat("pitch",pitch); n.putFloat("range",range); n.putFloat("spread",spread); n.putFloat("speed",speed); n.putFloat("damage",damage); n.putBoolean("requires_power",requiresPower);
            return n;
        }
        public void configure(CompoundTag n) {
            channel = n.getString("channel").substring(0,Math.min(64,n.getString("channel").length()));
            effect = n.getString("effect").substring(0,Math.min(512,n.getString("effect").length())); enabled = n.getBoolean("enabled");
            interval = clamp(n.getInt("interval"),1,72000); count = clamp(n.getInt("count"),1,100);
            cost = clamp(n.getInt("cost"),0,1000000); duration = clamp(n.getInt("duration"),20,72000); recharge = clamp(n.getInt("recharge"),20,72000);
            volume = finite(n.getFloat("volume"),0,4); pitch = finite(n.getFloat("pitch"),.5f,2);
            range = finite(n.getFloat("range"),1,128); spread = finite(n.getFloat("spread"),0,16); speed = finite(n.getFloat("speed"),0,2); damage = finite(n.getFloat("damage"),.1f,1000000);
            requiresPower = !n.contains("requires_power") || n.getBoolean("requires_power");
            counter = 0; lastError = ""; parsedParticle = null; setChanged();
            if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        }
        private static int clamp(int v,int lo,int hi) { return Math.max(lo,Math.min(hi,v)); }
        private static float finite(float v,float lo,float hi) { return Float.isFinite(v) ? Math.max(lo,Math.min(hi,v)) : lo; }
        @Override protected void saveAdditional(CompoundTag n) { super.saveAdditional(n); n.put("Device",config()); }
        @Override public void load(CompoundTag n) { super.load(n); if (n.contains("Device")) configure(n.getCompound("Device")); aimYaw=n.getFloat("AimYaw"); aimPitch=n.getFloat("AimPitch"); remaining=n.getInt("ActiveTicks"); cooldown=n.getInt("CooldownTicks"); linkedActive=n.getInt("LinkedActive"); linkedCooldown=n.getInt("LinkedCooldown"); linkedTrapKinds=n.getInt("LinkedTrapKinds"); powerAvailable=!n.contains("HasPower") || n.getBoolean("HasPower"); }
        @Override public CompoundTag getUpdateTag() { var n=saveWithoutMetadata(); n.putFloat("AimYaw",aimYaw); n.putFloat("AimPitch",aimPitch); n.putInt("ActiveTicks",remaining); n.putInt("CooldownTicks",cooldown); n.putInt("LinkedActive",linkedActive); n.putInt("LinkedCooldown",linkedCooldown); n.putInt("LinkedTrapKinds",linkedTrapKinds); n.putBoolean("HasPower",powerAvailable); return n; }
        @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        public void syncRuntime() {
            if (!(level instanceof ServerLevel) || kind()!=Kind.CONTROL && kind()!=Kind.TURRET) return;
            linkedActive=linkedCooldown=linkedTrapKinds=0;
            if (!channel.isBlank()) for (Device d:devices(level)) if(d!=this && d.channel.equals(channel)) {
                if (d.enabled && (d.kind()==Kind.ELECTRIC || d.kind()==Kind.FIRE || d.kind()==Kind.TURRET)) linkedTrapKinds |= 1 << d.kind().ordinal();
                linkedActive=Math.max(linkedActive,d.remaining);linkedCooldown=Math.max(linkedCooldown,d.cooldown);
            }
            powerAvailable=hasPower();
            String state=((remaining+19)/20)+":"+((cooldown+19)/20)+":"+((linkedActive+19)/20)+":"+((linkedCooldown+19)/20)+":"+powerAvailable+":"+linkedTrapKinds;
            if(!state.equals(lastRuntimeState)) {lastRuntimeState=state;level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
        }
        public void setActive(boolean active) {
            if (level != null && getBlockState().getValue(DeviceBlock.ACTIVE) != active)
                level.setBlock(worldPosition,getBlockState().setValue(DeviceBlock.ACTIVE,active),3);
        }
        public String activate(ServerPlayer player, boolean paid) {
            if (kind() != Kind.CONTROL && kind() != Kind.TURRET) return "message.zombierool.trap.use_controller";
            if (!WaveManager.isGameRunning()) return "message.zombierool.trap.match_only";
            if (!enabled || player == null || player.level() != level || player.isSpectator() || !player.isAlive()) return "message.zombierool.trap.unavailable";
            if (!hasPower()) return "message.zombierool.trap.no_power";
            if (remaining > 0) return "message.zombierool.trap.active";
            if (cooldown > 0) return "message.zombierool.trap.cooldown";
            if (kind() == Kind.CONTROL && (channel.isBlank() || devices(level).stream().noneMatch(d -> d != this && d.channel.equals(channel) && d.enabled && (d.kind() == Kind.ELECTRIC || d.kind() == Kind.FIRE || d.kind() == Kind.TURRET)))) return "message.zombierool.trap.no_link";
            if (!channel.isBlank() && devices(level).stream().anyMatch(d -> d != this && d.channel.equals(channel) && (d.remaining > 0 || d.cooldown > 0))) return "message.zombierool.trap.busy";
            if (paid && PointManager.getScore(player) < cost) return "message.zombierool.trap.no_points";
            if (paid) PointManager.modifyScore(player,-cost,false);
            owner = player.getUUID(); remaining = duration; counter = 0; setActive(true); syncRuntime();
            ((ServerLevel)level).playSound(null,worldPosition,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,1,.7f);
            LuaScriptManager.callEvent("OnTrapActivated",worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),channel,owner.toString(),duration);
            return "";
        }
        public void stop() {
            if (remaining > 0) {
                remaining = 0; cooldown = recharge; setActive(false); syncRuntime();
                LuaScriptManager.callEvent("OnTrapStopped",worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),channel);
            }
        }
        private Device controller() {
            if (remaining > 0) return this;
            if (channel.isBlank()) return null;
            return devices(level).stream().filter(d -> d.kind() == Kind.CONTROL && d.remaining > 0 && d.enabled && d.channel.equals(channel)).findFirst().orElse(null);
        }
        public boolean hasPower() {
            if (!(level instanceof ServerLevel server)) return false;
            return !requiresPower || me.cryo.zombierool.config.WorldConfig.get(server).getPowerSwitchPositions().isEmpty()
                    || me.cryo.zombierool.config.GlobalSwitchState.isActivated(server) || me.cryo.zombierool.gameplay.MapPower.receives(level,worldPosition);
        }
        private boolean lineClear(Vec3 start,Vec3 end,net.minecraft.world.entity.Entity entity) {
            return !Boolean.TRUE.equals(BlockGetter.traverseBlocks(start,end,entity,(e,p) -> {
                if(p.equals(worldPosition))return null;
                var shape=level.getBlockState(p).getCollisionShape(level,p,CollisionContext.of(e));
                return !shape.isEmpty() && shape.clip(start,end,p)!=null ? Boolean.TRUE : null;
            },e -> Boolean.FALSE));
        }
        private void tick() {
            if (!(level instanceof ServerLevel server)) return;
            syncRuntime();
            if (!WaveManager.isGameRunning()) { remaining = cooldown = counter = 0; owner = null; setActive(false); return; }
            if (!enabled || ((kind() == Kind.CONTROL || kind() == Kind.TURRET && controller() == this) && !hasPower())) { stop(); setActive(false); return; }
            if (cooldown > 0 && --cooldown == 0) LuaScriptManager.callEvent("OnTrapReady",worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),channel);
            if (remaining > 0 && --remaining == 0) { remaining = 1; stop(); }
            if (isEmitter(kind())) { if (counter++ % interval == 0) emit(); return; }
            if (kind() == Kind.CONTROL) return;
            Device controller = controller(); setActive(controller != null);
            if (controller == null) { counter = 0; targetHits.clear(); return; }
            counter++;
            if (kind() == Kind.TURRET && (counter-1) % interval != 0) return;
            Vec3 origin = Vec3.atCenterOf(worldPosition);
            Direction facing = getBlockState().getValue(DeviceBlock.FACING);
            Vec3 end = origin.add(Vec3.atLowerCornerOf(facing.getNormal()).scale(Math.min(range,16)));
            AABB area = kind() == Kind.TURRET ? new AABB(worldPosition).inflate(range) : new AABB(origin,end).inflate(.65);
            var targets = server.getEntitiesOfClass(AbstractZombieRoolEntity.class,area,e -> e.isAlive() && !e.isRemoved());
            targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
            ServerPlayer purchaser = controller.owner == null ? null : server.getServer().getPlayerList().getPlayer(controller.owner);
            for (var target : targets) {
                Vec3 aim = target.getBoundingBox().getCenter();
                Vec3 start = origin.add(kind() == Kind.TURRET ? new Vec3(0,.28,0) : Vec3.atLowerCornerOf(facing.getNormal()).scale(.05));
                if (!lineClear(start,aim,target) || target.distanceToSqr(origin)>range*range+1) continue;
                if (kind() != Kind.TURRET && targetHits.containsKey(target.getUUID()) && server.getGameTime()-targetHits.get(target.getUUID())<10) continue;
                if (kind() != Kind.TURRET) targetHits.put(target.getUUID(),server.getGameTime());
                if (kind() != Kind.TURRET) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,20,5,false,false));
                var old = DAMAGE_OWNER.get();
                try { if (purchaser != null) DAMAGE_OWNER.set(purchaser); else DAMAGE_OWNER.remove();
                    boolean hit = DamageManager.applyDamage(target,server.damageSources().generic(),damage);
                    if (hit) LuaScriptManager.callEvent("OnTrapHit",worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),target.getUUID().toString(),purchaser == null ? "" : purchaser.getUUID().toString(),damage,!target.isAlive());
                } finally { if (old == null) DAMAGE_OWNER.remove(); else DAMAGE_OWNER.set(old); }
                if (kind() == Kind.TURRET) {
                    Vec3 direction=aim.subtract(start);
                    aimYaw=(float)Math.toDegrees(Math.atan2(-direction.x,-direction.z));
                    aimPitch=(float)Math.toDegrees(Math.atan2(direction.y,Math.sqrt(direction.x*direction.x+direction.z*direction.z)));
                    server.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
                    for (int i=0;i<12;i++) { Vec3 v = start.lerp(aim,i/12.0); server.sendParticles(ParticleTypes.CRIT,v.x,v.y,v.z,1,0,0,0,0); }
                    server.playSound(null,worldPosition,net.minecraft.sounds.SoundEvent.createVariableRangeEvent(new ResourceLocation("zombierool:mg42_fire")),SoundSource.BLOCKS,1,1); break;
                }
            }
            if (kind() != Kind.TURRET) {
                long now=server.getGameTime();
                for (Player p:server.getEntitiesOfClass(Player.class,area,p -> p.isAlive() && !p.isCreative() && !p.isSpectator())) {
                    if(!lineClear(origin,p.getBoundingBox().getCenter(),p))continue;
                    me.cryo.zombierool.gameplay.FixedPlayerDamage.trapContact(p);
                }
                targetHits.entrySet().removeIf(e -> now-e.getValue()>20);
                if(counter%2 != 0)return;
                for (int i=1;i<=20;i++) { Vec3 v=origin.lerp(end,i/20.0);
                    if(kind()==Kind.ELECTRIC) { double jitter=(i%2==0?.16:-.16); server.sendParticles(ParticleTypes.ELECTRIC_SPARK,v.x+jitter,v.y,v.z-jitter,4,.03,.03,.03,.01); }
                    else { server.sendParticles(ParticleTypes.FLAME,v.x,v.y,v.z,5,.3,.18,.3,.035); if(i%4==0)server.sendParticles(ParticleTypes.SMOKE,v.x,v.y,v.z,2,.2,.2,.2,.02); }
                }
                if (counter % 40 == 0) server.playSound(null,worldPosition,kind() == Kind.FIRE ? SoundEvents.FIRE_AMBIENT : SoundEvents.BEACON_AMBIENT,SoundSource.BLOCKS,.5f,1.5f);
            }
        }
        public boolean emit() {
            if (!(level instanceof ServerLevel server) || !isEmitter(kind())) return false;
            try {
                if (kind() == Kind.SOUND) {
                    ResourceLocation id = new ResourceLocation(effect);
                    if (!ForgeRegistries.SOUND_EVENTS.containsKey(id) && !DynamicResourceManager.customAudioIds().contains(effect)) throw new IllegalArgumentException("Unknown sound: " + effect);
                    for (ServerPlayer p : server.players()) if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= range*range)
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),new MapDevicePackets.Sound(worldPosition,effect,volume,pitch,range));
                } else {
                    if (parsedParticle == null || !parsedEffect.equals(effect)) { parsedParticle = ParticleArgument.readParticle(new StringReader(DynamicResourceManager.resolveParticlePreset(effect)),net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.asLookup()); parsedEffect = effect; }
                    for (ServerPlayer p : server.players()) if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= range*range)
                        server.sendParticles(p,parsedParticle,true,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,count,spread,spread,spread,speed);
                }
                lastError = ""; LuaScriptManager.callEvent("OnEmitterPulse",worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),kind().name(),effect); return true;
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Invalid effect" : e.getMessage();
                if (!message.equals(lastError)) ZombieroolMod.LOGGER.warn("Map device at {}: {}",worldPosition,message);
                lastError = message; return false;
            }
        }
    }
}
