package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.client.MobColors;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Mob Slurry bucket tint (#281 Phase 3, maintainer ruling: slurry is coloured
 * for the mob it is for). Reads the {@code SLURRIED_ENTITY} component and
 * resolves the mob's colour via {@link MobColors} (spawn-egg sprite average);
 * an unstamped bucket or an unresolvable mob falls back to the murky ender
 * purple so the contents layer stays visible. Bound to the contents layer of
 * the {@code mob_slurry_bucket} item model.
 */
public record SlurriedEntityTint() implements ItemTintSource {

    public static final SlurriedEntityTint INSTANCE = new SlurriedEntityTint();
    public static final MapCodec<SlurriedEntityTint> CODEC = MapCodec.unit(INSTANCE);

    /** The fallback slurry colour (also the fluid's base tint). */
    public static final int FALLBACK_RGB = 0x9C6BC7;

    @Override
    public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        Identifier entityId = stack.get(PFDataComponents.SLURRIED_ENTITY.get());
        if (entityId != null) {
            int argb = MobColors.colorFor(entityId);
            if (argb != -1) {
                return argb;
            }
        }
        return Tints.opaque(FALLBACK_RGB);
    }

    @Override
    public MapCodec<SlurriedEntityTint> type() {
        return CODEC;
    }
}
