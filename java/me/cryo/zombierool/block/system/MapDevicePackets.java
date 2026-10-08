package me.cryo.zombierool.block.system;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.core.manager.DynamicResourceManager;

public final class MapDevicePackets {
    public record Open(BlockPos pos, String kind, CompoundTag config, List<String> catalog) {
        public static void encode(Open m,FriendlyByteBuf b) { b.writeBlockPos(m.pos); b.writeUtf(m.kind,32); b.writeNbt(m.config); b.writeCollection(m.catalog,(buf,s) -> buf.writeUtf(s,512)); }
        public static Open decode(FriendlyByteBuf b) {
            BlockPos p=b.readBlockPos(); String k=b.readUtf(32); CompoundTag n=b.readNbt();
            int size=b.readVarInt(); if(size<0 || size>20000) throw new IllegalArgumentException("Catalog too large");
            List<String> ids=new ArrayList<>(); for(int i=0;i<size;i++) ids.add(b.readUtf(512));
            return new Open(p,k,n==null ? new CompoundTag() : n,ids);
        }
        public static void handle(Open m,Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> me.cryo.zombierool.client.gui.MapDeviceScreen.open(m)));
            c.get().setPacketHandled(true);
        }
    }
    public record Save(BlockPos pos, CompoundTag config, boolean preview) {
        public static void encode(Save m,FriendlyByteBuf b) { b.writeBlockPos(m.pos); b.writeNbt(m.config); b.writeBoolean(m.preview); }
        public static Save decode(FriendlyByteBuf b) { return new Save(b.readBlockPos(),b.readNbt(),b.readBoolean()); }
        public static void handle(Save m,Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> {
                ServerPlayer p=c.get().getSender();
                if(p==null || !p.isCreative() || m.config==null || p.distanceToSqr(m.pos.getX()+.5,m.pos.getY()+.5,m.pos.getZ()+.5)>64 || !p.serverLevel().hasChunkAt(m.pos)) return;
                if(p.level().getBlockEntity(m.pos) instanceof MapDeviceSystem.Device d) {
                    d.stop(); d.configure(m.config);
                    if(m.preview && MapDeviceSystem.isEmitter(d.kind())) {
                        if(!d.emit()) p.displayClientMessage(net.minecraft.network.chat.Component.literal(d.lastError),false);
                    }
                }
            }); c.get().setPacketHandled(true);
        }
    }
    public record Sound(BlockPos pos,String id,float volume,float pitch,float range) {
        public static void encode(Sound m,FriendlyByteBuf b) { b.writeBlockPos(m.pos); b.writeUtf(m.id,512); b.writeFloat(m.volume); b.writeFloat(m.pitch); b.writeFloat(m.range); }
        public static Sound decode(FriendlyByteBuf b) { return new Sound(b.readBlockPos(),b.readUtf(512),b.readFloat(),b.readFloat(),b.readFloat()); }
        public static void handle(Sound m,Supplier<NetworkEvent.Context> c) {
            c.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,() -> () -> me.cryo.zombierool.client.MapDeviceSounds.play(m)));
            c.get().setPacketHandled(true);
        }
    }
    public static void register(SimpleChannel ch,int id) {
        ch.registerMessage(id++,Open.class,Open::encode,Open::decode,Open::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        ch.registerMessage(id++,Save.class,Save::encode,Save::decode,Save::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        ch.registerMessage(id,Sound.class,Sound::encode,Sound::decode,Sound::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void open(ServerPlayer p,MapDeviceSystem.Device d) {
        TreeSet<String> ids=new TreeSet<>();
        if(d.kind()==MapDeviceSystem.Kind.SOUND) {
            ForgeRegistries.SOUND_EVENTS.getKeys().forEach(k -> ids.add(k.toString())); ids.addAll(DynamicResourceManager.customAudioIds());
        } else if(d.kind()==MapDeviceSystem.Kind.PARTICLE) { ForgeRegistries.PARTICLE_TYPES.getKeys().forEach(k -> ids.add(k.toString())); ids.addAll(DynamicResourceManager.customParticleIds()); }
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),new Open(d.getBlockPos(),d.kind().name(),d.config(),ids.stream().limit(20000).toList()));
    }
}
