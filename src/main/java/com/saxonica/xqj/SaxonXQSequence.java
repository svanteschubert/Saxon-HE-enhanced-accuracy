////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.saxonica.xqj;

import com.saxonica.xqj.pull.PullFromIterator;
import com.saxonica.xqj.pull.PullToStax;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.TailIterator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.sax.SAXResult;
import javax.xml.xquery.*;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Properties;

/**
 * Saxon implementation of the XQSequence interface in XQJ, which represents an XDM sequence together
 * with a current position. This class is used for a sequence that can be read forwards, backwards,
 * or by absolute position.
 */
public class SaxonXQSequence extends Closable implements XQResultSequence, SaxonXQItemAccessor {

    private GroundedValue value;
    private int position;
    private SaxonXQPreparedExpression expression;
    private SaxonXQDataFactory factory;

    SaxonXQSequence(GroundedValue value, SaxonXQDataFactory factory) {
        this.value = value;
        this.factory = factory;
        setClosableContainer(factory);
    }

    SaxonXQSequence(GroundedValue value, /*@NotNull*/ SaxonXQPreparedExpression expression) {
        this.value = value;
        this.expression = expression;
        this.factory = expression.getConnection();
        setClosableContainer(expression);
    }

    GroundedValue getValue() {
        return value;
    }

    Configuration getConfiguration() {
        return factory.getConfiguration();
    }

    @Override
    public boolean absolute(int itempos) throws XQException {
        checkNotClosed();
        if (itempos > 0) {
            if (itempos <= value.getLength()) {
                position = itempos;
                return true;
            } else {
                position = -1;
                return false;
            }
        } else if (itempos < 0) {
            if (-itempos <= value.getLength()) {
                position = value.getLength() + itempos + 1;
                return true;
            } else {
                position = 0;
                return false;
            }
        } else {
            position = 0;
            return false;
        }
    }

    @Override
    public void afterLast() throws XQException {
        checkNotClosed();
        position = -1;
    }

    @Override
    public void beforeFirst() throws XQException {
        checkNotClosed();
        position = 0;
    }

    @Override
    public int count() throws XQException {
        checkNotClosed();
        return value.getLength();
    }

    @Override
    public boolean first() throws XQException {
        checkNotClosed();
        if (value.getLength() == 0) {
            position = 0;
            return false;
        } else {
            position = 1;
            return true;
        }
    }

    /*@Nullable*/
    @Override
    public XQItem getItem() throws XQException {
        checkNotClosed();
        SaxonXQItem item = new SaxonXQItem(value.itemAt(position - 1), factory);
        item.setClosableContainer(this);
        return item;
    }

    /*@Nullable*/
    @Override
    public Item getSaxonItem() {
        return value.itemAt(position - 1);
    }

    @Override
    public int getPosition() throws XQException {
        checkNotClosed();
        if (position >= 0) {
            return position;
        } else {
            return value.getLength() + 1;
        }
    }

    /*@NotNull*/
    @Override
    public XMLStreamReader getSequenceAsStream() throws XQException {
        checkNotClosed();
        PullProvider provider = new PullFromIterator(iterateRemainder());
        provider.setPipelineConfiguration(getConfiguration().makePipelineConfiguration());
        return new PullToStax(provider);
    }

    @Override
    public String getSequenceAsString(Properties props) throws XQException {
        checkNotClosed();
        StringWriter sw = new StringWriter();
        writeSequence(sw, props);
        return sw.toString();
    }

    @Override
    public boolean isAfterLast() throws XQException {
        checkNotClosed();
        return position < 0;
    }

    @Override
    public boolean isBeforeFirst() throws XQException {
        checkNotClosed();
        return position == 0 && value.getLength() != 0;
    }

    @Override
    public boolean isFirst() throws XQException {
        checkNotClosed();
        return position == 1;
    }

    @Override
    public boolean isLast() throws XQException {
        checkNotClosed();
        return position == value.getLength();
    }

    @Override
    public boolean isOnItem() throws XQException {
        checkNotClosed();
        return position >= 1;
    }

    @Override
    public boolean isScrollable() throws XQException {
        checkNotClosed();
        return true;
    }

    @Override
    public boolean last() throws XQException {
        checkNotClosed();
        int n = value.getLength();
        if (n == 0) {
            position = -1;
            return false;
        } else {
            position = n;
            return true;
        }
    }

    @Override
    public boolean next() throws XQException {
        checkNotClosed();
        if (position == value.getLength()) {
            position = -1;
            return false;
        } else {
            position++;
            return true;
        }
    }

    @Override
    public boolean previous() throws XQException {
        checkNotClosed();
        if (position == -1) {
            return last();
        }
        position--;
        return position != 0;
    }

    @Override
    public boolean relative(int itempos) throws XQException {
        checkNotClosed();
        if (position == -1) {
            position = value.getLength() + 1;
        }
        position += itempos;
        if (position <= 0) {
            position = 0;
            return false;
        }
        if (position > value.getLength()) {
            position = -1;
            return false;
        }
        return true;
    }

