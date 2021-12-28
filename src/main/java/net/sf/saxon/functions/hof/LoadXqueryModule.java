////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.functions.hof;

import net.sf.saxon.Configuration;
import net.sf.saxon.Controller;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.GlobalContextRequirement;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.functions.OptionsParameter;
import net.sf.saxon.functions.SystemFunction;
import net.sf.saxon.lib.ModuleURIResolver;
import net.sf.saxon.lib.StandardModuleURIResolver;
import net.sf.saxon.ma.map.*;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.query.*;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AtomicIterator;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.*;

import javax.xml.transform.stream.StreamSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * This class implements the function load-xquery-module(), which is a standard function in XPath 3.1.
 * It is classified as a higher-order function and therefore requires Saxon-PE or higher.
 */
public class LoadXqueryModule extends SystemFunction implements Callable {

    public static OptionsParameter makeOptionsParameter() {
        OptionsParameter op = new OptionsParameter();
        op.addAllowedOption("xquery-version", SequenceType.SINGLE_DECIMAL);
        op.addAllowedOption("location-hints", SequenceType.STRING_SEQUENCE);
        op.addAllowedOption("context-item", SequenceType.OPTIONAL_ITEM);
        op.addAllowedOption("variables", SequenceType.makeSequenceType(new MapType(BuiltInAtomicType.QNAME, SequenceType.ANY_SEQUENCE), StaticProperty.EXACTLY_ONE)); // standard type?
        op.addAllowedOption("vendor-options", SequenceType.makeSequenceType(new MapType(BuiltInAtomicType.QNAME, SequenceType.ANY_SEQUENCE), StaticProperty.EXACTLY_ONE));
        return op;
    }

    /**
     * Prepare an XPathContext object for evaluating the function
     *
     * @param callingContext the XPathContext of the function calling expression
     * @param originator
     * @return a suitable context for evaluating the function (which may or may
     * not be the same as the caller's context)
     */
    @Override
    public XPathContext makeNewContext(XPathContext callingContext, ContextOriginator originator) {
        return callingContext;
    }

    /**
     * Invoke the function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied
     * @return the result of invoking the function
     * @throws net.sf.saxon.trans.XPathException if a dynamic error occurs within the function
     */

