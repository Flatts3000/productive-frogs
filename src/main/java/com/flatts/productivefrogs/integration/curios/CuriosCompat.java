package com.flatts.productivefrogs.integration.curios;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.data.StoredEffect;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * Curios integration for Brewed Froglights (#169). Loaded ONLY when Curios is
 * present: {@code PFModBusEvents.onRegisterCapabilities} calls
 * {@link #registerCapabilities} behind a {@code ModList.isLoaded("curios")}
 * guard, so this class - and the {@code top.theillusivec4.curios.*} types it
 * references - never classload on a Curios-less pack (the Jade/JEI soft-dep
 * posture; Curios is {@code compileOnly} + a {@code run/mods} drop-in, never
 * bundled).
 *
 * <p>A brewed Froglight worn in the mod's dedicated {@code froglight} curio slot
 * self-buffs the wearer, identical to the held-in-hand path
 * ({@link com.flatts.productivefrogs.content.item.ConfigurableFroglightItem#inventoryTick})
 * but for the worn location. The slot is single-size (one Froglight at a time)
 * and accepts only Froglights - the {@code curios:tag} validator on
 * {@code data/productivefrogs/curios/slots/froglight.json} keys off the
 * {@code curios:froglight} item tag. The worn stack is in neither hand, so the
 * held path never double-applies.
 */
public final class CuriosCompat {

    private CuriosCompat() {
        // utility class
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
            CuriosCapability.ITEM,
            (stack, ctx) -> new BrewedFroglightCurio(stack),
            PFItems.CONFIGURABLE_FROGLIGHT.get());
    }

    /**
     * Register the {@code productivefrogs:brewed} slot validator and use it on
     * the froglight slot (not {@code curios:tag}). The tag validator gates by
     * ITEM, so it can't tell a brewed Froglight from a plain one - every
     * Froglight matched, and JEI (which reads slot validity via
     * {@code isStackValid}, never {@code canEquip}) showed them all as
     * equippable. A component-aware predicate fixes both the GUI and JEI: only
     * a stack actually carrying {@code STORED_EFFECT} is valid for the slot.
     * Call once during common setup ({@code enqueueWork}); registered on both
     * dist so the client GUI and JEI see it too.
     */
    public static void registerPredicate() {
        CuriosApi.registerCurioPredicate(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "brewed"),
            slotResult -> slotResult.stack().has(PFDataComponents.STORED_EFFECT.get()));
    }

    /** Slot id of the dedicated Froglight curio slot (matches the slot JSON filename). */
    public static final String SLOT = "froglight";

    /** Per-stack {@link ICurio}: re-applies the captured effect to the wearer while worn + enabled. */
    private record BrewedFroglightCurio(ItemStack stack) implements ICurio {

        @Override
        public ItemStack getStack() {
            return stack;
        }

        /**
         * Pin the Froglight to its own slot: Curios' generic {@code curio} slot
         * has no validator and would otherwise accept any curio, so without this
         * a Froglight could sit in a generic slot instead of the dedicated one.
         * Restrict equipping to the {@code froglight} slot only.
         */
        @Override
        public boolean canEquip(SlotContext context) {
            return SLOT.equals(context.identifier());
        }

        @Override
        public void curioTick(SlotContext context) {
            LivingEntity wearer = context.entity();
            if (wearer == null || wearer.level().isClientSide()) {
                return;
            }
            StoredEffect stored = stack.get(PFDataComponents.STORED_EFFECT.get());
            if (stored == null || !stored.enabled()) {
                return;
            }
            if (wearer.level().getGameTime() % ConfigurableFroglightBlockEntity.AURA_PULSE_TICKS == 0L) {
                wearer.addEffect(stored.toInstance());
            }
        }
    }
}
