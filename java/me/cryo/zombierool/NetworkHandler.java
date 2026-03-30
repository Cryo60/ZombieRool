package me.cryo.zombierool.network;

import me.cryo.zombierool.block.system.BuyWallWeaponSystem.C2SSetWallWeaponConfigPacket;
import me.cryo.zombierool.network.packet.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

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

        event.enqueueWork(() -> {
            int id = 0;

            INSTANCE.registerMessage(id++, C2SUnifiedInteractPacket.class, C2SUnifiedInteractPacket::encode, C2SUnifiedInteractPacket::decode, C2SUnifiedInteractPacket::handle);
            INSTANCE.registerMessage(id++, C2SObstacleDoorGUIPacket.class, C2SObstacleDoorGUIPacket::encode, C2SObstacleDoorGUIPacket::decode, C2SObstacleDoorGUIPacket::handle);
            INSTANCE.registerMessage(id++, C2SSetUniversalSpawnerConfigPacket.class, C2SSetUniversalSpawnerConfigPacket::encode, C2SSetUniversalSpawnerConfigPacket::decode, C2SSetUniversalSpawnerConfigPacket::handle);
            INSTANCE.registerMessage(id++, C2SReloadWeaponPacket.class, C2SReloadWeaponPacket::encode, C2SReloadWeaponPacket::decode, C2SReloadWeaponPacket::handler);
            INSTANCE.registerMessage(id++, C2SSetWallWeaponConfigPacket.class, C2SSetWallWeaponConfigPacket::encode, C2SSetWallWeaponConfigPacket::decode, C2SSetWallWeaponConfigPacket::handle);
            INSTANCE.registerMessage(id++, S2CSpecialWavePacket.class, S2CSpecialWavePacket::encode, S2CSpecialWavePacket::decode, S2CSpecialWavePacket::handle);
            INSTANCE.registerMessage(id++, C2SSavePerksConfigPacket.class, C2SSavePerksConfigPacket::encode, C2SSavePerksConfigPacket::decode, C2SSavePerksConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CPointGainPacket.class, S2CPointGainPacket::encode, S2CPointGainPacket::decode, S2CPointGainPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CPlayerDownPacket.class, S2CPlayerDownPacket::encode, S2CPlayerDownPacket::decode, S2CPlayerDownPacket::handle);
            INSTANCE.registerMessage(id++, S2CPlayerRevivePacket.class, S2CPlayerRevivePacket::encode, S2CPlayerRevivePacket::decode, S2CPlayerRevivePacket::handle);
            INSTANCE.registerMessage(id++, C2SReviveAttemptPacket.class, C2SReviveAttemptPacket::encode, C2SReviveAttemptPacket::decode, C2SReviveAttemptPacket::handle);
            INSTANCE.registerMessage(id++, S2CWaveUpdatePacket.class, S2CWaveUpdatePacket::encode, S2CWaveUpdatePacket::decode, S2CWaveUpdatePacket::handle);
            INSTANCE.registerMessage(id++, S2CPlayerPosePacket.class, S2CPlayerPosePacket::encode, S2CPlayerPosePacket::decode, S2CPlayerPosePacket::handle);
            INSTANCE.registerMessage(id++, S2CDisplayHitmarkerPacket.class, S2CDisplayHitmarkerPacket::encode, S2CDisplayHitmarkerPacket::decode, S2CDisplayHitmarkerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CRecoilPacket.class, S2CRecoilPacket::encode, S2CRecoilPacket::decode, S2CRecoilPacket::handle);
            INSTANCE.registerMessage(id++, S2CStopFourIsReadySoundPacket.class, S2CStopFourIsReadySoundPacket::encode, S2CStopFourIsReadySoundPacket::new, S2CStopFourIsReadySoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, me.cryo.zombierool.network.C2SMeleeAttackPacket.class, me.cryo.zombierool.network.C2SMeleeAttackPacket::encode, me.cryo.zombierool.network.C2SMeleeAttackPacket::new, me.cryo.zombierool.network.C2SMeleeAttackPacket::handle);
            INSTANCE.registerMessage(id++, S2CScreenShakePacket.class, S2CScreenShakePacket::encode, S2CScreenShakePacket::new, S2CScreenShakePacket::handle);
            INSTANCE.registerMessage(id++, S2CPlayGlobalSoundPacket.class, S2CPlayGlobalSoundPacket::encode, S2CPlayGlobalSoundPacket::decode, S2CPlayGlobalSoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CPlayPositionalSoundPacket.class, S2CPlayPositionalSoundPacket::encode, S2CPlayPositionalSoundPacket::decode, S2CPlayPositionalSoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSetEyeColorPacket.class, S2CSetEyeColorPacket::encode, S2CSetEyeColorPacket::decode, me.cryo.zombierool.network.handler.S2CSetEyeColorPacketHandler::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncColdWaterStatePacket.class, S2CSyncColdWaterStatePacket::encode, S2CSyncColdWaterStatePacket::decode, S2CSyncColdWaterStatePacket::handle);
            INSTANCE.registerMessage(id++, S2CStartGameAnimationPacket.class, S2CStartGameAnimationPacket::encode, S2CStartGameAnimationPacket::decode, S2CStartGameAnimationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CWaveChangeAnimationPacket.class, S2CWaveChangeAnimationPacket::encode, S2CWaveChangeAnimationPacket::decode, S2CWaveChangeAnimationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CAmmoCratePricePacket.class, S2CAmmoCratePricePacket::toBytes, S2CAmmoCratePricePacket::new, S2CAmmoCratePricePacket::handle);
            INSTANCE.registerMessage(id++, C2SRequestAmmoCrateInfoPacket.class, C2SRequestAmmoCrateInfoPacket::encode, C2SRequestAmmoCrateInfoPacket::new, C2SRequestAmmoCrateInfoPacket::handler);
            INSTANCE.registerMessage(id++, S2CUpdateOverlayPacket.class, S2CUpdateOverlayPacket::encode, S2CUpdateOverlayPacket::new, S2CUpdateOverlayPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncOverlaysPacket.class, S2CSyncOverlaysPacket::encode, S2CSyncOverlaysPacket::new, S2CSyncOverlaysPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncDynamicChalkPacket.class, S2CSyncDynamicChalkPacket::encode, S2CSyncDynamicChalkPacket::decode, S2CSyncDynamicChalkPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SUpdateChalkItemPacket.class, C2SUpdateChalkItemPacket::encode, C2SUpdateChalkItemPacket::decode, C2SUpdateChalkItemPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CSyncWunderfizzStatePacket.class, S2CSyncWunderfizzStatePacket::encode, S2CSyncWunderfizzStatePacket::new, S2CSyncWunderfizzStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CStartWunderfizzDrinkAnimationPacket.class, S2CStartWunderfizzDrinkAnimationPacket::encode, S2CStartWunderfizzDrinkAnimationPacket::new, S2CStartWunderfizzDrinkAnimationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CNotifyPurchasePacket.class, S2CNotifyPurchasePacket::encode, S2CNotifyPurchasePacket::decode, S2CNotifyPurchasePacket::handle);
            INSTANCE.registerMessage(id++, me.cryo.zombierool.MapEventManager.S2CMapConfigPacket.class, me.cryo.zombierool.MapEventManager.S2CMapConfigPacket::encode, me.cryo.zombierool.MapEventManager.S2CMapConfigPacket::decode, me.cryo.zombierool.MapEventManager.S2CMapConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, me.cryo.zombierool.MapEventManager.S2CPlaySoundPacket.class, me.cryo.zombierool.MapEventManager.S2CPlaySoundPacket::encode, me.cryo.zombierool.MapEventManager.S2CPlaySoundPacket::decode, me.cryo.zombierool.MapEventManager.S2CPlaySoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncActiveWunderfizzPositionPacket.class, S2CSyncActiveWunderfizzPositionPacket::encode, S2CSyncActiveWunderfizzPositionPacket::new, S2CSyncActiveWunderfizzPositionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SSyncClientPrefsPacket.class, C2SSyncClientPrefsPacket::encode, C2SSyncClientPrefsPacket::decode, C2SSyncClientPrefsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, me.cryo.zombierool.core.network.C2SShootPacket.class, me.cryo.zombierool.core.network.C2SShootPacket::encode, me.cryo.zombierool.core.network.C2SShootPacket::decode, me.cryo.zombierool.core.network.C2SShootPacket::handle);
            INSTANCE.registerMessage(id++, me.cryo.zombierool.core.network.S2CSyncPlayerDataPacket.class, me.cryo.zombierool.core.network.S2CSyncPlayerDataPacket::encode, me.cryo.zombierool.core.network.S2CSyncPlayerDataPacket::decode, me.cryo.zombierool.core.network.S2CSyncPlayerDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CWeaponVfxPacket.class, S2CWeaponVfxPacket::encode, S2CWeaponVfxPacket::decode, S2CWeaponVfxPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncGorePacket.class, S2CSyncGorePacket::encode, S2CSyncGorePacket::decode, S2CSyncGorePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, me.cryo.zombierool.network.S2CVisualBlockCrackPacket.class, me.cryo.zombierool.network.S2CVisualBlockCrackPacket::encode, me.cryo.zombierool.network.S2CVisualBlockCrackPacket::new, me.cryo.zombierool.network.S2CVisualBlockCrackPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncMapVisualsPacket.class, S2CSyncMapVisualsPacket::encode, S2CSyncMapVisualsPacket::decode, S2CSyncMapVisualsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSetFogPresetPacket.class, S2CSetFogPresetPacket::encode, S2CSetFogPresetPacket::decode, S2CSetFogPresetPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncBlueFirePacket.class, S2CSyncBlueFirePacket::encode, S2CSyncBlueFirePacket::decode, S2CSyncBlueFirePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2COpenConfigMenuPacket.class, S2COpenConfigMenuPacket::encode, S2COpenConfigMenuPacket::decode, S2COpenConfigMenuPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SUpdateConfigPacket.class, C2SUpdateConfigPacket::encode, C2SUpdateConfigPacket::decode, C2SUpdateConfigPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, me.cryo.zombierool.network.packet.C2SRequestConfigMenuPacket.class, me.cryo.zombierool.network.packet.C2SRequestConfigMenuPacket::encode, me.cryo.zombierool.network.packet.C2SRequestConfigMenuPacket::new, me.cryo.zombierool.network.packet.C2SRequestConfigMenuPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CSyncWeatherPacket.class, S2CSyncWeatherPacket::encode, S2CSyncWeatherPacket::decode, S2CSyncWeatherPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SGenerateWeaponMappingPacket.class, C2SGenerateWeaponMappingPacket::encode, C2SGenerateWeaponMappingPacket::decode, C2SGenerateWeaponMappingPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CReloadWeaponsPacket.class, S2CReloadWeaponsPacket::encode, S2CReloadWeaponsPacket::decode, S2CReloadWeaponsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncDynamicSkinPacket.class, S2CSyncDynamicSkinPacket::encode, S2CSyncDynamicSkinPacket::decode, S2CSyncDynamicSkinPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CPlayEntityVoiceSoundPacket.class, S2CPlayEntityVoiceSoundPacket::encode, S2CPlayEntityVoiceSoundPacket::decode, S2CPlayEntityVoiceSoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncPlayerStatsPacket.class, S2CSyncPlayerStatsPacket::encode, S2CSyncPlayerStatsPacket::decode, S2CSyncPlayerStatsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncPickablesPacket.class, S2CSyncPickablesPacket::encode, S2CSyncPickablesPacket::decode, S2CSyncPickablesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SSetBlindBuyConfigPacket.class, C2SSetBlindBuyConfigPacket::encode, C2SSetBlindBuyConfigPacket::decode, C2SSetBlindBuyConfigPacket::handle);
            INSTANCE.registerMessage(id++, S2CSyncMysteryBoxStatePacket.class, S2CSyncMysteryBoxStatePacket::encode, S2CSyncMysteryBoxStatePacket::decode, S2CSyncMysteryBoxStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncDynamicSoundPacket.class, S2CSyncDynamicSoundPacket::encode, S2CSyncDynamicSoundPacket::decode, S2CSyncDynamicSoundPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SSyncScopeStatePacket.class, C2SSyncScopeStatePacket::encode, C2SSyncScopeStatePacket::decode, C2SSyncScopeStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CTriggerScopeScreamerPacket.class, S2CTriggerScopeScreamerPacket::encode, S2CTriggerScopeScreamerPacket::decode, S2CTriggerScopeScreamerPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, me.cryo.zombierool.network.packet.C2SToggleCrawlPacket.class, me.cryo.zombierool.network.packet.C2SToggleCrawlPacket::encode, me.cryo.zombierool.network.packet.C2SToggleCrawlPacket::decode, me.cryo.zombierool.network.packet.C2SToggleCrawlPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CSyncCrawlStatePacket.class, S2CSyncCrawlStatePacket::encode, S2CSyncCrawlStatePacket::decode, S2CSyncCrawlStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SUpdateLethalStatePacket.class, C2SUpdateLethalStatePacket::encode, C2SUpdateLethalStatePacket::decode, C2SUpdateLethalStatePacket::handle);
            INSTANCE.registerMessage(id++, C2SThrowBackGrenadePacket.class, C2SThrowBackGrenadePacket::encode, C2SThrowBackGrenadePacket::decode, C2SThrowBackGrenadePacket::handle);
            INSTANCE.registerMessage(id++, S2CSyncThirdPersonAnimPacket.class, S2CSyncThirdPersonAnimPacket::encode, S2CSyncThirdPersonAnimPacket::decode, S2CSyncThirdPersonAnimPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncBowieKnifePacket.class, S2CSyncBowieKnifePacket::encode, S2CSyncBowieKnifePacket::decode, S2CSyncBowieKnifePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncInteractablesPacket.class, S2CSyncInteractablesPacket::encode, S2CSyncInteractablesPacket::decode, S2CSyncInteractablesPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SSecretConsoleCommandPacket.class, C2SSecretConsoleCommandPacket::encode, C2SSecretConsoleCommandPacket::decode, C2SSecretConsoleCommandPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CSecretConsoleLogPacket.class, S2CSecretConsoleLogPacket::encode, S2CSecretConsoleLogPacket::decode, S2CSecretConsoleLogPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CSyncCareerDataPacket.class, S2CSyncCareerDataPacket::encode, S2CSyncCareerDataPacket::decode, S2CSyncCareerDataPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CCareerNotificationPacket.class, S2CCareerNotificationPacket::encode, S2CCareerNotificationPacket::decode, S2CCareerNotificationPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, C2SBuyCamoPacket.class, C2SBuyCamoPacket::encode, C2SBuyCamoPacket::decode, C2SBuyCamoPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, C2SEquipCamoPacket.class, C2SEquipCamoPacket::encode, C2SEquipCamoPacket::decode, C2SEquipCamoPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, C2SSyncEquippedCamosPacket.class, C2SSyncEquippedCamosPacket::encode, C2SSyncEquippedCamosPacket::decode, C2SSyncEquippedCamosPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, C2SSyncEquippedSkinsPacket.class, C2SSyncEquippedSkinsPacket::encode, C2SSyncEquippedSkinsPacket::decode, C2SSyncEquippedSkinsPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
            INSTANCE.registerMessage(id++, S2CAddZRFPacket.class, S2CAddZRFPacket::encode, S2CAddZRFPacket::decode, S2CAddZRFPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CProgressChallengePacket.class, S2CProgressChallengePacket::encode, S2CProgressChallengePacket::decode, S2CProgressChallengePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CProgressWeaponStatPacket.class, S2CProgressWeaponStatPacket::encode, S2CProgressWeaponStatPacket::new, S2CProgressWeaponStatPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CMatchRecapPacket.class, S2CMatchRecapPacket::encode, S2CMatchRecapPacket::decode, S2CMatchRecapPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            INSTANCE.registerMessage(id++, S2CZombieBloodOverlayPacket.class, S2CZombieBloodOverlayPacket::encode, S2CZombieBloodOverlayPacket::decode, S2CZombieBloodOverlayPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        });
    }
}