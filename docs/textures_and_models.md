# Textures and Models

How visual assets are organized in Productive Frogs, with a focus on **tint-based colorization** so we don't need a separate texture per category variant.

## V1 visual lock (decided in conversation)

For V1 the visual story is intentionally lean:

1. **Tint-based everywhere there are category variants.** Primed Frog Egg blocks, the Frog Egg bottle's contents layer, the Resource Tadpole bucket's tadpole layer, Resource Tadpole entities in the world, Resource Frog entities in the world - all share a single base texture (or vanilla texture, when subclassing) and apply the category's RGB tint at render time. No per-category bespoke textures.
2. **Two-layer vanilla-bottle pattern for bottled / bucketed contents.** Layer 0 is the tinted content; Layer 1 is the static container exterior drawn on top (glass for bottles, iron for buckets). Mirrors vanilla `item/potion` exactly - `layer0` is `potion_overlay` (tinted) and `layer1` is `potion` (glass), in that order.
3. **No emissive glow on eggs.** Primed Frog Eggs don't emit light - the dropped Froglight resources (eaten-by-frog drops) will be the emissive piece of the mod, mirroring vanilla froglights as the rewarding bright thing.

**Single source of truth for tint color:** `Category.tintArgb()` returns the per-category ARGB int used by every rendering path. Changing a category's color anywhere is a one-line edit on the enum.

## Guiding Principles

1. **Tint-based colorization for variants.** Resource Slime variants, Slime Buckets, and Slime Milk fluids all share base textures and differ only by per-variant color. Color is data; texture is shared.
2. **JSON-driven.** Every variant's appearance is defined in its `data/productivefrogs/slime_variant/<name>.json` - no hard-coded colors in Java.
3. **Modpack-overridable.** Pack authors recolor any slime by overriding the variant JSON. No Java needed.
4. **Placeholder textures during development.** Mechanics-first. Replace placeholders with polished art before V1 release.

## Tint-Based Colorization - How It Works

Vanilla Minecraft has a built-in mechanism: the renderer multiplies a base texture's pixels by a per-instance color. This is how vanilla handles:

- Water (biome-tinted)
- Grass blocks and leaves (biome-tinted)
- Wool / Concrete (single dye color, white base texture)
- Spawn eggs (overlay-tinted by entity type)
- Potions (liquid tinted by effect)

For Productive Frogs, we use this for:

- **Resource Slime body** - one grayscale-ish body texture, tinted per variant
- **Resource Slime inner core** - separate overlay layer, also tinted
- **Slime Bucket contents** - bucket exterior is static; the slime-splash layer is tinted
- **Slime Milk fluid** - one base lumpy-liquid texture, tinted per fluid variant

## Entity tinting (Resource Tadpole, Resource Frog)

Blocks and items have built-in tint hooks (`BlockColor`, `ItemColor`). Entities don't have a parallel hook - vanilla rendering uses `LivingEntityRenderer.getModelTint(state)` which returns `-1` (white, no tint) by default. To tint an entity we subclass its renderer:

1. **Custom RenderState subclass.** `ResourceTadpoleRenderState extends LivingEntityRenderState` adds a `category` field. Same for `ResourceFrogRenderState extends FrogRenderState`. The render state is the snapshot passed to the renderer each frame - fields populated in `extractRenderState`.
2. **Custom Renderer subclass.** `ResourceTadpoleRenderer extends TadpoleRenderer` (and `ResourceFrogRenderer extends FrogRenderer`):
   - Override `createRenderState()` to return the subclass instance.
   - Override `extractRenderState(entity, state, partialTick)` - call super, then cast the state and copy `entity.getCategory()` onto it.
   - Override `getModelTint(state)` - return `state.category.tintArgb()` when present, else `-1` (no tint).
3. **Register the custom renderers** in `PFClientEvents` instead of vanilla `TadpoleRenderer`/`FrogRenderer`.

Vanilla textures stay as the base. With color-multiply tinting, the result is "vanilla green frog/tadpole multiplied by category color" - readable but not visually crisp until grayscale-base textures land. Acceptable for V1; the infrastructure is in place for cleaner art later.

