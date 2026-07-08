package com.makar.tacticaltablet.prefix;

import com.makar.tacticaltablet.account.PlayerIdentity;
import com.makar.tacticaltablet.account.PlayerLookup;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class PrefixCommand {

    private static final DateTimeFormatter EXPIRES_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final SuggestionProvider<CommandSourceStack> ROLE_SUGGESTIONS = PrefixCommand::suggestRoles;

    private PrefixCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root("prefix"));
        dispatcher.register(root("ttprefix"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .requires(PrefixCommand::canManage)
                .then(Commands.literal("set")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(ROLE_SUGGESTIONS)
                                        .executes(context -> setRole(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "user"),
                                                StringArgumentType.getString(context, "role")
                                        )))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .executes(context -> clearRole(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "user")
                                ))))
                .then(Commands.literal("get")
                        .then(Commands.argument("user", StringArgumentType.word())
                                .executes(context -> getRole(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "user")
                                ))))
                .then(Commands.literal("list")
                        .executes(context -> listRoles(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())));
    }

    private static int setRole(CommandSourceStack source, String user, String roleId) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        PrefixRole role = PrefixRole.byId(roleId);
        if (role == PrefixRole.NONE) {
            return clearRole(source, user);
        }

        PrefixManager.setRole(target.uuid(), target.name(), role, 0L);
        PrefixManager.syncAll(source.getServer());
        PrefixManager.updateTabNames(source.getServer());

        source.sendSuccess(
                () -> Component.literal(target.online()
                        ? "[Prefix] Игроку " + target.name() + " установлен префикс " + role.displayName() + "."
                        : "[Prefix] Offline-игроку " + target.name() + " установлен префикс " + role.displayName()
                        + ". Применится при следующем входе."),
                true
        );
        return 1;
    }

    private static int clearRole(CommandSourceStack source, String user) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        PrefixManager.clearRole(target.uuid());
        PrefixManager.syncAll(source.getServer());
        PrefixManager.updateTabNames(source.getServer());

        source.sendSuccess(
                () -> Component.literal(target.online()
                        ? "[Prefix] Префикс игрока " + target.name() + " очищен."
                        : "[Prefix] Префикс offline-игрока " + target.name()
                        + " очищен. Применится при следующем входе."),
                true
        );
        return 1;
    }

    private static int getRole(CommandSourceStack source, String user) {
        Optional<PlayerIdentity> resolved = PlayerLookup.resolve(source.getServer(), user);
        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(PlayerLookup.NOT_FOUND_MESSAGE));
            return 0;
        }

        PlayerIdentity target = resolved.get();
        PrefixPlayerData data = PrefixManager.getData(target.uuid());
        PrefixRole role = PrefixManager.getRole(target.uuid());
        String expires = data == null || data.expiresAt() <= 0L
                ? "навсегда"
                : EXPIRES_FORMAT.format(Instant.ofEpochMilli(data.expiresAt()));
        String lastKnownName = data == null ? target.name() : data.lastKnownName();

        source.sendSuccess(
                () -> Component.literal("[Prefix] " + target.name()
                        + ": role=" + role.id()
                        + ", display=" + (role.displayName().isBlank() ? "none" : role.displayName())
                        + ", expiresAt=" + expires
                        + ", lastKnownName=" + lastKnownName + "."),
                false
        );
        return role == PrefixRole.NONE ? 0 : 1;
    }

    private static int listRoles(CommandSourceStack source) {
        List<PrefixPlayerData> assigned = PrefixManager.getAssignedPlayers();
        if (assigned.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Prefix] Игроков с префиксами нет."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("[Prefix] Игроки с префиксами: " + assigned.size() + "."), false);
        for (PrefixPlayerData data : assigned) {
            PrefixRole role = data.effectiveRole();
            String expires = data.expiresAt() <= 0L
                    ? "навсегда"
                    : EXPIRES_FORMAT.format(Instant.ofEpochMilli(data.expiresAt()));
            source.sendSuccess(
                    () -> Component.literal("- " + data.lastKnownName()
                            + " (" + data.uuid() + "): " + role.displayName()
                            + ", expiresAt=" + expires),
                    false
            );
        }
        return assigned.size();
    }

    private static int reload(CommandSourceStack source) {
        PrefixManager.load(source.getServer());
        PrefixManager.cleanupExpired();
        PrefixManager.syncAll(source.getServer());
        PrefixManager.updateTabNames(source.getServer());

        source.sendSuccess(() -> Component.literal("[Prefix] Префиксы перезагружены."), true);
        return 1;
    }

    private static boolean canManage(CommandSourceStack source) {
        if (source.hasPermission(2)) return true;
        return source.getEntity() instanceof ServerPlayer player
                && PrefixManager.hasPermission(player, PrefixPermission.PREFIX_MANAGE);
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestRoles(
            CommandContext<CommandSourceStack> context,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        List<String> ids = new ArrayList<>();
        for (PrefixRole role : PrefixRole.values()) {
            ids.add(role.id());
        }
        ids.add("clear");
        ids.add("создатель");
        ids.add("элита");
        ids.add("про");
        ids.add("нет");
        return SharedSuggestionProvider.suggest(ids, builder);
    }
}
