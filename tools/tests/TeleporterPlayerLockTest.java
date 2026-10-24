package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.gameplay.TeleporterPlayerLock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.LogicalSide;

public final class TeleporterPlayerLockTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("LOCK CHECK: "+text);}
    public static void run(ServerLevel level)throws Exception{
        FakePlayer player=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TeleportLock"));
        player.setGameMode(GameType.SURVIVAL);player.setPos(1800.5,80,.5);player.setYRot(23);player.setXRot(12);player.getInventory().selected=0;
        level.setBlock(new BlockPos(1800,79,0),Blocks.STONE.defaultBlockState(),3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,3));
        long now=level.getGameTime();Vec3 position=player.position();
        TeleporterPlayerLock.update(player,60);
        check(!TeleporterPlayerLock.active(player)&&me.cryo.zombierool.gameplay.EquipmentSystem.valid(player),"Electrical charge leaves player actions available");
        player.move(MoverType.SELF,new Vec3(1,0,0));check(!player.position().equals(position),"Player can move during electrical charge");player.setPos(position.x,position.y,position.z);
        TeleporterPlayerLock.update(player,0);
        check(TeleporterPlayerLock.active(player),"Actual teleport tunnel activates the player lock");
        var mirror=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"LockMirror"));mirror.setGameMode(GameType.SURVIVAL);mirror.setId(player.getId());mirror.setPos(1804.5,80,.5);
        TeleporterPlayerLock.update(mirror,60);check(TeleporterPlayerLock.active(player),"Separate player objects sharing a numeric ID cannot release each other's lock");
        TeleporterPlayerLock.update(mirror,0);TeleporterPlayerLock.release(mirror);check(TeleporterPlayerLock.active(player),"Client/server-style identity collisions keep independent locks");
        player.move(MoverType.SELF,new Vec3(2,0,0));check(player.position().equals(position),"Applied movement mixin prevents walking during the tunnel");
        player.move(MoverType.PISTON,new Vec3(0,0,2));check(player.position().equals(position),"Piston movement cannot displace a teleporting player");
        var platformPos=new BlockPos(1800,80,0);level.setBlock(platformPos,me.cryo.zombierool.block.system.TeleporterSystem.PLATFORM.get().defaultBlockState(),3);
        var pad=(me.cryo.zombierool.block.system.TeleporterSystem.Pad)level.getBlockEntity(platformPos);pad.onLoad();var settings=pad.config.copy();settings.putBoolean("requiresPower",false);pad.configure(settings);
        check(me.cryo.zombierool.block.system.TeleporterTravel.depart(player,pad).equals("message.zombierool.teleporter.busy"),"Automatic teleporters cannot start a new charge before the tunnel finishes");
        player.push(1,1,1);check(player.getDeltaMovement().equals(Vec3.ZERO),"Applied push mixin blocks knockback and pushes");
        player.setDeltaMovement(1,-2,3);player.setSprinting(true);player.setYRot(100);player.setXRot(-50);TeleporterPlayerLock.enforce(player);
        check(player.getDeltaMovement().equals(Vec3.ZERO)&&!player.isSprinting()&&player.getYRot()==23&&player.getXRot()==12,"Player tick clears momentum and freezes view direction");
        var connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),player.connection.connection,player);
        check(connection!=null,"Fake player uses the real transformed packet listener");
        connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
        check(player.getInventory().selected==0,"Forged slot-change packet is rejected server-side");
        connection.handlePlayerAction(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.DROP_ITEM,BlockPos.ZERO,Direction.DOWN,0));
        check(player.getMainHandItem().getCount()==3,"Forged drop packet cannot remove held items");
        connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(1802.5,80,.5,95,45,true));
        check(player.position().equals(position)&&player.getYRot()==23&&player.getXRot()==12,"Movement packets cannot move or turn a teleporting player");
        var victim=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(level);victim.setPos(1801.5,80,.5);float health=victim.getHealth();
        player.attack(victim);check(victim.getHealth()==health,"Vanilla melee is cancelled before damage");
        var shoot=new com.tacz.guns.api.event.common.GunShootEvent(player,ItemStack.EMPTY,LogicalSide.SERVER);MinecraftForge.EVENT_BUS.post(shoot);
        check(shoot.isCanceled(),"TacZ shooting is rejected while locked");
        var reload=new com.tacz.guns.api.event.common.GunReloadEvent(player,ItemStack.EMPTY,LogicalSide.SERVER);MinecraftForge.EVENT_BUS.post(reload);
        check(reload.isCanceled(),"TacZ reloading is rejected while locked");
        var melee=new com.tacz.guns.api.event.common.GunMeleeEvent(player,ItemStack.EMPTY,LogicalSide.SERVER);MinecraftForge.EVENT_BUS.post(melee);
        check(melee.isCanceled(),"TacZ melee is rejected while locked");
        var select=new com.tacz.guns.api.event.common.GunFireSelectEvent(player,ItemStack.EMPTY,LogicalSide.SERVER);MinecraftForge.EVENT_BUS.post(select);
        check(select.isCanceled(),"Changing TacZ fire mode is rejected while locked");
        check(!me.cryo.zombierool.gameplay.EquipmentSystem.valid(player),"Equipment and grenade input is rejected while locked");
        TeleporterPlayerLock.update(player,-1);check(!TeleporterPlayerLock.active(player),"Cancellation immediately restores player actions");
        connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
        check(player.getInventory().selected==1,"Slot changes work again after cancellation");
        TeleporterPlayerLock.hold(player,TeleporterPlayerLock.TUNNEL_TICKS);
        level.getServer().getWorldData().overworldData().setGameTime(now+TeleporterPlayerLock.TUNNEL_TICKS-1);
        check(TeleporterPlayerLock.active(player),"Player stays locked throughout the arrival tunnel");
        level.getServer().getWorldData().overworldData().setGameTime(now+TeleporterPlayerLock.TUNNEL_TICKS);
        check(!TeleporterPlayerLock.active(player)&&me.cryo.zombierool.gameplay.EquipmentSystem.valid(player),"Actions resume exactly when the tunnel ends; destination stay is free");
        TeleporterPlayerLock.hold(player,60);player.setPos(1810.5,80,.5);
        check(!TeleporterPlayerLock.active(player),"An external teleport releases the previous anchor instead of snapping back");
        TeleporterPlayerLock.hold(player,60);player.setHealth(0);
        check(!TeleporterPlayerLock.active(player),"Death releases the lock");
        player.setHealth(20);TeleporterPlayerLock.hold(player,60);TeleporterPlayerLock.reset(level);
        check(!TeleporterPlayerLock.active(player),"World or match reset clears all player locks");
        level.getServer().getWorldData().overworldData().setGameTime(now);victim.discard();
        System.out.println("LOCK TEST PASS");
    }
}
