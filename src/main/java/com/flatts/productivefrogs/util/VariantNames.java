package com.flatts.productivefrogs.util;

import java.util.Locale;
import net.minecraft.resources.Identifier;

/**
 * Display-name derivation for variant ids, so component-driven items (Slime Milk
 * bucket, Configurable Froglight, Resource Slime spawn egg) render a readable
 * name for a datapack-added variant that ships no client lang file.
 *
 * <p>Built-in variants still get explicit {@code en_us.json} keys that win via
 * {@link net.minecraft.network.chat.Component#translatableWithFallback}; the
 * title-cased fallback only shows for variants without a lang entry. This is what
 * lets a variant be added by datapack alone (lang is a client asset, datapacks
 * are server data, so a pack-added variant cannot ship its own lang).
 */
public final class VariantNames {

    private VariantNames() {
        // static utility
    }

    /**
     * Title-cased fallback for a variant id's path: {@code iron} -> "Iron",
     * {@code ender_pearl} -> "Ender Pearl", {@code glow_ink_sac} -> "Glow Ink Sac".
     */
    public static String titleCase(Identifier variantId) {
        String[] words = variantId.getPath().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }
}
