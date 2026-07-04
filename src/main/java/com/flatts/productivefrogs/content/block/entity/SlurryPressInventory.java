package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.EnderNetItem;
import com.flatts.productivefrogs.content.item.EntityNetItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Slot model for the Slurry Press (#281, predation Phase 3), mirroring the
 * {@link SlimeChurnInventory} four-slot shape: two inputs on the left (a
 * filled Ender Net above, empty buckets below), two outputs on the right (the
 * Mob Slurry bucket above, the emptied Ender Net handed back below).
 *
 * <p>The net slot accepts only a FILLED {@link EnderNetItem} whose captured
 * mob passes {@link SlurryPressBlockEntity#isPressable} - the boss rejection
 * ({@code c:bosses}) happens here at insert time as well as at process time,
 * so a hopper can't even feed a boss net in.
 */
public class SlurryPressInventory extends ItemStackHandler {

    public static final int NET_SLOT = 0;
    public static final int BUCKET_SLOT = 1;
    public static final int SLURRY_OUTPUT_SLOT = 2;
    public static final int NET_OUTPUT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    private final Runnable onChanged;
    private final IItemHandler inputView;
    private final IItemHandler outputView;

    public SlurryPressInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new MultiSlotItemView(this, new int[] {NET_SLOT, BUCKET_SLOT}, true, false);
        this.outputView = new MultiSlotItemView(this, new int[] {SLURRY_OUTPUT_SLOT, NET_OUTPUT_SLOT}, false, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case NET_SLOT -> stack.getItem() instanceof EnderNetItem
                && EntityNetItem.isFilled(stack)
                && SlurryPressBlockEntity.isPressable(stack);
            case BUCKET_SLOT -> stack.is(Items.BUCKET);
            default -> false; // output slots reject inserts
        };
    }

    @Override
    public int getSlotLimit(int slot) {
        // Nets are singletons; keep the net slot to one so a stack of filled
        // nets can't wedge (only the buckets slot benefits from stacking).
        return slot == NET_SLOT || slot == NET_OUTPUT_SLOT ? 1 : super.getSlotLimit(slot);
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    public IItemHandler inputView() {
        return inputView;
    }

    public IItemHandler outputView() {
        return outputView;
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} input view: insert-only over both input slots. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> inputResource() {
        return new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(
            this, new int[] {NET_SLOT, BUCKET_SLOT}, true, false);
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} output view: extract-only over both output slots. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> outputResource() {
        return new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(
            this, new int[] {SLURRY_OUTPUT_SLOT, NET_OUTPUT_SLOT}, false, true);
    }

    public void serialize(ValueOutput output) {
        super.serialize(output);
    }

    public void deserialize(ValueInput input) {
        super.deserialize(input);
    }
}
