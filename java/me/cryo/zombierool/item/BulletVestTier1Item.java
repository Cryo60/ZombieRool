package me.cryo.zombierool.item;
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
import net.minecraft.nbt.CompoundTag; 
import net.minecraft.ChatFormatting;

@Mod.EventBusSubscriber
public abstract class BulletVestTier1Item extends ArmorItem {
    public BulletVestTier1Item(ArmorItem.Type type, Item.Properties properties) {
        super(new ArmorMaterial() {
            @Override
            public int getDurabilityForType(ArmorItem.Type type) {
                return Integer.MAX_VALUE;
            }
            @Override
            public int getDefenseForType(ArmorItem.Type type) {
                return 0;
            }
            @Override
            public int getEnchantmentValue() {
                return 9;
            }
            @Override
            public SoundEvent getEquipSound() {
                return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:equip_bulletvest"));
            }
            @Override
            public Ingredient getRepairIngredient() {
                return Ingredient.of();
            }
            @Override
            public String getName() {
                return "bullet_vest_tier_1";
            }
            @Override
            public float getToughness() {
                return 0f;
            }
            @Override
            public float getKnockbackResistance() {
                return 0f;
            }
        }, type, properties);
    }

    public static class Chestplate extends BulletVestTier1Item {
        public Chestplate() {
            super(ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1));
        }
        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return "zombierool:textures/models/armor/diamond__layer_1.png";
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity) {
            LivingEntity player = (LivingEntity) event.getEntity();
            ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
            
            if (chestplate.getItem() instanceof BulletVestTier1Item.Chestplate && !chestplate.isEmpty()) {
                CompoundTag tag = chestplate.getOrCreateTag();
                final int MAX_ARMOR_POINTS = 4;
                int currentArmorPoints = tag.getInt("BulletVestArmorPoints");
                boolean isVestInitialized = tag.getBoolean("BulletVestInitialized"); 

                if (!isVestInitialized) {
                    currentArmorPoints = MAX_ARMOR_POINTS;
                    tag.putInt("BulletVestArmorPoints", currentArmorPoints);
                    tag.putBoolean("BulletVestInitialized", true);
                }

                if (currentArmorPoints > 0) {
                    event.setAmount(0); 
                    event.setCanceled(true); 
                    currentArmorPoints--; 
                    tag.putInt("BulletVestArmorPoints", currentArmorPoints); 
                    
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:plate_crack")),
                            SoundSource.PLAYERS, 1.0f, 1.0f);
                            
                    if (!player.level().isClientSide) {
                        player.sendSystemMessage(Component.translatable("message.zombierool.vest.remaining", currentArmorPoints));
                    }

                    if (currentArmorPoints <= 0) {
                        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY); 
                        if (!player.level().isClientSide) {
                            player.sendSystemMessage(Component.translatable("message.zombierool.vest.broken").withStyle(ChatFormatting.RED));
                        }
                    }
                }
            }
        }
    }
}