package co.rsk.federate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;

public class BtcToRskClientForkTest {

    //@Test
    public void simpleTest() throws Exception {
        copyFiles();
        Process bitcoind1 = null;
        Process bitcoind2 = null;
        Process rskNode = null;
        try {
            bitcoind1 = startBitcoind1();
            bitcoind2 = startBitcoind2();
            Thread.sleep(4000);
            mineBlocks(1);
            Thread.sleep(1000);
            sendToFederation();
            Thread.sleep(4000);
            mineBlocks(6);
            rskNode = startRskNode();
            Thread.sleep(10000);
            BigInteger bridgeBalance = getBridgeBalance();
            assertEquals(bridgeBalance, new BigInteger("21").multiply(BigInteger.TEN.pow(24)));
            Thread.sleep(150000);
            bridgeBalance = getBridgeBalance();
            assertEquals(bridgeBalance, new BigInteger("20999998").multiply(BigInteger.TEN.pow(18)));
            rskNode.waitFor();
        } finally {
            try { rskNode.destroyForcibly(); } catch (Exception e) {}
            try { bitcoind1.destroyForcibly(); } catch (Exception e) {}
            try { bitcoind2.destroyForcibly(); } catch (Exception e) {}
        }
    }

    private void copyFiles() throws IOException, InterruptedException {
        deleteDir("/Users/oscar/Library/Application Support/Bitcoin/regtest-2000tx/regtest");
        deleteDir("/Users/oscar/Library/Application Support/Bitcoin/regtest-2000tx/regtest2");

        copyDir("/Users/oscar/Library/Application Support/Bitcoin/regtest-peg-lock2000/regtest", "/Users/oscar/Library/Application Support/Bitcoin/regtest-2000tx/regtest");
        copyDir("/Users/oscar/Library/Application Support/Bitcoin/regtest-peg-lock2000/regtest2", "/Users/oscar/Library/Application Support/Bitcoin/regtest-2000tx/regtest2");
    }

    /**
     * USE WITH EXTREME CAUTION
     * Deletes a directory. No rollback
     * @param dirPath Directory to delete
     */
    private void deleteDir(String dirPath) throws IOException, InterruptedException {
        System.out.println("BtcToRskClientForkTest.deleteDir");
        System.out.println("dirPath = [" + dirPath + "]");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("rm");
        commandParts.add("-rf");
        commandParts.add(dirPath);
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        process.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        System.out.println("Delete dir result = " + line);
    }

    /**
     * Copy a directory
     */
    private void copyDir(String src, String target) throws IOException, InterruptedException {
        System.out.println("BtcToRskClientForkTest.copyDir");
        System.out.println("src = [" + src + "], target = [" + target + "]");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("cp");
        commandParts.add("-r");
        commandParts.add(src);
        commandParts.add(target);
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        process.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        System.out.println("line = " + line);
    }

    private String mineBlocks(int numberOfBlocks) throws IOException, InterruptedException {
        System.out.println("BtcToRskClientForkTest.mineBlocks");
        System.out.println("numberOfBlocks = [" + numberOfBlocks + "]");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("curl");
        commandParts.add("--user");
        commandParts.add("oscar:12345678");
        commandParts.add("--data-binary");
        commandParts.add("{\"jsonrpc\": \"1.0\", \"id\":\"curltest\", \"method\": \"generate\", \"params\": [" + numberOfBlocks + "] }");
        commandParts.add("-H");
        commandParts.add("content-type: text/plain;");
        commandParts.add("http://127.0.0.1:18332/");
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        process.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        System.out.println("line = " + line);
        String blockHash = line.substring(24, 88);
        System.out.println("blockHash = " + blockHash);
        return blockHash;
    }

    private void sendToFederation() throws IOException, InterruptedException {
        System.out.println("BtcToRskClientForkTest.sendToFederation");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("curl");
        commandParts.add("--user");
        commandParts.add("oscar:12345678");
        commandParts.add("--data-binary");
        commandParts.add("{\"jsonrpc\": \"1.0\", \"id\":\"curltest\", \"method\": \"sendtoaddress\", \"params\": [\"2N5muMepJizJE1gR7FbHJU6CD18V3BpNF9p\", 2] }");
        commandParts.add("-H");
        commandParts.add("content-type: text/plain;");
        commandParts.add("http://127.0.0.1:21500/");
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        process.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        System.out.println("line = " + line);
        //String txHash = line.substring(line.indexOf("{\"result\":[\"") + 12, line.indexOf("\"],\"error\":null,\"id\":\"curltest\"}"));
    }



