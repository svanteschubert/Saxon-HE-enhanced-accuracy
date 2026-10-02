package net.sf.saxon;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XPathCompiler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Differential tests for the two accuracy features of this fork.
 *
 * {@link DecimalBasedFloatingPoint} and {@link CommercialRounding} hold the proofs.
 * Six of their eight tests were checked to fail on stock Saxon-HE 12.4 and pass here; the two marked
 * "contrast case" pass on both and are kept as the baseline the others are read
 * against. Each assertion carries the stock result in a comment, so the file doubles
 * as the specification of what this fork changes. Unlike the golden-file comparison in
 * {@link DecimalBasedFloatingPointTest}, a failure names the one expression that
 * regressed instead of a whole-file diff.
 *
 * {@link KnownGaps} records the original audit. The untyped-input, precision-argument
 * rounding and scientific-notation cases now run as regressions. Only the
 * associativity example stays disabled: no finite division precision can guarantee it.
 */
public class AccuracyRegressionTest {

    private static Processor processor;
    private static XPathCompiler xpath;
    private static XdmNode untypedDoc;

    /**
     * Element content is always xs:untypedAtomic when there is no schema - the situation
     * of every Schematron validation, and the case the KnownGaps below are about.
     */
    private static final String DOC
            = "<invoice>"
            + "<line>0.1</line>"
            + "<line>0.2</line>"
            + "<amount>133.70</amount>"
            + "<price>1.005</price>"
            + "</invoice>";

    @BeforeAll
    static void setUp() throws SaxonApiException {
        processor = new Processor(false);
        xpath = processor.newXPathCompiler();
        untypedDoc = processor.newDocumentBuilder()
                .build(new StreamSource(new StringReader(DOC)));
    }

    /**
     * @param expression an XPath expression evaluated without a context item
     * @return its string value
     */
    private static String eval(String expression) throws SaxonApiException {
        return xpath.evaluateSingle(expression, null).getStringValue();
    }

    /**
     * @param expression an XPath expression evaluated against the untyped document
     * @return its string value
     */
    private static String evalOnDoc(String expression) throws SaxonApiException {
        XdmItem result = xpath.evaluateSingle(expression, untypedDoc);
        return result.getStringValue();
    }

    @Nested
    @DisplayName("Feature 1: decimal-based floating-point instead of binary")
    class DecimalBasedFloatingPoint {

        @Test
        @DisplayName("One-third division keeps 34 decimal places, not Saxon's 18")
        void divisionPrecision() throws SaxonApiException {
            // stock Saxon-HE: 0.333333333333333333 (BigDecimalValue.DIVIDE_PRECISION = 18)
            Assertions.assertEquals("0.3333333333333333333333333333333333", eval("1 div 3"));
            Assertions.assertEquals("0.3333333333333333333333333333333333", eval("1.0 div 3"));
        }

        @Test
        @DisplayName("One-third division has 34 fractional digits (not a decimal128 implementation)")
        void oneThirdDivisionScale() throws SaxonApiException {
            // stock Saxon-HE: 18
            Assertions.assertEquals("34", eval("string-length(substring-after(string(1 div 3), '.'))"));
        }

        @Test
        @DisplayName("README invoice example: quantity * (price div baseQuantity)")
        void readmeInvoiceExample() throws SaxonApiException {
            // stock Saxon-HE: 333333333.333333333 - the error is already visible at 1e-9,
            // which is Cent level once quantities are large, as in energy and pharma.
            Assertions.assertEquals("333333333.3333333333333333333333333",
                    eval("1000000000.0 * (1.0 div 3)"));
            // stock Saxon-HE: 333333333.333333333333333333
            Assertions.assertEquals("333333333.3333333333333333333333333333333333",
                    eval("1000000000.0 * 1.0 div 3"));
        }

        /** Contrast case: passes on stock Saxon too. */
        @Test
        @DisplayName("A decimal literal sum is exact where binary floating-point is not")
        void decimalLiteralsAreExact() throws SaxonApiException {
            // Holds in stock Saxon too - literals with a '.' are xs:decimal by the XPath
            // grammar. Recorded as the contrast case for KnownGaps.untypedArithmetic.
            Assertions.assertEquals("true", eval("0.1 + 0.2 = 0.3"));
        }
    }

