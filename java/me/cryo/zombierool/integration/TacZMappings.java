package me.cryo.zombierool.integration;

import java.util.*;
import java.util.function.Predicate;
import me.cryo.zombierool.core.system.WeaponSystem.Definition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Ordered pack alternatives; each variant owns its attachments and upgraded gun. */
public final class TacZMappings {
    private TacZMappings() {}

    public static List<Definition.Tacz> variants(Definition def) {
        if (def == null || def.tacz == null) return List.of();
        var result = new ArrayList<Definition.Tacz>();
        var seen = new HashSet<String>();
        add(result, seen, def.tacz);
        if (def.tacz.fallbacks != null)
            for (var mapping : def.tacz.fallbacks) add(result, seen, mapping);
        return result;
    }

    private static void add(List<Definition.Tacz> result, Set<String> seen, Definition.Tacz mapping) {
        if (mapping != null && mapping.gun_id != null && ResourceLocation.tryParse(mapping.gun_id) != null
                && !mapping.gun_id.isBlank() && seen.add(mapping.gun_id)) result.add(mapping);
    }

    public static Definition.Tacz choose(Definition def) {
        return choose(def, TacZIntegration::isTaczGunAvailable);
    }

    public static Definition.Tacz choose(Definition def, Predicate<String> available) {
        for (var mapping : variants(def)) if (available.test(mapping.gun_id)) return mapping;
        return null;
    }

    public static Definition.Tacz forGun(Definition def, String id) {
        for (var mapping : variants(def))
            if (mapping.gun_id.equals(id) || id.equals(mapping.pap_gun_id)) return mapping;
        return null;
    }

    public static Definition.Tacz forStack(Definition def, ItemStack stack) {
        return forGun(def, stack.hasTag() ? stack.getTag().getString("GunId") : "");
    }

    public static boolean matchesBase(Definition def, String id) {
        for (var mapping : variants(def)) if (mapping.gun_id.equals(id)) return true;
        return false;
    }

    public static Set<String> mappedIds(Definition def) {
        var ids = new HashSet<String>();
        for (var mapping : variants(def)) {
            ids.add(mapping.gun_id);
            if (mapping.pap_gun_id != null && !mapping.pap_gun_id.isBlank()
                    && ResourceLocation.tryParse(mapping.pap_gun_id) != null) ids.add(mapping.pap_gun_id);
        }
        return ids;
    }
}
