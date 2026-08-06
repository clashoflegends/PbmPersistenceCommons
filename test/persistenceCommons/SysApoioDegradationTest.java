package persistenceCommons;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * An order upload gathers a build number and some display telemetry on its way out. Neither is worth
 * a failed submission, so both must degrade to an unknown value rather than throw. Both used to
 * throw: the version read blew up on a missing properties file, and the screen read blew up with
 * {@code HeadlessException} wherever there is no display.
 * <p>
 * Surefire runs this module headless (see the pom), so the screen case is exercised on every run.
 */
class SysApoioDegradationTest {

    @Test
    void anAbsentVersionFileYieldsAnEmptyVersionInsteadOfThrowing() {
        assertEquals("", SysApoio.getVersionClash("version_no_such_component"));
    }

    @Test
    void readsTheBuildNumberOfThisComponent() {
        // Present in this module (filtered at build time), so it must resolve to something.
        assertNotNull(SysApoio.getVersionClash("version_persistencecommons"));
    }

    @Test
    void screenTelemetryDegradesInsteadOfThrowingWithoutADisplay() {
        assertNotNull(SysApoio.getScreenSize());
        assertNotNull(SysApoio.getOsScale());
    }
}