    @Nested
    @DisplayName("Feature 2: half-away-from-zero rounding (kaufmaennisches Runden)")
    class CommercialRounding {

        @Test
        @DisplayName("round() rounds ties away from zero")
        void roundGoesAwayFromZero() throws SaxonApiException {
            Assertions.assertEquals("-2", eval("round(-1.5)"));   // stock Saxon-HE: -1
            Assertions.assertEquals("-1", eval("round(-0.5)"));   // stock Saxon-HE: 0
            Assertions.assertEquals("-3", eval("round(-2.5)"));   // stock Saxon-HE: -2
        }

        /** Contrast case: passes on stock Saxon too. */
        @Test
        @DisplayName("Positive halves are unaffected - both roundings agree there")
        void positiveHalvesUnchanged() throws SaxonApiException {
            Assertions.assertEquals("2", eval("round(1.5)"));
            Assertions.assertEquals("3", eval("round(2.5)"));
        }

        @Test
        @DisplayName("format-number() rounds ties away from zero, not half-to-even")
        void formatNumberGoesAwayFromZero() throws SaxonApiException {
            Assertions.assertEquals("0.29", eval("format-number(0.285, '0.00')"));    // stock Saxon-HE: 0.28
            Assertions.assertEquals("-0.29", eval("format-number(-0.285, '0.00')"));  // stock Saxon-HE: -0.28
            Assertions.assertEquals("3", eval("format-number(2.5, '0')"));            // stock Saxon-HE: 2
            Assertions.assertEquals("0.29", eval("format-number(0.285e0, '0.00')"));  // stock Saxon-HE: 0.28
            Assertions.assertEquals("3", eval("format-number(xs:float(2.5), '0')"));  // stock Saxon-HE: 2
            Assertions.assertEquals("1.3e0", eval("format-number(1.25, '0.0e0')"));   // stock Saxon-HE: 1.2e0
        }

        @Test
        @DisplayName("round-half-away-from-zero() is available explicitly, with a scale")
        void explicitFunction() throws SaxonApiException {
            // stock Saxon-HE: XPST0017, no such function
            Assertions.assertEquals("-2", eval("round-half-away-from-zero(-1.5)"));
            Assertions.assertEquals("-2.35", eval("round-half-away-from-zero(-2.345, 2)"));
            Assertions.assertEquals("2.35", eval("round-half-away-from-zero(2.345, 2)"));
        }

        @Test
        @DisplayName("The W3C-conformant rounding stays reachable under its own name")
        void conformantRoundingStillReachable() throws SaxonApiException {
            // stock Saxon-HE: XPST0017. This is the escape hatch for anyone who needs
            // the behaviour that fn:round() had before this fork replaced it.
            Assertions.assertEquals("-1", eval("round-half-to-positive-infinity(-1.5)"));
            Assertions.assertEquals("0", eval("round-half-to-positive-infinity(-0.5)"));
        }
    }

    @Nested
    @DisplayName("Accuracy audit - resolved regressions and remaining numeric-policy gaps")
    class KnownGaps {

        @Test
        @DisplayName("Resolved GAP 1: sum() over untyped element content must not use xs:double")
        void untypedSum() throws SaxonApiException {
            // Previously (and on stock Saxon): xs:double, 0.30000000000000004.
            // F&O 15.4.5 prescribes the cast to xs:double, so this is a deliberate
            // deviation - the same kind as replacing fn:round().
            Assertions.assertEquals("true", evalOnDoc("sum(/invoice/line) instance of xs:decimal"));
            Assertions.assertEquals("0.3", evalOnDoc("string(sum(/invoice/line))"));
            Assertions.assertEquals("0.15", evalOnDoc("string(avg(/invoice/line))"));
            Assertions.assertEquals("true", evalOnDoc("max(/invoice/line) instance of xs:decimal"));
        }

        @Test
        @DisplayName("Resolved GAP 2: arithmetic on untyped element content must not use xs:double")
        void untypedArithmetic() throws SaxonApiException {
            // Previously: ArithmeticExpression inserted an UntypedSequenceConverter to
            // BuiltInAtomicType.DOUBLE, so this was 0.30000000000000004 and compared false.
            Assertions.assertEquals("true", evalOnDoc("/invoice/line[1] + /invoice/line[2] = 0.3"));
            // Previously: 13369.999999999998
            Assertions.assertEquals("13370", evalOnDoc("string(/invoice/amount * 100)"));
            // Previously: 1, because xs:double stores 1.005 as 1.00499999999999989...
            Assertions.assertEquals("1.01", evalOnDoc("string(round(/invoice/price, 2))"));
        }

