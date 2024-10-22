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
        this.btcToRskClientActive = btcToRskClientActive;
        this.btcToRskClientRetiring = btcToRskClientRetiring;
        this.btcReleaseClient = btcReleaseClient;
    }

    @Override
    public void onActiveFederationChange(Federation newFederation) {
        triggerClientChange(btcToRskClientActive, newFederation);
    }

    @Override
    public void onRetiringFederationChange(Federation newFederation) {
        triggerClientChange(btcToRskClientRetiring, newFederation);
    }

    private void triggerClientChange(BtcToRskClient btcToRskClient, Federation newFederation) {
        // Stop the current clients
        btcToRskClient.stop();
        Optional.ofNullable(newFederation).ifPresent(btcReleaseClient::stop);

        // Check if this federator is part of the new federation
        if (newFederation == null || !newFederation.isMember(federationMember)) {
            logger.warn(
                "[triggerClientChange] This federator node ({}) is not part of the new federation ({}). Check your configuration for signers BTC, RSK, and MST keys",
                federationMember,
                Optional.ofNullable(newFederation).map(Federation::getAddress).orElse(null));
            return;
        }

        // Start clients if federator is part of the new federation
        String federationAddress = newFederation.getAddress().toString();
        logger.debug(
            "[triggerClientChange] Starting lock and release clients since I belong to federation {}", federationAddress);
        logger.info(
            "[triggerClientChange] Joined to {} federation", federationAddress);

        btcToRskClient.start(newFederation);
        btcReleaseClient.start(newFederation);
    }
}
