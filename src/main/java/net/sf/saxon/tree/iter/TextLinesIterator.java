////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.tree.iter;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.UnparsedTextFunction;
import net.sf.saxon.java.CleanerProxy;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.charcode.UTF16CharacterSet;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.transpile.CSharpInjectMembers;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.z.IntPredicateProxy;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.URI;

/**
 * An iterator that iterates over a file line by line, returning each line as a {@link StringValue}
 */
@CSharpInjectMembers(code={"~TextLinesIterator() { reader?.Close(); /* finalizer */ }"})
public abstract class TextLinesIterator implements SequenceIterator {

    protected LineNumberReader reader;
    protected IntPredicateProxy checker;
    StringValue current = null;
    int position = 0;
    protected Location location;
    protected URI uri;
    private CleanerProxy.CleanableProxy cleanable;
    protected TextLinesIterator() {
    }

    /*@Nullable*/
    @Override
    public StringValue next() {
        if (position < 0) {
            // input already exhausted
            close();
            return null;
        }
        try {
            String s = reader.readLine();
            if (s == null) {
                current = null;
                position = -1;
                close();
                return null;
            }
            if (position == 0 && s.length() > 0 && s.charAt(0) == '\ufeff') {
                // remove any BOM found at start of file
                s = s.substring(1);
            }
            checkLine(checker, s);
            current = new StringValue(s);
            position++;
            return current;
        } catch (IOException err) {
            close();
            XPathException e = handleIOError(uri, err);
            if (location != null) {
                e.setLocator(location);
            }
            throw new UncheckedXPathException(e);
        } catch (XPathException err) {
            throw new UncheckedXPathException(err);
        }
    }

    protected XPathException handleIOError(URI uri, IOException err) {
        return UnparsedTextFunction.handleIOError(uri, err);
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException err) {
            //
        }
        if (cleanable != null) {
            cleanable.clean();
        }
    }


    private void checkLine(IntPredicateProxy checker, /*@NotNull*/ String buffer) throws XPathException {
        for (int c = 0; c < buffer.length(); ) {
            int ch32 = buffer.charAt(c++);
            if (UTF16CharacterSet.isHighSurrogate(ch32)) {
                char low = buffer.charAt(c++);
                ch32 = UTF16CharacterSet.combinePair((char) ch32, low);
            }
            if (!checker.test(ch32)) {
                throw new XPathException("The unparsed-text file contains a character that is illegal in XML (line=" +
                        position + " column=" + (c + 1) + " value=hex " + Integer.toHexString(ch32) + ')')
                        .withErrorCode("FOUT1190")
                        .withLocation(location);
            }
        }
    }

    protected void arrangeCleanup(Reader reader, XPathContext context) {
        cleanable = context.getConfiguration().registerCleanupAction(this, () -> {
            try {
                reader.close();
            } catch (Exception err) {
                // no action
            }
        });
    }


}

