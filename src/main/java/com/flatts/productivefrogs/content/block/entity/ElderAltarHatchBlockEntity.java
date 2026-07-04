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
 * predation chain), Elderbane swimming in front of the Hatch, and the
 * raw-drops payout - the Elder Guardian's full player-credited loot roll
 * (Wet Sponge included) plus the data-driven
 * {@code productivefrogs:elder_altar} supplemental table.
 */
public class ElderAltarHatchBlockEntity extends BossAltarHatchBlockEntity {

    /**
     * Data-driven supplemental drops (the dragon-altar precedent): packs/mods
     * override or add pools to {@code productivefrogs:elder_altar} to change
     * what the altar yields beyond the Elder Guardian's own table. Ships empty.
     */
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable>
        ELDER_ALTAR_LOOT_TABLE = net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.LOOT_TABLE,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(
                com.flatts.productivefrogs.ProductiveFrogs.MOD_ID, "elder_altar"));

    public ElderAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.ELDER_ALTAR_HATCH.get(), pos, state, FrogKind.Apex.ELDER);
    }

    @Override
    protected boolean validateStructure(ServerLevel server, BlockPos pos) {
        ElderAltarValidator.Result result = ElderAltarValidator.validate(server, pos, orientation());
        if (result.valid()) {
            setOrientation(result.interior());
        }
        return result.valid();
    }

    @Override
    protected boolean fuelReady(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : ElderAltarValidator.receptacles(pos, orientation())) {
            if (!(server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void spendFuel(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : ElderAltarValidator.receptacles(pos, orientation())) {
            if (server.getBlockEntity(rp) instanceof SummonReceptacleBlockEntity r) {
                r.consume();
            }
        }
    }

    @Override
    protected void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show) {
        // In the water in front of the wall-mounted Hatch, facing the tank center.
        float yaw = orientation().toYRot();
        reconcileDisplayFrog(server, ElderAltarValidator.elderbanePos(pos, orientation()), yaw, yaw,
            ElderbaneFrog.type(), ElderbaneFrog.class, show);
    }

    @Override
    protected void lashDisplay(ServerLevel server, BlockPos pos) {
        lashDisplayFrog(server, ElderAltarValidator.elderbanePos(pos, orientation()), ElderbaneFrog.class);
    }

    @Override
    protected void discardDisplay(ServerLevel server, BlockPos pos) {
        discardDisplayFrog(server, ElderAltarValidator.elderbanePos(pos, orientation()), ElderbaneFrog.class);
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
     * The raw-drops payout (#281 Phase 4): the Elder Guardian's own loot roll
     * pays everything - {@code rollLoot} is player-credited, so the
     * {@code killed_by_player}-gated pools (the Wet Sponge, the fishing bonus)
     * pay naturally; no explicit top-up or strip-guard needed (review fix).
     */
    @Override
    protected void payOut(ServerLevel server, BlockPos pos) {
        rollLoot(server, pos, EntityType.ELDER_GUARDIAN,
            EntityType.ELDER_GUARDIAN.getDefaultLootTable().orElseThrow(),
            stack -> true);
        rollLoot(server, pos, EntityType.ELDER_GUARDIAN, ELDER_ALTAR_LOOT_TABLE, stack -> true);
        server.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.elder_altar_hatch");
    }
}
