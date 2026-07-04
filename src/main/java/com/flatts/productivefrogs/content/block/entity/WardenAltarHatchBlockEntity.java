package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.WardenbaneFrog;
import com.flatts.productivefrogs.content.multiblock.WardenAltarValidator;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Warden Altar Hatch block entity (#279) on the shared
 * {@link BossAltarHatchBlockEntity} machinery - the Shrieker Pit's output and
 * summon brain, anchored at the pit floor. Altar-specific pieces: the symmetric
 * {@link WardenAltarValidator}, four Shrieker Receptacles on the rim as fuel
 * (4 sculk shriekers = warning level 4), Wardenbane perched on the Hatch, and
 * the raw-drops payout - the Warden's own loot roll (the Sculk Catalyst) plus
 * one explicit Echo Shard per summon, making the altar the game's renewable
 * echo shard source (vanilla Wardens drop none; shards are chest-loot only).
 */
public class WardenAltarHatchBlockEntity extends BossAltarHatchBlockEntity {

    public WardenAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.WARDEN_ALTAR_HATCH.get(), pos, state, FrogKind.Apex.WARDEN);
    }

    @Override
    protected boolean validateStructure(ServerLevel server, BlockPos pos) {
        return WardenAltarValidator.validate(server, pos).valid();
    }

    @Override
    protected boolean fuelReady(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : WardenAltarValidator.receptacles(pos)) {
            if (!(server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void spendFuel(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : WardenAltarValidator.receptacles(pos)) {
            if (server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) {
                r.consume();
            }
        }
    }

    @Override
    protected void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show) {
        reconcileDisplayFrog(server, WardenAltarValidator.wardenbanePos(pos), 180.0F, null,
            WardenbaneFrog.type(), WardenbaneFrog.class, show);
    }

    @Override
    protected void lashDisplay(ServerLevel server, BlockPos pos) {
        lashDisplayFrog(server, WardenAltarValidator.wardenbanePos(pos), WardenbaneFrog.class);
    }

    @Override
    protected void discardDisplay(ServerLevel server, BlockPos pos) {
        discardDisplayFrog(server, WardenAltarValidator.wardenbanePos(pos), WardenbaneFrog.class);
    }

    @Override
    protected int summonDuration() {
        return PFConfig.wardenAltarSummonTicks();
    }

    @Override
    protected int xpReward() {
        return PFConfig.wardenAltarXpReward();
    }

    @Override
    protected void onSummonStart(ServerLevel server, BlockPos pos) {
        server.playSound(null, pos, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * The raw-drops payout (#281 Phase 4): the Warden's loot roll (the Sculk
     * Catalyst carries no player-kill condition, so the phantom roll pays it)
     * plus one explicit Echo Shard - stripped from the roll so a pack table
     * that adds shards can't double-pay.
     */
    @Override
    protected void payOut(ServerLevel server, BlockPos pos) {
        spillDeposit(server, pos, new ItemStack(Items.ECHO_SHARD));
        rollLoot(server, pos, EntityType.WARDEN,
            EntityType.WARDEN.getDefaultLootTable().orElseThrow(),
            stack -> !stack.is(Items.ECHO_SHARD));
        server.playSound(null, pos, SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.warden_altar_hatch");
    }
}
