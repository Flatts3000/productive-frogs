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
    void perCatalystKeysSerializeToTheirIds() {
        assertEquals("count_catalyst", ConfigEnabledCondition.Key.COUNT_CATALYST.getSerializedName());
        assertEquals("speed_catalyst", ConfigEnabledCondition.Key.SPEED_CATALYST.getSerializedName());
        assertEquals("quantity_catalyst", ConfigEnabledCondition.Key.QUANTITY_CATALYST.getSerializedName());
        assertEquals("infinite_catalyst", ConfigEnabledCondition.Key.INFINITE_CATALYST.getSerializedName());
    }

    @Test
    void codecRoundTripsPerCatalystKeys() {
        assertSame(ConfigEnabledCondition.Key.COUNT_CATALYST,
            ConfigEnabledCondition.Key.CODEC.byName("count_catalyst"));
        assertSame(ConfigEnabledCondition.Key.SPEED_CATALYST,
            ConfigEnabledCondition.Key.CODEC.byName("speed_catalyst"));
        assertSame(ConfigEnabledCondition.Key.QUANTITY_CATALYST,
            ConfigEnabledCondition.Key.CODEC.byName("quantity_catalyst"));
        assertSame(ConfigEnabledCondition.Key.INFINITE_CATALYST,
            ConfigEnabledCondition.Key.CODEC.byName("infinite_catalyst"));
    }

    @Test
    void frogNetKeyRoundTrips() {
        assertEquals("frog_net", ConfigEnabledCondition.Key.FROG_NET.getSerializedName());
        assertSame(ConfigEnabledCondition.Key.FROG_NET,
            ConfigEnabledCondition.Key.CODEC.byName("frog_net"));
    }

    @Test
    void frogLegsKeyRoundTrips() {
        assertEquals("frog_legs", ConfigEnabledCondition.Key.FROG_LEGS.getSerializedName());
        assertSame(ConfigEnabledCondition.Key.FROG_LEGS,
            ConfigEnabledCondition.Key.CODEC.byName("frog_legs"));
    }

    @Test
    void codecRejectsUnknownKey() {
        assertNull(ConfigEnabledCondition.Key.CODEC.byName("does_not_exist"));
    }
}
