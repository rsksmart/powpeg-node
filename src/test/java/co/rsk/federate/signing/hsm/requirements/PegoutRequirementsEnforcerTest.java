package co.rsk.federate.signing.hsm.requirements;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.rsk.federate.signing.hsm.message.PegoutCreationInformation;
import org.junit.Test;

public class PegoutRequirementsEnforcerTest {

    @Test
    public void enforce_does_nothing_if_version_one()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(1, mock(PegoutCreationInformation.class));

        verify(ancestorBlockUpdater, never()).ensureAncestorBlockInPosition(any());
    }

    @Test
    public void enforce_version_two_ok()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(2, mock(PegoutCreationInformation.class));

        verify(ancestorBlockUpdater, times(1)).ensureAncestorBlockInPosition(any());
    }

    @Test(expected = PegoutRequirementsEnforcerException.class)
    public void enforce_version_two_updater_fails()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        doThrow(new Exception()).when(ancestorBlockUpdater).ensureAncestorBlockInPosition(any());
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(2, mock(PegoutCreationInformation.class));
    }


    @Test(expected = PegoutRequirementsEnforcerException.class)
    public void enforce_invalid_version()
        throws Exception {
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(
            mock(AncestorBlockUpdater.class)
        );

        enforcer.enforce(3, mock(PegoutCreationInformation.class));
    }
}
