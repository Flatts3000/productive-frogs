package com.flatts.productivefrogs.compat;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gates each mixin on the mod it patches actually being present.
 *
 * <p>Productive Frogs has no hard dependency on Iron Furnaces and must load
 * fine without it, so the Iron Furnaces mixin cannot be applied
 * unconditionally: Mixin would fail to find the target class and take the
 * game down with it. The loading mod list is the only thing available this
 * early - {@code ModList} is not populated yet - and it answers the only
 * question that matters here.
 */
public final class PFMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("productivefrogs");

    private static final String IRON_FURNACES_MIXIN = "BlockIronFurnaceTileBaseMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(IRON_FURNACES_MIXIN)) {
            return isLoaded("ironfurnaces");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /**
     * Logged on purpose. The Iron Furnaces injector is non-required, so a future
     * version of that mod reshaping {@code split} would make the patch silently
     * stop applying; this line is how anyone reading a log can tell whether the
     * furnace they are looking at is patched.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(IRON_FURNACES_MIXIN)) {
            LOGGER.info("Applied the data-component fix to Iron Furnaces factory auto-split ({})", targetClassName);
        }
    }

    private static boolean isLoaded(String modId) {
        return FMLLoader.getLoadingModList() != null
            && FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }
}
