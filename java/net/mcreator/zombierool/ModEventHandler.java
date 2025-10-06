package net.mcreator.zombierool;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority; 
import net.mcreator.zombierool.init.ZombieroolModItems;
import net.mcreator.zombierool.MysteryBoxManager.Rarity; // Importation de l'enum Rarity
import net.mcreator.zombierool.MysteryBoxManager.WeightedWeapon; // Importation de la classe WeightedWeapon

@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEventHandler {

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("Zombierool: Populating Mystery Box Weapons list (from ModEventHandler)...");
            
            // --- Définition des Wonder Weapons (pour la vérification d'unicité) ---
            MysteryBoxManager.WONDER_WEAPONS.add(ZombieroolModItems.RAYGUN_MARKII.get());
            MysteryBoxManager.WONDER_WEAPONS.add(ZombieroolModItems.THUNDERGUN_WEAPON.get());
            MysteryBoxManager.WONDER_WEAPONS.add(ZombieroolModItems.WUNDERWAFFE_DG_2_WEAPON.get());
            MysteryBoxManager.WONDER_WEAPONS.add(ZombieroolModItems.WHISPER_WEAPON.get());
            MysteryBoxManager.WONDER_WEAPONS.add(ZombieroolModItems.RAYGUN_WEAPON.get());
            // Ajoutez d'autres Wonder Weapons ici si nécessaire
            
            // --- Ajout des armes avec leurs raretés ---
            // Wonder Weapons
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.RAYGUN_MARKII.get(), Rarity.WONDER_WEAPON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.THUNDERGUN_WEAPON.get(), Rarity.WONDER_WEAPON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.WUNDERWAFFE_DG_2_WEAPON.get(), Rarity.WONDER_WEAPON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.WHISPER_WEAPON.get(), Rarity.WONDER_WEAPON));

            // Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MA_5_D_WEAPON.get(), Rarity.EPIC));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.NEEDLER_WEAPON.get(), Rarity.EPIC));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.ENERGY_SWORD.get(), Rarity.EPIC));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.FLAMETHROWER_WEAPON.get(), Rarity.EPIC));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.PLASMA_PISTOL_WEAPON.get(), Rarity.EPIC));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.PERCEPTEUR_WEAPON.get(), Rarity.EPIC)); // User specified Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.VANDAL_WEAPON.get(), Rarity.EPIC));     // User specified Epic
            
            // Rare
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.M_16_A_4_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.DEAGLE_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.M_1_GARAND.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MP_40_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.STG_44_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.THOMPSON_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.PPSH_41.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.BAR_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.FG_42_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.SPAS_12_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.AK_47_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.FAMAS_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.SCAR_H_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.AUG_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.TAR_21_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.FNFAL_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.ACR_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.G_36C_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.P_90_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.VECTOR_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MP_7_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.RPD_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.RPK_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.GALIL_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.INTERVENTION_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.BARRET_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.DRAGUNOV_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.M_40_A_3_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.M_14_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.BROWNING_M_1911_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.ARC_12_WEAPON.get(), Rarity.RARE)); // Moved from Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.HYDRA_WEAPON.get(), Rarity.RARE));   // Moved from Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.RPG_WEAPON.get(), Rarity.RARE));    // Moved from Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MG_42_WEAPON.get(), Rarity.RARE));   // Moved from Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.STORM_WEAPON.get(), Rarity.RARE));  // Moved from Epic
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.BATTLE_RIFLE_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.COVENANT_CARBINE_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.DMR_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.CHINA_LAKE_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.SAW_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.SNIPER_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.L_85_A_2_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MPX_WEAPON.get(), Rarity.RARE));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.R_4_C_WEAPON.get(), Rarity.RARE));


            // Uncommon
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.KAR_98K_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.GLOCK_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MOSIN_NAGANT_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.ARISAKA_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.SPRINGFIELD_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.USP_45_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.FIVE_SEVEN_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.BERETTA_93R_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.GEWEHR_43_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.AK_74U_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.TRENCH_GUN_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.DOUBLE_BARREL_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MP_5_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.UMP_45_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.BIZON_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.UZI_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.TROICINQSEPTMAGNUM.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.M_7_SMG_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.OLD_SWORD_WEAPON.get(), Rarity.UNCOMMON));  
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.OLD_CROSSBOW_WEAPON.get(), Rarity.UNCOMMON));
            // --- New Uncommon Weapons ---
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.SHOTGUN_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.CZ_SCORPION_EVO_3_WEAPON.get(), Rarity.UNCOMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MASCHINENPISTOLE_28_WEAPON.get(), Rarity.UNCOMMON));


            // Common (starting pistol)
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.M_1911_WEAPON.get(), Rarity.COMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MAGNUM_WEAPON.get(), Rarity.COMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.MAUSER_C_96_WEAPON.get(), Rarity.COMMON));
            MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.add(new WeightedWeapon(ZombieroolModItems.STARR_1858_WEAPON.get(), Rarity.COMMON));
            

            System.out.println("Zombierool: Mystery Box Weapons (Weighted) list populated. Size: " + MysteryBoxManager.MYSTERY_BOX_WEAPONS_WEIGHTED.size());
            System.out.println("Zombierool: Wonder Weapons list populated. Size: " + MysteryBoxManager.WONDER_WEAPONS.size());

        });
    }
}
