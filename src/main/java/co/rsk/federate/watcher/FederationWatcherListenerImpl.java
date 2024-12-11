package co.rsk.federate.watcher;

import co.rsk.federate.BtcToRskClient;
import co.rsk.federate.bitcoin.BitcoinWrapper;
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
    private final BitcoinWrapper bitcoinWrapper;

    public FederationWatcherListenerImpl(
            BtcToRskClient btcToRskClientActive,
            BtcToRskClient btcToRskClientRetiring,
            BtcReleaseClient btcReleaseClient,
            BitcoinWrapper bitcoinWrapper) {
        this.btcToRskClientActive = btcToRskClientActive;
        this.btcToRskClientRetiring = btcToRskClientRetiring;
        this.btcReleaseClient = btcReleaseClient;
        this.bitcoinWrapper = bitcoinWrapper;
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
            logger.info(
                "[onProposedFederationChange] Proposed federation was cleared");
            return;
        }

        try {
            // start {@code BtcReleaseClient} with proposed federation
            // so it can sign svp spend tx
            btcReleaseClient.start(newProposedFederation);

            // add proposed federation to active btc to rsk client so
            // it can register svp spend tx in the bridge
            bitcoinWrapper.addFederationListener(newProposedFederation, btcToRskClientActive);
          
            logger.info(
                "[onProposedFederationChange] Clients for proposed federation [{}] started with success",
                newProposedFederation.getAddress());
        } catch (Exception e) {
            logger.error(
                "[onProposedFederationChange] Clients for proposed federation [{}] failed to start",
                newProposedFederation.getAddress(),
                e);
        }
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
                "[triggerClientChange] Clients for federation [{}] cannot be changed",
                newFederation.getAddress(),
                e);
        }
    }
    
    private void clearRetiringFederationClient() {
        logger.info("[triggerClientChange] Clearing retiring federation client");

        btcToRskClientRetiring.stop();
    }
}
