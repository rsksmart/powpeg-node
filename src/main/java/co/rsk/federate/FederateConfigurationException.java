package co.rsk.federate;

/**
 * Created by mario on 30/08/2016.
 */
public class FederateConfigurationException  extends RuntimeException{

    public FederateConfigurationException(String msg, Throwable e) {
        super(msg, e);
    }

    public FederateConfigurationException(String msg) {
        super(msg);
    }
}
