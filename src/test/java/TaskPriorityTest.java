import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TaskPriorityTest.java
 *
 * Exercises Task.Priority.fromString(), the parsing logic that turns raw
 * form input (a String submitted from the browser) into a Priority enum
 * value. This is meaningful application logic, not a trivial getter: it
 * has to handle a null value, a case-mismatched value, and a value that
 * doesn't match any known priority at all, and it's supposed to fall
 * back to MEDIUM in every one of those situations rather than throwing
 * an exception that would crash the /add request.
 */
class TaskPriorityTest {

    @ParameterizedTest(name = "fromString(\"{0}\") -> {1}")
    @CsvSource({
            "HIGH, HIGH",
            "high, HIGH",
            "High, HIGH",
            "  MEDIUM  , MEDIUM",
            "low, LOW"
    })
    void fromString_recognizesKnownValuesCaseInsensitively(String input, Task.Priority expected) {
        assertEquals(expected, Task.Priority.fromString(input));
    }

    @Test
    void fromString_nullDefaultsToMedium() {
        assertEquals(Task.Priority.MEDIUM, Task.Priority.fromString(null));
    }

    @ParameterizedTest(name = "fromString(\"{0}\") defaults to MEDIUM")
    @ValueSource(strings = { "", "urgent", "HIGHEST", "not-a-priority" })
    void fromString_unrecognizedValueDefaultsToMedium(String input) {
        assertEquals(Task.Priority.MEDIUM, Task.Priority.fromString(input));
    }

    @Test
    void label_capitalizesOnlyTheFirstLetter() {
        assertEquals("High", Task.Priority.HIGH.label());
        assertEquals("Medium", Task.Priority.MEDIUM.label());
        assertEquals("Low", Task.Priority.LOW.label());
    }

    @Test
    void cssClass_isLowercaseWithPriorityPrefix() {
        assertEquals("priority-high", Task.Priority.HIGH.cssClass());
        assertEquals("priority-low", Task.Priority.LOW.cssClass());
    }
}
