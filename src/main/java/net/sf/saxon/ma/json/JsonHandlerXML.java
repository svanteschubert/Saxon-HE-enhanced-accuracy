////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StandardEntityResolver;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.SAXSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;

/**
 * Handler to generate an XML representation of JSON from a series of events
 */
public class JsonHandlerXML extends JsonHandler {

    private Outputter out;
    private Builder builder;

    private Stack<String> keys;
    private Stack<Boolean> inMap = new Stack<>();

    private boolean allowAnyTopLevel;
    public boolean validate;
    private boolean checkForDuplicates;

    private static final String SCHEMA_URI = "http://www.w3.org/2005/xpath-functions.xsd";
    private static final String JSON_NS = NamespaceConstant.FN;
    public static final String PREFIX = "";

    private NamePool namePool;
    private FingerprintedQName mapQN;
    private FingerprintedQName arrayQN;
    private FingerprintedQName stringQN;
    private FingerprintedQName numberQN;
    private FingerprintedQName booleanQN;
    private FingerprintedQName nullQN;
    private FingerprintedQName keyQN;
    private FingerprintedQName escapedQN;
    private FingerprintedQName escapedKeyQN;

    private static final Untyped UNTYPED = Untyped.getInstance();
    private static final AnySimpleType SIMPLE_TYPE = AnySimpleType.getInstance();
    private static final BuiltInAtomicType BOOLEAN_TYPE = BuiltInAtomicType.BOOLEAN;
    private static final BuiltInAtomicType STRING_TYPE = BuiltInAtomicType.STRING;

    public HashMap<String, SchemaType> types;
    private Stack<HashSet<String>> mapKeys = new Stack<>();


    /**
     * Create a QName in null namespace
     *
     * @param s the local name
     * @return the QName
     */
    private FingerprintedQName qname(String s) {
        FingerprintedQName fp = new FingerprintedQName("", "", s);
        fp.obtainFingerprint(namePool);
        return fp;
    }

    /**
     * Create a QName in the JSON namespace
     *
     * @param s the local name
     * @return the QName
     */
    private FingerprintedQName qnameNS(String s) {
        FingerprintedQName fp = new FingerprintedQName(PREFIX, JSON_NS, s);
        fp.obtainFingerprint(namePool);
        return fp;
    }

    /**
     * Make the handler to construct the XML tree representation for JSON
     *
     * @param context       the context in which the result tree is to be built
     * @param staticBaseUri the static base URI, used for the base URI of the constructed tree
     * @param flags         flags indicating the chosen options
     * @throws XPathException if initialization fails, for example because of problems loading the schema
     */
    public JsonHandlerXML(XPathContext context, String staticBaseUri, int flags) throws XPathException {
        init(context, flags);
        builder = context.getController().makeBuilder();
        builder.setSystemId(staticBaseUri);
        builder.setTiming(false);
        out = new ComplexContentOutputter(builder);
        out.open();
        out.startDocument(ReceiverOption.NONE);
    }

    /**
     * Initialise the tree builder.
     * <p>This also ensures the appropriate schema is loaded when type validation is required</p>
     *
     * @param context the context in which the result tree is to be built
     * @param flags   flags indicating the chosen options
     * @throws XPathException if anything goes wrong (for example, with getting schema information)
     */
    private void init(XPathContext context, int flags) throws XPathException {
        keys = new Stack<String>();
       /* This may not need to be a stack as there should only be at most one pre-selected key
       * However, the stack neatly indicates its empty state
       * */
        setContext(context);
        charChecker = context.getConfiguration().getValidCharacterChecker();
        escape = (flags & JsonParser.ESCAPE) != 0;
        allowAnyTopLevel = (flags & JsonParser.ALLOW_ANY_TOP_LEVEL) != 0;
        validate = (flags & JsonParser.VALIDATE) != 0;
        checkForDuplicates = validate || (flags & JsonParser.DUPLICATES_RETAINED) == 0;
        types = new HashMap<>();

        namePool = context.getConfiguration().getNamePool();
        mapQN = qnameNS("map");
        arrayQN = qnameNS("array");
        stringQN = qnameNS("string");
        numberQN = qnameNS("number");
        booleanQN = qnameNS("boolean");
        nullQN = qnameNS("null");
        keyQN = qname("key");
        escapedQN = qname("escaped");
        escapedKeyQN = qname("escaped-key");

        if (validate) {
            // Note, we do not actually perform schema validation, because we assume the XML we are generating
            // is valid. Instead, we just set type annotations "on trust", as if we were validating.
            // Currently this means we aren't detecting duplicate keys, which would cause validation to fail.
            // The spec needs clarification in this area.
            try {
                Configuration config = context.getConfiguration();
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (config) {
                    config.checkLicensedFeature(Configuration.LicenseFeature.SCHEMA_VALIDATION, "validation", -1);
                    if (!config.isSchemaAvailable(JSON_NS)) {
                        InputSource is = new StandardEntityResolver(config).resolveEntity(null, SCHEMA_URI);
                        if (config.isTiming()) {
                            config.getLogger().info("Loading a schema from resources for: " + JSON_NS);
                        }
                        config.addSchemaSource(new SAXSource(is));
                    }
                }
                String[] typeNames = {"mapType", "arrayType", "stringType", "numberType", "booleanType", "nullType",
                    "mapWithinMapType", "arrayWithinMapType", "stringWithinMapType",
                    "numberWithinMapType", "booleanWithinMapType", "nullWithinMapType"};
                for (String t : typeNames) {
                    setType(t, config.getSchemaType(new StructuredQName(PREFIX, JSON_NS, t)));
                }
            } catch (SchemaException | SAXException e) {
                throw new XPathException(e);
            }
        }
    }

