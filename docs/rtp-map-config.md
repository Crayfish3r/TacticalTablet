# RTP map configuration

`tacticaltablet_zone.json` is the single map configuration file.  The `rtp`
section is optional: maps without it keep the existing surface RTP behaviour.

```json
{
  "zoneCenterX": 15.0,
  "zoneCenterZ": -30.0,
  "zoneRandomRadius": 0,
  "rtp": {
    "mode": "FIXED_Y_BOX",
    "minX": -100,
    "maxX": 130,
    "minZ": -145,
    "maxZ": 85,
    "spawnY": 42,
    "maxAttempts": 500,
    "localSearchRadius": 10,
    "teamSpreadRadius": 4,
    "requiredSolidBlocksBelow": 1,
    "requireInsideWorldBorder": true
  }
}
```

`spawnY` is the player's feet coordinate; the floor must be at `spawnY - 1`.
All min/max bounds are inclusive. `FIXED_Y_BOX` never queries a heightmap and
does not fall back to the surface when it cannot find a safe point.

When `rtp` is absent (or `mode` is absent), `WORLD_BORDER_SURFACE` is used.
Its defaults preserve the existing search: 80 attempts per requested pool point,
local search radius 14, team spread radius 4, three solid blocks below, and a
WorldBorder check. For `FIXED_Y_BOX`, omitted optional fields use those same
values; `requireInsideWorldBorder` defaults to `true`.
