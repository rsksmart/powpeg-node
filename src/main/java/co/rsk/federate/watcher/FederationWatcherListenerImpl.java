package co.rsk.federate.watcher;

import co.rsk.federate.BtcToRskClient;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.peg.federation.Federation;
import co.rsk.peg.federation.FederationMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.util.Optional;

public class FederationWatcherListenerImpl implements FederationWatcherListener {

    private static final Logger logger = LoggerFactory.getLogger(FederationWatcherListenerImpl.class);
    
    private final FederationMember federationMember;
    private final BtcToRskClient btcToRskClientActive;
    private final BtcToRskClient btcToRskClientRetiring;
    private final BtcReleaseClient btcReleaseClient;

    public FederationWatcherListenerImpl(
            FederationMember federationMember,
            BtcToRskClient btcToRskClientActive,
            BtcToRskClient btcToRskClientRetiring,
            BtcReleaseClient btcReleaseClient) {
        this.federationMember = Objects.requireNonNull(federationMember);
        this.btcToRskClientActive = Objects.requireNonNull(btcToRskClientActive);
        this.btcToRskClientRetiring = Objects.requireNonNull(btcToRskClientRetiring);
        this.btcReleaseClient = Objects.requireNonNull(btcReleaseClient);
    }

    @Override
    public void onActiveFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
        String oldFederationAddress = oldFederation.map(federation -> federation.getAddress().toString()).orElse("NONE");
        String newFederationAddress = oldFederation.map(federation -> federation.getAddress().toString()).orElse("NONE");
        logger.debug("[onActiveFederationChange] Active federation change: from {} to {}", oldFederationAddress, newFederationAddress);
        triggerClientChange(btcToRskClientActive, newFederation);
    }

    @Override
    public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
        String oldFederationAddress = oldFederation.map(federation -> federation.getAddress().toString()).orElse("NONE");
        String newFederationAddress = newFederation.map(federation -> federation.getAddress().toString()).orElse("NONE");
        logger.debug("[onRetiringFederationChange] Retiring federation change: from {} to {}", oldFederationAddress, newFederationAddress);
        triggerClientChange(btcToRskClientRetiring, newFederation);
    }

    private void triggerClientChange(BtcToRskClient client, Optional<Federation> federation) {
        // Stop the current clients
        client.stop();
        federation.ifPresent(btcReleaseClient::stop);

        // Exit early if federation is not present
        if (federation.isEmpty()) {
            logger.warn("[triggerClientChange] No federation available to join.");
            return;
        }

        Federation newFederation = federation.get();

        // Check if this federator is part of the new federation
        if (!newFederation.isMember(federationMember)) {
            logger.warn(
                "[triggerClientChange] This federator node is not part of the new federation. Check your configuration for signers BTC, RSK, and MST keys");
            return;
        }

        // Start clients if federator is part of the new federation
        String federationAddress = newFederation.getAddress().toString();
        logger.debug(
            "[triggerClientChange] Starting lock and release clients since I belong to federation {}", federationAddress);
        logger.info(
            "[triggerClientChange] Joined to {} federation", federationAddress);

        client.start(newFederation);
        btcReleaseClient.start(newFederation);
    }
}
