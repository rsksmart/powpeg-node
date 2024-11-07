package co.rsk.federate.watcher;

import co.rsk.federate.BtcToRskClient;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.peg.federation.Federation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

public class FederationWatcherListenerImpl implements FederationWatcherListener {

    private static final Logger logger = LoggerFactory.getLogger(FederationWatcherListenerImpl.class);
    
    private final BtcToRskClient btcToRskClientActive;
    private final BtcToRskClient btcToRskClientRetiring;
    private final BtcReleaseClient btcReleaseClient;

    public FederationWatcherListenerImpl(
            BtcToRskClient btcToRskClientActive,
            BtcToRskClient btcToRskClientRetiring,
            BtcReleaseClient btcReleaseClient) {
        this.btcToRskClientActive = btcToRskClientActive;
        this.btcToRskClientRetiring = btcToRskClientRetiring;
        this.btcReleaseClient = btcReleaseClient;
    }

    @Override
    public void onActiveFederationChange(Federation newActiveFederation) {
        triggerClientChange(btcToRskClientActive, newActiveFederation);
    }

    @Override
    public void onRetiringFederationChange(Federation newRetiringFederation) {
        if (newRetiringFederation == null) {
            clearRetiringFederationClient();
            return;
        }

        triggerClientChange(btcToRskClientRetiring, newRetiringFederation);
    }

    private void triggerClientChange(BtcToRskClient btcToRskClient, Federation newFederation) {
        // This method assumes that the new federation cannot be null
        Objects.requireNonNull(newFederation);
      
        try {
            // Stop the current clients
            btcToRskClient.stop();
            btcReleaseClient.stop(newFederation);

            // Start the current clients
            btcToRskClient.start(newFederation);
            btcReleaseClient.start(newFederation);

            logger.info(
                "[triggerClientChange] Clients for federation [{}] changed with success",
                newFederation.getAddress());
        } catch (Exception e) {
            logger.error(
                "[triggerClientChange] Clients for federation [{}] cannot be changed: {}",
                newFederation.getAddress(),
                e.getMessage());
        }
    }
    
    private void clearRetiringFederationClient() {
        logger.info("[triggerClientChange] Clearing retiring federation client");

        btcToRskClientRetiring.stop();
    }
}