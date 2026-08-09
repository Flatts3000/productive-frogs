# Iron Furnaces auto-split: the data-component fix

**Status:** shipped, on by default, `compat.ironFurnacesAutoSplitFix`.
**Applies to:** Iron Furnaces `4.3.2` on 1.21.1 (the current and only 1.21.1 build).
**Upstream:** [Qelifern/IronFurnaces#229](https://github.com/Qelifern/IronFurnaces/issues/229), open and unanswered.
**Downstream:** Sky Frogs [#225](https://github.com/Flatts3000/sky-frogs/issues/225) (the conversion), [#220](https://github.com/Flatts3000/sky-frogs/issues/220) (reported as duplication).

This is the only place in the mod where we patch another mod's code. It is
here because the defect corrupts *our* item and nothing short of Java can
reach it.

## The defect

A factory-augmented Iron Furnace has six input slots and an **auto-split**
toggle that keeps them evenly loaded so all six smelt in parallel.
`BlockIronFurnaceTileBase.split` implements that by pooling every input slot
holding "the same item" and averaging the counts across the pool. Its idea of
"the same item" is:

```java
if (this.getItem(FACTORY_INPUT[i2]).isEmpty()
    || this.getItem(FACTORY_INPUT[i2]).getItem() != itemToCheck.getItem()) continue;
```

An item-id comparison. It never looks at data components.

Every Froglight variant is the single item id
`productivefrogs:configurable_froglight` carrying its variant in the
`productivefrogs:slime_variant` component. So the furnace pools variants that
are not the same thing at all, averages their counts, and writes the new
counts back into slots that each keep their own variant.

A furnace holding 64 Cave Froglights in one slot and one Nether Star
Froglight in another comes out holding 32 and 33.

The total count is conserved, so this is not duplication in the strict sense.
It is a free transmuter, which is worse: it converts the cheapest thing a
player can farm into the most expensive one, at a 1:1 rate, on a block that
runs unattended. It is also silent. Nobody notices until their Froglight
stock is the wrong colour.

Nothing about this is Froglight-specific. Any single-id-plus-component item
from any mod is corrupted by the same path. Froglights are simply the case we
ship, and the case players hit, because a Sky Frogs player smelts Froglights
by the thousand.

## Why it is a regression, not a fresh bug

Iron Furnaces fixed this exact behaviour once already, for 1.19.2, as
[#147](https://github.com/Qelifern/IronFurnaces/issues/147) ("NBT data gets
wiped off of items when using auto sort in any furnace"), in 2023. That fix
was against the old NBT model. 1.21 replaced NBT with data components and the
comparison went back to being id-only. Reported as #229 on 2026-07-05 with
the reproduction and the offending line; no response, and the repository's
last commit predates the report.

Everywhere else in that same class Iron Furnaces gets it right: its
auto-input, auto-output and internal insert paths all use
`ItemStack.isSameItemSameComponents`. `split` is the outlier.

## Why a mixin, and not anything cheaper

The corruption happens inside a block-entity tick. There is no datapack,
recipe, tag or config that reaches it. Specifically:

- **Iron Furnaces has no auto-split toggle in its config.** Checked the whole
  `Config` class: speeds, tiers, generation rates, XP cap, light updates.
  Nothing about auto-split or factories.
- **A modpack's only lever is removing the Factory augment**
  (`ironfurnaces:augment_factory`), because `split` is called from exactly two
  places and both sit inside the `isFactory()` branch of the tick. That kills
  the bug by killing the feature, and it does nothing for players on other
  packs.
- **Splitting the Froglight into one item per variant** would fix this class
  of bug everywhere, permanently, but it is the mod's central data decision
  (an unbounded datapack registry of variants cannot become a fixed set of
  registered items) and it would break every existing world.

## What the patch does

`compat/ironfurnaces/FactoryAutoSplit` is a faithful reimplementation of
`split` with one thing changed: the slot-grouping test is
`ItemStack.isSameItemSameComponents`. The empty-slot seeding and the
averaging maths are reproduced exactly, including which slots absorb the
remainder, so **a furnace holding one item type behaves identically to
unpatched Iron Furnaces**.

The safety property for mixed furnaces is that counts never move *across*
groups: a variant's total is untouchable. It is not that every group gets
balanced. Like upstream, one call balances a single group, the one the last
occupied slot belongs to, so a furnace holding Cave Froglights in slots 7 to
10 and one Nether Star Froglight in slot 12 balances only the Nether Star and
leaves the Cave slots uneven until smelting shifts which slot is last. That is
upstream's ordering, deliberately kept, and it is why the differential tests
for plain items pass at all.

`mixin/BlockIronFurnaceTileBaseMixin` injects at the head of `split`,
delegates, and cancels. That substitutes the whole behaviour rather than
leaving half the original running, which is safe here because `split` is the
sole entry point: its two helpers, `fillEmptySlots` and `getSplitCounts`,
have no other callers anywhere in the mod.

The helper takes a plain `Container` and the slot-index array, so it carries
no Iron Furnaces types and is unit-tested without the mod installed
(`FactoryAutoSplitTest`, including the exact reported scenario).

## How it is kept safe

Three guards, because patching someone else's code deserves them:

1. **No compile-time dependency.** The mixin names its target by string, so
   the mod builds and runs with Iron Furnaces absent.
2. **`PFMixinPlugin` declines to apply the mixin** unless `ironfurnaces` is
   in the loading mod list. Without this, Mixin fails to resolve the target
   class and takes the game down for every player who does not have the mod.
   The gate is an explicit mixin-to-modid map and **refuses anything not in
   it**, so a future compat mixin added to the json without a gate entry costs
   a missing patch and a warning line rather than a crash for everyone lacking
   its target mod.
3. **Nothing about the patch is required.** Both the injector
   (`require = 0`) and the mixin config itself (`"required": false`) are
   non-fatal. Both are needed: `require` only governs the `@Inject`, while the
   `@Shadow` of `FACTORY_INPUT` is resolved when the mixin is applied, and on a
   *required* config that failure is fatal. With only the injector relaxed, a
   future Iron Furnaces renaming that field would crash every player who has
   both mods - the precise opposite of the guarantee this section claims.
   Pinned by `MixinWiringTest.theConfigStaysNonRequiredSoFailuresAreNotFatal`.

   Failing open means failing silently, which is why `PFMixinPlugin.postApply`
   logs a line when the patch *does* apply:

   ```
   [productivefrogs]: Applied the data-component fix to Iron Furnaces factory
   auto-split (ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase)
   ```

   Absence of that line in a log means the furnace is unpatched.

`compat.ironFurnacesAutoSplitFix = false` hands the behaviour back to Iron
Furnaces, for an operator who wants the original or is testing an upstream
fix.

## How it is tested

Four layers, because a mixin can be wrong in four unrelated ways.

**The behaviour we want** (`FactoryAutoSplitTest`, 9 tests). The reported
scenario and its mirror, single-variant balancing, plain items, per-tier slot
ranges, and refusal of out-of-range arguments.

**The behaviour we must not change** (`FactoryAutoSplitDifferentialTest`, 8
tests). `UpstreamAutoSplitReference` is Iron Furnaces 4.3.2's `split`,
`fillEmptySlots` and `getSplitCounts` transcribed verbatim into the test
source. One test asserts that the reference *still reproduces the bug*, which
is what keeps the transcription honest. The rest run both algorithms over the
same 400 randomised furnace states per tier range and assert they agree
exactly for componentless items and for single-variant furnaces. That is the
evidence behind the claim that a furnace full of iron ore behaves as it always
did; without it, that claim is just a comment.

The same fixture asserts the invariants that hold where there is no upstream
behaviour worth matching: per-variant totals conserved, no slot over its stack
limit, non-Froglight components respected, and convergence to a fixed point
rather than oscillation.

**The plumbing** (`MixinWiringTest`, 6 tests). Renaming the mixin class,
moving it out of the declared package, renaming the plugin, or dropping the
`[[mixins]]` line from `neoforge.mods.toml` each leaves a green build and an
unpatched furnace. These catch all four. (Note the mixin class is checked as a
compiled resource, not with `Class.forName`: Mixin marks mixin classes invalid
for ordinary classloading.)

**That the patch reaches the real class**
(`PFGameTests.ironFurnacesAutoSplitPreservesFroglightVariants`). Everything
above passes just as happily when the mixin never applies. This one places a
real `ironfurnaces:iron_furnace`, loads two Froglight variants into its factory
input slots, invokes Iron Furnaces' own `split` reflectively on the
mixin-transformed block entity, and asserts the rare variant did not multiply.
It is environment-driven like the cross-mod variant test: with Iron Furnaces
absent it passes without asserting, and it logs that it did.

Verified 2026-08-09 against the pinned `4.3.2` jar in `run/mods`:

- `./gradlew test` - 23 tests, green.
- `./gradlew runGameTestServer` - 197/197, with
  `Applied the data-component fix ...` in the log and no skip line.
- **Negative control.** With `compat.ironFurnacesAutoSplitFix = false` and
  nothing else changed, the GameTest fails: *"auto-split multiplied the rare
  Froglight variant: expected 1, got 11"*. The in-world defect is real, the
  test detects it, and the patch is what stops it.

Re-run that negative control after any change to the mixin. A patch test that
cannot fail is not a test.

## Maintenance

**On any Iron Furnaces version bump, re-verify.** The injector failing open
means a broken patch is silent. The check is: launch with the new build and
grep the log for the line above. If it is missing, decompile
`BlockIronFurnaceTileBase`, see whether `split(ZII)V` still exists and
whether the grouping comparison is still id-only, and either retarget the
mixin or delete this whole patch because they finally fixed it.

Deleting it is the goal. If #229 is ever resolved upstream, this file, the
mixin, the plugin, the helper, the test and the config key all go, and the
`compat/` package goes back to not existing.
