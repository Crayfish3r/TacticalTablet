package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.clan.ClanConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClanCreateInputPolicyTest {
    @Test
    void validatesAllClientFormConstraints() {
        assertTrue(ClanCreateInputPolicy.isValid("Alpha", "A", 0, true, ClanConstants.CREATE_COST));
        assertFalse(ClanCreateInputPolicy.isValid("ab", "A", 0, true, ClanConstants.CREATE_COST));
        assertFalse(ClanCreateInputPolicy.isValid("Alpha", "", 0, true, ClanConstants.CREATE_COST));
        assertFalse(ClanCreateInputPolicy.isValid("Alpha", "ABCDE", 0, true, ClanConstants.CREATE_COST));
        assertFalse(ClanCreateInputPolicy.isValid("Alpha", "A", ClanConstants.MAX_CLANS, true,
                ClanConstants.CREATE_COST));
        assertFalse(ClanCreateInputPolicy.isValid("Alpha", "A", 0, false, ClanConstants.CREATE_COST));
        assertFalse(ClanCreateInputPolicy.isValid("Alpha", "A", 0, true, ClanConstants.CREATE_COST - 1));
    }
}
