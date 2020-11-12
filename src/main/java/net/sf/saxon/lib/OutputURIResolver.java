////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.trans.XsltController;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import java.util.function.Function;


/**
 * This interface defines an OutputURIResolver. This is a counterpart to the JAXP
 * URIResolver, but is used to map the URI of a secondary result document to a Result object
 * which acts as the destination for the new document.
 * <p>From Saxon 9.9 this interface is obsolescent. It is unable to handle the full flexibility
 * of XSLT 3.0, for example it cannot handle raw output, JSON serialization, or the item-separator
 * serialization property. A new mechanism has therefore been introduced. This has a low-level
 * interface {@link XsltController#setResultDocumentResolver(ResultDocumentResolver)}, and a high-level
 * counterpart at the s9api level, using the setResultDocumentHandler() method on {@link net.sf.saxon.s9api.Xslt30Transformer}
 * and {@link net.sf.saxon.s9api.XsltTransformer}.</p>
 */

public interface OutputURIResolver {

    /**
     * Get an instance of this OutputURIResolver class.
     * <p>This method is called every time an xsl:result-document instruction
     * is evaluated (with an href attribute). The resolve() and close() methods
     * will be called on the returned instance.</p>
     * <p>Note that in Saxon-EE, the xsl:result-document instruction executes
     * asynchronously, which means that documents are not necessarily closed in the
     * order they are opened, and multiple documents may be open at once.</p>
     * <p>If the OutputURIResolver is stateless (that is, it retains no information
     * between resolve() and close()), then the same instance can safely be returned
     * each time. For a stateful OutputURIResolver, it must either take care to be
     * thread-safe (handling multiple invocations of xsl:result-document concurrently),
     * or it must return a fresh instance of itself for each call.</p>
     */

    OutputURIResolver newInstance();

    /**
     * Resolve an output URI.
     *
     * @param href The relative URI of the output document. This corresponds to the
     *             href attribute of the xsl:result-document instruction.
     * @param base The base URI that should be used. This is the Base Output URI, typically
     *             the URI of the principal output document
     * @return a Result object representing the destination for the XML document. The
     *         method can also return null, in which case the standard output URI resolver
     *         will be used to create a Result object.
     *         <p>The systemId property of the returned Result object should normally be the
     *         result of resolving href (as a relative URI) against the value of base.
     *         This systemId is used to enforce the error
     *         conditions in the XSLT specification that disallow writing two result trees to the
     *         same destination, or reading from a destination that is written to in the same
     *         transformation. Setting the systemId to null, or to a deceptive value, will defeat
     *         these error checks (which can sometimes be useful). Equally, setting the systemId
     *         to the same value on repeated calls when different href/base arguments are supplied
     *         will cause a spurious error.</p>
     * @throws javax.xml.transform.TransformerException
     *          if any error occurs
     */

    Result resolve(String href, String base) throws TransformerException;

    /**
     * Signal completion of the result document. This method is called by the system
     * when the result document has been successfully written. It allows the resolver
     * to perform tidy-up actions such as closing output streams, or firing off
     * processes that take this result tree as input. The original Result object is
     * supplied to identify the document that has been completed. This allows the
     * OutputURIResolver to be stateless, making it easier to handle the asynchronous
     * calls of xsl:result-document that arise in Saxon-EE.
     *
     * @param result The result object returned by the previous call of resolve()
     * @throws javax.xml.transform.TransformerException
     *          if any error occurs
     */

    void close(Result result) throws TransformerException;

}

