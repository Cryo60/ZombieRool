package me.cryo.zombierool.hotfix;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.*;
import me.cryo.zombierool.player.PlayerCrawlManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class HotfixDownReset {
    private HotfixDownReset() {}
    public static void resetAll() {
        var server=ServerLifecycleHooks.getCurrentServer();
        if(server!=null)for(ServerPlayer player:server.getPlayerList().getPlayers())resetPlayer(player);
    }
    public static void resetPlayer(Player entity) {
        if(!(entity instanceof ServerPlayer player))return;
        var uuid=player.getUUID();
        PlayerCrawlManager.setCrawling(uuid,false);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.GLOWING);
        player.setPose(Pose.STANDING);player.refreshDimensions();player.fallDistance=0;
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CPlayerDownPacket(false,uuid));
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CSyncCrawlStatePacket(uuid,false));
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CPlayerPosePacket(uuid,Pose.STANDING));
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new S2CPlayerRevivePacket(uuid,-1,0));
    }
}
