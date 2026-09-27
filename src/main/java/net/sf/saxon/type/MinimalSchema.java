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
 * A schema that contains only the built-in types. This class is used in Saxon-HE and Saxon-PE,
 * which do not support schema validation.
 */

public class MinimalSchema implements Schema {

    final Configuration config;

    public MinimalSchema(Configuration config) {
        this.config = config;
    }

    /**
     * Get the configuration
     */
    @Override
    public Configuration getConfiguration() {
        return config;
    }

    public boolean includesTargetNamespace(NamespaceUri target) {
        return target.equals(NamespaceUri.SCHEMA);
    }

    /**
     * Get a global attribute declaration by fingerprint
     *
     * @param fingerprint the NamePool fingerprint of the element name
     * @return the attribute declaration whose name matches the given
     * fingerprint, or null if no element declaration with this name has
     * been registered.
     */
    @Override
    public IAttributeDecl getAttributeDecl(int fingerprint) {
        return null;
    }

    /**
     * Get a global attribute declaration by QName
     *
     * @param name the the element name
     * @return the attribute declaration whose name matches the given
     * fingerprint, or null if no element declaration with this name has
     * been registered.
     */
    @Override
    public IAttributeDecl getAttributeDecl(StructuredQName name) {
        return null;
    }

    /**
     * Get the global element declaration with a given fingerprint
     *
     * @param fingerprint the NamePool fingerprint of the name of the element
     * @return the element declaration, or null if not found.
     */
    @Override
    public IElementDecl getElementDecl(int fingerprint) {
        return null;
    }

    /**
     * Get the global element declaration with a given  name
     *
     * @param name the name of the element
     * @return the element declaration, or null if not found.
     */
    @Override
    public IElementDecl getElementDecl(StructuredQName name) {
        return null;
    }

    /**
     * Get the top-level schema type definition with a given QName.
     *
     * @param name the name of the required schema type
     * @return the schema type , or null if there is none
     * with this name.
     * @since 9.7
     */
    @Override
    public SchemaType getSchemaType(StructuredQName name) {
        if (name.hasURI(NamespaceUri.SCHEMA)) {
            return BuiltInType.getSchemaTypeByLocalName(name.getLocalPart());
        }
        return null;
    }

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
    @Override
    public Receiver getDocumentValidator(Receiver receiver, String systemId, ParseOptions validationOptions, Location initiatingLocation) {
        return receiver;
    }

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
     * @throws XPathException if a validator for the element cannot be created
     */
    @Override
    public Receiver getElementValidator(Receiver receiver, ParseOptions validationOptions, Location locationId) throws XPathException {
        return receiver;
    }

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
    @Override
    public SimpleType validateAttribute(StructuredQName nodeName, UnicodeString value, int validation) throws ValidationException {
        ValidationFailure ve = new ValidationFailure(
                "No global attribute declaration found for attribute " +
                        nodeName.getDisplayName());
        ve.setErrorCode("XTTE1512");
        throw ve.makeException();
    }

    /**
     * Make a {@code schema-element} node test for a given element name
     *
     * @param fp the integer fingerprint of the element name
     * @return a node test corresponding to the XPath item type syntax schema-element(E)
     * @throws SchemaException if there is no element declaration with this name in the schema
     */
    @Override
    public GNodeType makeSchemaElementTest(int fp) throws SchemaException {
        throw new SchemaException("No such element declaration");
    }

    /**
     * Make a {@code schema-attribute} node test for a given attribute name
     *
     * @param fp the integer fingerprint of the attribute name
     * @return a node test corresponding to the XPath item type syntax schema-attribute(E)
     * @throws SchemaException if there is no attribute declaration with this name in the schema
     */
    @Override
    public GNodeType makeSchemaAttributeTest(int fp) throws SchemaException {
        throw new SchemaException("No such attribute declaration");
    }
    
}

