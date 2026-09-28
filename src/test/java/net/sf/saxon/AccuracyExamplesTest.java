package net.sf.saxon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.AccuracyExamples.Example;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.Xslt30Transformer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs every example of the README table on this fork. Each test is named after its row,
 * showing the expression, this fork's result and the result of stock Saxon-HE 13.0.
 * En16931ComparisonIT checks the stock column against the real Saxon-HE 13.0 JAR.
 */
class AccuracyExamplesTest {

    private static final Path ROOT = Path.of(System.getProperty("saxon.base.dir", "."));

    private static List<Example> examples;
    private static Map<String, String> actual;

    @BeforeAll
    static void evaluate(@TempDir Path temp) throws Exception {
        examples = AccuracyExamples.load(ROOT.resolve(AccuracyExamples.EXAMPLES));
        Processor processor = new Processor(false);
        Xslt30Transformer transformer = processor.newXsltCompiler()
                .compile(new StreamSource(ROOT.resolve(AccuracyExamples.STYLESHEET).toFile())).load30();
        Path results = temp.resolve("results.xml");
        Serializer serializer = processor.newSerializer(results.toFile());
        transformer.transform(new StreamSource(ROOT.resolve(AccuracyExamples.EXAMPLES).toFile()), serializer);
        serializer.close();
        actual = AccuracyExamples.results(results);
    }

    @TestFactory
    Stream<DynamicTest> everyExampleGivesTheForkResult() {
        return examples.stream().map(example -> DynamicTest.dynamicTest(
                example.id() + "  " + example.xpath() + "  =  " + example.fork()
                        + "   (Saxon-HE 13.0: " + example.stock() + ")",
                () -> Assertions.assertEquals(example.fork(), actual.get(example.id()))));
    }

    @Test
    void onlyTheContrastCasesAgreeWithStockSaxon() {
        for (Example example : examples) {
            Assertions.assertEquals(example.cause().equals("same"), example.stock().equals(example.fork()),
                    example.id() + " must differ from stock Saxon unless its cause is 'same'");
        }
    }

    @Test
    void readmeTableListsEveryExample() throws Exception {
        List<String> readme = Files.readAllLines(ROOT.resolve("README.md"));
        for (Example example : examples) {
            String row = readme.stream().filter(line -> line.startsWith("| " + example.id() + " |"))
                    .findFirst().orElse(null);
            Assertions.assertNotNull(row, "README.md has no table row for " + example.id());
            for (String value : List.of(example.xpath(), example.stock(), example.fork())) {
                Assertions.assertTrue(row.contains("`" + value + "`"),
                        "README.md row " + example.id() + " must show `" + value + "`");
            }
        }
    }
}
