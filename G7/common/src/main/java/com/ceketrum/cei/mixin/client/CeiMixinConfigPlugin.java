package com.ceketrum.cei.mixin.client;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Dynamic Mixin Config Plugin that detects the Minecraft version at launch.
 * It activates 26.1 mixins on 26.1 (1.21-1.21.11) and 26.2 mixins on 26.2.
 */
public class CeiMixinConfigPlugin implements IMixinConfigPlugin {
    private static final boolean IS_26_2 = checkClassExists("net.minecraft.client.input.MouseButtonEvent");

    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className, false, CeiMixinConfigPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("261")) {
            return !IS_26_2;
        }
        if (mixinClassName.endsWith("262")) {
            return IS_26_2;
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
