package com.makar.tacticaltablet.account;

import java.util.UUID;

public record PlayerIdentity(UUID uuid, String name, boolean online) {
}
