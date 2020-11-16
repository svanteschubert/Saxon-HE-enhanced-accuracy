package net.sf.saxon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class DecimalBasedFloatingPointTest {

    private static final String[] MAIN_ARGS = {"-s:src\\test\\resources\\xml\\in.xml","-xsl:src\\test\\resources\\xsl\\test.xsl","-o:target\\generated-sources\\out.xml"};
    
    @Test
    void testPrecision() {
        try{
            Transform.main(MAIN_ARGS);
            System.out.println("\n");
            System.out.println("Rounding half-even 123456789.987654321 with scale  2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, 2, RoundingMode.HALF_EVEN));
            System.out.println("Rounding half-even 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN));
            System.out.println("Rounding half-even 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-even 123456789 with scale -2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-even 123456789 with scale -2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, 2, RoundingMode.HALF_EVEN).toPlainString());
        }catch(Throwable t){
            Logger.getLogger(getClass().getName()).severe(t.getLocalizedMessage());
            throw t;            
        }
    }
    
    public static void main(String[] args){
        new DecimalBasedFloatingPointTest().testPrecision();
    }
}