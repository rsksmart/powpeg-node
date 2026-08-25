package co.rsk.federate.watcher;

import co.rsk.federate.BtcToRskClient;
import co.rsk.federate.btcreleaseclient.BtcReleaseClient;
import co.rsk.peg.federation.Federation;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FederationWatcherListenerImpl implements FederationWatcherListener {

    private static final Logger logger = LoggerFactory.getLogger(FederationWatcherListenerImpl.class);

    private final BtcToRskClient btcToRskClientActive;
    private final BtcToRskClient btcToRskClientRetiring;
    private final BtcReleaseClient btcReleaseClient;

    public FederationWatcherListenerImpl(
        BtcToRskClient btcToRskClientActive,
        BtcToRskClient btcToRskClientRetiring,
        BtcReleaseClient btcReleaseClient
    ) {
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

    @Override
    public void onProposedFederationChange(Federation newProposedFederation) {
        if (newProposedFederation == null) {
            logger.info("[onProposedFederationChange] Proposed federation was cleared");
            return;
        }

        // start {@code BtcReleaseClient} with proposed federation,
        // so it can sign svp spend tx
        //
        // Failures are propagated on purpose, so that FederationWatcher does not record this
        // federation as notified and retries on the next best block. See triggerClientChange
        btcReleaseClient.start(newProposedFederation);

        logger.info(
            "[onProposedFederationChange] BtcReleaseClient for proposed federation [{}] started with success",
            newProposedFederation.getAddress()
        );
    }

    private void triggerClientChange(BtcToRskClient btcToRskClient, Federation newFederation) {
        // This method assumes that the new federation cannot be null
        Objects.requireNonNull(newFederation);

        // Failures are propagated on purpose. FederationWatcher records the federation it notified
        // about only after this method returns normally, so throwing keeps its state stale and the
        // change is retried on the next best block; stop and start are idempotent, so retrying is
        // safe. Swallowing here would instead leave this node neither watching pegins nor signing
        // pegouts for the federation, with no further attempt. Escaping exceptions cannot disrupt
        // block processing, since rskj isolates every listener callback.

        // Stop the current clients
        btcToRskClient.stop();
        btcReleaseClient.stop(newFederation);

        // Start the current clients
        btcToRskClient.start(newFederation);
        btcReleaseClient.start(newFederation);

        logger.info(
            "[triggerClientChange] Clients for federation [{}] changed with success",
            newFederation.getAddress());
    }
    
    private void clearRetiringFederationClient() {
        logger.info("[triggerClientChange] Clearing retiring federation client");

        btcToRskClientRetiring.stop();
    }
}