    @Override
    public MapItem call(XPathContext context, Sequence[] args) throws XPathException {
        Sequence xqueryVersionOption = null;
        Sequence locationHintsOption = null;
        Sequence variablesOption = null;
        Sequence contextItemOption = null;
        Sequence vendorOptionsOption = null;
        if (args.length == 2) {
            MapItem suppliedOptions = (MapItem) args[1].head();
            Map<String, Sequence> checkedOptions = getDetails().optionDetails.processSuppliedOptions(suppliedOptions, context);
            xqueryVersionOption = checkedOptions.get("xquery-version");
            if (xqueryVersionOption != null &&
                    ((DecimalValue)xqueryVersionOption.head()).getDoubleValue()*10 > 31) {
                throw new XPathException("No XQuery version " + xqueryVersionOption + " processor is available", "FOQM0006");
            }
            locationHintsOption = checkedOptions.get("location-hints");
            variablesOption = checkedOptions.get("variables");
            contextItemOption = checkedOptions.get("context-item");
            vendorOptionsOption = checkedOptions.get("vendor-options");

        }

        int qv = 31;
        if (xqueryVersionOption != null) {
            BigDecimal decimalVn = ((DecimalValue) xqueryVersionOption.head()).getDecimalValue();
            if (decimalVn.equals(new BigDecimal("1.0")) || decimalVn.equals(new BigDecimal("3.0")) || decimalVn.equals(new BigDecimal("3.1"))) {
                qv = decimalVn.multiply(BigDecimal.TEN).intValue();
            } else {
                throw new XPathException("Unsupported XQuery version " + decimalVn, "FOQM0006");
            }
        }

        String moduleUri = args[0].head().getStringValue();
        if (moduleUri.isEmpty()) {
            throw new XPathException("First argument of fn:load-xquery-module() must not be a zero length string", "FOQM0001");
        }
        List<String> locationHints = new ArrayList<>(); // location hints are currently ignored by QT3TestDriver?
        if (locationHintsOption != null) {
            SequenceIterator iterator = locationHintsOption.iterate();
            Item hint;
            while ((hint = iterator.next()) != null) {
                locationHints.add(hint.getStringValue());
            }
        }

        // Set the vendor options (configuration features) -- at the moment none supported
        /*if (vendorOptionsOption != null) {
            MapItem vendorOptions = (MapItem) options.get(new StringValue("vendor-options")).head();
        }*/

        Configuration config = context.getConfiguration();
        StaticQueryContext staticQueryContext = config.newStaticQueryContext();
        ModuleURIResolver moduleURIResolver = config.getModuleURIResolver();
        if (moduleURIResolver == null) {
            moduleURIResolver = new StandardModuleURIResolver(config);
        }
        staticQueryContext.setModuleURIResolver(moduleURIResolver);
        String baseURI = getRetainedStaticContext().getStaticBaseUriString();
        staticQueryContext.setBaseURI(baseURI);
        StreamSource[] streamSources;
        try {
            String[] hints = locationHints.toArray(new String[0]);
            streamSources = staticQueryContext.getModuleURIResolver().resolve(moduleUri, baseURI, hints);
            if (streamSources == null) {
                streamSources = new StandardModuleURIResolver(config).resolve(moduleUri, baseURI, hints);
            }
        } catch (XPathException e) {
            e.maybeSetErrorCode("FOQM0002");
            throw e;
        }
        if (streamSources.length == 0) {
            throw new XPathException("No library module found with specified target namespace " + moduleUri, "FOQM0002");
        }


        try {
            // Note: Location hints other than the first are ignored
            String sourceQuery = QueryReader.readSourceQuery(streamSources[0], config.getValidCharacterChecker() );
            staticQueryContext.compileLibrary(sourceQuery);
        } catch (XPathException e) {
            throw new XPathException(e.getMessage(), "FOQM0003"); // catch when module is invalid
        }
        QueryLibrary lib = staticQueryContext.getCompiledLibrary(moduleUri);
        if (lib == null) {
            throw new XPathException("The library module located does not have the expected namespace " + moduleUri, "FOQM0002");
        }
        QueryModule main = new QueryModule(staticQueryContext); // module to be loaded is a library module not a main module
        // so use alternative constructor?
        main.setPackageData(lib.getPackageData());
        main.setExecutable(lib.getExecutable());
        lib.link(main);
        XQueryExpression xqe = new XQueryExpression(new ContextItemExpression(), main, false);
        DynamicQueryContext dqc = new DynamicQueryContext(context.getConfiguration());

        // Get the external variables and set parameters on DynamicQueryContext dqc
        if (variablesOption != null) {
            MapItem extVariables = (MapItem) variablesOption.head();
            AtomicIterator iterator = extVariables.keys();
            AtomicValue key;
            while ((key = iterator.next()) != null) {
                dqc.setParameter(((QNameValue) key).getStructuredQName(), ((Sequence) extVariables.get(key)).materialize());
            }
        }

        // Get the context item supplied, and set it on the new Controller
        if (contextItemOption != null) {
            Item contextItem = contextItemOption.head();
            GlobalContextRequirement gcr = main.getExecutable().getGlobalContextRequirement();
            if (gcr != null) {
                ItemType req = gcr.getRequiredItemType();
                if (req != null && !req.matches(contextItem, config.getTypeHierarchy())) {
                    throw new XPathException("Required context item type is " + req, "FOQM0005");
                }
            }
            dqc.setContextItem(contextItemOption.head());
        }

        Controller newController = xqe.newController(dqc);

        XPathContext newContext = newController.newXPathContext();

        // Evaluate the global variables, and add values to the result.

        HashTrieMap variablesMap = new HashTrieMap();
        for (GlobalVariable var : lib.getGlobalVariables()) {
            GroundedValue value;
            QNameValue qNameValue = new QNameValue(var.getVariableQName(), BuiltInAtomicType.QNAME);
            if (qNameValue.getNamespaceURI().equals(moduleUri)) {
                try {
                    value = var.evaluateVariable(newContext);
                } catch (XPathException e) {
                    e.setIsGlobalError(false);  // to make it catchable
                    if (e.getErrorCodeLocalPart().equals("XPTY0004")) {
                        throw new XPathException(e.getMessage(), "FOQM0005"); // catches when external variables have wrong type
                    } else {
                        throw e;
                    }
                }
                variablesMap = variablesMap.addEntry(qNameValue, value);
            }
        }
        // Add functions to the result.
        HashTrieMap functionsMap = new HashTrieMap();
        XQueryFunctionLibrary functionLib = lib.getGlobalFunctionLibrary();
        Iterator<XQueryFunction> functionIterator = functionLib.getFunctionDefinitions();
        ExportAgent agent = out -> {
            XPathException err = new XPathException(
                    "Cannot export a stylesheet that statically incorporates XQuery functions",
                    SaxonErrorCode.SXST0069);
            err.setIsStaticError(true);
            throw err;
        };
        if (functionIterator.hasNext()) {
            XQueryFunction function;
            MapItem newMap;
            QNameValue functionQName;
            while (functionIterator.hasNext()) {
                function = functionIterator.next();
                functionQName = new QNameValue(function.getFunctionName(), BuiltInAtomicType.QNAME);
                if (functionQName.getNamespaceURI().equals(moduleUri)) {
                    UserFunction userFunction = function.getUserFunction();
                    UserFunctionReference.BoundUserFunction buf =
                            new UserFunctionReference.BoundUserFunction(agent, userFunction, null, newController);
                    if (functionsMap.get(functionQName) != null) {
                        newMap = ((MapItem) functionsMap.get(functionQName)).addEntry(new Int64Value(function.getNumberOfArguments()), buf);
                    } else {
                        newMap = new SingleEntryMap(Int64Value.makeIntegerValue(function.getNumberOfArguments()), buf);
                    }
                    functionsMap = functionsMap.addEntry(functionQName, newMap);
                }
            }
        }

        DictionaryMap map = new DictionaryMap();
        map.initialPut("variables", variablesMap);
        map.initialPut("functions", functionsMap);
        return map;
    }
}

// Copyright (c) 2018-2020 Saxonica Limited
