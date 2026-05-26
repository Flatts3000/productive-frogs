# Spawnery - full implementation spec

A config-gated, skyblock-oriented appliance block that turns glass bottles into bottled frogspawn (Frog Eggs), fueled by slime balls and optionally primed to a species. It bootstraps the **frog side** of the production loop in worlds where vanilla frogspawn (swamps / mangroves) is unreachable.

Platform: NeoForge 21.1.230 / Minecraft 1.21.1 / Java 21. Target release: **v1.4** (a new V1 appliance). Branch: `feat/spawnery`.

Status of this doc: **implemented on `feat/spawnery`** (build + 59 GameTests green). Remaining before release: the manual `runClient` visual pass (GUI render, block model/tint - GameTest is blind to visuals).

---

## 1. Why it exists

The loop starts from vanilla frogspawn: bottle it (`FrogspawnBottlingHandler`), prime/place it, hatch a frog. A void skyblock world has no swamp/mangrove, so no frogspawn, so the frog side never starts. `docs/species_as_category_redesign.md` already records that frog-egg priming was made biome-agnostic so skyblock could bootstrap frogs "if a pack provides any vanilla frogspawn." The Spawnery **is** that provider, in-mod and player-operated. It is disabled by default; a normal world has swamps and never needs it.

## 2. Scope

V1 appliance: single block, hand/hopper operated, no power/multiblock (the V1 appliance rule in `docs/versioning.md`). Frog-side analogue of the Slime Milker. **Not** automation.

## 3. Player-facing behavior

Furnace-shaped GUI, modelled on `SlimeMilkerBlockEntity` + the vanilla furnace burn loop.

| Slot | Index | Accepts | Role |
|---|---|---|---|
| Bottle | 0 | `minecraft:glass_bottle` | container; consumed, returned filled |
| Fuel | 1 | `minecraft:slime_ball` | burn fuel - 1 ball = 1 bottle |
| Primer | 2 | any item in a `spawnery_primer/<species>` tag, or empty | selects output species |
| Output | 3 | (extract only) | the produced Frog Egg |

Per completed cycle: consume **1 glass bottle** + **1 slime ball of burn**, write one `FrogEggItem` to output:

- **Primer empty / unrecognised:** plain `FrogEggItem` (no `contained_category`) = vanilla frogspawn bottle. Primer not consumed.
- **Primer recognised:** resolve to a `Category` via the tags (canonical species order, first match wins), consume **1 primer**, stamp that category onto the egg = species-primed ("modded") egg.

The egg is species-level (six categories), not resource-level. `FrogEggItem` is `stacksTo(1)`, so the output holds exactly one egg; the block stalls until the egg is removed (identical to the Milker, whose milk bucket is also `stacksTo(1)`).

### Fuel economy

1 slime ball = 1 bottle ("1:1 or worse" ceiling). A ball is consumed at the start of a cycle and yields a burn lasting exactly one cycle, so the flame UI behaves like a furnace while the ratio stays 1:1. Tunable via `BURN_TICKS_PER_BALL` (= production ticks) if a costlier ratio is wanted.

### Primers (default-world resource, pack-overridable tags)

**Framing (updated 2026-05-26):** the defaults are tuned for a **normal world**, not skyblock. Each species' primer is **one** representative resource that species unlocks - "show the frog the resource you want and you get that species' egg." A normal-world player already has that resource; a skyblock / restricted pack overrides the tag to fit its progression (see the Primer overridability section). The Spawnery keeps its own per-species item tags (one entry each) rather than `findByPrimer` so it's exactly one primer per species (not the whole resource pool) and overridable per species.

