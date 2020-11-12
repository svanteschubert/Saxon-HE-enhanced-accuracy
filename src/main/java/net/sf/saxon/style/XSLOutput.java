////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.functions.ResolveQName;
import net.sf.saxon.functions.ResolveURI;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.SaxonOutputKeys;
import net.sf.saxon.lib.SerializerFactory;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import javax.xml.transform.OutputKeys;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * An xsl:output element in the stylesheet.
 */

public class XSLOutput extends StyleElement {

    private StructuredQName outputFormatName;
    /*@Nullable*/ private String method = null;
    private String outputVersion = null;

    private String useCharacterMaps = null;
    private Map<String, String> serializationAttributes = new HashMap<String, String>(10);

    private HashMap<String, String> userAttributes = null;

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    @Override
    public void prepareAttributes() {
        String nameAtt = null;
        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getStructuredQName().getClarkName();
            if (f.equals("name")) {
                nameAtt = Whitespace.trim(value);
            } else if (f.equals("version")) {
                String outputVersion = Whitespace.trim(value);
                serializationAttributes.put(f, outputVersion);
            } else if (f.equals("use-character-maps")) {
                useCharacterMaps = value;
            } else if (f.equals("parameter-document")) {
                String val = Whitespace.trim(value);
                try {
                    val = ResolveURI.makeAbsolute(val, getBaseURI()).toASCIIString();
                } catch (URISyntaxException e) {
                    compileError(XPathException.makeXPathException(e));
                }
                serializationAttributes.put(f, val);
                //serializationAttributes.put(SaxonOutputKeys.PARAMETER_DOCUMENT_BASE_URI, getBaseURI());
            } else if (XSLResultDocument.fans.contains(f) && !f.equals("output-version")) {
                String val = value;
                if (!f.equals(SaxonOutputKeys.ITEM_SEPARATOR) && !f.equals(SaxonOutputKeys.NEWLINE)) {
                    val = Whitespace.trim(val);
                }
                serializationAttributes.put(f, val);
            } else {
                String attributeURI = attName.getURI();
                if ("".equals(attributeURI) ||
                        NamespaceConstant.XSLT.equals(attributeURI) ||
                        NamespaceConstant.SAXON.equals(attributeURI)) {
                    checkUnknownAttribute(attName);
                } else {
                    String name = '{' + attributeURI + '}' + attName.getLocalPart();
                    if (userAttributes == null) {
                        userAttributes = new HashMap<>(5);
                    }
                    userAttributes.put(name, value);
                }
            }
        }
        if (nameAtt != null) {
            outputFormatName = makeQName(nameAtt, "XTSE1570", "name");
        }
    }

    /**
     * Get the name of the xsl:output declaration
     *
     * @return the name, as a structured QName; or null for an unnamed output declaration
     */

    public StructuredQName getFormatQName() {
        return outputFormatName;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {
        checkTopLevel("XTSE0010", false);
        checkEmpty();
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) {
        // no action
    }

    /**
     * Process the [xsl:]version attribute if there is one
     *
     * @param ns the namespace URI of the attribute required, either the XSLT namespace or ""
     */
    @Override
    protected void processVersionAttribute(String ns)  {
        version = ((StyleElement)getParent()).getEffectiveVersion();
    }

    /**
     * Validate the properties,
     * and return the values as additions to a supplied Properties object.
     *
     * @param details        the Properties object to be populated with property values
     * @param precedences    a HashMap to be populated with information about the precedence
     *                       of the property values: the key is the property name as a Clark name, the value
     *                       is a boxed integer giving the property's import precedence
     * @param thisPrecedence the precedence of thi instruction
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is found
     */

    protected void gatherOutputProperties(Properties details, HashMap<String, Integer> precedences, int thisPrecedence)
            throws XPathException {
        SerializerFactory sf = getConfiguration().getSerializerFactory();
        if (method != null) {
            if ("xml".equals(method) || "html".equals(method) ||
                    "text".equals(method) || "xhtml".equals(method) ||
                    "json".equals(method) || "adaptive".equals(method)) {
                checkAndPut(sf, OutputKeys.METHOD, method, details, precedences, thisPrecedence);
                //details.put(OutputKeys.METHOD, method);
            } else {
                String[] parts;
                try {
                    parts = NameChecker.getQNameParts(method);
                    String prefix = parts[0];
                    if (prefix.isEmpty()) {
                        compileError("method must be xml, html, xhtml, text, json, adaptive, or a prefixed name", "XTSE1570");
                    } else {
                        String uri = getURIForPrefix(prefix, false);
                        if (uri == null) {
                            undeclaredNamespaceError(prefix, "XTSE0280", "method");
                        }
                        checkAndPut(sf, OutputKeys.METHOD, '{' + uri + '}' + parts[1], details, precedences, thisPrecedence);
                        //details.put(OutputKeys.METHOD, '{' + uri + '}' + parts[1] );
                    }
                } catch (QNameException e) {
                    compileError("Invalid method name. " + e.getMessage(), "XTSE1570");
                }
            }
        }

        for (Map.Entry<String, String> entry : serializationAttributes.entrySet()) {
            checkAndPut(sf, entry.getKey(), entry.getValue(), details, precedences, thisPrecedence);
        }

        if (serializationAttributes.containsKey(SaxonOutputKeys.NEXT_IN_CHAIN)) {
            checkAndPut(sf, SaxonOutputKeys.NEXT_IN_CHAIN_BASE_URI, getSystemId(), details, precedences, thisPrecedence);
        }

        if (useCharacterMaps != null) {
            String s = prepareCharacterMaps(this, useCharacterMaps, details);
            details.setProperty(SaxonOutputKeys.USE_CHARACTER_MAPS, s);
        }

        // deal with user-defined attributes

        if (userAttributes != null) {
            for (Map.Entry<String, String> e : userAttributes.entrySet()) {
                details.setProperty(e.getKey(), e.getValue());
            }
        }

    }

    /**
     * Add an output property to the list of properties after checking that it is consistent
     * with other properties
     *
     * @param property       the name of the property in Clark format
     * @param value          the value of the property
     * @param props          the list of properties to be updated
     * @param precedences    the import precedence of each property
     * @param thisPrecedence the import precedence of the declaration containing this value
     * @throws net.sf.saxon.trans.XPathException
     *          if an error occurs
     */

    private void checkAndPut(SerializerFactory sf, String property, String value, Properties props,
                             HashMap<String, Integer> precedences, int thisPrecedence) {
        try {
            if (isListOfNames(property)) {
                boolean useDefaultNS = !property.equals(SaxonOutputKeys.ATTRIBUTE_ORDER);
                boolean allowStar = property.equals(SaxonOutputKeys.ATTRIBUTE_ORDER);
                value = SaxonOutputKeys.parseListOfNodeNames(
                        value, this, useDefaultNS, false, allowStar, "XTSE0280");
            }
            if (isQName(property) && value.contains(":")) {
                value = ResolveQName.resolveQName(value, this).getEQName();
            }
            value = sf.checkOutputProperty(property, value);
        } catch (XPathException err) {
            String code = property.equals("method") ? "XTSE1570" : "XTSE0020";
            if (property.contains("{")) {
                compileError(err.getMessage(), code);
            } else {
                compileErrorInAttribute(err.getMessage(), code, property);
            }
            return;
        }

        String old = props.getProperty(property);
        if (old == null) {
            props.setProperty(property, value);
            precedences.put(property, thisPrecedence);
        } else if (old.equals(value)) {
            // do nothing
        } else if (isListOfNames(property)) {
            props.setProperty(property, old + " " + value);
            precedences.put(property, thisPrecedence);
        } else {
            Integer oldPrec = precedences.get(property);
            if (oldPrec == null) {
                return;    // shouldn't happen but ignore it
            }
            if (oldPrec > thisPrecedence) {
                // ignore this value, the other has higher precedence
            } else if (oldPrec == thisPrecedence) {
                compileError("Conflicting values for output property " + property, "XTSE1560");
            } else {
                // this has higher precedence: can't happen
                throw new IllegalStateException("Output properties must be processed in decreasing precedence order");
            }
        }
    }

    private static boolean isListOfNames(String property) {
        return property.equals(OutputKeys.CDATA_SECTION_ELEMENTS) ||
                property.equals(SaxonOutputKeys.SUPPRESS_INDENTATION) ||
                property.equals(SaxonOutputKeys.ATTRIBUTE_ORDER) ||
                property.equals(SaxonOutputKeys.DOUBLE_SPACE);
    }

    private static boolean isQName(String property) {
        return property.equals(OutputKeys.METHOD) ||
                property.equals(SaxonOutputKeys.JSON_NODE_OUTPUT_METHOD);
    }

    /**
     * Process the use-character-maps attribute
     *
     * @param element          the stylesheet element on which the use-character-maps attribute appears
     * @param useCharacterMaps the value of the use-character-maps attribute
     * @param details          The existing output properties
     * @return the augmented value of the use-character-maps attribute in Clark notation
     */
    public static String prepareCharacterMaps(StyleElement element,
                                              String useCharacterMaps,
                                              Properties details) {
        PrincipalStylesheetModule psm = element.getPrincipalStylesheetModule();
        String existing = details.getProperty(SaxonOutputKeys.USE_CHARACTER_MAPS);
        if (existing == null) {
            existing = "";
        }
        StringBuilder s = new StringBuilder();
        StringTokenizer st = new StringTokenizer(useCharacterMaps, " \t\n\r", false);
        while (st.hasMoreTokens()) {
            String displayname = st.nextToken();
            StructuredQName qName = element.makeQName(displayname, null, "use-character-maps");
            ComponentDeclaration decl = psm.getCharacterMap(qName);
            if (decl == null) {
                element.compileErrorInAttribute(
                    "No character-map named '" + displayname + "' has been defined", "XTSE1590", "use-character-maps");
            }
            s.append(" ").append(qName.getClarkName());
        }
        existing = s + existing;
        return existing;
    }

}

