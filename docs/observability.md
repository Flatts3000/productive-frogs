# Observability

How Productive Frogs makes its own behavior legible at every layer, for
developer diagnosis. This is the design spec for a single cross-cutting debug
framework (`PFDebug`) that replaces the ad-hoc, add-then-delete logging we have
reached for during past investigations.

> **Status:** implemented (PR #106, 2026-05-25). All three phases landed: the
> `PFDebug` core, the `/pf debug` command, and instrumentation of every area.
> This doc is the design + per-layer contract; the phased roadmap at the bottom
> records the build order that was followed.

## Why this exists

Two failure modes have cost real time on this project:

1. **Client-render bugs are invisible to GameTest.** Tints, texture resolution,
   render types, UVs, and inner-block selection never appear in any automated
   test (`docs/testing.md` "visuals are blind"). During the 2026-05-25
   inner-block investigation, ~25 round-trips went into re-reading correct
   source while the running build diverged from it. A one-line render-thread log
   ended it in one launch. That log was added ad-hoc and then deleted.
2. **Gameplay resolution is silent.** Infusion, split-discovery, the tongue-drop
   category check, milker conversion, and source-block spawning all make
   decisions (matched this variant, rolled this chance, picked this position)
   with no trace. When one misbehaves, the only tool is reading code and
   guessing.

The fix is to make instrumentation **permanent, gated, and uniform**: one helper
called the same way at every layer, off by default (zero cost in production),
flipped on per-area when diagnosing. Add a new handler or renderer, add one
`PFDebug` line at its decision point, and that layer is observable forever.

## Principles

- **Cost when off.** The helper short-circuits on a single `volatile boolean`
  read, so no log I/O runs and a `Supplier` message is never built. Eager args
  and the `logOnce` dedup key are still evaluated by the caller before the gate,
  so hot paths (per-frame render, per-tick sensor, item color handlers) wrap the
  call in `PFDebug.on(area)` to avoid building the key + capturing lambda;
  rarely-firing event-driven sites call directly.
- **One mechanism, used everywhere.** No per-class loggers, no `System.out`, no
  bespoke flags. Every layer calls `PFDebug`. Consistency is the point: a dev
  who learns it once can turn on any layer.
- **Greppable, structured output.** Every line is prefixed `[PF/<area>]` and
  written through the existing `productivefrogs` SLF4J logger to
  `run/logs/latest.log`. `grep "\[PF/render\]" latest.log` isolates one layer.
- **Side-aware.** Render and tint areas only fire client-side; gameplay areas
  fire server-side. The framework is side-agnostic (a plain static gate) but the
  control surface documents which areas have effect on which side (see Control
  surface > side semantics).
- **Dedup on hot paths.** Per-entity render and per-tick sensor logging dedups by
  entity (and by a signature that changes when the resolved value changes), so a
  steady scene logs once per entity, not 60x/second.
- **Discoverable.** `/pf debug` with no args lists every area and its current
  state. Adding an area is one enum constant; the conventions section says where
  the log line goes.

## Non-goals

- **Player-facing telemetry / metrics endpoints.** No Prometheus, no counters
  exported anywhere. This is developer diagnosis, not production monitoring. A
  Minecraft mod has no metrics surface to ship to.
- **Crash reporting.** NeoForge already writes crash reports; we do not duplicate
  that.
- **Performance profiling.** Use a sampling profiler (JFR, async-profiler) or the
  vanilla `/debug` tick profiler. `PFDebug` logs decisions, not timings.
- **Replacing unconditional lifecycle logs.** The three existing
  `ProductiveFrogs.LOGGER.info/warn` calls (init, common-setup, inverted-interval
  warning) stay as-is. They are always-on operational signal, not gated debug.

## Area taxonomy

One area per layer. The set maps directly onto the package structure, so a dev
who knows where a class lives knows which area to enable.

| Area id | Layer | Classes | What it surfaces |
|---|---|---|---|
| `lifecycle` | Mod init / registration | `ProductiveFrogs`, `PF*` registers | register-pass order, element count per `DeferredRegister` |
| `registry` | Datapack registries | `PFDataPackRegistryEvents`, `SlimeVariant`, `ParentSpeciesEntry` | count loaded per registry, each variant id + category on load, decode failures + fallbacks |
| `config` | Mod config | `PFConfig` | resolved values at common-setup, validation warnings |
| `infusion` | Slime infusion | `SlimeInfusionHandler` | primer item id, matched variant (or none), species-lock pass/fail |
| `split` | Split discovery | `SlimeSplitDiscoveryHandler` | parent species -> category lookup, discovery roll vs chance, weighted pick |
| `tongue` | Frog kill drop | `FrogTongueDropHandler` | frog category, slime category, match bool, froglight emitted (variant vs category) |
| `egg` | Egg / bottle interactions | `EggPrimerHandler`, `FrogspawnBottlingHandler` | interaction outcome, resolved category |
| `sensor` | Frog AI targeting | `ResourceFrogAttackablesSensor` | candidate slime, category-filter pass/fail, chosen `NEAREST_ATTACKABLE` |
| `milker` | Slime Milker appliance | `SlimeMilkerBlock`, `SlimeMilkerBlockEntity` | input bucket variant, cook progress, output milk variant, fail-closed reason |
| `milk_source` | Slime Milk source block | `SlimeMilkSourceBlock` | next spawn tick scheduled, picked position + strategy, spawned entity + variant, `spawns_remaining` decrement, drain |
| `render` | Entity renderers | `ResourceSlimeRenderer`, `ParentSlimeRenderer`, `ResourceFrogRenderer`, `ResourceTadpoleRenderer`, the outer layers | per-entity resolved variant id, category, texture path, fallback-used flag, shell ARGB + its source (variant vs category) |
| `tint` | Item / block color handlers | the `RegisterColorHandlersEvent.Item` / `.Block` lambdas in `PFClientEvents` | item, resolved variant id, tintIndex, resolved ARGB |

Reserved keyword: **`all`** (matches every area; valid in the system property and
the command, never a real area id).

Granularity rationale: `render` and `tint` stay separate because they are
distinct failure surfaces (a render-type/alpha bug versus a component-resolution
bug). `milker` and `milk_source` stay separate because the appliance and the
source block are different concerns players debug independently. Everything else
is one area per handler class.

## API surface

A single class, `com.flatts.productivefrogs.util.PFDebug` (common code: no
client-only imports, so server and client both link it). Sketch, not final:

```java
public final class PFDebug {

    public enum Area {
        LIFECYCLE("lifecycle"), REGISTRY("registry"), CONFIG("config"),
        INFUSION("infusion"), SPLIT("split"), TONGUE("tongue"), EGG("egg"),
        SENSOR("sensor"), MILKER("milker"), MILK_SOURCE("milk_source"),
        RENDER("render"), TINT("tint");

        final String id;
        volatile boolean enabled;       // flipped by startup flags + command
        Area(String id) { this.id = id; }
    }

    /** The gate. A volatile read; cheap enough for per-frame hot paths. */
    public static boolean on(Area area) { return area.enabled; }

    /** Eager message (use only when args are already in hand). */
    public static void log(Area area, String msg) {
        if (area.enabled) ProductiveFrogs.LOGGER.info("[PF/{}] {}", area.id, msg);
    }

    public static void log(Area area, String fmt, Object... args) {
        if (area.enabled) ProductiveFrogs.LOGGER.info("[PF/" + area.id + "] " + fmt, args);
    }

    /** Lazy message: supplier runs only when the gate is open. Hot paths. */
    public static void log(Area area, Supplier<String> msg) {
        if (area.enabled) ProductiveFrogs.LOGGER.info("[PF/{}] {}", area.id, msg.get());
    }

    /**
     * Dedup-by-key. Logs once per (area, key); a steady render scene logs once
     * per entity instead of every frame. Fold the changing value into the key
     * (e.g. entityId + "/" + variantId) so a change re-emits.
     */
    public static void logOnce(Area area, Object key, Supplier<String> msg) {
        if (area.enabled && SEEN.add(area.id + ":" + key))
            ProductiveFrogs.LOGGER.info("[PF/{}] {}", area.id, msg.get());
    }

    // toggling an area on (via command) clears its dedup set so one-shots re-arm
    public static void setEnabled(Area area, boolean enabled) { ... resetDedup(area) ... }
}
```

Notes:

- **Emission level is INFO**, not DEBUG. The area gate *is* the filter; once a
  dev opts an area in, they want it in `latest.log` without also raising the
  log4j level. DEBUG would be swallowed by the default config.
- **Dedup set** is a `ConcurrentHashMap.newKeySet()` (render runs on the client
  thread, gameplay on the server thread; the set is touched from both in
  singleplayer). It is bounded by live entity count for render; cleared on
  area-toggle and may be cleared on world unload (Phase 1 detail).
- **Logger.** `PFDebug` holds its own SLF4J logger named `productivefrogs` (the
  same name as `ProductiveFrogs.LOGGER`, so output is identical), referenced via
  the inlined `ProductiveFrogs.MOD_ID` constant. That avoids loading the
  `ProductiveFrogs` mod class in unit tests. The `[PF/<area>]` prefix is what
  makes lines greppable and area-attributable.

## Control surface

Two ways to flip an area, per the chosen design (system property for startup,
in-game command for live iteration).

### Startup: system property

Read once, during mod construction, into the `Area.enabled` flags:

```
-Dproductivefrogs.debug=render,tint,infusion
-Dproductivefrogs.debug=all
```

Canonical form is a single comma-separated property listing area ids (or `all`).
Unknown ids log one warning and are ignored. This is the form to put in a run
config / `gradlew runClient --args` for a session that starts with an area on.
Bootstrap happens in the `ProductiveFrogs` constructor (server + client share
the entry point), before any handler or renderer runs.

### Live: in-game command

`RegisterCommandsEvent` registers a command tree on the server (integrated or
dedicated). Canonical literal `/productivefrogs`, with `/pf` as a convenience
alias:

```
/pf debug                 -> list every area and its on/off state
/pf debug <area> on|off   -> toggle one area (tab-completes area ids)
/pf debug all on|off      -> toggle every area
```

Permission level 2 (the op / cheat-command default): diagnostic, harmless, but
not something a non-op should flip. Toggling an area on also resets that area's
dedup set so per-entity one-shots re-fire immediately.

### Side semantics (important, document so it does not surprise)

The `Area.enabled` flags are static fields in the shared JVM. What that means per
deployment:

- **Singleplayer / LAN host:** one JVM hosts both the integrated server and the
  client, so `/pf debug render on` flips the flag the render thread reads. Every
  area is live-toggleable here. This is the common dev case.
- **Dedicated server:** no client, so `render` / `tint` areas have nothing to
  fire and the command only meaningfully affects gameplay areas. A connected
  client's render flags are a separate JVM and are not reached by the server
  command; set them with the system property at client launch.

So: the system property is the universal control (works on any side at startup);
the command is the fast path that fully covers singleplayer dev and the
gameplay areas of a dedicated server. We do not (in this spec) register a
client-side command to reach a remote client's render flags; that is a possible
later addition if it is ever needed.

## Per-layer instrumentation plan

What each area logs and where the call site goes. This is the contract Phase 2/3
implements; Phase 1 implements only `render` + `tint`.

### render (Phase 1, client)

- `ResourceSlimeRenderer.getTextureLocation` -> `logOnce(RENDER, entityId + "/" + variantId, ...)`:
  entity id, resolved variant id (or null), category, final texture path, whether
  the category fallback or the BOG default was used.
- `ResourceSlimeOuterLayer.resolveShellTint` -> same dedup key: resolved shell
  ARGB (hex) and whether it came from the variant `primary_color` or
  `Category.shellTintArgb()`.
- `ParentSlimeRenderer` / `TintedSlimeOuterLayer`: pinned texture + tint at
  construction (one-shot per species renderer).
- `ResourceFrogRenderer`, `ResourceTadpoleRenderer`, `CategoryTintLayer`: resolved
  category + tint per entity (dedup by entity id).

### tint (Phase 1, client)

MC 1.21.1 has no JSON `ItemTintSource` pipeline (that is 1.21.4+); the tint logic
lives in the per-item / per-block lambdas registered in
`PFClientEvents.onRegisterItemColors` / `onRegisterBlockColors`. The `tint` area
instruments the registry-backed resolution points (where the
`slime_variant` component resolves to a `SlimeVariant.primaryColor`), which is the
bug-prone chain:

- Slime Bucket item color (variant -> primary color).
- Configurable Froglight item color.
- Resource Slime spawn egg color (primary for tintIndex 0, secondary for 1).
- Configurable Froglight block color (BE variant -> primary color).

Each logs `logOnce(TINT, item + "/" + variantId, ...)`: the item, the resolved
variant id, the tint index, and the resolved ARGB. The simple category-only
lambdas (Frog Egg bottle, Primed Frog Egg items, Tadpole Bucket) are left
uninstrumented as low-value (a constant `Category.tintRgb()` with no registry
hop).

### infusion (Phase 2, server)

- `SlimeInfusionHandler` -> primer item id, the variant `findByPrimerItem`
  returned (or none), the target species-lock check result, the resulting
  `ResourceSlime` variant + category.

### split (Phase 2, server)

- `SlimeSplitDiscoveryHandler` -> parent EntityType id -> category lookup result,
  the discovery chance read from config, the rolled value, pass/fail, and on pass
  the `pickWeighted` variant chosen.

### tongue (Phase 2, server)

- `FrogTongueDropHandler` -> frog category, slime category, the equality check,
  and which froglight was emitted (variant-stamped `configurable_froglight` vs the
  category froglight) or why nothing dropped.

### egg (Phase 2, server)

- `EggPrimerHandler` -> matched primer tag, the `Category.values()` iteration hit,
  the placed Primed Frog Egg block id.
- `FrogspawnBottlingHandler` -> the bottle outcome (frogspawn consumed, Frog Egg
  item produced).

### sensor (Phase 2, client-driven AI, runs server-side on entities)

- `ResourceFrogAttackablesSensor` -> per scan, the candidate slimes considered,
  each one's category-filter pass/fail, and the entity written to
  `NEAREST_ATTACKABLE`. Dedup by frog id + chosen target id (so it logs when the
  target changes, not every tick).

### milker (Phase 3, server)

- `SlimeMilkerBlockEntity` -> input bucket variant parsed, cook tick progress at
  start/finish, output milk variant, and the fail-closed reason when a bucket has
  no variant tag or an unknown variant.

### milk_source (Phase 3, server)

- `SlimeMilkSourceBlock` -> scheduled next spawn tick, the picked spawn position
  and which scan strategy found it (rim / diagonal / below / inside-fallback), the
  spawned entity type + variant, the `spawns_remaining` decrement, and the drain
  to air at zero.

### registry / config / lifecycle (Phase 3, server)

- `registry`: a `ServerStartedEvent` handler in `PFDataPackRegistryEvents` dumps
  each datapack registry's loaded count and every entry's mapping (slime_variant
  -> category + primer; parent_species -> entity_type + category) once the codecs
  have decoded all JSON. A server-started dump is simpler and more reliable than
  hooking the codec decode path, and shows the post-merge result a datapack sees.
- `config`: `PFConfig` values logged once at common-setup (depletion toggle +
  count, spawn interval, discovery chance).
- `lifecycle`: the slime-milk variant count logged at common-setup. Per-register
  element counts aren't cheaply available from `DeferredRegister` at register
  time, so the variant total stands in as the load-bearing count.

## Testing

`PFDebug`'s gate logic is plain Java with no Minecraft runtime dependency, so it
is unit-testable (JUnit, the existing `src/test` harness). A `PFDebugTest`
covers:

- default state is off for every area;
- `parseStartupFlags("render,tint")` enables exactly those;
- `parseStartupFlags("all")` enables every area; an unknown id is ignored (and
  does not throw);
- `setEnabled` flips the flag and `logOnce` re-fires after a toggle (dedup reset);
- `logOnce` with a stable key fires once; a changed key fires again;
- `on(area)` is false (and the supplier is never invoked) when the area is off.

The supplier-not-invoked-when-off assertion is the one that protects the
zero-cost promise; pin it with a supplier that flips a test flag and assert the
flag stays false.

Render correctness itself stays un-GameTestable (the engine renders nothing
headless). The `render` area is the substitute: one client launch with
`-Dproductivefrogs.debug=render`, one spawn, read the resolved values in
`latest.log`. This is the workflow the v1.1 investigation proved out, now
permanent. Cross-reference `docs/testing.md` "visuals are blind".

## Performance

- **Gate:** one `volatile boolean` read per call site. On the per-frame render
  path that is one read per entity per frame, which is negligible next to the
  draw call it guards.
- **When all areas are off (production default):** every call site is a single
  false branch; no allocation, no string work, suppliers never run.
- **Dedup set:** bounded by live entity count for render; event-driven areas log
  unconditionally (low frequency) and do not touch it. Cleared on area-toggle.
- **No I/O on the hot path beyond the gated log itself,** which only happens when
  a dev has opted in.

## Conventions: adding observability to a new layer

When you add a handler, renderer, block entity, or registry, give it
observability in the same PR:

1. If it is a genuinely new layer, add one `Area` enum constant (id =
   snake_case) and a row to the taxonomy table above.
2. At the class's decision point (where it resolves a variant, picks a target,
   computes a tint, converts an item), add a guarded `PFDebug.log(...)` /
   `logOnce(...)` reporting the inputs and the chosen output.
