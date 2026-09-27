////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyAtomicIterator;
import net.sf.saxon.tree.iter.UnparsedTextIterator;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.io.LineNumberReader;
import java.io.StringReader;
import java.net.URI;
import java.util.Map;


public class UnparsedTextLines extends UnparsedTextFunction implements Callable {

    private final static OptionsParameter OPTION_DETAILS;

    static {
        OptionsParameter o = new OptionsParameter(40);
        o.addAllowedOption("encoding", SequenceType.OPTIONAL_STRING);
        o.addAllowedOption("fallback", SequenceType.SINGLE_BOOLEAN, null);
        OPTION_DETAILS = o;
    }

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
        String encoding = null;
        boolean fallback = false;
        if (getArity() == 2) {
            Item arg1 = arguments[1].head();
            if (arg1 instanceof StringValue) {
                encoding = arg1.getStringValue();
            } else if (arg1 instanceof MapItem) {
                Map<String, GroundedValue> options = OPTION_DETAILS.processSuppliedOptions(((MapItem) arg1), context, 40);
                GroundedValue encodingOption = options.get("encoding");
                if (encodingOption != null) {
                    encoding = encodingOption.getStringValue();
                }
                GroundedValue fallbackOption = options.get("fallback");
                if (fallbackOption != null) {
                    Item fn = fallbackOption.head();
                    fallback = fn.effectiveBooleanValue();
                }
            }
        }
        try {
            return SequenceTool.toLazySequence(evalUnparsedTextLines(hrefVal, encoding, fallback, context));
        } catch (XPathException e) {
            e.maybeSetErrorCode("FOUT1170");
            if (getArity() == 2) {
                throw e.replacingErrorCode("FOUT1200", "FOUT1190");
            }
            throw e;
        }
    }

    private SequenceIterator evalUnparsedTextLines(StringValue hrefVal, String encoding, boolean fallback, XPathContext context) throws XPathException {
        if (hrefVal == null) {
            return EmptyAtomicIterator.INSTANCE;
        }
        String href = hrefVal.getStringValue();
        boolean stable = context.getConfiguration().getBooleanProperty(Feature.STABLE_UNPARSED_TEXT);
        if (stable) {
            // if results have to be stable, the text has to be read into memory and cached
            StringValue content = UnparsedText.evalUnparsedText(hrefVal, getStaticBaseUriString(), encoding, false, false, context);
            assert content != null;
            URI abs = UnparsedTextFunction.getAbsoluteURI(href, getStaticBaseUriString(), context);
            LineNumberReader reader = new LineNumberReader(new StringReader(content.getStringValue()));
            return new UnparsedTextIterator(reader, abs, context, encoding, fallback);
        } else {
            // with unstable results, we avoid reading the whole file into memory
            final URI absoluteURI = UnparsedTextFunction.getAbsoluteURI(href, getRetainedStaticContext().getStaticBaseUriString(), context);
            return new UnparsedTextIterator(absoluteURI, context, encoding, fallback, null);
        }
    }

}

// Copyright (c) 2012-2026 Saxonica Limited
