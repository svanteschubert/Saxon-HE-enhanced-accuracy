package net.sf.saxon;

import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.xml.transform.TransformerFactory;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class NumericAccuracyTest {
    private Processor processor(boolean optimize) {
        Processor processor = new Processor(false);
        if (!optimize) {
            processor.setConfigurationProperty(Feature.OPTIMIZATION_LEVEL, "0");
        }
        return processor;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void roundingAtCompileTimeAndRuntime(boolean optimize) throws Exception {
        Processor processor = processor(optimize);
        String[][] cases = {
                {"-0.5", "0", "-1"}, {"0.5", "0", "1"},
                {"-0.845", "2", "-0.85"}, {"0.845", "2", "0.85"},
                {"-0.8449", "2", "-0.84"}, {"-0.8451", "2", "-0.85"},
                {"xs:float('-2.5')", "0", "-3"},
                {"xs:double('-2.5')", "0", "-3"},
                {"xs:float('-1.25')", "1", "-1.3"},
                {"xs:double('-1.25')", "1", "-1.3"},
                {"9223372036854775807", "-1", "9223372036854775810"},
                {"-9223372036854775808", "-1", "-9223372036854775810"},
                {"-1500000000000000000000000000000", "-30", "-2000000000000000000000000000000"},
                {"1.25", "999999999999999999999", "1.25"},
                {"1.25", "-999999999999999999999", "0"},
                {"-150", "-2147483648", "0"},
                {"xs:double('NaN')", "2", "NaN"},
                {"xs:double('INF')", "2", "INF"},
                {"xs:float('-INF')", "2", "-INF"},
                {"()", "2", ""}
        };
        XPathCompiler constants = processor.newXPathCompiler();
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareVariable(new QName("v"));
        compiler.declareVariable(new QName("p"));
        for (String[] c : cases) {
            assertEquals(c[2], constants.evaluate("string(round(" + c[0] + ", " + c[1] + "))", null).toString(), c[0]);
            XdmValue value = constants.evaluate(c[0], null);
            XdmValue precision = constants.evaluate(c[1], null);
            for (String expression : new String[]{"round($v, $p)", "round#2($v, $p)",
                    "round-half-away-from-zero($v, $p)",
                    "function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'round'), 2)($v, $p)"}) {
                XPathSelector selector = compiler.compile("string(" + expression + ")").load();
                selector.setVariable(new QName("v"), value);
                selector.setVariable(new QName("p"), precision);
                assertEquals(c[2], selector.evaluate().toString(), expression + " for " + c[0]);
            }
        }
        for (String expression : new String[]{"round($v)", "round#1($v)", "round-half-away-from-zero($v)"}) {
            XPathSelector selector = compiler.compile(expression).load();
            selector.setVariable(new QName("v"), new XdmAtomicValue(new BigDecimal("-2.5")));
            selector.setVariable(new QName("p"), new XdmAtomicValue(0));
            assertEquals("-3", selector.evaluate().toString());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void decimalArithmeticAndTypeBoundaries(boolean optimize) throws Exception {
        XPathCompiler compiler = processor(optimize).newXPathCompiler();
        String[] truths = {
                "0.1 + 0.2 = 0.3",
                "1e-40 * 1e40 = 1",
                "1e-40 div 2 = 5e-41",
                "1e-40 div 3 gt 0",
                "1e0 instance of xs:decimal",
                "sum((0.1, 0.2)) = 0.3",
                "avg((0.1, 0.2)) = 0.15",
                "round((1 div 3) * 100, 2) = 33.33",
                "round(-2.5) = -3",
                "round-half-to-positive-infinity(-2.5) = -2",
                "round-half-to-positive-infinity(-0.845, 2) = -0.84",
                "round-half-to-even(-2.5) = -2",
                "round(xs:float('-2.5')) instance of xs:float",
                "round(xs:double('-2.5')) instance of xs:double",
                "round(-25, -1) instance of xs:integer",
                "1 div round(xs:double('-0.1')) = xs:double('-INF')",
                // Binary types remain binary: callers must cast invoice inputs to xs:decimal.
                "number('0.1') instance of xs:double",
                "xs:double('0.1') + xs:double('0.2') ne xs:double('0.3')",
                "not('1e2' castable as xs:decimal)"
        };
        for (String expression : truths) {
            assertTrue(((XdmAtomicValue) compiler.evaluateSingle(expression, null)).getBooleanValue(), expression);
        }
        assertThrows(SaxonApiException.class, () -> compiler.evaluate("1 div 0", null));
    }

    @Test
    void xqueryUsesTheSameArithmetic() throws Exception {
        XQueryEvaluator query = new Processor(false).newXQueryCompiler().compile(
                "declare variable $v external; (round($v), round($v, 0), 1e-40 * 1e40)").load();
        query.setExternalVariable(new QName("v"), new XdmAtomicValue(new BigDecimal("-2.5")));
        XdmValue values = query.evaluate();
        assertEquals("-3", values.itemAt(0).getStringValue());
        assertEquals("-3", values.itemAt(1).getStringValue());
        assertEquals("1", values.itemAt(2).getStringValue());
    }

    @Test
    void jaxpSelectsThisForkAndReportsUpstreamVersion() {
        assertInstanceOf(TransformerFactoryImpl.class, TransformerFactory.newInstance());
        assertEquals("13.0", Version.getProductVersion());
    }
}