3. Prefer `logOnce` (keyed by entity + resolved value) on per-tick / per-frame
   paths; plain `log` on event-driven paths.

The bar: someone debugging that layer should be able to enable its area and read
exactly what it decided and why, without editing code.

## Phased roadmap

Each phase is a PR-sized slice. Phase 1 delivers the framework plus the two
areas that motivated it; later phases are pure call-site additions.

- **Phase 1 - core + client-render areas.** `PFDebug` (Area enum, gate,
  `log`/`logOnce`, dedup, startup-flag parse), the `/pf debug` command via
  `RegisterCommandsEvent`, `PFDebugTest`, and instrumentation of the `render` +
  `tint` areas. This alone replaces the delete-after ad-hoc render logging.
- **Phase 2 - gameplay server areas.** `infusion`, `split`, `tongue`, `egg`,
  `sensor` call sites.
- **Phase 3 - blocks, registries, lifecycle.** `milker`, `milk_source`,
  `registry`, `config`, `lifecycle` call sites.

## Relationship to existing docs

- `docs/testing.md` "visuals are blind" documents why render needs this; the
  `render` area is the answer.
- `docs/backlog.md` Tooling has the original one-line "debug observability flags"
  item; it points here now.
- The memory note on stale-build-first render debugging (the v1.1 lesson) is the
  origin story; `PFDebug` is the permanent form of that one-line render log.
