# Tactical kill feed and third-party HUDs

TacticalTablet is the authoritative match kill feed. Its server config is enabled by default:

```toml
[killFeed]
enabled = true
```

SuperbWarfare renders its own client-only `KillMessageOverlay`; TacticalTablet does not send packets
that attempt to disable or modify that foreign overlay. On every client, disable the SBW overlay in
`config/superbwarfare-client.toml`:

```toml
[kill_message]
show_kill_message = false
```

The key is defined by SBW's official `KillMessageConfig.SHOW_KILL_MESSAGE` source:
https://github.com/Mercurows/SuperbWarfare/blob/superbwarfare/src/main/kotlin/com/atsuishio/superbwarfare/config/client/KillMessageConfig.kt

With Cloth Config installed, the same client option is exposed in the SuperbWarfare kill-message
configuration screen. This is a per-client setting and must be distributed with the client/modpack
configuration. TaCZ itself does not need to be modified by TacticalTablet for this purpose.
