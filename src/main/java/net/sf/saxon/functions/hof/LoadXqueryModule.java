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
import net.sf.saxon.om.*;
import net.sf.saxon.query.*;
import net.sf.saxon.str.Twine8;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.LicenseException;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.AnyFunctionType;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.SequenceType;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class implements the function load-xquery-module(), which is a standard function in XPath 3.1.
 * It is classified as a higher-order function and therefore requires Saxon-PE or higher.
 */
public class LoadXqueryModule extends SystemFunction implements Callable {

    public static OptionsParameter makeOptionsParameter(int version) {
        OptionsParameter op = new OptionsParameter(version);
        op.addAllowedOption("xquery-version", SequenceType.SINGLE_DECIMAL);
        op.addAllowedOption("location-hints", SequenceType.STRING_SEQUENCE);
        op.addAllowedOption("context-item", SequenceType.OPTIONAL_ITEM);
        op.addAllowedOption("content", SequenceType.OPTIONAL_STRING);
        op.addAllowedOption("variables", SequenceType.one(new MapType(BuiltInAtomicType.QNAME, SequenceType.ANY_SEQUENCE))); // standard type?
        op.addAllowedOption("vendor-options", SequenceType.one(new MapType(BuiltInAtomicType.QNAME, SequenceType.ANY_SEQUENCE)));
        return op;
    }


    public static ItemType RESULT_TYPE = RecordType.nonExtensible(
            new RecordType.Field("variables",
                                 SequenceType.one(
                                         //map(xs:QName, item()*)
                                         new MapType(BuiltInAtomicType.QNAME,
                                                     SequenceType.ANY_SEQUENCE)), false),
            new RecordType.Field("functions",
                                 SequenceType.one(
                                         //map(xs:QName, map(xs:integer, function(*)))
                                         new MapType(BuiltInAtomicType.QNAME,
                                                     SequenceType.one(
                                                             new MapType(BuiltInAtomicType.INTEGER,
                                                                         SequenceType.one(AnyFunctionType.INSTANCE))))), false)
    );


