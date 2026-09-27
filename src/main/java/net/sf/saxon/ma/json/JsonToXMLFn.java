////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.functions.UnparsedTextFunction;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.PortableNamedXNodeType;
import net.sf.saxon.pattern.qname.NamespaceQNameTest;
import net.sf.saxon.resource.EncodingDetector;
import net.sf.saxon.resource.ResourceLoader;
import net.sf.saxon.str.UnicodeBuilder;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.SpecificFunctionType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.gnode.DocumentNodeType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntPredicateProxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.Map;

/**
 * Implements the json-to-xml function defined in XSLT 3.0.
 */

public class JsonToXMLFn extends SystemFunction {

    public static OptionsParameter makeOptionsParameter(int version) {
        SpecificFunctionType fallbackType = new SpecificFunctionType(
                SequenceType.SINGLE_STRING, SequenceType.SINGLE_ATOMIC);
        SpecificFunctionType parserType = new SpecificFunctionType(
                SequenceType.SINGLE_UNTYPED_ATOMIC, SequenceType.OPTIONAL_ITEM);
        OptionsParameter jsonToXmlOptions = new OptionsParameter(version);
        jsonToXmlOptions.addAllowedOption("liberal", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        jsonToXmlOptions.addAllowedOption("duplicates", SequenceType.SINGLE_STRING, null);
        jsonToXmlOptions.setAllowedValues("duplicates", "FOJS0005", "reject", "use-first", "retain");
        jsonToXmlOptions.addAllowedOption("validate", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        jsonToXmlOptions.addAllowedOption("escape", SequenceType.SINGLE_BOOLEAN, BooleanValue.FALSE);
        jsonToXmlOptions.addAllowedOption("fallback", SequenceType.one(fallbackType), null);
        jsonToXmlOptions.addAllowedOption("number-parser", SequenceType.one(parserType), null);
        return jsonToXmlOptions;
    }

    public static ItemType RESULT_TYPE = new DocumentNodeType(
            new PortableNamedXNodeType(Type.ELEMENT, new NamespaceQNameTest(NamespaceUri.FN)));

    public static StringValue decodeBinary(XPathContext context, byte[] binaryValue)
            throws XPathException, IOException {
        InputStream inputStream = new ByteArrayInputStream(binaryValue);

        String encoding = EncodingDetector.inferStreamEncoding(inputStream, "UTF-8", null);

        UnicodeBuilder uBuilder = new UnicodeBuilder();
        Reader reader = ResourceLoader.getReaderFromStream(inputStream, encoding, false);
        IntPredicateProxy checker = context.getConfiguration().getValidCharacterChecker();
        UnparsedTextFunction.readFile(checker, reader, false, uBuilder);
        return new StringValue(uBuilder.toUnicodeString());
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
            return EmptySequence.INSTANCE;
        }

        UnicodeString input = arg0.getUnicodeStringValue();
        MapItem options = null;
        if (getArity() == 2) {
            options = (MapItem) arguments[1].head();
        }
        Item result = eval(input, options, context);
        return SequenceTool.itemOrEmpty(result);
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
    protected Item eval(UnicodeString input, MapItem options, XPathContext context) throws XPathException {
        JsonParser parser = new JsonParser();
        int flags = 0;
        Map<String, GroundedValue> checkedOptions = null;
        if (options != null) {
            int version = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            checkedOptions = getDetails().optionDetails.processSuppliedOptions(options, context, version);
            flags = JsonParser.getFlags(checkedOptions, true, context.getController().getExecutable().isSchemaAware());
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
        flags |= JsonParser.NUMERIC_FORMAT_RETAINED;
        JsonHandlerXML handler = new JsonHandlerXML(context, getRetainedStaticContext(), flags);
        if (options != null) {
            handler.setFallbackFunction(checkedOptions, context);
            parser.setNumberParser(checkedOptions);
        }
        parser.parse(input.codePoints(), flags, handler, context);
        return handler.getResult();
    }



}

// Copyright (c) 2011-2026 Saxonica Limited
