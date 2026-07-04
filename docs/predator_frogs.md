# Predator Frogs + mob predation - delivery roadmap

> **What this is.** The phased implementation plan for the "frogs eat mobs" redesign (epic [#281](https://github.com/Flatts3000/productive-frogs/issues/281)). The epic issue is the authoritative **spec** (decisions + acceptance criteria); this doc is the **runway** - how the system is built and shipped in dependency order. Read #281 first.
>
> **One-line thesis.** All mob drops come from a frog eating the mob. A bred **Predator Frog** eats vanilla mobs in the world (live entity, like a Resource Frog); bosses are eaten at their altars by a bred **Apex Frog**. **No enclosure blocks** - the hard classes are handled by Basin-spawned teleport-disable + the amphibious Gulper, plus ordinary player-built containment, fed by a waterloggable **Slurry Basin** (and a sibling **Slime Milk Basin** on the slime side). XP is a fluid at altars, orbs in the open. No Froglight from a vanilla-mob eat.

## Scope: this IS 2.0.0

The predation system is the **defining feature of the 2.0.0 release** - it is what earns the major bump over the 1.x line. The 26.1 port is the *platform substrate*; on its own it would be "1.x on 26.1," a port, not a major version. This epic is what makes 2.0 a 2.0.

**Sequencing:**
- The green `port/mc-26.1` branch **merges to `main`** as the 2.0.0 **foundation** - `main` becomes 26.1 and enters 2.0.0 development. This merge is branch integration, not a release.
- Phases 1-5 below are the **2.0.0 development sequence** (dev + beta builds on `main`), not separate minor releases.
- **2.0.0 releases when the predation system lands** (Phase 4 at minimum; Phase 5 polish may trail into a 2.0.x). It does not release on the bare port.
- #281's **2.0.0 milestone is correct** - no re-milestoning. The Froglight-payout -> raw-drops shift and the Apex-Frog altar gate are 2.0.0 content (Phase 4).

**Supersedes:** `docs/port_mc_26_1.md` Phase 9 ("release 2.0.0 once the build is green ... does not wait on partner mods") predates this scope decision. The port merge is the foundation, but the 2.0.0 *release* is gated on this epic, not on the bare green port. (Still true that it does not wait on partner *mods* - cross-mod integrations remain deferred to 2.x minors.)

## Dependency graph

```
Phase 1  Predator tier + eat path + frog abilities  (foundational; everything hangs off the tier)
   |
   +--> Phase 2  Liquid Experience                   (small, self-contained fluid)
   |
   +--> Phase 3  Ender Net + Slurry Press + Basin     (supply: makes the eat path farmable)
   |
   +--> Phase 4  Apex frogs + boss altars             (needs tier + LE; two new altars #279/#280)
            |
            v
        Phase 5  Retirement sweep + polish + guide
```

Nixing the enclosures removed a whole phase: the fly/swim/teleport classes are now handled by frog abilities (folded into Phase 1) plus the Slurry Basin (Phase 3), not a dedicated build. Phases 2, 3, and 4 can run in parallel once Phase 1 lands. Phase 5 closes out.

---

## Phase 1 - Predator tier + eat path + frog abilities  (size: XL, foundational)

> **STATUS: IMPLEMENTED** on `feat/predator-frogs-phase1` (2026-07-03). Two architecture notes from the build:
> - Identity was refactored to a **unified sealed `FrogKind`** (resource/<category>, midas, predator/<kind>) replacing the Category-ordinal + Midas-boolean pair, per the maintainer's "refactor into a better pattern" ruling - one synced string, exhaustive-switch branch sites, so the Apex tier fails compilation everywhere a decision is owed.
> - The eat kill is a **fake-player kill**: a server FakePlayer wielding a looting-N sword (N = the Bounty mapping) deals lethal damage, so the prey dies a genuine player death - player-gated drops, looting, and XP orbs all flow from the vanilla death pipeline with zero loot-table emulation. Prey edibility bypasses `frog_food` (tagging prey would make vanilla frogs hunt it) via a PF `PFShootTongue` behavior + the `predator_prey` registry.
> - The variant retirement ("Breaking" below) was deliberately NOT started here - deferred whole to Phase 5 so the cull is one coordinated, maintainer-reviewed sweep.

The end-to-end slice that proves the thesis: a bred Predator Frog eats a mob and drops its player-kill loot + XP orbs. Predators are **live world entities** (like Resource Frogs). Works day-one off natural spawns; the Slurry farm comes in Phase 3.

**Ships:**
- The **Predator Frog tier** - Prowler (overworld), Cinder (nether), Gulper (aquatic), Rift (end). A new tier/flag, **not** a 7th `Category` (Midas precedent). New EntityType(s), egg items, tadpoles, renderers (subclass + tint).
- **Breeding cross** - the settled resource-species pair map (Bog x Cave -> Prowler, Infernal x Geode -> Cinder, Tide x Bog -> Gulper, Void x Geode -> Rift) deterministically yields a predator tadpole; same-environment predators breed true; all other cross pairs still cannot mate. Stats (Appetite/Bounty/Reach) inherit through the cross.
- **Eat path (all non-boss classes)** - extend `ResourceFrogAttackablesSensor` + `FrogTongueDropHandler` to vanilla mobs, gated so a predator only targets its environment's mobs and the six resource frogs are untouched (both-layer mutual exclusion preserved). The sensor targets any eligible class in range (ground open, flyers in a player box, aquatic in water, teleporters that were Basin-spawned so teleport is disabled).
- **Basin-spawned teleport-disable + underwater breathing** - a mob spawned by a Mob Slurry Basin has **teleportation disabled by default** (a spawn-time flag), so a Basin-farmed enderman/shulker can't escape and the frog eats it (no aura; Prowler farms enderman by first-encounter, Rift the shulker). Underwater breathing turned out to be **vanilla parity, not a Gulper ability**: vanilla frogs breathe underwater via the `can_breathe_under_water` EntityType tag (tag-driven on 1.21.1 too - a subclassed EntityType is never in its parent's tags, so PF frogs had been drowning since v1.0; hotfixed onto the 1.x line as well). PF ships tag entries for its frog + tadpole and ALL PF frogs breathe underwater; the Gulper's distinction is purely its aquatic prey set (the vanilla tongue already works underwater). These replace the enclosures entirely.
- **Environment rule** - a mob's frog = where the player first encounters it (enderman -> overworld / Prowler).
- **Data-driven mob eligibility** - hand-authored per-mob class/environment JSON (the map on #281 is the seed), excluding no-kill-drop mobs. Datapack-overridable.
- **Player-kill loot** - roll the mob's loot table as a player kill (player-gated drops included) with looting = Bounty tier; loot drops on the ground (hoppers collect, like Froglights); XP as **vanilla orbs**.
- **Config flag** for the whole predation system (default ON, v1.18 config-suite posture).

**Breaking:** begins retiring the mob-derived slime variants (bone, string, leather, feather, gunpowder, rotten flesh, ...) - items lose names/recipes as in the v1.6 retirement. Coordinate with Phase 5's full sweep.

**GameTest:** breeding-cross determinism + breed-true, both-direction sensor gating (resource vs predator vs slime), eat-path loot roll + Bounty->looting mapping, Rift teleport-suppression, Gulper amphibious eat.

**Internal build order:** frog-tier infra + breeding first (entities exist and breed), then the eat path + abilities on top.

---

## Phase 2 - Liquid Experience  (size: S)

> **STATUS: IMPLEMENTED** on `feat/liquid-experience-phase2` (2026-07-03). Pattern notes from the build:
> - Deliberately the **simplest fluid shape in the mod** - a third shape alongside milk and molten, chosen fresh rather than cloning either: no data components (XP is fungible, so NeoForge's STOCK `BucketResourceHandler` serves the bucket - a first for PF), no source block / BE (never placeable, "no world pools" is enforced by construction), but with a bucket (molten has none).
> - The 20 mB/point ratio turned out to be the **NeoForge-documented standard** on `Tags.Fluids.EXPERIENCE` itself, not just community convention - the constants + helpers live on `LiquidExperienceFluid`, pinned by a unit test.
> - The bucket's right-click IS the spend path: drink it, absorb exactly 50 points, keep the empty bucket. No orb scatter, no new block needed.
> - Gotcha for future transfer-API tests: `ItemAccess.forStack` never swaps the underlying Item, so a bucket drain (full -> empty bucket) through it silently moves 0 mB - use a player-slot / handler-slot access.

The XP fluid. Small and self-contained; unblocks builds banking XP.

**Ships:**
- `liquid_experience` fluid (source-only is fine - lives in tanks/pipes, no world pools), member of vanilla `c:experience` at **20 mB = 1 XP point**.
- Bucket + fluid-handler capability so PF and third-party XP tanks/pipes/drains move it; exact-volume round-trip (no drift at the 20 mB boundary).
- A drain/spend path back to a player (or rely on an in-pack `c:experience` consumer; tag interop alone satisfies the AC).

**GameTest:** 20 mB/point conservation, `c:experience` tag interop (any tank exposing the tag round-trips PF's fluid).

**Note:** open predation stays orbs (Phase 1, no build to bank it); this fluid only ever fills the boss-altar Hatch tanks (Phase 4) and serves `c:experience` interop.

---

## Phase 3 - Ender Net + Slurry Press + Slurry Basin  (size: L)

> **STATUS: IMPLEMENTED** on `feat/predator-frogs-phase3` (2026-07-03). Pattern notes from the build:
> - The net mechanic was **refactored into a shared `EntityNetItem` base** (capture / release / whole-entity `saveWithoutId` round-trip / tamper-checked release) rather than cloning the Frog Net - each net supplies only its catch gate and config flag. The Frog Net's behaviour is unchanged.
> - Mob Slurry is the **R-1 model a third time**: ONE `mob_slurry` fluid + a `SLURRIED_ENTITY` EntityType-id component, carried through tanks/pipes by the same `MilkBucketFluidResourceHandler` as milk (with the full budget/catalyst component set - parity by literally sharing the handler). Like Liquid Experience it has **no block form**, which is what makes the waterlogged Basin trivially safe: the fluid can never exist in the world, so there is nothing for pool water to mix with or wash away.
> - The Press runs a **flat 100-tick cycle**, not the spawn economy - the economy (budget, catalysts, cadence) lives downstream on the Basin, exactly where it lives for milk. Boss rejection (`c:bosses`) is enforced at slot-insert AND re-checked at tick (tampered NBT stalls inert).
> - The two Basins share one **`AbstractBasinBlockEntity` engine** (economy state, catalyst right-click + bucket component round-trip, fill-only pipe intake on the Terrarium funnel pattern, drain-to-bucket) - the parity principle enforced by the shared class being the feature set. The maintainer's spawn-placement order (horizontal ring, then above, then below, any of the 26 cells) is collision-checked per spawned entity, so tall mobs and water cells both work. A depleted Basin EMPTIES (the block persists) - that is its whole advantage over the milk source, which drains to air.
> - Every slurry-spawned mob is stamped with the Phase 1 `TELEPORT_DISABLED` attachment at creation (uniformly - a no-op for non-teleporters). (The Basin/Controller boss-milk refusals retired with the catalyst altars in Phase 5.)

The supply chain that makes the eat path farmable.

**Ships:**
- **Ender Net** - new item, ender-themed color scheme, captures **any** living mob, whole-entity round-trip (`saveWithoutId`, the #210 lesson). Frog Net stays frog-only.
- **Slurry Press** - new appliance, standard furnace-style shape (Block + BE + Inventory + Menu + Screen; copy the Milker/Churn). Filled Ender Net + empty bucket in -> `<Mob> Slurry` bucket out + empty net returned. Bucket output (no internal tank), mirroring the Churn. Rejects boss-mob nets.
- **Mob Slurry** - one fluid + mob-type data component (R-1 model), `%s Slurry` lang template.
- **Mob Slurry Basin** - a **waterloggable container block** that holds any mob's slurry inside the block (fill by bucket or fluid pipe) and spawns it on the `MilkSpawnEconomy` (budget + catalysts). One block for every mob; **works waterlogged or dry** (waterlog it so aquatic mobs survive). The slurry never becomes a world fluid, so a waterlogged Basin coexists with the pool with no mixing or washing-away.
- **Slime Milk Basin** - the parallel block on the slime side: a waterloggable container holding any variant's Slime Milk, spawning Resource Slimes, working wet or dry. **Additive** - the existing Slime Milk source block stays (both coexist).

**Parity principle:** Mob Slurry and Slime Milk are two separate paths that **mostly** share features - spawn economy, catalysts (Bountiful/Rapid/Teeming/Endless), buckets, pipes/tanks, Basin behavior all match. The primary divergence is the production appliance (Slurry Press vs Milker); other differences only where the mechanics require them. Default to parity.

**GameTest:** Ender Net capture round-trip, Slurry Press (net + bucket -> Slurry bucket + empty net, boss rejection), Mob Slurry Basin + Slime Milk Basin spawn economy (each works waterlogged and dry).

---

> **Phase removed.** The former Phase 4 was the fly/swim/teleport enclosures (Aviary / Aquarium / Endarium + Enclosure Hatch + a virtual eat cycle). The 2026-07-03 decision to nix all enclosures deleted it: flyers get a player box, aquatic mobs the amphibious Gulper in a water pool, teleporters are Basin-spawned with teleport disabled - all folded into Phase 1's abilities + Phase 3's Slurry Basin. No enclosure blocks ship.

## Phase 4 - Apex Frogs + boss altars  (size: L)

> **STATUS: 4a + 4b IMPLEMENTED** (4a 2026-07-03; 4b 2026-07-04 - the Warden "Shrieker Pit" #279 + the Elder Guardian "Monument Well" #280, on the extracted shared `BossAltarHatchBlockEntity` machinery; see `docs/warden_altar.md` / `docs/elder_altar.md`). Notes:
> - `FrogKind.Apex` is the sealed hierarchy's 4th permit - adding it made the compiler enumerate every decision site (lay carrier, sensor diet, breeding rules), exactly as the Phase 1 design promised. Apex breeds true; the four predator crosses (Cinder x Prowler -> Wither, Rift x Cinder -> Dragon, Gulper x Prowler -> Elder, Prowler x Rift -> Warden) conceive it; `bossEntityId()` keys each Apex to its boss.
> - The altar retrofit is COMPOSITION: one shared `AltarApexDock` (installed-frog net NBT + the Liquid Experience bank + the armed() gate) owned by each hatch BE. Shift-right-click with a net holding the MATCHING Apex installs it (net returns empty); the display frog (Dragonsbane/Witherbane) now renders only while armed; breaking the hatch respawns the REAL frog, stats intact. When `predators.enabled` is off the requirement is waived so predation-disabled packs keep working altars.
> - XP pays as Liquid Experience into the dock (20 mB/point, 64-bucket bank, extract-only `Fluid.BLOCK`; overflow -> orbs, never voided). Drops are RAW (the settled ruling): dragon's breath + egg, the wither's unstripped loot + an explicit raw Nether Star (the table only stars a player-credited kill, so it stays explicit + strip-guarded against double-pay).

The boss tier and the altar retrofit + two new altars.

**Ships:**
- **Apex Frog tier** - one per boss: Wither / Dragon / Elder / Warden Apex Frog. Bred from the settled cross-environment predator pairs (Cinder x Prowler -> Wither, Rift x Cinder -> Dragon, Gulper x Prowler -> Elder, Prowler x Rift -> Warden). Each eats only its own boss (both-layer gating).
- **Altar retrofit** (Wither #247, Dragon #249) - keep every shipped mechanic; the only two deltas: the altar runs only with its matching Apex Frog installed (net-install on the Hatch, data+render, drop-on-break), and XP pays as **Liquid Experience** into the Hatch tank instead of orbs. The already-settled raw-drops-not-Froglights payout applies.
- **New altars** - Elder Guardian (#280) and Warden (#279), shipping with the Apex-Frog gate + Liquid Experience from the start.

**GameTest:** apex cross-breeding determinism, apex-only eat gating, altar requires-frog (no frog -> no summon), install-reject-release round-trip, Liquid Experience payout.

---

## Phase 5 - Retirement sweep + polish + guide  (size: M)

> **STATUS: DELIVERED** (2026-07-04, minus the Patchouli entries - still blocked on the 26.1 port, tracked separately). The 21 mob-derived variants and the whole catalyst-altar mechanism retired (kept by ruling: armadillo scute, turtle scute, honeycomb - husbandry, not kill loot); the four predation milestones shipped on the new `predation_milestone` trigger; JEI grew the Mob Slurry subtype + info pages for the predation chain; `variants.bossVariantsEnabled` folded into `boss.enabled`. Breaking note in CHANGELOG under [Unreleased].

Close-out.

**Ships:**
- **Full mob-derived variant retirement** - audit the `slime_variant` JSONs, remove every variant that stood in for a mob drop (enumerated from the tree at this point), confirm ore/resource variants untouched. One coordinated breaking note.
- **JEI + Jade** for the new appliances/blocks (Slurry Press, Slurry Basin, altars); subtype interpreters where a component-carrying item needs distinct entries.
- **Advancements** for the new milestones (first predator bred, first apex, first mob farmed, boss farmed via frog).
- **Patchouli guide** entries (deferred until Patchouli ports to 26.1 - do not block the phase on it).
- Config consolidation + CHANGELOG + this doc marked delivered.

---

## Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| Breeding-cross touches `canMate` / vanilla breeding | High | Phase 1 adds designated-pair exceptions without loosening the six-species `canMate`; GameTest both the new crosses and that undesignated pairs still refuse. |
| Sensor/drop extension weakens the six-species gating | High | Preserve both-layer mutual exclusion; GameTest that resource frogs never eat mobs and predators never eat slimes, both directions. |
| Variant retirement breaks existing packs/worlds | Medium | Follow the v1.6 retirement pattern (items lose names/recipes, soft where possible); one coordinated breaking note; stage ground-class in Phase 1, remainder in Phase 5. |
| Liquid Experience mis-tagged -> no cross-mod interop | Medium | Test against the `c:experience` tag, not a specific mod; pin the 20 mB/point ratio in a unit test. |
| Whole-entity round-trip drops stats (Ender Net + altar install) | Medium | `saveWithoutId` everywhere an entity serializes (the #210 lesson); GameTest capture -> release and install -> release conservation on every removal path. |
| Waterlogged Basin fights the water pool (mixing / washing / suffocation) | Medium | Fluid stays inside the block (never a world fluid); Basin is waterloggable and works wet or dry; GameTest that a waterlogged Basin spawns into the surrounding water without disturbing the pool. |
| Basin teleport-disable mis-applied (leaks to wild mobs, or breaks vanilla) | Low | Flag lives on the Basin-spawned entity only; wild teleporters keep vanilla behaviour; GameTest a Basin-spawned enderman/shulker cannot teleport while a wild one still can. |

## Deferred to implementation time (not open design questions)

- The **full hand-authored mob -> class/environment JSON** for the vanilla roster (the mob-handling map on #281 is the seed).
- The **final retired-variant list**, enumerated from the `slime_variant` tree at Phase 5.

## Cross-references

- Spec + acceptance criteria: [#281](https://github.com/Flatts3000/productive-frogs/issues/281)
- New altars: [#279](https://github.com/Flatts3000/productive-frogs/issues/279) (Warden), [#280](https://github.com/Flatts3000/productive-frogs/issues/280) (Elder Guardian)
- Reuses: `MilkSpawnEconomy` (spawn budget), the R-1 single-fluid+component model (`docs/port_mc_26_1_reimplementation.md`), the appliance shape (`docs/slime_churn.md`), the altar Hatch convention (`docs/dragon_altar.md`).
