package com.flatts.productivefrogs.content.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.TestRegistryUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Direct conservation coverage for the R-3 forward capability adapter
 * ({@link RestrictedItemResourceHandler}) - the journaled
 * {@code ResourceHandler<ItemResource>} that backs every appliance's
 * {@code Capabilities.Item.BLOCK}, so hoppers/pipes can move items in and out.
 *
 * <p>The in-world hopper GameTests (ApplianceTests) exercise the happy path (an
 * item moves and the source drains). This pins the transactional boundaries where
 * a journaling bug would <b>dupe or lose</b> items: capacity / availability
 * clamping, the input-only / output-only direction gates, and - the crux of the
 * snapshot-before-mutate design - that an <b>aborted</b> transaction reverts the
 * backing exactly and a <b>committed</b> one persists it. A GameTest is blind to
 * these because they need explicit transaction control, not a hopper tick.
 */
@SuppressWarnings("removal") // ItemStackHandler is the backing every appliance inventory still uses.
class RestrictedItemResourceHandlerTest {

    @BeforeAll
    static void bindComponents() {
        TestRegistryUtil.bindComponents();
    }

    private static ItemStackHandler oneSlot() {
        return new ItemStackHandler(1);
    }

    /**
     * An iron-ingot stack of {@code count}. The bare-JUnit bootstrap binds EMPTY
     * default components (see {@link TestRegistryUtil}), so an item's default
     * {@code MAX_STACK_SIZE} (64) is absent and {@code getMaxStackSize()} would fall
     * back to 1 - collapsing every count. Stamp the component explicitly so stacking
     * behaves like it does in-game.
     */
    private static ItemStack ironStack(int count) {
        ItemStack s = new ItemStack(Items.IRON_INGOT, count);
        s.set(DataComponents.MAX_STACK_SIZE, 64);
        return s;
    }

    private static ItemResource iron() {
        return ItemResource.of(ironStack(1));
    }

    @Test
    void insertClampsToCapacityNoDupe() {
        ItemStackHandler root = oneSlot();
        RestrictedItemResourceHandler h = new RestrictedItemResourceHandler(root, new int[] {0}, true, true);
        int accepted;
        try (Transaction tx = Transaction.openRoot()) {
            accepted = h.insert(0, iron(), 100, tx); // request 100 into a max-64 slot
            tx.commit();
        }
        assertEquals(64, accepted, "insert must accept only what fits (64), not the requested 100");
        assertEquals(64, root.getStackInSlot(0).getCount(),
            "root slot must hold exactly the accepted 64 - the surplus is neither stored (dupe) nor reported as accepted (loss)");
    }

    @Test
    void outputOnlyViewRejectsInsert() {
        ItemStackHandler root = oneSlot();
        RestrictedItemResourceHandler out = new RestrictedItemResourceHandler(root, new int[] {0}, false, true);
        int accepted;
        try (Transaction tx = Transaction.openRoot()) {
            accepted = out.insert(0, iron(), 8, tx);
            tx.commit();
        }
        assertEquals(0, accepted, "an output-only view must reject inserts");
        assertTrue(root.getStackInSlot(0).isEmpty(), "a rejected insert must not mutate the backing slot");
        assertFalse(out.isValid(0, iron()), "output-only view isValid must be false");
    }

    @Test
    void inputOnlyViewRejectsExtract() {
        ItemStackHandler root = oneSlot();
        root.setStackInSlot(0, ironStack(10));
        RestrictedItemResourceHandler in = new RestrictedItemResourceHandler(root, new int[] {0}, true, false);
        int extracted;
        try (Transaction tx = Transaction.openRoot()) {
            extracted = in.extract(0, iron(), 10, tx);
            tx.commit();
        }
        assertEquals(0, extracted, "an input-only view must reject extractions");
        assertEquals(10, root.getStackInSlot(0).getCount(), "a rejected extract must not mutate the backing slot");
    }

    @Test
    void extractClampsToAvailableNoPhantom() {
        ItemStackHandler root = oneSlot();
        root.setStackInSlot(0, ironStack(10));
        RestrictedItemResourceHandler h = new RestrictedItemResourceHandler(root, new int[] {0}, true, true);
        int extracted;
        try (Transaction tx = Transaction.openRoot()) {
            extracted = h.extract(0, iron(), 100, tx); // request 100, only 10 present
            tx.commit();
        }
        assertEquals(10, extracted, "extract must yield only the available 10, not the requested 100 - no phantom items");
        assertTrue(root.getStackInSlot(0).isEmpty(), "the slot must be emptied after extracting all it held");
    }

    @Test
    void abortedInsertRevertsNoDupe() {
        ItemStackHandler root = oneSlot();
        RestrictedItemResourceHandler h = new RestrictedItemResourceHandler(root, new int[] {0}, true, true);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(32, h.insert(0, iron(), 32, tx), "insert should accept 32 within the transaction");
            // Snapshot-before-mutate: the write lands on the backing immediately so a
            // consumer can read its own in-progress write (read-your-writes).
            assertEquals(32, root.getStackInSlot(0).getCount(),
                "insert mutates the backing immediately within the open transaction");
            // No commit() -> the try-with-resources close aborts the transaction.
        }
        assertTrue(root.getStackInSlot(0).isEmpty(),
            "an aborted insert must revert the backing to empty - no ghost items left behind (dupe)");
    }

    @Test
    void committedInsertPersists() {
        ItemStackHandler root = oneSlot();
        RestrictedItemResourceHandler h = new RestrictedItemResourceHandler(root, new int[] {0}, true, true);
        try (Transaction tx = Transaction.openRoot()) {
            h.insert(0, iron(), 32, tx);
            tx.commit();
        }
        assertEquals(32, root.getStackInSlot(0).getCount(), "a committed insert must persist to the backing");
    }

    @Test
    void abortedExtractRevertsNoLoss() {
        ItemStackHandler root = oneSlot();
        root.setStackInSlot(0, ironStack(32));
        RestrictedItemResourceHandler h = new RestrictedItemResourceHandler(root, new int[] {0}, true, true);
        try (Transaction tx = Transaction.openRoot()) {
            assertEquals(32, h.extract(0, iron(), 32, tx), "extract should yield 32 within the transaction");
            assertTrue(root.getStackInSlot(0).isEmpty(),
                "extract mutates the backing immediately within the open transaction");
        }
        assertEquals(32, root.getStackInSlot(0).getCount(),
            "an aborted extract must restore the backing - the items are not lost");
    }
}
