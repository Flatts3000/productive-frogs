package com.flatts.productivefrogs;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Shared test utility that binds default data components to all registry
 * holders before tests create {@code ItemStack} or {@code FluidStack} objects.
 *
 * <p>In MC 26.1 the {@code ItemStack} and {@code FluidStack} constructors call
 * {@code Holder.Reference.components()}, which throws "Components not bound yet"
 * unless {@code bindComponents(DataComponentMap)} has been called on the holder.
 * The moddev JUnit bootstrap runs mod loading and registry freeze but does NOT
 * trigger the {@code DataComponentInitializers} apply phase. Call
 * {@code bindComponents()} in a {@code @BeforeAll} method in any test class
 * that constructs stacks.
 *
 * <p>The normal path is {@code DataComponentInitializers.build(context).forEach(apply)},
 * but that requires damage-type tags (e.g. {@code minecraft:is_fire}) which are
 * only populated after full datapack loading - not available in the bare JUnit
 * bootstrap. When that call throws, we fall back to binding any still-unbound
 * item and fluid holders with {@code DataComponentMap.EMPTY}. Tests that only
 * read/write custom components (which are set explicitly on the stack) are
 * unaffected by the absent vanilla defaults.
 */
public final class TestRegistryUtil {
    private static boolean done = false;

    /**
     * Ensures all item and fluid holders have their components bound so that
     * {@code ItemStack} and {@code FluidStack} construction succeeds. Safe to
     * call from multiple test classes - subsequent calls are no-ops.
     */
    public static synchronized void bindComponents() {
        if (done) {
            return;
        }
        done = true;
        RegistryAccess context = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        try {
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(context)
                .forEach(DataComponentInitializers.PendingComponents::apply);
        } catch (IllegalStateException e) {
            // Damage-type tags (e.g. minecraft:damage_type/is_fire) are not loaded in
            // the JUnit bootstrap. build() fails partway through, leaving some holders
            // unbound. Finish the job by binding every remaining unbound holder with
            // an empty component map so ItemStack / FluidStack construction succeeds.
            bindUnbound(BuiltInRegistries.ITEM);
            bindUnbound(BuiltInRegistries.FLUID);
        }
    }

    private static <T> void bindUnbound(HolderLookup<T> lookup) {
        lookup.listElements().forEach(holder -> {
            try {
                holder.bindComponents(DataComponentMap.EMPTY);
            } catch (IllegalStateException ignored) {
                // Already bound by the partial build() run above.
            }
        });
    }

    private TestRegistryUtil() {}
}
