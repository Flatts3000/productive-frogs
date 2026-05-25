package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers the {@code /productivefrogs debug} command tree (alias {@code /pf})
 * that toggles {@link PFDebug} areas live, without a restart. Op-level (permission
 * 2): diagnostic and harmless, but not for non-ops.
 *
 * <pre>
 *   /pf debug                 list every area and its on/off state
 *   /pf debug &lt;area&gt;           show one area's state
 *   /pf debug &lt;area&gt; on|off    toggle one area ("all" toggles every area)
 * </pre>
 *
 * <p>The {@link PFDebug.Area} flags are static fields in the shared JVM. In
 * singleplayer one JVM hosts both sides, so this command toggles render areas
 * the client thread reads as well as gameplay areas. On a dedicated server it
 * meaningfully affects only the server-side gameplay areas; a connected client's
 * render flags are a separate JVM and must be set with the {@code -D} system
 * property at launch. See {@code docs/observability.md} > Control surface.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class PFCommands {

    /** Tab-completion tokens: every area id plus the {@code all} keyword. */
    private static final List<String> AREA_TOKENS = buildAreaTokens();
    private static final List<String> STATE_TOKENS = List.of("on", "off");

    private PFCommands() {
        // event handler, not instantiable
    }

    private static List<String> buildAreaTokens() {
        List<String> tokens = new ArrayList<>();
        tokens.add(PFDebug.ALL);
        for (PFDebug.Area area : PFDebug.Area.values()) {
            tokens.add(area.id());
        }
        return tokens;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Canonical root + short alias. A fresh subtree per root (a Brigadier
        // builder is consumed when built, so it can't be shared between roots).
        event.getDispatcher().register(
            Commands.literal("productivefrogs").requires(src -> src.hasPermission(2)).then(debugSubtree()));
        event.getDispatcher().register(
            Commands.literal("pf").requires(src -> src.hasPermission(2)).then(debugSubtree()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> debugSubtree() {
        return Commands.literal("debug")
            .executes(ctx -> listAreas(ctx.getSource()))
            .then(Commands.argument("area", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(AREA_TOKENS, builder))
                .executes(ctx -> showArea(ctx.getSource(), StringArgumentType.getString(ctx, "area")))
                .then(Commands.argument("state", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(STATE_TOKENS, builder))
                    .executes(PFCommands::setAreaFromContext)));
    }

    private static int listAreas(CommandSourceStack source) {
        StringBuilder sb = new StringBuilder("Productive Frogs debug areas:");
        for (PFDebug.Area area : PFDebug.Area.values()) {
            sb.append("\n  ").append(area.id()).append(": ").append(PFDebug.on(area) ? "on" : "off");
        }
        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int showArea(CommandSourceStack source, String token) {
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(PFDebug.ALL)) {
            return listAreas(source);
        }
        Optional<PFDebug.Area> area = PFDebug.byId(normalized);
        if (area.isEmpty()) {
            source.sendFailure(Component.literal("Unknown debug area: " + token));
            return 0;
        }
        boolean state = PFDebug.on(area.get());
        source.sendSuccess(() -> Component.literal(area.get().id() + ": " + (state ? "on" : "off")), false);
        return 1;
    }

    private static int setAreaFromContext(CommandContext<CommandSourceStack> ctx) {
        return setArea(
            ctx.getSource(),
            StringArgumentType.getString(ctx, "area"),
            StringArgumentType.getString(ctx, "state"));
    }

    private static int setArea(CommandSourceStack source, String token, String stateToken) {
        Boolean enabled = parseState(stateToken);
        if (enabled == null) {
            source.sendFailure(Component.literal("Expected on or off, got: " + stateToken));
            return 0;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals(PFDebug.ALL)) {
            PFDebug.setAll(enabled);
            final boolean shown = enabled;
            source.sendSuccess(() -> Component.literal("All debug areas " + (shown ? "on" : "off")), true);
            return 1;
        }
        Optional<PFDebug.Area> area = PFDebug.byId(normalized);
        if (area.isEmpty()) {
            source.sendFailure(Component.literal("Unknown debug area: " + token));
            return 0;
        }
        PFDebug.setEnabled(area.get(), enabled);
        final boolean shown = enabled;
        source.sendSuccess(
            () -> Component.literal("Debug area " + area.get().id() + " " + (shown ? "on" : "off")), true);
        return 1;
    }

    private static Boolean parseState(String token) {
        return switch (token.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "enable", "enabled", "1" -> Boolean.TRUE;
            case "off", "false", "disable", "disabled", "0" -> Boolean.FALSE;
            default -> null;
        };
    }
}
