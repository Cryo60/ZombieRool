package me.cryo.zombierool.block.system;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import java.util.*;

/** Dedicated channel keeps addon packet numbering independent of the main protocol. */
public final class MachineDeliveryPackets {
    private record Open(BlockPos pos,CompoundTag data){}
    private record Request(BlockPos pos,String action,CompoundTag data){}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool","machine_delivery"),()->"1","1"::equals,"1"::equals);
    public static void register(){
        CHANNEL.registerMessage(0,Open.class,(m,b)->{b.writeBlockPos(m.pos);b.writeNbt(m.data);},b->new Open(b.readBlockPos(),b.readNbt()),(m,c)->{c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.gui.MachineDeliveryScreen.open(m.pos,m.data)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Request.class,(m,b)->{b.writeBlockPos(m.pos);b.writeUtf(m.action,20);b.writeNbt(m.data);},b->new Request(b.readBlockPos(),b.readUtf(20),b.readNbt()),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->{
            var p=ctx.getSender();if(p==null||!p.isCreative()||m.data==null)return;
            boolean sky=m.pos.equals(MachineDeliverySystem.SKY_POS);
            if(!sky&&(p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(m.pos))>64||!p.serverLevel().hasChunkAt(m.pos)))return;
            var d=sky?MachineDeliverySystem.sky(p.serverLevel()):MachineDeliverySystem.at(p.serverLevel(),m.pos);if(d==null)return;
            try{switch(m.action){
                case "open"->open(p,d);
                case "save"->d.configure(m.data);
                case "preview"->{d.configure(m.data);var stored=d.config();if(!stored.getBoolean("enabled")){var test=stored.copy();test.putBoolean("enabled",true);d.configure(test);}String error=d.trigger("",null);d.configure(stored);if(!error.isEmpty())p.displayClientMessage(Component.translatable("gui.zombierool.delivery.error."+error),false);}
                case "reset"->{d.resetGroup();open(p,d);}
                default->{}
            }}catch(IllegalStateException ex){p.displayClientMessage(Component.translatable("gui.zombierool.delivery.reset_first"),false);}
        });ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    public static void open(ServerPlayer p,MachineDeliverySystem.Delivery d){var n=d.status();var choices=new ListTag();for(String id:MachineDeliverySystem.catalog()){var row=new CompoundTag();row.putString("id",id);choices.add(row);}n.put("catalog",choices);CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Open(d.getBlockPos(),n));}
    public static void request(BlockPos p,String action,CompoundTag n){CHANNEL.sendToServer(new Request(p,action,n));}
}
