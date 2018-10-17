package nl.quintor.studybits.config;

import nl.quintor.studybits.indy.wrapper.*;
import nl.quintor.studybits.indy.wrapper.message.IndyMessageTypes;
import nl.quintor.studybits.indy.wrapper.util.PoolUtils;
import nl.quintor.studybits.messages.StudyBitsMessageTypes;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.hyperledger.indy.sdk.pool.Pool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.io.File;
import java.nio.file.Paths;

@Configuration
public class IndyConfiguration {
    @Value("${nl.quintor.studybits.university.name}")
    private String universityName;

    @Bean
    public TrustAnchor universityTrustAnchor(IndyWallet universityWallet) throws Exception {
        return new TrustAnchor(universityWallet);
    }

    @Bean
    public Verifier universityVerifier(IndyWallet universityWallet) {
        return new Verifier(universityWallet);
    }

    @Bean
    public Issuer universityIssuer(IndyWallet universityWallet) {
        return new Issuer(universityWallet);
    }

    @Bean
    public IndyWallet universityWallet() throws Exception {
        Pool.setProtocolVersion(PoolUtils.PROTOCOL_VERSION).get();
        StudyBitsMessageTypes.init();
        IndyMessageTypes.init();
        String name = universityName.replace(" ", "");
        String poolName = PoolUtils.createPoolLedgerConfig(null);
        IndyPool indyPool = new IndyPool(poolName);
        return IndyWallet.create(indyPool, name, StringUtils.leftPad(name, 32, '0'));
    }
}
