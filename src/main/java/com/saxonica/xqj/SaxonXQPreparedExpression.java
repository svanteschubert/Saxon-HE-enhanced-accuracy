////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.om.*;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.query.XQueryExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.z.IntHashSet;
import net.sf.saxon.z.IntIterator;

import javax.xml.namespace.QName;
import javax.xml.xquery.*;
import java.util.HashSet;
import java.util.List;

/**
 * Saxon implementation of the XQJ interface XQPreparedExpression. This represents a compiled XQuery
 * expression, together with the dynamic context for its evaluation. Note that this means the object
 * should not be used in more than one thread concurrently.
 * <p>Note that an expression is scrollable or not depending on the scrollability property of the XQConnection
 * that was used to compile this expression (at the time it was compiled). If the expression is scrollable then
 * its results are delivered in an XQSequence that supports scrolling backwards as well as forwards.</p>
 * <p>For full Javadoc details, see the XQJ interface specification.</p>
 */
public class SaxonXQPreparedExpression extends SaxonXQDynamicContext implements XQPreparedExpression {

    private XQueryExpression expression;
    private SaxonXQStaticContext staticContext;
    private DynamicQueryContext context;
    private boolean scrollable;

    protected SaxonXQPreparedExpression(SaxonXQConnection connection,
                                        XQueryExpression expression,
                                        /*@NotNull*/ SaxonXQStaticContext sqc,
                                        DynamicQueryContext context)
            throws XQException {
        this.connection = connection;
        this.expression = expression;
        this.staticContext = new SaxonXQStaticContext(sqc); // take a snapshot of the supplied static context
        this.context = context;
        scrollable = sqc.getScrollability() == XQConstants.SCROLLTYPE_SCROLLABLE;
        setClosableContainer(connection);
    }

    @Override
    protected DynamicQueryContext getDynamicContext() {
        return context;
    }

    protected SaxonXQConnection getConnection() {
        return connection;
    }

    @Override
    protected SaxonXQDataFactory getDataFactory() throws XQException {
        if (connection.isClosed()) {
            close();
        }
        checkNotClosed();
        return connection;
    }

    protected XQueryExpression getXQueryExpression() {
        return expression;
    }

    protected SaxonXQStaticContext getSaxonXQStaticContext() {
        return staticContext;
    }

    @Override
    public void cancel() throws XQException {
        checkNotClosed();
    }

    /*@NotNull*/
    @Override
    public XQResultSequence executeQuery() throws XQException {
        checkNotClosed();
        try {
            SequenceIterator iter = expression.iterator(context);
            if (scrollable) {
                GroundedValue value = iter.materialize();
                return new SaxonXQSequence(value, this);
            } else {
                return new SaxonXQForwardSequence(new FocusTrackingIterator(iter), this);
            }
        } catch (XPathException de) {
            XQException xqe = new XQException(de.getMessage());
            xqe.initCause(de);
            throw xqe;
        }
    }

    /*@NotNull*/
    @Override
    public QName[] getAllExternalVariables() throws XQException {
        checkNotClosed();
        List<GlobalVariable> vars = expression.getPackageData().getGlobalVariableList();
        if (vars == null || vars.isEmpty()) {
            return EMPTY_QNAME_ARRAY;
        } else {
            HashSet<StructuredQName> params = new HashSet<>(vars.size());
            for (GlobalVariable var : vars) {
                if (var instanceof GlobalParam) {
                    params.add(var.getVariableQName());
                }
            }
            QName[] qnames = new QName[params.size()];
            int q = 0;
            for (StructuredQName name : params) {
                qnames[q++] = new QName(name.getURI(), name.getLocalPart(), name.getPrefix());
            }
            return qnames;
        }
    }

    /*@NotNull*/ private static QName[] EMPTY_QNAME_ARRAY = new QName[0];

    /*@NotNull*/
    @Override
    public QName[] getAllUnboundExternalVariables() throws XQException {
        checkNotClosed();
        java.util.Collection<StructuredQName> boundParameters = getDynamicContext().getParameters().getKeys();
        IntHashSet unbound = new IntHashSet(boundParameters.size());
        QName[] all = getAllExternalVariables();
        for (int i = 0; i < all.length; i++) {
            StructuredQName sq = new StructuredQName("", all[i].getNamespaceURI(), all[i].getLocalPart());
            if (!boundParameters.contains(sq)) {
                unbound.add(i);
            }
        }
        QName[] unboundq = new QName[unbound.size()];
        int c = 0;
        IntIterator iter = unbound.iterator();
        while (iter.hasNext()) {
            int x = iter.next();
            unboundq[c++] = all[x];
        }
        return unboundq;
    }

    @Override
    public XQStaticContext getStaticContext() throws XQException {
        checkNotClosed();
        return staticContext;   // a snapshot of the static context at the time the expression was created
        // old code in Saxon 9.2:
        // return new SaxonXQExpressionContext(expression);
    }

    /*@NotNull*/
    @Override
    public XQSequenceType getStaticResultType() throws XQException {
        checkNotClosed();
        Expression exp = expression.getExpression();    // unwrap two layers!
        ItemType itemType = exp.getItemType();
        int cardinality = exp.getCardinality();
        SequenceType staticType = SequenceType.makeSequenceType(itemType, cardinality);
        return new SaxonXQSequenceType(staticType, connection.getConfiguration());
    }

    /*@NotNull*/
    @Override
    public XQSequenceType getStaticVariableType(QName name) throws XQException {
        checkNotClosed();
        checkNotNull(name);
        StructuredQName qn = new StructuredQName(
                name.getPrefix(), name.getNamespaceURI(), name.getLocalPart());
        for (GlobalVariable var : expression.getPackageData().getGlobalVariableList()) {
            if (var instanceof GlobalParam && var.getVariableQName().equals(qn)) {
                return new SaxonXQSequenceType(var.getRequiredType(), connection.getConfiguration());
            }
        }
        throw new XQException("Variable " + name + " is not declared");
    }


    @Override
    protected boolean externalVariableExists(QName name) {
        StructuredQName qn = new StructuredQName(
                name.getPrefix(), name.getNamespaceURI(), name.getLocalPart());
        for (GlobalVariable var : expression.getPackageData().getGlobalVariableList()) {
            if (var instanceof GlobalParam && var.getVariableQName().equals(qn)) {
                return true;
            }
        }
        return false;
    }

    private void checkNotNull(/*@Nullable*/ Object arg) throws XQException {
        if (arg == null) {
            throw new XQException("Argument is null");
        }
    }

}
