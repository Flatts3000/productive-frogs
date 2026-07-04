package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFItemTags;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Four-slot inventory backing the Spawnery BlockEntity, on {@link ItemStackHandler}
 * so the BE can expose it as the {@code Capabilities.ItemHandler.BLOCK} capability
 * for hoppers / pipe mods (1.21.1 API).
 *
 * <p>Slot semantics:
 * <ul>
 *   <li>{@link #BOTTLE_SLOT} accepts only {@code minecraft:glass_bottle} (the
 *       container, consumed and returned as the filled Frog Egg).</li>
 *   <li>{@link #FUEL_SLOT} accepts only {@code minecraft:slime_ball} (burn fuel,
 *       1 ball = 1 bottle).</li>
 *   <li>{@link #PRIMER_SLOT} accepts any item in a {@code spawnery_primer/<species>}
 *       tag (see {@link PFItemTags}); selects the output species.</li>
 *   <li>{@link #OUTPUT_SLOT} rejects inserts; the production loop writes via
 *       {@link #setStackInSlot(int, ItemStack)} which bypasses the validity check.</li>
 * </ul>
 *
 * <p>Side-restricted views via {@link #inputView()} / {@link #outputView()} back the
 * side-aware capability provider in {@code PFModBusEvents}: the bottom face returns
 * the extract-only OUTPUT view; every other face returns the insert-only view over
 * the three input slots, which routes each pushed item to the slot whose
 * {@link #isItemValid} accepts it.
 */
public class SpawneryInventory extends ItemStackHandler {

    public static final int BOTTLE_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int PRIMER_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    private final Runnable onChanged;
    private final IItemHandler inputView;
    private final IItemHandler outputView;

    public SpawneryInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new MultiSlotItemView(this, new int[] {BOTTLE_SLOT, FUEL_SLOT, PRIMER_SLOT}, true, false);
        this.outputView = new MultiSlotItemView(this, new int[] {OUTPUT_SLOT}, false, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case BOTTLE_SLOT -> stack.is(Items.GLASS_BOTTLE);
            case FUEL_SLOT -> stack.is(Items.SLIME_BALL);
            case PRIMER_SLOT -> isValidPrimer(stack);
            default -> false; // OUTPUT_SLOT rejects inserts
        };
    }

    /**
     * A valid Spawnery primer: a slime ball (primes plain vanilla frogspawn) or any
     * item in a {@code spawnery_primer/<species>} tag (primes that species). The
     * Spawnery requires a primer - there is no empty-primer production. The slime
     * ball doubles as the fuel, so a vanilla egg costs one in the fuel slot and one
     * here.
     */
    public static boolean isValidPrimer(ItemStack stack) {
        return stack.is(Items.SLIME_BALL) || PFItemTags.primerCategory(stack) != null;
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

    public void serialize(CompoundTag tag) {
        tag.merge(serializeNBT(RegistryAccess.EMPTY));
    }

    public void deserialize(CompoundTag tag) {
        deserializeNBT(RegistryAccess.EMPTY, tag);
    }

}
