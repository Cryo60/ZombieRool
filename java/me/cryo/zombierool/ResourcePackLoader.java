package me.cryo.zombierool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.locating.IModFile;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ResourcePackLoader {

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            try {
                IModFile modFile = ModList.get().getModFileById("zombierool").getFile();
                Path resourcePath = modFile.findResource("resourcepacks/ZombieRool");

                event.addRepositorySource((packConsumer) -> {
                    String description = Component.translatable("resourcepack.zombierool.description").getString();
                    Pack pack = Pack.readMetaAndCreate(
                            "builtin/zombierool_pack",
                            Component.literal(description),
                            true,
                            (path) -> new PathPackResources(path, resourcePath, true),
                            PackType.CLIENT_RESOURCES,
                            Pack.Position.TOP,
                            PackSource.BUILT_IN
                    );
                    
                    if (pack != null) {
                        packConsumer.accept(pack);
                    }
                });
            } catch (Exception e) {
                System.err.println("[ZombieRool] Error loading resource pack: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}