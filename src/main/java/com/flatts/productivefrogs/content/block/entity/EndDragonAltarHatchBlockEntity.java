package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.DragonsbaneFrog;
import com.flatts.productivefrogs.content.multiblock.DragonAltarValidator;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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

    private static final int LEVEL_EVENT_DRAGON_ROAR = 3001;   // ANIMATION_DRAGON_SUMMON_ROAR
    private static final int LEVEL_EVENT_DRAGON_DEATH = 1028;  // SOUND_DRAGON_DEATH

    /** Data-driven drop list for the altar (pack-overridable); see {@code loot_table/dragon_altar.json}. */
    private static final ResourceKey<LootTable> DRAGON_ALTAR_LOOT_TABLE = ResourceKey.create(
        Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "dragon_altar"));

    /** Boss slime variants whose Froglights the altar pays out (each smelts back to the resource). */
    private static final Identifier DRAGON_BREATH_VARIANT =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "dragon_breath");
    private static final Identifier DRAGON_EGG_VARIANT =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "dragon_egg");

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
            be.summonTicks = PFConfig.dragonAltarSummonTicks();
            server.levelEvent(LEVEL_EVENT_DRAGON_ROAR, pos, 0);
            be.syncToClient();
        }
    }

    /** Advance an in-progress summon: roar at intervals, then pay out at the end. */
    private static void advanceSummon(ServerLevel server, BlockPos pos, EndDragonAltarHatchBlockEntity be) {
        be.summonTicks--;
        int total = PFConfig.dragonAltarSummonTicks();
        int elapsed = total - be.summonTicks;
        if (elapsed == 60 || elapsed == 120 || elapsed == total - 10) {
            server.levelEvent(LEVEL_EVENT_DRAGON_ROAR, pos, 0);
        }
        be.setChanged();
        if (be.summonTicks <= 0) {
            completeSummon(server, pos, be);
        }
    }

    /** Finish the summon: spend the crystals and pay out the reward (XP + boss froglights + the dragon's drops). */
    private static void completeSummon(ServerLevel server, BlockPos pos, EndDragonAltarHatchBlockEntity be) {
        be.summonTicks = 0;
        be.syncToClient();
        if (!DragonAltarValidator.validate(server, pos).valid()) {
            return; // broken mid-summon - abort, crystals untouched
        }
        // Dragonsbane eats the summoned dragon (tongue lash).
        for (DragonsbaneFrog frog : server.getEntitiesOfClass(DragonsbaneFrog.class, new AABB(plinthFrogPos(pos)).inflate(0.5))) {
            frog.triggerEat();
        }
        for (BlockPos rp : DragonAltarValidator.receptacles(pos)) {
            if (server.getBlockEntity(rp) instanceof EndCrystalReceptacleBlockEntity r) {
                r.consume();
            }
        }
        // Reward, in three parts:
        // 1. XP orbs at the hatch.
        int xp = PFConfig.dragonAltarXpReward();
        if (xp > 0) {
            ExperienceOrb.award(server, Vec3.atCenterOf(pos), xp);
        }
        // 2. The boss Froglights - the altar's signature output. Like the rest of the
        //    mod's frog loop it yields variant-stamped Froglights (smeltable back to the
        //    resource), not the raw resource: a Dragon Breath Froglight always, and a
        //    Dragon Egg Froglight when repeatableEgg is on (the renewable-egg lever, now
        //    delivered as the froglight that smelts to an egg).
        spill(server, pos, be.deposit(FrogTongueDropHandler.buildFroglight(DRAGON_BREATH_VARIANT, null)));
        if (PFConfig.dragonAltarRepeatableEgg()) {
            spill(server, pos, be.deposit(FrogTongueDropHandler.buildFroglight(DRAGON_EGG_VARIANT, null)));
        }
        // 3. Whatever the dragon itself drops - the data-driven productivefrogs:dragon_altar
        //    loot table (default: the Princess's Kiss). Packs edit/extend it without Java.
        rollDragonLoot(server, pos, be);
        server.levelEvent(LEVEL_EVENT_DRAGON_DEATH, pos, 0);
    }

    /**
     * Roll the {@code productivefrogs:dragon_altar} loot table into the hatch. That table
     * is the data-driven definition of what the dragon itself drops (default: the
     * Princess's Kiss); packs/mods override or add pools to change what the altar yields.
     * The boss Froglights are paid out separately (see {@link #completeSummon}).
     * A never-spawned phantom dragon supplies the {@code this_entity} loot context so pack
     * conditions can key off the dragon, mirroring a real kill - but no dragon ever enters
     * the world (no portal / boss bar / gateway).
     */
    private static void rollDragonLoot(ServerLevel server, BlockPos pos, EndDragonAltarHatchBlockEntity be) {
        EnderDragon phantom = EntityType.ENDER_DRAGON.create(server);
        if (phantom == null) {
            return; // never added to the world; only the loot context needs it
        }
        phantom.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        LootParams params = new LootParams.Builder(server)
            .withParameter(LootContextParams.THIS_ENTITY, phantom)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.DAMAGE_SOURCE, server.damageSources().genericKill())
            .create(LootContextParamSets.ENTITY);
        LootTable table = server.getServer().reloadableRegistries().getLootTable(DRAGON_ALTAR_LOOT_TABLE);
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
        List<DragonsbaneFrog> frogs = server.getEntitiesOfClass(DragonsbaneFrog.class, new AABB(plinth).inflate(0.5));
        if (!valid) {
            for (DragonsbaneFrog f : frogs) {
                f.discard();
            }
            return;
        }
        double cx = plinth.getX() + 0.5;
        double cy = plinth.getY();
        double cz = plinth.getZ() + 0.5;
        if (frogs.isEmpty()) {
            DragonsbaneFrog frog = DragonsbaneFrog.type().create(server);
            if (frog != null) {
                frog.snapTo(cx, cy, cz, 180.0F, 0.0F);
                frog.setYBodyRot(180.0F);
                frog.setYHeadRot(180.0F);
                server.addFreshEntity(frog);
            }
        } else {
            DragonsbaneFrog frog = frogs.get(0);
            frog.snapTo(cx, cy, cz, frog.getYRot(), 0.0F);
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
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, this.items);
        this.summonTicks = input.getIntOr("SummonTicks", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("SummonTicks", summonTicks);
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
