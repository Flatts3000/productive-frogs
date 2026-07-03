package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * NeoForge data attachments owned by Productive Frogs.
 *
 * <p>{@link #PRINCESS_CONVERTING} (#216) is the remaining-ticks countdown for a
 * frog being turned into a villager by a Princess's Kiss. It lives as an
 * attachment rather than entity fields so it works on <b>any</b> frog - vanilla,
 * the Resource Frog, or a modded frog we don't own. It serializes (Codec.INT), so
 * an in-progress conversion survives a save/reload, mirroring the zombie-cure
 * being reload-safe. A frog is "converting" iff {@code hasData} is true; the value
 * is the ticks left.
 */
public final class PFAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ProductiveFrogs.MOD_ID);

    public static final Supplier<AttachmentType<Integer>> PRINCESS_CONVERTING =
        ATTACHMENT_TYPES.register(
            "princess_converting_ticks",
            () -> AttachmentType.builder(() -> 0).serialize(Codec.INT.fieldOf("value")).build());

    /**
     * Teleport lock (#281): a mob spawned by a Mob Slurry Basin carries this so
     * it can't teleport away from the predator farming it - the settled
     * no-enclosures answer for enderman/shulker. {@code PredationTeleportHandler}
     * cancels {@code EntityTeleportEvent.EnderEntity} while it is set. Persisted
     * (Codec.BOOL) so a Basin-spawned teleporter stays locked across reloads;
     * wild mobs never carry it. The Basin (predation Phase 3) is the setter -
     * until it ships, the attachment + handler are the standing mechanism.
     */
    public static final Supplier<AttachmentType<Boolean>> TELEPORT_DISABLED =
        ATTACHMENT_TYPES.register(
            "teleport_disabled",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL.fieldOf("value")).build());

    private PFAttachments() {
        // registry holder
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
