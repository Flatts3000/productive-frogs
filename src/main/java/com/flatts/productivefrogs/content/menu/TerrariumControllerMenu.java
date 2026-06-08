package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.TerrariumControllerBlockEntity;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 * Status menu for the {@link TerrariumControllerBlockEntity} - no item slots
 * (milk is hand-fed / piped); the screen reads formed-state, milk-buffer fill,
 * and the first structural problem off the synced {@link ContainerData}.
 */
public class TerrariumControllerMenu extends AbstractContainerMenu {

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;
    @Nullable
    private final TerrariumControllerBlockEntity be;

    public TerrariumControllerMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new SimpleContainerData(TerrariumControllerBlockEntity.DATA_COUNT));
    }

    public TerrariumControllerMenu(int containerId, Inventory playerInv,
            @Nullable TerrariumControllerBlockEntity be, ContainerData data) {
        super(PFMenuTypes.TERRARIUM_CONTROLLER.get(), containerId);
        this.be = be;
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;
        addDataSlots(dataAccess);
    }

    /** The milk variant currently buffered (read off the synced client BE), or null when empty. */
    @Nullable
    public ResourceLocation tankVariant() {
        return be == null ? null : be.tankVariant();
    }

    public boolean formed() {
        return dataAccess.get(TerrariumControllerBlockEntity.DATA_FORMED) != 0;
    }

    public int charges() {
        return dataAccess.get(TerrariumControllerBlockEntity.DATA_CHARGES);
    }

    public int bufferDepth() {
        return dataAccess.get(TerrariumControllerBlockEntity.DATA_BUFFER_DEPTH);
    }

    /** Index into {@link TerrariumControllerBlockEntity#PROBLEM_KEYS}, or -1. */
    public int problemIndex() {
        return dataAccess.get(TerrariumControllerBlockEntity.DATA_PROBLEM);
    }

    /** Sprinklers in the formed multiblock (0 when unformed). */
    public int sprinklerCount() {
        return dataAccess.get(TerrariumControllerBlockEntity.DATA_SPRINKLERS);
    }

    /** Incubators in the formed multiblock (0 when unformed). */
    public int incubatorCount() {
        return dataAccess.get(TerrariumControllerBlockEntity.DATA_INCUBATORS);
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.TERRARIUM_CONTROLLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY; // no slots
    }

    @Nullable
    private static TerrariumControllerBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof TerrariumControllerBlockEntity controller ? controller : null;
    }
}
