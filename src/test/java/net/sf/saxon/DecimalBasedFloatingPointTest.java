package net.sf.saxon;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DecimalBasedFloatingPointTest {
    @Test
    void commandLineRoundingRegression() throws Exception {
        Path root = Path.of(System.getProperty("saxon.base.dir"));
        Path output = root.resolve("target/generated-sources/out.xml");
        Files.createDirectories(output.getParent());
        ByteArrayOutputStream messages = new ByteArrayOutputStream();
        PrintStream original = System.err;
        try (PrintStream capture = new PrintStream(messages, true, StandardCharsets.UTF_8)) {
            System.setErr(capture);
            Transform.main(new String[]{
                    "-s:" + root.resolve("src/test/resources/xml/in.xml"),
                    "-xsl:" + root.resolve("src/test/resources/xsl/test.xsl"),
                    "-o:" + output, "-quit:off"
            });
        } finally {
            System.setErr(original);
        }
        String actual = messages.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        Files.writeString(root.resolve("target/xsl-rounding-test.txt"), actual);
        String expected = Files.readString(root.resolve("src/test/resources/references/xsl-rounding-test.txt"))
                .replace("\r\n", "\n");
        assertEquals(expected, actual);
    }
}
