package com.flatts.productivefrogs.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Bare-BlockEntity coverage for the Controller's milk funnel: the single-variant
 * FIFO buffer, the reject-until-empty rule, and the buffer-depth cap. No level
 * needed - the intake reads catalyst components off a per-variant milk bucket
 * ItemStack and never touches the world.
 */
class TerrariumControllerBufferTest {

    private static final ResourceLocation IRON =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
    private static final ResourceLocation COPPER =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");

    private static TerrariumControllerBlockEntity newController() {
        return new TerrariumControllerBlockEntity(BlockPos.ZERO,
            PFBlocks.TERRARIUM_CONTROLLER.get().defaultBlockState());
    }

    private static ItemStack milk(ResourceLocation variant) {
        return PFItems.slimeMilkBucket(variant);
    }

    @Test
    void emptyBufferAcceptsAnyVariant() {
        TerrariumControllerBlockEntity c = newController();
        assertTrue(c.canAccept(IRON));
        assertTrue(c.canAccept(COPPER));
        assertNull(c.tankVariant());
    }

    @Test
    void pushingABucketLocksTheVariant() {
        TerrariumControllerBlockEntity c = newController();
        assertTrue(c.pushChargeFromBucket(milk(IRON)));
        assertEquals(1, c.bufferedCharges());
        assertEquals(IRON, c.tankVariant());
    }

    @Test
    void rejectsADifferentVariantWhileHoldingMilk() {
        TerrariumControllerBlockEntity c = newController();
        assertTrue(c.pushChargeFromBucket(milk(IRON)));
        assertFalse(c.canAccept(COPPER), "reject-until-empty gates on variant");
        assertFalse(c.pushChargeFromBucket(milk(COPPER)));
        assertEquals(1, c.bufferedCharges());
    }

    @Test
    void rejectsBeyondTheBufferDepth() {
        TerrariumControllerBlockEntity c = newController();
        int depth = PFConfig.terrariumControllerBufferDepth();
        for (int i = 0; i < depth; i++) {
            assertTrue(c.pushChargeFromBucket(milk(IRON)), "accepts up to the buffer depth");
        }
        assertFalse(c.canAccept(IRON), "buffer full");
        assertFalse(c.pushChargeFromBucket(milk(IRON)));
        assertEquals(depth, c.bufferedCharges());
    }

    @Test
    void nbtRoundTripPreservesBufferAndVariant() {
        TerrariumControllerBlockEntity c = newController();
        c.pushChargeFromBucket(milk(IRON));
        c.pushChargeFromBucket(milk(IRON));
        CompoundTag tag = new CompoundTag();
        c.saveAdditional(tag, null);

        TerrariumControllerBlockEntity reloaded = newController();
        reloaded.loadAdditional(tag, null);
        assertEquals(2, reloaded.bufferedCharges());
        assertEquals(IRON, reloaded.tankVariant());
    }
}
