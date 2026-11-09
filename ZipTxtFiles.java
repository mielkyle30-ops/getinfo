import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipTxtFiles {

    /**
     * Legacy default: zip and delete the .txt files under ./cisco/yyyy-MM-dd/
     * (Prefer ziping(Path) with RUN_DIR.)
     */
    public void ziping() {
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        Path dir = Paths.get("./cisco").resolve(date);
        try {
            Path created = zipTxtFiles(dir);
            if (created != null) {
                deleteTxtFiles(dir);
                System.out.println("[" + dir + "] zipped and removed the .txt files -> " + created);
            }
        } catch (NoSuchFileException nsfe) {
            System.out.println("Folder not found: " + dir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Recommended: pass the run folder (RUN_DIR) directly.
     */
    public void ziping(Path dir) {
        if (dir == null) {
            System.out.println("ziping(Path): dir is null, skipping.");
            return;
        }
        try {
            Path created = zipTxtFiles(dir);
            if (created != null) {
                deleteTxtFiles(dir);
                System.out.println("[" + dir + "] zipped and removed the .txt files -> " + created);
            }
        } catch (NoSuchFileException nsfe) {
            System.out.println("Folder not found: " + dir);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Zip the *.txt files in dir into Switch_log.zip (or Switch_log (n).zip)
     * 
     * @return path of the zip file, or null if none was created
     */
    private static Path zipTxtFiles(Path dir) throws IOException {
        // check the folder exists
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.out.println("Not a folder: " + dir);
            return null;
        }

        // make sure there is something to zip
        int txtCount = countTxtFiles(dir);
        if (txtCount == 0) {
            System.out.println("No .txt files to zip: " + dir);
            return null;
        }

        // choose the zip name ((1), (2)... if it already exists)
        Path zipFile = nextAvailableZipPath(dir, "Switch_log.zip");

        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFile));
                DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {

            byte[] buffer = new byte[8192];
            int added = 0;

            for (Path filePath : stream) {
                // files only
                if (!Files.isRegularFile(filePath))
                    continue;

                ZipEntry entry = new ZipEntry(filePath.getFileName().toString());
                zipOut.putNextEntry(entry);
                try (InputStream in = new BufferedInputStream(Files.newInputStream(filePath))) {
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zipOut.write(buffer, 0, len);
                    }
                }
                zipOut.closeEntry();
                added++;
            }

            if (added == 0) {
                // nothing was added (e.g. a race): remove the empty zip
                Files.deleteIfExists(zipFile);
                System.out.println("Nothing added, zip not created: " + dir);
                return null;
            }
        }

        System.out.println("ZIP created: " + zipFile);
        return zipFile;
    }

    private static void deleteTxtFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            int deleted = 0;
            for (Path filePath : stream) {
                if (Files.isRegularFile(filePath)) {
                    Files.delete(filePath);
                    deleted++;
                }
            }
            System.out.println("Deleted .txt files: " + deleted);
        }
    }

    private static int countTxtFiles(Path dir) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path ignored : stream)
                count++;
        }
        return count;
    }

    private static Path nextAvailableZipPath(Path dir, String preferredName) throws IOException {
        Path preferred = dir.resolve(preferredName);
        if (!Files.exists(preferred)) {
            // make sure the parent folder exists
            Files.createDirectories(dir);
            return preferred;
        }
        // Switch_log (n).zip pattern
        String base = preferredName;
        String name = base;
        String stem;
        String ext;

        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            stem = base.substring(0, dot);
            ext = base.substring(dot); // ".zip"
        } else {
            stem = base;
            ext = "";
        }

        int n = 1;
        while (true) {
            name = String.format("%s (%d)%s", stem, n, ext);
            Path candidate = dir.resolve(name);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            n++;
        }
    }
}
