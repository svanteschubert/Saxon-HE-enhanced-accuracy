package net.sf.saxon;

import org.junit.jupiter.api.Test;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class SchematronAccuracyTest {
    @Test
    void compileUnmodifiedSchematronAndValidatePositiveAndNegativeVat() throws Exception {
        TransformerFactory factory = TransformerFactory.newInstance();
        URL compiler = getClass().getResource("/xslt/2.0/pipeline-for-svrl.xsl");
        assertNotNull(compiler);
        StringWriter generated = new StringWriter();
        factory.newTransformer(new StreamSource(compiler.toExternalForm())).transform(
                new StreamSource(getClass().getResource("/schematron/invoice.sch").toExternalForm()),
                new StreamResult(generated));
        var validator = factory.newTemplates(new StreamSource(new StringReader(generated.toString())));
        for (String sign : new String[]{"", "-"}) {
            for (String vat : new String[]{"0.85", "0.84"}) {
                String xml = """
                        <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                          xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                          xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                          <cac:TaxTotal><cac:TaxSubtotal>
                            <cbc:TaxableAmount>%s4.225</cbc:TaxableAmount>
                            <cbc:TaxAmount>%s%s</cbc:TaxAmount>
                            <cac:TaxCategory><cbc:Percent>20</cbc:Percent></cac:TaxCategory>
                          </cac:TaxSubtotal></cac:TaxTotal>
                          <cac:InvoiceLine><cbc:LineExtensionAmount>0.1</cbc:LineExtensionAmount></cac:InvoiceLine>
                          <cac:InvoiceLine><cbc:LineExtensionAmount>0.2</cbc:LineExtensionAmount></cac:InvoiceLine>
                          <cac:LegalMonetaryTotal><cbc:LineExtensionAmount>0.3</cbc:LineExtensionAmount></cac:LegalMonetaryTotal>
                        </Invoice>
                        """.formatted(sign, sign, vat);
                StringWriter result = new StringWriter();
                validator.newTransformer().transform(new StreamSource(new StringReader(xml)), new StreamResult(result));
                DocumentBuilderFactory dom = DocumentBuilderFactory.newInstance();
                dom.setNamespaceAware(true);
                var report = dom.newDocumentBuilder().parse(new InputSource(new StringReader(result.toString())));
                var failures = report.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl", "failed-assert");
                assertEquals(vat.equals("0.85") ? 0 : 2, failures.getLength(), sign + vat + ": " + result);
                if (failures.getLength() > 0) {
                    assertEquals("vat-one-argument", failures.item(0).getAttributes().getNamedItem("id").getNodeValue());
                    assertEquals("vat-two-arguments", failures.item(1).getAttributes().getNamedItem("id").getNodeValue());
                }
            }
        }
    }
}
