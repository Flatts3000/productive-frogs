package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ElderbaneFrog;
import com.flatts.productivefrogs.content.multiblock.ElderAltarValidator;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Elder Guardian Altar Hatch block entity (#280) on the shared
 * {@link BossAltarHatchBlockEntity} machinery - the Monument Well's output and
 * summon brain, anchored in the tank floor. Altar-specific pieces: the symmetric
 * water-checked {@link ElderAltarValidator}, four Tide Offering Receptacles at
 * the roof corners as fuel (4 prismarine crystals - renewable through the
 * predation chain), Elderbane swimming above the Hatch, and the raw-drops
 * payout - the Elder Guardian's own loot roll covers everything (the Wet Sponge
 * is unconditional and the prismarine/cod pools carry no player-kill condition),
 * so no explicit top-up is needed.
 */
public class ElderAltarHatchBlockEntity extends BossAltarHatchBlockEntity {

    public ElderAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.ELDER_ALTAR_HATCH.get(), pos, state, FrogKind.Apex.ELDER);
    }

    @Override
    protected boolean validateStructure(ServerLevel server, BlockPos pos) {
        return ElderAltarValidator.validate(server, pos).valid();
    }

    @Override
    protected boolean fuelReady(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : ElderAltarValidator.receptacles(pos)) {
            if (!(server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void spendFuel(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : ElderAltarValidator.receptacles(pos)) {
            if (server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) {
                r.consume();
            }
        }
    }

    @Override
    protected void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show) {
        reconcileDisplayFrog(server, ElderAltarValidator.elderbanePos(pos), 180.0F, null,
            ElderbaneFrog.type(), ElderbaneFrog.class, show);
    }

    @Override
    protected void lashDisplay(ServerLevel server, BlockPos pos) {
        lashDisplayFrog(server, ElderAltarValidator.elderbanePos(pos), ElderbaneFrog.class);
    }

    @Override
    protected void discardDisplay(ServerLevel server, BlockPos pos) {
        discardDisplayFrog(server, ElderAltarValidator.elderbanePos(pos), ElderbaneFrog.class);
    }

    @Override
    protected int summonDuration() {
        return PFConfig.elderAltarSummonTicks();
    }

    @Override
    protected int xpReward() {
        return PFConfig.elderAltarXpReward();
    }

    @Override
    protected void onSummonStart(ServerLevel server, BlockPos pos) {
        server.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * The raw-drops payout (#281 Phase 4): the Wet Sponge is paid explicitly (its
     * loot pool carries {@code killed_by_player}, which the phantom generic-kill
     * roll is not - the same trap as the wither's Nether Star) and stripped from
     * the roll so a table that DOES yield one can't double-pay; the rest of the
     * Elder Guardian's table (shards, cod/crystals) rolls unconditioned.
     */
    @Override
    protected void payOut(ServerLevel server, BlockPos pos) {
        spillDeposit(server, pos, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WET_SPONGE));
        rollLoot(server, pos, EntityType.ELDER_GUARDIAN,
            EntityType.ELDER_GUARDIAN.getDefaultLootTable().orElseThrow(),
            stack -> !stack.is(net.minecraft.world.item.Items.WET_SPONGE));
        server.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.elder_altar_hatch");
    }
}
