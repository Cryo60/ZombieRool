package me.cryo.zombierool.handlers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import me.cryo.zombierool.item.throwable.Grenade;
import me.cryo.zombierool.item.throwable.Molotov;
import me.cryo.zombierool.item.throwable.Stielhandgranate;
import me.cryo.zombierool.item.throwable.MonkeyBomb;
import me.cryo.zombierool.ExplosionControl;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncThirdPersonAnimPacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LethalWeaponManager {
    private static final Map<UUID, Integer> cookingTimers = new ConcurrentHashMap<>();
    private static final Map<UUID, String> pickedUpLethals = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> throwCooldowns = new ConcurrentHashMap<>();
    private static final int MAX_COOK_TICKS = 100;
    public static boolean isCooking(UUID playerId) {
        return cookingTimers.containsKey(playerId);
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID pid = event.getEntity().getUUID();
        cookingTimers.remove(pid);
        pickedUpLethals.remove(pid);
        throwCooldowns.remove(pid);
    }
    public static void startCookingPickedUpGrenade(ServerPlayer player, int cookedTicks, String type) {
        cookingTimers.put(player.getUUID(), cookedTicks);
        pickedUpLethals.put(player.getUUID(), type);
        String animName = getAnimNameForCook(type);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new S2CSyncThirdPersonAnimPacket(player.getUUID(), animName, 100));
        SoundEvent sound = getPinSoundForType(type);
        if (sound != null) {
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1f, 1f);
        }
    }
    private static String getAnimNameForCook(String type) {
        if (type.contains("molotov")) return "molotov_light";
        if (type.contains("stielhandgranate")) return "stielhandgranate_cook";
        if (type.contains("monkey_bomb")) return "monkey_bomb_cook";
        return "grenade_cook";
    }
    private static String getAnimNameForThrow(String type) {
        if (type.contains("molotov")) return "molotov_throw";
        if (type.contains("stielhandgranate")) return "stielhandgranate_throw";
        if (type.contains("monkey_bomb")) return "monkey_bomb_throw";
        return "grenade_throw";
    }
    private static SoundEvent getPinSoundForType(String type) {
        if (type.contains("molotov")) return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "molotov_light"));
        if (type.contains("monkey_bomb")) return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "monkey_ratchet"));
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "grenade_pin"));
    }
    public static void handleStateChange(ServerPlayer player, int state) {
        long now = player.level().getGameTime();
        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            if (state == 1) { 
                long cd = throwCooldowns.getOrDefault(player.getUUID(), 0L);
                if (now < cd) return;
                if (cap.getLethalCount() > 0 && !cookingTimers.containsKey(player.getUUID())) {
                    cookingTimers.put(player.getUUID(), 0);
                    String type = cap.getLethalType();
                    String animName = getAnimNameForCook(type);
                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new S2CSyncThirdPersonAnimPacket(player.getUUID(), animName, 100));
                    SoundEvent sound = getPinSoundForType(type);
                    if (sound != null) {
                        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1f, 1f);
                    }
                }
            } else if (state == 2) { 
                if (cookingTimers.containsKey(player.getUUID())) {
                    int cookedTicks = cookingTimers.remove(player.getUUID());
                    String pickedUpType = pickedUpLethals.remove(player.getUUID());
                    String type = pickedUpType != null ? pickedUpType : cap.getLethalType();
                    String animName = getAnimNameForThrow(type);
                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new S2CSyncThirdPersonAnimPacket(player.getUUID(), animName, 15));
                    if (pickedUpType != null) {
                        throwLethal(player, pickedUpType, cookedTicks);
                        throwCooldowns.put(player.getUUID(), now + 40); 
                    } else if (cap.getLethalCount() > 0) {
                        if (type.contains("molotov") && cookedTicks < 24) {
                            return; 
                        }
                        cap.setLethalCount(cap.getLethalCount() - 1);
                        cap.sync(player);
                        throwLethal(player, type, cookedTicks);
                        throwCooldowns.put(player.getUUID(), now + 40);
                    }
                }
            }
        });
    }
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Map.Entry<UUID, Integer> entry : cookingTimers.entrySet()) {
            UUID playerId = entry.getKey();
            int ticks = entry.getValue() + 1;
            if (ticks >= MAX_COOK_TICKS) {
                cookingTimers.remove(playerId);
                String pickedUpType = pickedUpLethals.remove(playerId);
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
                if (player != null && player.isAlive() && !player.isSpectator()) {
                    player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                        String type = pickedUpType != null ? pickedUpType : cap.getLethalType();
                        if (pickedUpType == null && cap.getLethalCount() > 0) {
                            cap.setLethalCount(cap.getLethalCount() - 1);
                            cap.sync(player);
                        }
                        if (type.contains("molotov")) {
                            player.setSecondsOnFire(10);
                            player.hurt(player.damageSources().onFire(), 1000.0f);
                        } else {
                            ExplosionControl.doCustomExplosion(player.level(), player, player.position(), 150.0f, 3.5f, 1.0f, 1000.0f, 1000.0f, 1.5f, "EXPLOSION", "zombierool:explosion_old", false);
                        }
                    });
                }
            } else {
                cookingTimers.put(playerId, ticks);
            }
        }
    }
    private static void throwLethal(ServerPlayer player, String type, int cookedTicks) {
        ThrowableItemProjectile projectile;
        if (type.contains("molotov")) {
            projectile = new Molotov.MolotovEntity(player.level(), player);
            SoundEvent throwSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "molotov_throw"));
            if (throwSound != null) player.level().playSound(null, player.blockPosition(), throwSound, SoundSource.PLAYERS, 1f, 1f);
        } else if (type.contains("stielhandgranate")) {
            projectile = new Stielhandgranate.StielhandgranateEntity(player.level(), player, cookedTicks);
        } else if (type.contains("monkey_bomb")) {
            projectile = new MonkeyBomb.MonkeyBombEntity(player.level(), player, cookedTicks);
        } else {
            projectile = new Grenade.GrenadeEntity(player.level(), player, cookedTicks);
        }
        Vec3 start = player.getEyePosition(1f);
        projectile.setPos(start.x, start.y - 0.1, start.z);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.2f, 1.0f);
        player.level().addFreshEntity(projectile);
    }
}