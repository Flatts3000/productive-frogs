# Testing

Productive Frogs ships two layers of automated testing, both running in CI on every push and PR. Each layer covers a different cost/coverage tradeoff.

## Layer 1: JUnit 5 unit tests

**Where:** `src/test/java/`
**Run:** `./gradlew test`
**Wired into:** the `build` CI job (already required by main branch protection).

Two flavors share the same task:

### Pure-logic tests (no Minecraft required)

Plain JUnit. Fast, isolated, runnable in any JVM. Used for codecs, derivations, value-object invariants. Example: `CategoryTest` exercises:

- enum width (6 categories, load-bearing for the design)
- `Category.id()` / `primerTagPath()` / `primedEggItemName()` formats
- `tintArgb()` / `tintRgb()` invariants (alpha bits, agreement of low 24)
- JSON codec round-trip via `JsonOps`
- Stream codec round-trip + byte-budget assertion
- Failure path: unknown id rejected with codec error

### Registry-loaded tests (Minecraft classes available)

Enabled by moddev's JUnit integration via:

```groovy
neoForge {
    unitTest {
        enable()
        testedMod = mods.named("${mod_id}").get()
    }
}
```

These tests boot Minecraft's class loaders and let our `DeferredRegister`s populate `BuiltInRegistries` before the tests run. They cover the silent-failure modes that pure-logic tests can't reach — a registry that wasn't wired to the mod event bus, an ID typo, a Block↔Item bijection that broke after a refactor.

Example: `PFRegistryTest` verifies:

- `productivefrogs:frog_egg` resolves to our `FrogEggItem`
- `productivefrogs:resource_tadpole_bucket` resolves to our bucket
- Each `<category>_frog_egg` block is a `PrimedFrogEggBlock` carrying the matching `Category`
- Each `<category>_frog_egg` BlockItem exists and is a `BlockItem`
- `Block.asItem()` round-trips correctly for every category

### Adding a new JUnit test

1. Drop a `*.java` file under `src/test/java/com/flatts/productivefrogs/...`
2. Use JUnit 5 (`@Test`, `@ParameterizedTest`, `@EnumSource`, etc.)
3. Run `./gradlew test` to verify locally
4. Push — CI runs it as part of `build`

## Layer 2: In-world GameTests

**Where:** `src/main/java/com/flatts/productivefrogs/gametest/PFGameTests.java`
**Structures:** `src/main/resources/data/productivefrogs/structure/*.nbt`
**Run:** `./gradlew runGameTestServer`
**Wired into:** the `gameTest` CI job (independent of `build`, so reports as a separate required check).

GameTests run real headless Minecraft scenarios in scripted plots. They're the right tool for:

- "Primed Frog Egg block breaks when its water support is removed" (canSurvive + updateShape chain) — *already shipped*
- "Primed Frog Egg hatches into N tadpoles of the matching category" (block tick + entity spawn + category propagation)
- "Resource Tadpole grows into Resource Frog of the same category" (`ageUp` override via access transformer)
- "Two same-category Resource Frogs in water + slimeballs lay a Primed Frog Egg of that category" (LayCategoryFrogspawn brain task override)

### What GameTest does NOT cover — visuals are blind

`runGameTestServer` boots a **dedicated server** — no client, no renderer, no shader pipeline. Any bug that lives entirely in the client-side render path is invisible to it. Treat GameTest as a server-state oracle, not a UI oracle. Specifically, GameTest cannot catch:

- **Tint resolution** — wrong `ItemTintSource` registered, wrong layer index, `BlockColor` returning -1, source-alpha mismatch.
- **Texture paths** — missing PNG, typo'd path resolving to the purple-and-black missing-texture cube.
- **UV / model transforms** — fragment sampling the wrong section of an atlas, broken display transforms.
- **Render type** — opaque vs. translucent vs. cutout misassignment, source-alpha collapsing a layered model.
- **Particle / animation** — frog tongue extend animation, slime jiggle, bucket pour sound (sound is server-driven but client-rendered).
- **GUI layout** — Slime Milker's slot positions, progress arrow, tooltip wrapping.
- **Creative-tab ordering and icons**.
- **Language file coverage** — a missing translation key surfaces as the raw `item.productivefrogs.foo` string in tooltips, which is a visual regression invisible to server state.

**Canonical example.** PR #27 shipped a Resource Slime where the outer translucent shell rendered solid gray instead of the category-tinted gradient. Every GameTest passed. Root cause: the outer-layer texture had source-alpha 255 where vanilla expects ~180; the render type respects source alpha so the gradient collapsed into an opaque cube. Server state was identical to a healthy build — only the screen pixels differed. The bug shipped through CI to a playtest.

**Take away:** if you touch any of `client/`, `assets/<modid>/`, `Category.tintArgb`, item-model JSON, block-model JSON, lang files, or particle/sound code, schedule a manual `./gradlew runClient` pass and walk the affected surface before marking the work done. Document the playtest matrix in the PR description so the reviewer knows what was eyeballed.

### Registration pattern (MC 1.21.11)

