////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.Sender;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.parser.RetainedStaticContext;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.Validation;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.trans.XsltController;
import net.sf.saxon.tree.tiny.TinyBuilder;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implement the saxon:doc() function - a variant of the fn:doc function with
 * a second argument to supply option parameters. Used to underpin the xsl:source-document
 * instruction with streamable="no".
 */

public class Doc_2 extends SystemFunction implements Callable {

    public Doc_2() {
    }

    public static OptionsParameter makeOptionsParameter() {
        SequenceType listOfQNames = SequenceType.makeSequenceType(BuiltInAtomicType.QNAME, StaticProperty.ALLOWS_ZERO_OR_MORE);
        OptionsParameter op = new OptionsParameter();
        op.addAllowedOption("base-uri", SequenceType.SINGLE_STRING);
        op.addAllowedOption("validation", SequenceType.SINGLE_STRING);
        op.setAllowedValues("validation", "SXZZ0001", "strict", "lax", "preserve", "strip", "skip");
        op.addAllowedOption("type", SequenceType.SINGLE_QNAME);
        op.addAllowedOption("strip-space", SequenceType.SINGLE_STRING);
        op.setAllowedValues("strip-space", "SXZZ0001", "none", "all", "ignorable", "package-defined", "default");
        op.addAllowedOption("stable", SequenceType.SINGLE_BOOLEAN);
        op.addAllowedOption("dtd-validation", SequenceType.SINGLE_BOOLEAN);
        op.addAllowedOption("accumulators", listOfQNames);
        op.addAllowedOption("use-xsi-schema-location", SequenceType.SINGLE_BOOLEAN);
        return op;
    }


