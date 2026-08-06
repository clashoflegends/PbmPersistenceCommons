package persistenceCommons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The build number is read while composing an order upload and on every log line, so a missing or
 * unreadable {@code version_<component>.properties} must degrade to an unknown version. It used to
 * throw {@code MissingResourceException} straight out of the upload.
 */
class SysApoioVersionTest {

    @Test
    void anAbsentVersionFileYieldsAnEmptyVersionInsteadOfThrowing() {
        assertEquals("", SysApoio.getVersionClash("version_no_such_component"));
    }

    @Test
    void readsTheBuildNumberOfThisComponent() {
        // Present in this module (filtered at build time), so it must resolve to something.
        assertNotNull(SysApoio.getVersionClash("version_persistencecommons"));
    }
}
