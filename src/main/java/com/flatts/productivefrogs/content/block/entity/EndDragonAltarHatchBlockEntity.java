package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.entity.PlinthFrog;
import com.flatts.productivefrogs.content.multiblock.DragonAltarValidator;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * End Dragon Altar Hatch block entity (#249). Same role as the Terrarium Hatch -
 * an output inventory you open like a chest and pipe items out of - but a distinct
 * block (different recipe) and decoupled from the Terrarium: the dragon-altar
 * summon deposits the dragon's drops (one dragon's breath, the config egg) here,
 * and XP spawns as orbs at the hatch. Plain 27-slot chest GUI ({@link ChestMenu})
 * so no custom screen is needed; pipes pull via an {@link InvWrapper} item handler.
 */
public class EndDragonAltarHatchBlockEntity extends BaseContainerBlockEntity {

    public static final int SIZE = 27;

    /** How often (ticks) the altar reconciles its plinth frog against structure validity. */
    private static final int RECONCILE_INTERVAL = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);
    private int tickCounter;

    public EndDragonAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(), pos, state);
    }

    /** Pipe/hopper view over the chest inventory. */
    public InvWrapper itemHandler() {
        return itemHandler;
    }

    /**
     * Deposit a dragon-altar drop, spilling any overflow as a returned leftover
     * (the summon decides what to do with it). Used by the summon state machine.
     */
    public ItemStack deposit(ItemStack stack) {
        return ItemHandlerHelper.insertItem(itemHandler, stack, false);
    }

    /** The block the plinth frog stands in: on top of the central bedrock plinth (two below the hatch). */
    public static BlockPos plinthFrogPos(BlockPos hatchPos) {
        return hatchPos.offset(0, -2, 0);
    }

    /**
     * Reconcile the plinth display frog (#249) against structure validity: when the
     * altar is valid keep exactly one {@link PlinthFrog} pinned on the plinth; when
     * it is broken, remove it. Throttled - it does not need per-tick precision, and
     * re-pinning each pass also corrects any drift.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EndDragonAltarHatchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (++be.tickCounter < RECONCILE_INTERVAL) {
            return;
        }
        be.tickCounter = 0;
        boolean valid = DragonAltarValidator.validate(server, pos).valid();
        BlockPos plinth = plinthFrogPos(pos);
        List<PlinthFrog> frogs = server.getEntitiesOfClass(PlinthFrog.class, new AABB(plinth).inflate(0.5));
        if (!valid) {
            for (PlinthFrog f : frogs) {
                f.discard();
            }
            return;
        }
        double cx = plinth.getX() + 0.5;
        double cy = plinth.getY();
        double cz = plinth.getZ() + 0.5;
        if (frogs.isEmpty()) {
            PlinthFrog frog = PlinthFrog.type().create(server);
            if (frog != null) {
                frog.moveTo(cx, cy, cz, 180.0F, 0.0F);
                frog.setYBodyRot(180.0F);
                frog.setYHeadRot(180.0F);
                server.addFreshEntity(frog);
            }
        } else {
            // Keep the first; re-pin it (correct any drift) and cull duplicates.
            PlinthFrog frog = frogs.get(0);
            frog.moveTo(cx, cy, cz, frog.getYRot(), 0.0F);
            frog.setDeltaMovement(Vec3.ZERO);
            for (int i = 1; i < frogs.size(); i++) {
                frogs.get(i).discard();
            }
        }
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        this.items = list;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.end_dragon_altar_hatch");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }
}
