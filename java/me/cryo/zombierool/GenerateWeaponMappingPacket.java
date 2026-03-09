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

import java.io.File;
import java.io.FileWriter;
import java.util.function.Supplier;

public class GenerateWeaponMappingPacket {
    private final ResourceLocation taczId;

    public GenerateWeaponMappingPacket(ResourceLocation taczId) {
        this.taczId = taczId;
    }

    public GenerateWeaponMappingPacket(FriendlyByteBuf buf) {
        this.taczId = buf.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(taczId);
    }

    public static GenerateWeaponMappingPacket decode(FriendlyByteBuf buf) {
        return new GenerateWeaponMappingPacket(buf);
    }

    public static void handle(GenerateWeaponMappingPacket msg, Supplier<NetworkEvent.Context> ctx) {
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
                        def.type = "RIFLE"; // Default generic type
                        def.tacz = new WeaponSystem.Definition.Tacz();
                        def.tacz.gun_id = msg.taczId.toString();

                        // Populate base stats directly from TacZ without crashing if mod isn't here
                        me.cryo.zombierool.integration.TacZIntegration.generateMappingTemplate(msg.taczId, def);

                        try (FileWriter writer = new FileWriter(jsonFile)) {
                            Gson gson = new GsonBuilder().setPrettyPrinting().create();
                            gson.toJson(def, writer);
                            player.sendSystemMessage(Component.literal("§aFichier de mapping généré : " + jsonFile.getName()));
                            
                            // Hot-reload the weapons
                            WeaponSystem.Loader.loadWeapons();
                            me.cryo.zombierool.integration.TacZIntegration.syncTaczGunData();
                        }
                    } else {
                        player.sendSystemMessage(Component.literal("§eLe fichier " + jsonFile.getName() + " existe déjà ! Modifiez-le puis faites un Reset Loot."));
                    }
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("§cErreur lors de la génération du fichier."));
                    e.printStackTrace();
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}