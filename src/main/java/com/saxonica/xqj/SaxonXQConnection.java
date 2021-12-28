////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.StaticQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trans.XPathException;

import javax.xml.xquery.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

/**
 * Saxon implementation of the XQL interface XQConnection. This interface represents a
 * "connection" between an XQuery application and an XQuery server. In Saxon the client
 * and server run in the same process so the concept of a connection is rather notional,
 * and some of the properties have little meaning. However, the connection is the factory
 * object used to compile queries.
 * <p>For Javadoc descriptions of the public methods, see the XQJ documentation.</p>
 */
public class SaxonXQConnection extends SaxonXQDataFactory implements XQConnection {

    private Configuration config;
    private SaxonXQStaticContext staticContext;

    /**
     * Create an SaxonXQConnection from a SaxonXQDataSource
     *
     * @param dataSource the data source.
     */
    SaxonXQConnection(SaxonXQDataSource dataSource) {
        config = dataSource.getConfiguration();
        staticContext = new SaxonXQStaticContext(config);
        init();
    }

    /**
     * Get the Saxon Configuration in use. Changes made to this Configuration will affect this
     * data source and XQJ connections created from it (either before or afterwards).
     *
     * @return the configuration in use.
     */

    @Override
    public Configuration getConfiguration() {
        return config;
    }

    @Override
    public void commit() throws XQException {
        checkNotClosed();
    }

    @Override
    public XQExpression createExpression() throws XQException {
        checkNotClosed();
        return new SaxonXQExpression(this);
    }

    @Override
    public XQExpression createExpression(XQStaticContext properties) throws XQException {
        checkNotClosed();
        SaxonXQDataSource.checkNotNull(properties, "properties");
        return new SaxonXQExpression(this, (SaxonXQStaticContext) properties);
    }


    @Override
    public boolean getAutoCommit() {
        return false;
    }

    @Override
    public XQMetaData getMetaData() throws XQException {
        checkNotClosed();
        return new SaxonXQMetaData(this);
    }


    @Override
    public XQStaticContext getStaticContext() throws XQException {
        checkNotClosed();
        return new SaxonXQStaticContext(staticContext);
    }

    @Override
    public XQPreparedExpression prepareExpression(InputStream xquery) throws XQException {
        return prepareExpression(xquery, staticContext);
    }

    @Override
    public XQPreparedExpression prepareExpression(InputStream xquery, XQStaticContext properties) throws XQException {
        checkNotClosed();
        SaxonXQDataSource.checkNotNull(xquery, "xquery");
        SaxonXQDataSource.checkNotNull(properties, "properties");
        try {
            SaxonXQStaticContext xqStaticContext = (SaxonXQStaticContext) properties;
            StaticQueryContext sqc = xqStaticContext.getSaxonStaticQueryContext();
            XQueryExpression exp = sqc.compileQuery(xquery, null);
            DynamicQueryContext dqc = new DynamicQueryContext(config);
            dqc.setApplyFunctionConversionRulesToExternalVariables(false);
            return new SaxonXQPreparedExpression(this, exp, xqStaticContext, dqc);
        } catch (XPathException | NullPointerException e) {
            throw newXQException(e);
        }
    }

    @Override
    public XQPreparedExpression prepareExpression(Reader xquery) throws XQException {
        return prepareExpression(xquery, staticContext);
    }

    @Override
    public XQPreparedExpression prepareExpression(Reader xquery, XQStaticContext properties) throws XQException {
        checkNotClosed();
        SaxonXQDataSource.checkNotNull(xquery, "xquery");
        SaxonXQDataSource.checkNotNull(properties, "properties");
        try {
            SaxonXQStaticContext xqStaticContext = (SaxonXQStaticContext) properties;
            StaticQueryContext sqc = xqStaticContext.getSaxonStaticQueryContext();
            XQueryExpression exp = sqc.compileQuery(xquery);
            DynamicQueryContext dqc = new DynamicQueryContext(config);
            dqc.setApplyFunctionConversionRulesToExternalVariables(false);
            return new SaxonXQPreparedExpression(this, exp, xqStaticContext, dqc);
        } catch (XPathException | IOException | NullPointerException e) {
            throw newXQException(e);
        }
    }

    @Override
    public XQPreparedExpression prepareExpression(String xquery) throws XQException {
        return prepareExpression(xquery, staticContext);
    }

    @Override
    public XQPreparedExpression prepareExpression(String xquery, XQStaticContext properties) throws XQException {
        SaxonXQDataSource.checkNotNull(xquery, "xquery");
        return prepareExpression(new StringReader(xquery), properties);
    }

    /**
     * Copy a prepared expression to create a new prepared expression. The prepared expression to be copied
     * may belong to a different connection. This method (which is a Saxon extension to the XQJ interface) allows
     * a query to be compiled once, and reused concurrently under multiple connections in multiple threads. The
     * compiled code of the existing query and its static context are shared with the original query, but a new
     * dynamic context is established, so that the two expressions can safely be used in parallel.
     *
     * @param expression the XQPreparedExpression to be copied. This must have been created using Saxon, and it
     *                   must have been created with an XQConnection derived from the same XQDataSource as this connection.
     * @return a copy of the supplied expression, that can be used in a different connection or thread with its
     *         own dynamic context. The new copy of the expression belongs to this connection, and can be used in the same
     *         way as an expression created using any of the prepareExpression() methods on this class.
     * @throws XQException, for example if either of the connections has been closed
     */

    public XQPreparedExpression copyPreparedExpression(XQPreparedExpression expression) throws XQException {
        checkNotClosed();
        if (!(expression instanceof SaxonXQPreparedExpression)) {
            throw new IllegalArgumentException("Supplied expression must be compiled using Saxon");
        }
        XQueryExpression xqe = ((SaxonXQPreparedExpression) expression).getXQueryExpression();
        if (xqe.getExecutable().getConfiguration() != config) {
            throw new IllegalArgumentException("Supplied expression must derive from the same XQDataSource");
        }
        SaxonXQStaticContext sqc = ((SaxonXQPreparedExpression) expression).getSaxonXQStaticContext();
        DynamicQueryContext dqc = new DynamicQueryContext(config);
        return new SaxonXQPreparedExpression(this, xqe, sqc, dqc);
    }

    @Override
    public void rollback() throws XQException {
        checkNotClosed();
        // no-op
    }


    @Override
    public void setAutoCommit(boolean autoCommit) throws XQException {
        checkNotClosed();
        // no-op
    }

    @Override
    public void setStaticContext(XQStaticContext properties) throws XQException {
        checkNotClosed();
        SaxonXQDataSource.checkNotNull(properties, "properties");
        staticContext = new SaxonXQStaticContext((SaxonXQStaticContext) properties);
    }

    /*@NotNull*/
    private XQException newXQException(Exception err) {
        XQException xqe = new XQException(err.getMessage());
        xqe.initCause(err);
        return xqe;
    }
}

