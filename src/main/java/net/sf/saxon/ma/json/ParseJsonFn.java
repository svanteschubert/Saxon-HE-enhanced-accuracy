////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.Map;

/**
 * Implements the parse-json function, as defined in XPath 3.1
 *
 * The parsing code, and the handling of options is shared with the json-to-xml function.
 */
public class ParseJsonFn extends JsonToXMLFn {

    public static OptionsParameter OPTION_DETAILS;
    static {
        SpecificFunctionType fallbackType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.SINGLE_STRING);
        OptionsParameter parseJsonOptions = new OptionsParameter();
        parseJsonOptions.addAllowedOption("liberal", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        parseJsonOptions.addAllowedOption("duplicates", SequenceType.SINGLE_STRING, new StringValue("use-first"));
        parseJsonOptions.setAllowedValues("duplicates", "FOJS0005", "reject", "use-first", "use-last");
        parseJsonOptions.addAllowedOption("escape", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        parseJsonOptions.addAllowedOption("fallback", SequenceType.makeSequenceType(fallbackType, StaticProperty.EXACTLY_ONE), null);
        OPTION_DETAILS = parseJsonOptions;
    }


    /**
     * Parse the JSON string according to supplied options
     *
     * @param input   JSON input string
     * @param options options for the conversion as a map of xs:string : value pairs
     * @param context XPath evaluation context
     * @return the result of the parsing, as an XML element
     * @throws XPathException if the syntax of the input is incorrect
     */
    @Override
    protected Item eval(String input, MapItem options, XPathContext context) throws XPathException {
        Map<String, Sequence> checkedOptions = null;
        if (options != null) {
            checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context);
        }
        return parse(input, checkedOptions, context);
    }

    /**
     * Parse the JSON string according to supplied options
     *
     * @param input   JSON input string
     * @param options options for the conversion as a map of xs:string : value pairs
     * @param context XPath evaluation context
     * @return the result of the parsing, as an XML element
     * @throws XPathException if the syntax of the input is incorrect
     */

    public static Item parse(String input, Map<String, Sequence> options, XPathContext context) throws XPathException {
        JsonParser parser = new JsonParser();
        int flags = 0;
        if (options != null) {
            flags = JsonParser.getFlags(options, context, false);
        }
        JsonHandlerMap handler = new JsonHandlerMap(context, flags);
        if ((flags & JsonParser.DUPLICATES_RETAINED) != 0) {
            throw new XPathException("parse-json: duplicates=retain is not allowed", "FOJS0005");
        }
        if ((flags & JsonParser.DUPLICATES_SPECIFIED) == 0) {
            flags |= JsonParser.DUPLICATES_FIRST;
        }
        if (options != null) {
            handler.setFallbackFunction(options, context);
        }
        parser.parse(input, flags, handler, context);
        return handler.getResult().head();
    }


}

// Copyright (c) 2011-2020 Saxonica Limited
