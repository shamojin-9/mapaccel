package com.shamoji.mapaccel.server;

import com.shamoji.mapaccel.security.TrustLedger;
import com.shamoji.mapaccel.security.ValidationCoordinator;

public final class MapAccelServerState {
    public static final ClientResourceLedger CLIENT_RESOURCES = new ClientResourceLedger();
    public static final TrustLedger TRUST = new TrustLedger();
    public static final ValidationCoordinator VALIDATION = new ValidationCoordinator(TRUST);

    private MapAccelServerState() {
    }
}
