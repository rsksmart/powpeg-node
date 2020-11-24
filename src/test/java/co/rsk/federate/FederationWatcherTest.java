package co.rsk.federate;

import co.rsk.bitcoinj.core.BtcECKey;
import co.rsk.bitcoinj.core.NetworkParameters;
import co.rsk.peg.Federation;
import co.rsk.peg.FederationMember;
import org.ethereum.crypto.ECKey;
import org.ethereum.facade.Ethereum;
import org.ethereum.listener.EthereumListenerAdapter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.powermock.reflect.Whitebox;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class FederationWatcherTest {
    private Federation federation1 = new Federation(
            getFederationMembersFromPksForBtc(1000, 2000, 3000, 4000),
            Instant.ofEpochMilli(5005L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
    );

    private Federation federation2 = new Federation(
            getFederationMembersFromPksForBtc(2000, 3000, 4000, 5000, 6000, 7000),
            Instant.ofEpochMilli(15300L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
    );

    private Federation federation3 = new Federation(
            getFederationMembersFromPksForBtc(5000, 6000, 7000),
            Instant.ofEpochMilli(7400L),
            0L,
            NetworkParameters.fromID(NetworkParameters.ID_REGTEST)
    );

    private FederationProvider federationProvider;
    private Ethereum ethereumMock;
    private FederationWatcher watcher;

    @Before
    public void createMocksAndWatcher() {
        federationProvider = mock(FederationProvider.class);
        ethereumMock = mock(Ethereum.class);
        watcher = new FederationWatcher(ethereumMock);
    }

    @Test
    public void setsListenerUp() throws Exception {
        Mockito.doAnswer((InvocationOnMock m) -> {
            Object listener = m.getArgument(0);
            Assert.assertEquals("co.rsk.federate.FederationWatcher$FederationWatcherRskListener", listener.getClass().getName());
            Assert.assertSame(Whitebox.getInternalState(watcher, "federationProvider"), federationProvider);
            return null;
        }).when(ethereumMock).addListener(any());

        watcher.setup(federationProvider);
        verify(ethereumMock).addListener(any());
    }

    @Test
    public void triggersActiveFederationChange_none_to_active() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.empty(), Optional.empty());
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation1.getAddress());
        when(federationProvider.getActiveFederation()).thenReturn(federation1);
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    Assert.assertEquals(Optional.empty(), oldFederation);
                    Assert.assertEquals(federation1, newFederation);
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(2, logger.activeCalls);
        Assert.assertEquals(0, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, times(1)).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    public void triggersActiveFederationChange_active_to_otherActive() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.of(federation1), Optional.empty());
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation2.getAddress());
        when(federationProvider.getActiveFederation()).thenReturn(federation2);
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    Assert.assertEquals(Optional.of(federation1), oldFederation);
                    Assert.assertEquals(federation2, newFederation);
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(2, logger.activeCalls);
        Assert.assertEquals(0, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, times(1)).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    public void doesntTriggerActiveOrRetiringFederationChange_none() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.of(federation1), Optional.empty());
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation1.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(0, logger.activeCalls);
        Assert.assertEquals(0, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    public void doesntTriggerActiveOrRetiringFederationChange_noChange() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.of(federation1), Optional.of(federation2));
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation1.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.of(federation2.getAddress()));

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(0, logger.activeCalls);
        Assert.assertEquals(0, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, never()).getRetiringFederation();
    }

    @Test
    public void triggersRetiringFederationChange_none_to_retiring() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.of(federation2), Optional.empty());
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation2.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.of(federation1.getAddress()));
        when(federationProvider.getRetiringFederation()).thenReturn(Optional.of(federation1));

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    Assert.assertEquals(Optional.empty(), oldFederation);
                    Assert.assertEquals(Optional.of(federation1), newFederation);
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(0, logger.activeCalls);
        Assert.assertEquals(2, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, times(1)).getRetiringFederation();
    }

    @Test
    public void triggersRetiringFederationChange_retiring_to_none() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.of(federation2), Optional.of(federation1));
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation2.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.empty());
        when(federationProvider.getRetiringFederation()).thenReturn(Optional.empty());

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    Assert.assertEquals(Optional.of(federation1), oldFederation);
                    Assert.assertEquals(Optional.empty(), newFederation);
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(0, logger.activeCalls);
        Assert.assertEquals(2, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, times(1)).getRetiringFederation();
    }

    @Test
    public void triggersRetiringFederationChange_retiring_to_otherRetiring() throws Exception {
        EthereumListenerAdapter rskListener = setupAndGetRskListener(Optional.of(federation3), Optional.of(federation1));
        class EventsLogger
        {
            public int activeCalls = 0;
            public int retiringCalls = 0;
        }

        when(federationProvider.getActiveFederationAddress()).thenReturn(federation3.getAddress());
        when(federationProvider.getRetiringFederationAddress()).thenReturn(Optional.of(federation2.getAddress()));
        when(federationProvider.getRetiringFederation()).thenReturn(Optional.of(federation2));

        EventsLogger logger = new EventsLogger();

        for (int i = 0; i < 2; i++) {
            watcher.addListener(new FederationWatcher.Listener() {
                @Override
                public void onActiveFederationChange(Optional<Federation> oldFederation, Federation newFederation) {
                    logger.activeCalls++;
                }

                @Override
                public void onRetiringFederationChange(Optional<Federation> oldFederation, Optional<Federation> newFederation) {
                    Assert.assertEquals(Optional.of(federation1), oldFederation);
                    Assert.assertEquals(Optional.of(federation2), newFederation);
                    logger.retiringCalls++;
                }
            });
        }

        rskListener.onBestBlock(null, null);
        Assert.assertEquals(0, logger.activeCalls);
        Assert.assertEquals(2, logger.retiringCalls);
        verify(federationProvider, times(1)).getActiveFederationAddress();
        verify(federationProvider, times(1)).getRetiringFederationAddress();
        verify(federationProvider, never()).getActiveFederation();
        verify(federationProvider, times(1)).getRetiringFederation();
    }

    private EthereumListenerAdapter setupAndGetRskListener(Optional<Federation> activeFederation, Optional<Federation> retiringFederation) throws Exception {
        class ListenerHolder {
            public EthereumListenerAdapter listener = null;
        }

        final ListenerHolder holder = new ListenerHolder();
        Mockito.doAnswer((InvocationOnMock m) -> {
            holder.listener = (EthereumListenerAdapter) m.getArgument(0);
            return null;
        }).when(ethereumMock).addListener(any());
        watcher.setup(federationProvider);
        Whitebox.setInternalState(watcher, "activeFederation", activeFederation);
        Whitebox.setInternalState(watcher, "retiringFederation", retiringFederation);
        Assert.assertNotNull(holder.listener);
        return holder.listener;
    }

    private List<FederationMember> getFederationMembersFromPksForBtc(Integer... pks) {
        return Arrays.stream(pks).map(n -> new FederationMember(
                BtcECKey.fromPrivate(BigInteger.valueOf(n)),
                new ECKey(),
                new ECKey()
        )).collect(Collectors.toList());
    }
}
