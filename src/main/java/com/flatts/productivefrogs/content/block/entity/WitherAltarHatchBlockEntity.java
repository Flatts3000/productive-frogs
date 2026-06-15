package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.WitherbaneFrog;
import com.flatts.productivefrogs.content.multiblock.WitherAltarValidator;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * Wither Altar Hatch block entity (#247) - the altar's output and summon brain.
 * Mirrors {@link EndDragonAltarHatchBlockEntity}: a {@link BaseContainerBlockEntity}
 * chest you open or pipe from, with a {@code static serverTick} running the summon
 * state machine. When the structure validates, the boss tier is on, and all seven
 * receptacles (4 Soul Sand + 3 Wither Skull) are filled, a summon runs; Witherbane
 * (pinned on top) devours the Wither replica, the receptacles are spent, and the
 * reward lands here - a Nether Star Froglight + XP + whatever else the Wither drops.
 */
public class WitherAltarHatchBlockEntity extends BaseContainerBlockEntity {

    public static final int SIZE = 27;

    /** How often (ticks) the altar reconciles Witherbane against structure validity. */
    private static final int RECONCILE_INTERVAL = 20;

    /** Summon length - mirrors the feel of a vanilla spawn (~10s). Public for the renderer's progress calc. */
    public static final int SUMMON_TICKS = 200;
    /** XP awarded per summon - a vanilla Wither's value. */
    private static final int XP_REWARD = 50;

    /** Boss slime variant whose Froglight the altar pays out (smelts back to a Nether Star). */
    private static final ResourceLocation NETHER_STAR_VARIANT =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "nether_star");

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);
    private int tickCounter;
    /** 0 = idle; > 0 = a summon is in progress (ticks remaining). Synced (start/end) as the on/off signal. */
    private int summonTicks;
    /** Client-only render state: game time when the renderer first saw this summon, for local growth animation. */
    public long clientSummonStartGameTime = -1L;

    public WitherAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.WITHER_ALTAR_HATCH.get(), pos, state);
    }

    /** Pipe/hopper view over the chest inventory. */
    public InvWrapper itemHandler() {
        return itemHandler;
    }

    /**
     * Deposit a reward item, spilling any overflow as a returned leftover (the summon
     * decides what to do with it). Used by the summon state machine.
     */
    public ItemStack deposit(ItemStack stack) {
        return ItemHandlerHelper.insertItem(itemHandler, stack, false);
    }

    /** Server ticker: drive the summon state machine, then (when idle) reconcile Witherbane. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WitherAltarHatchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (be.summonTicks > 0) {
            advanceSummon(server, pos, be);
            return;
        }
        if (++be.tickCounter < RECONCILE_INTERVAL) {
            return;
        }
        be.tickCounter = 0;
        boolean valid = WitherAltarValidator.validate(server, pos).valid();
        reconcileFrog(server, pos, valid);
        // Start a summon once the altar is complete and all seven receptacles are loaded.
        if (valid && PFConfig.bossEnabled() && allReceptaclesFilled(server, pos)) {
            be.summonTicks = SUMMON_TICKS;
            server.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
            be.syncToClient();
        }
    }

    /** Advance an in-progress summon: pay out at the end. */
    private static void advanceSummon(ServerLevel server, BlockPos pos, WitherAltarHatchBlockEntity be) {
        be.summonTicks--;
        be.setChanged();
        if (be.summonTicks <= 0) {
            completeSummon(server, pos, be);
        }
    }

    /** Finish the summon: spend the receptacles and pay out the reward. */
    private static void completeSummon(ServerLevel server, BlockPos pos, WitherAltarHatchBlockEntity be) {
        be.summonTicks = 0;
        be.syncToClient();
        if (!WitherAltarValidator.validate(server, pos).valid()) {
            return; // broken mid-summon - abort, receptacles untouched
        }
        // Witherbane eats the summoned Wither (tongue lash).
        for (WitherbaneFrog frog : server.getEntitiesOfClass(WitherbaneFrog.class,
                new AABB(WitherAltarValidator.witherbanePos(pos)).inflate(0.5))) {
            frog.triggerEat();
        }
        // Spend all seven ritual receptacles (the full vanilla cost).
        for (BlockPos rp : WitherAltarValidator.receptacles(pos)) {
            if (server.getBlockEntity(rp) instanceof WitherSummonReceptacleBlockEntity r) {
                r.consume();
            }
        }
        // Reward: XP at the hatch + the Nether Star Froglight (the boss-Froglight model)
        // + whatever else the Wither is programmed to drop (its loot table, star stripped).
        if (XP_REWARD > 0) {
            ExperienceOrb.award(server, Vec3.atCenterOf(pos), XP_REWARD);
        }
        spill(server, pos, be.deposit(FrogTongueDropHandler.buildFroglight(NETHER_STAR_VARIANT, null)));
        rollWitherLoot(server, pos, be);
        server.playSound(null, pos, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /**
     * Roll the vanilla {@code minecraft:entities/wither} loot table into the hatch with a
     * never-spawned phantom Wither as the {@code this_entity} context - this is the
     * "whatever else the Wither drops" bucket and catches mod GLM additions. The raw
     * Nether Star is stripped, because the star is paid out as the Nether Star Froglight.
     */
    private static void rollWitherLoot(ServerLevel server, BlockPos pos, WitherAltarHatchBlockEntity be) {
        WitherBoss phantom = EntityType.WITHER.create(server);
        if (phantom == null) {
            return; // never added to the world; only the loot context needs it
        }
        phantom.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        LootParams params = new LootParams.Builder(server)
            .withParameter(LootContextParams.THIS_ENTITY, phantom)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().genericKill())
            .create(LootContextParamSets.ENTITY);
        LootTable table = server.getServer().reloadableRegistries().getLootTable(EntityType.WITHER.getDefaultLootTable());
        table.getRandomItems(params, server.getRandom().nextLong(), stack -> {
            if (!stack.is(Items.NETHER_STAR)) { // the star only ever appears as the Froglight
                spill(server, pos, be.deposit(stack));
            }
        });
        phantom.discard();
    }

    private static void spill(ServerLevel server, BlockPos pos, ItemStack overflow) {
        if (!overflow.isEmpty()) {
            Containers.dropItemStack(server, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, overflow);
        }
    }

    private static boolean allReceptaclesFilled(ServerLevel server, BlockPos hatch) {
        for (BlockPos rp : WitherAltarValidator.receptacles(hatch)) {
            if (!(server.getBlockEntity(rp) instanceof WitherSummonReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Keep exactly one Witherbane pinned when valid; remove it when broken. Re-pinning
     * each pass also corrects any drift. It faces the ritual (+Z, yaw 0).
     */
    private static void reconcileFrog(ServerLevel server, BlockPos pos, boolean valid) {
        BlockPos perch = WitherAltarValidator.witherbanePos(pos);
        List<WitherbaneFrog> frogs = server.getEntitiesOfClass(WitherbaneFrog.class, new AABB(perch).inflate(0.5));
        if (!valid) {
            for (WitherbaneFrog f : frogs) {
                f.discard();
            }
            return;
        }
        double cx = perch.getX() + 0.5;
        double cy = perch.getY();
        double cz = perch.getZ() + 0.5;
        if (frogs.isEmpty()) {
            WitherbaneFrog frog = WitherbaneFrog.type().create(server);
            if (frog != null) {
                frog.moveTo(cx, cy, cz, 0.0F, 0.0F);
                frog.setYBodyRot(0.0F);
                frog.setYHeadRot(0.0F);
                server.addFreshEntity(frog);
            }
        } else {
            WitherbaneFrog frog = frogs.get(0);
            frog.moveTo(cx, cy, cz, frog.getYRot(), 0.0F);
            frog.setDeltaMovement(Vec3.ZERO);
            for (int i = 1; i < frogs.size(); i++) {
                frogs.get(i).discard();
            }
        }
    }

    private void syncToClient() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Summon progress for the client animation (0 = idle, else ticks remaining). */
    public int summonTicks() {
        return summonTicks;
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
        return Component.translatable("block.productivefrogs.wither_altar_hatch");
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
        this.summonTicks = tag.getInt("SummonTicks");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("SummonTicks", summonTicks);
    }

    // Sync only the summon progress to the client (the chest contents ride the menu,
    // not the BE update tag) so the summon animation can read it.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("SummonTicks", summonTicks);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
