package net.sf.saxon;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * The example table in src/test/resources/examples/accuracy-examples.xml, shared by
 * {@link AccuracyExamplesTest} (this fork) and {@link En16931Comparison} (stock Saxon-HE 13.0).
 */
final class AccuracyExamples {

    static final String EXAMPLES = "src/test/resources/examples/accuracy-examples.xml";
    static final String STYLESHEET = "src/test/resources/examples/accuracy-examples.xsl";

    /** One row of the table: the XPath is evaluated with the {@code input} element as context item. */
    record Example(String id, String cause, String xpath, String stock, String fork) { }

    private AccuracyExamples() { }

    static List<Example> load(Path examples) throws Exception {
        List<Example> result = new ArrayList<>();
        NodeList nodes = parse(examples).getElementsByTagName("example");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element example = (Element) nodes.item(i);
            result.add(new Example(example.getAttribute("id"), example.getAttribute("cause"),
                    text(example, "xpath"), text(example, "stock"), text(example, "fork")));
        }
        return result;
    }

    /** @return the actual value of each example id, as written by accuracy-examples.xsl */
    static Map<String, String> results(Path results) throws Exception {
        Map<String, String> actual = new LinkedHashMap<>();
        NodeList nodes = parse(results).getElementsByTagName("result");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element result = (Element) nodes.item(i);
            actual.put(result.getAttribute("id"), result.getAttribute("actual"));
        }
        return actual;
    }

    private static Element parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static String text(Element parent, String name) {
        return parent.getElementsByTagName(name).item(0).getTextContent();
    }
}
