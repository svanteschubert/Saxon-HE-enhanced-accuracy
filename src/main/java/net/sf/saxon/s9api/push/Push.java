////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api.push;

import net.sf.saxon.s9api.*;

/**
 * An interface designed for applications to generate XML documents by issuing events. Functionally
 * similar to the SAX {@link org.xml.sax.ContentHandler} or the Stax {@link javax.xml.stream.XMLStreamWriter},
 * it is designed eliminate the usability problems and ambiguities in those specifications.
 *
 * <p>The {@code Push} interface can be used to create a single tree rooted at a document node.
 * It is possible to constrain the document node to be well-formed (in which case it must have
 * a single element node child, plus optionally comment and processing instruction children).
 * Some implementations may only accept well-formed documents.</p>
 *
 * <p>The document created using the {@code Push} interface is set to the {@link Destination}
 * defined when {@code Push} is created using the factory method {@link Processor#newPush(Destination)}.
 * The {@link Destination} will commonly be an {@link XdmDestination} or a {@link Serializer},
 * but it could also be, for example, an {@link XsltTransformer} or an {@link SchemaValidator}.</p>
 *
 * <p>Here is an example of application code written to construct a simple XML document:</p>
 *
 * <pre>{@code
 * Document doc = processor.newPush(destination).document(true);
 * doc.setDefaultNamespace("http://www.example.org/ns");
 * Element top = doc.element("root")
 *                       .attribute("version", "1.5");
 * for (Employee emp : employees) {
 *     top.element("emp")
 *        .attribute("ssn", emp.getSSN())
 *        .text(emp.getName());
 * }
 * doc.close();
 * }</pre>
 */

public interface Push {

    /**
     * Start an XML document.
     *
     * @param wellFormed Set to true if the document is required to be well-formed;
     *                   set to false if there is no such requirement. A well-formed
     *                   document must have as its children exactly one element node
     *                   plus optionally, any number of comment and processing instruction
     *                   nodes (no text nodes are allowed); any attempt to construct a node
     *                   sequence that does not follow these rules will result in an exception.
     *                   If the document is not required to be well-formed, the children
     *                   of the document node may comprise any sequence of element, text,
     *                   comment, and processing instruction nodes.
     * @return a Document object which may be used to add content to the document, or to
     * close the document when it has been fully written.
     * @throws SaxonApiException if the specified constraints are violated, or if the
     * implementation detects any problems
     */

    Document document(boolean wellFormed) throws SaxonApiException;

}

