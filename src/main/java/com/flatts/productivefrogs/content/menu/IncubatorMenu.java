package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.IncubatorBlockEntity;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Status menu for the {@link IncubatorBlockEntity} - no item slots (seeding is a
 * right-click with a Frog Egg, breeding feeds it directly); the screen reads
 * growth progress + state off the synced {@link ContainerData}.
 */
public class IncubatorMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    public IncubatorMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new SimpleContainerData(IncubatorBlockEntity.DATA_COUNT));
    }

    public IncubatorMenu(int containerId, Inventory playerInv, @Nullable IncubatorBlockEntity be, ContainerData data) {
        super(PFMenuTypes.INCUBATOR.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;
        addDataSlots(dataAccess);
    }

    public int growthRemaining() {
        return dataAccess.get(IncubatorBlockEntity.DATA_GROWTH_REMAINING);
    }

    public int growthTotal() {
        return dataAccess.get(IncubatorBlockEntity.DATA_GROWTH_TOTAL);
    }

    /** 0 = empty, 1 = growing, 2 = waiting at the frog cap. */
    public int state() {
        return dataAccess.get(IncubatorBlockEntity.DATA_STATE);
    }

    /** The kind currently incubating (species, Midas, or a predator), or null when empty. */
    @Nullable
    public com.flatts.productivefrogs.data.FrogKind incubatingKind() {
        return com.flatts.productivefrogs.data.FrogKind.bySyncIndex(
            dataAccess.get(IncubatorBlockEntity.DATA_KIND));
    }

    /** The incubating kind's fallback category (legacy readout), or null when empty. */
    @Nullable
    public Category incubatingCategory() {
        com.flatts.productivefrogs.data.FrogKind kind = incubatingKind();
        return kind == null ? null : kind.fallbackCategory();
    }

    /** Live frogs in this Incubator's cavity. */
    public int frogCount() {
        return dataAccess.get(IncubatorBlockEntity.DATA_FROGS);
    }

    /** Configured frog cap. */
    public int frogCap() {
        return dataAccess.get(IncubatorBlockEntity.DATA_FROG_CAP);
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.INCUBATOR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // no slots
    }

    @Nullable
    private static IncubatorBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof IncubatorBlockEntity inc ? inc : null;
    }
}