    /**
     * Prepare an XPathContext object for evaluating the function
     *
     * @param callingContext the XPathContext of the function calling expression
     * @param originator identifies the location of the caller for diagnostics
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

        // Requires EE
        try {
            context.getConfiguration().checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XQUERY, "fn:load-xquery-module", -1);
        } catch (LicenseException e) {
            throw new XPathException(e.getMessage(), "FOQM0006");
        }

        Sequence xqueryVersionOption = null;
        Sequence locationHintsOption = null;
        Sequence variablesOption = null;
        Sequence contextItemOption = null;
        Sequence vendorOptionsOption = null;
        Sequence contentOption = null;
        if (args.length == 2) {
            MapItem suppliedOptions = (MapItem) args[1].head();
            int hostVersion = getRetainedStaticContext().getPackageData().getHostLanguageVersion();
            if (suppliedOptions == null) {
                suppliedOptions = EmptyMap.INSTANCE_40;
            }
            Map<String, GroundedValue> checkedOptions = getDetails().optionDetails.processSuppliedOptions(suppliedOptions, context, hostVersion);
            xqueryVersionOption = checkedOptions.get("xquery-version");
            if (xqueryVersionOption != null) {
                double vn = ((DecimalValue) xqueryVersionOption.head()).getDoubleValue();
                if (vn * 10 > 40) {
                    throw new XPathException("No XQuery version " + vn + " processor is available", "FOQM0006");
                }
            }
            locationHintsOption = checkedOptions.get("location-hints");
            variablesOption = checkedOptions.get("variables");
            contextItemOption = checkedOptions.get("context-item");
            contentOption = checkedOptions.get("content");
            vendorOptionsOption = checkedOptions.get("vendor-options");

        }

        int qv = 31;
        if (xqueryVersionOption != null) {
            BigDecimal decimalVn = ((DecimalValue) xqueryVersionOption.head()).getDecimalValue();
            if (decimalVn.compareTo(new BigDecimal("1.0")) == 0
                    || decimalVn.compareTo(new BigDecimal("3.0")) == 0
                    || decimalVn.compareTo(new BigDecimal("3.1")) == 0
                    || decimalVn.compareTo(new BigDecimal("4.0")) == 0) {
                qv = decimalVn.multiply(BigDecimal.TEN).intValue();
            } else {
                throw new XPathException("Unsupported XQuery version " + decimalVn, "FOQM0006");
            }
        }

        NamespaceUri moduleUri = NamespaceUri.of(args[0].head().getStringValue());
        if (moduleUri.isEmpty()) {
            throw new XPathException("First argument of fn:load-xquery-module() must not be a zero length string", "FOQM0001");
        }

        StreamSource[] streamSources = null;

        Configuration config = context.getConfiguration();
        StaticQueryContext staticQueryContext = config.newStaticQueryContext();
        staticQueryContext.setLanguageVersion(qv);
        staticQueryContext.preLoadSchema(getRetainedStaticContext().getImportedSchema());

        String content = null;
        if (contentOption != null) {
            content = contentOption.head().getStringValue();
            streamSources = new StreamSource[1];
            streamSources[0] = new StreamSource(new StringReader(content), getStaticBaseUriString());
            staticQueryContext.setBaseURI(getStaticBaseUriString());
        }


        if (content == null) {
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


            ModuleURIResolver moduleURIResolver = config.getModuleURIResolver();
            if (moduleURIResolver == null) {
                moduleURIResolver = new StandardModuleURIResolver(config);
            }
            staticQueryContext.setModuleURIResolver(moduleURIResolver);
            String baseURI = getRetainedStaticContext().getStaticBaseUriString();
            staticQueryContext.setBaseURI(baseURI);

            try {
                String[] hints = locationHints.toArray(new String[0]);
                streamSources = staticQueryContext.getModuleURIResolver().resolve(moduleUri.toString(), baseURI, hints);
                if (streamSources == null) {
                    streamSources = new StandardModuleURIResolver(config).resolve(moduleUri.toString(), baseURI, hints);
                }
            } catch (XPathException e) {
                e.maybeSetErrorCode("FOQM0002");
                throw e;
            }
            if (streamSources.length == 0) {
                throw new XPathException("No library module found with specified target namespace " + moduleUri, "FOQM0002");
            }
        }


        try {
            // Note: Location hints other than the first are ignored
            String sourceQuery = QueryReader.readSourceQuery(config, streamSources[0], config.getValidCharacterChecker());
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
        main.initializeFunctionLibraries();
        lib.link(main);
        XQueryExpression xqe = new XQueryExpression(new ContextItemExpression(), main, false);
        DynamicQueryContext dqc = new DynamicQueryContext(context.getConfiguration());

        XQueryParser.createRunTimeFunctionLibrary(main, config, main.getExecutable());

        // Get the external variables and set parameters on DynamicQueryContext dqc
        if (variablesOption != null) {
            MapItem extVariables = (MapItem) variablesOption.head();
            for (KeyValuePair kvp : extVariables.keyValuePairs()) {
                QNameValue key = (QNameValue)kvp.key();
                dqc.setParameter(key.getStructuredQName(), kvp.value());
            }
        }

        // Get the context item supplied, and set it on the new Controller
        if (contextItemOption != null) {
            GroundedValue contextValue = contextItemOption.materialize();
            GlobalContextRequirement gcr = main.getExecutable().getGlobalContextRequirement();
            if (gcr != null) {
                SequenceType req = gcr.getRequiredSequenceType();
                if (req != null && !req.matches(contextValue)) {
                    throw new XPathException("Required context item type is " + req, "FOQM0005");
                }
            }
            dqc.setContextValue(contextValue);
        }

        Controller newController = xqe.newController(dqc);

        XPathContext newContext = newController.newXPathContext();

        // Evaluate the global variables, and add values to the result.

        GeneralMapBuilder variablesMapBuilder = AbstractFixedMap.getBuilder(40);
        for (GlobalVariable var : lib.getImportedGlobalVariables()) {
            GroundedValue value;
            QNameValue qNameValue = new QNameValue(var.getVariableQName(), BuiltInAtomicType.QNAME);
            if (qNameValue.getNamespaceURI().equals(moduleUri) && !var.isPrivate()) {
                try {
                    value = var.evaluateVariable(newContext);
                } catch (XPathException e) {
                    e.setIsGlobalError(false);  // to make it catchable
                    throw e.replacingErrorCode("XPTY0004", "FOQM0005"); // catches when external variables have wrong type
                }
                variablesMapBuilder.put(qNameValue, value);
            }
        }
        MapItem variablesMap = variablesMapBuilder.getCompletedMap();

        // Add functions to the result.
        XQueryFunctionLibrary functionLib = lib.getGlobalFunctionLibrary();

        ExportAgent agent = new ExportAgent() {
            @Override
            public void export(ExpressionPresenter out) throws XPathException {
                XPathException err = new XPathException(
                        "Cannot export a stylesheet that statically incorporates XQuery functions",
                        SaxonErrorCode.SXST0069);
                err.setIsStaticError(true);
                throw err;
            }
        };

        MapItem functionsMap = EmptyMap.INSTANCE_40;
        final List<XQueryFunction> list = new ArrayList<>();
        functionLib.processAllFunctions(fn -> list.add(fn));
        for (XQueryFunction function : list) {
            MapItem newMap;
            QNameValue functionQName = new QNameValue(function.getFunctionName(), BuiltInAtomicType.QNAME);
            if (functionQName.getNamespaceURI().equals(moduleUri) && !function.isPrivate()) {
                UserFunction userFunction = function.getUserFunction();
                UserFunctionReference.BoundUserFunction buf =
                        new UserFunctionReference.BoundUserFunction(userFunction, userFunction.getArity(), null, agent, newController);
                if (functionsMap.get(functionQName) != null) {
                    newMap = ((MapItem) functionsMap.get(functionQName)).put(new Int64Value(function.getNumberOfParameters()), buf);
                } else {
                    newMap = new SingleEntryMap(Int64Value.makeIntegerValue(function.getNumberOfParameters()), buf, 40);
                }
                functionsMap = functionsMap.put(functionQName, newMap);
            }
        }

        return new ShapedMap(RESULT_SHAPE, variablesMap, functionsMap);
    }

    private static final Shape RESULT_SHAPE = new Shape(new Twine8("variables"), new Twine8("functions"));

}

// Copyright (c) 2018-2026 Saxonica Limited
