package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.core.system.WeaponSystem;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.integration.TacZIntegration;

import java.util.function.Supplier;

public class C2SSecretConsoleCommandPacket {
    private final String command;

    public C2SSecretConsoleCommandPacket(String command) {
        this.command = command;
    }

    public C2SSecretConsoleCommandPacket(FriendlyByteBuf buf) {
        this.command = buf.readUtf();
    }

    public static void encode(C2SSecretConsoleCommandPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.command);
    }

    public static C2SSecretConsoleCommandPacket decode(FriendlyByteBuf buf) {
        return new C2SSecretConsoleCommandPacket(buf);
    }

    private static void sendLog(ServerPlayer player, String message) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CSecretConsoleLogPacket("§7" + message));
    }

    public static void handle(C2SSecretConsoleCommandPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) {
                if (player != null) sendLog(player, "§cYou do not have permission to use the console.");
                return;
            }

            ServerLevel level = player.serverLevel();
            String cmd = msg.command.trim();

            try {
                if (cmd.startsWith("lua ")) {
                    String code = cmd.substring(4);
                    String result = LuaScriptManager.executeString(code);
                    sendLog(player, result);
                } 
                else if (cmd.startsWith("points ")) {
                    int amount = Integer.parseInt(cmd.substring(7).trim());
                    PointManager.modifyScore(player, amount);
                    sendLog(player, "§aPoints updated by " + amount);
                } 
                // LA COMMANDE ZRF EST MAINTENANT PUREMENT CLIENT DANS SecretConsoleScreen.java,
                // plus besoin de l'intercepter ici !
                else if (cmd.startsWith("wave ")) {
                    int wave = Integer.parseInt(cmd.substring(5).trim());
                    WaveManager.forceSetWave(level, wave);
                    sendLog(player, "§aWave forced to " + wave);
                } 
                else if (cmd.equals("killall")) {
                    int count = 0;
                    for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                        if (e instanceof me.cryo.zombierool.entity.ZombieEntity ||
                            e instanceof me.cryo.zombierool.entity.CrawlerEntity ||
                            e instanceof me.cryo.zombierool.entity.HellhoundEntity) {
                            ((LivingEntity) e).setHealth(0);
                            ((LivingEntity) e).die(level.damageSources().generic());
                            count++;
                        }
                    }
                    sendLog(player, "§aKilled " + count + " active zombies.");
                } 
                else if (cmd.startsWith("weapon ")) {
                    String id = cmd.substring(7).trim();
                    net.minecraft.world.item.ItemStack weapon = WeaponFacade.createWeaponStack(id, false, player);
                    if (!weapon.isEmpty()) {
                        WeaponFacade.grantWeaponToPlayer(player, weapon);
                        sendLog(player, "§aGiven weapon: " + id);
                    } else {
                        sendLog(player, "§cUnknown weapon ID.");
                    }
                } 
                else if (cmd.equals("god") || cmd.equals("noclip")) {
                    if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                        player.setGameMode(GameType.SURVIVAL);
                        sendLog(player, "§aGamemode set to Survival.");
                    } else {
                        player.setGameMode(cmd.equals("noclip") ? GameType.SPECTATOR : GameType.CREATIVE);
                        sendLog(player, "§aGamemode set to " + (cmd.equals("noclip") ? "Spectator" : "Creative"));
                    }
                } 
                else if (cmd.equals("reload scripts")) {
                    LuaScriptManager.loadScripts(level);
                    sendLog(player, "§aLua scripts reloaded successfully.");
                } 
                else if (cmd.equals("reload weapons")) {
                    WeaponSystem.Loader.loadWeapons();
                    TacZIntegration.syncTaczGunData();
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new me.cryo.zombierool.network.packet.S2CReloadWeaponsPacket());
                    sendLog(player, "§aWeapon definitions reloaded and synced.");
                } 
                else if (cmd.equals("zombies")) {
                    int count = 0;
                    for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                        if (e instanceof me.cryo.zombierool.entity.ZombieEntity ||
                            e instanceof me.cryo.zombierool.entity.CrawlerEntity ||
                            e instanceof me.cryo.zombierool.entity.HellhoundEntity) {
                            count++;
                        }
                    }
                    sendLog(player, "§bThere are currently " + count + " active zombies in the world.");
                } 
                else if (cmd.equals("tp_last")) {
                    LivingEntity last = null;
                    for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
                        if (e instanceof me.cryo.zombierool.entity.ZombieEntity ||
                            e instanceof me.cryo.zombierool.entity.CrawlerEntity ||
                            e instanceof me.cryo.zombierool.entity.HellhoundEntity) {
                            last = (LivingEntity) e;
                        }
                    }
                    if (last != null) {
                        last.teleportTo(player.getX(), player.getY(), player.getZ());
                        sendLog(player, "§aTeleported a zombie to your location.");
                    } else {
                        sendLog(player, "§cNo zombies found.");
                    }
                }
                else {
                    sendLog(player, "§cUnknown command. Available: lua, points, wave, killall, weapon, god, noclip, reload scripts, reload weapons, zombies, tp_last");
                }

            } catch (Exception e) {
                sendLog(player, "§cCommand error: " + e.getMessage());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}