////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.event.SequenceNormalizer;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.serialize.SerializationProperties;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;


/**
 * The <tt>ResultDocumentResolver</tt> interface may be implemented by a user application;
 * it is a callback that is called whenever an xsl:result-document instruction
 * is executed.
 * <p>There is a single method: {@link #resolve(XPathContext, String, String, SerializationProperties)}.
 * Saxon calls this method supplying the dynamic evaluation context, together with the value of the href
 * attribute and the output base URI for the transformation.</p>
 * <p>The result of the callback is an application-supplied instance of the
 * {@link Outputter} interface. Saxon will send events to this {@code Receiver},
 * representing the <b>raw</b> results of the {@code xsl:result-document} instruction.
 * If the application wants results in the form of a document node generated using
 * sequence normalization, then it must include a {@link SequenceNormalizer} in the
 * output pipeline.</p>
 * <p>The {@code ResultDocumentResolver} is called for every {@code xsl:result-document}
 * instruction whether or not it specifies an {@code href} attribute.</p>
 * <p>The implementation must be thread-safe (calls to {@code xsl:result-document} may
 * occur in several threads concurrently). The returned {@code Receiver}
 * may be called in a different thread.</p>
 * <p>If the application wishes to take action when the {@code xsl:result-document} instruction
 * finishes, that is, when the results are available for use, then it should intercept the
 * {@link Outputter#close()} call on the returned {@code Receiver}. This can be done
 * (for example) by adding a {@link net.sf.saxon.event.CloseNotifier} to the output pipeline,
 * or by use of the {@link SequenceNormalizer#onClose} method.</p>
 * <p>This interface supersedes the {@link OutputURIResolver} interface provided in earlier
 * Saxon releases. The {@code OutputURIResolver} was limited because it did not have access
 * to the dynamic context, nor to the serialization parameters, and it did not handle
 * raw output as required by the XSLT 3.0 specification, needed in particular to create
 * JSON output documents.</p>
 * @since 9.9
 */

public interface ResultDocumentResolver {

    /**
     * Saxon calls this method when an {@code xsl:result-document} instruction
     * with an {@code href} attribute is evaluated.
     * <p><em>Note: it may be appropriate for the method to obtain a suitable
     * {@code Receiver} by calling one of the static factory methods on the
     * {@link SerializerFactory} class. A {@code SerializerFactory} can be
     * obtained by calling {@code context.getConfiguration().getSerializerFactory()}.
     * </em></p>
     * @param context the dynamic evaluation context
     * @param href the effective value of the {@code href} attribute of
     *             {@code xsl:result-document} instruction. This will be a zero-length
     *             string if the attribute is omitted.
     * @param baseUri the base output URI of the transformation (typically, the
     *                destination of the principal output destination). This may be null
     *                if no base output URI is known. The recommended action if it is null
     *                is to use the {@code href} value alone if it is absolute URI, otherwise
     *                to raise an error ({@link SaxonErrorCode#SXRD0002}, since there
     *                is no W3C-defined code for the condition).
     * @param properties the serialization properties defined explicitly or implicitly on the
     *               {@code xsl:result-document} instruction, together with information
     *               about character maps in the stylesheet that might be referenced.
     *               Serialization parameters can be ignored if the result document is
     *               not being serialized. However, if the serialization parameters include
     *               a validation factory, then this must not be ignored: a validator
     *               must be inserted at a suitable point in the output pipeline.
     * @return a new instance of the {@code Receiver} class, which Saxon will then call
     * to open the output document, and subsequently to close it. This {@code Receiver}
     * will receive a sequence of events representing the <b>raw result</b> of the
     * {@code xsl:result-document} instruction, as a <b>regular event sequence</b>
     * conforming to the rules defined in {@link net.sf.saxon.event.RegularSequenceChecker}.
     * <p>The implementation should set the {@code systemId} property of the returned
     * {@code Receiver} to the result of resolving the supplied {@code href} against the
     * supplied {@code baseUri}. On return from this method, Saxon will check that
     * the {@code systemId} is non-null and that it satisfies the uniqueness conditions
     * imposed by the XSLT specification. Specifically, it is not permitted for two
     * calls on {@code xsl:result-document} to use the same URI, and this URI must not
     * be read in a call on {@code doc()} or {@code document()}, either before or after
     * executing the {@code xsl:result-document} instruction.</p>
     * @throws XPathException if a result document cannot be opened
     */

    Receiver resolve(
            XPathContext context, String href, String baseUri, SerializationProperties properties)
            throws XPathException;

}