    /**
     * Record a SchemaType for a particular name
     *
     * @param name the name to be used for the type, e.g. "arrayType"
     * @param st   the schema type to be used for typing such entities
     */
    public void setType(String name, SchemaType st) {
        types.put(name, st);
    }

    /**
     * Set the key to be written for the next entry in an object/map
     *
     * @param unEscaped the key for the entry (null implies no key) in unescaped form (backslashes,
     *                  if present, do not signal an escape sequence)
     * @param reEscaped the key for the entry (null implies no key) in reescaped form. In this form
     *                  special characters are represented as backslash-escaped sequences if the escape
     *                  option is yes; if escape=no, the reEscaped form is the same as the unEscaped form.
     * @return true if the key is already present in the map, false if it is not
     */
    @Override
    public boolean setKey(String unEscaped, String reEscaped) {
        this.keys.push(unEscaped);
        return checkForDuplicates && !mapKeys.peek().add(reEscaped);
    }

    /**
     * Return the complete parsed result
     *
     * @return the XML document for this JSON
     * @throws XPathException if an error occurs downstream
     */
    @Override
    public Item getResult() throws XPathException {
        out.endDocument();
        out.close();
        return builder.getCurrentRoot();
    }

    /**
     * Check whether a string contains an escape sequence
     *
     * @param literal the string to be checked
     * @return true if the string contains a backslash
     */
    private boolean containsEscape(String literal) {
        return literal.indexOf('\\') >= 0;
    }

    private boolean isInMap() {
        return !inMap.isEmpty() && inMap.peek();
    }

    /**
     * Start an element in the result tree
     *
     * @param qn       the QName of the element
     * @param typeName the string name of the type to use
     * @throws XPathException if a dynamic error occurs
     */
    private void startElement(FingerprintedQName qn, String typeName) throws XPathException {
        startElement(qn, types.get(typeName));
    }

    /**
     * Start an element in the result tree, which will have a key attribute if a key has been pre-selected
     *
     * @param qn the QName of the element
     * @param st the schema type to use when validating - null implies untyped.
     * @throws XPathException if a dynamic error occurs
     */
    private void startElement(FingerprintedQName qn, SchemaType st) throws XPathException {
        out.startElement(qn, validate && st != null ? st : UNTYPED,
                         Loc.NONE, ReceiverOption.NONE);
        if (isInMap()) {
            String k = keys.pop();
            k = reEscape(k);
            if (escape) {
                markAsEscaped(k, true);
            }
            out.attribute(keyQN, validate ? STRING_TYPE : SIMPLE_TYPE, k,
                          Loc.NONE, ReceiverOption.NONE);
        }
    }

    private void startContent() throws XPathException {
        out.startContent();
    }

    /**
     * Write characters as a text node in the tree
     *
     * @param s the string to be added
     * @throws XPathException if a dynamic error occurs
     */
    private void characters(String s) throws XPathException {
        out.characters(s, Loc.NONE, ReceiverOption.NONE);
    }

    /**
     * End the current element in the treel
     *
     * @throws XPathException if a dynamic error occurs
     */
    private void endElement() throws XPathException {
        out.endElement();
    }


    /**
     * Open a new array
     *
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public void startArray() throws XPathException {
        startElement(arrayQN, isInMap() ? "arrayWithinMapType" : "arrayType");
        inMap.push(false);
        startContent();
    }

    /**
     * Close the current array
     *
     * @throws XPathException if a dynamic error occurs
     */

    @Override
    public void endArray() throws XPathException {
        inMap.pop();
        endElement();
    }

    /**
     * Start a new object/map
     *
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void startMap() throws XPathException {
        startElement(mapQN, isInMap() ? "mapWithinMapType": "mapType");
        if (checkForDuplicates) {
            mapKeys.push(new HashSet<>());
        }
        inMap.push(true);
        startContent();
    }

    /**
     * Close the current object/map
     *
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void endMap() throws XPathException {
        inMap.pop();
        if (checkForDuplicates) {
            mapKeys.pop();
        }
        endElement();
    }

    /**
     * Write a numeric value
     *
     * @param asString the string representation of the value
     * @param asDouble the double representation of the value
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void writeNumeric(String asString, double asDouble) throws XPathException {
        startElement(numberQN, isInMap() ? "numberWithinMapType" : "numberType");
        startContent();
        characters(asString);
        endElement();
    }

    /**
     * Write a string value
     *
     * @param val the string to be written. This will be in unescaped form if unescaping was requested
     *            in the flags, otherwise it may contain JSON escape sequences
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void writeString(String val) throws XPathException {
        startElement(stringQN, isInMap() ? "stringWithinMapType" : "stringType");
        CharSequence escaped = reEscape(val);
        if (escape) {
            markAsEscaped(escaped, false);
        }
        startContent();
        characters(escaped.toString());
        endElement();
    }

 @Override
    protected void markAsEscaped(CharSequence escaped, boolean isKey) throws XPathException {
        if (containsEscape(escaped.toString()) && escape) {
            NodeName name = isKey ? escapedKeyQN : escapedQN;
            out.attribute(name, validate ? BOOLEAN_TYPE : SIMPLE_TYPE, "true",
                          Loc.NONE, ReceiverOption.NONE);
        }
    }

    /**
     * Write a boolean value
     *
     * @param value the boolean value to be written
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void writeBoolean(boolean value) throws XPathException {
        startElement(booleanQN, isInMap() ? "booleanWithinMapType" : "booleanType");
        startContent();
        characters(Boolean.toString(value));
        endElement();
    }

    /**
     * Write a null value
     *
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void writeNull() throws XPathException {
        startElement(nullQN, isInMap() ? "nullWithinMapType" : "nullType");
        startContent();
        endElement();
    }
}

// Copyright (c) 2014-2020 Saxonica Limited
