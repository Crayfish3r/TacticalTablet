package com.makar.tacticaltablet.prefix;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PrefixTabDisplayNameTest {

    @Test
    void playerWithoutTeamKeepsDefaultNameColorAndBadgeColors() {
        Component displayName = tabName(ChatFormatting.RESET, PrefixRole.OWNER);

        assertNull(name(displayName).getStyle().getColor());
        assertBadgeColors(displayName, PrefixRole.OWNER);
    }

    @Test
    void redTeamColorsOnlyPlayerName() {
        Component displayName = tabName(ChatFormatting.RED, PrefixRole.MODER);

        assertEquals(ChatFormatting.RED.getColor(), name(displayName).getStyle().getColor().getValue());
        assertBadgeColors(displayName, PrefixRole.MODER);
        assertNotEquals(ChatFormatting.RED.getColor(), badge(displayName).getStyle().getColor().getValue());
    }

    @Test
    void blueTeamColorsOnlyPlayerName() {
        Component displayName = tabName(ChatFormatting.BLUE, PrefixRole.BUILDER);

        assertEquals(ChatFormatting.BLUE.getColor(), name(displayName).getStyle().getColor().getValue());
        assertBadgeColors(displayName, PrefixRole.BUILDER);
        assertNotEquals(ChatFormatting.BLUE.getColor(), badge(displayName).getStyle().getColor().getValue());
    }

    @Test
    void rebuildingAfterTeamTransitionUsesNewTeamColor() {
        Component redDisplayName = tabName(ChatFormatting.RED, PrefixRole.OWNER);
        Component blueDisplayName = tabName(ChatFormatting.BLUE, PrefixRole.OWNER);

        assertEquals(ChatFormatting.RED.getColor(), name(redDisplayName).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.BLUE.getColor(), name(blueDisplayName).getStyle().getColor().getValue());
    }

    @Test
    void rebuildingAfterCleanupRestoresOriginalOrDefaultColor() {
        Component matchDisplayName = tabName(ChatFormatting.RED, PrefixRole.OWNER);
        Component originalTeamDisplayName = tabName(ChatFormatting.BLUE, PrefixRole.OWNER);
        Component defaultDisplayName = tabName(ChatFormatting.RESET, PrefixRole.OWNER);

        assertEquals(ChatFormatting.RED.getColor(), name(matchDisplayName).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.BLUE.getColor(), name(originalTeamDisplayName).getStyle().getColor().getValue());
        assertNull(name(defaultDisplayName).getStyle().getColor());
    }

    @Test
    void chatFlowRetainsItsExistingBaseAndBadgeStyles() {
        Component chatName = PrefixDisplayHelper.appendSuffix(
                Component.literal("PlayerName").withStyle(ChatFormatting.GOLD),
                PrefixRole.OWNER
        );

        assertEquals("PlayerName [OWNER]", chatName.getString());
        assertEquals(ChatFormatting.GOLD.getColor(), chatName.getStyle().getColor().getValue());
        assertEquals(PrefixRole.OWNER.backgroundColor(), chatName.getSiblings().get(0).getStyle().getColor().getValue());
    }

    @Test
    void roleChangeUpdatesBadgeWithoutChangingTeamColor() {
        Component ownerDisplayName = tabName(ChatFormatting.RED, PrefixRole.OWNER);
        Component moderDisplayName = tabName(ChatFormatting.RED, PrefixRole.MODER);

        assertEquals(ChatFormatting.RED.getColor(), name(ownerDisplayName).getStyle().getColor().getValue());
        assertEquals(ChatFormatting.RED.getColor(), name(moderDisplayName).getStyle().getColor().getValue());
        assertEquals(" [OWNER]", badge(ownerDisplayName).getString());
        assertEquals(" [MODER]", badge(moderDisplayName).getString());
        assertNotEquals(
                badge(ownerDisplayName).getStyle().getColor().getValue(),
                badge(moderDisplayName).getStyle().getColor().getValue()
        );
    }

    @Test
    void neutralContainerPreventsTeamColorFromLeakingToBadgeOrFollowingComponents() {
        Component displayName = tabName(ChatFormatting.RED, PrefixRole.MODER);
        Component withFollowingText = displayName.copy().append(Component.literal(" tail"));

        assertNull(withFollowingText.getStyle().getColor());
        assertEquals(ChatFormatting.RED.getColor(), name(withFollowingText).getStyle().getColor().getValue());
        assertBadgeColors(withFollowingText, PrefixRole.MODER);
        assertNull(withFollowingText.getSiblings().get(2).getStyle().getColor());
    }

    private static Component tabName(ChatFormatting teamColor, PrefixRole role) {
        return PrefixDisplayHelper.buildTabName(Component.literal("PlayerName"), teamColor, role);
    }

    private static Component name(Component displayName) {
        return displayName.getSiblings().get(0);
    }

    private static Component badge(Component displayName) {
        return displayName.getSiblings().get(1);
    }

    private static void assertBadgeColors(Component displayName, PrefixRole role) {
        Component badge = badge(displayName);
        assertEquals(role.backgroundColor(), badge.getStyle().getColor().getValue());
        assertEquals(role.textColor(), badge.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(role.backgroundColor(), badge.getSiblings().get(1).getStyle().getColor().getValue());
    }
}
