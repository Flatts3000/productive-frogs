package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.AltarDisplayFrog;
import com.flatts.productivefrogs.data.FrogKind;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Shared brain of every boss-altar Hatch (#247 Wither, #249 Dragon, #279 Warden,
 * #280 Elder Guardian) - the refactor that stops the fourth copy-paste of the
 * summon machinery (the FrogKind precedent: unify before cloning). A 27-slot
 * {@link BaseContainerBlockEntity} chest you open or pipe from, plus:
 *
 * <ul>
 *   <li>the {@link AltarApexDock} (#281 Phase 4) - the installed Apex frog gate
 *       + the Liquid Experience bank; breaking the Hatch releases the frog;</li>
 *   <li>the summon state machine ({@link #serverTick}): validate -> reconcile the
 *       display frog -> when armed + fueled, run a timed summon -> lash, spend the
 *       fuel, bank the XP, and {@link #payOut} the boss's raw drops.</li>
 * </ul>
 *
 * <p>Subclasses supply the altar-specific pieces as hooks: the validator, the
 * fuel receptacles, the display frog, the sounds, and the payout. The
 * {@code summonTicks} on/off signal is synced start/end for the client replica
 * animation ({@link #clientSummonStartGameTime} is the renderer's local clock).
 */
public abstract class BossAltarHatchBlockEntity extends BaseContainerBlockEntity {

    public static final int SIZE = 27;

    /** How often (ticks) an idle altar revalidates + reconciles its display frog. */
    private static final int RECONCILE_INTERVAL = 20;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);
    private final AltarApexDock dock;
    private int tickCounter;
    /** 0 = idle; > 0 = a summon is in progress (ticks remaining). Synced (start/end) as the on/off signal. */
    private int summonTicks;
    /** Client-only render state: game time when the renderer first saw this summon, for local growth animation. */
    public long clientSummonStartGameTime = -1L;

    /**
     * The altar's resolved build orientation, cached from the last successful
     * validation and synced to the client. Semantics per altar: the wither's
     * ritual-wall direction, or the direction from a wall-mounted Hatch toward
     * the altar interior (#279/#280 - the Hatch sits in the wall so pipes reach
     * its outer face). SOUTH is every validator's canonical authoring frame, so
     * it doubles as the identity default.
     */
    private net.minecraft.core.Direction orientation = net.minecraft.core.Direction.SOUTH;
    /** Client mirror of the dock's installed state (rides the update tag; the frog NBT itself never syncs). */
    private boolean clientApexInstalled;

    protected BossAltarHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, FrogKind.Apex apex) {
        super(type, pos, state);
        this.dock = new AltarApexDock(apex, this::setChanged);
    }

    // ---- the altar-specific hooks ----------------------------------------

    /** Validate the surrounding structure (subclasses may cache extras, e.g. the resolved ritual direction). */
    protected abstract boolean validateStructure(ServerLevel server, BlockPos pos);

    /** Whether every fuel receptacle is loaded (the per-summon cost is present). */
    protected abstract boolean fuelReady(ServerLevel server, BlockPos pos);

    /** Spend the per-summon fuel (called once, on successful completion). */
    protected abstract void spendFuel(ServerLevel server, BlockPos pos);

    /** Pin/remove the display frog to match {@code show} (valid + armed). */
    protected abstract void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show);

    /** Tongue-lash the display frog (the boss-eat, on completion). */
    protected abstract void lashDisplay(ServerLevel server, BlockPos pos);

    /** Discard the display frog (the Hatch is being removed). */
    protected abstract void discardDisplay(ServerLevel server, BlockPos pos);

    /** Summon length in ticks (config-backed). */
    protected abstract int summonDuration();

    /** XP points banked as Liquid Experience per completed summon (config-backed). */
    protected abstract int xpReward();

    /** The summon just started (play the start sound / level event). */
    protected abstract void onSummonStart(ServerLevel server, BlockPos pos);

    /** A summon tick elapsed (optional mid-summon effects; dragon roars here). */
    protected void onSummonProgress(ServerLevel server, BlockPos pos, int elapsed, int total) {
    }

    /** Deposit the boss's raw drops + play the completion sound. XP is already banked. */
    protected abstract void payOut(ServerLevel server, BlockPos pos);

    // ---- shared surface ----------------------------------------------------

    /** Pipe/hopper view over the chest inventory. */
    public InvWrapper itemHandler() {
        return itemHandler;
    }

    /** The Apex dock (#281 Phase 4): the installed Apex frog + the Liquid Experience bank. */
    public AltarApexDock dock() {
        return dock;
    }

    /**
     * Whether this altar's Apex frog is installed - server reads the dock, the
     * client reads the update-tag mirror. Drives the Jade "waiting for its Apex
     * Frog" warning on a structurally-complete but unarmed altar.
     */
    public boolean apexInstalled() {
        return dock.isInstalled() || clientApexInstalled;
    }

    /** Deposit a reward item, returning whatever did not fit (the caller spills it). */
    public ItemStack deposit(ItemStack stack) {
        return ItemHandlerHelper.insertItem(itemHandler, stack, false);
    }

    /** Summon progress for the client animation (0 = idle, else ticks remaining). */
    public int summonTicks() {
        return summonTicks;
    }

    /** The resolved build orientation (see the field javadoc); read by renderers and fuel lookups. */
    public net.minecraft.core.Direction orientation() {
        return orientation;
    }

    /** Cache the resolved orientation; sync to the client (renderers read it) only on change. */
    protected void setOrientation(net.minecraft.core.Direction dir) {
        if (dir != null && dir.getAxis().isHorizontal() && this.orientation != dir) {
            this.orientation = dir;
            setChanged();
            syncToClient();
        }
    }

    // ---- the summon state machine -----------------------------------------

    /** Server ticker: drive an in-progress summon, else periodically validate + reconcile + arm. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BossAltarHatchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (be.summonTicks > 0) {
            be.advanceSummon(server, pos);
            return;
        }
        if (++be.tickCounter < RECONCILE_INTERVAL) {
            return;
        }
        be.tickCounter = 0;
        boolean valid = be.validateStructure(server, pos);
        // The display frog IS the installed Apex's render (Phase 4): it shows
        // only while the altar is valid AND armed (installed, or predation off).
        be.reconcileDisplay(server, pos, valid && be.dock.armed());
        if (valid && PFConfig.bossEnabled() && be.dock.armed() && be.fuelReady(server, pos)) {
            be.summonTicks = be.summonDuration();
            be.onSummonStart(server, pos);
            be.syncToClient();
        }
    }

    private void advanceSummon(ServerLevel server, BlockPos pos) {
        summonTicks--;
        int total = summonDuration();
        onSummonProgress(server, pos, total - summonTicks, total);
        setChanged();
        if (summonTicks > 0) {
            return;
        }
        summonTicks = 0;
        syncToClient();
        if (!validateStructure(server, pos)) {
            return; // broken mid-summon - abort, fuel untouched
        }
        lashDisplay(server, pos);
        spendFuel(server, pos);
        // XP banks as Liquid Experience in the dock (overflow -> orbs, never voided).
        int xp = xpReward();
        if (xp > 0) {
            dock.bankXp(server, Vec3.atCenterOf(pos), xp);
        }
        payOut(server, pos);
    }

    /** Hatch removal (Phase 4): the container spills (super), the installed Apex respawns, the display frog goes. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel server) {
            dock.releaseFrog(server, pos);
            discardDisplay(server, pos);
        }
    }

    // ---- shared helpers ------------------------------------------------------

    /**
     * Roll a loot table into the hatch with a never-spawned phantom of
     * {@code phantomType} as the {@code this_entity} context (mirrors a real kill so
     * pack/GLM conditions apply, but no boss ever enters the world). Stacks failing
     * {@code keep} are dropped - the strip-guard against double-paying an explicit reward.
     */
    protected void rollLoot(ServerLevel server, BlockPos pos, EntityType<?> phantomType,
            ResourceKey<LootTable> tableKey, Predicate<ItemStack> keep) {
        Entity phantom = phantomType.create(server, EntitySpawnReason.MOB_SUMMONED);
        if (phantom == null) {
            return; // never added to the world; only the loot context needs it
        }
        phantom.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        LootParams params = new LootParams.Builder(server)
            .withParameter(LootContextParams.THIS_ENTITY, phantom)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().genericKill())
            .create(LootContextParamSets.ENTITY);
        LootTable table = server.getServer().reloadableRegistries().getLootTable(tableKey);
        table.getRandomItems(params, server.getRandom().nextLong(), stack -> {
            if (keep.test(stack)) {
                spillDeposit(server, pos, stack);
            }
        });
        phantom.discard();
    }

    /** Deposit into the hatch, spilling any overflow above it. */
    protected void spillDeposit(ServerLevel server, BlockPos pos, ItemStack stack) {
        ItemStack overflow = deposit(stack);
        if (!overflow.isEmpty()) {
            Containers.dropItemStack(server, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, overflow);
        }
    }

    /**
     * Keep exactly one display frog of {@code type} pinned at {@code perch} while
     * {@code show}; remove all when hidden. Re-pinning each pass corrects drift.
     * {@code spawnYaw} orients a fresh spawn; a non-null {@code steerYaw} re-asserts
     * the yaw every pass (the wither altar tracks its ritual direction), null keeps
     * the frog's current yaw (the dragon altar lets players admire it turned).
     */
    protected <T extends AltarDisplayFrog> void reconcileDisplayFrog(ServerLevel server, BlockPos perch,
            float spawnYaw, @Nullable Float steerYaw, EntityType<T> type, Class<T> cls, boolean show) {
        List<T> frogs = server.getEntitiesOfClass(cls, new AABB(perch).inflate(0.5));
        if (!show) {
            for (T f : frogs) {
                f.discard();
            }
            return;
        }
        double cx = perch.getX() + 0.5;
        double cy = perch.getY();
        double cz = perch.getZ() + 0.5;
        if (frogs.isEmpty()) {
            T frog = type.create(server, EntitySpawnReason.MOB_SUMMONED);
            if (frog != null) {
                frog.snapTo(cx, cy, cz, spawnYaw, 0.0F);
                frog.setYBodyRot(spawnYaw);
                frog.setYHeadRot(spawnYaw);
                server.addFreshEntity(frog);
            }
        } else {
            T frog = frogs.get(0);
            float yaw = steerYaw != null ? steerYaw : frog.getYRot();
            frog.snapTo(cx, cy, cz, yaw, 0.0F);
            if (steerYaw != null) {
                frog.setYBodyRot(yaw);
                frog.setYHeadRot(yaw);
            }
            frog.setDeltaMovement(Vec3.ZERO);
            for (int i = 1; i < frogs.size(); i++) {
                frogs.get(i).discard();
            }
        }
    }

    /** Tongue-lash every display frog of {@code cls} at {@code perch}. */
    protected <T extends AltarDisplayFrog> void lashDisplayFrog(ServerLevel server, BlockPos perch, Class<T> cls) {
        for (T frog : server.getEntitiesOfClass(cls, new AABB(perch).inflate(0.5))) {
            frog.triggerEat();
        }
    }

    /** Discard every display frog of {@code cls} at {@code perch} (Hatch removal). */
    protected <T extends AltarDisplayFrog> void discardDisplayFrog(ServerLevel server, BlockPos perch, Class<T> cls) {
        for (T frog : server.getEntitiesOfClass(cls, new AABB(perch).inflate(1.0))) {
            frog.discard();
        }
    }

    public void syncToClient() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---- container plumbing ---------------------------------------------------

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
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.summonTicks = input.getIntOr("SummonTicks", 0);
        this.dock.load(input);
        // "Ritual" is the historical key (the wither altar shipped it first).
        String dirName = input.getStringOr("Ritual", "");
        net.minecraft.core.Direction d = dirName.isEmpty() ? null : net.minecraft.core.Direction.byName(dirName);
        this.orientation = d != null && d.getAxis().isHorizontal() ? d : net.minecraft.core.Direction.SOUTH;
        this.clientApexInstalled = input.getBooleanOr("ApexInstalled", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("SummonTicks", summonTicks);
        output.putString("Ritual", orientation.getName());
        dock.save(output);
    }

    // Sync the summon progress to the client (the chest contents ride the menu,
    // not the BE update tag) so the replica animation can read it.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("SummonTicks", summonTicks);
        tag.putBoolean("ApexInstalled", dock.isInstalled());
        tag.putString("Ritual", orientation.getName());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
