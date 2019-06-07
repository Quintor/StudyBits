package main;

import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.apache.commons.io.FileUtils;
import org.hyperledger.indy.sdk.pool.Pool;
import org.springframework.context.event.EventListener;

import java.io.File;
import java.nio.file.Paths;

public class Bootstrap {

    private static final String TEST_POOL_IP = "10.40.121.131";
    private static final String POOL_NAME = "testPool";

   @EventListener
    public void boot() {
        try {
            removeIndyClientDirectory();
            Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
            String poolname = PoolUtils.createPoolLedgerConfig(null, POOL_NAME + System.currentTimeMillis());
            IndyPool indyPool = new IndyPool(poolname);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeIndyClientDirectory() throws Exception {
        String homeDir = System.getProperty("user.home");
        File indyClientDir = Paths.get(homeDir, ".indy_client").toFile();
        FileUtils.deleteDirectory(indyClientDir);
    }
}
