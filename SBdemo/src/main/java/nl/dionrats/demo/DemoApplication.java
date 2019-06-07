package nl.dionrats.demo;

import org.hyperledger.indy.sdk.LibIndy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

    private static final String LIB_PATH = "/Users/dionrats/Nextcloud/school/Leerjaar_4/Stage/studybits/indy-sdk/libindy/target/debug";

    public static void main(String[] args) {
        LibIndy.init(LIB_PATH);
        SpringApplication.run(DemoApplication.class, args);
    }

}
