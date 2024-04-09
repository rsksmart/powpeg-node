package co.rsk.federate.bitcoin;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class BitcoinRpcClient {
    private String rpcUrl;
    private String rpcUser;
    private String rpcPassword;

    public BitcoinRpcClient(String rpcUrl, String rpcUser, String rpcPassword) {
        this.rpcUrl = rpcUrl;
        this.rpcUser = rpcUser;
        this.rpcPassword = rpcPassword;
    }

    public String executeRPC(String method, String params) throws IOException {
        String jsonRequest = "{\"jsonrpc\": \"1.0\", \"id\":\"1\", \"method\": \"" + method + "\", \"params\": [" + params + "]}";

        URL url = new URL(this.rpcUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("content-type", "text/plain;");

        String authString = rpcUser + ":" + rpcPassword;
        byte[] message = authString.getBytes("UTF-8");
        String basicAuth = DatatypeConverter.printBase64Binary(message);
        conn.setRequestProperty("Authorization", "Basic " + basicAuth);

        conn.setDoOutput(true);
        OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
        writer.write(jsonRequest);
        writer.flush();
        writer.close();
        conn.getOutputStream().close();

        InputStream responseStream = conn.getResponseCode() / 100 == 2
                                         ? conn.getInputStream()
                                         : conn.getErrorStream();
        Scanner s = new Scanner(responseStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }
}
