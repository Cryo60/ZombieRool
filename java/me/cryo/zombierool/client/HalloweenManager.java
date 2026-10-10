package me.cryo.zombierool.client;
import me.cryo.zombierool.configuration.ZRClientConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import java.time.LocalDate;
/** Cosmetic equipment is returned to render layers; entity inventories are never changed. */
public final class HalloweenManager {
    private static final java.util.Map<LivingEntity,ItemStack[]> costumes=new java.util.WeakHashMap<>();
    public static boolean isHalloweenPeriod(){return switch(ZRClientConfig.getHalloweenMode()){case FORCE_ON->true;case FORCE_OFF->false;default->isNaturalHalloweenPeriod();};}
    public static boolean isNaturalHalloweenPeriod(){LocalDate d=LocalDate.now();return d.getMonthValue()==10&&d.getDayOfMonth()>=20||d.getMonthValue()==11&&d.getDayOfMonth()<=5;}
    public static void updateFromConfig(){BloodSplatters.clear();}
    public static void setForceHalloweenMode(boolean forced){ZRClientConfig.setHalloweenMode(forced?ZRClientConfig.HalloweenMode.FORCE_ON:ZRClientConfig.HalloweenMode.AUTO);}
    public static boolean isHalloweenModeForced(){return ZRClientConfig.getHalloweenMode()==ZRClientConfig.HalloweenMode.FORCE_ON;}
    public static String getHalloweenStatus(){return "Halloween: "+ZRClientConfig.getHalloweenMode()+" / "+isHalloweenPeriod();}
    public static ItemStack visualEquipment(LivingEntity entity,EquipmentSlot slot){
        if(!(entity instanceof me.cryo.zombierool.entity.ZombieEntity)||!isHalloweenPeriod()||slot.getType()!=EquipmentSlot.Type.ARMOR)return entity.getItemBySlot(slot);
        ItemStack[] costume=costumes.computeIfAbsent(entity,e->{
            int[] colors={0xFF8C00,0x800080,0xFF4500,0x111111};int color=colors[Math.floorMod(e.getUUID().hashCode(),colors.length)];
            ItemStack head=new ItemStack(me.cryo.zombierool.init.ZombieroolModBlocks.BLACK_PUMPKIN.get());head.getOrCreateTag().putBoolean("zombierool:emissive_pumpkin",true);
            return new ItemStack[]{armor(Items.LEATHER_BOOTS,color),armor(Items.LEATHER_LEGGINGS,color),armor(Items.LEATHER_CHESTPLATE,color),head};
        });
        return costume[slot.getIndex()];
    }
    private static ItemStack armor(Item item,int color){ItemStack s=new ItemStack(item);s.getOrCreateTagElement("display").putInt("color",color);return s;}
}
