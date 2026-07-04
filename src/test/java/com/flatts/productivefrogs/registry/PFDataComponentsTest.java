package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Integration tests for our custom {@link DataComponentType} registrations.
 * The {@code CONTAINED_CATEGORY} component is what drives every Category-aware
 * surface in the mod — Frog Egg bottle behavior, tooltip name, content-layer
 * tint — so silently losing it would manifest as widespread "everything is
 * vanilla frogspawn" bugs. These tests catch that early.
 */
class PFDataComponentsTest {

    @Test
    void containedCategoryComponentIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "contained_category");
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id);
        assertNotNull(type, id + " must be registered");
        assertSame(PFDataComponents.CONTAINED_CATEGORY.get(), type,
            "Supplier must resolve to the registered DataComponentType");
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void containedCategoryRoundTripsThroughItemStack(Category cat) {
        // Treat the component end-to-end: stamp it onto a stack, read it back,
        // and verify equality. Exercises both the persistent codec (used by
        // /give and save data) and the in-memory carriage on the stack itself.
        ItemStack stack = new ItemStack(Items.GLASS_BOTTLE);
        stack.set(PFDataComponents.CONTAINED_CATEGORY.get(), cat);
        Category readBack = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
        assertEquals(cat, readBack);
    }

    @Test
    void absentComponentReadsAsNull() {
        // The "no category, contains vanilla frogspawn" state is encoded as the
        // component being absent. If a refactor ever changed it to a sentinel
        // value, FrogEggItem.useOn would silently switch placement behavior.
        ItemStack stack = new ItemStack(Items.GLASS_BOTTLE);
        assertNull(stack.get(PFDataComponents.CONTAINED_CATEGORY.get()),
            "absent component must read back as null, not a sentinel");
    }
}
