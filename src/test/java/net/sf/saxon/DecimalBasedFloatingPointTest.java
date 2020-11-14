package net.sf.saxon;

import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class DecimalBasedFloatingPointTest {

    private static final String[] MAIN_ARGS = {"-s:src\\test\\resources\\xml\\in.xml","-xsl:src\\test\\resources\\xsl\\test.xsl","-o:target\\generated-sources\\out.xml"};
    
    @Test
    void testPrecision() {
        try{
            Transform.main(MAIN_ARGS);
        }catch(Throwable t){
            Logger.getLogger(getClass().getName()).severe(t.getLocalizedMessage());
            throw t;            
        }
    }
    
    public static void main(String[] args){
        new DecimalBasedFloatingPointTest().testPrecision();
    }
}