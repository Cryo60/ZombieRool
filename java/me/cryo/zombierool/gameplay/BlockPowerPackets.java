package me.cryo.zombierool.gameplay;

import java.util.Optional;
import java.util.function.Supplier;
import me.cryo.zombierool.block.SignalTechnicalBlock;
import me.cryo.zombierool.network.NetworkHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BlockPowerPackets {
    public record Request(BlockPos pos) {
        public static void encode(Request m,FriendlyByteBuf b){b.writeBlockPos(m.pos);}
        public static Request decode(FriendlyByteBuf b){return new Request(b.readBlockPos());}
        public static void handle(Request m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->{
            var player=c.get().getSender();
            if(player!=null&&player.isCreative()&&player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(m.pos))<=64&&player.serverLevel().hasChunkAt(m.pos)&&BlockPowerSettings.configurable(player.level(),m.pos))open(player,m.pos);
        });c.get().setPacketHandled(true);}
    }
    public record Open(BlockPos pos,BlockPowerSettings.Mode mode,boolean signal,boolean visible) {
        public static void encode(Open m,FriendlyByteBuf b){b.writeBlockPos(m.pos);b.writeEnum(m.mode);b.writeBoolean(m.signal);b.writeBoolean(m.visible);}
        public static Open decode(FriendlyByteBuf b){return new Open(b.readBlockPos(),b.readEnum(BlockPowerSettings.Mode.class),b.readBoolean(),b.readBoolean());}
        public static void handle(Open m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.gui.BlockPowerScreen.open(m)));c.get().setPacketHandled(true);}
    }
    public record Save(BlockPos pos,BlockPowerSettings.Mode mode,boolean visible) {
        public static void encode(Save m,FriendlyByteBuf b){b.writeBlockPos(m.pos);b.writeEnum(m.mode);b.writeBoolean(m.visible);}
        public static Save decode(FriendlyByteBuf b){return new Save(b.readBlockPos(),b.readEnum(BlockPowerSettings.Mode.class),b.readBoolean());}
        public static void handle(Save m,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->{
            ServerPlayer player=c.get().getSender();
            if(player==null||!player.isCreative()||player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(m.pos))>64||!player.serverLevel().hasChunkAt(m.pos)||!BlockPowerSettings.configurable(player.level(),m.pos))return;
            var state=player.level().getBlockState(m.pos);
            if(state.getBlock() instanceof SignalTechnicalBlock)player.level().setBlock(m.pos,state.setValue(SignalTechnicalBlock.SURVIVAL_VISIBLE,m.visible),3);
            else BlockPowerSettings.setMode(player.serverLevel(),m.pos,m.mode);
        });c.get().setPacketHandled(true);}
    }
    public static void register(SimpleChannel channel,int id){
        channel.registerMessage(id,Open.class,Open::encode,Open::decode,Open::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(id+1,Save.class,Save::encode,Save::decode,Save::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
        channel.registerMessage(id+2,Request.class,Request::encode,Request::decode,Request::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void open(ServerPlayer player,BlockPos pos){
        var state=player.level().getBlockState(pos);boolean signal=state.getBlock() instanceof SignalTechnicalBlock;
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new Open(pos,BlockPowerSettings.mode(player.level(),pos),signal,signal&&state.getValue(SignalTechnicalBlock.SURVIVAL_VISIBLE)));
    }
}
