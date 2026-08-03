package com.makar.tacticaltablet.tablet.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanPagePolicyTest {
    @Test
    void ownerMemberPendingAndOutsiderHaveDistinctActions() {
        ClanPagePolicy.Permissions owner = ClanPagePolicy.permissions(true, true, false);
        assertTrue(owner.canChangeColor());
        assertTrue(owner.canDisband());
        assertTrue(owner.canReviewRequests());
        assertTrue(owner.canKickMembers());
        assertFalse(owner.canLeave());

        ClanPagePolicy.Permissions member = ClanPagePolicy.permissions(false, true, false);
        assertTrue(member.canLeave());
        assertFalse(member.canDisband());

        ClanPagePolicy.Permissions pending = ClanPagePolicy.permissions(false, false, true);
        assertFalse(pending.canRequestJoin());
        assertFalse(pending.canLeave());

        ClanPagePolicy.Permissions outsider = ClanPagePolicy.permissions(false, false, false);
        assertTrue(outsider.canRequestJoin());
        assertFalse(outsider.canReviewRequests());
    }
}
