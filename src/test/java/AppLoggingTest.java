import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AppLoggingTest.java
 *
 * Tests the LOG_LEVEL override logic in AppLogging: both the JDK level
 * names and the familiar DEBUG/WARN/ERROR aliases must map to the right
 * java.util.logging Level, and an invalid value must leave the configured
 * level alone instead of breaking logging.
 */
class AppLoggingTest {

    private final Logger appLog = Logger.getLogger(AppLogging.ROOT);
    private Level original;

    @BeforeEach
    void setUp() {
        original = appLog.getLevel();
        appLog.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        appLog.setLevel(original);
    }

    @ParameterizedTest(name = "LOG_LEVEL={0} -> {1}")
    @CsvSource({
            "FINE, FINE",
            "debug, FINE",
            "TRACE, FINER",
            "info, INFO",
            "WARN, WARNING",
            "warning, WARNING",
            "ERROR, SEVERE"
    })
    void applyLevel_acceptsJdkNamesAndCommonAliases(String input, String expected) {
        AppLogging.applyLevel(input);

        assertEquals(Level.parse(expected), appLog.getLevel());
    }

    @Test
    void applyLevel_withUnknownValue_keepsCurrentLevel() {
        AppLogging.applyLevel("LOUD");

        assertEquals(Level.INFO, appLog.getLevel());
    }
}
