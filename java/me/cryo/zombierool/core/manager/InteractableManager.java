package me.cryo.zombierool.core.manager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncInteractablesPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InteractableManager {
    public static class Interactable {
        public String id;
        public Vec3 pos;
        public double radius;
        public String langKey;

        public Interactable(String id, Vec3 pos, double radius, String langKey) {
            this.id = id;
            this.pos = pos;
            this.radius = radius;
            this.langKey = langKey;
        }
    }

    private static final Map<String, Interactable> interactables = new ConcurrentHashMap<>();

    public static void register(String id, Vec3 pos, double radius, String langKey) {
        interactables.put(id, new Interactable(id, pos, radius, langKey));
        syncAll();
    }

    public static void remove(String id) {
        if (interactables.remove(id) != null) {
            syncAll();
        }
    }

    public static void clear() {
        interactables.clear();
        syncAll();
    }

    public static Map<String, Interactable> getInteractables() {
        return interactables;
    }

    public static void syncAll() {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncInteractablesPacket(interactables));
    }

    public static void syncToPlayer(ServerPlayer player) {
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncInteractablesPacket(interactables));
    }
}