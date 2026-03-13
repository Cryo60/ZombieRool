package me.cryo.zombierool.client;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import me.cryo.zombierool.init.ZombieroolModExtraBlockEntities;
import me.cryo.zombierool.core.registry.ZRBlocks;
import me.cryo.zombierool.core.registry.ZRRegistry;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZRClientSetup {

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
	    event.enqueueWork(() -> {
	        for (RegistryObject<Block> blockRegistryObject : ZRBlocks.CUTOUT_BLOCKS) {
	            ItemBlockRenderTypes.setRenderLayer(blockRegistryObject.get(), RenderType.cutout());
	        }
	        ItemBlockRenderTypes.setRenderLayer(ZRBlocks.DEFENSE_DOOR.get(), RenderType.cutout());

	        for (Item item : ZRRegistry.GUN_ITEMS) {
	            ItemProperties.register(item, new ResourceLocation("zombierool:empty"), (stack, level, entity, seed) -> {
	                if (stack.getItem() instanceof me.cryo.zombierool.api.IReloadable reloadable) {
	                    return reloadable.getAmmo(stack) == 0 ? 1.0F : 0.0F;
	                }
	                return 0.0F;
	            });

	            ItemProperties.register(item, new ResourceLocation("zombierool:is_pap"), (stack, level, entity, seed) -> {
	                if (stack.getItem() instanceof me.cryo.zombierool.api.IPackAPunchable pap) {
	                    return pap.isPackAPunched(stack) ? 1.0F : 0.0F;
	                }
	                return 0.0F;
	            });

	            ResourceLocation regName = ForgeRegistries.ITEMS.getKey(item);
	            if (regName != null && regName.getPath().equals("needler")) {
	                ItemProperties.register(item, new ResourceLocation("zombierool:ammo_stage"), (stack, level, entity, seed) -> {
	                    if (stack.getItem() instanceof me.cryo.zombierool.api.IReloadable r) {
	                        float ratio = (float)r.getAmmo(stack) / r.getMaxAmmo(stack);
	                        if (ratio <= 0) return 4.0F;
	                        if (ratio <= 0.25) return 3.0F;
	                        if (ratio <= 0.5) return 2.0F;
	                        if (ratio <= 0.75) return 1.0F;
	                    }
	                    return 0.0F;
	                });
	            }
	        }

            // Propriété pour le rendu dynamique de l'Universal Spawner dans l'inventaire
            ItemProperties.register(UniversalSpawnerSystem.UNIVERSAL_SPAWNER_ITEM.get(), new ResourceLocation("zombierool:spawner_type"), (stack, level, entity, seed) -> {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.contains("BlockStateTag")) {
                    String type = tag.getCompound("BlockStateTag").getString("mob_type");
                    return switch (type) {
                        case "crawler" -> 0.1f;
                        case "hellhound" -> 0.2f;
                        case "player" -> 0.3f;
                        default -> 0.0f; // zombie
                    };
                }
                return 0.0f;
            });
	    });
	}

	@SubscribeEvent
	public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
	    event.registerBlockEntityRenderer(ZombieroolModExtraBlockEntities.DEFENSE_DOOR.get(), DefenseDoorSystem.DefenseDoorRenderer::new);
	}
}