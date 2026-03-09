package me.cryo.zombierool.mixins;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ZombieroolMixinPlugin implements IMixinConfigPlugin {
    private boolean hasTacz;

    @Override
    public void onLoad(String mixinPackage) {
        try {
            Class.forName("com.tacz.guns.GunMod", false, this.getClass().getClassLoader());
            hasTacz = true;
        } catch (ClassNotFoundException e) {
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