package me.cryo.zombierool.block.system;

import java.util.*;
import java.util.function.Supplier;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class TransportPackets {
    private TransportPackets(){}
    public record Open(BlockPos pos,String kind,CompoundTag config) {
        public static void encode(Open m,FriendlyByteBuf b){b.writeBlockPos(m.pos);b.writeUtf(m.kind,32);b.writeNbt(m.config);}
        public static Open decode(FriendlyByteBuf b){return new Open(b.readBlockPos(),b.readUtf(32),b.readNbt());}
        public static void handle(Open m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.gui.TransportScreen.open(m)));c.get().setPacketHandled(true);}
    }
    public record Save(BlockPos pos,CompoundTag config) {
        public static void encode(Save m,FriendlyByteBuf b){b.writeBlockPos(m.pos);b.writeNbt(m.config);}
        public static Save decode(FriendlyByteBuf b){return new Save(b.readBlockPos(),b.readNbt());}
        public static void handle(Save m,Supplier<NetworkEvent.Context> c){
            c.get().enqueueWork(()->{
                ServerPlayer p=c.get().getSender();
                if(p==null||!p.isCreative()||m.config==null||!p.serverLevel().hasChunkAt(m.pos)||p.distanceToSqr(Vec3.atCenterOf(m.pos))>64)return;
                if(p.level().getBlockEntity(m.pos) instanceof TraversalSystem.Anchor a&&a.root.equals(m.pos))a.configure(m.config);
                else if(p.level().getBlockEntity(m.pos) instanceof TeleporterSystem.Pad pad){
                    if(pad.busy){p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.teleporter.busy"),true);return;}
                    pad.configure(m.config);
                }
            });c.get().setPacketHandled(true);
        }
    }
    public record Jump(int entityId,UUID uuid,Vec3 start,Vec3 peak,Vec3 end,int duration,int elapsed) {
        private static void vec(FriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
        private static Vec3 vec(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
        public static void encode(Jump m,FriendlyByteBuf b){b.writeVarInt(m.entityId);b.writeUUID(m.uuid);vec(b,m.start);vec(b,m.peak);vec(b,m.end);b.writeVarInt(m.duration);b.writeVarInt(m.elapsed);}
        public static Jump decode(FriendlyByteBuf b){return new Jump(b.readVarInt(),b.readUUID(),vec(b),vec(b),vec(b),b.readVarInt(),b.readVarInt());}
        public static void handle(Jump m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.TransportClient.receive(m)));c.get().setPacketHandled(true);}
    }
    public record Charge(int ticks) {
        public static void encode(Charge m,FriendlyByteBuf b){b.writeVarInt(m.ticks);}
        public static Charge decode(FriendlyByteBuf b){return new Charge(b.readVarInt());}
        public static void handle(Charge m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.screens.TeleporterChargeOverlay.start(m.ticks)));c.get().setPacketHandled(true);}
    }
    public static void charge(ServerPlayer player,int ticks){NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new Charge(ticks));}
    public static void register(SimpleChannel ch,int id){
        ch.registerMessage(id++,Open.class,Open::encode,Open::decode,Open::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(id++,Save.class,Save::encode,Save::decode,Save::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        ch.registerMessage(id++,Jump.class,Jump::encode,Jump::decode,Jump::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(id,Charge.class,Charge::encode,Charge::decode,Charge::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void open(ServerPlayer player,BlockPos pos,String kind,CompoundTag config){
        if(kind.equals("teleporter")){
            var registry=TeleporterRegistry.get(player.serverLevel());var platforms=new net.minecraft.nbt.ListTag();var receivers=new net.minecraft.nbt.ListTag();
            registry.pads.entrySet().stream().filter(e->!e.getKey().equals(pos)&&e.getValue().getBoolean("enabled")&&!e.getValue().getString("role").equals("SWITCH")).sorted(java.util.Comparator.comparing(e->e.getValue().getString("id"))).forEach(e->{String id=e.getValue().getString("id");if(registry.named(id).size()==1){platforms.add(net.minecraft.nbt.StringTag.valueOf(id));if(e.getValue().getString("role").equals("RECEIVER"))receivers.add(net.minecraft.nbt.StringTag.valueOf(id));}});config.put("_platformIds",platforms);config.put("_receiverIds",receivers);
        }
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new Open(pos,kind,config));}
}
