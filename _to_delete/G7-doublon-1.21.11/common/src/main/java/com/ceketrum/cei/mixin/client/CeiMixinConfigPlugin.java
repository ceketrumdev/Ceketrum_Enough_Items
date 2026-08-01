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
    // ATTENTION : ne jamais sonder net.minecraft.client.input.MouseButtonEvent.
    // Cette classe existe deja en 1.21.11, donc la sonde renvoyait toujours true :
    // tous les mixins ...261 etaient desactives et tous les ...262 actives, alors
    // que leurs cibles (GuiGraphicsExtractor / extractRenderState) n'existent pas.
    // Resultat : plus aucun mixin CEI ne s'appliquait sur G7.
    // On sonde donc la classe reellement propre a 26.2.
    private static final boolean IS_26_2 = checkClassExists("net.minecraft.client.gui.GuiGraphicsExtractor");

    /**
     * ATTENTION : ne JAMAIS utiliser Class.forName ici, meme avec initialize=false.
     * La classe serait tout de meme chargee, pendant la phase de preparation des
     * configs mixin -- donc avant que les autres mods aient pu appliquer leurs
     * propres mixins dessus. fabric-rendering-v1 mixine GuiGraphicsExtractor et
     * plantait avec MixinTargetAlreadyLoadedException a cause de ce sondage.
     *
     * On teste donc la presence de la RESSOURCE, ce qui n'entraine aucun
     * chargement de classe.
     */
    private static boolean checkClassExists(String className) {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader loader = CeiMixinConfigPlugin.class.getClassLoader();
        if (loader != null && loader.getResource(resource) != null) {
            return true;
        }
        return ClassLoader.getSystemResource(resource) != null;
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
