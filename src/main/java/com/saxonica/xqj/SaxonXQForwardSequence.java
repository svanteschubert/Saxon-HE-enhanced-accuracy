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
import net.sf.saxon.om.FocusTrackingIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pull.PullProvider;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.PrependSequenceIterator;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

import javax.xml.stream.XMLStreamReader;
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
 * The class is a Saxon implementation of the XQJ interface XQResultSequence. This
 * implementation is used to represent a sequence that can only be read in a forwards direction.
 */
public class SaxonXQForwardSequence extends Closable implements XQResultSequence {

    private FocusTrackingIterator iterator;
    SaxonXQPreparedExpression expression;
    int position = 0;   // set to -count when positioned after the end
    int lastReadPosition = Integer.MIN_VALUE;   // used to prevent reading the same item twice, which XQJ doesn't allow

    protected SaxonXQForwardSequence(FocusTrackingIterator iterator, SaxonXQPreparedExpression expression) {
        this.iterator = iterator;
        this.expression = expression;
        setClosableContainer(expression);
    }

    SequenceIterator getIterator(){
        return iterator;
    }

    Configuration getConfiguration() {
        return expression.getConnection().getConfiguration();
    }

    @Override
    public XQConnection getConnection() throws XQException {
        checkNotClosed();
        return expression.getConnection();
    }

    @Override
    public String getAtomicValue() throws XQException {
        return getCurrentXQItem(true).getAtomicValue();
    }

    @Override
    public boolean getBoolean() throws XQException {
        return getCurrentXQItem(true).getBoolean();
    }

    @Override
    public byte getByte() throws XQException {
        return getCurrentXQItem(true).getByte();
    }

    @Override
    public double getDouble() throws XQException {
        return getCurrentXQItem(true).getDouble();
    }

    @Override
    public float getFloat() throws XQException {
        return getCurrentXQItem(true).getFloat();
    }

    @Override
    public int getInt() throws XQException {
        return getCurrentXQItem(true).getInt();
    }

    @Override
    public XMLStreamReader getItemAsStream() throws XQException {
        return getCurrentXQItem(true).getItemAsStream();
    }

    @Override
    public String getItemAsString(Properties props) throws XQException {
        return getCurrentXQItem(true).getItemAsString(props);
    }

    @Override
    public XQItemType getItemType() throws XQException {
        return getCurrentXQItem(false).getItemType();
    }

    @Override
    public long getLong() throws XQException {
        return getCurrentXQItem(true).getLong();
    }

    @Override
    public Node getNode() throws XQException {
        return getCurrentXQItem(true).getNode();
    }

    @Override
    public URI getNodeUri() throws XQException {
        return getCurrentXQItem(false).getNodeUri();
    }

    @Override
    public Object getObject() throws XQException {
        return getCurrentXQItem(true).getObject();
    }

    @Override
    public short getShort() throws XQException {
        return getCurrentXQItem(true).getShort();
    }

    @Override
    public boolean instanceOf(XQItemType type) throws XQException {
        return getCurrentXQItem(false).instanceOf(type);
    }

    @Override
    public void writeItem(OutputStream os, Properties props) throws XQException {
        getCurrentXQItem(true).writeItem(os, props);
    }

    @Override
    public void writeItem(Writer ow, Properties props) throws XQException {
        getCurrentXQItem(true).writeItem(ow, props);
    }

    @Override
    public void writeItemToResult(Result result) throws XQException {
        getCurrentXQItem(true).writeItemToResult(result);
    }

    @Override
    public void writeItemToSAX(ContentHandler saxHandler) throws XQException {
        getCurrentXQItem(true).writeItemToSAX(saxHandler);
    }

    @Override
    public boolean absolute(int itempos) throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public void afterLast() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public void beforeFirst() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public int count() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean first() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    /*@NotNull*/
    @Override
    public XQItem getItem() throws XQException {
        return getCurrentXQItem(true);
    }

