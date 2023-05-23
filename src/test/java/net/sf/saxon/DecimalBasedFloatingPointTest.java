package net.sf.saxon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;
import java.io.File;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DecimalBasedFloatingPointTest {

    private static final String[] MAIN_ARGS = {"-s:src" + File.separator +
                                                 "test" + File.separator +
                                                 "resources" + File.separator +
                                                 "xml" + File.separator +
                                                 "in.xml",
                                               "-xsl:src" + File.separator +
                                               "test" + File.separator +
                                               "resources" + File.separator +
                                               "xsl" + File.separator +
                                               "test.xsl",
                                               "-o:target" + File.separator +
                                               "generated-sources" + File.separator +
                                               "out.xml"};

    @Test
    @DisplayName("Decimal-based floating-point test")
    void testPrecision() {
        try{
            System.out.println("Working Directory = " + System.getProperty("user.dir"));
            Transform.main(MAIN_ARGS);
            System.out.println("\n");
            System.out.println("Rounding half-even 123456789.987654321 with scale  2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, 2, RoundingMode.HALF_EVEN));
            System.out.println("Rounding half-even 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN));
            System.out.println("Rounding half-even 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-even 123456789 with scale -2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-even 123456789 with scale  2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE,  2, RoundingMode.HALF_EVEN).toPlainString());
        }catch(Throwable t){
            Logger.getLogger(getClass().getName()).severe(t.getLocalizedMessage());
            throw t;
        }
    }

    public static void main(String[] args){
        new DecimalBasedFloatingPointTest().testPrecision();
    }
}