| Species | Default primer | Tag |
|---|---|---|
| Cave | `minecraft:iron_ingot` | `productivefrogs:spawnery_primer/cave` |
| Geode | `minecraft:amethyst_shard` | `productivefrogs:spawnery_primer/geode` |
| Bog | `minecraft:bone` | `productivefrogs:spawnery_primer/bog` |
| Tide | `minecraft:prismarine_shard` | `productivefrogs:spawnery_primer/tide` |
| Infernal | `minecraft:blaze_powder` | `productivefrogs:spawnery_primer/infernal` |
| Void | `minecraft:ender_pearl` | `productivefrogs:spawnery_primer/void` |

Each is an actual variant in that species' pool, so the default Spawnery primer aligns with that resource's `findByPrimer` mapping (iron -> Cave both ways) - but the tag stays the override surface. Bog uses `bone` rather than a slime ball because the slime ball is the fuel. (Earlier drafts used skyblock-reachable signature items - cobblestone/mud/kelp/netherrack - on the assumption the mod should ship skyblock-ready; that was reversed in favour of normal-world defaults + pack overrides.)

## 4. Config gating

New `spawnery` section in `PFConfig` (COMMON) - **DONE**:

- `SPAWNERY_ENABLED` -> `spawnery.enabled`, default **false**. Gates craftability + creative + JEI.
- `SPAWNERY_PRODUCTION_TICKS` -> `spawnery.productionTicks`, default 200, range [1, 24000]. Burn/cook duration per bottle.

When `enabled = false`:
1. **Uncraftable** - recipe carries `productivefrogs:config_enabled` condition reading `spawnery.enabled`; failed condition drops the recipe at datapack load (JEI then shows no recipe).
2. **Hidden from creative** - `PFCreativeTabs` guards the `accept` behind `SPAWNERY_ENABLED.get()`.
3. **Hidden from JEI** - the plugin removes the Spawnery item from the ingredient list + skips its info page when disabled.

A **placed** block still functions regardless of the flag (inertness was not requested; this keeps GameTests runnable and lets a pack light the block by flipping the flag). Toggling requires a world reload to re-evaluate the recipe condition.

## 5. Crafting recipe

`data/productivefrogs/recipe/spawnery.json`, shaped, config-gated:

```json
{
  "neoforge:conditions": [
    { "type": "productivefrogs:config_enabled", "config": "spawnery" }
  ],
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "pattern": ["PPP", "CMC", "CCC"],
  "key": {
    "C": { "item": "minecraft:cobblestone" },
    "M": { "item": "minecraft:bone_meal" },
    "P": { "tag": "minecraft:planks" }
  },
  "result": { "id": "productivefrogs:spawnery", "count": 1 }
}
```

5 cobblestone, 1 bonemeal (center), 3 planks (any wood). Bonemeal is crafting-only; not an operating feedstock. (Result-object shape `{"id","count"}` is the 1.21.1 form; verify against an existing recipe at build.)

## 6. Config-reading recipe condition - DONE

`com.flatts.productivefrogs.data.condition.ConfigEnabledCondition` (record) implements NeoForge `ICondition` with a `MapCodec`, registered to `NeoForgeRegistries.Keys.CONDITION_CODECS` via `PFConditions` (a `DeferredRegister`). Carries a closed-enum `Key` (`spawnery`); `test()` returns the mapped `PFConfig` flag, guarded by `PFConfig.SPEC.isLoaded()` (fails closed if unloaded). `ProductiveFrogs` constructor must call `PFConditions.register(modEventBus)`.

## 7. Class-by-class implementation

### 7.1 `registry/PFItemTags` (new)

Holds the six primer `TagKey<Item>`s.

```java
public final class PFItemTags {
    public static final Map<Category, TagKey<Item>> SPAWNERY_PRIMER = build();
    private static Map<Category, TagKey<Item>> build() {
        EnumMap<Category, TagKey<Item>> m = new EnumMap<>(Category.class);
        for (Category c : Category.values())
            m.put(c, TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "spawnery_primer/" + c.id())));
        return Collections.unmodifiableMap(m);
    }
    public static TagKey<Item> spawneryPrimer(Category c) { return SPAWNERY_PRIMER.get(c); }
}
```

