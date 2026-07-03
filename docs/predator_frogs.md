# Predator Frogs + mob predation - delivery roadmap

> **What this is.** The phased implementation plan for the "frogs eat mobs" redesign (epic [#281](https://github.com/Flatts3000/productive-frogs/issues/281)). The epic issue is the authoritative **spec** (decisions + acceptance criteria); this doc is the **runway** - how the system is built and shipped in dependency order. Read #281 first.
>
> **One-line thesis.** All mob drops come from a frog eating the mob. Ground mobs are eaten in the open by a bred **Predator Frog**; fly/swim/teleport mobs are eaten inside a per-class enclosure; bosses are eaten at their altars by a bred **Apex Frog**. XP is a fluid. No Froglight is produced from a vanilla-mob eat.

## Scope: this IS 2.0.0

The predation system is the **defining feature of the 2.0.0 release** - it is what earns the major bump over the 1.x line. The 26.1 port is the *platform substrate*; on its own it would be "1.x on 26.1," a port, not a major version. This epic is what makes 2.0 a 2.0.

**Sequencing:**
- The green `port/mc-26.1` branch **merges to `main`** as the 2.0.0 **foundation** - `main` becomes 26.1 and enters 2.0.0 development. This merge is branch integration, not a release.
- Phases 1-6 below are the **2.0.0 development sequence** (dev + beta builds on `main`), not separate minor releases.
- **2.0.0 releases when the predation system lands** (Phase 5 at minimum; Phase 6 polish may trail into a 2.0.x). It does not release on the bare port.
- #281's **2.0.0 milestone is correct** - no re-milestoning. The Froglight-payout -> raw-drops shift and the Apex-Frog altar gate are 2.0.0 content (Phase 5).

**Supersedes:** `docs/port_mc_26_1.md` Phase 9 ("release 2.0.0 once the build is green ... does not wait on partner mods") predates this scope decision. The port merge is the foundation, but the 2.0.0 *release* is gated on this epic, not on the bare green port. (Still true that it does not wait on partner *mods* - cross-mod integrations remain deferred to 2.x minors.)

## Dependency graph

```
Phase 1  Predator tier + ground eat path   (foundational; everything hangs off the tier)
   |
   +--> Phase 2  Liquid Experience          (small, self-contained fluid)
   |
   +--> Phase 3  Ender Net + Slurry Press    (supply: makes the ground path farmable)
   |        |
   |        v
   +----> Phase 4  Class enclosures          (needs tier + LE + Slurry)
   |
   +--> Phase 5  Apex frogs + boss altars    (needs tier + LE; two new altars #279/#280)
            |
            v
        Phase 6  Retirement sweep + polish + guide
```

Phases 2, 3, and 5 can run in parallel once Phase 1 lands. Phase 4 is the join point (needs 1 + 2 + 3). Phase 6 closes out.

---

## Phase 1 - Predator tier + the ground eat path  (size: XL, foundational)

The smallest end-to-end slice that proves the thesis: a bred Predator Frog eats a wild ground mob and drops its player-kill loot + XP orbs. Works day-one off natural mob spawns; the Slurry farm comes in Phase 3.

**Ships:**
- The **Predator Frog tier** - Prowler (overworld), Cinder (nether), Gulper (aquatic), Rift (end). A new tier/flag, **not** a 7th `Category` (Midas precedent). New EntityType(s), egg items, tadpoles, renderers (subclass + tint).
- **Breeding cross** - the settled resource-species pair map (Bog x Cave -> Prowler, Infernal x Geode -> Cinder, Tide x Bog -> Gulper, Void x Geode -> Rift) deterministically yields a predator tadpole; same-environment predators breed true; all other cross pairs still cannot mate. Stats (Appetite/Bounty/Reach) inherit through the cross.
- **Ground eat path** - extend `ResourceFrogAttackablesSensor` + `FrogTongueDropHandler` to vanilla mobs, gated so a predator only targets its environment's ground mobs and the six resource frogs are untouched (both-layer mutual exclusion preserved).
- **Data-driven mob eligibility** - hand-authored per-mob class/environment JSON (ground/walk mobs first). Datapack-overridable.
- **Player-kill loot** - roll the mob's loot table as a player kill (player-gated drops included) with looting = Bounty tier; emit the mob's XP as **vanilla orbs** at the eat site.
- **Config flag** for the whole predation system (default ON, v1.18 config-suite posture).

**Breaking:** begins retiring the ground mob-derived slime variants (bone, string, leather, feather, gunpowder, rotten flesh, ...) - items lose names/recipes as in the v1.6 retirement. Coordinate with Phase 6's full sweep; retire only the ground-class ones here.

**GameTest:** breeding-cross determinism + breed-true, both-direction sensor gating (resource vs predator vs slime), eat-path loot roll + Bounty->looting mapping.

**Internal build order:** frog-tier infra + breeding first (entities exist and breed), then the eat path on top.

---

## Phase 2 - Liquid Experience  (size: S)

The XP fluid. Small and self-contained; unblocks builds banking XP.

**Ships:**
- `liquid_experience` fluid (source-only is fine - lives in tanks/pipes, no world pools), member of vanilla `c:experience` at **20 mB = 1 XP point**.
- Bucket + fluid-handler capability so PF and third-party XP tanks/pipes/drains move it; exact-volume round-trip (no drift at the 20 mB boundary).
- A drain/spend path back to a player (or rely on an in-pack `c:experience` consumer; tag interop alone satisfies the AC).

**GameTest:** 20 mB/point conservation, `c:experience` tag interop (any tank exposing the tag round-trips PF's fluid).

**Note:** open-world predator eats stay orbs (Phase 1); this fluid only ever fills build tanks (Phases 4 + 5).

---

## Phase 3 - Ender Net + Slurry Press + Mob Slurry  (size: L)

The supply chain that makes the ground path farmable and feeds the enclosures.

**Ships:**
- **Ender Net** - new item, ender-themed color scheme, captures **any** living mob, whole-entity round-trip (`saveWithoutId`, the #210 lesson). Frog Net stays frog-only.
- **Slurry Press** - new appliance, standard furnace-style shape (Block + BE + Inventory + Menu + Screen; copy the Milker/Churn). Filled Ender Net + empty bucket in -> `<Mob> Slurry` bucket out + empty net returned. Bucket output (no internal tank), mirroring the Churn. Rejects boss-mob nets.
- **Mob Slurry** - one fluid + mob-type data component (R-1 model), `%s Slurry` lang template. A placed Slurry source spawns its mob on the `MilkSpawnEconomy` (budget + catalysts), exactly like a Slime Milk source.

**GameTest:** Ender Net capture round-trip, Slurry Press (net + bucket -> Slurry bucket + empty net, boss rejection), placed Slurry source spawn economy.

---

## Phase 4 - Class enclosures: Aviary / Aquarium / Endarium  (size: L)

The fly/swim/teleport builds - the join point (needs the tier, Liquid Experience, and Slurry).

**Ships:**
- **Enclosure Hatch** (shared bottom block: net-install target, frog store, loot inventory + XP tank) + three class blocks on top: **Aviary** (fly), **Aquarium** (swim), **Endarium** (teleport). Two-block pair validates on placement/neighbor change; either alone is inert.
- **Net install** - shift-right-click the Hatch with a Frog Net or Ender Net holding a predator; frog stored as BE data + BER render, no live entity. Break -> release the real frog (whole-entity, every removal path via `preRemoveSideEffects`). Reject non-predator nets.
- **Virtual eat cycle** - Slurry inserted into the class block (only slurries matching the class); runs with both a frog and slurry and only when the frog's environment matches the slurry's mob; Appetite-paced; consumes slurry on the spawn-economy budget (catalysts ride the components); fake spawn + eat render, **no entities ever spawn**; loot -> Hatch, XP -> Hatch's Liquid Experience tank.
- Extends the mob-class JSON to fly/swim/teleport mobs (hand-authored).

**Breaking:** retire the fly/swim/teleport mob-derived variants (blaze, ender pearl, phantom membrane, ...).

**GameTest:** pair validation, install-reject-release round-trip, the full virtual cycle (slurry consumption, class + environment gating, loot-to-Hatch, XP-to-tank).

---

## Phase 5 - Apex Frogs + boss altars  (size: L)

The boss tier and the altar retrofit + two new altars.

**Ships:**
- **Apex Frog tier** - one per boss: Wither / Dragon / Elder / Warden Apex Frog. Bred from the settled cross-environment predator pairs (Cinder x Prowler -> Wither, Rift x Cinder -> Dragon, Gulper x Prowler -> Elder, Prowler x Rift -> Warden). Each eats only its own boss (both-layer gating).
- **Altar retrofit** (Wither #247, Dragon #249) - keep every shipped mechanic; the only two deltas: the altar runs only with its matching Apex Frog installed (net-install on the Hatch, data+render, drop-on-break), and XP pays as **Liquid Experience** into the Hatch tank instead of orbs. The already-settled raw-drops-not-Froglights payout applies.
- **New altars** - Elder Guardian (#280) and Warden (#279), shipping with the Apex-Frog gate + Liquid Experience from the start.

**GameTest:** apex cross-breeding determinism, apex-only eat gating, altar requires-frog (no frog -> no summon), install-reject-release round-trip, Liquid Experience payout.

---

## Phase 6 - Retirement sweep + polish + guide  (size: M)

Close-out.

**Ships:**
- **Full mob-derived variant retirement** - audit the `slime_variant` JSONs, remove every variant that stood in for a mob drop (enumerated from the tree at this point), confirm ore/resource variants untouched. One coordinated breaking note.
- **JEI + Jade** for the new appliances/blocks (Slurry Press, Enclosure Hatch + class blocks, altars); subtype interpreters where a component-carrying item needs distinct entries.
- **Advancements** for the new milestones (first predator bred, first apex, first enclosure, boss farmed via frog).
- **Patchouli guide** entries (deferred until Patchouli ports to 26.1 - do not block the phase on it).
- Config consolidation + CHANGELOG + this doc marked delivered.

---

## Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| Breeding-cross touches `canMate` / vanilla breeding | High | Phase 1 adds designated-pair exceptions without loosening the six-species `canMate`; GameTest both the new crosses and that undesignated pairs still refuse. |
| Sensor/drop extension weakens the six-species gating | High | Preserve both-layer mutual exclusion; GameTest that resource frogs never eat mobs and predators never eat slimes, both directions. |
| Variant retirement breaks existing packs/worlds | Medium | Follow the v1.6 retirement pattern (items lose names/recipes, soft where possible); one coordinated breaking note; stage ground-class in Phase 1, remainder in Phase 6. |
| Liquid Experience mis-tagged -> no cross-mod interop | Medium | Test against the `c:experience` tag, not a specific mod; pin the 20 mB/point ratio in a unit test. |
| Whole-entity round-trip drops stats (nets + Hatch install) | Medium | `saveWithoutId` everywhere an entity serializes (the #210 lesson); GameTest install -> release conservation on every removal path. |

## Deferred to implementation time (not open design questions)

- The **full hand-authored mob -> class/environment JSON** for the vanilla roster (the movement-class guide in #281 is the seed).
- The **final retired-variant list**, enumerated from the `slime_variant` tree at Phase 6.

## Cross-references

- Spec + acceptance criteria: [#281](https://github.com/Flatts3000/productive-frogs/issues/281)
- New altars: [#279](https://github.com/Flatts3000/productive-frogs/issues/279) (Warden), [#280](https://github.com/Flatts3000/productive-frogs/issues/280) (Elder Guardian)
- Reuses: `MilkSpawnEconomy` (spawn budget), the R-1 single-fluid+component model (`docs/port_mc_26_1_reimplementation.md`), the appliance shape (`docs/slime_churn.md`), the altar Hatch convention (`docs/dragon_altar.md`).
