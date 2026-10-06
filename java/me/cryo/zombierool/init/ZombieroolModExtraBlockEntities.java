package me.cryo.zombierool.init;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.core.registry.ZRBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZombieroolModExtraBlockEntities {
	public static RegistryObject<BlockEntityType<DefenseDoorSystem.DefenseDoorBlockEntity>> DEFENSE_DOOR = 
	    RegistryObject.create(new ResourceLocation(ZombieroolMod.MODID, "defense_door"), ForgeRegistries.BLOCK_ENTITY_TYPES);
	
	@SubscribeEvent
	public static void onRegister(RegisterEvent event) {
	    if (event.getRegistryKey().equals(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES)) {
	        // Seul le bloc valide est DEFENSE_DOOR. L'autre (OPENED) est un bloc de migration qui ne doit pas avoir de TileEntity
	        BlockEntityType<DefenseDoorSystem.DefenseDoorBlockEntity> type = BlockEntityType.Builder.of(
	                DefenseDoorSystem.DefenseDoorBlockEntity::new,
	                ZRBlocks.DEFENSE_DOOR.get()
	        ).build(null);
	        
	        event.register(ForgeRegistries.Keys.BLOCK_ENTITY_TYPES, 
	            helper -> helper.register(new ResourceLocation(ZombieroolMod.MODID, "defense_door"), type)
	        );
	    }
	}
}
