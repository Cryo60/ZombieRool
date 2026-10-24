package me.cryo.zombierool.block.system;

import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.gameplay.TraversalMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.pathfinder.Node;

public final class ZiplineInteraction {
    private ZiplineInteraction(){}
    public static void use(ServerPlayer player,BlockPos pos){
        if(!TeleporterSystem.validPlayer(player)||TraversalMotion.active(player))return;
        TraversalSystem.Route route=null;
        for(var anchor:TraversalSystem.loaded(player.level())){
            if(anchor.kind()!=TraversalSystem.Kind.ZIPLINE||!anchor.ready())continue;
            if(anchor.getBlockPos().equals(pos))route=new TraversalSystem.Route(anchor,false);
            else if(anchor.bidirectional&&pos.equals(anchor.exit))route=new TraversalSystem.Route(anchor,true);
        }
        if(route==null||!route.anchor().allowPlayers||player.distanceToSqr(route.start())>9)return;
        var anchor=route.anchor();
        if(!me.cryo.zombierool.gameplay.BlockPowerSettings.required(player.level(),pos,anchor.requiresPower,me.cryo.zombierool.config.GlobalSwitchState.isActivated(player.level())||me.cryo.zombierool.gameplay.MapPower.receives(player.level(),pos))){player.displayClientMessage(Component.translatable("message.zombierool.power_required"),true);return;}
        if(PointManager.getScore(player)<anchor.cost){player.displayClientMessage(Component.translatable("message.zombierool.teleporter.no_points"),true);return;}
        var endBox=player.getBoundingBox().move(route.end().subtract(player.position()));
        if(player.level().getBlockCollisions(player,endBox).iterator().hasNext()){player.displayClientMessage(Component.translatable("message.zombierool.teleporter.unsafe"),true);return;}
        if(anchor.cost>0)PointManager.modifyScore(player,-anchor.cost,false);
        TraversalMotion.start(player,route);
    }
}
