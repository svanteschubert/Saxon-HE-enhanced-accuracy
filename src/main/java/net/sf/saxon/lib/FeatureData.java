
////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can
// obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses",
// as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

// AUTO-GENERATED FROM FeatureKeys.xml - DO NOT EDIT THIS FILE
// If you edit this file, your edits WILL BE TERMINATED with
// extreme prejudice by the next build.

public class FeatureData {
    public String uri;
    public int code;
    public String editions;
    public Class<?> type;
    public Object defaultValue;

    public FeatureData(String uri, int code, String editions, Class<?> type, Object defaultValue) {
        this.uri = uri;
        this.code = code;
        this.editions = editions;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    public static java.util.List<FeatureData> featureList = new java.util.ArrayList<>();

    public static void init() {

       featureList.add(new FeatureData("http://saxon.sf.net/feature/allow-external-functions", 1, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/allow-multithreading", 2, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/allow-old-java-uri-format", 3, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/allowSyntaxExtensions", 4, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/assertionsCanSeeComments", 5, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/collation-uri-resolver", 6, "HE PE EE", net.sf.saxon.lib.CollationURIResolver.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/collation-uri-resolver-class", 7, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/collection-finder", 8, "HE PE EE", net.sf.saxon.lib.CollectionFinder.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/collection-finder-class", 9, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/compile-with-tracing", 12, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/configuration", 13, "HE PE EE", net.sf.saxon.Configuration.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/configuration-file", 14, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/debugByteCode", 15, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/debugByteCodeDir", 16, "EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/defaultCollation", 17, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/defaultCollection", 18, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/defaultCountry", 19, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/defaultLanguage", 20, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/defaultRegexEngine", 21, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/disableXslEvaluate", 22, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/displayByteCode", 23, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/validation", 24, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/dtd-validation-recoverable", 25, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/eagerEvaluation", 26, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/entityResolverClass", 27, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/environmentVariableResolver", 28, "HE PE EE", net.sf.saxon.lib.EnvironmentVariableResolver.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/environmentVariableResolverClass", 29, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/errorListenerClass", 30, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/expandAttributeDefaults", 31, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/expathFileDeleteTemporaryFiles", 32, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/generateByteCode", 33, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/ignoreSAXSourceParser", 34, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/implicitSchemaImports", 35, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/lazyConstructionMode", 36, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/licenseFileLocation", 37, "PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/linenumbering", 38, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/markDefaultedAttributes", 39, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/maxCompiledClasses", 40, "EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/messageEmitterClass", 41, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/moduleURIResolver", 42, "HE PE EE", net.sf.saxon.lib.ModuleURIResolver.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/moduleURIResolverClass", 43, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/monitorHotSpotByteCode", 44, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/multipleSchemaImports", 45, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/namePool", 46, "HE PE EE", net.sf.saxon.om.NamePool.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/occurrenceLimits", 47, "EE", Object.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/optimizationLevel", 48, "HE PE EE", Object.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/outputURIResolver", 49, "HE PE EE", net.sf.saxon.lib.OutputURIResolver.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/outputURIResolverClass", 50, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/preEvaluateDocFunction", 51, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/recognize-uri-query-parameters", 53, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/recoveryPolicy", 54, "HE PE EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/recoveryPolicyName", 55, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/resultDocumentThreads", 56, "EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/retain-dtd-attribute-types", 57, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/schemaURIResolver", 58, "EE", net.sf.saxon.lib.SchemaURIResolver.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/schemaURIResolverClass", 59, "EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/schema-validation", 60, "EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/schema-validation-mode", 61, "EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/serializerFactoryClass", 62, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/sourceParserClass", 63, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/sourceResolverClass", 64, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/stableCollectionUri", 65, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/stableUnparsedText", 66, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/standardErrorOutputFile", 67, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/streamability", 68, "EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/strictStreamability", 69, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/streamingFallback", 70, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/strip-whitespace", 71, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/styleParserClass", 72, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/suppressEvaluationExpiryWarning", 73, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/suppressXPathWarnings", 74, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/suppressXsltNamespaceCheck", 75, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/thresholdForCompilingTypes", 76, "HE PE EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/timing", 77, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/trace-external-functions", 78, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/traceListener", 79, "HE PE EE", net.sf.saxon.lib.TraceListener.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/traceListenerClass", 80, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/traceListenerOutputFile", 81, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/trace-optimizer-decisions", 82, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/treeModel", 83, "HE PE EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/treeModelName", 84, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/unparsedTextURIResolver", 85, "HE PE EE", net.sf.saxon.lib.UnparsedTextURIResolver.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/unparsedTextURIResolverClass", 86, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/uriResolverClass", 87, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/use-pi-disable-output-escaping", 88, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/use-typed-value-cache", 89, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/useXsiSchemaLocation", 90, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/validation-comments", 91, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/validation-warnings", 92, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/version-warning", 93, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xinclude-aware", 94, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xml-version", 95, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/parserFeature?uri=", 96, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/parserProperty?uri=", 97, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryAllowUpdate", 98, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryConstructionMode", 99, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryDefaultElementNamespace", 100, "HE PE EE", Object.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryDefaultFunctionNamespace", 101, "HE PE EE", Object.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryEmptyLeast", 102, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryInheritNamespaces", 103, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryMultipleModuleImports", 104, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryPreserveBoundarySpace", 105, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryPreserveNamespaces", 106, "HE PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryRequiredContextItemType", 107, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xquerySchemaAware", 108, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryStaticErrorListenerClass", 109, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xqueryVersion", 110, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xsd-version", 111, "EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/enableAssertions", 112, "PE EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/initialMode", 113, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/initialTemplate", 114, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xsltSchemaAware", 115, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/stylesheetErrorListener", 116, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/stylesheetURIResolver", 117, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xsltVersion", 118, "HE PE EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/regexBacktrackingLimit", 119, "HE PE EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xpathVersionForXsd", 120, "EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/xpathVersionForXslt", 121, "HE PE EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/thresholdForFunctionInlining", 122, "EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/thresholdForHotspotByteCode", 123, "EE", Integer.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/allowedProtocols", 124, "EE", Object.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/retainNodeForDiagnostics", 125, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/allowUnresolvedSchemaComponents", 126, "EE", Boolean.class, Boolean.FALSE));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/zipUriPattern", 127, "EE", String.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/saxon-resource-resolver", 128, "HE PE EE", Object.class, null));

       featureList.add(new FeatureData("http://saxon.sf.net/feature/saxon-resource-resolver-class", 129, "HE PE EE", String.class, null));

     }
 }
