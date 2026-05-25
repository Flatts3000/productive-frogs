# Code Review Audit - Security / Trust Boundaries (2026-05-24)

Specialist: security reviewer (Sonnet). Scope: deserialization and decode safety across network
sync, datapack JSON, and NBT. Threat model is a game mod (no secrets/auth/SQL/HTTP). Raw findings
preserved for posterity. See `docs/code_review_2026_05_24.md` for the synthesis. IDs here (S-N) are
this reviewer's own numbering.

## CRITICAL (reviewer rating; synthesis re-rated HIGH for a game mod)

### S-1: Category.STREAM_CODEC unbounded ordinal array access
Trust boundary: network (S2C malicious server crashes client; C2S where the component flows, e.g.
a modified creative client's set-slot packet).
File: `data/Category.java:31-32`, used by `registry/PFDataComponents.java` (`networkSynchronized`).

```java
ByteBufCodecs.idMapper(ordinal -> values()[ordinal], Category::ordinal)
```

Reading `ByteBufCodecs.idMapper(IntFunction, ToIntFunction)`: it reads a VarInt and calls the
decode function with no bounds check. Any ordinal outside `[0, 5]` or negative throws
`ArrayIndexOutOfBoundsException` during packet decode. The `contained_category` data component is
synced over the wire, so a crafted ordinal is a remotely triggerable crash.

Fix: bounds-check and throw `io.netty.handler.codec.DecoderException` on out-of-range input (the
pipeline catches it and closes the connection cleanly), or encode by name via `Category.CODEC` +
`ByteBufCodecs.STRING_UTF8` (also robust to enum reordering). [Synthesis CR-1, rated HIGH.]

## HIGH

### S-2: No GameTest covers a malformed category string in a datapack entry
Trust boundary: datapack. File: `data/Category.java:30` (`StringRepresentable.fromEnum`).

The codec itself is safe - an unknown `"category"` value yields `DataResult.error` and the entry is
skipped, not a crash. But this graceful-skip path is not exercised by a test. In multiplayer an
admin- or modpack-authored malicious datapack reaches all players at world join. No code change to
the codec; add a GameTest asserting a `SlimeVariant` JSON with `"category": "nonexistent"` is
rejected gracefully.

## MEDIUM

### S-6: variantId accepted without registry validation in block-entity load/apply
Trust boundary: disk-NBT / S2C. File: `content/block/entity/ConfigurableFroglightBlockEntity.java:113-133`.
Any `ResourceLocation` is accepted as `variantId` without confirming it resolves in `SLIME_VARIANT`.
No crash - `registry.get(id)` returns null and the block renders with the default tint. Display-only.
Defense-in-depth: add a comment that `variantId` may not resolve (datapack removed / mod absent).

### S-7: entity_type in ParentSpeciesEntry not validated against the loaded registry
Trust boundary: datapack. File: `data/ParentSpeciesEntry.java:68-74`. `entity_type` is a raw
`ResourceLocation`; an unloaded id makes the entry silently inert. This is the correct behavior for
cross-mod compat (entries for absent mods skip). No dereference can throw. No change required.

## LOW

### S-3: DATA_CATEGORY synced field is correctly bounds-checked [positive finding]
File: `content/entity/ResourceSlime.java:79-88`. `getCategory()` reads the raw INT synced field and
clamps out-of-range ordinals to `Category.BOG`. Correct pattern; no issue.

### S-4: CookProgress loaded without clamping - negative value stalls the block
Trust boundary: disk-NBT (tampered save). File:
`content/block/entity/SlimeMilkerBlockEntity.java:200-201`. A negative `CookProgress` never reaches
`COOK_TIME_TOTAL` until it wraps through `Integer.MAX_VALUE`, locking the block "WORKING" forever.
Local single-player DoS (requires file write access). Fix: `Math.max(0, ...)` on load. [Synthesis CR-10.]

### S-5: Bucket NBT reads use type checks + tryParse throughout [positive finding]
File: `content/item/ResourceTadpoleBucketItem.java:86-99` and `SlimeBucketItem`. `readVariant`
type-checks the tag and uses `ResourceLocation.tryParse` (null, not throw); `readCategory` wraps
`Category.valueOf` in try/catch. The whole bucket chain is correctly defensive. No issue.

### S-8: loadFromBucketTag one-liner hinders audit
File: `content/entity/ResourceSlime.java:359-363`. Logic is correct (try/catch + tryParse) but
compressed onto single multi-statement lines. Expand to the multi-line style used elsewhere.
[Synthesis CR-20.]

### S-9: primaryColor/secondaryColor not range-constrained in the codec
Trust boundary: datapack. File: `data/SlimeVariant.java:85-86`. `Codec.INT` accepts negatives /
out-of-24-bit values, producing garbage tints (no crash; `weight` already uses `intRange`). Apply
`Codec.intRange(0, 0xFFFFFF)` to the color fields. [Synthesis CR-11.]

## Action priority
1. S-1 before the next multiplayer-capable build (only remotely triggerable finding; 5-line fix).
2. S-4 (CookProgress clamp; one-token fix introduced with the new Slime Milker).
3. S-2 (datapack rejection test) and S-9 (color clamping) as defense-in-depth follow-ups.
4. S-6, S-7, S-8 informational.

## Summary
| ID | Severity | Boundary | File |
|----|----------|----------|------|
| S-1 | CRITICAL (re-rated HIGH) | network | data/Category.java:31-32 |
| S-2 | HIGH | datapack | data/Category.java:30 |
| S-3 | positive | network | entity/ResourceSlime.java:83-88 |
| S-4 | LOW | disk-NBT | block/entity/SlimeMilkerBlockEntity.java:200 |
| S-5 | positive | disk-NBT/S2C | item/ResourceTadpoleBucketItem.java:86-99 |
| S-6 | MEDIUM | disk-NBT/S2C | block/entity/ConfigurableFroglightBlockEntity.java:113-133 |
| S-7 | MEDIUM | datapack | data/ParentSpeciesEntry.java:68-74 |
| S-8 | LOW | disk-NBT | entity/ResourceSlime.java:359-363 |
| S-9 | LOW | datapack | data/SlimeVariant.java:85-86 |
