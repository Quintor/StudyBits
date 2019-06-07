package nl.quintor.studybits;

import org.apache.commons.io.FileUtils;
import org.hyperledger.indy.sdk.LibIndy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.nio.file.Paths;

@SpringBootApplication
public class Main {

    private static final String LIBPATH = "/Users/dionrats/Nextcloud/school/Leerjaar_4/Stage/studybits/indy-sdk/libindy/target/debug";

    public static void main(String[] args) throws Exception {
        LibIndy.init(LIBPATH);
        removeIndyClientDirectory();
        SpringApplication.run(Main.class, args);
    }

    private static void removeIndyClientDirectory() throws Exception {
        String homeDir = System.getProperty("user.home");
        File indyClientDir = Paths.get(homeDir, ".indy_client").toFile();
        FileUtils.deleteDirectory(indyClientDir);
    }
}