    public static ParseOptions setParseOptions(
            RetainedStaticContext rsc, Map<String, Sequence> checkedOptions, XPathContext context) throws XPathException {
        ParseOptions result = new ParseOptions(context.getConfiguration().getParseOptions());

        Sequence value = checkedOptions.get("validation");
        if (value != null) {
            String valStr = value.head().getStringValue();
            if ("skip".equals(valStr)) {
                valStr = "strip";
            }
            int v = Validation.getCode(valStr);
            if (v == Validation.INVALID) {
               throw new XPathException("Invalid validation value " + valStr, "SXZZ0002");
            }
            result.setSchemaValidationMode(v);
        }
        value = checkedOptions.get("type");
        if (value != null) {
            QNameValue qval = (QNameValue) value.head();
            result.setTopLevelType(context.getConfiguration().getSchemaType(qval.getStructuredQName()));
            result.setSchemaValidationMode(Validation.BY_TYPE);
        }
        value = checkedOptions.get("strip-space");
        if (value != null) {
            String s = value.head().getStringValue();
            switch (s) {
                case "all":
                    result.setSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance());
                    break;
                case "none":
                    result.setSpaceStrippingRule(NoElementsSpaceStrippingRule.getInstance());
                    break;
                case "ignorable":
                    result.setSpaceStrippingRule(IgnorableSpaceStrippingRule.getInstance());
                    break;
                case "package-defined":
                case "default":
                    PackageData data = rsc.getPackageData();
                    if (data instanceof StylesheetPackage) {
                        result.setSpaceStrippingRule(((StylesheetPackage) data).getSpaceStrippingRule());
                    }
                    break;
            }
        }
        value = checkedOptions.get("dtd-validation");
        if (value != null) {
            result.setDTDValidationMode(((BooleanValue)value.head()).getBooleanValue() ? Validation.STRICT : Validation.SKIP);
        }
        value = checkedOptions.get("accumulators");
        if (value != null) {
            AccumulatorRegistry reg = rsc.getPackageData().getAccumulatorRegistry();
            Set<Accumulator> accumulators = new HashSet<>();
            SequenceIterator iter = value.iterate();

            Item it;
            while ((it = iter.next()) != null) {
                QNameValue name = (QNameValue)it;
                Accumulator acc = reg.getAccumulator(name.getStructuredQName());
                accumulators.add(acc);
            }
            result.setApplicableAccumulators(accumulators);
        }
        value = checkedOptions.get("use-xsi-schema-location");
        if (value != null) {
            result.setUseXsiSchemaLocation(((BooleanValue) value.head()).getBooleanValue());
        }
        return result;
    }


    /**
     * Evaluate the expression
     *
     * @param context   the dynamic evaluation context
     * @param arguments the values of the arguments, supplied as SequenceIterators
     * @return the result of the evaluation, in the form of a SequenceIterator
     * @throws XPathException if a dynamic error occurs during the evaluation of the expression
     */
    @Override
    public ZeroOrOne<NodeInfo> call(XPathContext context, Sequence[] arguments) throws XPathException {
        AtomicValue hrefVal = (AtomicValue) arguments[0].head();
        if (hrefVal == null) {
            return ZeroOrOne.empty();
        }
        String href = hrefVal.getStringValue();
        Item param = arguments[1].head();
        Map<String, Sequence> checkedOptions =
                getDetails().optionDetails.processSuppliedOptions((MapItem) param, context);
        ParseOptions parseOptions = setParseOptions(getRetainedStaticContext(), checkedOptions, context);

        NodeInfo item = fetch(href, parseOptions, context).getRootNode();
        if (item == null) {
            // we failed to read the document
            throw new XPathException("Failed to load document " + href, "FODC0002", context);
        }
        Controller controller = context.getController();
        if (controller instanceof XsltController) {
            ((XsltController) controller).getAccumulatorManager().setApplicableAccumulators(
                    item.getTreeInfo(), parseOptions.getApplicableAccumulators()
            );
        }
        return new ZeroOrOne<>(item);
    }

    private TreeInfo fetch(String href, ParseOptions options, XPathContext context) throws XPathException {
        // Get a Source from the URIResolver
        Configuration config = context.getConfiguration();
        Controller controller = context.getController();
        URI abs;
        try {
            abs = ResolveURI.makeAbsolute(href, getStaticBaseUriString());
        } catch (URISyntaxException e) {
            throw new XPathException("Invalid URI supplied to saxon:doc - " + e.getMessage(), "FODC0002");
        }
        Source source = config.getSourceResolver().resolveSource(new StreamSource(abs.toASCIIString()), config);

        TreeInfo newdoc;
        if (source instanceof NodeInfo || source instanceof DOMSource) {
            NodeInfo startNode = controller.prepareInputTree(source);
            newdoc = startNode.getTreeInfo();
        } else {
            Builder b = controller.makeBuilder();
            if (b instanceof TinyBuilder) {
                ((TinyBuilder) b).setStatistics(config.getTreeStatistics().SOURCE_DOCUMENT_STATISTICS);
            }
            b.setPipelineConfiguration(b.getPipelineConfiguration());
            try {
                Sender.send(source, b, options);
                newdoc = b.getCurrentRoot().getTreeInfo();
                b.reset();
            } finally {
                if (options.isPleaseCloseAfterUse()) {
                    ParseOptions.close(source);
                }
            }
        }
        return newdoc;
    }

    /**
     * Get the static properties of this expression (other than its type). The result is
     * bit-signficant. These properties are used for optimizations. In general, if
     * property bit is set, it is true, but if it is unset, the value is unknown.
     *
     * @param arguments the expressions supplied as arguments to the function
     */

    @Override
    public int getSpecialProperties(Expression[] arguments) {
        return StaticProperty.ORDERED_NODESET |
                StaticProperty.PEER_NODESET |
                StaticProperty.NO_NODES_NEWLY_CREATED |
                StaticProperty.SINGLE_DOCUMENT_NODESET;
        // Declaring it as a peer node-set expression avoids sorting of expressions such as
        // doc(XXX)/a/b/c
        // The doc() function might appear to be creative: but it isn't, because multiple calls
        // with the same arguments will produce identical results.
    }


}


