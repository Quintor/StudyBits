package nl.quintor.studybits.university;

import org.apache.commons.io.FileUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.io.File;
import java.nio.file.Paths;

@EnableAutoConfiguration
@ComponentScan
public class Main {

    public static void main( String[] args ) throws Exception {
        removeIndyClientDirectory();
        SpringApplication.run(Main.class, args);
    }

    private static void removeIndyClientDirectory() throws Exception {
        String homeDir = System.getProperty("user.home");
        File indyClientDir = Paths.get(homeDir, ".indy_client")
                                  .toFile();
        FileUtils.deleteDirectory(indyClientDir);
    }
}