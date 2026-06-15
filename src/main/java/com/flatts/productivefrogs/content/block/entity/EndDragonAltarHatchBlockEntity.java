package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.PlinthFrog;
import com.flatts.productivefrogs.content.multiblock.DragonAltarValidator;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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

    /** Summon length - mirrors the feel of the vanilla respawn (~10s). Public for the renderer's progress calc. */
    public static final int SUMMON_TICKS = 200;
    /** XP awarded per summon - the vanilla repeat-kill dragon value. */
    private static final int XP_REWARD = 500;
    /** Whether each summon also yields a dragon egg (on - the altar makes the egg renewable). */
    private static final boolean REPEATABLE_EGG = true;
    private static final int LEVEL_EVENT_DRAGON_ROAR = 3001;   // ANIMATION_DRAGON_SUMMON_ROAR
    private static final int LEVEL_EVENT_DRAGON_DEATH = 1028;  // SOUND_DRAGON_DEATH

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);
    private int tickCounter;
    /** 0 = idle; > 0 = a summon is in progress (ticks remaining). Synced (start/end) as the on/off signal. */
    private int summonTicks;
    /** Client-only render state: game time when the renderer first saw this summon, for local growth animation. */
    public long clientSummonStartGameTime = -1L;

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

    /** The block the plinth frog stands in: on top of the central bedrock plinth (three below the hatch). */
    public static BlockPos plinthFrogPos(BlockPos hatchPos) {
        return hatchPos.offset(0, -3, 0);
    }

    /** Server ticker: drive the summon state machine, then (when idle) reconcile the plinth frog. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, EndDragonAltarHatchBlockEntity be) {
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
        boolean valid = DragonAltarValidator.validate(server, pos).valid();
        reconcileFrog(server, pos, valid);
        // Start a summon once the altar is complete and all four crystals are loaded.
        if (valid && PFConfig.bossEnabled() && allReceptaclesFilled(server, pos)) {
            be.summonTicks = SUMMON_TICKS;
            server.levelEvent(LEVEL_EVENT_DRAGON_ROAR, pos, 0);
            be.syncToClient();
        }
    }

    /** Advance an in-progress summon: roar at intervals, then pay out at the end. */
    private static void advanceSummon(ServerLevel server, BlockPos pos, EndDragonAltarHatchBlockEntity be) {
        be.summonTicks--;
        int elapsed = SUMMON_TICKS - be.summonTicks;
        if (elapsed == 60 || elapsed == 120 || elapsed == SUMMON_TICKS - 10) {
            server.levelEvent(LEVEL_EVENT_DRAGON_ROAR, pos, 0);
        }
        be.setChanged();
        if (be.summonTicks <= 0) {
            completeSummon(server, pos, be);
        }
    }

    /** Finish the summon: spend the crystals and pay out the reward (XP + breath + optional egg). */
    private static void completeSummon(ServerLevel server, BlockPos pos, EndDragonAltarHatchBlockEntity be) {
        be.summonTicks = 0;
        be.syncToClient();
        if (!DragonAltarValidator.validate(server, pos).valid()) {
            return; // broken mid-summon - abort, crystals untouched
        }
        // Dragonsbane eats the summoned dragon (tongue lash).
        for (PlinthFrog frog : server.getEntitiesOfClass(PlinthFrog.class, new AABB(plinthFrogPos(pos)).inflate(0.5))) {
            frog.triggerEat();
        }
        for (BlockPos rp : DragonAltarValidator.receptacles(pos)) {
            if (server.getBlockEntity(rp) instanceof EndCrystalReceptacleBlockEntity r) {
                r.consume();
            }
        }
        // Reward: XP orbs at the hatch, one dragon's breath (and, if enabled, an egg)
        // into the hatch inventory with overflow dropped above it.
        ExperienceOrb.award(server, Vec3.atCenterOf(pos), XP_REWARD);
        spill(server, pos, be.deposit(new ItemStack(Items.DRAGON_BREATH)));
        if (REPEATABLE_EGG) {
            spill(server, pos, be.deposit(new ItemStack(Items.DRAGON_EGG)));
        }
        // Every other drop the dragon would yield. Vanilla's ender_dragon loot table
        // is empty, but it is the hook drop-adding mods target via global loot
        // modifiers - so rolling it here routes any modded dragon drops into the
        // hatch too, with no real dragon (and no portal / boss bar / gateway).
        rollDragonLoot(server, pos, be);
        server.levelEvent(LEVEL_EVENT_DRAGON_DEATH, pos, 0);
    }

    /** Roll the {@code minecraft:entities/ender_dragon} loot table into the hatch (mod-drop compat). */
    private static void rollDragonLoot(ServerLevel server, BlockPos pos, EndDragonAltarHatchBlockEntity be) {
        EnderDragon phantom = EntityType.ENDER_DRAGON.create(server);
        if (phantom == null) {
            return; // never added to the world; only the loot context needs it
        }
        phantom.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        LootParams params = new LootParams.Builder(server)
            .withParameter(LootContextParams.THIS_ENTITY, phantom)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().genericKill())
            .create(LootContextParamSets.ENTITY);
        LootTable table = server.getServer().reloadableRegistries().getLootTable(EntityType.ENDER_DRAGON.getDefaultLootTable());
        table.getRandomItems(params, server.getRandom().nextLong(), stack -> spill(server, pos, be.deposit(stack)));
        phantom.discard();
    }

    private static void spill(ServerLevel server, BlockPos pos, ItemStack overflow) {
        if (!overflow.isEmpty()) {
            Containers.dropItemStack(server, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, overflow);
        }
    }

    private static boolean allReceptaclesFilled(ServerLevel server, BlockPos hatch) {
        for (BlockPos rp : DragonAltarValidator.receptacles(hatch)) {
            if (!(server.getBlockEntity(rp) instanceof EndCrystalReceptacleBlockEntity r) || !r.isFilled()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Keep exactly one plinth frog pinned when valid; remove it when broken. Re-pinning
     * each pass also corrects any drift.
     */
    private static void reconcileFrog(ServerLevel server, BlockPos pos, boolean valid) {
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
            PlinthFrog frog = frogs.get(0);
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
