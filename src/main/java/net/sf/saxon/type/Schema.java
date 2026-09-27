// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.gnode.GNodeType;

/**
 * A schema is a collection of schema components. It may be derived from a single schema
 * document (an xs:schema element), in which case it contains those components defined in that
 * schema document, the components defined in the included documents (expanded
 * transitively), and the components defined in imported schema documents (not
 * expanded transitively). Alternatively, it may be a collection of schema components
 * derived from multiple independent schema documents.
 */

public interface Schema {

    /**
     * Get the configuration
     */

    Configuration getConfiguration();

    boolean includesTargetNamespace(NamespaceUri target);
    /**
     * Get a global attribute declaration by fingerprint
     *
     * @param fingerprint the NamePool fingerprint of the element name
     * @return the attribute declaration whose name matches the given
     * fingerprint, or null if no element declaration with this name has
     * been registered.
     */

    IAttributeDecl getAttributeDecl(int fingerprint);

    /**
     * Get a global attribute declaration by QName
     *
     * @param name the the element name
     * @return the attribute declaration whose name matches the given
     * fingerprint, or null if no element declaration with this name has
     * been registered.
     */

    IAttributeDecl getAttributeDecl(StructuredQName name);

    /**
     * Get the global element declaration with a given fingerprint
     *
     * @param fingerprint the NamePool fingerprint of the name of the element
     * @return the element declaration, or null if not found.
     */

    IElementDecl getElementDecl(int fingerprint);


    /**
     * Get the global element declaration with a given  name
     *
     * @param name the name of the element
     * @return the element declaration, or null if not found.
     */

    IElementDecl getElementDecl(StructuredQName name);

    /**
     * Get the top-level schema type definition with a given QName.
     *
     * @param name the name of the required schema type
     * @return the schema type , or null if there is none
     * with this name.
     * @since 9.7
     */

    /*@Nullable*/
    SchemaType getSchemaType(StructuredQName name);

    /**
     * Get a document-level validator to add to a Receiver pipeline.
     * <p>This method is intended for internal use.</p>
     *
     * @param receiver           The receiver to which events should be sent after validation
     * @param systemId           the base URI of the document being validated
     * @param validationOptions  Supplies options relevant to XSD validation
     * @param initiatingLocation The location of the expression that requested validation
     * @return A Receiver to which events can be sent for validation
     */

    Receiver getDocumentValidator(Receiver receiver,
                                         String systemId,
                                         ParseOptions validationOptions,
                                         Location initiatingLocation);

    /**
     * Get a Receiver that can be used to validate an element, and that passes the validated
     * element on to a target receiver. If validation is not supported, the returned receiver
     * will be the target receiver.
     * <p>This method is intended for internal use.</p>
     *
     * @param receiver          the target receiver tp receive the validated element
     * @param validationOptions options affecting the way XSD validation is done
     * @param locationId        current location in the stylesheet or query
     * @return The target receiver, indicating that with this configuration, no validation
     * is performed.
     * @throws net.sf.saxon.trans.XPathException if a validator for the element cannot be created
     */

    Receiver getElementValidator(Receiver receiver,
                                 ParseOptions validationOptions,
                                 Location locationId)
            throws XPathException;

    /**
     * Validate an attribute value.
     * <p>This method is intended for internal use.</p>
     *
     * @param nodeName   the name of the attribute
     * @param value      the value of the attribute as a string
     * @param validation STRICT or LAX
     * @return the type annotation to apply to the attribute node
     * @throws ValidationException if the value is invalid
     */

    SimpleType validateAttribute(StructuredQName nodeName, UnicodeString value, int validation)
            throws ValidationException, MissingComponentException;


    /**
     * Make a {@code schema-element} node test for a given element name
     *
     * @param fp the integer fingerprint of the element name
     * @return a node test corresponding to the XPath item type syntax schema-element(E)
     * @throws SchemaException if there is no element declaration with this name in the schema
     */

    GNodeType makeSchemaElementTest(int fp) throws SchemaException;

    /**
     * Make a {@code schema-attribute} node test for a given attribute name
     *
     * @param fp the integer fingerprint of the attribute name
     * @return a node test corresponding to the XPath item type syntax schema-attribute(E)
     * @throws SchemaException if there is no attribute declaration with this name in the schema
     */

    GNodeType makeSchemaAttributeTest(int fp) throws SchemaException;


}

