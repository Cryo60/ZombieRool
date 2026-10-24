package me.cryo.zombierool.gameplay;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
@Mod.EventBusSubscriber(modid="zombierool")
public final class PanzerCaptureEvents{
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.getPersistentData().hasUUID("zr_panzer_owner")){var owner=p.serverLevel().getEntity(p.getPersistentData().getUUID("zr_panzer_owner"));if(owner instanceof SpecialEnemyEntity boss)boss.releaseCapture();p.getPersistentData().remove("zr_panzer_owner");}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){e.getEntity().getPersistentData().remove("zr_panzer_owner");}
}
