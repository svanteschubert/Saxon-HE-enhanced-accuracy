package net.sf.saxon;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in integration test: mvn -Pen16931-comparison verify. */
@EnabledIfSystemProperty(named = "en16931.compare", matches = "true")
class En16931ComparisonIT {
    @Test
    void compareOfficialValidatorsAndNumericProbes() throws Exception {
        En16931Comparison.compare(
                Path.of(System.getProperty("saxon.base.dir")),
                Path.of(System.getProperty("en16931.output")),
                Path.of(System.getProperty("en16931.fork.jar")));
    }
}
