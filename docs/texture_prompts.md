# Texture Prompts (for AI generation)

Prompts for generating every texture Productive Frogs needs, organized by usage and priority. Designed for pixel-art-capable AI tools — copy/paste each prompt into your generator of choice.

## Tooling Recommendations

Not all AI image tools handle pixel art well. Recommended:

| Tool | Notes |
|---|---|
| **PixelLab.ai** | Purpose-built for game pixel art. Best for clean, consistent Minecraft-style output. |
| **Stable Diffusion + Pixel Art LoRA** | Most flexibility. Use a LoRA like `pixel-art-xl` or `mc_texture_lora`. Set resolution to 64×64 or 128×128 (upscaled), then downscale. |
| **Civitai Minecraft-texture models** | Several community models exist trained on vanilla Minecraft textures. Search "Minecraft texture". |
| **Bing Image Creator / DALL-E 3** | Generally poor at true pixel art — produces "stylized" output that needs heavy post-processing. Avoid unless other options unavailable. |
| **Midjourney** | Same issue — too smooth. Possible with `--style raw` and very specific prompting but inconsistent. |

## Post-Processing Every Output

Every generated image needs:

1. **Downscale to 16×16** (or 32×32 for higher detail). Use nearest-neighbor algorithm (not bilinear/lanczos which blur pixels).
2. **Palette-quantize** to 8-16 colors. In GIMP: `Image → Mode → Indexed → 16 colors`. In Aseprite: `Sprite → Color Mode → Indexed`.
3. **Remove anti-aliasing artifacts.** Manually erase any soft-edge pixels with a 1-pixel hard brush.
4. **Verify transparency.** Items need a transparent background; blocks need a solid one.

## Style Anchor (re-use in every prompt for consistency)

Append this to every texture prompt to lock the style:

> `, 16x16 pixel art, Minecraft block texture style, chunky pixels, limited color palette, no anti-aliasing, sharp pixel edges, retro game art, single-frame still image, transparent background where applicable`

