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
        // Per-catalyst recipe gates (#201): each ANDs the catalysts master so the
        // master still drops all four, and the child adds individual granularity.
        // This is the recipe-load (fail-closed) copy of the same master-AND-child
        // relationship that PFConfig.catalyst*Enabled() expresses for the runtime
        // (fail-open) paths - keep the two in sync if the relationship changes.
        COUNT_CATALYST("count_catalyst") {
            @Override
            boolean read() {
                return PFConfig.MILK_CATALYSTS_ENABLED.get() && PFConfig.CATALYST_COUNT_ENABLED.get();
            }
        },
        SPEED_CATALYST("speed_catalyst") {
            @Override
            boolean read() {
                return PFConfig.MILK_CATALYSTS_ENABLED.get() && PFConfig.CATALYST_SPEED_ENABLED.get();
            }
        },
        QUANTITY_CATALYST("quantity_catalyst") {
            @Override
            boolean read() {
                return PFConfig.MILK_CATALYSTS_ENABLED.get() && PFConfig.CATALYST_QUANTITY_ENABLED.get();
            }
        },
        INFINITE_CATALYST("infinite_catalyst") {
            @Override
            boolean read() {
                return PFConfig.MILK_CATALYSTS_ENABLED.get() && PFConfig.CATALYST_INFINITE_ENABLED.get();
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
        },
        FROG_NET("frog_net") {
            @Override
            boolean read() {
                return PFConfig.FROG_NET_ENABLED.get();
            }
        },
        // The predation system master (#281): gates the Ender Net, Slurry Press,
        // and Mob Slurry Basin recipes (Phase 3).
        PREDATORS("predators") {
            @Override
            boolean read() {
                return PFConfig.PREDATORS_ENABLED.get();
            }
        },
        FROG_LEGS("frog_legs") {
            @Override
            boolean read() {
                return PFConfig.FROG_LEGS_ENABLED.get();
            }
        },
        FROGLIGHT_WEAPON("froglight_weapon") {
            @Override
            boolean read() {
                return PFConfig.FROGLIGHT_WEAPON_ENABLED.get();
            }
        },
        // Equivalence lane master (#253): gates the Alembic + Distiller recipes.
        // Routed through equivalenceEnabled() (not the raw flag) so the dev force-on
        // (-Dproductivefrogs.equivalence) makes the machines craftable in dev too.
        EQUIVALENCE("equivalence") {
            @Override
            boolean read() {
                return PFConfig.equivalenceEnabled();
            }
        },
        // Boss-tier master (#200): gates the catalyst-altar block recipes and the
        // boss Froglight smelt-backs. The variant suppression half rides
        // PFConfig.variantEnabled, not a recipe condition.
        BOSS("boss") {
            @Override
            boolean read() {
                return PFConfig.BOSS_ENABLED.get();
            }
        },
        // Frog stats master (#202): gates the Sweetslime recipe. The behavioral
        // suppression (baseline stats, no breeding, no Jade lines) is in Java.
        FROG_STATS("frog_stats") {
            @Override
            boolean read() {
                return PFConfig.FROG_STATS_ENABLED.get();
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
