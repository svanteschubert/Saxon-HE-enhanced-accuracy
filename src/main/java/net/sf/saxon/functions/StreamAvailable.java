////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.event.*;
import net.sf.saxon.expr.Callable;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.trans.QuitParsingException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.value.BooleanValue;

/**
* This class implements the XSLT 3.0 function stream-available()
*/


public class StreamAvailable extends SystemFunction implements Callable {

    /**
     * Call the Callable.
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences.
     *                  <p>Generally it is advisable, if calling iterate() to process a supplied sequence, to
     *                  call it only once; if the value is required more than once, it should first be converted
     *                  to a {@link net.sf.saxon.om.GroundedValue} by calling the utility methd
     *                  SequenceTool.toGroundedValue().</p>
     *                  <p>If the expected value is a single item, the item should be obtained by calling
     *                  Sequence.head(): it cannot be assumed that the item will be passed as an instance of
     *                  {@link net.sf.saxon.om.Item} or {@link net.sf.saxon.value.AtomicValue}.</p>
     *                  <p>It is the caller's responsibility to perform any type conversions required
     *                  to convert arguments to the type expected by the callee. An exception is where
     *                  this Callable is explicitly an argument-converting wrapper around the original
     *                  Callable.</p>
     * @return the result of the evaluation, in the form of a Sequence. It is the responsibility
     *         of the callee to ensure that the type of result conforms to the expected result type.
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the argument
     */
    @Override
    public BooleanValue call(XPathContext context, Sequence[] arguments) throws XPathException {
        boolean result = isAvailable(arguments[0].head().getStringValue(), context);
        return BooleanValue.get(result);
    }

    /**
     * Do the work.
     * @param uri the URI to be tested
     * @param context the dynamic evaluation context
     * @return boolean true if the stream is available as defined by the spec
     */

    private boolean isAvailable(String uri, XPathContext context) {
        try {
            Receiver tester = new StreamTester(context.getConfiguration().makePipelineConfiguration());
            DocumentFn.sendDoc(uri, getRetainedStaticContext().getStaticBaseUriString(), context, null, tester, new ParseOptions());
        } catch (QuitParsingException e) {
            // Indicates that the first element was reported and the parse was then aborted
            return true;
        } catch (XPathException e) {
            return false;
        }
        return false;
    }

    /**
     * An implementation of Receiver which does nothing other than throwing a QuitParsingException
     * when the first startElement event is notified
     */

    private static class StreamTester extends ProxyReceiver {

        public StreamTester(PipelineConfiguration pipe) {
            super(new Sink(pipe));
        }

        @Override
        public void startElement(NodeName elemName, SchemaType type,
                                 AttributeMap attributes, NamespaceMap namespaces,
                                 Location location, int properties) throws XPathException {
            throw new QuitParsingException(false);
        }

    }

}
