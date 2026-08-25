# Migration from DeluxeWarfareDatapack

The mod JAR now provides the server-data resources `lobby:lobby` and
`lobby:spawn`. Match lifecycle, scoreboard state, player tags, lives, respawns,
game modes, lobby movement, the zone, world borders, gamerules, and lobby
bootstrap are owned by Java code. No `war:*` or scheduled `lobby:*` function is
required or embedded.

## Why the server must be stopped

The lobby dimension is a dynamic-registry entry. It must be present while the
world registries are opened. Do not use `/reload` as a migration mechanism and
do not start the existing world with both the external datapack and the new mod
missing. A two-stage live migration is not safer: leaving both packs enabled
would retain the old load/scheduled functions and their conflicting side
effects. Perform the replacement as one stopped-server operation.

## Safe migration

1. Fully stop the server and confirm that the Java process has exited.
2. Make a complete, restorable backup of the world, including `level.dat`, all
   dimension folders, `data`, `datapacks`, and player data. Keep copies of the
   old mod JAR and `DeluxeWarfareDatapack.zip` with that backup.
3. Install the new TacticalTablet mod JAR. Confirm that the JAR contains:
   `data/lobby/dimension/lobby.json`,
   `data/lobby/dimension_type/lobby.json`, and
   `data/lobby/structures/spawn.nbt`.
4. Remove or move the old external DeluxeWarfareDatapack out of the world's
   `datapacks` directory. Do not delete the archived copy.
5. Never start the existing world with neither the old datapack nor this new
   mod version installed.
6. First start a copy of the world. In the log, verify that `lobby:lobby` is
   available and inspect the `Lobby bootstrap` decision. An old or manually
   edited lobby must log that existing content was detected and that only the
   migration marker was recorded. It must not place the structure again.
7. On the copied world, verify:
   - `/execute in lobby:lobby run tp <player> 0.5 69 0.5` succeeds;
   - existing lobby blocks and manual edits remain intact;
   - `/place template lobby:spawn` resolves the template (cancel the command or
     test it only in a disposable area; never place it over the real lobby);
   - normal players enter the lobby, while moderators, eliminated players, and
     legitimate spectators keep spectator mode;
   - starting and ending a match update `gameState/#state`, `lives`,
     `war.playing`, and `in_lobby` without `/function` commands;
   - zone phases, world border, respawn/lives, spectator HUD, kill feed, Curios
     equipment, late joins, and moderator mode still work;
   - `/reload` does not change match state, game modes, or lobby blocks.
8. Stop the copied server cleanly, start it again, and confirm the bootstrap log
   says the saved version is already committed and preserves lobby blocks.
9. Only after the copied-world checks pass, repeat the same stopped-server
   replacement on production.

## Gamerules

TacticalTablet applies these intentionally global rules at server start and at
match lifecycle enforcement: `announceAdvancements=false`,
`doImmediateRespawn=true`, `keepInventory=false`, `doBlockDrops=false`,
`doMobSpawning=false`, `doWeatherCycle=false`, and
`naturalRegeneration=true`. They preserve the previous server behavior, but
because gamerules are global they also affect other dimensions and game modes
in the same world. Review this list before sharing the world with unrelated
gameplay.

## Rollback

1. Stop the server completely.
2. Restore the full pre-migration world backup; do not reuse a world that was
   opened with a different dynamic-registry set.
3. Restore the old TacticalTablet mod JAR.
4. Restore the archived DeluxeWarfareDatapack to the world's `datapacks`
   directory.
5. Start a copy first and verify `lobby:lobby`, player data, scoreboard state,
   and lobby contents before returning it to production.

The resource locations are deliberately unchanged: the dimension remains
`lobby:lobby`, its dimension type remains `lobby:lobby`, and the structure
remains `lobby:spawn`.
