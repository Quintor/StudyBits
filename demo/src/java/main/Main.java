package main;

import lombok.extern.slf4j.Slf4j;
import org.hyperledger.indy.sdk.LibIndy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class Main {

    private static final String LIB_PATH = "/Users/dionrats/Nextcloud/school/Leerjaar_4/Stage/studybits/indy-sdk/libindy/target/debug";


    public static void main(String[] args) {
        LibIndy.init(LIB_PATH);
        SpringApplication.run(Main.class, args);
    }
}
