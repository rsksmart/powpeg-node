package co.rsk.federate.bitcoin;

import co.rsk.federate.rpc.JsonRpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class BitcoinRpcClientTest {
    private String rpcHost;
    private int rpcPort;
    private String rpcUser;
    private String rpcPassword;

    @BeforeEach
    void setUp() {
        rpcHost = "127.0.0.1";
        rpcPort = 18332;
        rpcUser = "rsk";
        rpcPassword = "rsk";
    }

    @Test
    void test() throws IOException, JsonRpcException {
        String rpcUser = "rsk";
        String rpcPassword = "rsk";
        String rpcUrl = "http://127.0.0.1:18332";
        BitcoinRpcClient rpcClient = new BitcoinRpcClient(rpcUrl, rpcUser, rpcPassword);

        String transactionId = "99a0dcc5c9cd79107e2d912af379a7c41a345b932a54beafa9d6f5953a9dc72a";
        try {
            String result = rpcClient.executeRPC("gettransaction", "\"" + transactionId + "\"");
            System.out.println("Blockchain Info: " + result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
