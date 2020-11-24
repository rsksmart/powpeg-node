package co.rsk.federate.solidity;

import org.ethereum.config.SystemProperties;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by mario on 03/04/17.
 */
public class DummySolidityCompiler extends SolidityCompiler{

    private static Logger logger = LoggerFactory.getLogger(DummySolidityCompiler.class);

    public DummySolidityCompiler(SystemProperties config) {
        super(config);
    }

    @Override
    public SolidityCompiler.Result compile(byte[] source, boolean combinedJson, SolidityCompiler.Options... options) throws IOException {
        logger.debug("Someone tried to compile something on a FedNode...");
        return null;
    }
}
