////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.SequenceTool;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.UnparsedTextIterator;
import net.sf.saxon.value.StringValue;

import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URI;


public class UnparsedTextLines extends UnparsedTextFunction implements Callable {

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue hrefVal = (StringValue) arguments[0].head();
        String encoding = getArity() == 2 ? arguments[1].head().getStringValue() : null;
        try {
            return SequenceTool.toLazySequence(evalUnparsedTextLines(hrefVal, encoding, context));
        } catch (XPathException e) {
            if (getArity() == 2 && e.getErrorCodeLocalPart().equals("FOUT1200")) {
                e.setErrorCode("FOUT1190");
            }
            throw e;
        }
    }

    private SequenceIterator evalUnparsedTextLines(StringValue hrefVal, String encoding, XPathContext context) throws XPathException {
        if (hrefVal == null) {
            return EmptyIterator.ofAtomic();
        }
        String href = hrefVal.getStringValue();
        boolean stable = context.getConfiguration().getBooleanProperty(Feature.STABLE_UNPARSED_TEXT);
        if (stable) {
            // if results have to be stable, the text has to be read into memory and cached
            StringValue content = UnparsedText.evalUnparsedText(hrefVal, getStaticBaseUriString(), encoding, context);
            assert content != null;
            URI abs = UnparsedTextFunction.getAbsoluteURI(href, getStaticBaseUriString(), context);
            LineNumberReader reader = new LineNumberReader(new StringReader(content.getStringValue()));
            return new UnparsedTextIterator(reader, abs, context, encoding);
        } else {
            // with unstable results, we avoid reading the whole file into memory
            final URI absoluteURI = UnparsedTextFunction.getAbsoluteURI(href, getRetainedStaticContext().getStaticBaseUriString(), context);
            return new UnparsedTextIterator(absoluteURI, context, encoding, null);
        }
    }

}

// Copyright (c) 2012-2020 Saxonica Limited
