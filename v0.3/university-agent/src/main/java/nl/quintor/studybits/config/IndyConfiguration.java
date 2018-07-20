package nl.quintor.studybits.config;

import nl.quintor.studybits.indy.wrapper.IndyPool;
import nl.quintor.studybits.indy.wrapper.IndyWallet;
import nl.quintor.studybits.indy.wrapper.Issuer;
import nl.quintor.studybits.indy.wrapper.TrustAnchor;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.File;
import java.nio.file.Paths;

@Configuration
public class IndyConfiguration {
    @Bean
    public TrustAnchor universityTrustAnchor(IndyWallet universityWallet) throws Exception {
        return new TrustAnchor(universityWallet);
    }

    @Bean
    public Issuer universityIssuer(IndyWallet universityWallet) {
        return new Issuer(universityWallet);
    }

    @Bean
    public IndyWallet universityWallet() throws Exception {
        String name = "rug";
        String poolName = PoolUtils.createPoolLedgerConfig(null);
        IndyPool indyPool = new IndyPool(poolName);
        return IndyWallet.create(indyPool, name + "_wallet", StringUtils.leftPad(name, 32, '0'));
    }
}
