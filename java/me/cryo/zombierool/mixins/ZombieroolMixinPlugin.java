package me.cryo.zombierool.mixins;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.util.List;
import java.util.Set;

public class ZombieroolMixinPlugin implements IMixinConfigPlugin {

    private boolean hasTacz = false;

    @Override
    public void onLoad(String mixinPackage) {
        // MÉTHODE BARBARE ANTI-DEADLOCK :
        // On contourne totalement le système de Forge et de Java.
        // Au lieu de demander à Java si TacZ est chargé, on scanne physiquement 
        // le dossier "mods" sur le disque dur. 
        // Résultat : Zéro interaction avec Kotlin, zéro chance de freeze.
        try {
            File modsDir = new File("mods");
            if (modsDir.exists() && modsDir.isDirectory()) {
                File[] files = modsDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.getName().toLowerCase().contains("tacz")) {
                            hasTacz = true;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            hasTacz = false;
        }
    }

    @Override
    public String getRefMapperConfig() { 
        return null; 
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("TacZ")) {
            return hasTacz;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { 
        return null; 
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}