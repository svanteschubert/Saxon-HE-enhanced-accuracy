////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;

import javax.xml.xquery.XQException;
import javax.xml.xquery.XQMetaData;
import java.util.Set;

/**
 * Saxon implementation of the XQMetaData interface
 */
public class SaxonXQMetaData implements XQMetaData {

    private SaxonXQConnection connection;

    /**
     * Create the metadata for a given Saxon configuration
     *
     * @param connection the Saxon connection
     */

    public SaxonXQMetaData(SaxonXQConnection connection) {
        this.connection = connection;
    }

    @Override
    public int getMaxExpressionLength() throws XQException {
        checkNotClosed();
        checkNotClosed();
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaxUserNameLength() throws XQException {
        checkNotClosed();
        return Integer.MAX_VALUE;
    }

    @Override
    public int getProductMajorVersion() throws XQException {
        checkNotClosed();
        return Version.getStructuredVersionNumber()[0];
    }

    @Override
    public int getProductMinorVersion() throws XQException {
        checkNotClosed();
        return Version.getStructuredVersionNumber()[1];
    }

    /*@NotNull*/
    @Override
    public String getProductName() throws XQException {
        checkNotClosed();
        return Version.getProductName();
    }

    /*@NotNull*/
    @Override
    public String getProductVersion() throws XQException {
        checkNotClosed();
        return Version.getProductVersion();
    }

    @Override
    public Set getSupportedXQueryEncodings() throws XQException {
        checkNotClosed();
        return java.nio.charset.Charset.availableCharsets().keySet();
    }

    /*@Nullable*/
    @Override
    public String getUserName() throws XQException {
        checkNotClosed();
        return null;
    }

    @Override
    public int getXQJMajorVersion() throws XQException {
        checkNotClosed();
        return 1;
    }

    @Override
    public int getXQJMinorVersion() throws XQException {
        checkNotClosed();
        return 0;
    }

    /*@NotNull*/
    @Override
    public String getXQJVersion() throws XQException {
        checkNotClosed();
        return "1.0";
    }

    @Override
    public boolean isFullAxisFeatureSupported() throws XQException {
        checkNotClosed();
        return true;
    }

    @Override
    public boolean isModuleFeatureSupported() throws XQException {
        checkNotClosed();
        return true;
    }

    @Override
    public boolean isReadOnly() throws XQException {
        checkNotClosed();
        return true;
    }

    @Override
    public boolean isSchemaImportFeatureSupported() throws XQException {
        checkNotClosed();
        return connection.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY);
    }

    @Override
    public boolean isSchemaValidationFeatureSupported() throws XQException {
        checkNotClosed();
        return connection.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY);
    }

    @Override
    public boolean isSerializationFeatureSupported() throws XQException {
        checkNotClosed();
        return true;
    }

    @Override
    public boolean isStaticTypingExtensionsSupported() throws XQException {
        checkNotClosed();
        return false;
    }

    @Override
    public boolean isStaticTypingFeatureSupported() throws XQException {
        checkNotClosed();
        return false;
    }

    @Override
    public boolean isTransactionSupported() throws XQException {
        checkNotClosed();
        return false;
    }

    @Override
    public boolean isUserDefinedXMLSchemaTypeSupported() throws XQException {
        checkNotClosed();
        return connection.getConfiguration().isLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY);
    }

    @Override
    public boolean isXQueryEncodingDeclSupported() throws XQException {
        checkNotClosed();
        return true;
    }

    @Override
    public boolean isXQueryEncodingSupported(String encoding) throws XQException {
        checkNotClosed();
        return getSupportedXQueryEncodings().contains(encoding);
    }

    @Override
    public boolean isXQueryXSupported() throws XQException {
        checkNotClosed();
        return false;
    }

    @Override
    public boolean wasCreatedFromJDBCConnection() throws XQException {
        checkNotClosed();
        return false;
    }

    private void checkNotClosed() throws XQException {
        connection.checkNotClosed();
    }
}

