package me.cryo.zombierool;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.configuration.ZRClientConfig;

import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HalloweenManager {
    private static final int HALLOWEEN_START_DAY_OCTOBER = 20;
    private static final int HALLOWEEN_END_DAY_NOVEMBER = 5;

    private static final int[] ARMOR_COLORS = {
        0xFF8C00,  
        0xFFA500,  
        0xFF4500,  
        0x8B4513,  
        0x000000,  
        0x800080   
    };

    private static final Map<Class<? extends Entity>, Consumer<Entity>> ENTITY_MODIFIERS = new HashMap<>();

    static {
        registerEntityModifier(me.cryo.zombierool.entity.ZombieEntity.class, entity -> {
		    me.cryo.zombierool.entity.ZombieEntity zombie = (me.cryo.zombierool.entity.ZombieEntity) entity;
		    Random random = new Random();

		    ItemStack blackPumpkin = new ItemStack(me.cryo.zombierool.init.ZombieroolModBlocks.BLACK_PUMPKIN.get());
		    CompoundTag pumpkinTag = blackPumpkin.getOrCreateTag();
		    pumpkinTag.putBoolean("zombierool:emissive_pumpkin", true);
		    zombie.setItemSlot(EquipmentSlot.HEAD, blackPumpkin);
		    zombie.getPersistentData().putBoolean("zombierool:halloween_light", true);

		    int armorColor;
		    if (random.nextFloat() < 0.7F) {
		        armorColor = ARMOR_COLORS[0];
		    } else {
		        armorColor = ARMOR_COLORS[random.nextInt(ARMOR_COLORS.length)];
		    }

		    ItemStack chestplate = createDyedLeatherArmor(Items.LEATHER_CHESTPLATE, armorColor);
		    ItemStack leggings = createDyedLeatherArmor(Items.LEATHER_LEGGINGS, armorColor);
		    ItemStack boots = createDyedLeatherArmor(Items.LEATHER_BOOTS, armorColor);

		    zombie.setItemSlot(EquipmentSlot.CHEST, chestplate);
		    zombie.setItemSlot(EquipmentSlot.LEGS, leggings);
		    zombie.setItemSlot(EquipmentSlot.FEET, boots);

		    if (random.nextFloat() < 0.4F) {
		        ItemStack tool;
		        if (random.nextBoolean()) {
		            tool = new ItemStack(Items.WOODEN_HOE);
		        } else {
		            tool = new ItemStack(Items.WOODEN_SHOVEL);
		        }
		        zombie.setItemSlot(EquipmentSlot.MAINHAND, tool);
		    }

		    zombie.setDropChance(EquipmentSlot.HEAD, 0.0F);
		    zombie.setDropChance(EquipmentSlot.CHEST, 0.0F);
		    zombie.setDropChance(EquipmentSlot.LEGS, 0.0F);
		    zombie.setDropChance(EquipmentSlot.FEET, 0.0F);
		    zombie.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		});

        registerEntityModifier(me.cryo.zombierool.entity.CrawlerEntity.class, entity -> {
            me.cryo.zombierool.entity.CrawlerEntity crawler = (me.cryo.zombierool.entity.CrawlerEntity) entity;
            crawler.setHalloweenSkin(true);
        });
    }

    private static ItemStack createDyedLeatherArmor(net.minecraft.world.item.Item armorItem, int color) {
        ItemStack stack = new ItemStack(armorItem);
        CompoundTag displayTag = stack.getOrCreateTagElement("display");
        displayTag.putInt("color", color);
        return stack;
    }

    public static <T extends Entity> void registerEntityModifier(
            Class<T> entityClass, 
            Consumer<T> modifier) {
        ENTITY_MODIFIERS.put(entityClass, (Consumer<Entity>) modifier);
    }

    public static boolean isHalloweenPeriod() {
        ZRClientConfig.HalloweenMode mode = ZRClientConfig.getHalloweenMode();
        if (mode == ZRClientConfig.HalloweenMode.FORCE_OFF) {
            return false;
        }
        if (mode == ZRClientConfig.HalloweenMode.FORCE_ON) {
            return true;
        }
        return isNaturalHalloweenPeriod();
    }

    public static boolean isNaturalHalloweenPeriod() {
        LocalDate today = LocalDate.now();
        Month currentMonth = today.getMonth();
        int currentDay = today.getDayOfMonth();

        if (currentMonth == Month.OCTOBER && currentDay >= HALLOWEEN_START_DAY_OCTOBER) {
            return true;
        }
        if (currentMonth == Month.NOVEMBER && currentDay <= HALLOWEEN_END_DAY_NOVEMBER) {
            return true;
        }

        return false;
    }

    public static void updateFromConfig() {
    }

    public static void setForceHalloweenMode(boolean forced) {
        ZRClientConfig.setHalloweenMode(
            forced ? ZRClientConfig.HalloweenMode.FORCE_ON : ZRClientConfig.HalloweenMode.AUTO
        );
    }

    public static boolean isHalloweenModeForced() {
        return ZRClientConfig.getHalloweenMode() == ZRClientConfig.HalloweenMode.FORCE_ON;
    }

    public static void applyHalloweenModifications(Entity entity) {
        if (!isHalloweenPeriod()) {
            return;
        }

        Class<? extends Entity> entityClass = entity.getClass();
        Consumer<Entity> modifier = ENTITY_MODIFIERS.get(entityClass);
        if (modifier != null) {
            modifier.accept(entity);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();

        if (level.isClientSide()) {
            return;
        }

        applyHalloweenModifications(entity);
    }

    public static String getHalloweenStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== Halloween Manager Status ===\n");
        status.append("Mode: ").append(ZRClientConfig.getHalloweenMode()).append("\n");
        status.append("Effective Status: ").append(isHalloweenPeriod() ? "ACTIVE" : "INACTIVE").append("\n");
        status.append("Natural Period: ").append(isNaturalHalloweenPeriod() ? "ACTIVE" : "INACTIVE").append("\n");
        status.append("Date Range: October ").append(HALLOWEEN_START_DAY_OCTOBER)
              .append(" - November ").append(HALLOWEEN_END_DAY_NOVEMBER).append("\n");
        status.append("Registered Entities: ").append(ENTITY_MODIFIERS.size()).append("\n");
        status.append("Current Date: ").append(LocalDate.now()).append("\n");
        return status.toString();
    }
}