////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.s9api;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.UnparsedTextFunction;
import net.sf.saxon.ma.json.ParseJsonFn;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.z.IntPredicateProxy;
import net.sf.saxon.z.IntSetPredicate;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@code JsonBuilder} is used to parse JSON documents
 * @since 11
 */

public class JsonBuilder {

    private Configuration config;
    private boolean liberal;

    protected JsonBuilder(Configuration config) {
        this.config = config;
    }

    /**
     * Say whether JSON parsing should be liberal. When this option is selected, the JSON parser
     * tolerates a number of deviations from the JSON specification.
     * <p>Specifically, liberal mode currently allows:</p>
     * <ul>
     *     <li>Trailing commas in array and object literals</li>
     *     <li>More flexibility for numeric literals (for example, exponential notation and leading '+')</li>
     *     <li>Invalid escape sequences in character strings</li>
     *     <li>Unquoted strings, provided they follow the syntax of an XML NMTOKEN</li>
     * </ul>
     * @param liberal - true if parsing is to be liberal, false if it is to be strict
     */

    public void setLiberal(boolean liberal) {
        this.liberal = liberal;
    }

    /**
     * Ask whether JSON parsing should be liberal. When this option is selected, the JSON parser
     * tolerates a number of deviations from the JSON specification, for example trailing commas
     * in an array or object literal.
     *
     * @return true if parsing is to be liberal, false if it is to be strict
     */

    public boolean isLiberal() {
        return liberal;
    }

    /**
     * Parse a JSON input string, supplied as a {@code Reader} and construct an {@code XdmValue} representing
     * its content. The {@link XdmValue} will usually be an {@link XdmMap} or {@link XdmArray}.
     * If the JSON input consists simply of a string, number, or boolean, then the result will be an
     * {@code XdmAtomicValue}. If the input is the JSON string "null", the method returns an
     * {@code XdmEmptySequence}.
     *
     * <p>The method follows the rules of the {@code fn:parse-json()} function when called with
     * default options.</p>
     *
     * @param jsonReader supplies the input JSON as a stream of characters
     * @return the result of parsing the JSON input. This will be an {@code XdmItem} in all cases except
     * where the input JSON text is the string "null".
     * @throws SaxonApiException if the JSON input is invalid, or if the reader throws an IOException
     * @since 11
     */

    public XdmValue parseJson(Reader jsonReader) throws SaxonApiException {
        try {
            XPathContext context = new Controller(config).newXPathContext();
            IntPredicateProxy checker = IntSetPredicate.ALWAYS_TRUE;
            UnicodeString content = UnparsedTextFunction.readFile(checker, jsonReader);
            Map<String, GroundedValue> options = new HashMap<>();
            options.put("liberal", BooleanValue.get(liberal));
            options.put("escape", BooleanValue.TRUE);
            Item result = ParseJsonFn.parse(content.toString(), options, context);
            return XdmValue.wrap(result);
        } catch (XPathException | IOException e) {
            throw new SaxonApiException(e);
        }
    }

    /**
     * Parse a JSON input string, supplied as a {@code String} and construct an {@code XdmValue} representing
     * its content. The {@code XdmValue} will usually be an {@code XdmMap} or {@code XdmArray}.
     * If the JSON input consists simply of a string, number, or boolean, then the result will be an
     * {@code XdmAtomicValue}. If the input is the JSON string "null", the method returns an
     * {@code XdmEmptySequence}.
     *
     * <p>The method follows the rules of the {@code fn:parse-json()} function when called with
     * default options.</p>
     *
     * @param json supplies the input JSON as a string
     * @return the result of parsing the JSON input
     * @throws SaxonApiException if the JSON input is invalid
     * @since 11
     */

    public XdmValue parseJson(String json) throws SaxonApiException {
        return parseJson(new StringReader(json));
    }

}

