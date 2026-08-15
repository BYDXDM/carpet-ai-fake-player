import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FindJava {
    public static void main(String[] args) {
        // Common Java installation paths
        String[] paths = {
            "C:\\Program Files\\Java",
            "C:\\Program Files (x86)\\Java",
            "C:\\Users\\Administrator\\.jdks",
            "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft\\jdk*"
        };

        for (String p : paths) {
            File dir = new File(p);
            if (dir.exists()) {
                System.out.println("Found directory: " + p);
                try (Stream<Path> walk = Files.walk(dir.toPath(), 2)) {
                    walk.filter(Files::isDirectory)
                        .filter(path -> path.toString().contains("jdk") || path.toString().contains("java"))
                        .forEach(System.out::println);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
