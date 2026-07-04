package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.DragonsbaneFrog;
import com.flatts.productivefrogs.content.multiblock.DragonAltarValidator;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * End Dragon Altar Hatch block entity (#249) on the shared
 * {@link BossAltarHatchBlockEntity} machinery. Altar-specific pieces: the
 * site-bound {@link DragonAltarValidator} (augments the vanilla End exit
 * portal), four End Crystal receptacles as fuel, Dragonsbane on the bedrock
 * plinth, dragon roars during the summon, and the raw-drops payout - Dragon's
 * Breath always, a Dragon Egg when {@code repeatableEgg} is on, plus the
 * data-driven {@code productivefrogs:dragon_altar} loot table (packs edit or
 * extend it without Java).
 */
public class EndDragonAltarHatchBlockEntity extends BossAltarHatchBlockEntity {

    private static final int LEVEL_EVENT_DRAGON_ROAR = 3001;   // ANIMATION_DRAGON_SUMMON_ROAR
    private static final int LEVEL_EVENT_DRAGON_DEATH = 1028;  // SOUND_DRAGON_DEATH

    /** Data-driven drop list for the altar (pack-overridable); see {@code loot_table/dragon_altar.json}. */
    private static final ResourceKey<LootTable> DRAGON_ALTAR_LOOT_TABLE = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "dragon_altar"));

    public EndDragonAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(), pos, state, FrogKind.Apex.DRAGON);
    }

    /** The block the plinth frog stands in: on top of the central bedrock plinth (three below the hatch). */
    public static BlockPos plinthFrogPos(BlockPos hatchPos) {
        return hatchPos.offset(0, -3, 0);
    }

    @Override
    protected boolean validateStructure(ServerLevel server, BlockPos pos) {
        return DragonAltarValidator.validate(server, pos).valid();
    }

    @Override
    protected boolean fuelReady(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : DragonAltarValidator.receptacles(pos)) {
            if (!(server.getBlockEntity(rp) instanceof EndCrystalReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void spendFuel(ServerLevel server, BlockPos pos) {
        for (BlockPos rp : DragonAltarValidator.receptacles(pos)) {
            if (server.getBlockEntity(rp) instanceof EndCrystalReceptacleBlockEntity r) {
                r.consume();
            }
        }
    }

    @Override
    protected void reconcileDisplay(ServerLevel server, BlockPos pos, boolean show) {
        reconcileDisplayFrog(server, plinthFrogPos(pos), 180.0F, null,
            DragonsbaneFrog.type(), DragonsbaneFrog.class, show);
    }

    @Override
    protected void lashDisplay(ServerLevel server, BlockPos pos) {
        lashDisplayFrog(server, plinthFrogPos(pos), DragonsbaneFrog.class);
    }

    @Override
    protected void discardDisplay(ServerLevel server, BlockPos pos) {
        discardDisplayFrog(server, plinthFrogPos(pos), DragonsbaneFrog.class);
    }

    @Override
    protected int summonDuration() {
        return PFConfig.dragonAltarSummonTicks();
    }

    @Override
    protected int xpReward() {
        return PFConfig.dragonAltarXpReward();
    }

    @Override
    protected void onSummonStart(ServerLevel server, BlockPos pos) {
        server.levelEvent(LEVEL_EVENT_DRAGON_ROAR, pos, 0);
    }

    /** Roar at intervals through the summon. */
    @Override
    protected void onSummonProgress(ServerLevel server, BlockPos pos, int elapsed, int total) {
        if (elapsed == 60 || elapsed == 120 || elapsed == total - 10) {
            server.levelEvent(LEVEL_EVENT_DRAGON_ROAR, pos, 0);
        }
    }

    /**
     * The raw-drops payout (#281 Phase 4): Dragon's Breath always, a Dragon Egg when
     * repeatableEgg is on (the renewable-egg lever), then whatever the data-driven
     * {@code productivefrogs:dragon_altar} table yields (default: the Princess's Kiss).
     */
    @Override
    protected void payOut(ServerLevel server, BlockPos pos) {
        spillDeposit(server, pos, new ItemStack(Items.DRAGON_BREATH));
        if (PFConfig.dragonAltarRepeatableEgg()) {
            spillDeposit(server, pos, new ItemStack(Items.DRAGON_EGG));
        }
        rollLoot(server, pos, EntityType.ENDER_DRAGON, DRAGON_ALTAR_LOOT_TABLE, stack -> true);
        server.levelEvent(LEVEL_EVENT_DRAGON_DEATH, pos, 0);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.end_dragon_altar_hatch");
    }
}
