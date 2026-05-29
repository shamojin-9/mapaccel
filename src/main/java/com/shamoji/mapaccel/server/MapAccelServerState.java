package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.security.InboundRateLimiter;
import com.shamoji.mapaccel.security.TrustLedger;
import com.shamoji.mapaccel.security.ValidationCoordinator;

public final class MapAccelServerState {
    public static final ClientResourceLedger CLIENT_RESOURCES = new ClientResourceLedger();
    public static final PreviewAssistLedger PREVIEW_ASSIST = new PreviewAssistLedger();
    public static final TrustLedger TRUST = new TrustLedger();
    public static final ValidationCoordinator VALIDATION = new ValidationCoordinator(TRUST);
    public static final InboundRateLimiter RATE_LIMITER = new InboundRateLimiter();

    private MapAccelServerState() {
    }
}
