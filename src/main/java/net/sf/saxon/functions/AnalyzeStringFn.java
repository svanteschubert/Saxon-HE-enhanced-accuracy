////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.ComplexContentOutputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Loc;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.*;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.SchemaType;
import net.sf.saxon.type.SimpleType;
import net.sf.saxon.type.Untyped;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * Implements the fn:analyze-string function defined in XPath 3.0.
 */
public class AnalyzeStringFn extends RegexFunction {

    private NodeName resultName;
    private NodeName nonMatchName;
    private NodeName matchName;
    private NodeName groupName;
    private NodeName groupNrName;

    private SchemaType resultType = Untyped.getInstance();
    private SchemaType nonMatchType = Untyped.getInstance();
    private SchemaType matchType = Untyped.getInstance();
    private SchemaType groupType = Untyped.getInstance();
    private SimpleType groupNrType = BuiltInAtomicType.UNTYPED_ATOMIC;

    @Override
    protected boolean allowRegexMatchingEmptyString() {
        return false;
    }

    private synchronized void init(Configuration config, boolean schemaAware) throws XPathException {
        resultName = new FingerprintedQName("", NamespaceConstant.FN, "analyze-string-result");
        nonMatchName = new FingerprintedQName("", NamespaceConstant.FN, "non-match");
        matchName = new FingerprintedQName("", NamespaceConstant.FN, "match");
        groupName = new FingerprintedQName("", NamespaceConstant.FN, "group");
        groupNrName = new NoNamespaceName("nr");

        if (schemaAware) {
            resultType = config.getSchemaType(new StructuredQName("", NamespaceConstant.FN, "analyze-string-result-type"));
            nonMatchType = BuiltInAtomicType.STRING;
            matchType = config.getSchemaType(new StructuredQName("", NamespaceConstant.FN, "match-type"));
            groupType = config.getSchemaType(new StructuredQName("", NamespaceConstant.FN, "group-type"));
            groupNrType = BuiltInAtomicType.POSITIVE_INTEGER;
            if (resultType == null || matchType == null || groupType == null) {
                throw new XPathException("Schema for analyze-string has not been successfully loaded");
            }
        }
    }

    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as Sequences
     * @return the result of the evaluation, in the form of a Sequence
     * @throws net.sf.saxon.trans.XPathException
     *          if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public NodeInfo call(XPathContext context, Sequence[] arguments) throws XPathException {
        Item inputItem = arguments[0].head();
        CharSequence input;
        if (inputItem == null) {
            input = "";
        } else {
            input = inputItem.getStringValueCS();
        }
        RegularExpression re = getRegularExpression(arguments);
        RegexIterator iter = re.analyze(input);

        if (resultName == null) {
            boolean schemaAware = context.getController().getExecutable().isSchemaAware();
            Configuration config = context.getConfiguration();
            //noinspection SynchronizationOnLocalVariableOrMethodParameter
            synchronized (config) {
                if (schemaAware && !config.isSchemaAvailable(NamespaceConstant.FN)) {
                    InputStream inputStream = Configuration.locateResource("xpath-functions.scm", new ArrayList<>(), new ArrayList<>());
                    if (inputStream == null) {
                        throw new XPathException("Failed to load xpath-functions.scm from the classpath");
                    }
                    InputSource is = new InputSource(inputStream);
                    if (config.isTiming()) {
                        config.getLogger().info("Loading schema from resources for: " + NamespaceConstant.FN);
                    }
                    config.addSchemaSource(new SAXSource(is));
                }
            }
            init(context.getConfiguration(), schemaAware);
        }

        final Builder builder = context.getController().makeBuilder();
        final ComplexContentOutputter out = new ComplexContentOutputter(builder);
        builder.setBaseURI(getStaticBaseUriString());
        out.open();
        out.startElement(resultName, resultType, Loc.NONE, ReceiverOption.NONE);
        out.startContent();
        Item item;
        while ((item = iter.next()) != null) {
            if (iter.isMatching()) {
                out.startElement(matchName, matchType, Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                iter.processMatchingSubstring(new RegexIterator.MatchHandler() {
                    @Override
                    public void characters(CharSequence s) throws XPathException {
                        out.characters(s, Loc.NONE, ReceiverOption.NONE);
                    }

                    @Override
                    public void onGroupStart(int groupNumber) throws XPathException {
                        out.startElement(groupName, groupType,
                                         Loc.NONE, ReceiverOption.NONE);
                        out.attribute(groupNrName, groupNrType, "" + groupNumber,
                                      Loc.NONE, ReceiverOption.NONE);
                        out.startContent();
                    }

                    @Override
                    public void onGroupEnd(int groupNumber) throws XPathException {
                        out.endElement();
                    }
                });
                out.endElement();
            } else {
                out.startElement(nonMatchName, nonMatchType, Loc.NONE, ReceiverOption.NONE);
                out.startContent();
                out.characters(item.getStringValueCS(), Loc.NONE, ReceiverOption.NONE);
                out.endElement();
            }
        }

        out.endElement();
        out.close();
        return builder.getCurrentRoot();

    }

}

// Copyright (c) 2018-2020 Saxonica Limited
