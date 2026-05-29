package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.flatts.productivefrogs.data.condition.ConfigEnabledCondition;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link ConfigEnabledCondition.Key} - the closed enum mapping
 * a recipe-condition {@code config} string to a PFConfig flag. Pins the
 * serialized name and the codec round-trip so the Spawnery recipe's
 * {@code "config":"spawnery"} keeps resolving and a typo'd key fails loudly at
 * decode time instead of silently disabling a feature.
 *
 * <p>Deliberately does not touch the live config value ({@code isEnabled()}): the
 * config spec isn't loaded in a bare unit-test JVM, which is exactly why the
 * condition guards on {@code SPEC.isLoaded()}. That guard is exercised in-world by
 * the Spawnery GameTests, not here.
 */
class ConfigEnabledConditionTest {

    @Test
    void spawneryKeySerializesToItsId() {
        assertEquals("spawnery", ConfigEnabledCondition.Key.SPAWNERY.getSerializedName());
    }

    @Test
    void codecRoundTripsKnownKey() {
        assertSame(ConfigEnabledCondition.Key.SPAWNERY,
            ConfigEnabledCondition.Key.CODEC.byName("spawnery"));
    }

    @Test
    void milkCatalystsKeySerializesToItsId() {
        assertEquals("milk_catalysts", ConfigEnabledCondition.Key.MILK_CATALYSTS.getSerializedName());
    }

    @Test
    void codecRoundTripsMilkCatalystsKey() {
        assertSame(ConfigEnabledCondition.Key.MILK_CATALYSTS,
            ConfigEnabledCondition.Key.CODEC.byName("milk_catalysts"));
    }

    @Test
    void codecRejectsUnknownKey() {
        assertNull(ConfigEnabledCondition.Key.CODEC.byName("does_not_exist"));
    }
}
