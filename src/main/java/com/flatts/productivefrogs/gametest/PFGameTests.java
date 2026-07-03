package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world GameTest registrar for Productive Frogs. Each test is a headless
 * scenario run inside a real server via {@code ./gradlew runGameTestServer}; it
 * gets the shared 5x5x5 plot ({@code data/<modid>/structure/empty_5x5x5.nbt}) and
 * asserts behaviour via {@link GameTestHelper}.
 *
 * <p><b>26.1 registration (R-6 port).</b> The 1.21.1 {@code @GameTestHolder} +
 * {@code @GameTest} annotation/reflection form is gone. A test is now two halves:
 * its body (a {@code Consumer<GameTestHelper>} registered into
 * {@link Registries#TEST_FUNCTION}) and its metadata (a {@link TestData}, carried
 * by a {@link FunctionGameTestInstance} registered via {@link RegisterGameTestsEvent}).
 * {@link #test(String, int, Consumer)} hides that two-step, so each domain class
 * (e.g. {@link SpeciesCategoryTests}) is just bodies + one registration line each.
 * This keeps the suite split across small per-feature files instead of one monolith.
 *
 * <p>{@code manualOnly = false} + {@code required = true} keep every test in the
 * automated CI batch - flip either and the required gameTest job silently skips it.
 */
public final class PFGameTests {

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(Registries.TEST_FUNCTION, ProductiveFrogs.MOD_ID);

    /** The default plot most tests run on. */
    private static final String DEFAULT_STRUCTURE = "empty_5x5x5";

    private record Spec(ResourceKey<Consumer<GameTestHelper>> fn, Identifier structure, Rotation rotation, int maxTicks) {}

    private static final List<Spec> SPECS = new ArrayList<>();

    private PFGameTests() {
    }

    /**
     * Register one test on the default {@code empty_5x5x5} plot. Its body is added to
     * {@link Registries#TEST_FUNCTION} and paired with a {@link FunctionGameTestInstance}
     * at {@link RegisterGameTestsEvent} time. {@code name} must be lower_snake_case.
     */
    static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        test(name, DEFAULT_STRUCTURE, Rotation.NONE, maxTicks, body);
    }

    /** Register a test on a specific structure (e.g. {@code empty_9x9x9}, {@code dragon_altar}). */
    static void test(String name, String structure, int maxTicks, Consumer<GameTestHelper> body) {
        test(name, structure, Rotation.NONE, maxTicks, body);
    }

    /** Register a test on a specific structure with a structure rotation (legacy {@code rotationSteps}). */
    static void test(String name, String structure, Rotation rotation, int maxTicks, Consumer<GameTestHelper> body) {
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder =
            FUNCTIONS.register(name, () -> body);
        SPECS.add(new Spec(holder.getKey(),
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, structure), rotation, maxTicks));
    }

    /** Wire up from the {@code ProductiveFrogs} constructor. */
    public static void register(IEventBus modEventBus) {
        // Each domain class contributes its tests via PFGameTests.test(...). Add a
        // line here when introducing a new domain file.
        SpeciesCategoryTests.register();
        InfusionDiscoveryTests.register();
        ConfigSuiteTests.register();
        EggTadpoleFrogTests.register();
        TongueDropsFroglightTests.register();
        SlimeBucketTests.register();
        MilkSourceTests.register();
        ApplianceTests.register();
        CrucibleMoldTests.register();
        BossAltarTests.register();
        BreedingFrogAiTests.register();
        PredatorBreedingTests.register();
        TerrariumTests.register();
        EquivalenceLaneTests.register();

        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(PFGameTests::onRegisterGameTests);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        // One shared empty environment (no weather/time/gamerule overrides).
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "default"));
        for (Spec spec : SPECS) {
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                env, spec.structure(), spec.maxTicks(),
                0,            // setupTicks
                true,         // required (run in CI)
                spec.rotation(),
                false,        // manualOnly (false -> auto CI batch)
                1,            // maxAttempts
                1,            // requiredSuccesses
                false,        // skyAccess
                1);           // padding between batched instances
            event.registerTest(spec.fn().identifier(), new FunctionGameTestInstance(spec.fn(), data));
        }
    }
}