## Per-Variant Color in JSON

The `primary_color` and `secondary_color` fields on each `slime_variant` JSON drive all rendering tint for that variant. Both are packed 24-bit RGB integers (`0xRRGGBB`, range `0`..`0xFFFFFF`), not `[r, g, b]` triples. `category` is a bare species name (`cave`, not `productivefrogs:metallic`). A primer (`primer_item` or `primer_tag`) is required; there is no `display_name`, `color_rgb`, `core_color_rgb`, or `loot_table` field. A real entry (`data/productivefrogs/productivefrogs/slime_variant/iron.json`):

```json
{
  "primer_item": "minecraft:iron_ingot",
  "category": "cave",
  "primary_color": 14211288,
  "secondary_color": 12632256,
  "inner_block": "minecraft:iron_block"
}
```

`primary_color` tints the slime body / inventory icon; `secondary_color` is the variant's accent tint. `inner_block` (optional) names the vanilla block whose texture is baked inside the slime (`scripts/generate_resource_slime_textures.py`). Cross-mod variants swap `primer_item` for a `primer_tag` (e.g. `c:ingots/tin`) gated behind `neoforge:conditions`.

These are starting points - tunable via JSON without recompiling. Use a packed-int helper (`(r << 16) | (g << 8) | b`) when picking new colors.

## Data Flow at Render Time

```
data/productivefrogs/productivefrogs/slime_variant/iron.json
  { "primary_color": 14211288, "secondary_color": 12632256, ... }
              ↓  (loaded at world load / datapack reload)
       SlimeVariant registry
              ↓
ResourceSlime entity instance - variant = "iron"
              ↓
Renderer asks: "what's my variant's color?"
              ↓
Binds shared body texture + applies tint 0xD8D8D8
              ↓
                  Draws an iron-tinted slime
```

Same flow applies to:
- Slime Bucket item - variant stored in NBT, renderer looks up color
- Slime Milk fluid block - fluid variant has its own color
- Tadpole and adult Resource Frog - frog category has a color (defined separately from slime colors)

## Texture Files Needed

### Slime body textures (per-variant, baked)

Resource Slimes are NOT a single shared body texture tinted at render. Each variant has its own pre-baked PNG (`scripts/generate_resource_slime_textures.py` bakes the `inner_block` into the body), and each parent species has a fallback texture. The outer shell is still tint-multiplied by `primary_color` on top of the baked texture.

| File | Purpose |
|---|---|
| `textures/entity/slime/<variant>_resource_slime.png` | Per-variant baked Resource Slime body (e.g. `iron_resource_slime.png`); inner block baked in, shell tinted |
| `textures/entity/slime/<species>_slime.png` | Per-species fallback body (`cave_slime.png`, `geode_slime.png`, `bog_slime.png`, `tide_slime.png`, `infernal_slime.png`, `void_slime.png`) |

### Shared fluid / bucket textures (tintable)

| File | Purpose |
|---|---|
| `textures/block/slime_milk_still.png` | Animated still fluid - lumpy slime texture |
| `textures/block/slime_milk_flow.png` | Animated flowing fluid |
| `textures/item/slime_milk_bucket.png` | Bucket of milk - exterior static, contents tinted |

### Per-Block / Per-Item Textures (not tintable, unique designs)

| File | Purpose | Style |
|---|---|---|
| `textures/block/slime_milker/top.png` | Milker top face - recessed slot for bucket | 16×16, industrial |
| `textures/block/slime_milker/front.png` | Milker front face - visible press/piston | 16×16, industrial |
| `textures/block/slime_milker/side.png` | Milker side panels | 16×16, industrial |
| `textures/block/slime_milker/bottom.png` | Milker bottom | 16×16, plain |
| `textures/item/frog_egg.png` | Frog Egg item (in inventory) | 16×16, slime-spawn-shape |
| `textures/block/frog_egg.png` | Frog Egg block (placed) | 16×16, matching item but world-block |
| `textures/block/primed_frog_egg_*.png` | 6 primed egg variants (per category) | 16×16 each, tinted overlay |
| `textures/block/froglight_*.png` | Drop blocks per slime variant (decorative) | 16×16, glowing |

