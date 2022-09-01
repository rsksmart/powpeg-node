package co.rsk.federate.signing.hsm.requirements;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.rsk.federate.signing.hsm.message.ReleaseCreationInformation;
import org.junit.Test;

public class ReleaseRequirementsEnforcerTest {

    @Test
    public void enforce_does_nothing_if_version_one()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(1, mock(ReleaseCreationInformation.class));

        verify(ancestorBlockUpdater, never()).ensureAncestorBlockInPosition(any());
    }

    @Test
    public void enforce_version_two_ok()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(2, mock(ReleaseCreationInformation.class));

        verify(ancestorBlockUpdater, times(1)).ensureAncestorBlockInPosition(any());
    }

    @Test(expected = ReleaseRequirementsEnforcerException.class)
    public void enforce_version_two_updater_fails()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        doThrow(new Exception()).when(ancestorBlockUpdater).ensureAncestorBlockInPosition(any());
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(2, mock(ReleaseCreationInformation.class));
    }

    @Test
    public void enforce_version_three_ok()
        throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(3, mock(ReleaseCreationInformation.class));

        verify(ancestorBlockUpdater, times(1)).ensureAncestorBlockInPosition(any());
    }

    @Test(expected = ReleaseRequirementsEnforcerException.class)
    public void enforce_invalid_version()
        throws Exception {
        ReleaseRequirementsEnforcer enforcer = new ReleaseRequirementsEnforcer(
            mock(AncestorBlockUpdater.class)
        );

        enforcer.enforce(4, mock(ReleaseCreationInformation.class));
    }
}
