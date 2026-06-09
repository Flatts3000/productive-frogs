package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A reusable tool that catches a frog into the item and releases it elsewhere, so
 * a bred-up Resource Frog can be relocated (or a Terrarium restocked) without
 * leashing or killing it (issue #205).
 *
 * <p>Modelled directly on Productive Bees' Bee Cage (the
 * {@code SturdyBeeCage} reusable variant): a plain {@link Item} that stores the
 * captured entity as a {@link net.minecraft.nbt.CompoundTag} in the vanilla
 * {@link DataComponents#CUSTOM_DATA} component, catches via
 * {@link #interactLivingEntity} and releases via {@link #useOn}. The whole
 * entity is serialized with {@link Entity#saveWithoutId}, so for a Resource Frog
 * the category, bred Appetite/Bounty/Reach stats, persistence, health, and a
 * custom name all survive the round trip - no hand-picked field list to keep
 * current - and any other captured frog comes back identical too.
 *
 * <p>Catches <b>any</b> frog, vanilla or modded: anything that is a vanilla
 * {@link Frog} (covers the vanilla frog, the Resource Frog, and any frog mob that
 * subclasses it) or whose entity type is in the {@code productivefrogs:frogs}
 * entity-type tag (the data-driven hook a pack adds a non-subclass modded frog
 * to). Non-frogs are left alone. A loaded net renders "filled" via the
 * {@code productivefrogs:filled} item-model property (registered in
 * {@code PFClientEvents}).
 */
public class FrogNetItem extends Item {

    /** NBT key for the captured entity's type id (mirrors the bee cage's "entity"). */
    private static final String TAG_ENTITY = "entity";
    /** NBT key for the captured frog's resolved display name, for the item name suffix. */
    private static final String TAG_NAME = "name";

    /**
     * Entity types this net may catch beyond vanilla {@link Frog} subclasses -
     * the {@code productivefrogs:frogs} tag (vanilla frog + Resource Frog by
     * default; a pack adds modded frogs that don't subclass {@code Frog}).
     */
    public static final TagKey<EntityType<?>> CATCHABLE = TagKey.create(
        Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "frogs"));

    public FrogNetItem(Properties properties) {
        super(properties);
    }

    /** Whether this net is allowed to catch the target - any vanilla/modded frog. */
    public static boolean isCatchable(Entity target) {
        return target instanceof Frog || target.getType().is(CATCHABLE);
    }

    /** Whether the stack holds a captured frog (CUSTOM_DATA present and carrying an entity id). */
    public static boolean isFilled(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof FrogNetItem)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().contains(TAG_ENTITY);
    }

    /**
     * Serialize a Resource Frog into a net stack: its type id, a resolved name for
     * the item-name suffix, and the full entity NBT via {@link Entity#saveWithoutId}.
     * The stored UUID is dropped so a released frog always gets a fresh identity
     * (no duplicate-UUID risk if a filled net is creative-copied).
     */
    public static void captureEntity(Entity target, ItemStack netStack) {
        CompoundTag nbt = new CompoundTag();
        target.saveWithoutId(nbt);
        nbt.remove("UUID");
        nbt.putString(TAG_ENTITY, EntityType.getKey(target.getType()).toString());
        nbt.putString(TAG_NAME, target.getName().getString());
        netStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    /**
     * Rebuild the captured frog from a filled net (entity created from the stored
     * type id, then {@link Entity#load}ed with the saved NBT), or {@code null} if
     * the stack is empty or its stored type can't be created. Does not place it -
     * the caller positions and adds it to the level.
     */
    @Nullable
    public static Entity entityFromStack(ItemStack netStack, Level level) {
        CustomData data = netStack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        EntityType<?> type = EntityType.byString(tag.getString(TAG_ENTITY)).orElse(null);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            return null;
        }
        entity.load(tag);
        return entity;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        // Off-switch (#205 / #196 config-coverage): a disabled net never catches.
        if (!PFConfig.frogNetEnabled()) {
            return InteractionResult.PASS;
        }
        // Any living frog (vanilla or modded); an already-loaded net is inert
        // until released.
        if (isFilled(stack) || !target.isAlive() || !isCatchable(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            player.swing(hand);
            return InteractionResult.SUCCESS;
        }

        // Build a fresh filled net and put it back in hand rather than mutating the
        // held stack in place - setItemInHand flags the slot so the loaded net
        // reliably resyncs to the client (an in-place component change could leave
        // the client showing an empty net while the frog is already gone). The net
        // stacks to 1, so the copy carries the single net.
        ItemStack filled = stack.copy();
        captureEntity(target, filled);
        player.setItemInHand(hand, filled);
        target.discard();
        player.swing(hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        if (level.isClientSide() || !PFConfig.frogNetEnabled() || !isFilled(stack)) {
            return InteractionResult.FAIL;
        }
        Entity frog = entityFromStack(stack, level);
        if (frog == null) {
            return InteractionResult.FAIL;
        }
        // Spawn in the block adjacent to the clicked face, centred on the cell.
        BlockPos spawn = context.getClickedPos().relative(context.getClickedFace());
        frog.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
            frog.getYRot(), frog.getXRot());
        level.addFreshEntity(frog);
        // Reusable: clear the capture and leave the empty net in hand (the Sturdy
        // Bee Cage release path, not the single-use basic cage).
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
        String name = data == null ? "" : data.copyTag().getString(TAG_NAME);
        return Component.translatable(this.getDescriptionId())
            .append(Component.literal(" (" + name + ")"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!isFilled(stack)) {
            tooltip.add(Component.translatable("productivefrogs.frog_net.empty")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        // Bred stats live on the frog NBT (ResourceFrog.addAdditionalSaveData);
        // surface them so the player can read a caught frog without releasing it.
        if (tag.contains("Appetite") || tag.contains("Bounty") || tag.contains("Reach")) {
            tooltip.add(Component.translatable("productivefrogs.frog_net.stats",
                    tag.getInt("Appetite"), tag.getInt("Bounty"), tag.getInt("Reach"))
                .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("productivefrogs.frog_net.release")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
