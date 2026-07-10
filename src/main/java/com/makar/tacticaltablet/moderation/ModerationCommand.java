package com.makar.tacticaltablet.moderation;

import com.makar.tacticaltablet.account.PlayerIdentity;
import com.makar.tacticaltablet.account.PlayerLookup;
import com.makar.tacticaltablet.core.TacticalTabletMod;
import com.makar.tacticaltablet.prefix.PrefixManager;
import com.makar.tacticaltablet.prefix.PrefixPermission;
import com.makar.tacticaltablet.prefix.PrefixRole;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ModerationCommand {

    private static final int MAX_REASON_LENGTH = 160;
    private static final long SECOND_MS = 1_000L;
    private static final long MINUTE_MS = 60L * SECOND_MS;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;
    private static final long MODER_MAX_DURATION_MS = 7L * DAY_MS;
    private static final long OWNER_MAX_DURATION_MS = 365L * DAY_MS;

    private ModerationCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ttmodermode")
                .requires(source -> hasPermission(source, PrefixPermission.MODER_MODE))
                .then(Commands.literal("on").executes(context -> setModerMode(context.getSource(), true)))
                .then(Commands.literal("off").executes(context -> setModerMode(context.getSource(), false))));

        dispatcher.register(Commands.literal("ttmute")
                .requires(source -> hasPermission(source, PrefixPermission.MOD_MUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> mute(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "duration"),
                                                StringArgumentType.getString(context, "reason")
                                        ))))));

        dispatcher.register(Commands.literal("ttunmute")
                .requires(source -> hasPermission(source, PrefixPermission.MOD_UNMUTE))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> unmute(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")
                        ))));

        dispatcher.register(Commands.literal("ttkick")
                .requires(source -> hasPermission(source, PrefixPermission.MOD_KICK))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> kick(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "player"),
                                        StringArgumentType.getString(context, "reason")
                                )))));

        dispatcher.register(Commands.literal("tttempban")
                .requires(source -> hasPermission(source, PrefixPermission.MOD_TEMPBAN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("duration", StringArgumentType.word())
                                .then(Commands.argument("reason", StringArgumentType.greedyString())
                                        .executes(context -> tempBan(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "player"),
                                                StringArgumentType.getString(context, "duration"),
                                                StringArgumentType.getString(context, "reason")
                                        ))))));

        dispatcher.register(Commands.literal("ttunban")
                .requires(source -> hasPermission(source, PrefixPermission.MOD_UNBAN))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> unban(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")
                        ))));

        dispatcher.register(Commands.literal("ttpunishments")
                .requires(source -> hasPermission(source, PrefixPermission.MOD_PUNISHMENTS))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(context -> punishments(
                                context.getSource(),
                                StringArgumentType.getString(context, "player")
                        ))));
    }

    private static int setModerMode(CommandSourceStack source, boolean enabled) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[Moderation] This command can only be used by a player."));
            return 0;
        }

        if (enabled) {
            ModerModeManager.enable(player);
        } else {
            ModerModeManager.disable(player);
        }
        return 1;
    }

    private static int mute(CommandSourceStack source, String playerName, String durationText, String reasonText) {
        Optional<PlayerIdentity> resolved = resolvePlayer(source, playerName);
        if (resolved.isEmpty()) return 0;

        PlayerIdentity target = resolved.get();
        String reason = normalizeReason(source, reasonText);
        if (reason == null) return 0;

        Long duration = parseDuration(source, durationText);
        if (duration == null || !checkDurationLimit(source, duration)) return 0;
        if (!canPunish(source, target, "mute")) return 0;

        long expiresAt = safeExpiresAt(duration);
        PunishmentManager.mute(target.uuid(), target.name(), issuerUuid(source), issuerName(source), expiresAt, reason);

        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), target);
        if (online != null) {
            online.sendSystemMessage(Component.literal("[Moderation] You are muted for "
                    + PunishmentManager.formatRemaining(expiresAt) + ". Reason: " + reason));
        }

        audit(source, "mute", target, reason, expiresAt);
        source.sendSuccess(() -> Component.literal("[Moderation] Muted " + target.name()
                + " until " + PunishmentManager.formatExpiresAt(expiresAt) + "."), true);
        return 1;
    }

    private static int unmute(CommandSourceStack source, String playerName) {
        Optional<PlayerIdentity> resolved = resolvePlayer(source, playerName);
        if (resolved.isEmpty()) return 0;

        PlayerIdentity target = resolved.get();
        PunishmentRecord record = PunishmentManager.getMute(target.uuid());
        if (!canRemovePunishment(source, record, "unmute", target)) {
            return 0;
        }

        boolean removed = PunishmentManager.unmute(target.uuid());
        if (removed) {
            audit(source, "unmute", target, "", 0L);
        }
        source.sendSuccess(() -> Component.literal(removed
                ? "[Moderation] Unmuted " + target.name() + "."
                : "[Moderation] " + target.name() + " has no active mute."), true);
        return removed ? 1 : 0;
    }

    private static int kick(CommandSourceStack source, String playerName, String reasonText) {
        Optional<PlayerIdentity> resolved = resolvePlayer(source, playerName);
        if (resolved.isEmpty()) return 0;

        PlayerIdentity targetIdentity = resolved.get();
        ServerPlayer target = PlayerLookup.getOnline(source.getServer(), targetIdentity);
        if (target == null) {
            source.sendFailure(Component.literal("[Moderation] Target must be online."));
            return 0;
        }

        String reason = normalizeReason(source, reasonText);
        if (reason == null) return 0;
        if (!canPunish(source, targetIdentity, "kick")) return 0;

        audit(source, "kick", targetIdentity, reason, 0L);
        target.connection.disconnect(Component.literal("[Moderation] Kicked. Reason: " + reason));
        source.sendSuccess(() -> Component.literal("[Moderation] Kicked " + targetIdentity.name() + "."), true);
        return 1;
    }

    private static int tempBan(CommandSourceStack source, String playerName, String durationText, String reasonText) {
        Optional<PlayerIdentity> resolved = resolvePlayer(source, playerName);
        if (resolved.isEmpty()) return 0;

        PlayerIdentity target = resolved.get();
        String reason = normalizeReason(source, reasonText);
        if (reason == null) return 0;

        Long duration = parseDuration(source, durationText);
        if (duration == null || !checkDurationLimit(source, duration)) return 0;
        if (!canPunish(source, target, "tempban")) return 0;

        long expiresAt = safeExpiresAt(duration);
        PunishmentManager.tempBan(target.uuid(), target.name(), issuerUuid(source), issuerName(source), expiresAt, reason);

        ServerPlayer online = PlayerLookup.getOnline(source.getServer(), target);
        if (online != null) {
            online.connection.disconnect(Component.literal("[Moderation] Temporarily banned until "
                    + PunishmentManager.formatExpiresAt(expiresAt) + ". Reason: " + reason));
        }

        audit(source, "tempban", target, reason, expiresAt);
        source.sendSuccess(() -> Component.literal("[Moderation] Temp-banned " + target.name()
                + " until " + PunishmentManager.formatExpiresAt(expiresAt) + "."), true);
        return 1;
    }

    private static int unban(CommandSourceStack source, String playerName) {
        Optional<PlayerIdentity> resolved = resolvePlayer(source, playerName);
        if (resolved.isEmpty()) return 0;

        PlayerIdentity target = resolved.get();
        PunishmentRecord record = PunishmentManager.getTempBan(target.uuid());
        if (!canRemovePunishment(source, record, "unban", target)) {
            return 0;
        }

        boolean removed = PunishmentManager.unban(target.uuid());
        if (removed) {
            audit(source, "unban", target, "", 0L);
        }
        source.sendSuccess(() -> Component.literal(removed
                ? "[Moderation] Unbanned " + target.name() + "."
                : "[Moderation] " + target.name() + " has no active tempban."), true);
        return removed ? 1 : 0;
    }

    private static int punishments(CommandSourceStack source, String playerName) {
        Optional<PlayerIdentity> resolved = resolvePlayer(source, playerName);
        if (resolved.isEmpty()) return 0;

        PlayerIdentity target = resolved.get();
        PunishmentRecord mute = PunishmentManager.getMute(target.uuid());
        PunishmentRecord ban = PunishmentManager.getTempBan(target.uuid());

        source.sendSuccess(() -> Component.literal("[Moderation] Punishments for " + target.name() + ":"), false);
        if (mute == null && ban == null) {
            source.sendSuccess(() -> Component.literal("- no active punishments"), false);
            return 0;
        }

        if (mute != null) {
            source.sendSuccess(() -> describeRecord("mute", mute), false);
        }
        if (ban != null) {
            source.sendSuccess(() -> describeRecord("tempban", ban), false);
        }
        return 1;
    }

    private static Component describeRecord(String label, PunishmentRecord record) {
        return Component.literal("- " + label
                + ": reason=\"" + record.reason() + "\""
                + ", issuer=" + record.issuerName()
                + ", expiresAt=" + PunishmentManager.formatExpiresAt(record.expiresAt())
                + ", remaining=" + PunishmentManager.formatRemaining(record.expiresAt()));
    }

    private static Optional<PlayerIdentity> resolvePlayer(CommandSourceStack source, String playerName) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), playerName);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
        }
        return resolved;
    }

    private static boolean hasPermission(CommandSourceStack source, String permission) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return PrefixManager.hasPermission(player, permission);
        }

        return source.getEntity() == null;
    }

    private static boolean canPunish(CommandSourceStack source, PlayerIdentity target, String action) {
        if (target == null || target.uuid() == null) return false;

        PrefixRole targetRole = PrefixManager.getRole(target.uuid());
        if (targetRole == PrefixRole.OWNER) {
            source.sendFailure(Component.literal("[Moderation] Owner cannot be " + action + "d."));
            return false;
        }

        if (!(source.getEntity() instanceof ServerPlayer issuer)) {
            return true;
        }

        if (issuer.getUUID().equals(target.uuid())) {
            source.sendFailure(Component.literal("[Moderation] You cannot " + action + " yourself."));
            return false;
        }

        PrefixRole issuerRole = PrefixManager.getRole(issuer);
        if (issuerRole == PrefixRole.OWNER) {
            return true;
        }

        if (issuerRole == PrefixRole.MODER && targetRole == PrefixRole.MODER) {
            source.sendFailure(Component.literal("[Moderation] Moderators cannot punish other moderators."));
            return false;
        }

        if (!target.online() && targetRole == PrefixRole.NONE) {
            TacticalTabletMod.LOGGER.info(
                    "[Moderation] {} is {}ing offline target {} ({}) with unknown role",
                    issuer.getGameProfile().getName(),
                    action,
                    target.name(),
                    target.uuid()
            );
        }

        return true;
    }

    private static boolean canRemovePunishment(
            CommandSourceStack source,
            PunishmentRecord record,
            String action,
            PlayerIdentity target
    ) {
        if (record == null) {
            return true;
        }

        if (!(source.getEntity() instanceof ServerPlayer actor)) {
            return true;
        }

        PrefixRole actorRole = PrefixManager.getRole(actor);
        if (actorRole == PrefixRole.OWNER || PrefixManager.hasPermission(actor, "tacticaltablet.*")) {
            return true;
        }

        UUID actorUuid = actor.getUUID();
        UUID issuerUuid = record.issuerUuid();
        if (actorUuid.equals(issuerUuid)) {
            return true;
        }

        PrefixRole issuerRole = PrefixManager.getRole(issuerUuid);
        if (issuerRole == PrefixRole.OWNER) {
            source.sendFailure(Component.literal("[Moderation] You cannot " + action
                    + " a punishment issued by owner."));
            auditDeniedRemoval(source, action, target, record, "owner-issued");
            return false;
        }

        if (issuerRole == PrefixRole.MODER) {
            source.sendFailure(Component.literal("[Moderation] You cannot " + action
                    + " a punishment issued by another moderator."));
            auditDeniedRemoval(source, action, target, record, "other-moder-issued");
            return false;
        }

        return true;
    }

    private static String normalizeReason(CommandSourceStack source, String reasonText) {
        String reason = reasonText == null ? "" : reasonText.trim();
        if (reason.isBlank()) {
            source.sendFailure(Component.literal("[Moderation] Reason is required."));
            return null;
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            source.sendFailure(Component.literal("[Moderation] Reason is too long. Max " + MAX_REASON_LENGTH + " characters."));
            return null;
        }
        return reason;
    }

    private static Long parseDuration(CommandSourceStack source, String durationText) {
        String value = durationText == null ? "" : durationText.trim().toLowerCase(Locale.ROOT);
        if (value.length() < 2) {
            source.sendFailure(Component.literal("[Moderation] Invalid duration. Use 10s, 5m, 2h, 3d."));
            return null;
        }

        char unit = value.charAt(value.length() - 1);
        long multiplier = switch (unit) {
            case 's' -> SECOND_MS;
            case 'm' -> MINUTE_MS;
            case 'h' -> HOUR_MS;
            case 'd' -> DAY_MS;
            default -> -1L;
        };
        if (multiplier <= 0L) {
            source.sendFailure(Component.literal("[Moderation] Invalid duration unit. Use s, m, h, or d."));
            return null;
        }

        long amount;
        try {
            amount = Long.parseLong(value.substring(0, value.length() - 1));
        } catch (NumberFormatException exception) {
            source.sendFailure(Component.literal("[Moderation] Invalid duration amount."));
            return null;
        }

        if (amount <= 0L || amount > Long.MAX_VALUE / multiplier) {
            source.sendFailure(Component.literal("[Moderation] Duration must be greater than zero."));
            return null;
        }

        return amount * multiplier;
    }

    private static boolean checkDurationLimit(CommandSourceStack source, long duration) {
        long max = maxDuration(source);
        if (duration <= max) {
            return true;
        }

        source.sendFailure(Component.literal("[Moderation] Duration too long. Max "
                + (max / DAY_MS) + "d for your role."));
        return false;
    }

    private static long maxDuration(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player
                && PrefixManager.getRole(player) != PrefixRole.OWNER) {
            return MODER_MAX_DURATION_MS;
        }

        return OWNER_MAX_DURATION_MS;
    }

    private static long safeExpiresAt(long duration) {
        long now = System.currentTimeMillis();
        if (duration > Long.MAX_VALUE - now) {
            return Long.MAX_VALUE;
        }
        return now + duration;
    }

    private static UUID issuerUuid(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
    }

    private static String issuerName(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? player.getGameProfile().getName()
                : "Console";
    }

    private static void audit(CommandSourceStack source, String action, PlayerIdentity target, String reason, long expiresAt) {
        TacticalTabletMod.LOGGER.info(
                "[Moderation] issuer={} action={} target={} ({}) expiresAt={} reason={}",
                issuerName(source),
                action,
                target.name(),
                target.uuid(),
                expiresAt <= 0L ? "-" : PunishmentManager.formatExpiresAt(expiresAt),
                reason == null || reason.isBlank() ? "-" : reason
        );
    }

    private static void auditDeniedRemoval(
            CommandSourceStack source,
            String action,
            PlayerIdentity target,
            PunishmentRecord record,
            String reason
    ) {
        TacticalTabletMod.LOGGER.info(
                "[Moderation] denied issuer={} action={} target={} ({}) recordIssuer={} ({}) reason={}",
                issuerName(source),
                action,
                target == null ? "unknown" : target.name(),
                target == null ? "unknown" : target.uuid(),
                record.issuerName(),
                record.issuerUuid(),
                reason
        );
    }
}
