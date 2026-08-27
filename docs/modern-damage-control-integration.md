# Modern Damage Control integration

## Verified target

- Modern Damage Control: exactly `1.0.32`.
- Published JAR SHA-256: `eaf15ce984a6af47f25fffa026abc7eb8be64d4ecb4b81b943a29d3c421fc461`.
- Audited source commit: `f8acf7cf81856ae1d5628ae4487553a1d57a6db1` from
  `https://github.com/balitely/Modern-Damage-Control`.
- No broader compatibility range is claimed. TacticalTablet checks `moderndamage` and the exact
  version before loading either versioned adapter. Missing or different versions leave the rest of
  TacticalTablet operational and disable only the MDC HUD and editor.

The optional Forge dependency in `mods.toml` deliberately accepts any installed MDC version so that
Forge itself does not reject the game before TacticalTablet can show its safe unsupported-version
state. Compatibility is enforced by the runtime gate.

## Hot-edit allow-list

The schema contains exactly 68 typed numeric fields:

- bleeding: `minorBleedingIntervalTicks`, `minorBleedingDamagePerLevel`,
  `majorBleedingIntervalTicks`, `majorBleedingDamagePerLevel`;
- arm stamina: `meleeAttackCost`, `bowDrawCostPerTick`, `adsCostPerTick`,
  `miningCostPerBlock`, `staminaRegenDelayTicks`;
- leg stamina: `legSprintingCostPerTick`, `legSwimmingCostPerTick`, `legJumpCost`,
  `legCrouchEnterCost`, `legCrouchExitCost`, `legCrawlEnterCost`, `legCrawlExitCost`,
  `legStaminaRegenDelayTicks`;
- for every injury profile below: `threshold`, `chance`, and `duration`:
  - head: dizziness;
  - chest: minor bleeding;
  - stomach: nausea, major bleeding, minor bleeding;
  - left/right arm: matching fracture, major bleeding, minor bleeding;
  - left/right leg: matching fracture, major bleeding, minor bleeding.

The exact field IDs, bounds, integer rules and defaults live in
`ModernDamageBalanceSchema`. The server requires the complete field set and rejects unknown IDs,
duplicates, missing fields, non-finite numbers, out-of-range values, fractional integer fields,
zero effect durations, inverted major/minor bleeding thresholds, and inconsistent bleeding interval
or damage ordering. A revision check prevents stale editors from overwriting newer values.

## Why these values are hot-safe in MDC 1.0.32

All evidence below refers to the verified source commit:

- Injury selection calls `ModClothConfig.getEffects(hitPart)` for every injury and immediately reads
  `EffectEntry.getThreshold()`, `getChance()` and `getDuration()` in
  `InjuryEventHandler.java:567-570`. `ModClothConfig.getEffects` calls `get()` and returns the live
  body-part list in `ModClothConfig.java:374-379`.
- Every bleeding effect tick obtains `ModClothConfig.get()` and then reads the current interval and
  damage fields in `AbstractBleedingEffect.java:24-31`.
- Arm action handlers obtain `ModClothConfig.get()` in the event handler and read the current melee,
  bow, ADS and mining costs at `ArmStaminaEventHandler.java:29-35,46-69,138-144`.
- Leg action handlers obtain `ModClothConfig.get()` and read current jump, sprint, swim, crouch and
  crawl costs at `LegStaminaEventHandler.java:24-74`.
- Arm and leg capability ticks obtain `ModClothConfig.get()` and read current regeneration delays at
  `ArmStamina.java:97-102` and `LegStamina.java:94-99`.

The editor intentionally excludes regeneration *speed*: MDC reads that from registered player
attributes (`ARM_STAMINA_REGEN` and `LEG_STAMINA_REGEN`), so changing similarly named config values
would not prove immediate application to existing capabilities. Damage mode, maximum health,
body-part health ratios, hitboxes, entity/armor JSON and every registration-time or unverified value
are also excluded.

## Persistence and synchronization

Application runs inside the packet handler's server-thread work item. TacticalTablet validates a
complete DTO first, snapshots all old values, writes an atomic `.tacticaltablet.bak` of the existing
MDC file, mutates the live AutoConfig object, and invokes MDC's own AutoConfig holder `save()`.
Failures restore the complete in-memory snapshot and the prior file; success alone advances the
revision, logs every changed key, and broadcasts the committed values to connected clients.

The client HUD reads only MDC's synchronized arm/leg caches and MDC maximum-stamina attributes. When
the TacticalTablet HUD is enabled, MDC's stock stamina overlay is disabled through MDC's own
`enableStaminaHUD` AutoConfig field; no broad render-event cancellation is used.

## Reproducible smoke profiles

Normal runs do not include MDC. For local userdev smoke tests only, Gradle accepts:

- `-PmdcSmokeVersion=1.0.32` for the supported build;
- `-PmdcSmokeVersion=1.0.3` for a published unsupported build;
- `-PgameRunDirectory=<path>` to isolate generated configs/world data.

The profile adds deobfuscated MDC, Cloth Config 11.1.136 and TaCZ 1.1.7 only to the selected run's
runtime classpath; it does not package them in TacticalTablet.
