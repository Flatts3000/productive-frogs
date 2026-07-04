package com.flatts.productivefrogs.content.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.TestRegistryUtil;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FuelValues;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Pins the Froglight furnace-fuel contract: only the fuel-resource variants burn,
 * each for exactly as long as the vanilla item it is themed on - {@code coal} like
 * a coal item (1600t), {@code blaze} like a blaze rod (2400t), {@code lava} like a
 * lava bucket (20000t, #231). Every other variant - including the cross-mod
 * {@code blazing} Powah crystal - and an unstamped Froglight is inert (0t = not fuel).
 *
 * <p>Asserts both the item-level hook ({@link ConfigurableFroglightItem#getBurnTime})
 * and the {@code ItemStack#getBurnTime} integration path NeoForge's furnace uses,
 * so a regression that drops the per-variant gate (making every Froglight fuel) or
 * changes a burn value is caught.
 */
class ConfigurableFroglightItemFuelTest {

    @BeforeAll
    static void bindComponents() {
        TestRegistryUtil.bindComponents();
    }

    private static ItemStack froglight(String variantName) {
        ItemStack stack = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        if (variantName != null) {
            stack.set(PFDataComponents.SLIME_VARIANT.get(),
                Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantName));
        }
        return stack;
    }

    private static FuelValues emptyFuel() {
        return new FuelValues.Builder(
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
            FeatureFlagSet.of()).build();
    }

    @ParameterizedTest
    @CsvSource({"coal,1600", "blaze,2400", "lava,20000"})
    void fuelVariantsBurnForTheirThemedTime(String variantName, int expectedTicks) {
        ItemStack stack = froglight(variantName);
        assertEquals(expectedTicks,
            PFItems.CONFIGURABLE_FROGLIGHT.get().getBurnTime(stack, null, emptyFuel()),
            variantName + " Froglight item hook should report its themed burn time");
        assertEquals(expectedTicks, stack.getBurnTime(null, emptyFuel()),
            variantName + " Froglight should be furnace fuel worth its themed burn time");
    }

    @ParameterizedTest
    @ValueSource(strings = {"iron", "copper", "gold", "diamond", "emerald", "redstone", "lapis", "blazing"})
    void otherVariantFroglightsAreNotFuel(String variantName) {
        ItemStack stack = froglight(variantName);
        assertEquals(0, PFItems.CONFIGURABLE_FROGLIGHT.get().getBurnTime(stack, null, emptyFuel()),
            variantName + " Froglight must not be furnace fuel");
        assertEquals(0, stack.getBurnTime(null, emptyFuel()),
            variantName + " Froglight must not be furnace fuel (stack path)");
    }

    @Test
    void unstampedFroglightIsNotFuel() {
        ItemStack stack = froglight(null);
        assertEquals(0, PFItems.CONFIGURABLE_FROGLIGHT.get().getBurnTime(stack, null, emptyFuel()),
            "An unstamped Froglight (no variant) must not be furnace fuel");
        assertEquals(0, stack.getBurnTime(null, emptyFuel()),
            "An unstamped Froglight (no variant) must not be furnace fuel (stack path)");
    }
}
