package com.makar.tacticaltablet.storage;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

/** Completion handle for exactly one immutable persistence revision. */
public interface SaveTicket {
    Path target();
    long revision();
    CompletionStage<DurableSaveResult> completion();
}
