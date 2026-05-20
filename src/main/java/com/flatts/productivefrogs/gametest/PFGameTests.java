package com.flatts.productivefrogs.gametest;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * In-world GameTests for Productive Frogs. Each test is a headless scenario
 * that runs inside a real Minecraft server via {@code ./gradlew runGameTestServer};
 * each is given a small plot via the referenced structure NBT and asserts
 * behavior via {@link GameTestHelper}.
 *
 * <p>Registration in MC 1.21.11 uses the registry-based system (the older
 * {@code @GameTestHolder}/{@code @GameTest} annotations were removed):
 * <ol>
 *   <li><b>Test functions</b> register via a standard {@link DeferredRegister}
 *       on {@link BuiltInRegistries#TEST_FUNCTION}. NeoForge unfreezes that
 *       registry after vanilla bootstrap and fires {@code RegisterEvent} for
 *       it on the mod bus, identical lifecycle to Blocks/Items.</li>
 *   <li><b>Test instances</b> are registered through {@link RegisterGameTestsEvent},
 *       which pairs a function holder with a structure + timing metadata.
 *       That event fires only on game-test-enabled boots, and per
 *       NeoForge issue patterns it can fire twice — guard via
 *       {@link #testInstancesRegistered}.</li>
 *   <li><b>Structure NBT</b> lives at {@code data/<modid>/structure/<name>.nbt}
 *       — singular {@code structure/}, same as 1.21.x tag dirs went singular.</li>
 * </ol>
 *
 * <p>Pattern lifted from the working reference at
 * <a href="https://github.com/ksoichiro/JustBlockShapes/blob/main/neoforge/1.21.11/src/main/java/com/justblockshapes/neoforge/gametest/JustBlockShapesGameTestNeoForge.java">ksoichiro/JustBlockShapes</a>.
 */
public final class PFGameTests {

    private static final Identifier EMPTY_STRUCTURE =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "empty_5x5x5");

    public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, ProductiveFrogs.MOD_ID);

    private static final List<RegisteredTest> REGISTERED_TESTS = new ArrayList<>();

    /** {@link RegisterGameTestsEvent} can fire more than once per JVM. */
    private static boolean testInstancesRegistered = false;

    private record RegisteredTest(
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder,
        int timeoutTicks
    ) {}

    static {
        registerTest("primed_egg_breaks_when_water_removed",
            PFGameTests::primedEggBreaksWhenWaterRemoved, 100);
    }

    private PFGameTests() {
        // static-only
    }

    private static void registerTest(String name, Consumer<GameTestHelper> test, int timeoutTicks) {
        DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>> holder =
            TEST_FUNCTIONS.register(name, () -> test);
        REGISTERED_TESTS.add(new RegisteredTest(holder, timeoutTicks));
    }

    /** Wire up via the mod event bus from {@code ProductiveFrogs} constructor. */
    public static void register(IEventBus modEventBus) {
        TEST_FUNCTIONS.register(modEventBus);
        modEventBus.addListener(PFGameTests::onRegisterGameTests);
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        if (testInstancesRegistered) {
            return;
        }
        testInstancesRegistered = true;

        Holder<TestEnvironmentDefinition> defaultEnv = event.registerEnvironment(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "default")
        );

        for (RegisteredTest test : REGISTERED_TESTS) {
            if (!test.holder().isBound()) {
                continue;
            }
            TestData<Holder<TestEnvironmentDefinition>> testData = new TestData<>(
                defaultEnv,
                EMPTY_STRUCTURE,
                test.timeoutTicks(),
                0,      // setupTicks
                true    // required
            );
            event.registerTest(
                test.holder().getId(),
                new FunctionGameTestInstance(test.holder().getKey(), testData)
            );
        }
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    /**
     * Place a Primed Frog Egg on a water source, then remove the water. The
     * block's {@code updateShape} runs {@code canSurvive}, sees no water below,
     * and replaces itself with air. Verifies the survive-on-water rule.
     */
    private static void primedEggBreaksWhenWaterRemoved(GameTestHelper helper) {
        BlockPos eggPos = new BlockPos(2, 2, 2);
        helper.setBlock(eggPos.below(), Blocks.WATER);
        helper.setBlock(eggPos, PFBlocks.primedEgg(Category.METALLIC));
        helper.assertBlockPresent(PFBlocks.primedEgg(Category.METALLIC), eggPos);

        // Knock out the water — the egg should detect via neighbor update
        // (PrimedFrogEggBlock.updateShape → canSurvive false → air) and disappear.
        helper.setBlock(eggPos.below(), Blocks.AIR);
        helper.succeedWhen(() -> helper.assertBlockNotPresent(PFBlocks.primedEgg(Category.METALLIC), eggPos));
    }
}