    @Override
    public void writeSequence(OutputStream os, /*@Nullable*/ Properties props) throws XQException {
        checkNotClosed();
        checkNotNull(os);
        if (props == null) {
            props = new Properties();
        }
        props = SaxonXQSequence.setDefaultProperties(props);
        try {
            QueryResult.serializeSequence(iterateRemainder(), getConfiguration(), os, props);
            position = -1;
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    @Override
    public void writeSequence(Writer ow, /*@Nullable*/ Properties props) throws XQException {
        checkNotClosed();
        checkNotNull(ow);
        if (props == null) {
            props = new Properties();
        }
        props = setDefaultProperties(props);
        try {
            PrintWriter pw;
            if (ow instanceof PrintWriter) {
                pw = (PrintWriter) ow;
            } else {
                pw = new PrintWriter(ow);
            }
            QueryResult.serializeSequence(iterateRemainder(), getConfiguration(), pw, props);
            position = -1;
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    @Override
    public void writeSequenceToResult(Result result) throws XQException {
        checkNotClosed();
        Properties props = SaxonXQSequence.setDefaultProperties(null);
        try {
            QueryResult.serializeSequence(iterateRemainder(), getConfiguration(), result, props);
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    @Override
    public void writeSequenceToSAX(ContentHandler saxHandler) throws XQException {
        checkNotClosed();
        writeSequenceToResult(new SAXResult(saxHandler));
    }

    @Override
    public String getAtomicValue() throws XQException {
        return getCurrentItem().getAtomicValue();
    }

    @Override
    public boolean getBoolean() throws XQException {
        return getCurrentItem().getBoolean();
    }

    @Override
    public byte getByte() throws XQException {
        return getCurrentItem().getByte();
    }

    @Override
    public double getDouble() throws XQException {
        return getCurrentItem().getDouble();
    }

    @Override
    public float getFloat() throws XQException {
        return getCurrentItem().getFloat();
    }

    @Override
    public int getInt() throws XQException {
        return getCurrentItem().getInt();
    }

    /*@NotNull*/
    @Override
    public XMLStreamReader getItemAsStream() throws XQException {
        return getCurrentItem().getItemAsStream();
    }

    @Override
    public String getItemAsString(Properties props) throws XQException {
        return getCurrentItem().getItemAsString(props);
    }

    /*@NotNull*/
    @Override
    public XQItemType getItemType() throws XQException {
        return getCurrentItem().getItemType();
    }

    @Override
    public long getLong() throws XQException {
        return getCurrentItem().getLong();
    }

    @Override
    public Node getNode() throws XQException {
        return getCurrentItem().getNode();
    }

    /*@NotNull*/
    @Override
    public URI getNodeUri() throws XQException {
        return getCurrentItem().getNodeUri();
    }

    @Override
    public Object getObject() throws XQException {
        return getCurrentItem().getObject();
    }

    @Override
    public short getShort() throws XQException {
        return getCurrentItem().getShort();
    }

    @Override
    public boolean instanceOf(/*@NotNull*/ XQItemType type) throws XQException {
        return getCurrentItem().instanceOf(type);
    }

    @Override
    public void writeItem(OutputStream os, Properties props) throws XQException {
        getCurrentItem().writeItem(os, props);
    }

    @Override
    public void writeItem(Writer ow, Properties props) throws XQException {
        getCurrentItem().writeItem(ow, props);
    }

    @Override
    public void writeItemToResult(Result result) throws XQException {
        getCurrentItem().writeItemToResult(result);
    }

    @Override
    public void writeItemToSAX(ContentHandler saxHandler) throws XQException {
        getCurrentItem().writeItemToSAX(saxHandler);
    }

    /*@Nullable*/
    private SaxonXQItem getCurrentItem() throws XQException {
        checkNotClosed();
        if (position == 0) {
            throw new XQException("Sequence is positioned before first item");
        }
        if (position < 0) {
            throw new XQException("Sequence is positioned after last item");
        }
        SaxonXQItem item = new SaxonXQItem(value.itemAt(position - 1), factory);
        item.setClosableContainer(this);
        return item;
    }

    @Override
    public XQConnection getConnection() throws XQException {
        checkNotClosed();
        if (expression == null) {
            throw new IllegalStateException("Connection not available");
        }
        return expression.getConnection();
    }

    /*@Nullable*/
    private SequenceIterator iterateRemainder() throws XQException {
        try {
            if (position == 0) {
                return value.iterate();
            } else if (position < 0) {
                return EmptyIterator.emptyIterator();
            } else {
                return TailIterator.make(value.iterate(), position);
            }
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    private void checkNotNull(/*@Nullable*/ Object arg) throws XQException {
        if (arg == null) {
            throw new XQException("Argument is null");
        }
    }

    /*@NotNull*/
    private XQException newXQException(/*@NotNull*/ Exception err) {
        XQException xqe = new XQException(err.getMessage());
        xqe.initCause(err);
        return xqe;
    }

    /*@Nullable*/
    static Properties setDefaultProperties(/*@Nullable*/ Properties props) {
        Properties newProps = props == null ? new Properties() : new Properties(props);
        boolean changed = false;
        if (newProps.getProperty(OutputKeys.METHOD) == null) {
            newProps.setProperty(OutputKeys.METHOD, "xml");
            changed = true;
        }
        if (newProps.getProperty(OutputKeys.INDENT) == null) {
            newProps.setProperty(OutputKeys.INDENT, "yes");
            changed = true;
        }
        if (newProps.getProperty(OutputKeys.OMIT_XML_DECLARATION) == null) {
            newProps.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            changed = true;
        }
        return changed || props == null ? newProps : props;
    }
}
