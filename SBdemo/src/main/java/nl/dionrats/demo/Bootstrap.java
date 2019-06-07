package nl.dionrats.demo;

import lombok.extern.slf4j.Slf4j;
import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.apache.commons.io.FileUtils;
import org.hyperledger.indy.sdk.pool.Pool;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;

@Slf4j
@Component
public class Bootstrap {

    private static final String TEST_POOL_IP = "10.40.121.131";
    private static final String POOL_NAME = "testPool";

    @EventListener
    public void boot(ContextRefreshedEvent event) {
        try {
            removeIndyClientDirectory();
            Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
            String poolname = PoolUtils.createPoolLedgerConfig(TEST_POOL_IP, POOL_NAME + System.currentTimeMillis());
            IndyPool indyPool = new IndyPool(poolname);

            log.debug("DEMO", indyPool.getPoolName());
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
