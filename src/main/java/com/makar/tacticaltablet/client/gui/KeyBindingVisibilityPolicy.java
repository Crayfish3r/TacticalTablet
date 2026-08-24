package com.makar.tacticaltablet.client.gui;

import net.minecraft.client.KeyMapping;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class KeyBindingVisibilityPolicy {

    private static final String TACZ_CRAWL = "key.tacz.crawl.desc";

    private static final Set<String> PARCOOL_HIDDEN = Set.of(
            "key.parcool.HideInBlock",
            "key.parcool.openSetting",
            "key.parcool.Vault",
            "key.parcool.Enable",
            "key.parcool.QuickTurn",
            "key.parcool.Flipping",
            "key.parcool.FastRun"
    );

    private KeyBindingVisibilityPolicy() {
    }

    public static Optional<Entry> classify(String name, String category) {
        if (name == null || category == null) return Optional.empty();

        if (KeyMapping.CATEGORY_MOVEMENT.equals(category)) {
            return entry(Group.MOVEMENT, name, null, null);
        }
        if (KeyMapping.CATEGORY_GAMEPLAY.equals(category)) {
            if ("key.pickItem".equals(name)) return Optional.empty();
            String description = "key.military_equipment.voicez".equals(name)
                    ? "screen.tacticaltablet.keybind.description.shout" : null;
            return entry(Group.GAMEPLAY, name, null, description);
        }
        if (KeyMapping.CATEGORY_INVENTORY.equals(category)) {
            return entry(Group.INVENTORY, name, null, null);
        }
        if (KeyMapping.CATEGORY_MULTIPLAYER.equals(category)) {
            if ("key.command".equals(name) || "key.socialInteractions".equals(name)) {
                return Optional.empty();
            }
            return entry(Group.MULTIPLAYER, name, null, null);
        }
        if (KeyMapping.CATEGORY_MISC.equals(category)) {
            if (!"key.screenshot".equals(name) && !"key.fullscreen".equals(name)) {
                return Optional.empty();
            }
            return entry(Group.MISC, name, null, null);
        }

        return switch (category) {
            case "key.category.lrtactical" -> classifyLesRaisins(name);
            case "key.categories.parcool" -> classifyParCool(name);
            case "key.category.pingwheel.name" -> classifyPingWheel(name);
            case "key.categories.tacticaltablet" -> classifyTacticalTablet(name);
            case "key.category.tacz" -> classifyTacz(name);
            case "key.categories.voicechat" -> entry(Group.VOICE_CHAT, name, null, null);
            case "key.categories.thermal_vision" -> classifyThermalVision(name);
            default -> Optional.empty();
        };
    }

    public static boolean mustBeUnbound(String name) {
        return TACZ_CRAWL.equals(name);
    }

    private static Optional<Entry> classifyLesRaisins(String name) {
        return switch (name) {
            case "key.lrtactical.normal_attack.desc" -> entry(
                    Group.LES_RAISINS,
                    name,
                    "screen.tacticaltablet.keybind.name.shield_normal",
                    "screen.tacticaltablet.keybind.description.shield_normal"
            );
            case "key.lrtactical.sp_attack.desc" -> entry(
                    Group.LES_RAISINS,
                    name,
                    "screen.tacticaltablet.keybind.name.shield_special",
                    "screen.tacticaltablet.keybind.description.shield_special"
            );
            default -> Optional.empty();
        };
    }

    private static Optional<Entry> classifyParCool(String name) {
        if (PARCOOL_HIDDEN.contains(name)) return Optional.empty();
        String suffix = switch (name) {
            case "key.parcool.RideZipline" -> "zipline";
            case "key.parcool.HorizontalWallRun" -> "wall_run";
            case "key.parcool.ClingToCliff" -> "cling";
            case "key.parcool.Crawl" -> "crawl";
            case "key.parcool.WallJump" -> "wall_jump";
            case "key.parcool.WallSlide" -> "wall_slide";
            case "key.parcool.Dodge" -> "dodge";
            case "key.parcool.Breakfall" -> "breakfall";
            case "key.parcool.ClimbPoles", "key.parcool.HangDown" -> null;
            default -> "__hidden__";
        };
        if ("__hidden__".equals(suffix)) return Optional.empty();
        String description = suffix == null ? null
                : "screen.tacticaltablet.keybind.description.parcool." + suffix;
        String display = "key.parcool.RideZipline".equals(name)
                ? "screen.tacticaltablet.keybind.name.parcool.zipline" : null;
        return entry(Group.PARCOOL, name, display, description);
    }

    private static Optional<Entry> classifyPingWheel(String name) {
        if (!"key.pingwheel.ping_location".equals(name)) return Optional.empty();
        return entry(Group.PING_WHEEL, name,
                "screen.tacticaltablet.keybind.name.ping",
                "screen.tacticaltablet.keybind.description.ping");
    }

    private static Optional<Entry> classifyTacticalTablet(String name) {
        if (!"key.tacticaltablet.spectator_next".equals(name)
                && !"key.tacticaltablet.spectator_previous".equals(name)) {
            return Optional.empty();
        }
        return entry(Group.TACTICAL_TABLET, name, null,
                "screen.tacticaltablet.keybind.description.spectator");
    }

    private static Optional<Entry> classifyTacz(String name) {
        if (mustBeUnbound(name)) return Optional.empty();
        return entry(Group.TACZ, name, null, null);
    }

    private static Optional<Entry> classifyThermalVision(String name) {
        if (!"key.thermal_vision.toggle_thermal_vision".equals(name)) return Optional.empty();
        return entry(Group.THERMAL_VISION, name, null,
                "screen.tacticaltablet.keybind.description.thermal");
    }

    private static Optional<Entry> entry(Group group, String originalName,
                                         String displayNameKey, String descriptionKey) {
        return Optional.of(new Entry(group, originalName, displayNameKey, descriptionKey));
    }

    public record Entry(Group group, String originalName,
                        String displayNameKey, String descriptionKey) {
    }

    public enum Group {
        MOVEMENT("key.categories.movement"),
        GAMEPLAY("key.categories.gameplay"),
        INVENTORY("key.categories.inventory"),
        MULTIPLAYER("key.categories.multiplayer"),
        MISC("key.categories.misc"),
        LES_RAISINS("screen.tacticaltablet.keybind.category.shield"),
        PARCOOL("key.categories.parcool"),
        PING_WHEEL("screen.tacticaltablet.keybind.category.ping"),
        TACTICAL_TABLET("screen.tacticaltablet.keybind.category.tacticaltablet"),
        TACZ("key.category.tacz"),
        VOICE_CHAT("key.categories.voicechat"),
        THERMAL_VISION("key.categories.thermal_vision");

        private final String titleKey;

        Group(String titleKey) {
            this.titleKey = titleKey;
        }

        public String titleKey() {
            return titleKey;
        }

        public String focusKey() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
