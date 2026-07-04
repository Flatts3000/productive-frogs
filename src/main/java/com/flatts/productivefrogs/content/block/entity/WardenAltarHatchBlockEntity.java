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
        WardenAltarValidator.Result result = WardenAltarValidator.validate(server, pos);
        if (result.valid()) {
            setOrientation(result.interior());
        }
        return result.valid();
    }

    @Override
    protected boolean fuelReady(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : WardenAltarValidator.receptacles(pos, orientation())) {
            if (!(server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void spendFuel(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : WardenAltarValidator.receptacles(pos, orientation())) {
            if (server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) {
                r.consume();
            }
        }
    }

    @Override
    protected void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show) {
        // In front of the wall-mounted Hatch, facing the pit center (the replica).
        float yaw = orientation().toYRot();
        reconcileDisplayFrog(server, WardenAltarValidator.wardenbanePos(pos, orientation()), yaw, yaw,
            WardenbaneFrog.type(), WardenbaneFrog.class, show);
    }

    @Override
    protected void lashDisplay(ServerLevel server, BlockPos pos) {
        lashDisplayFrog(server, WardenAltarValidator.wardenbanePos(pos, orientation()), WardenbaneFrog.class);
    }

    @Override
    protected void discardDisplay(ServerLevel server, BlockPos pos) {
        discardDisplayFrog(server, WardenAltarValidator.wardenbanePos(pos, orientation()), WardenbaneFrog.class);
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

    /** Heartbeat ambience while the replica claws its way out of the pit floor. */
    @Override
    protected void onSummonProgress(ServerLevel server, BlockPos pos, int elapsed, int total) {
        if (elapsed > 0 && elapsed % 40 == 0 && elapsed < total - 10) {
            server.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2F, 1.0F);
        }
    }

    /**
     * Data-driven supplemental drops (the dragon-altar precedent): packs/mods
     * override or add pools to {@code productivefrogs:warden_altar} to change
     * what the altar yields beyond the Warden's own table. Ships empty.
     */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>
        WARDEN_ALTAR_LOOT_TABLE = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.LOOT_TABLE,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                com.flatts.productivefrogs.ProductiveFrogs.MOD_ID, "warden_altar"));

    /**
     * The raw-drops payout (#281 Phase 4): the Warden's loot roll (the Sculk
     * Catalyst carries no player-kill condition, so the phantom roll pays it)
     * plus one explicit Echo Shard - stripped from the roll so a pack table
     * that adds shards can't double-pay - plus the data-driven
     * {@code productivefrogs:warden_altar} supplemental table.
     */
    @Override
    protected void payOut(ServerLevel server, BlockPos pos) {
        spillDeposit(server, pos, new ItemStack(Items.ECHO_SHARD));
        rollLoot(server, pos, EntityType.WARDEN,
            EntityType.WARDEN.getDefaultLootTable().orElseThrow(),
            stack -> !stack.is(Items.ECHO_SHARD));
        rollLoot(server, pos, EntityType.WARDEN, WARDEN_ALTAR_LOOT_TABLE, stack -> true);
        server.playSound(null, pos, SoundEvents.WARDEN_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.warden_altar_hatch");
    }
}
