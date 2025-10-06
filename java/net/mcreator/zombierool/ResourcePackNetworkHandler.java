package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class ResourcePackNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("zombierool", "resourcepack"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.registerMessage(id(), RequestResourcePackMessage.class,
            RequestResourcePackMessage::encode,
            RequestResourcePackMessage::decode,
            RequestResourcePackMessage::handle);

        INSTANCE.registerMessage(id(), ResourcePackInfoMessage.class,
            ResourcePackInfoMessage::encode,
            ResourcePackInfoMessage::decode,
            ResourcePackInfoMessage::handle);
    }

    public static void sendToServer(Object message) {
        INSTANCE.sendToServer(message);
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static class RequestResourcePackMessage {
        public RequestResourcePackMessage() {}

        public static void encode(RequestResourcePackMessage msg, FriendlyByteBuf buf) {
        }

        public static RequestResourcePackMessage decode(FriendlyByteBuf buf) {
            return new RequestResourcePackMessage();
        }

        public static void handle(RequestResourcePackMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    ResourcePackWorldData data = ResourcePackWorldData.get(player.server);
                    if (data != null && data.hasResourcePack()) {
                        sendToPlayer(player, new ResourcePackInfoMessage(
                            data.getResourcePackUrl(),
                            data.getResourcePackName()
                        ));
                    } else {
                        sendToPlayer(player, new ResourcePackInfoMessage(null, null));
                    }
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class ResourcePackInfoMessage {
        private final String url;
        private final String name;

        public ResourcePackInfoMessage(String url, String name) {
            this.url = url;
            this.name = name;
        }

        public static void encode(ResourcePackInfoMessage msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.url != null);
            if (msg.url != null) {
                buf.writeUtf(msg.url);
                buf.writeUtf(msg.name);
            }
        }

        public static ResourcePackInfoMessage decode(FriendlyByteBuf buf) {
            boolean hasRP = buf.readBoolean();
            if (hasRP) {
                return new ResourcePackInfoMessage(buf.readUtf(), buf.readUtf());
            }
            return new ResourcePackInfoMessage(null, null);
        }

        public static void handle(ResourcePackInfoMessage msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                if (msg.url != null && msg.name != null) {
                    net.mcreator.zombierool.client.MultiplayerResourcePackHandler.showPrompt(msg.url, msg.name);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}