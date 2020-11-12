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
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.util.Map;

/**
 * Implements the json-to-xml function defined in XSLT 3.0.
 */

public class JsonToXMLFn extends SystemFunction {

    public static OptionsParameter OPTION_DETAILS;

    static{
        SpecificFunctionType fallbackType = new SpecificFunctionType(
                new SequenceType[]{SequenceType.SINGLE_STRING}, SequenceType.SINGLE_STRING);
        OptionsParameter jsonToXmlOptions = new OptionsParameter();
        jsonToXmlOptions.addAllowedOption("liberal", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        jsonToXmlOptions.addAllowedOption("duplicates", SequenceType.SINGLE_STRING, null);
        jsonToXmlOptions.setAllowedValues("duplicates", "FOJS0005", "reject", "use-first", "retain");
        jsonToXmlOptions.addAllowedOption("validate", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        jsonToXmlOptions.addAllowedOption("escape", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        jsonToXmlOptions.addAllowedOption("fallback", SequenceType.makeSequenceType(fallbackType, StaticProperty.EXACTLY_ONE), null);
        OPTION_DETAILS = jsonToXmlOptions;
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
        Item arg0 = arguments[0].head();
        if (arg0 == null) {
            return EmptySequence.getInstance();
        }
        String input = arg0.getStringValue();
        MapItem options = null;
        if (getArity() == 2) {
            options = (MapItem) arguments[1].head();
        }
        Item result = eval(input, options, context);
        return result == null ? EmptySequence.getInstance() : result;
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
    protected Item eval(String input, MapItem options, XPathContext context) throws XPathException {
        JsonParser parser = new JsonParser();
        int flags = 0;
        Map<String, Sequence> checkedOptions = null;
        if (options != null) {
            checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context);
            flags = JsonParser.getFlags(checkedOptions, context, true);
            if ((flags & JsonParser.DUPLICATES_LAST) != 0) {
                throw new XPathException("json-to-xml: duplicates=use-last is not allowed", "FOJS0005");
            }
            if ((flags & JsonParser.DUPLICATES_SPECIFIED) == 0) {
                if ((flags & JsonParser.VALIDATE) != 0) {
                    flags |= JsonParser.DUPLICATES_REJECTED;
                } else {
                    flags |= JsonParser.DUPLICATES_RETAINED;
                }
            }
        } else {
            flags = JsonParser.DUPLICATES_RETAINED;
        }
        JsonHandlerXML handler = new JsonHandlerXML(context, getStaticBaseUriString(), flags);
        if (options != null) {
            handler.setFallbackFunction(checkedOptions, context);
        }
        parser.parse(input, flags, handler, context);
        return handler.getResult();
    }



}

// Copyright (c) 2011-2020 Saxonica Limited
