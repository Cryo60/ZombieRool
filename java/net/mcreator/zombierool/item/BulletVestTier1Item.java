package net.mcreator.zombierool.item;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag; // Importation pour CompoundTag

@Mod.EventBusSubscriber
public abstract class BulletVestTier1Item extends ArmorItem {

    public BulletVestTier1Item(ArmorItem.Type type, Item.Properties properties) {
        super(new ArmorMaterial() {
            @Override
            public int getDurabilityForType(ArmorItem.Type type) {
                // *** CORRECTION CLÉ ICI ***
                // Définit une très grande durabilité pour empêcher Minecraft de casser l'armure
                // via son propre système de durabilité. La durabilité est désormais entièrement gérée par NBT.
                return Integer.MAX_VALUE;
            }

            @Override
            public int getDefenseForType(ArmorItem.Type type) {
                // Points de défense pour cette armure (mis à 0 car les dégâts sont gérés par notre logique personnalisée)
                return 0;
            }

            @Override
            public int getEnchantmentValue() {
                // Enchantabilité de l'armure
                return 9;
            }

            @Override
            public SoundEvent getEquipSound() {
                // Son joué lorsque l'armure est équipée
                return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:equip_bulletvest"));
            }

            @Override
            public Ingredient getRepairIngredient() {
                // Ingrédient nécessaire pour réparer l'armure (vide car elle se casse complètement)
                return Ingredient.of();
            }

            @Override
            public String getName() {
                // Nom du matériau de l'armure
                return "bullet_vest_tier_1";
            }

            @Override
            public float getToughness() {
                // Robustesse de l'armure (mise à 0 car ce n'est pas une armure standard)
                return 0f;
            }

            @Override
            public float getKnockbackResistance() {
                // Résistance au recul (mise à 0)
                return 0f;
            }
        }, type, properties);
    }

    public static class Chestplate extends BulletVestTier1Item {
        public Chestplate() {
            // Constructeur pour le plastron, définissant son type et ses propriétés
            super(ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1));
        }

        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            // Spécifie la texture pour le modèle d'armure
            return "zombierool:textures/models/armor/diamond__layer_1.png";
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // Vérifie si l'entité subissant des dégâts est une LivingEntity (par exemple, un joueur, un mob)
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity player = (LivingEntity) event.getEntity();

            // Récupère l'ItemStack dans l'emplacement du plastron
            ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);

            // Vérifie si l'objet équipé est notre BulletVestTier1Item.Chestplate personnalisé
            if (chestplate.getItem() instanceof BulletVestTier1Item.Chestplate && !chestplate.isEmpty()) {
                // Récupère ou crée la balise NBT pour l'ItemStack afin de stocker des données personnalisées
                CompoundTag tag = chestplate.getOrCreateTag();

                final int MAX_ARMOR_POINTS = 4;
                int currentArmorPoints = tag.getInt("BulletVestArmorPoints");
                boolean isVestInitialized = tag.getBoolean("BulletVestInitialized"); // Nouveau drapeau d'initialisation

                // Si le gilet n'a pas encore été initialisé (c'est-à-dire qu'il subit des dégâts pour la première fois),
                // force sa durabilité au maximum et marque-le comme initialisé.
                if (!isVestInitialized) {
                    currentArmorPoints = MAX_ARMOR_POINTS;
                    tag.putInt("BulletVestArmorPoints", currentArmorPoints);
                    tag.putBoolean("BulletVestInitialized", true);
                }

                // Seulement absorber les dégâts s'il reste des points d'armure
                if (currentArmorPoints > 0) {
                    event.setAmount(0); // Définit les dégâts entrants à 0
                    event.setCanceled(true); // Annule complètement l'événement de dégâts

                    currentArmorPoints--; // Décrémente les points d'armure pour le coup
                    tag.putInt("BulletVestArmorPoints", currentArmorPoints); // Enregistre les points mis à jour dans NBT

                    // Joue un son lorsque la plaque se fissure
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:plate_crack")),
                            SoundSource.PLAYERS, 1.0f, 1.0f);

                    // Envoie un message au joueur (uniquement côté serveur)
                    if (!player.level().isClientSide) {
                        player.sendSystemMessage(Component.literal("Veste pare-balles : " + currentArmorPoints + " points restants."));
                    }

                    // Si les points d'armure tombent à 0 ou moins, brise le gilet
                    if (currentArmorPoints <= 0) {
                        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); // Retire le gilet de l'emplacement
                        if (!player.level().isClientSide) {
                            player.sendSystemMessage(Component.literal("Votre veste pare-balles s'est brisée !"));
                        }
                    }
                }
                // Sinon (currentArmorPoints est 0 ou moins), ce bloc 'if' est ignoré, et le joueur subit des dégâts normaux.
            }
        }
    }
}
