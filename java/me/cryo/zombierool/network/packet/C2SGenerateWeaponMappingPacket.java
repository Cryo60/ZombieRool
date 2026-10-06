package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.system.WeaponSystem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import java.io.File;
import java.io.FileWriter;
import java.util.function.Supplier;

public class C2SGenerateWeaponMappingPacket {
    private final ResourceLocation taczId;

    public C2SGenerateWeaponMappingPacket(ResourceLocation taczId) {
        this.taczId = taczId;
    }

    public C2SGenerateWeaponMappingPacket(FriendlyByteBuf buf) {
        this.taczId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taczId);
    }

    public static C2SGenerateWeaponMappingPacket decode(FriendlyByteBuf buf) {
        return new C2SGenerateWeaponMappingPacket(buf);
    }

    public static void handle(C2SGenerateWeaponMappingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                try {
                    File dir = FMLPaths.GAMEDIR.get().resolve("data/zombierool/gameplay/weapons/").toFile();
                    dir.mkdirs();

                    String safeName = msg.taczId.getPath().replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    File jsonFile = new File(dir, safeName + ".json");

                    if (!jsonFile.exists()) {
                        WeaponSystem.Definition def = new WeaponSystem.Definition();
                        def.id = safeName;
                        def.name = safeName.toUpperCase();
                        def.type = "RIFLE"; 
                        def.tacz = new WeaponSystem.Definition.Tacz();
                        def.tacz.gun_id = msg.taczId.toString();

                        me.cryo.zombierool.integration.TacZIntegration.generateMappingTemplate(msg.taczId, def);

                        try (FileWriter writer = new FileWriter(jsonFile)) {
                            Gson gson = new GsonBuilder().setPrettyPrinting().create();
                            gson.toJson(def, writer);
                            player.sendSystemMessage(Component.translatable("message.zombierool.mapping.generated", jsonFile.getName()).withStyle(ChatFormatting.GREEN));
                            
                            WeaponSystem.Loader.loadWeapons();
                            me.cryo.zombierool.integration.TacZIntegration.syncTaczGunData();
                        }
                    } else {
                        player.sendSystemMessage(Component.translatable("message.zombierool.mapping.exists", jsonFile.getName()).withStyle(ChatFormatting.YELLOW));
                    }

                } catch (Exception e) {
                    player.sendSystemMessage(Component.translatable("message.zombierool.mapping.error").withStyle(ChatFormatting.RED));
                    e.printStackTrace();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
