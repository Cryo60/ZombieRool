package net.mcreator.zombierool.client;

import net.mcreator.zombierool.ZombieroolMod; // Votre classe de mod principale
import net.mcreator.zombierool.item.DeagleWeaponItem; // Votre item Deagle
import net.mcreator.zombierool.init.ZombieroolModItems; // Classe où vos items sont enregistrés (vérifiez le nom exact dans votre projet MCreator)
import net.mcreator.zombierool.item.R4CWeaponItem;
import net.mcreator.zombierool.item.VandalWeaponItem;
import net.mcreator.zombierool.item.RPGWeaponItem; // NOUVEAU: Importe ton RPGWeaponItem
import net.mcreator.zombierool.item.NeedlerWeaponItem; // NOUVEAU: Importe ton NeedlerWeaponItem


import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.ItemStack; // Ajoute cet import pour ItemStack

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Enregistre la propriété 'is_golden' pour votre DeagleWeaponItem
            ItemProperties.register(ZombieroolModItems.DEAGLE_WEAPON.get(),
                new ResourceLocation(ZombieroolMod.MODID, "is_golden"),
                (stack, world, entity, seed) -> {
                    if (stack.getItem() instanceof DeagleWeaponItem deagleItem) {
                        return deagleItem.isGoldenDeagle(stack) ? 1.0F : 0.0F;
                    }
                    return 0.0F;
                }
            );

            // Nouveau: Enregistre la propriété 'is_black_ice' pour votre R4CWeaponItem
            ItemProperties.register(ZombieroolModItems.R_4_C_WEAPON.get(), // Assurez-vous que R4C_WEAPON est l'ID correct de votre R4C
                new ResourceLocation(ZombieroolMod.MODID, "is_black_ice"),
                (stack, world, entity, seed) -> {
                    if (stack.getItem() instanceof R4CWeaponItem r4cItem) {
                        return r4cItem.isBlackIce(stack) ? 1.0F : 0.0F;
                    }
                    return 0.0F;
                }
            );

            ItemProperties.register(ZombieroolModItems.VANDAL_WEAPON.get(), // Assurez-vous que VANDAL_WEAPON est l'ID correct de votre Vandal
                new ResourceLocation(ZombieroolMod.MODID, "is_dragon_vandal"),
                (stack, world, entity, seed) -> {
                    if (stack.getItem() instanceof VandalWeaponItem vandalItem) {
                        return vandalItem.isDragonVandal(stack) ? 1.0F : 0.0F;
                    }
                    return 0.0F;
                }
            );

            // --- AJOUTS POUR LE RPG ET LE NEEDLER ---

            // Enregistrement de la propriété 'empty' pour RPGWeaponItem
            ItemProperties.register(ZombieroolModItems.RPG_WEAPON.get(), // Assure-toi que RPG_WEAPON est l'ID correct de ton RPG
                new ResourceLocation(ZombieroolMod.MODID, "empty"),
                (stack, world, entity, seed) -> {
                    if (stack.getItem() instanceof RPGWeaponItem rpgItem) { // Vérifie que c'est bien l'instance de ton RPG
                        return rpgItem.getAmmo(stack) == 0 ? 1.0F : 0.0F;
                    }
                    return 0.0F; // Retourne 0.0 si ce n'est pas un RPG (par sécurité)
                }
            );

            // Enregistrement de la propriété 'empty_level' pour NeedlerWeaponItem
            ItemProperties.register(ZombieroolModItems.NEEDLER_WEAPON.get(), // Assure-toi que NEEDLER_WEAPON est l'ID correct de ton Needler
                new ResourceLocation(ZombieroolMod.MODID, "empty_level"),
                (stack, world, entity, seed) -> {
                    if (stack.getItem() instanceof NeedlerWeaponItem needlerItem) { // Vérifie que c'est bien l'instance de ton Needler
                        int currentAmmo = needlerItem.getAmmo(stack);
                        int maxAmmo = needlerItem.getMaxAmmo();

                        if (maxAmmo <= 0) {
                            return 0.0F;
                        }
                        return (float) (maxAmmo - currentAmmo) / maxAmmo;
                    }
                    return 0.0F; // Retourne 0.0 si ce n'est pas un Needler (par sécurité)
                }
            );
            // --- FIN DES AJOUTS ---

        });
    }
}