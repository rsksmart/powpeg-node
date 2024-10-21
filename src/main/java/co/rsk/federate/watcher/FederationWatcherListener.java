package co.rsk.federate.watcher;

import co.rsk.peg.federation.Federation;
import java.util.Optional;

/**
 * A listener interface for receiving notifications about changes in federations.
 * Implementers of this interface will be notified when the active or retiring federation changes.
 */
public interface FederationWatcherListener {

    /**
     * Invoked when the active federation changes.
     *
     * @param oldFederation an {@code Optional} containing the previous active federation.
     *                      This will be empty if there was no active federation before.
     * @param newFederation an {@code Optional} containing the new active federation.
     *                      This will be empty if there is no active federation after the change.
     */
    void onActiveFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation);

    /**
     * Invoked when the retiring federation changes.
     *
     * @param oldFederation an {@code Optional} containing the previous retiring federation.
     *                      This will be empty if there was no retiring federation before.
     * @param newFederation an {@code Optional} containing the new retiring federation.
     *                      This will be empty if there is no retiring federation after the change.
     */
    void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation);
}