### New Parent Slime Species (each is a unique entity, mostly using vanilla slime model)

| Entity | Body texture | Notes |
|---|---|---|
| Cave Slime | `entity/slime/cave_slime.png` | Gray/stony with dust speckles |
| Geode Slime | `entity/slime/geode_slime.png` | Translucent prismatic with faceted overlays |
| Bog Slime | `entity/slime/bog_slime.png` | Murky green with peat/mud overlays |
| Tide Slime | `entity/slime/tide_slime.png` | Translucent ocean-blue with bubble pattern |
| Infernal Slime | `entity/slime/infernal_slime.png` | Hot orange/red with ember speckles |
| Void Slime | `entity/slime/void_slime.png` | Dark purple with starfield speckles |

These are *not* tintable - each parent slime species has a fully designed texture. They're a one-time art investment (6 entities), not a per-variant cost.

## Item Models - JSON Format

Items use vanilla-style JSON models. For the Frog Egg (single-layer item):

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "productivefrogs:item/frog_egg"
  }
}
```

For Slime Bucket (two-layer with tint on contents):

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "productivefrogs:item/slime_bucket/exterior",
    "layer1": "productivefrogs:item/slime_bucket/contents"
  }
}
```

The mod registers an `ItemColor` provider for the `slime_bucket` item that returns the variant's `primary_color` for `layer1` (tintindex 1) and white (no tint) for `layer0`.

## Block Models - JSON Format

Slime Milker uses the standard "all-sides" model template:

```json
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "top": "productivefrogs:block/slime_milker/top",
    "bottom": "productivefrogs:block/slime_milker/bottom",
    "side": "productivefrogs:block/slime_milker/side",
    "front": "productivefrogs:block/slime_milker/front"
  }
}
```

(Vanilla doesn't have a "cube_4_face" template that supports a unique front; we'd write our own model JSON or use NeoForge's `cube_oriented` template.)

## Texture Pipeline - Development Strategy

**Phase 1: Placeholder textures.** Use a Python script (with PIL or Pillow) to generate simple shape-based 16×16 PNGs for every required file. Lets us build and test mechanics without commissioned art. Example placeholders:

- Frog Egg → small green-jelly oval with a darker dot inside
- Slime Milker top → gray cube with a small darker square (slot)
- Slime body → off-white blob shape (will be tinted)
- Frog Egg → small green oval

**Phase 2: Hand-drawn replacements.** Pixel art done in Aseprite (the standard) or Piskel (free web alternative), Krita, or GIMP. ~30 minutes per item once the style is established.

**Phase 3: Polished release art.** Either keep the hand-drawn versions or commission an artist if the project gains traction.

## Animations (V1 Optional)

Two places where animation pays for itself:

1. **Slime Milker press animation.** When activated, the front-face texture cycles through 4-8 frames showing pistons descending. Trivial to add via vanilla `.mcmeta` animation format.
2. **Slime Milk still+flowing animation.** Mandatory for fluids to feel right - water and lava animate; ours should too. ~8 frames at 4 ticks/frame is a good default.

Both are static texture files with a `.mcmeta` sidecar:

```json
// textures/block/slime_milk_still.png.mcmeta
{
  "animation": {
    "frametime": 4,
    "interpolate": true
  }
}
```

## Resource Pack Compatibility

Because the mod uses tint-based colorization driven by JSON variants, third-party resource packs can:

- **Override the base body/core/bucket/milk textures** - change the *style* (rough vs smooth, pixel detail) across all variants
- **NOT override per-variant colors** - those come from datapack JSON, not resource pack texture files

If a pack author wants to recolor a single variant, they edit the variant's JSON via a datapack, not via resource pack. This is the right separation: resource packs = visual style, datapacks = balance/identity.
