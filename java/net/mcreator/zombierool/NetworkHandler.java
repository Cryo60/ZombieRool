package net.mcreator.zombierool.network;

import net.mcreator.zombierool.network.packet.SyncColdWaterStatePacket; 

import net.mcreator.zombierool.SetWallWeaponConfigPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

import net.mcreator.zombierool.network.packet.PlayGlobalSoundPacket;
import net.mcreator.zombierool.network.StopFourIsReadySoundPacket;
import net.mcreator.zombierool.network.MeleeAttackPacket;

import net.mcreator.zombierool.network.packet.SetEyeColorPacket;
import net.mcreator.zombierool.network.handler.SetEyeColorPacketHandler;

import net.mcreator.zombierool.network.PurchaseObstacleMessage;

import net.mcreator.zombierool.network.RepairBarricadeMessage; 
import net.mcreator.zombierool.network.ObstacleDoorGUIPacket;
import net.mcreator.zombierool.network.SetSpawnerChannelMessage;
import net.mcreator.zombierool.network.ReloadWeaponMessage;
import net.mcreator.zombierool.network.CaptureWallTexturePacket;
import net.mcreator.zombierool.network.PurchaseWallWeaponPacket;
import net.mcreator.zombierool.network.StartUpgradeMessage;
import net.mcreator.zombierool.network.SpecialWavePacket;
import net.mcreator.zombierool.network.SavePerksConfigMessage;
import net.mcreator.zombierool.network.PurchasePerkMessage;
import net.mcreator.zombierool.network.PointGainPacket;
import net.mcreator.zombierool.network.PlayerDownPacket;
import net.mcreator.zombierool.network.PlayerRevivePacket;
import net.mcreator.zombierool.network.C2SReviveAttemptPacket;
import net.mcreator.zombierool.network.WaveUpdatePacket;
import net.mcreator.zombierool.network.PlayerPosePacket;
import net.mcreator.zombierool.network.DisplayHitmarkerPacket;
import net.mcreator.zombierool.network.RecoilPacket;
import net.mcreator.zombierool.network.PurchaseMysteryBoxMessage;
import net.mcreator.zombierool.network.ScreenShakePacket;
import net.mcreator.zombierool.network.ObstacleDoorCopyBlockPacket;
import net.mcreator.zombierool.network.PurchaseWunderfizzDrinkMessage;
import net.mcreator.zombierool.network.packet.SyncWunderfizzLocationPacket;
import net.mcreator.zombierool.network.BloodOverlayPacket;
import net.mcreator.zombierool.network.packet.StartGameAnimationPacket;
import net.mcreator.zombierool.network.packet.WaveChangeAnimationPacket;
import net.mcreator.zombierool.network.SyncBloodOverlaysPacket;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class NetworkHandler {

    private static boolean alreadyRegistered = false;
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("zombierool", "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        if (alreadyRegistered) return;
        alreadyRegistered = true;
        
        // Enregistrer le système de resource packs (canal séparé)
        ResourcePackNetworkHandler.register();
        
        event.enqueueWork(() -> {
            int id = 0; // Start message ID counter

            INSTANCE.registerMessage(id++, RepairBarricadeMessage.class, RepairBarricadeMessage::encode, RepairBarricadeMessage::decode, RepairBarricadeMessage::handler);
            INSTANCE.registerMessage(id++, ObstacleDoorGUIPacket.class, ObstacleDoorGUIPacket::encode, ObstacleDoorGUIPacket::decode, ObstacleDoorGUIPacket::handle);
            INSTANCE.registerMessage(id++, SetSpawnerChannelMessage.class, SetSpawnerChannelMessage::encode, SetSpawnerChannelMessage::decode, SetSpawnerChannelMessage::handle);
            INSTANCE.registerMessage(id++, ReloadWeaponMessage.class, ReloadWeaponMessage::encode, ReloadWeaponMessage::decode, ReloadWeaponMessage::handler);
            INSTANCE.registerMessage(id++, SetWallWeaponConfigPacket.class, SetWallWeaponConfigPacket::encode, SetWallWeaponConfigPacket::decode, SetWallWeaponConfigPacket::handle);
            INSTANCE.registerMessage(id++, CaptureWallTexturePacket.class, CaptureWallTexturePacket::encode, CaptureWallTexturePacket::decode, CaptureWallTexturePacket::handle);
            INSTANCE.registerMessage(id++, PurchaseWallWeaponPacket.class, PurchaseWallWeaponPacket::encode, PurchaseWallWeaponPacket::decode, PurchaseWallWeaponPacket::handle);
            INSTANCE.registerMessage(id++, StartUpgradeMessage.class, StartUpgradeMessage::encode, StartUpgradeMessage::decode, StartUpgradeMessage::handler);
            INSTANCE.registerMessage(id++, SpecialWavePacket.class, SpecialWavePacket::encode, SpecialWavePacket::decode, SpecialWavePacket::handle);
            INSTANCE.registerMessage(id++, SavePerksConfigMessage.class, SavePerksConfigMessage::encode, SavePerksConfigMessage::decode, SavePerksConfigMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, PurchasePerkMessage.class, PurchasePerkMessage::encode, PurchasePerkMessage::decode, PurchasePerkMessage::handler);
            INSTANCE.registerMessage(id++, PointGainPacket.class, PointGainPacket::encode, PointGainPacket::decode, PointGainPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, PlayerDownPacket.class, PlayerDownPacket::encode, PlayerDownPacket::decode, PlayerDownPacket::handle);
            INSTANCE.registerMessage(id++, PlayerRevivePacket.class, PlayerRevivePacket::encode, PlayerRevivePacket::decode, PlayerRevivePacket::handle);
            INSTANCE.registerMessage(id++, C2SReviveAttemptPacket.class, C2SReviveAttemptPacket::encode, C2SReviveAttemptPacket::decode, C2SReviveAttemptPacket::handle);
            INSTANCE.registerMessage(id++, WaveUpdatePacket.class, WaveUpdatePacket::encode, WaveUpdatePacket::decode, WaveUpdatePacket::handle);
            INSTANCE.registerMessage(id++, PlayerPosePacket.class, PlayerPosePacket::encode, PlayerPosePacket::decode, PlayerPosePacket::handle);
            INSTANCE.registerMessage(id++, DisplayHitmarkerPacket.class, DisplayHitmarkerPacket::encode, DisplayHitmarkerPacket::decode, DisplayHitmarkerPacket.Handler::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, RecoilPacket.class, RecoilPacket::encode, RecoilPacket::decode, RecoilPacket::handle);
            INSTANCE.registerMessage(id++, PurchaseMysteryBoxMessage.class, PurchaseMysteryBoxMessage::encode, PurchaseMysteryBoxMessage::decode, PurchaseMysteryBoxMessage::handle);
            INSTANCE.registerMessage(id++, StopFourIsReadySoundPacket.class, StopFourIsReadySoundPacket::encode, StopFourIsReadySoundPacket::new, StopFourIsReadySoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, MeleeAttackPacket.class, MeleeAttackPacket::encode, MeleeAttackPacket::new, MeleeAttackPacket::handle);
            INSTANCE.registerMessage(id++, ScreenShakePacket.class, ScreenShakePacket::encode, ScreenShakePacket::new, ScreenShakePacket::handle);
            INSTANCE.registerMessage(id++, PlayGlobalSoundPacket.class, PlayGlobalSoundPacket::encode, PlayGlobalSoundPacket::decode, PlayGlobalSoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, SetEyeColorPacket.class, SetEyeColorPacket::encode, SetEyeColorPacket::decode, SetEyeColorPacketHandler::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, PurchaseObstacleMessage.class, PurchaseObstacleMessage::encode, PurchaseObstacleMessage::new, PurchaseObstacleMessage::handler);
            INSTANCE.registerMessage(id++, ObstacleDoorCopyBlockPacket.class, ObstacleDoorCopyBlockPacket::encode, ObstacleDoorCopyBlockPacket::new, ObstacleDoorCopyBlockPacket::handle);
            INSTANCE.registerMessage(id++, SyncColdWaterStatePacket.class, SyncColdWaterStatePacket::encode, SyncColdWaterStatePacket::decode, SyncColdWaterStatePacket::handle);
            INSTANCE.registerMessage(id++, PurchaseWunderfizzDrinkMessage.class, PurchaseWunderfizzDrinkMessage::encode, PurchaseWunderfizzDrinkMessage::decode, PurchaseWunderfizzDrinkMessage::handler);
            INSTANCE.registerMessage(id++, SyncWunderfizzLocationPacket.class, SyncWunderfizzLocationPacket::encode, SyncWunderfizzLocationPacket::decode, SyncWunderfizzLocationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, StartGameAnimationPacket.class, StartGameAnimationPacket::encode, StartGameAnimationPacket::decode, StartGameAnimationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, WaveChangeAnimationPacket.class, WaveChangeAnimationPacket::encode, WaveChangeAnimationPacket::decode, WaveChangeAnimationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CAmmoCratePricePacket.class,S2CAmmoCratePricePacket::toBytes,S2CAmmoCratePricePacket::new, S2CAmmoCratePricePacket::handle);
            INSTANCE.registerMessage(id++, PurchaseAmmoCrateMessage.class, PurchaseAmmoCrateMessage::encode, PurchaseAmmoCrateMessage::new, PurchaseAmmoCrateMessage::handler);
			INSTANCE.registerMessage(id++, C2SRequestAmmoCrateInfoPacket.class,C2SRequestAmmoCrateInfoPacket::encode,C2SRequestAmmoCrateInfoPacket::new,C2SRequestAmmoCrateInfoPacket::handler);
			INSTANCE.registerMessage(id++, BloodOverlayPacket.class, BloodOverlayPacket::encode, BloodOverlayPacket::new, BloodOverlayPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
			INSTANCE.registerMessage(id++, SyncBloodOverlaysPacket.class, SyncBloodOverlaysPacket::encode, SyncBloodOverlaysPacket::new, SyncBloodOverlaysPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
			
           });
	}
}