    private Process startBitcoind1() throws IOException {
        System.out.println("BtcToRskClientForkTest.startBitcoind1");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("/Applications/Bitcoin-Qt12.app/Contents/MacOS/Bitcoin-Qt");
        commandParts.add("-regtest");
        commandParts.add("-printtoconsole ");
        commandParts.add("-datadir=/Users/oscar/Library/Application Support/Bitcoin/regtest-2000tx/regtest");
        commandParts.add("-debug");
        commandParts.add("-server");
        commandParts.add("-rpcuser=oscar");
        commandParts.add("-rpcpassword=12345678");
        commandParts.add("-limitancestorcount=100000");
        commandParts.add("-limitancestorsize=100000");
        commandParts.add("-limitdescendantcount=100000");
        commandParts.add("-limitdescendantsize=100000");
        commandParts.add("-maxmempool=4000");
        commandParts.add("-listen");
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        Process process = processBuilder.start();
        return process;
    }

    private Process startBitcoind2() throws IOException {
        System.out.println("BtcToRskClientForkTest.startBitcoind2");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("/Applications/Bitcoin-Qt12.app/Contents/MacOS/Bitcoin-Qt");
        commandParts.add("-regtest");
        commandParts.add("-printtoconsole ");
        commandParts.add("-datadir=/Users/oscar/Library/Application Support/Bitcoin/regtest-2000tx/regtest2");
        commandParts.add("-debug");
        commandParts.add("-server");
        commandParts.add("-rpcuser=oscar");
        commandParts.add("-rpcpassword=12345678");
        commandParts.add("-limitancestorcount=100000");
        commandParts.add("-limitancestorsize=100000");
        commandParts.add("-limitdescendantcount=100000");
        commandParts.add("-limitdescendantsize=100000");
        commandParts.add("-maxmempool=4000");
        commandParts.add("-connect=127.0.0.1");
        commandParts.add("-rpcport=21500");
        commandParts.add("-port=18334");
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts);
        Process process = processBuilder.start();
        return process;
    }



    private Process startRskNode() throws IOException {
        System.out.println("BtcToRskClientForkTest.startRskNode");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("java");
        commandParts.add("-Drsk.conf.file=/Users/oscar/codigo/rootstock/rootstockJ/rskj-core/src/main/resources/federator1.conf");
        commandParts.add("-cp");
        commandParts.add("/Users/oscar/codigo/rootstock/rootstockJ/rskj-core/build/libs/rskj-core-0.0.1-LOTUS-all.jar");
        commandParts.add("Start");

        ProcessBuilder processBuilder = new ProcessBuilder(commandParts)
                .directory(new File("/Users/oscar/temp/BtcToRskClientForkTest"));

        Process process = processBuilder.start();

        return process;
    }


    private BigInteger getBridgeBalance() throws IOException, InterruptedException {
        System.out.println("BtcToRskClientForkTest.getBridgeBalance");
        List<String> commandParts = new ArrayList<>();
        commandParts.add("curl");
        commandParts.add("--user");
        commandParts.add("oscar:12345678");
        commandParts.add("--data-binary");
        commandParts.add("{\"jsonrpc\": \"1.0\", \"id\":\"curltest\", \"method\": \"eth_getBalance\", \"params\": [\"0x0000000000000000000000000000000001000006\"] }");
        commandParts.add("-H");
        commandParts.add("content-type: text/plain;");
        commandParts.add("http://127.0.0.1:4444/");
        ProcessBuilder processBuilder = new ProcessBuilder(commandParts).redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = processBuilder.start();
        process.waitFor();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        System.out.println("line = " + line);
        String hexBalance = line.substring(45, 67);
        return new BigInteger(Hex.decode(hexBalance));
    }
}
