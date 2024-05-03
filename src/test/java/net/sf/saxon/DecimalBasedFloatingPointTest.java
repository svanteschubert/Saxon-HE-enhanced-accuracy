package net.sf.saxon;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Level;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DecimalBasedFloatingPointTest {

    // BASIC PROJECT DIRECTORIES
    static final String PROJECT_DIR = System.getProperty("saxon.base.dir") + File.separator;

    /**
     * TARGET_DIR: target/ - build output directory
     */
    private static final String TARGET_DIR = PROJECT_DIR + "target" + File.separator;

    /**
     * RESOURCES_DIR: src/test/resources/ - test resources directory
     */
    private static final String RESOURCES_DIR = PROJECT_DIR + "src" + File.separator
            + "test" + File.separator
            + "resources" + File.separator;

    /**
     * REFERENCES_DIR: src/test/resources/references - test references input
     * directory
     */
    private static final String REFERENCES_DIR = RESOURCES_DIR + "references" + File.separator;

    /**
     * XSD_DIR: src/test/resources/xsd - test xsd input directory
     */
    private static final String XSL_DIR = RESOURCES_DIR + "xsl" + File.separator;

    /**
     * XSD_DIR: src/test/resources/xsd - test xsd input directory
     */
    private static final String XML_DIR = RESOURCES_DIR + "xml" + File.separator;

    private static final String[] MAIN_ARGS = {"-s:" + XML_DIR + "in.xml",
        "-xsl:" + XSL_DIR + "test.xsl",
        "-o:" + TARGET_DIR + "generated-sources"
        + File.separator + "out.xml",
        "-quit:off"};

    @Test
    @DisplayName("XSL Rounding test")
    void testRounding() {

        try {
            System.out.println("Working Directory = " + System.getProperty("user.dir"));

            String reportFileName = "xsl-rounding-test.txt";

            // Create a stream to hold the output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            // IMPORTANT: Save the old System.out!
            PrintStream old = System.err;
            // Tell Java to use your special stream
            System.setErr(ps);

            Transform.main(MAIN_ARGS);

            // Put things back
            System.err.flush();
            System.setErr(old);
            // Show what happened
            String result = baos.toString();
            System.out.println(result);
            // if you change the programming, update the reference by copying new result as new reference!
            Files.writeString(Paths.get(new File(TARGET_DIR + reportFileName).toURI()), result, Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            String resultReloaded = Files.readString(Paths.get(new File(TARGET_DIR + reportFileName).toURI()), Charset.forName("UTF-8"));
            File refFile = new File(REFERENCES_DIR + reportFileName);
            if (refFile.exists()) {
                String referenceResult = Files.readString(Paths.get(refFile.toURI()), Charset.forName("UTF-8"));
                if (!resultReloaded.equals(referenceResult)) {
                    compareTextFiles(TARGET_DIR + reportFileName, REFERENCES_DIR + reportFileName);
                    Assertions.fail("\nRegression test fails as reference was different!\nNote: If the test fails due to a new output (e.g. programming update) copy the new result over the old reference:\n\t" + TARGET_DIR + reportFileName + "\n\t\tto" + "\n\t" + REFERENCES_DIR + reportFileName);                    
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(DecimalBasedFloatingPointTest.class.getName()).log(Level.SEVERE, null, ex);
        }

    }


    @Test
    @DisplayName("Decimal-based floating-point test")
    void testPrecision() {
        try {
            System.out.println("Working Directory = " + System.getProperty("user.dir"));

            String reportFileName = "decimal-based-floating-point-test.txt";

            // Create a stream to hold the output
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            // IMPORTANT: Save the old System.out!
            PrintStream old = System.out;
            // Tell Java to use your special stream
            System.setOut(ps);
            System.out.println("\n");
            System.out.println("Rounding half-even 123456789.987654321 with scale  2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, 2, RoundingMode.HALF_EVEN));
            System.out.println("Rounding half-even 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN));
            System.out.println("Rounding half-even 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-even 123456789 with scale -2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, -2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-even 123456789 with scale  2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, 2, RoundingMode.HALF_EVEN).toPlainString());
            System.out.println("Rounding half-up 123456789.987654321 with scale  2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, 2, RoundingMode.HALF_UP));
            System.out.println("Rounding half-up 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_UP));
            System.out.println("Rounding half-up 123456789.987654321 with scale -2 to " + new BigDecimal("123456789.987654321").divide(BigDecimal.ONE, -2, RoundingMode.HALF_UP).toPlainString());
            System.out.println("Rounding half-up 123456789 with scale -2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, -2, RoundingMode.HALF_UP).toPlainString());
            System.out.println("Rounding half-up 123456789 with scale  2 to " + new BigDecimal("123456789").divide(BigDecimal.ONE, 2, RoundingMode.HALF_UP).toPlainString());
            System.out.println("(1000000000.0 *(1.0 div 3 )) " + new BigDecimal("1.0").divide(new BigDecimal("3"), 34, RoundingMode.HALF_UP).multiply(new BigDecimal("1000000000.0")).toPlainString());
            System.out.println("(1000000000.0 * 1.0 div 3 )  " + new BigDecimal("1000000000.0").multiply(BigDecimal.ONE).divide(new BigDecimal("3"), 34, RoundingMode.HALF_UP).toPlainString());

            // Put things back
            System.out.flush();
            System.setOut(old);
            // Show what happened
            String result = baos.toString();
            System.out.println(result);
            // if you change the programming, update the reference by copying new result as new reference!
            Files.writeString(Paths.get(new File(TARGET_DIR + reportFileName).toURI()), result, Charset.forName("UTF-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            String resultReloaded = Files.readString(Paths.get(new File(TARGET_DIR + reportFileName).toURI()), Charset.forName("UTF-8"));
            File refFile = new File(REFERENCES_DIR + reportFileName);
            if (refFile.exists()) {
                String referenceResult = Files.readString(Paths.get(refFile.toURI()), Charset.forName("UTF-8"));
                if (!resultReloaded.equals(referenceResult)) {
                    compareTextFiles(TARGET_DIR + reportFileName, REFERENCES_DIR + reportFileName);                    
                    Assertions.fail("\nRegression test fails as reference was different!\nNote: If the test fails due to a new output (e.g. programming update) copy the new result over the old reference:\n\t" + TARGET_DIR + reportFileName + "\n\t\tto" + "\n\t" + REFERENCES_DIR + reportFileName);
                }
            }

        } catch (Throwable t) {
            Logger.getLogger(getClass().getName()).severe(t.getLocalizedMessage());
            Assertions.fail(t);
        }
    }

    public static void main(String[] args) {
        new DecimalBasedFloatingPointTest().testRounding();
        new DecimalBasedFloatingPointTest().testPrecision();        
    }
    

    /**
     * Just for showing the different lines, in case the test and reference
     * files as strings are unequal!
     */
    private static void compareTextFiles(String firstFilePath, String secondFilePath) {
        try (
                BufferedReader bfr1 = Files.newBufferedReader(Paths.get(firstFilePath)); BufferedReader bfr2 = Files.newBufferedReader(Paths.get(secondFilePath));) {
            String line1;
            String line2;
            // read the first file and store it in an ArrayList
            while ((line1 = bfr1.readLine()) != null) {
                line2 = bfr2.readLine();
                if (!line1.equals(line2)) {
                    System.err.println("### Unmatched Line1: '" + line1 + "'\n");
                    System.err.println("### Unmatched Line2: '" + line2 + "'\n\n");
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage() + e.toString());
        }
    }    
}
