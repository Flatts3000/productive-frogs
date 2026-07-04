package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.WitherbaneFrog;
import com.flatts.productivefrogs.content.multiblock.WitherAltarValidator;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wither Altar Hatch block entity (#247) on the shared
 * {@link BossAltarHatchBlockEntity} machinery. Altar-specific pieces: the
 * facing-aware {@link WitherAltarValidator} (the resolved ritual direction is
 * cached + synced for the replica renderer and stamped onto the receptacles),
 * the seven-receptacle vanilla summon T as fuel, Witherbane on top of the
 * Hatch, and the raw-drops payout - the explicit Nether Star (the vanilla
 * table only stars player-credited kills) plus the Wither's own loot roll
 * with the star stripped so a table that DOES yield one can't double-pay.
 */
public class WitherAltarHatchBlockEntity extends BossAltarHatchBlockEntity {

    public WitherAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.WITHER_ALTAR_HATCH.get(), pos, state, FrogKind.Apex.WITHER);
    }

    /**
     * The resolved ritual direction (way the receptacle wall faces) - the wither's
     * name for the base orientation; read by the replica renderer.
     */
    public Direction ritual() {
        return orientation();
    }

    @Override
    protected boolean validateStructure(ServerLevel server, BlockPos pos) {
        WitherAltarValidator.Result result = WitherAltarValidator.validate(server, pos);
        if (result.valid() && result.ritual() != null) {
            setOrientation(result.ritual());
            // A FORMED altar forces every receptacle's held item onto the
            // arena-facing side, whatever face the player inserted it on
            // (unformed altars keep the player's choice). Runs every
            // validation pass, so a fresh insert snaps within a second.
            for (BlockPos rp : WitherAltarValidator.receptacles(pos, result.ritual())) {
                if (server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) {
                    r.setRitual(result.ritual());
                }
            }
        }
        return result.valid();
    }

    @Override
    protected boolean fuelReady(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : WitherAltarValidator.receptacles(pos, ritual())) {
            if (!(server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void spendFuel(ServerLevel server, BlockPos pos) {
        // Spend all seven ritual receptacles (the full vanilla cost).
        for (BlockPos rp : WitherAltarValidator.receptacles(pos, ritual())) {
            if (server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) {
                r.consume();
            }
        }
    }

    /** Witherbane pins on top of the Hatch facing the resolved ritual. */
    @Override
    protected void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show) {
        float yaw = ritual().toYRot();
        reconcileDisplayFrog(server, WitherAltarValidator.witherbanePos(pos), yaw, yaw,
            WitherbaneFrog.type(), WitherbaneFrog.class, show);
    }

    @Override
    protected void lashDisplay(ServerLevel server, BlockPos pos) {
        lashDisplayFrog(server, WitherAltarValidator.witherbanePos(pos), WitherbaneFrog.class);
    }

    @Override
    protected void discardDisplay(ServerLevel server, BlockPos pos) {
        discardDisplayFrog(server, WitherAltarValidator.witherbanePos(pos), WitherbaneFrog.class);
    }

    @Override
    protected int summonDuration() {
        return PFConfig.witherAltarSummonTicks();
    }

    @Override
    protected int xpReward() {
        return PFConfig.witherAltarXpReward();
    }

    @Override
    protected void onSummonStart(ServerLevel server, BlockPos pos) {
        server.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * The raw-drops payout (#281 Phase 4): the star is paid explicitly - vanilla's
     * wither loot TABLE is empty; the Nether Star is hardcoded in
     * {@code WitherBoss.dropCustomDeathLoot}, so no loot-context trick can roll
     * it - and stripped from the roll so a pack table that DOES add one can't
     * double-pay (the roll is player-credited, so such a pool would pay).
     */
    @Override
    protected void payOut(ServerLevel server, BlockPos pos) {
        spillDeposit(server, pos, new ItemStack(Items.NETHER_STAR));
        rollLoot(server, pos, EntityType.WITHER,
            EntityType.WITHER.getDefaultLootTable().orElseThrow(),
            stack -> !stack.is(Items.NETHER_STAR));
        server.playSound(null, pos, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.wither_altar_hatch");
    }

    // orientation persistence + client sync (the "Ritual" key) lives in the base.
}