        @Test
        @DisplayName("Resolved GAP 3: comparing untyped content with a number must not use xs:double")
        void untypedComparison() throws SaxonApiException {
            // Previously: UntypedNumericComparer parsed the content to double and used
            // Double.compare, so the boundary case below was decided in binary.
            Assertions.assertEquals("true", evalOnDoc("/invoice/price = 1.005"));
            Assertions.assertEquals("true",
                    evalOnDoc("round(/invoice/price * 100) div 100 = 1.01"));
        }

        @Test
        @DisplayName("Untyped input that is not a decimal keeps its xs:double semantics")
        void untypedSpecialValuesStayDouble() throws SaxonApiException {
            Assertions.assertEquals("NaN", eval("string(xs:untypedAtomic('NaN') + 1)"));
            Assertions.assertEquals("-INF", eval("string(xs:untypedAtomic('-INF') + 1)"));
            // Beyond the exponent bound, the value is converted as xs:double, as on stock Saxon
            Assertions.assertEquals("INF", eval("string(xs:untypedAtomic('1e999') + 0)"));
            Assertions.assertEquals("1500", eval("string(xs:untypedAtomic('1.5E3') + 0)"));
            // An explicit xs:double operand still makes the operation binary
            Assertions.assertEquals("true",
                    eval("(xs:untypedAtomic('0.1') + xs:double('0.2')) instance of xs:double"));
            SaxonApiException invalid = Assertions.assertThrows(SaxonApiException.class,
                    () -> eval("xs:untypedAtomic('abc') + 1"));
            Assertions.assertEquals("FORG0001", invalid.getErrorCode().getLocalName());
            // Decimal division by zero is an error, as for decimal literals (stock: INF)
            SaxonApiException zero = Assertions.assertThrows(SaxonApiException.class,
                    () -> eval("xs:untypedAtomic('1') div xs:untypedAtomic('0')"));
            Assertions.assertEquals("FOAR0001", zero.getErrorCode().getLocalName());
        }

        @Test
        @DisplayName("Resolved GAP 4: round() with a precision argument must round away from zero too")
        void roundWithPrecisionIsInconsistent() throws SaxonApiException {
            // Previously: round(-1.5) is -2 but round(-1.5, 0) is -1, because only the
            // 1-argument form was re-registered in XPath20FunctionSet; the 2-argument
            // form is registered separately in XPath30FunctionSet and still uses Round.
            Assertions.assertEquals("-2", eval("round(-1.5, 0)"));
            Assertions.assertEquals("-2.35", eval("round(-2.345, 2)"));
        }

        @Test
        @DisplayName("Resolved GAP 5: exponent notation must stay compilable")
        void exponentLiteralsMustCompile() throws SaxonApiException {
            // Previously: XPST0003 "Invalid numeric literal", because NumericValue.parseNumber
            // routes the exponent form into BigDecimalValue.makeDecimalValue, whose XSD
            // decimal lexical rules forbid an exponent. Any stylesheet using scientific
            // notation fails to compile - valid XPath 2.0/3.1 that stock Saxon accepts.
            // Intended: accept the lexical form and give it decimal semantics.
            Assertions.assertEquals("100", eval("string(1.0e2)"));
            Assertions.assertEquals("0.3", eval("string(0.1e0 + 0.2e0)"));
        }

        @Test
        @Disabled("Exact multiplication does not undo rounding in a nonterminating quotient")
        @DisplayName("GAP 6: the README's own example is still not satisfied")
        void readmeGoalNotReached() throws SaxonApiException {
            // The README states both bracketings should give the same result. They still
            // differ - stock at the 9th decimal, the fork at the 25th. A shared
            // significant-digit context could make this example agree, but cannot
            // guarantee associativity in general. This fork preserves exact products
            // and only approximates nonterminating quotients.
            Assertions.assertEquals("true",
                    eval("(1000000000.0 * (1.0 div 3)) = (1000000000.0 * 1.0 div 3)"));
        }
    }
}
