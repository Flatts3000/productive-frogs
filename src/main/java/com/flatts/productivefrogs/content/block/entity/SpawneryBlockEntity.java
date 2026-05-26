package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.SpawneryBlock;
import com.flatts.productivefrogs.content.menu.SpawneryMenu;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItemTags;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the {@link SpawneryBlock} - a furnace-style appliance that
 * turns glass bottles into bottled frogspawn ({@code FrogEggItem}), fueled by slime
 * balls and optionally primed to a species. Modelled on
 * {@link SlimeMilkerBlockEntity} plus the vanilla furnace burn loop.
 *
 * <p>Per completed cycle ({@link PFConfig#SPAWNERY_PRODUCTION_TICKS} ticks): consume
 * one glass bottle and one slime ball of burn, then write one Frog Egg to the output.
 * The primer slot decides the egg: empty / untagged -> a plain vanilla frogspawn
 * bottle (no {@code contained_category}); a {@code spawnery_primer/<species>}-tagged
 * item -> an egg stamped with that {@link Category} (the primer is consumed too).
 *
 * <p>{@code FrogEggItem} is {@code stacksTo(1)}, so the output holds exactly one egg
 * and the block stalls until it is removed - identical to the Milker's milk bucket.
 *
 * <p>Fuel is 1:1: a slime ball is consumed to ignite a burn lasting exactly one
 * cycle ({@code burnDuration} = production ticks), so the flame UI reads like a
 * furnace while the ratio stays one ball per bottle.
 *
 * <p>The placed block functions regardless of {@code spawnery.enabled} - that flag
 * gates only crafting / JEI / creative visibility (see {@code docs/spawnery.md}).
 */
public class SpawneryBlockEntity extends BlockEntity implements MenuProvider {

    public static final int BOTTLE_SLOT = SpawneryInventory.BOTTLE_SLOT;
    public static final int FUEL_SLOT = SpawneryInventory.FUEL_SLOT;
    public static final int PRIMER_SLOT = SpawneryInventory.PRIMER_SLOT;
    public static final int OUTPUT_SLOT = SpawneryInventory.OUTPUT_SLOT;
    public static final int SLOT_COUNT = SpawneryInventory.SLOT_COUNT;

    public static final int DATA_COOK_PROGRESS = 0;
    public static final int DATA_COOK_TOTAL = 1;
    public static final int DATA_BURN_TIME = 2;
    public static final int DATA_BURN_DURATION = 3;
    public static final int DATA_COUNT = 4;

    private int cookProgress = 0;
    private int burnTime = 0;
    private int burnDuration = 0;

    private final SpawneryInventory inventory = new SpawneryInventory(this::setChanged);

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_COOK_PROGRESS -> cookProgress;
                case DATA_COOK_TOTAL -> productionTicks();
                case DATA_BURN_TIME -> burnTime;
                case DATA_BURN_DURATION -> burnDuration;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_COOK_PROGRESS -> cookProgress = value;
                case DATA_BURN_TIME -> burnTime = value;
                case DATA_BURN_DURATION -> burnDuration = value;
                default -> {
                    // DATA_COOK_TOTAL is config-derived; ignore client writes.
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SpawneryBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SPAWNERY.get(), pos, state);
    }

    public SpawneryInventory getInventory() {
        return inventory;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public int getCookProgress() {
        return cookProgress;
    }

    /** Ticks per bottle, from config; clamped to at least 1 so the cook can finish. */
    private static int productionTicks() {
        return Math.max(1, PFConfig.SPAWNERY_PRODUCTION_TICKS.get());
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawneryBlockEntity be) {
        int total = productionTicks();
        be.burnDuration = total;

        ItemStack bottle = be.inventory.getStackInSlot(BOTTLE_SLOT);
        ItemStack output = be.inventory.getStackInSlot(OUTPUT_SLOT);
        Category cat = PFItemTags.primerCategory(be.inventory.getStackInSlot(PRIMER_SLOT));

        // FrogEgg is stacksTo(1): the output holds exactly one egg, so a new cycle
        // can run only while the output is empty.
        boolean canProduce = !bottle.isEmpty() && output.isEmpty();

        boolean changed = false;
        if (canProduce) {
            if (be.burnTime <= 0) {
                ItemStack fuel = be.inventory.getStackInSlot(FUEL_SLOT);
                if (!fuel.isEmpty() && fuel.is(Items.SLIME_BALL)) {
                    fuel.shrink(1);
                    be.burnTime = total;
                    changed = true;
                    PFDebug.log(PFDebug.Area.SPAWNERY, () -> String.format(
                        "spawnery @%s: ignite, slime ball consumed (burn=%d)", pos, total));
                }
            }
            if (be.burnTime > 0) {
                be.burnTime--;
                be.cookProgress++;
                changed = true;
                if (be.cookProgress >= total) {
                    be.complete(level, pos, cat);
                }
            } else if (be.cookProgress != 0) {
                // No fuel available: stall, don't lose progress visually beyond reset.
                be.cookProgress = 0;
                changed = true;
            }
        } else {
            // Stalled: no bottle, or the stacksTo(1) output still holds an egg.
            // Reset cook progress and let any committed burn tick down (the flame
            // burns out), mirroring vanilla furnace behaviour when its input is
            // removed mid-cook. Because burn and cook are locked 1:1 and the output
            // is written only by complete() (never a hopper/player insert), this
            // branch fires only pre-ignite (progress already 0) or when a player
            // pulls the bottle mid-cycle - never spuriously mid-cook.
            if (be.cookProgress != 0) {
                be.cookProgress = 0;
                changed = true;
            }
            if (be.burnTime > 0) {
                be.burnTime--;
                changed = true;
            }
        }

        if (changed) {
            be.setChanged();
        }
        setLit(level, pos, state, be.burnTime > 0);
    }

    private void complete(Level level, BlockPos pos, @Nullable Category cat) {
        // extractItem (not a raw shrink on the returned stack) so each consumption
        // fires onContentsChanged -> setChanged independently of the output write
        // below - no reliance on the ordering to persist the consumed inputs.
        inventory.extractItem(BOTTLE_SLOT, 1, false);
        if (cat != null) {
            inventory.extractItem(PRIMER_SLOT, 1, false);
        }
        inventory.setStackInSlot(OUTPUT_SLOT, makeEgg(cat));
        cookProgress = 0;
        burnTime = 0;
        level.playSound(null, pos, SoundEvents.FROG_LAY_SPAWN, SoundSource.BLOCKS,
            0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        PFDebug.log(PFDebug.Area.SPAWNERY, () -> String.format(
            "spawnery @%s: produced %s frogspawn bottle", pos, cat == null ? "vanilla" : cat.id()));
    }

    private static ItemStack makeEgg(@Nullable Category cat) {
        ItemStack egg = new ItemStack(PFItems.FROG_EGG.get());
        if (cat != null) {
            egg.set(PFDataComponents.CONTAINED_CATEGORY.get(), cat);
        }
        return egg;
    }

    /**
     * Toggle the {@link SpawneryBlock#LIT} blockstate and sync to clients. No-op
     * when already matching, so the per-tick common case never spams neighbour
     * updates.
     */
    private static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (!(state.getBlock() instanceof SpawneryBlock)) {
            return;
        }
        if (state.getValue(SpawneryBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(SpawneryBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // -------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("BurnTime", burnTime);
        CompoundTag invTag = new CompoundTag();
        inventory.serialize(invTag);
        tag.put("Inventory", invTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // burnDuration is recomputed from config each tick, so it isn't persisted.
        // Clamp both counters into [0, productionTicks()] so a tampered/migrated save
        // can't load a huge cookProgress (which would complete() on the first tick) or
        // an over-long burn. productionTicks() reads the live config value.
        int total = productionTicks();
        int loadedCook = tag.contains("CookProgress", Tag.TAG_INT) ? tag.getInt("CookProgress") : 0;
        cookProgress = Math.max(0, Math.min(loadedCook, total));
        int loadedBurn = tag.contains("BurnTime", Tag.TAG_INT) ? tag.getInt("BurnTime") : 0;
        burnTime = Math.max(0, Math.min(loadedBurn, total));
        if (tag.contains("Inventory", Tag.TAG_COMPOUND)) {
            inventory.deserialize(tag.getCompound("Inventory"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // -------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.spawnery");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SpawneryMenu(containerId, playerInv, this, dataAccess);
    }
}
