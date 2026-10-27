package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.system.WeaponImplementations.MeleeWeaponItem;
import me.cryo.zombierool.item.ScarletWeaponItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The same timing contract drives server contacts and both client camera views. */
public final class MeleeMoves {
    public record Move(int duration, int contact, int charge, double reach, double cone, int targets, float secondary) {}
    public static String kind(ItemStack stack) {
        if (stack.getItem() instanceof ScarletWeaponItem s) return s.kind();
        return stack.getItem() instanceof me.cryo.zombierool.item.EnergySwordItem ? "energy" : "sword";
    }
    public static boolean twoHanded(ItemStack stack) { return kind(stack).equals("greatsword") || kind(stack).equals("halberd"); }
    public static boolean quick(ItemStack stack) { return kind(stack).equals("knife") || kind(stack).equals("dagger"); }
    public static Move move(ItemStack stack, Player player, boolean heavy) {
        var weapon=(MeleeWeaponItem)stack.getItem();
        String kind=kind(stack);
        int base=Math.max(6,Math.min(20,Math.round(weapon.getFireRate(stack,player)*.85f)));
        int charge=quick(stack)?9:twoHanded(stack)?16:kind.equals("axe")?14:12;
        int duration=heavy?Math.max(12,base+5):base;
        int contact=heavy?3:Math.max(2,Math.min(5,Math.round(duration*.28f)));
        double reach=Math.max(1.5,Math.min(4.5,weapon.getDefinition().stats.range+.3));
        double cone=quick(stack)?.91:kind.equals("axe")?.8:twoHanded(stack)?.62:.73;
        int targets=quick(stack)?1:kind.equals("axe")?2:twoHanded(stack)?5:3;
        return new Move(duration,contact,charge,reach,heavy?cone-.12:cone,heavy?Math.min(7,targets+1):targets,twoHanded(stack)?.6f:.45f);
    }
    private MeleeMoves() {}
}
