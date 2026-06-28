package persistenceCommons;

import com.thoughtworks.xstream.XStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * EGF round-trip verification: reads every .egf file under the given directory,
 * deserializes with XStream 1.4.21 + model.** whitelist, re-serializes, and
 * compares the two XML strings. Run once after the XStream upgrade to satisfy the
 * Phase 2 verification gate.
 *
 * Usage:
 *   mvn -pl PbmPersistenceCommons exec:java \
 *     -Dexec.mainClass=persistenceCommons.EgfRoundTripVerify \
 *     -Dexec.args="."
 */
public class EgfRoundTripVerify {

    public static void main(String[] args) throws Exception {
        String rootPath = args.length > 0 ? args[0] : ".";
        File root = new File(rootPath);
        if (!root.exists()) {
            System.err.println("Directory not found: " + rootPath);
            System.exit(1);
        }

        List<File> egfFiles = new ArrayList<>();
        collectEgf(root, egfFiles);
        System.out.println("Found " + egfFiles.size() + " EGF files under " + rootPath);

        int passed = 0, failed = 0;
        for (File f : egfFiles) {
            try {
                Object obj = deserialize(f);
                String xml1 = serialize(obj);
                Object obj2 = fromXml(xml1);
                String xml2 = serialize(obj2);
                if (xml1.equals(xml2)) {
                    System.out.println("[PASS] " + f.getName());
                    passed++;
                } else {
                    System.out.println("[DIFF] " + f.getName() + " (round-trip XML differs)");
                    failed++;
                }
            } catch (Exception e) {
                System.out.println("[FAIL] " + f.getName() + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                failed++;
            }
        }
        System.out.println("\nResults: " + passed + " passed, " + failed + " failed");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void collectEgf(File dir, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collectEgf(f, out);
            else if (f.getName().endsWith(".egf")) out.add(f);
        }
    }

    private static XStream newXStream() {
        XStream xs = new XStream();
        xs.allowTypesByWildcard(new String[]{"model.**"});
        return xs;
    }

    private static Object deserialize(File egfFile) throws Exception {
        File xml = ZipManager.getInstance().doUncompressGzip(egfFile);
        try (InputStream is = new BufferedInputStream(new FileInputStream(xml));
             InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
            return newXStream().fromXML(reader);
        }
    }

    private static String serialize(Object obj) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Writer writer = new OutputStreamWriter(out, "UTF-8");
        newXStream().toXML(obj, writer);
        writer.flush();
        return out.toString("UTF-8");
    }

    private static Object fromXml(String xml) {
        return newXStream().fromXML(xml);
    }
}
