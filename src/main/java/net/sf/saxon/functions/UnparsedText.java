////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Controller;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.ZeroOrOne;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.CharSequenceConsumer;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.StringValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of fn:unparsed-text() - with one argument or two
 */
public class UnparsedText extends UnparsedTextFunction implements PushableFunction {

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
    public ZeroOrOne<StringValue> call(XPathContext context, Sequence[] arguments) throws XPathException {
        StringValue hrefVal = (StringValue) arguments[0].head();
        String encoding = getArity() == 2 ? arguments[1].head().getStringValue() : null;
        try {
            return new ZeroOrOne<>(evalUnparsedText(hrefVal, getStaticBaseUriString(), encoding, context));
        } catch (XPathException e) {
            if (getArity() == 2 && e.getErrorCodeLocalPart().equals("FOUT1200")) {
                e.setErrorCode("FOUT1190");
            }
            throw e;
        }
    }

    @Override
    public void process(Outputter destination, XPathContext context, Sequence[] arguments) throws XPathException {
        boolean stable = context.getConfiguration().getBooleanProperty(Feature.STABLE_UNPARSED_TEXT);
        if (stable) {
            ZeroOrOne<StringValue> result = call(context, arguments);
            StringValue value = result.head();
            if (value != null) {
                destination.append(value, Loc.NONE, ReceiverOption.NONE);
            }
        } else {
            StringValue href = (StringValue) arguments[0].head();
            URI absoluteURI = getAbsoluteURI(href.getStringValue(), getStaticBaseUriString(), context);
            String encoding = getArity() == 2 ? arguments[1].head().getStringValue() : null;
            CharSequenceConsumer consumer = destination.getStringReceiver(false);
            consumer.open();
            try {
                readFile(absoluteURI, encoding, consumer, context);
                consumer.close();
            } catch (XPathException e) {
                if (getArity() == 2 && e.getErrorCodeLocalPart().equals("FOUT1200")) {
                    e.setErrorCode("FOUT1190");
                }
                throw e;
            }
        }
    }

    private static final String errorValue = "\u0000";

    /**
     * Evaluation of the unparsed-text function
     *
     * @param hrefVal  the relative URI
     * @param base     the base URI
     * @param encoding the encoding to be used
     * @param context  dynamic evaluation context
     * @return the result of the function
     * @throws XPathException if evaluation fails
     */

    public static StringValue evalUnparsedText(StringValue hrefVal, String base, String encoding, XPathContext context) throws XPathException {
        CharSequence content;
        StringValue result;
        boolean stable = context.getConfiguration().getBooleanProperty(Feature.STABLE_UNPARSED_TEXT);
        try {
            if (hrefVal == null) {
                return null;
            }
            String href = hrefVal.getStringValue();
            URI absoluteURI = getAbsoluteURI(href, base, context);
            if (stable) {
                final Controller controller = context.getController();
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized(controller) {
                    Map<URI, String> cache = (Map<URI, String>)controller.getUserData("unparsed-text-cache", "");
                    if (cache != null) {
                        String existing = cache.get(absoluteURI);
                        if (existing != null) {
                            if (existing.startsWith(errorValue)) {
                                throw new XPathException(existing.substring(1), "FOUT1170");
                            }
                            return new StringValue(existing);
                        }
                    }
                    XPathException error = null;
                    try {
                        StringValue.Builder consumer = new StringValue.Builder();
                        readFile(absoluteURI, encoding, consumer, context);
                        content = consumer.getStringValue().getStringValueCS();
                    } catch (XPathException e) {
                        error = e;
                        content = errorValue + e.getMessage();
                    }
                    if (cache == null) {
                        cache = new HashMap<>();
                        controller.setUserData("unparsed-text-cache", "", cache);
                        cache.put(absoluteURI, content.toString());
                    }
                    if (error != null) {
                        throw error;
                    }
                }
            } else {
                StringValue.Builder consumer = new StringValue.Builder();
                readFile(absoluteURI, encoding, consumer, context);
                return consumer.getStringValue();
            }
            result = new StringValue(content);
        } catch (XPathException err) {
            err.maybeSetErrorCode("FOUT1170");
            throw err;
        }
        return result;
    }

    // diagnostic method to output the octets of a file

    public static void main(String[] args) throws Exception {
        FastStringBuffer sb1 = new FastStringBuffer(FastStringBuffer.C256);
        FastStringBuffer sb2 = new FastStringBuffer(FastStringBuffer.C256);
        File file = new File(args[0]);
        InputStream is = new FileInputStream(file);
        while (true) {
            int b = is.read();
            if (b < 0) {
                System.out.println(sb1);
                System.out.println(sb2);
                break;
            }
            sb1.append(Integer.toHexString(b) + " ");
            sb2.append((char) b + " ");
            if (sb1.length() > 80) {
                System.out.println(sb1);
                System.out.println(sb2);
                sb1 = new FastStringBuffer(FastStringBuffer.C256);
                sb2 = new FastStringBuffer(FastStringBuffer.C256);
            }
        }
        is.close();
    }

}
