package me.cryo.zombierool.client;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.block.system.MapDeviceSystem;
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class VehicleDecorInput {
 @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
 public static void input(InputEvent.InteractionKeyMappingTriggered e){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||!mc.player.isCreative()||(!e.isAttack()&&!e.isUseItem())||!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit))return;if(!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof MapDeviceSystem.Device d)||d.kind()!=MapDeviceSystem.Kind.VEHICLE)return;var n=new net.minecraft.nbt.CompoundTag();n.putBoolean("vehicleEdit",true);n.putBoolean("removeVehicle",e.isAttack());me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendToServer(new me.cryo.zombierool.block.system.MapDevicePackets.Save(hit.getBlockPos(),n,false));e.setCanceled(true);e.setSwingHand(false);}
}
