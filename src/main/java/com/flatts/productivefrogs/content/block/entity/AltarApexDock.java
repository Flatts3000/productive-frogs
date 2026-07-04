package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.fluid.LiquidExperienceFluid;
import com.flatts.productivefrogs.content.item.EntityNetItem;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Apex-Frog dock + Liquid Experience bank every boss-altar Hatch owns
 * (#281 Phase 4). Composition, not inheritance - the two shipped hatch BEs
 * (and the two Phase 4 newcomers) each hold one and delegate:
 *
 * <ul>
 *   <li><b>Install</b> (maintainer ruling): shift-right-click the Hatch with a
 *       net holding the altar's MATCHING Apex frog. The frog's whole net NBT is
 *       stored here (the #210 whole-entity lesson) - the altar renders its
 *       display frog while installed, and breaking the Hatch respawns the REAL
 *       frog, stats intact, where the altar stood.</li>
 *   <li><b>Gate</b>: {@link #armed()} - the altar's summon loop runs only while
 *       the matching Apex is installed. When the predation system is config-off
 *       the requirement is waived (apex frogs are unbreedable then; the altars
 *       fall back to their pre-Phase-4 behaviour instead of bricking).</li>
 *   <li><b>Liquid Experience bank</b>: the altar's XP payout banks as
 *       {@code liquid_experience} here ({@link LiquidExperienceFluid#MB_PER_POINT
 *       20 mB/point}) instead of orb spray; pipes drain it via the extract-only
 *       {@link #fluidResource} capability. A full bank overflows the REMAINDER
 *       as vanilla orbs - XP is never voided.</li>
 * </ul>
 */
public final class AltarApexDock {

    /** Bank capacity: 64 buckets = 3,200 XP points at 20 mB/point. */
    public static final int CAPACITY_MB = 64 * 1000;

    private final FrogKind.Apex required;
    private final Runnable onChanged;

    @Nullable
    private CompoundTag installedFrogNbt;
    private int liquidXpMb;

    public AltarApexDock(FrogKind.Apex required, Runnable onChanged) {
        this.required = required;
        this.onChanged = onChanged;
    }

    /** The Apex kind this altar demands ({@code Apex.WITHER}, {@code Apex.DRAGON}, ...). */
    public FrogKind.Apex required() {
        return required;
    }

    public boolean isInstalled() {
        return installedFrogNbt != null;
    }

    /**
     * Whether the altar may run its summon loop: the matching Apex is
     * installed, or the predation system is config-off (requirement waived so
     * a predation-disabled pack keeps working altars).
     */
    public boolean armed() {
        return !PFConfig.predatorsEnabled() || isInstalled();
    }

    /**
     * Try to install from a filled net: the captured entity must be a Resource
     * FROG (the entity-type check matters: a ResourceTadpole writes the same
     * Kind NBT dialect, so kind alone would let an apex TADPOLE install and arm
     * the altar) whose Kind is this altar's required Apex. On success the net
     * NBT moves onto the dock (caller empties the net item). Returns false -
     * net untouched - for an empty net, a non-frog, or the wrong kind.
     */
    public boolean tryInstall(ItemStack netStack) {
        if (isInstalled() || !EntityNetItem.isFilled(netStack)) {
            return false;
        }
        CustomData data = netStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        String entityId = tag.getStringOr("entity", "");
        if (!com.flatts.productivefrogs.registry.PFEntities.RESOURCE_FROG.getId().toString().equals(entityId)) {
            return false; // a tadpole (or anything else) is not an installable Apex
        }
        FrogKind kind = FrogKind.readFromTag(tag).orElse(null);
        if (kind != required) {
            return false;
        }
        installedFrogNbt = tag;
        onChanged.run();
        return true;
    }

    /**
     * Respawn the installed frog at {@code pos} (the Hatch being broken - the
     * maintainer ruling's "the frog drops"). Whole-entity rebuild through the
     * net's own release path, so stats and name survive exactly like a normal
     * net release. Clears the dock.
     */
    public void releaseFrog(ServerLevel level, BlockPos pos) {
        if (installedFrogNbt == null) {
            return;
        }
        // Rebuild exactly like EntityNetItem.entityFromStack: type id from the
        // net dialect, whole-entity load, passengers stripped. The stored NBT is
        // cleared only AFTER a successful spawn - a failed rebuild must never
        // void the installed entity (it stays docked for the next attempt).
        CompoundTag tag = installedFrogNbt;
        var type = net.minecraft.world.entity.EntityType.byString(tag.getStringOr("entity", "")).orElse(null);
        if (type == null) {
            return;
        }
        Entity frog = type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (frog == null) {
            return;
        }
        tag.remove("Passengers");
        frog.load(net.minecraft.world.level.storage.TagValueInput.create(
            net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess(), tag));
        if (!(frog instanceof ResourceFrog)) {
            frog.discard();
            return; // NBT retained - nothing is voided
        }
        frog.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(frog);
        installedFrogNbt = null;
        onChanged.run();
    }

    // ---- the Liquid Experience bank ------------------------------------

    /**
     * Bank {@code points} of XP as Liquid Experience; whatever exceeds the
     * bank's capacity sprays as vanilla orbs at {@code at} (never voided).
     */
    public void bankXp(ServerLevel level, Vec3 at, int points) {
        int mb = LiquidExperienceFluid.pointsToMb(points);
        int accepted = Math.min(mb, CAPACITY_MB - liquidXpMb);
        if (accepted > 0) {
            liquidXpMb += accepted;
            onChanged.run();
        }
        int overflowPoints = LiquidExperienceFluid.mbToWholePoints(mb - accepted);
        if (overflowPoints > 0) {
            ExperienceOrb.award(level, at, overflowPoints);
        }
    }

    public int liquidXpMb() {
        return liquidXpMb;
    }

    /** Extract-only Liquid Experience view for pipes ({@code Capabilities.Fluid.BLOCK}). */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidResource() {
        return new XpBank();
    }

    private final class XpBank
            implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> {

        private final BankJournal journal = new BankJournal();

        @Override
        public int size() {
            return 1;
        }

        @Override
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            return liquidXpMb > 0
                ? net.neoforged.neoforge.transfer.fluid.FluidResource.of(PFFluids.LIQUID_EXPERIENCE.get())
                : net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return liquidXpMb;
        }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return CAPACITY_MB;
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return resource.getFluid() == PFFluids.LIQUID_EXPERIENCE.get();
        }

        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0; // the altar fills the bank; pipes only drain
        }

        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount <= 0 || liquidXpMb <= 0
                    || resource.getFluid() != PFFluids.LIQUID_EXPERIENCE.get()) {
                return 0;
            }
            int drained = Math.min(amount, liquidXpMb);
            journal.updateSnapshots(transaction);
            liquidXpMb -= drained;
            return drained;
        }

        private final class BankJournal
                extends net.neoforged.neoforge.transfer.transaction.SnapshotJournal<Integer> {

            @Override
            protected Integer createSnapshot() {
                return liquidXpMb;
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                liquidXpMb = snapshot;
            }

            @Override
            protected void onRootCommit(Integer originalState) {
                onChanged.run();
            }
        }
    }

    // ---- serialization ---------------------------------------------------

    public void save(ValueOutput output) {
        if (installedFrogNbt != null) {
            output.store("InstalledFrog", CompoundTag.CODEC, installedFrogNbt);
        }
        if (liquidXpMb > 0) {
            output.putInt("LiquidXpMb", liquidXpMb);
        }
    }

    public void load(ValueInput input) {
        installedFrogNbt = input.read("InstalledFrog", CompoundTag.CODEC).orElse(null);
        liquidXpMb = Mth.clamp(input.getIntOr("LiquidXpMb", 0), 0, CAPACITY_MB);
    }
}
