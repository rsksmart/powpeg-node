package co.rsk.federate.signing.hsm.requirements;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import co.rsk.federate.signing.hsm.message.PegoutCreationInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PegoutRequirementsEnforcerTest {

    private AncestorBlockUpdater ancestorBlockUpdater;
    private ReleaseRequirementsEnforcer enforcer;

    @BeforeEach
    void setup() {
        ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        enforcer = new ReleaseRequirementsEnforcer(ancestorBlockUpdater);
    }

    @Test
    void enforce_does_nothing_if_version_one() throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(ancestorBlockUpdater);

        enforcer.enforce(1, mock(PegoutCreationInformation.class));

        verify(ancestorBlockUpdater, never()).ensureAncestorBlockInPosition(any());
    }

    @Test
    void enforce_version_two_ok() throws Exception {
        test_enforce_version(ancestorBlockUpdater, enforcer, 2);
    }

    @Test
    void enforce_version_three_ok() throws Exception {
        test_enforce_version(ancestorBlockUpdater, enforcer, 3);
    }

    @Test
    void enforce_version_four_ok() throws Exception {
        test_enforce_version(ancestorBlockUpdater, enforcer, 4);
    }

    void test_enforce_version(AncestorBlockUpdater ancestorBlockUpdater, ReleaseRequirementsEnforcer enforcer, int version) throws Exception {
        enforcer.enforce(version, mock(PegoutCreationInformation.class));

        verify(ancestorBlockUpdater, times(1)).ensureAncestorBlockInPosition(any());
    }

    @Test
    void enforce_version_two_updater_fails() throws Exception {
        AncestorBlockUpdater ancestorBlockUpdater = mock(AncestorBlockUpdater.class);
        doThrow(new Exception()).when(ancestorBlockUpdater).ensureAncestorBlockInPosition(any());
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(ancestorBlockUpdater);

        assertThrows(ReleaseRequirementsEnforcerException.class, () -> enforcer.enforce(2, mock(PegoutCreationInformation.class)));
    }

    @Test
    void enforce_invalid_version() {
        PegoutRequirementsEnforcer enforcer = new PegoutRequirementsEnforcer(mock(AncestorBlockUpdater.class));

        assertThrows(PegoutRequirementsEnforcer.class, () -> enforcer.enforce(-5, mock(PegoutCreationInformation.class)));
    }
}