For animated fluids, use `32x32` and drop "single-frame" (you'll get multiple frames or use multiple gens).

---

## Priority 1 — Core V1 Items (must-have for first build)

### Frog Net (item)

```
A Minecraft-style pixel art item icon of a small bug-catching net for a frog: short wooden stick handle on the diagonal lower-left to upper-right, with a small white mesh net loop attached at the top end. Wood handle is light brown with a darker grain pixel. Net mesh is white with visible cross-hatch pattern. Item viewed in standard inventory perspective.
```

+ style anchor

### Frog Egg (item)

```
A Minecraft-style pixel art item icon of a single frog egg in jelly: a clear translucent jelly outer shell containing a small dark green embryo dot inside. Round-to-oval shape, centered. Light highlight on upper-left of the jelly. Soft green-gray jelly color, transparent feel.
```

+ style anchor

### Frog Egg (block — unprimed)

```
A Minecraft-style pixel art block texture: top-down view of a cluster of frog eggs on water. Multiple semi-translucent green-gray jelly orbs touching each other, each with a tiny dark embryo dot. Subtle water-droplet shine. Pattern repeats seamlessly at edges (tileable).
```

+ style anchor

### Primed Frog Egg blocks (6 variants — same shape, different jelly color)

Use the same base prompt as Frog Egg (block), and override only the "jelly color" phrase per variant:

| Variant | Jelly color phrase |
|---|---|
| Metallic | `light iron-gray jelly with a slight metallic sheen` |
| Mineral | `dusty brown-red jelly with embedded redstone-orange flecks` |
| Gem | `pale cyan-blue translucent jelly with faceted crystal highlights` |
| Aquatic | `seafoam-teal jelly with prismarine-green undertones` |
| Infernal | `dark red-orange jelly with glowing magma-orange dots` |
| Arcane | `deep purple jelly with starfield-white speckle highlights` |

### Slime Milker — top face

```
A Minecraft-style pixel art block texture, top-down view: a metal hatch with a circular recessed slot in the center sized for a bucket. The slot is darker, with a hint of slime-green inside. Surrounding plate is gray industrial metal with small visible rivets on the corners. Subtle wear and scratches on the metal.
```

+ style anchor

### Slime Milker — front face

```
A Minecraft-style pixel art block texture, side view: the front of a mechanical press. A vertical metal piston in the center, descending into a small slime-green chamber at the bottom. Industrial gray-iron metal frame around. Two small bolts visible on either side of the piston. A tiny orange status indicator dot.
```

+ style anchor

### Slime Milker — side face

```
A Minecraft-style pixel art block texture, side view: a metal industrial panel with vertical ribbing. Three or four small visible cogwheels or gears mounted on the side, partially recessed. Gray-iron metal color with a brassy bronze accent on the cogs. Realistic worn-machine feel.
```

+ style anchor

### Slime Milker — bottom face

```
A Minecraft-style pixel art block texture: a simple metal underside of a machine. Plain gray-iron metal with rivets in each corner. Slight gradient toward the center (darker middle) suggesting weight. Functional, unornamented.
```

+ style anchor

### Slime Bucket — exterior (static, vanilla-bucket-style)

```
A Minecraft-style pixel art item icon: a standard iron bucket. Same color and shape as the vanilla minecraft iron bucket. Light gray metal with a darker bottom and a thin handle arc. Used as a base layer (will be layered with slime contents above it).
```

+ style anchor

### Slime Bucket — contents (tintable splash layer)

```
A Minecraft-style pixel art item icon: a near-white slime splash shape sitting inside a bucket outline. The slime contents are visible above the bucket rim - a rounded blob shape with a small bubble or two. Near-white grayscale gradient (will be tinted in-game by the variant color). Bucket outline is NOT visible (transparent everywhere except the slime).
```

+ style anchor (also append: `grayscale tintable mask`)

---

## Priority 2 — Resource Slime Visuals (tintable bases — drawn ONCE, reused for all variants)

### Resource Slime body (tintable base)

```
A Minecraft-style pixel art entity texture: a slime body, viewed as an unfolded texture map (like vanilla slime). Near-white grayscale with subtle internal shading - lighter highlights on top, slightly darker shadows on bottom. Translucent, jelly-like feel. The texture will be color-tinted at render time, so the base must be desaturated and have full tonal range from near-white to medium gray.
```

+ style anchor + `grayscale tintable mask, neutral tone for color multiplication`

### Resource Slime inner core (tintable overlay)

```
A Minecraft-style pixel art entity overlay texture: a small cube-shaped inner core visible through a translucent slime body. The cube is darker gray than the body, with sharp edges and a slight 3D shading (lighter top face, darker side faces). This will overlay the body texture and be color-tinted independently.
```

+ style anchor + `grayscale tintable mask`

---

## Priority 3 — Fluids (Slime Milk)

### Slime Milk still fluid (animated, 8 frames)

```
A Minecraft-style pixel art animated fluid texture: a thick lumpy liquid surface like slime. Subtle wave motion, near-white grayscale base (to be color-tinted in-game), with slight bubble or blob pulsations. 8 frames showing slow pulsing motion. Use 32x32 resolution per frame, vertical strip layout (256x32 total).
```

+ style anchor (modified: `32x32 per frame, vertical strip of 8 frames`)

### Slime Milk flowing fluid (animated, 8 frames)

```
A Minecraft-style pixel art animated fluid texture: lumpy slime liquid flowing downward in vertical streaks. Slow blob-like flow, near-white grayscale (to be tinted in-game). 8 frames showing droplets sliding downward. 32x32 per frame, vertical strip layout.
```

+ style anchor (modified: `32x32 per frame, vertical strip of 8 frames`)

### Slime Milk Bucket (combined, layered like Slime Bucket)

Same prompts as Slime Bucket exterior and contents — the milk bucket uses the same shape, just with milk-fluid contents instead of slime contents.

---

## Priority 4 — Parent Slime Species (unique, not tintable)

Each is a unique entity with its own permanent texture, drawn ONCE.

### Cave Slime (mineral category parent)

```
A Minecraft-style pixel art entity texture, slime body unfolded map: a dark gray stony slime body. Surface is matte and slightly granular like wet cave stone. Subtle darker speckles of coal-dust and small lighter dots of quartz embedded in the body. Inner core is visible as a darker gray cube. Cave-dweller vibe - mossy and slightly damp.
```

+ style anchor

### Geode Slime (gem category parent)

```
A Minecraft-style pixel art entity texture, slime body unfolded map: a pale translucent slime body with embedded prismatic crystals. Body is faceted-looking, with visible angular highlights in pale pink, lavender, and white - mimicking amethyst geode interiors. Inner core is a clear cube with rainbow refraction hints on the corners.
```

+ style anchor

### Tide Slime (aquatic category parent)

```
A Minecraft-style pixel art entity texture, slime body unfolded map: a translucent ocean-blue slime body. Surface shows small bubble patterns and faint flowing water lines. Light blue with darker navy depths. Inner core is a clear cube with a small bubble or seafoam highlight. Ocean-dweller feel - wet, moving, alive.
```

+ style anchor

### Void Slime (arcane category parent)

```
A Minecraft-style pixel art entity texture, slime body unfolded map: a deep dark purple slime body with tiny star-like speckles. Body has a slight inner glow suggesting void energy. The body texture includes hints of black-purple gradients with white pinpoint stars scattered across. Inner core is a deeper purple-black cube with faint ender-particle glow.
```

+ style anchor

---

## Priority 5 — Drop Blocks (Froglights, one per slime variant)

Drop blocks are visually similar to vanilla froglights — emissive, decorative, with the slime's resource color.

### Generic Froglight drop block (used for ALL variants, color comes from variant JSON via tint)

```
A Minecraft-style pixel art block texture: a glowing translucent froglight block, vanilla-style. Bumpy organic surface texture with internal blob patterns visible through the translucency. Near-white grayscale base with light tones - WILL BE COLOR-TINTED in-game per variant. Emissive look - slight glow around the bumpy patterns.
```

+ style anchor + `grayscale tintable mask`

Per-variant froglight is THIS base texture, tinted by the variant's `color_rgb`. No separate file per variant.

---

## Priority 6 — Resource Frogs (tintable base + tintable overlay)

Like slimes, frogs use a shared base texture with per-category tint. Each Resource Frog category has its own color set defined in JSON.

### Resource Frog body (tintable base)

```
A Minecraft-style pixel art entity texture: a frog body unfolded as a texture map, similar to vanilla minecraft frog. Near-white grayscale base for tinting. Includes visible skin texture, slightly wet/glossy look. Eyes are dark spots that remain dark regardless of tint. Mouth is a closed line. Standard frog proportions, vanilla-style.
```

+ style anchor + `grayscale tintable mask, vanilla frog proportions`

### Resource Frog back pattern overlay (tintable, second color)

```
A Minecraft-style pixel art entity overlay texture: a frog back pattern showing speckles and stripes that overlay the base body. Near-white grayscale (will be tinted to a darker shade of the variant color, giving the frog a two-tone look). The pattern includes small dots and one or two streaks across the back.
```

+ style anchor + `grayscale tintable mask`

---

## Color Reference for Resource Frogs

For each Resource Frog category, the JSON defines two colors (body + back pattern). Suggested:

| Category | Body `color_rgb` | Pattern `pattern_color_rgb` |
|---|---|---|
| Metallic | `[140, 140, 130]` | `[80, 80, 70]` |
| Mineral | `[180, 80, 60]` | `[110, 50, 40]` |
| Gem | `[120, 200, 220]` | `[60, 130, 150]` |
| Aquatic | `[70, 150, 130]` | `[30, 90, 80]` |
| Infernal | `[200, 90, 50]` | `[130, 50, 30]` |
| Arcane | `[110, 80, 180]` | `[60, 40, 120]` |

These ship in the Resource Frog category JSONs alongside the slime variant JSONs.

---

## Generation Workflow

1. Start with **Priority 1** prompts. Get the Frog Net, Frog Egg, Slime Milker faces working. These are the "first-launch" blockers.
2. Move to **Priority 2** — the shared tintable bases. These unlock ALL slime variants in one go.
3. **Priority 3** — fluid animations. Trickier (multi-frame); save until you have a stable pixel-art pipeline.
4. **Priority 4** — parent slime species. One-time investment, but each is a unique design.
5. **Priority 5** — generic Froglight base. One file, used everywhere.
6. **Priority 6** — Resource Frog base + pattern.

**Total unique textures**: ~25 files for all of V1 (vs. hundreds if every variant had its own).

## Iteration Tips

- Run each prompt **3-5 times** and pick the best result. Pixel art AI is high-variance.
- **Iterate on phrasing** if results are wrong — try "Minecraft" → "minecraft block" → "blocky game" → "voxel art" if the output is too smooth.
- **Reference vanilla textures** — describe the texture you want by saying "like vanilla minecraft [X]" for similar-shape items.
- **Save the seed** of good outputs so you can re-generate the same style for related textures.
