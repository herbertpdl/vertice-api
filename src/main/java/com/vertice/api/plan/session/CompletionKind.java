package com.vertice.api.plan.session;

/** Who finished a session: the client, or the weekly rollover for one left in progress. */
public enum CompletionKind {
    CLIENT,
    AUTO
}
