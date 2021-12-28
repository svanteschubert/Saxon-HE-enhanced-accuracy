////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.*;
import net.sf.saxon.query.DynamicQueryContext;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.*;
import org.w3c.dom.Node;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.xquery.*;
import java.io.InputStream;
import java.io.Reader;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Saxon implementation of the XQJ DynamicContext interface
 */

public abstract class SaxonXQDynamicContext extends Closable implements XQDynamicContext {

    protected SaxonXQConnection connection;

    protected abstract DynamicQueryContext getDynamicContext();

    protected final Configuration getConfiguration() {
        return connection.getConfiguration();
    }

    protected abstract SaxonXQDataFactory getDataFactory() throws XQException;

    protected abstract boolean externalVariableExists(QName name);

    /*@Nullable*/ private TimeZone implicitTimeZone = null;

    @Override
    public void bindAtomicValue(QName varname, String value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        checkNotNull(value);
        checkNotNull(type);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromAtomicValue(value, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindBoolean(QName varname, boolean value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        BooleanValue target = BooleanValue.get(value);
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindByte(QName varname, byte value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromByte(value, type);
        AtomicValue target = (AtomicValue) item.getSaxonItem();
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindDocument(QName varname, InputStream value, String baseURI, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) connection.createItemFromDocument(value, baseURI, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindDocument(QName varname, Reader value, String baseURI, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) connection.createItemFromDocument(value, baseURI, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindDocument(QName varname, Source value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) connection.createItemFromDocument(value, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindDocument(QName varname, String value, String baseURI, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) connection.createItemFromDocument(value, baseURI, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindDocument(QName varname, /*@NotNull*/ XMLStreamReader value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) connection.createItemFromDocument(value, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindDouble(QName varname, double value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        AtomicValue target = new DoubleValue(value);
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindFloat(QName varname, float value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        AtomicValue target = new FloatValue(value);
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindInt(QName varname, int value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromInt(value, type);
        AtomicValue target = (AtomicValue) item.getSaxonItem();
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindItem(QName varname, /*@NotNull*/ XQItem value) throws XQException {
        checkNotClosed();
        ((SaxonXQItem) value).checkNotClosed();
        checkNotNull(varname);
        bindExternalVariable(varname, ((SaxonXQItem) value).getSaxonItem());
    }

    @Override
    public void bindLong(QName varname, long value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromLong(value, type);
        AtomicValue target = (AtomicValue) item.getSaxonItem();
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindNode(QName varname, Node value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromNode(value, type);
        bindExternalVariable(varname, item.getSaxonItem());
    }

    @Override
    public void bindObject(QName varname, /*@NotNull*/ Object value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromObject(value, type);
        bindExternalVariable(varname, item.getSaxonItem());

    }

    @Override
    public void bindSequence(QName varname, /*@NotNull*/ XQSequence value) throws XQException {
        checkNotClosed();
        ((Closable) value).checkNotClosed();
        checkNotNull(varname);
        try {
            if (value instanceof SaxonXQForwardSequence) {
                GroundedValue extent = ((SaxonXQForwardSequence) value).getIterator().materialize();
                getDynamicContext().setParameter(getStructuredQName(varname), extent);
            } else if (value instanceof SaxonXQSequence) {
                bindExternalVariable(varname, ((SaxonXQSequence) value).getValue());
            } else {
                throw new XQException("XQSequence value is not a Saxon sequence");
            }
        } catch (XPathException de) {
            XQException err = new XQException(de.getMessage());
            err.initCause(de);
            throw err;
        }
    }

    @Override
    public void bindShort(QName varname, short value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromShort(value, type);
        AtomicValue target = (AtomicValue) item.getSaxonItem();
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    @Override
    public void bindString(QName varname, String value, XQItemType type) throws XQException {
        checkNotClosed();
        checkNotNull(varname);
        SaxonXQItem item = (SaxonXQItem) getDataFactory().createItemFromString(value, type);
        AtomicValue target = (AtomicValue) item.getSaxonItem();
        checkAtomic(type, target);
        bindExternalVariable(varname, target);
    }

    /*@Nullable*/
    @Override
    public TimeZone getImplicitTimeZone() throws XQException {
        checkNotClosed();
        if (implicitTimeZone != null) {
            return implicitTimeZone;
        } else {
            return new GregorianCalendar().getTimeZone();
        }
    }

    @Override
    public void setImplicitTimeZone(TimeZone implicitTimeZone) throws XQException {
        checkNotClosed();
        GregorianCalendar now = new GregorianCalendar(implicitTimeZone);
        try {
            getDynamicContext().setCurrentDateTime(new DateTimeValue(now, true));
        } catch (XPathException e) {
            throw new XQException(e.getMessage());
        }
        this.implicitTimeZone = implicitTimeZone;
    }


    private void bindExternalVariable(QName varName, Sequence value) throws XQException {
        checkNotNull(varName);
        checkNotNull(value);
        try {
            if (varName.equals(XQConstants.CONTEXT_ITEM)) {
                getDynamicContext().setContextItem(SequenceTool.asItem(value));
            } else {
                if (!externalVariableExists(varName)) {
                    throw new XQException("No external variable named " + varName + " exists in the query");
                }
                getDynamicContext().setParameter(getStructuredQName(varName), value.materialize());
            }
        } catch (XPathException e) {
            XQException err = new XQException(e.getMessage());
            err.initCause(e);
            throw err;
        }
    }

    private void checkAtomic(/*@Nullable*/ XQItemType type, AtomicValue value) throws XQException {
        if (type == null) {
            return;
        }
        ItemType itemType = ((SaxonXQItemType) type).getSaxonItemType();
        if (!itemType.isPlainType()) {
            throw new XQException("Target type is not atomic");
        }
        AtomicType at = (AtomicType) itemType;
        if (!at.matches(value, getConfiguration().getTypeHierarchy())) {
            throw new XQException("value is invalid for specified type");
        }
    }


    /*@NotNull*/
    private static StructuredQName getStructuredQName(QName qname) {
        return new StructuredQName(qname.getPrefix(), qname.getNamespaceURI(), qname.getLocalPart());
    }

    private static void checkNotNull(/*@Nullable*/ Object arg) throws XQException {
        if (arg == null) {
            throw new XQException("Argument is null");
        }
    }

}