### 7.2 `content/block/SpawneryBlock` (new) extends `Block implements EntityBlock`

- Properties (in `PFBlocks`): `mapColor(MapColor.PLANT)`, `strength(1.5F)`, `sound(SoundType.STONE)`, `lightLevel(s -> s.getValue(LIT) ? 8 : 0)`.
- Blockstate props: `FACING = HORIZONTAL_FACING`, `LIT = BooleanProperty.create("lit")`. Default north / not-lit.
- `getStateForPlacement` -> facing = `ctx.getHorizontalDirection().getOpposite()`.
- `rotate` / `mirror` on FACING.
- `newBlockEntity` -> `new SpawneryBlockEntity(pos, state)`.
- `getTicker` -> server only, `SpawneryBlockEntity::serverTick` (type-guarded helper like the Milker).
- `useWithoutItem` / `useItemOn` -> open the menu (server: `serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos))`).
- `playerWillDestroy` -> drop all four inventory slots via `Containers.dropItemStack`.
- `onRemove` -> `updateNeighbourForOutputSignal` on block change.

### 7.3 `content/block/entity/SpawneryInventory` (new) extends `ItemStackHandler`

- Slot constants: `BOTTLE_SLOT=0, FUEL_SLOT=1, PRIMER_SLOT=2, OUTPUT_SLOT=3, SLOT_COUNT=4`.
- `isItemValid(slot, stack)`:
  - BOTTLE -> `stack.is(Items.GLASS_BOTTLE)`
  - FUEL -> `stack.is(Items.SLIME_BALL)`
  - PRIMER -> `isPrimer(stack)` = any `Category` where `stack.is(PFItemTags.spawneryPrimer(c))` (`ItemStack.is(TagKey)` reads the item's holder tags, populated after tag load - valid at gameplay time)
  - OUTPUT -> false
- `onContentsChanged` -> `onChanged.run()`.
- Sided views via a `MultiSlotView` (generalises the Milker's single-slot `SidedView`):
  - `inputView()` -> indices `[BOTTLE, FUEL, PRIMER]`, insert-only (extract disabled). Hoppers call `insertItem(slot,...)` per exposed slot; `delegate.insertItem(index,...)` respects `isItemValid`, so a bottle lands in BOTTLE, a slime ball in FUEL, a tagged primer in PRIMER, and anything else bounces.
  - `outputView()` -> index `[OUTPUT]`, extract-only.
- `serialize` / `deserialize` reuse `serializeNBT(RegistryAccess.EMPTY)` like the Milker.

### 7.4 `content/block/entity/SpawneryBlockEntity` (new) extends `BlockEntity implements MenuProvider`

Fields: `int cookProgress`, `int burnTime`, `int burnDuration`; `SpawneryInventory inventory`.

ContainerData indices: `DATA_COOK_PROGRESS=0, DATA_COOK_TOTAL=1, DATA_BURN_TIME=2, DATA_BURN_DURATION=3, DATA_COUNT=4`.

`serverTick(level, pos, state, be)` algorithm:

```
total = max(1, PFConfig.SPAWNERY_PRODUCTION_TICKS.get())
be.burnDuration = total
Category cat = resolvePrimerCategory(be.inv[PRIMER])         // null = vanilla
canProduce = !be.inv[BOTTLE].isEmpty() && be.inv[OUTPUT].isEmpty()
if (canProduce) {
    if (be.burnTime <= 0) {                                  // need to ignite a cycle
        fuel = be.inv[FUEL]
        if (!fuel.isEmpty() && fuel.is(SLIME_BALL)) {
            fuel.shrink(1); be.burnTime = total; be.setChanged()
            PFDebug TONGUE? no -> SPAWNERY: "ignite, fuel consumed"
        }
    }
    if (be.burnTime > 0) {
        be.burnTime--; be.cookProgress++; be.setChanged()
        if (be.cookProgress >= total) be.complete(cat)       // see below
    } else {
        be.resetCook()                                       // no fuel: stall
    }
} else {
    be.resetCook()
    if (be.burnTime > 0) { be.burnTime--; be.setChanged() }  // extinguish a stalled flame
}
setLit(level, pos, state, be.burnTime > 0)                   // LIT blockstate, UPDATE_CLIENTS, no-op if unchanged
```

`complete(cat)`:
```
inv[BOTTLE].shrink(1)
if (cat != null) inv[PRIMER].shrink(1)
inv.setStackInSlot(OUTPUT, makeEgg(cat))
cookProgress = 0; burnTime = 0
level.playSound(null, pos, SoundEvents.FROG_LAY_SPAWN, BLOCKS, 0.8F, 1.0F)
PFDebug.SPAWNERY: "produced <vanilla|cat> egg"
```

`makeEgg(cat)`: `ItemStack(PFItems.FROG_EGG)`; if `cat != null` set `CONTAINED_CATEGORY`.

`resolvePrimerCategory(stack)`: empty -> null; else first `Category` whose tag contains the stack, else null.

NBT (`saveAdditional`/`loadAdditional`): `CookProgress`, `BurnTime`, `Inventory` (`burnDuration` is recomputed each tick from config, not persisted). Clamp on load: `cookProgress = max(0, loaded)`, `burnTime = max(0, loaded)`. `getUpdateTag`/`getUpdatePacket` resync like the Milker.

`MenuProvider`: `getDisplayName` -> `block.productivefrogs.spawnery`; `createMenu` -> `new SpawneryMenu(id, playerInv, this, dataAccess)`.

### 7.5 `content/menu/SpawneryMenu` (new) extends `AbstractContainerMenu`

Slot positions (176x166 GUI, furnace-derived with a second top slot):

| Slot | X | Y |
|---|---|---|
| Bottle | 56 | 17 |
| Fuel | 56 | 53 |
| Primer | 116 | 17 |
| Output | 116 | 35 |

As shipped: bottle over fuel on the left (flame between), primer over output on the right, arrow bridging. Arrow at (79, 34) source (176,14) 24x16; flame at (56, 36) source (176,0) 14x14 (vanilla burn sprites re-inlined into the background by `scripts/generate_spawnery_gui.ps1`). Player inventory at (8,84), hotbar (8,142).

Two ctors (network `(id, inv, buf)` reading the BlockPos; server `(id, inv, be, data)`) like `SlimeMilkerMenu`, plus the defensive dummy-container fallback when the BE has not synced. `addDataSlots(dataAccess)`.

Data getters: `getCookProgress`, `getCookTotal` (fallback to `SPAWNERY_PRODUCTION_TICKS` default if 0), `getBurnTime`, `getBurnTotal`.

`quickMoveStack`: container slots (0..3) -> player inv; player items -> bottle slot if `glass_bottle`, fuel slot if `slime_ball`, primer slot if `isPrimer`, else main<->hotbar shuffle.

### 7.6 `client/screen/SpawneryScreen` (new) extends `AbstractContainerScreen<SpawneryMenu>`

- Background `textures/gui/container/spawnery.png` (256x256, 176x166 region).
- `renderBg`: blit background; blit cook arrow (width scales `cookProgress/total`); blit burn flame (vanilla furnace style - 14px sprite that shrinks from the bottom as `burnTime/burnDuration` drops).

## 8. Registration wiring (exact insertions)

| File | Edit |
|---|---|
| `PFBlocks` | add `SPAWNERY` `DeferredBlock<SpawneryBlock>` after `SLIME_MILKER` (props in 7.2) |
| `PFItems` | add `SPAWNERY` `registerSimpleBlockItem("spawnery", PFBlocks.SPAWNERY, new Item.Properties())` |
| `PFBlockEntities` | add `SPAWNERY` `BlockEntityType.Builder.of(SpawneryBlockEntity::new, PFBlocks.SPAWNERY.get()).build(null)` |
| `PFMenuTypes` | add `SPAWNERY` `IMenuTypeExtension.create(SpawneryMenu::new)` |
| `PFClientEvents` | `event.register(PFMenuTypes.SPAWNERY.get(), SpawneryScreen::new)` in `onRegisterMenuScreens` |
| `PFModBusEvents` | `registerBlockEntity(Capabilities.ItemHandler.BLOCK, SPAWNERY, (be,side) -> side==DOWN ? be.getInventory().outputView() : be.getInventory().inputView())` |
| `ProductiveFrogs` ctor | `PFConditions.register(modEventBus)` (no ordering dep; place after `PFCreativeTabs.register`) |
| `PFDebug` | add `SPAWNERY("spawnery")` to the `Area` enum |

## 9. Visibility gating

- `PFCreativeTabs`: after the `SLIME_MILKER` accept, add `if (PFConfig.SPAWNERY_ENABLED.get()) output.accept(PFItems.SPAWNERY.get());` (import `PFConfig`).
- `ProductiveFrogsJeiPlugin`:
  - Info page in `registerRecipes`: only when `SPAWNERY_ENABLED.get()`, `reg.addIngredientInfo(new ItemStack(PFBlocks.SPAWNERY), VanillaTypes.ITEM_STACK, Component.translatable("productivefrogs.jei.spawnery.info"))`.
  - Hide when disabled: implement `onRuntimeAvailable(IJeiRuntime)` -> if `!SPAWNERY_ENABLED.get()`, `runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, List.of(new ItemStack(PFItems.SPAWNERY.get())))`. (Verify the exact 1.21.1 JEI method names against the plugin's existing imports at build.)

## 10. Assets

- `blockstates/spawnery.json`: variants over `facing` (n/e/s/w) x `lit` (true/false). **As shipped on the branch:** both `lit` values map to the single `spawnery` model (no `_on` model yet); `lit` only drives the light level.
- `models/block/spawnery.json`: `minecraft:block/orientable_with_bottom`. **As shipped: placeholder vanilla textures** - `moss_block` top, `cobblestone` side+bottom, `oak_planks` front. Renders cleanly and reads as a cobble/wood build, but is not bespoke art.
- `models/item/spawnery.json`: `{"parent":"productivefrogs:block/spawnery"}`.
- **TEXTURES OWED (documented; not yet generated or wired).** The Spawnery needs proper bespoke block textures - thematically the same appliance family as the Slime Milker (cobblestone + wood-plank build, wood-framed window) but visually distinct: the window shows **frogspawn** (a dark teal egg pool when idle, a bright teal glow when lit) where the Milker shows green slime. A generator is prepared at `scripts/generate_spawnery_textures.ps1` - it derives the 7 textures (`spawnery_{top,front,side,bottom}` + `spawnery_{top,front,side}_on`) from the Milker's, masking the window via the Milker's `_working` green and recolouring it to a frogspawn teal. **Fix-later steps:** run the generator; add `models/block/spawnery_on.json` (front -> `spawnery_front_on`, top -> `spawnery_top_on`, side -> `spawnery_side_on`); point `models/block/spawnery.json` at the new `spawnery_*` textures; update `blockstates/spawnery.json` so `lit=true` -> `spawnery_on`; verify on the `runClient` pass.
- GUI `textures/gui/container/spawnery.png`: composite from vanilla `furnace.png` via a `scripts/generate_spawnery_gui.ps1` (mirrors `generate_slime_milker_gui.ps1`): paint slot frames at the 7.5 positions (bottle, primer, fuel) + output, re-inline the arrow + flame sprites into the (176,*) atlas region.

## 11. Data files

- `loot_table/blocks/spawnery.json`: drop-self with `survives_explosion`, `random_sequence productivefrogs:blocks/spawnery` (copy the Milker's loot table).
- `recipe/spawnery.json`: section 5.
- `tags/item/spawnery_primer/<species>.json` x6, each: `{ "replace": false, "values": ["minecraft:<primer>"] }` with the section 3 items.

## 12. Lang keys (`en_us.json`)

```
"block.productivefrogs.spawnery": "Spawnery",
"productivefrogs.jei.spawnery.info": "Turns glass bottles into bottled frogspawn, fueled by slime balls. Leave the primer slot empty for vanilla frogspawn, or add a species primer (cobblestone, mud, kelp, amethyst, netherrack, or an ender pearl) to bottle that species' eggs. A skyblock bootstrap; off by default."
```

The Spawnery is a single block, not a variant, so the per-variant `LangCompletenessTest` 5-key family does not apply. The JEI key is covered by that test's `allReferencedJeiInfoKeysExist` check.

## 13. Tests

### JUnit
- `SpawneryRecipeTest`: load `recipe/spawnery.json` from processed resources; assert `type == crafting_shaped`, pattern counts (5 `C`, 3 `P`, 1 `M`), result id `productivefrogs:spawnery`, and the `config_enabled`/`spawnery` condition is present.
- `ConfigEnabledConditionTest`: `Key.SPAWNERY.getSerializedName() == "spawnery"`; `Key.CODEC` round-trips `"spawnery"`; `Key.CODEC` rejects an unknown id.

### GameTest (`PFGameTests`, template `empty_5x5x5`, `timeoutTicks ~ 260` to clear the 200-tick default)
- `spawneryProducesVanillaBottle`: place spawnery, set BOTTLE=glass_bottle, FUEL=slime_ball; succeedWhen OUTPUT is `FROG_EGG` with **no** `CONTAINED_CATEGORY`, bottle + fuel consumed.
- `spawneryPrimesToSpecies`: BOTTLE + FUEL + PRIMER=cobblestone; succeedWhen OUTPUT is `FROG_EGG` with `CONTAINED_CATEGORY == CAVE`, primer consumed.
- `spawneryStallsWithoutFuel`: BOTTLE only; assert no OUTPUT after a delay.
- `spawneryStallsWithoutBottle`: FUEL only; assert no OUTPUT after a delay.

(Placed-block function is config-independent, so these run under the default disabled config.)

## 14. Build & verification order

1. `PFConfig` (done) -> `ConfigEnabledCondition` + `PFConditions` (done) -> register in `ProductiveFrogs` ctor.
2. `PFItemTags` + 6 primer tag JSONs.
3. `SpawneryInventory` -> `SpawneryBlockEntity` -> `SpawneryBlock`; register block/item/BE.
4. `SpawneryMenu` -> `SpawneryScreen`; register menu + screen binding.
5. `PFModBusEvents` caps; `PFCreativeTabs` guard; `PFDebug` area.
6. Assets (blockstate/models/item/textures, GUI via script); loot table; recipe.
7. JEI info + hide.
8. Lang keys.
9. JUnit + GameTest.
10. `./gradlew build` (JAVA_HOME -> jdk-21) then `./gradlew runGameTestServer`. Manual `runClient` pass for GUI/model/tint (GameTest is blind to visuals).

## Primer overridability (modpacks)

**Decision (2026-05-26): the override surface is item tags, not a custom recipe type.** A modpack retunes any species' primer by overriding the `spawnery_primer/<species>` tag in its own datapack - no mod change, no recipe type. Tags were chosen over a custom recipe type because they have wider, first-class support across every tool a pack author uses (datapack JSON, KubeJS, CraftTweaker), fit the mod's data-driven / minimal-Java ethos, and match what the mapping actually is - a classification ("which items prime which species"). A custom recipe type would need a bespoke KubeJS/CraftTweaker bridge to be ergonomic, which would not exist for a niche mod, so it is the *less* tweakable surface here. Reconsider only if a future need arises for richer per-mapping semantics (multiple inputs, variable consume counts, per-recipe conditions, custom output); the current single-primer -> species model uses none of that.

### How a pack retunes a primer

The default Cave primer is `iron_ingot` (a normal-world resource). A skyblock pack, where iron is gated but cobblestone is infinite, swaps it:

Datapack - `data/productivefrogs/tags/item/spawnery_primer/cave.json`:
```json
{ "replace": true, "values": ["minecraft:cobblestone"] }
```

KubeJS - `server_scripts`:
```js
ServerEvents.tags('item', event => {
    event.remove('productivefrogs:spawnery_primer/cave', 'minecraft:iron_ingot')
    event.add('productivefrogs:spawnery_primer/cave', 'minecraft:cobblestone')
})
```

CraftTweaker:
```zenscript
<tag:items:productivefrogs:spawnery_primer/cave>.remove(<item:minecraft:iron_ingot>);
<tag:items:productivefrogs:spawnery_primer/cave>.add(<item:minecraft:cobblestone>);
```

A modded item that may be absent - list it soft so the tag still loads when the mod isn't present:
```json
{ "replace": false, "values": [{ "id": "othermod:special_ore", "required": false }] }
```

### Granularity: the Spawnery gates a species, not a single resource

A primer selects one of the six frog **species** (Cave / Geode / Bog / Tide / Infernal / Void), and that frog farms its whole resource set. So a pack gates "access to the Cave frog" (hence all Cave resources), not "access to diamond" specifically. Per-*resource* gating lives on the slime-priming side (`SlimeVariant` primers), not the Spawnery.

### Follow-ups (deferred - fix-later batch)

- **Docs:** add a "retuning Spawnery primers" subsection to `docs/cross_mod_compat.md` that cross-references this section, so pack authors find it from the compat doc.
- **Dynamic JEI display (code):** make the Spawnery JEI info read the *current* `spawnery_primer/<species>` tag contents and list them per species, so a pack's overrides surface automatically and players can discover the pack's gating. Gives the recipe-type's discoverability without the recipe type.

## 15. Decisions & assumptions (redline here)

- **D1** Furnace-style 4-slot GUI (bottle, fuel, primer, output). [user: "whatever is easier" -> furnace-style]
- **D2** Fuel = `minecraft:slime_ball`, 1:1 with bottles. [user]
- **D3** Primer consumed per primed egg (mirrors slime priming). [my call]
- **D4** Egg is species-level, not resource-level. [matches existing mod model]
- **D5** Primers are dedicated per-species pack-overridable tags (one entry each), NOT `findByPrimer` - so it's exactly one primer per species (not the whole resource pool) and overridable per species. Defaults are the representative **normal-world** resource per species: iron ingot / amethyst shard / bone / prismarine shard / blaze powder / ender pearl (Cave/Geode/Bog/Tide/Infernal/Void). [reframed 2026-05-26 from skyblock-signature items to normal-world resources; packs override for restricted worlds]
- **D6** Disabled = uncraftable + hidden from JEI/creative; a placed block still works. [user said uncraftable + hidden; placed-inertness explicitly not requested -> keeps it test-friendly]
- **D7** Bonemeal is crafting-only, not an operating feedstock. [user]
- **D8** `productionTicks` default 200 (10 s); 1 slime ball burns exactly one cycle. [my call, configurable]
- **D9** Cave Slime Milk is NOT a default primer (can't bootstrap) but the resolver honours it if a pack tags it. [user idea, parked as pack-addable]
- **D10** Primer override surface is item **tags**, NOT a custom recipe type. [decided 2026-05-26 - tags have wider first-class pack-tool support (datapack + KubeJS + CraftTweaker), fit the mod's minimal-Java/JSON ethos, and match the classification nature of the mapping; a custom recipe type would need a bespoke KubeJS/CraftTweaker bridge to be ergonomic. See the "Primer overridability" section.]

## 16. Out of scope

- Hopper-fed automation upgrades (V2).
- A native crusher / any non-frog-side bootstrap.
- Save migration (none needed; purely additive).
