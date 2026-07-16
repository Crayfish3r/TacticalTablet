package com.makar.tacticaltablet.progression;

enum IdempotentCreditStatus {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    FAILED
}
