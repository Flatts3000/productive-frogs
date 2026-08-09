package com.flatts.productivefrogs.compat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gates each mixin on the mod it patches actually being present.
 *
 * <p>Productive Frogs has no hard dependency on Iron Furnaces and must load
 * fine without it, so the Iron Furnaces mixin cannot be applied
 * unconditionally: Mixin would fail to find the target class and take the
 * game down with it. The loading mod list is the only thing available this
 * early - {@code ModList} is not populated yet - and it answers the only
 * question that matters here.
 *
 * <p><b>Unknown mixins are refused, not allowed.</b> The gate is an explicit
 * map from mixin class to the mod it needs. Anything not in that map is
 * declined and logged, so adding a compat mixin and forgetting to register it
 * here costs a missing patch and a warning line, never a crash for every
 * player who lacks the target mod. Failing open in the one class whose job is
 * failing closed is not a trade worth making; a mixin that patches vanilla and
 * genuinely needs no gate belongs in the map with a {@code null} mod id, which
 * says so out loud.
 */
public final class PFMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("productivefrogs");

    /**
     * Mixin simple class name to the mod id it requires, or {@code null} for a
     * mixin that needs no mod present. Every entry in
     * {@code productivefrogs.mixins.json} must appear here.
     */
    private static final Map<String, String> REQUIRED_MOD = Map.of(
        "BlockIronFurnaceTileBaseMixin", "ironfurnaces"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return shouldApplyMixin(mixinClassName, PFMixinPlugin::isLoaded);
    }

    /**
     * The gate itself, with the mod-presence lookup injected so it can be
     * exercised in both directions from a unit test. {@link FMLLoader} reports
     * nothing outside a real mod-loading environment, which would otherwise
     * make every assertion here pass for any implementation, including a
     * hardcoded {@code false} or a typo'd mod id.
     *
     * @param mixinClassName fully qualified name of the mixin being considered
     * @param modPresent     answers whether a mod id is loading
     */
    static boolean shouldApplyMixin(String mixinClassName, Predicate<String> modPresent) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (!REQUIRED_MOD.containsKey(simpleName)) {
            LOGGER.warn("Refusing to apply unregistered mixin {} - add it to PFMixinPlugin.REQUIRED_MOD "
                + "with the mod id it patches (or null if it needs none)", mixinClassName);
            return false;
        }
        String modId = REQUIRED_MOD.get(simpleName);
        return modId == null || modPresent.test(modId);
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
     * Logged on purpose. The Iron Furnaces injector is non-required and the
     * whole config is non-required, so a future version of that mod reshaping
     * {@code split} or renaming {@code FACTORY_INPUT} would make the patch
     * silently stop applying; this line is how anyone reading a log can tell
     * whether the furnace they are looking at is patched.
     */
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith("BlockIronFurnaceTileBaseMixin")) {
            LOGGER.info("Applied the data-component fix to Iron Furnaces factory auto-split ({})", targetClassName);
        }
    }

    private static boolean isLoaded(String modId) {
        return FMLLoader.getLoadingModList() != null
            && FMLLoader.getLoadingModList().getModFileById(modId) != null;
    }
}
