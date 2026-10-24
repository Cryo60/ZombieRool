package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@Mod.EventBusSubscriber(modid="zombierool")
public final class PortableEquipmentPackets {
    private record State(java.util.UUID player,CompoundTag equipment,boolean key){}
    private record Action(String action,BlockHitResult hit){}
    private record Locks(String dimension,java.util.Set<BlockPos> targets){}
    private record LockDrop(BlockPos pos,int state){}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation("zombierool","portable_equipment"),()->"5","5"::equals,"5"::equals);
    public static void register(){
        CHANNEL.registerMessage(0,State.class,(p,b)->{b.writeUUID(p.player);b.writeNbt(p.equipment);b.writeBoolean(p.key);},b->new State(b.readUUID(),b.readNbt(),b.readBoolean()),(p,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.PortableEquipmentClient.receive(p.player,p.equipment,p.key)));c.get().setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Action.class,(p,b)->{b.writeUtf(p.action,16);b.writeBlockHitResult(p.hit);},b->new Action(b.readUtf(16),b.readBlockHitResult()),(a,c)->{
            var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p==null||!p.isAlive()||p.isSpectator())return;
                switch(a.action){case "toggle"->PortableEquipment.toggle(p);case "place"->{if(!PortableEquipment.place(p,a.hit))p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.equipment.placement_blocked"),true);}case "retrieve"->PortableEquipment.retrieve(p,a.hit.getBlockPos());case "bash"->PortableEquipment.bashHeld(p);case "loot"->WardenLocks.collectLookedAt(p);case "unlock"->WardenLocks.unlockLookedAt(p);default->{}}
            });context.setPacketHandled(true);
        },java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2,Locks.class,(p,b)->{b.writeUtf(p.dimension);b.writeCollection(p.targets,(buf,pos)->buf.writeBlockPos(pos));},b->new Locks(b.readUtf(),new java.util.HashSet<>(b.readList(buf->buf.readBlockPos()))),(p,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.PortableEquipmentClient.locks(p.dimension,p.targets)));c.get().setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,LockDrop.class,(p,b)->{b.writeBlockPos(p.pos);b.writeVarInt(p.state);},b->new LockDrop(b.readBlockPos(),b.readVarInt()),(p,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.PortableEquipmentClient.lockDrop(p.pos,net.minecraft.world.level.block.Block.stateById(p.state))));c.get().setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void requestToggle(){CHANNEL.sendToServer(new Action("toggle",new BlockHitResult(Vec3.ZERO,net.minecraft.core.Direction.UP,BlockPos.ZERO,false)));}
    public static void requestPlace(BlockHitResult hit){CHANNEL.sendToServer(new Action("place",hit));}
    public static void lockDrop(ServerLevel l,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(()->l.getChunkAt(pos)),new LockDrop(pos,net.minecraft.world.level.block.Block.getId(state)));}
    public static void requestRetrieve(BlockPos pos){CHANNEL.sendToServer(new Action("retrieve",new BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false)));}
    public static void requestLoot(){CHANNEL.sendToServer(new Action("loot",new BlockHitResult(Vec3.ZERO,net.minecraft.core.Direction.UP,BlockPos.ZERO,false)));}
    public static void requestBash(){CHANNEL.sendToServer(new Action("bash",new BlockHitResult(Vec3.ZERO,net.minecraft.core.Direction.UP,BlockPos.ZERO,false)));}
    public static void requestUnlock(BlockPos pos){CHANNEL.sendToServer(new Action("unlock",new BlockHitResult(Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false)));}
    private static State state(ServerPlayer p){return new State(p.getUUID(),PortableEquipment.data(p).copy(),WardenLocks.hasKey(p.serverLevel()));}
    public static void sync(ServerPlayer p){CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->p),state(p));}
    public static void syncLocks(ServerLevel l,java.util.Set<BlockPos> targets){CHANNEL.send(PacketDistributor.DIMENSION.with(l::dimension),new Locks(l.dimension().location().toString(),targets));}
    private static void sendLocks(ServerPlayer p){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Locks(p.level().dimension().location().toString(),WardenLocks.closedTargets(p.serverLevel())));}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.PlayerTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END&&e.player instanceof ServerPlayer p&&p.tickCount%20==0)sync(p);}
    @SubscribeEvent public static void login(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){sync(p);sendLocks(p);}}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){PortableEquipment.data(p).putBoolean("active",false);sync(p);sendLocks(p);}}
    @SubscribeEvent public static void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking e){if(e.getEntity() instanceof ServerPlayer observer&&e.getTarget() instanceof ServerPlayer p)CHANNEL.send(PacketDistributor.PLAYER.with(()->observer),state(p));}
}
