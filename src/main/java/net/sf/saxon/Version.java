////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;


/**
 * The Version class holds the SAXON version information.
 */

public final class Version {

    private static final int MAJOR_VERSION = 10;
    private static final int MINOR_VERSION = 8;
    private static final int BUILD = 31510; //mddhh
    private static final String MAJOR_RELEASE_DATE = "2020-03-16";
    private static final String MINOR_RELEASE_DATE = "2022-03-15";


    private Version() {
        // class is never instantiated
    }

    /**
     * Return the name of this product. Supports the XSLT 2.0 system property xsl:product-name
     *
     * @return the string "SAXON"
     */


    public static String getProductName() {
        return "SAXON";
    }

    /**
     * Return the name of the product vendor.
     *
     * @return the string "Saxonica"
     */

    public static String getProductVendor() {
        return "Saxonica";
    }

    /**
     * Get the version number of the schema-aware version of the product
     *
     * @param edition the Saxon edition code, e.g. "EE" or "JS"
     * @return the version number of this version of Saxon, as a string
     */

    public static String getProductVariantAndVersion(String edition) {
        return edition + " " + getProductVersion();
    }

    /**
     * Get the user-visible version number of this version of the product
     *
     * @return the version number of this version of Saxon, as a string: for example "10.1"
     */

    public static String getProductVersion() {
        return MAJOR_VERSION + "." + MINOR_VERSION;
    }

    /**
     * Get the components of the structured version number. This is used in the .NET product
     * to locate an assembly in the dynamic assembly cache; it is also used by XQJ. The caller can assume
     * that there will always be at least two components. The current implementation in Saxon 10
     * returns [10, n, b] where n is the minor version number (initially 0, incremented for each
     * maintenance release), and b is the build number, which is typically a six-digit number.
     *
     * @return the three components of the version number, as an array: for example {9, 0, 1, 1}
     */

    public static int[] getStructuredVersionNumber() {
        return new int[]{MAJOR_VERSION, MINOR_VERSION, BUILD};
    }

    /**
     * Get the issue date of this version of the product. This will be the release date of the
     * latest maintenance release
     *
     * @return the release date, as an ISO 8601 string
     */

    public static String getReleaseDate() {
        return MINOR_RELEASE_DATE;
    }

    /**
     * Get the issue date of the most recent major release of the product, that is, a release offering
     * new functionality rather than just bug fixes (typically, a release in which the first two digits
     * of the version number change, for example 9.2 to 9.3).
     *
     * @return the release date, as an ISO 8601 string
     */

    public static String getMajorReleaseDate() {
        return MAJOR_RELEASE_DATE;
    }

    /**
     * Get a message used to identify this product when a transformation is run using the -t option
     *
     * @return A string containing both the product name and the product
     *         version
     */

    public static String getProductTitle() {
        return getProductName() + '-' + softwareEdition + ' ' +
            getProductVersion() + (platform.isJava() ? 'J' : 'N') + " from Saxonica";
    }

    /**
     * Return a web site address containing information about the product. Supports the XSLT system property xsl:vendor-url
     *
     * @return the string "http://saxon.sf.net/"
     */

    public static String getWebSiteAddress() {
        return "http://www.saxonica.com/";
    }

    /**
     * Invoking net.sf.saxon.Version from the command line outputs the build number
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.err.println(getProductTitle() + " (build " + BUILD + ')');
    }


    public static Class<? extends Configuration> configurationClass;


    public static String softwareEdition;

    static {
        softwareEdition = "HE";
    }

    public static Platform platform;

    static {
              platform = new net.sf.saxon.java.JavaPlatform();
    }


}