MC 1.21.11 refactored the GameTest API — the old `@GameTestHolder`/`@GameTest` annotations are gone. The new system has three pieces:

1. **Test function** — a `Consumer<GameTestHelper>` registered in `Registries.TEST_FUNCTION`. NeoForge unfreezes this registry after vanilla bootstrap and exposes it via standard `RegisterEvent` on the mod bus, so a regular `DeferredRegister` works:

   ```java
   public static final DeferredRegister<Consumer<GameTestHelper>> TEST_FUNCTIONS =
       DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, ProductiveFrogs.MOD_ID);
   ```

   *Common gotcha:* `TestFunctionLoader.registerLoader(...)` is vanilla-only — it runs during `BuiltInRegistries.bootStrap()` which fires **before mod loading**, so mod static initializers and constructors are too late. Use `DeferredRegister` instead; we tried the `TestFunctionLoader` path first and it silently produced "missing test function" failures because our loader was registered after the registry was already populated and frozen.

2. **Test instance** — a `FunctionGameTestInstance` registered via `RegisterGameTestsEvent`. Pairs a function holder with `TestData` (structure id, max ticks, etc):

   ```java
   @SubscribeEvent
   public static void onRegisterGameTests(RegisterGameTestsEvent event) {
       if (testInstancesRegistered) return;   // event can fire twice — guard it
       testInstancesRegistered = true;
       Holder<TestEnvironmentDefinition> env = event.registerEnvironment(
           Identifier.fromNamespaceAndPath(MOD_ID, "default")
       );
       // ... build TestData, register FunctionGameTestInstance per test
   }
   ```

3. **Structure NBT** — lives at `data/<modid>/structure/<name>.nbt` (singular `structure/`, like the tag dirs went singular in 1.21.x). Defines the test plot bounds. For tests that build their scenario programmatically via `helper.setBlock`, an all-air structure of suitable size is enough. We ship `empty_5x5x5.nbt` for that.

The canonical reference pattern lives in `PFGameTests.java`; copy its `registerTest(...)` helper for new tests. The full pattern was sourced from [ksoichiro/JustBlockShapes](https://github.com/ksoichiro/JustBlockShapes/blob/main/neoforge/1.21.11/src/main/java/com/justblockshapes/neoforge/gametest/JustBlockShapesGameTestNeoForge.java), one of the few working 1.21.11 NeoForge mods using the new system.

### Adding a new GameTest

1. Write the test method as `private static void myTest(GameTestHelper helper) { ... }` in `PFGameTests`.
2. Add a `registerTest("my_test", PFGameTests::myTest, timeoutTicks);` call in the static initializer.
3. If the test needs a custom plot (vs the default `empty_5x5x5`), ship a new NBT at `data/productivefrogs/structure/<name>.nbt` and pass that identifier through `TestData`.
4. Run `./gradlew runGameTestServer` locally to verify.
5. Push — CI's `gameTest` job runs all tests.

### Authoring structure NBTs

For empty plots, generate programmatically (see the Python recipe in `PFGameTests.java`'s commit history). For complex scenarios that need pre-placed blocks, author in-game:

1. In a creative dev world, `/give @s minecraft:test_block` and `/give @s minecraft:test_instance_block`.
2. Build the layout you want; place TestBlocks if using `BlockBasedTestInstance`.
3. Use a `test_instance_block` to export the plot — it writes to `<world>/generated/<namespace>/structure/<name>.nbt`.
4. Copy into `src/main/resources/data/productivefrogs/structure/`.

## CI layout

`.github/workflows/ci.yml` runs two parallel jobs:

- **`build`** — `./gradlew build` (compile + `test` task with both JUnit flavors + jar upload)
- **`gameTest`** — `./gradlew runGameTestServer` (boots headless MC, runs all registered tests)

Both are required status checks on main; PR can't merge with either failing.

## What manual playtesting still covers

Both automated layers target server state. They don't replace eyeballs for:

- **Visuals** — tint, render layers, model transforms, alpha behavior, creative-tab layout. See [§What GameTest does NOT cover](#what-gametest-does-not-cover--visuals-are-blind) above for the full list and the PR #27 outer-shell-gray cautionary tale.
- **Player-driven flows** — right-click semantics, hand-slot consumption, sound feedback, GUI interactions.
- **Cross-mod compat sanity** (Mekanism, Create, etc. — future).

### Minimum playtest checklist for visual-touching PRs

When a PR changes any of `client/`, `assets/`, `Category.tintArgb`, item/block model JSON, or lang entries, run `./gradlew runClient` and verify:

1. The affected item / block renders the expected color in **inventory**, **dropped item**, and **placed-in-world** views (these resolve tints through different code paths).
2. The translucent / cutout render type looks right — outer layer should not collapse into an opaque solid; gradient should not vanish.
3. Display name reads correctly (no raw `item.productivefrogs.foo` translation-key fallback).
4. Tooltip and JEI search find the item by every category / variant name.

Capture screenshots in the PR description for any visually-distinct surface. The reviewer should be able to verify the pixels without re-running the client.

The automated layers are about regression safety, not feature acceptance.
