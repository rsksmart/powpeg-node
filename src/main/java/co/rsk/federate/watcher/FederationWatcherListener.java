package co.rsk.federate.watcher;

import co.rsk.peg.federation.Federation;

/**
 * A listener interface for receiving notifications about changes in federations.
 * Implementers of this interface will be notified when the active or retiring federation changes.
 */
public interface FederationWatcherListener {

    /**
     * Invoked when the active federation changes.
     *
     * @param newActiveFederation the new active federation after the change.
     *                      This will never be {@code null}; the active federation is always present.
     */
    void onActiveFederationChange(Federation newActiveFederation);

    /**
     * Invoked when the retiring federation changes.
     *
     * @param newRetiringFederation the new retiring federation after the change.
     *                      This can be {@code null}; the retiring federation is not always present.
     */
    void onRetiringFederationChange(Federation newRetiringFederation);

    /**
     * Invoked when the proposed federation changes.
     *
     * @param newProposedFederation the new proposed federation after the change.
     *                      This can be {@code null}; the proposed federation is not always present.
     */
    void onProposedFederationChange(Federation newProposedFederation);
}
