package com.flatts.productivefrogs.data.condition;

import com.flatts.productivefrogs.PFConfig;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * A datapack {@link ICondition} that passes only when a named Productive Frogs
 * config boolean is enabled. NeoForge ships {@code mod_loaded}, {@code item_exists},
 * {@code tag_empty}, etc., but nothing that reads a mod config value - so recipes
 * (and any other condition-gated JSON) that should appear only when a feature is
 * turned on need this.
 *
 * <p>Used by the Spawnery's crafting recipe, which carries
 * {@code {"type":"productivefrogs:config_enabled","config":"spawnery"}}. With
 * {@code spawnery.enabled=false} (the default) the condition fails, the recipe is
 * dropped at datapack load, and JEI shows no recipe for the block. Flipping the
 * config + reloading the world brings it back.
 *
 * <p>Conditions are evaluated server-side during datapack load, which happens
 * after COMMON config loads, so {@link PFConfig#SPEC} is normally available by
 * then. It still guards on {@link net.neoforged.neoforge.common.ModConfigSpec#isLoaded()}
 * and fails closed (disabled) if the spec somehow is not yet loaded, rather than
 * throwing the {@code IllegalStateException} that a raw {@code .get()} would.
 *
 * <p>The {@code config} key is a small closed set ({@link Key}) rather than a free
 * string so a typo in a JSON surfaces at decode time and there is one obvious place
 * to wire a new gated feature.
 */
public record ConfigEnabledCondition(Key config) implements ICondition {

    public static final MapCodec<ConfigEnabledCondition> CODEC =
        RecordCodecBuilder.mapCodec(instance -> instance.group(
            Key.CODEC.fieldOf("config").forGetter(ConfigEnabledCondition::config)
        ).apply(instance, ConfigEnabledCondition::new));

    @Override
    public boolean test(IContext context) {
        return config.isEnabled();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    /**
     * The set of config booleans that can gate JSON. Each maps to a
     * {@link PFConfig} flag; {@link #isEnabled()} reads it defensively.
     */
    public enum Key implements StringRepresentable {
        SPAWNERY("spawnery") {
            @Override
            boolean read() {
                return PFConfig.SPAWNERY_ENABLED.get();
            }
        },
        MILK_CATALYSTS("milk_catalysts") {
            @Override
            boolean read() {
                return PFConfig.MILK_CATALYSTS_ENABLED.get();
            }
        },
        SLIME_MILKER("slime_milker") {
            @Override
            boolean read() {
                return PFConfig.SLIME_MILKER_ENABLED.get();
            }
        },
        SLIME_CHURN("slime_churn") {
            @Override
            boolean read() {
                return PFConfig.SLIME_CHURN_ENABLED.get();
            }
        },
        CRUCIBLE("crucible") {
            @Override
            boolean read() {
                return PFConfig.CRUCIBLE_ENABLED.get();
            }
        },
        CASTING_MOLD("casting_mold") {
            @Override
            boolean read() {
                return PFConfig.CASTING_MOLD_ENABLED.get();
            }
        };

        public static final StringRepresentable.EnumCodec<Key> CODEC =
            StringRepresentable.fromEnum(Key::values);

        private final String id;

        Key(String id) {
            this.id = id;
        }

        /** Read the underlying flag; only valid once {@link PFConfig#SPEC} is loaded. */
        abstract boolean read();

        boolean isEnabled() {
            return PFConfig.SPEC.isLoaded() && read();
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }
}