    @Override
    public int getPosition() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    /*@NotNull*/
    @Override
    public XMLStreamReader getSequenceAsStream() throws XQException {
        checkNotClosed();
        checkOnlyReadOnce();
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
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean isBeforeFirst() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean isFirst() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean isLast() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean isOnItem() throws XQException {
        checkNotClosed();
        return position > 0;
    }

    @Override
    public boolean isScrollable() throws XQException {
        checkNotClosed();
        return false;
    }

    @Override
    public boolean last() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean next() throws XQException {
        checkNotClosed();
        if (position < 0) {
            return false;
        }
        try {
            Item next = iterator.next();
            if (next == null) {
                position = -1;
                return false;
            } else {
                position++;
                return true;
            }
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    @Override
    public boolean previous() throws XQException {
        throw new XQException("Sequence is forwards-only");
    }

    @Override
    public boolean relative(int itempos) throws XQException {
        checkNotClosed();
//        if (itempos < 0) {
        throw new XQException("Sequence is forwards-only, cannot move backwards");
//        } else {
//            for (int i=0; i<itempos; i++) {
//                if (!next()) {
//                    return false;
//                }
//            }
//            return true;
//        }
    }

    @Override
    public void writeSequence(OutputStream os, /*@Nullable*/ Properties props) throws XQException {
        checkNotNull(os);
        checkNotClosed();
        checkOnlyReadOnce();
        if (props == null) {
            props = new Properties();
        }
        props = SaxonXQSequence.setDefaultProperties(props);
        try {
            QueryResult.serializeSequence(iterateRemainder(), getConfiguration(), os, props);
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    @Override
    public void writeSequence(Writer ow, /*@Nullable*/ Properties props) throws XQException {
        checkNotClosed();
        checkNotNull(ow);
        checkOnlyReadOnce();
        if (props == null) {
            props = new Properties();
        } else {
            SaxonXQItem.validateSerializationProperties(props, expression.getConfiguration());
        }
        props = SaxonXQSequence.setDefaultProperties(props);
        PrintWriter pw;
        if (ow instanceof PrintWriter) {
            pw = (PrintWriter) ow;
        } else {
            pw = new PrintWriter(ow);
        }
        try {
            QueryResult.serializeSequence(iterateRemainder(), getConfiguration(), pw, props);
        } catch (XPathException e) {
            throw newXQException(e);
        }
    }

    private SequenceIterator iterateRemainder() throws XQException {
        if (isOnItem()) {
            return new PrependSequenceIterator(iterator.current(), iterator);
        } else {
            return iterator;
        }
    }

    @Override
    public void writeSequenceToResult(Result result) throws XQException {
        checkNotClosed();
        checkNotNull(result);
        checkOnlyReadOnce();
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
        checkNotNull(saxHandler);
        writeSequenceToResult(new SAXResult(saxHandler));
    }

    /*@NotNull*/
    private XQItem getCurrentXQItem(boolean onceOnly) throws XQException {
        checkNotClosed();
        if (position == 0) {
            throw new XQException("The XQSequence is positioned before the first item");
        } else if (position < 0) {
            throw new XQException("The XQSequence is positioned after the last item");
        }
        if (onceOnly) {
            checkOnlyReadOnce();
        }
        SaxonXQItem item = new SaxonXQItem(iterator.current(), expression.getConnection());
        item.setClosableContainer(this);
        return item;
    }

    /*@Nullable*/ Item getSaxonItem() {
        return iterator.current();
    }


    private void checkNotNull(/*@Nullable*/ Object arg) throws XQException {
        if (arg == null) {
            throw new XQException("Argument is null");
        }
    }

    private void checkOnlyReadOnce() throws XQException {
        if (position == lastReadPosition) {
            throw new XQException("XQJ does not allow the same item to be read more than once");
        }
        lastReadPosition = position;
    }

    /*@NotNull*/
    private XQException newXQException(/*@NotNull*/ Exception err) {
        XQException xqe = new XQException(err.getMessage());
        xqe.initCause(err);
        return xqe;
    }
}
