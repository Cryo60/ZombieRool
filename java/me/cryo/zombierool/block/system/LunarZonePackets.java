package me.cryo.zombierool.block.system;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import java.util.Optional;

public final class LunarZonePackets {
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool","lunar_zones"),()->"1","1"::equals,"1"::equals);
    public record Open(BlockPos pos,CompoundTag config){}
    public record Save(BlockPos pos,CompoundTag config){}
    private static void write(BlockPos pos,CompoundTag config,FriendlyByteBuf b){b.writeBlockPos(pos);b.writeNbt(config);}
    public static void register(){
        CHANNEL.registerMessage(0,Open.class,(m,b)->write(m.pos,m.config,b),b->new Open(b.readBlockPos(),b.readNbt()),(m,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.gui.LunarZoneScreen.open(m.pos,m.config)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Save.class,(m,b)->write(m.pos,m.config,b),b->new Save(b.readBlockPos(),b.readNbt()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p!=null&&p.isCreative()&&m.config!=null&&p.distanceToSqr(m.pos.getCenter())<=64&&p.serverLevel().hasChunkAt(m.pos)&&p.level().getBlockEntity(m.pos) instanceof AtmosphereZoneBlock.Zone z)z.configure(m.config);});context.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void open(ServerPlayer p,AtmosphereZoneBlock.Zone z){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Open(z.getBlockPos(),z.config()));}
    public static void save(BlockPos p,CompoundTag config){CHANNEL.sendToServer(new Save(p,config));}
}
