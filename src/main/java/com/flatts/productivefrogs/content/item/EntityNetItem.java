package com.flatts.productivefrogs.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The shared catch-and-release net mechanic (#205 Frog Net, #281 Ender Net):
 * a reusable tool that serializes a living entity into the item's
 * {@link DataComponents#CUSTOM_DATA} and releases it elsewhere. Extracted from
 * the Frog Net when the Ender Net arrived (predation Phase 3) - the capture,
 * release, and round-trip logic is identical; subclasses supply only WHAT they
 * may catch ({@link #canCatch}) and their config gate ({@link #enabled()}).
 *
 * <p>Modelled on Productive Bees' reusable Bee Cage: the whole entity is
 * serialized with {@link Entity#saveWithoutId} (the #210 lesson - no
 * hand-picked field list to keep current), so category/kind, bred stats,
 * health, and a custom name all survive the round trip for ANY captured
 * entity. The stored UUID is dropped so a released entity always gets a fresh
 * identity (no duplicate-UUID risk if a filled net is creative-copied).
 *
 * <p>A loaded net renders "filled" via the data-driven
 * {@code minecraft:has_component} item-model condition on
 * {@code minecraft:custom_data} - no client Java.
 */
public abstract class EntityNetItem extends Item {

    /** NBT key for the captured entity's type id (mirrors the bee cage's "entity"). */
    protected static final String TAG_ENTITY = "entity";
    /** NBT key for the captured entity's resolved display name, for the item name suffix. */
    protected static final String TAG_NAME = "name";

    protected EntityNetItem(Properties properties) {
        super(properties);
    }

    /** Whether this net is allowed to catch (and, defensively, release) the target. */
    protected abstract boolean canCatch(Entity target);

    /** Config gate: a disabled net neither catches nor releases. */
    protected abstract boolean enabled();

    /** Whether the stack holds a captured entity (CUSTOM_DATA present and carrying an entity id). */
    public static boolean isFilled(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof EntityNetItem)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(TAG_ENTITY);
    }

    /**
     * Serialize an entity into a net stack: its type id, a resolved name for
     * the item-name suffix, and the full entity NBT via {@link Entity#saveWithoutId}.
     */
    public static void captureEntity(Entity target, ItemStack netStack) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, target.registryAccess());
        target.saveWithoutId(output);
        CompoundTag nbt = output.buildResult();
        nbt.remove("UUID");
        nbt.putString(TAG_ENTITY, EntityType.getKey(target.getType()).toString());
        nbt.putString(TAG_NAME, target.getName().getString());
        netStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    /** The captured entity's type, or null when the stack is empty / carries an unknown id. */
    @Nullable
    public static EntityType<?> capturedType(ItemStack netStack) {
        CustomData data = netStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return EntityType.byString(data.copyTag().getStringOr(TAG_ENTITY, "")).orElse(null);
    }

    /**
     * Rebuild the captured entity from a filled net (entity created from the
     * stored type id, then {@link Entity#load}ed with the saved NBT), or
     * {@code null} if the stack is empty or its stored type can't be created.
     * Does not place it - the caller positions and adds it to the level.
     *
     * <p>Defense in depth: the catch path gates on {@link #canCatch}, but
     * CUSTOM_DATA could be written by an NBT editor or other code; re-checking
     * here stops a tampered net from releasing something this net type may not
     * carry. Serialized passengers are dropped - they weren't part of the
     * catch, and a crafted Passengers tree could drive deep recursion in load().
     */
    @Nullable
    public Entity entityFromStack(ItemStack netStack, Level level) {
        CustomData data = netStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return rebuildCaptured(data.copyTag(), level, this::canCatch);
    }

    /**
     * THE whole-entity rebuild for the net NBT dialect - the single owner of
     * the type-from-tag / create / gate / strip-Passengers / load sequence
     * (review finding: AltarApexDock.releaseFrog carried a hand copy that had
     * already drifted). {@code gate} runs on the freshly-created (pre-load)
     * entity; a refused or uncreatable entity returns null with nothing added
     * to the world. Serialized passengers are dropped - they weren't part of
     * the catch, and a crafted Passengers tree could drive deep recursion.
     */
    @Nullable
    public static Entity rebuildCaptured(CompoundTag tag, Level level,
            java.util.function.Predicate<Entity> gate) {
        EntityType<?> type = EntityType.byString(tag.getStringOr(TAG_ENTITY, "")).orElse(null);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity == null) {
            return null;
        }
        if (!gate.test(entity)) {
            entity.discard();
            return null;
        }
        tag.remove("Passengers");
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return entity;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!enabled()) {
            return InteractionResult.PASS;
        }
        // An already-loaded net is inert until released.
        if (isFilled(stack) || !target.isAlive() || !canCatch(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            player.swing(hand);
            return InteractionResult.SUCCESS;
        }

        // Build a fresh filled net and put it back in hand rather than mutating the
        // held stack in place - setItemInHand flags the slot so the loaded net
        // reliably resyncs to the client. copyWithCount(1) guards against a
        // creative-held count > 1 leaking into the filled net.
        ItemStack filled = stack.copyWithCount(1);
        captureEntity(target, filled);
        player.setItemInHand(hand, filled);
        target.discard();
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!enabled() || !isFilled(stack)) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        // Apex install (#281 Phase 4): clicking a boss-altar Hatch with a filled
        // net INSTALLS instead of releasing. This lives here, not in the block:
        // sneaking with an item skips block interaction entirely on this MC line
        // (isSecondaryUseActive), so the documented shift-right-click gesture only
        // ever reaches the item's useOn - the original block-side branch was dead
        // code and the net quietly spilled the frog beside the altar instead.
        if (level.getBlockEntity(context.getClickedPos())
                instanceof com.flatts.productivefrogs.content.block.entity.BossAltarHatchBlockEntity hatch) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (hatch.dock().tryInstall(stack)) {
                stack.remove(DataComponents.CUSTOM_DATA);
                level.playSound(null, context.getClickedPos(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
            } else {
                level.playSound(null, context.getClickedPos(), net.minecraft.sounds.SoundEvents.VILLAGER_NO,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return InteractionResult.SUCCESS; // never spill the frog onto an altar hatch
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Entity released = entityFromStack(stack, level);
        if (released == null) {
            return InteractionResult.FAIL;
        }
        BlockPos spawn = context.getClickedPos().relative(context.getClickedFace());
        released.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            released.getYRot(), released.getXRot());
        level.addFreshEntity(released);
        // Reusable: clear the capture and leave the empty net in hand.
        stack.remove(DataComponents.CUSTOM_DATA);
        Player player = context.getPlayer();
        if (player != null) {
            player.swing(context.getHand());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!isFilled(stack)) {
            return Component.translatable(this.getDescriptionId());
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String name = data == null ? "" : data.copyTag().getStringOr(TAG_NAME, "");
        return Component.translatable(this.getDescriptionId())
            .append(Component.literal(" (" + name + ")"));
    }
}
