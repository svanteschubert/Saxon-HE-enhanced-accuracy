/// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

////import com.saxonica.trans.XSLRecord;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.CheckSumFilter;
import net.sf.saxon.event.FilterFactory;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Stripper;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.accum.Accumulator;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.accum.AccumulatorRule;
import net.sf.saxon.expr.compat.ArithmeticExpression10;
import net.sf.saxon.expr.compat.GeneralComparison10;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.expr.number.NumberFormatter;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.*;
import net.sf.saxon.functions.*;
import net.sf.saxon.functions.hof.*;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.functions.registry.XPath30FunctionSet;
import net.sf.saxon.lib.*;
import net.sf.saxon.ma.MapOrArray;
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.arrays.SquareArrayConstructor;
import net.sf.saxon.ma.jnode.AnyJNodeType;
import net.sf.saxon.ma.jnode.RootJNode;
import net.sf.saxon.ma.map.AbstractFixedMap;
import net.sf.saxon.ma.map.GeneralMapBuilder;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.MapItem;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.pattern.nodetest.AnyGNode;
import net.sf.saxon.pattern.nodetest.NodeTest;
import net.sf.saxon.pattern.nodetest.NodeTestStar;
import net.sf.saxon.pattern.qname.*;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.regex.ARegularExpression;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeChar;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.style.PackageVersion;
import net.sf.saxon.style.StylesheetFunctionLibrary;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.packages.IPackageLoader;
import net.sf.saxon.trans.rules.BuiltInRuleSet;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpDelegate;
import net.sf.saxon.tree.jiter.TopDownStackIterable;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.type.*;
import net.sf.saxon.type.coercion.GNodeSequenceConverter;
import net.sf.saxon.type.coercion.SequenceCoercer;
import net.sf.saxon.type.gnode.AnyGNodeType;
import net.sf.saxon.type.gnode.NamedXNodeType;
import net.sf.saxon.type.gnode.NodeKindType;
import net.sf.saxon.value.*;
import net.sf.saxon.z.IntHashMap;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Supplier;

/**
 * This class reads the XML exported form of a package and reconstructs the package object in memory.
 */

public class PackageLoaderHE implements IPackageLoader {

    private final static NestedIntegerValue SAXON9911 = new NestedIntegerValue(new int[]{9, 9, 1, 1});

    private final Configuration config;
    //private Schema importedSchema;
    protected final Stack<StylesheetPackage> packStack = new Stack<>();
    private final XPathParser parser;
    public final Stack<List<ComponentInvocation>> fixups = new Stack<>();
    public final List<Action> completionActions = new ArrayList<>();
    public StylesheetPackage topLevelPackage;
    public final Map<String, StylesheetPackage> allPackages = new HashMap<>();
    public Stack<LocalBinding> localBindings;
    private final ExecutableFunctionLibrary overriding;
    private final ExecutableFunctionLibrary underriding;
    private final Stack<RetainedStaticContext> contextStack = new Stack<>();
    public final Map<SymbolicName, UserFunction> userFunctions = new HashMap<>();
    private final Map<String, IntHashMap<Location>> locationMap = new HashMap<>();
    private final Map<Integer, Component> componentIdMap = new HashMap<>();
    private final Map<Component, String> externalReferences = new HashMap<>();
    private String relocatableBase = null;
    private NestedIntegerValue originalVersion = null;
    private Map<String, Schema> usedSchemata = null;

    public PackageLoaderHE(Configuration config) {
        this.config = config;
        overriding = new ExecutableFunctionLibrary(config);
        underriding = new ExecutableFunctionLibrary(config);
        try {
            parser = config.newExpressionParser("XP", false, new IndependentContext(config));
            QNameParser qNameParser = new QNameParser(null).withAcceptEQName(true, 40);
            parser.setQNameParser(qNameParser);
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

    public static void processAccumulatorList(PackageLoaderHE loader, SourceDocument inst, String accumulatorNames) {
        if (accumulatorNames != null) {
            final List<StructuredQName> accNameList = new ArrayList<>();
            StringTokenizer tokenizer = new StringTokenizer(accumulatorNames);
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                StructuredQName name = StructuredQName.fromEQName40((token));
                accNameList.add(name);
            }
            final StylesheetPackage pack = loader.getPackStack().peek();
            loader.addCompletionAction(() -> {
                Set<Accumulator> list = new HashSet<>();
                for (StructuredQName sn : accNameList) {
                    for (Accumulator test : pack.getAccumulatorRegistry().getAllAccumulators()) {
                        if (test.getAccumulatorName().equals(sn)) {
                            list.add(test);
                        }
                    }
                }
                inst.setUsedAccumulators(list);
            });
        }
    }

    public Configuration getConfiguration() {
        return config;
    }

    public Schema getSchema() {
        return contextStack.peek().getImportedSchema();
    }

    public StylesheetPackage getTopLevelPackage() {
        return topLevelPackage;
    }

    public StylesheetPackage getPackage(String key) {
        return allPackages.get(key);
    }

    public Stack<StylesheetPackage> getPackStack() {
        return packStack;
    }

    public void addCompletionAction(Action action) {
        completionActions.add(action);
    }

    private void addComponentFixup(ComponentInvocation invocation) {
        List<ComponentInvocation> currentList = fixups.peek();
        currentList.add(invocation);
    }

    @Override
    public StylesheetPackage loadPackage(Source source, Map<String, Schema> usedSchemata) throws XPathException {
        this.usedSchemata = usedSchemata;
        ParseOptions options = new ParseOptions()
                .withSpaceStrippingRule(AllElementsSpaceStrippingRule.INSTANCE)
                .withSchemaValidationMode(Validation.SKIP)
                .withDTDValidationMode(Validation.SKIP);

        final List<ProxyReceiver> filters = new ArrayList<>(1);
        FilterFactory checksumFactory = next -> {
            CheckSumFilter filter = new CheckSumFilter(next);
            filter.setCheckExistingChecksum(true);
            filters.add(filter);
            return filter;
        };


        options = options.withFilter(checksumFactory);

        NodeInfo doc = config.buildDocumentTree(source, options).getRootNode();

        CheckSumFilter csf = (CheckSumFilter) filters.get(0);
        if (!csf.isChecksumCorrect()) {
            throw new XPathException("Package cannot be loaded: incorrect checksum", SaxonErrorCode.SXPK0002);
        }
        return loadPackageDoc(doc);
    }

    @Override
    public StylesheetPackage loadPackageDoc(NodeInfo doc) throws XPathException {

        StylesheetPackage pack = config.makeStylesheetPackage();
        pack.setRuleManager(new RuleManager(pack));
        pack.setCharacterMapIndex(new CharacterMapIndex());
        pack.setJustInTimeCompilation(false);
        pack.setImportedSchema("", config.emptySchema());
        if (packStack.isEmpty()) {
            topLevelPackage = pack;
        }
        packStack.push(pack);
        NodeInfo packageElement = (NodeInfo) doc.iterateChildAxis(NodeKindType.ELEMENT).next();
        if (packageElement.getNamespaceUri() != NamespaceUri.SAXON_XSLT_EXPORT) {
            throw new XPathException("Incorrect namespace for XSLT export file", SaxonErrorCode.SXPK0002);
        }
        if (!packageElement.getLocalPart().equals("package")) {
            throw new XPathException("Outermost element of XSLT export file must be 'package'", SaxonErrorCode.SXPK0002);
        }
        String versionAtt = packageElement.getAttributeValue(NamespaceUri.NULL, "version");
        if (versionAtt != null) {
            pack.setHostLanguage(HostLanguage.XSLT, Integer.parseInt(versionAtt));
        }
        String saxonVersionAtt = packageElement.getAttributeValue(NamespaceUri.NULL, "saxonVersion");
        if (saxonVersionAtt == null) {
            saxonVersionAtt = "9.8.0.1"; //Arbitrarily; older SEF files do not have this attribute
        }
        originalVersion = NestedIntegerValue.parse(saxonVersionAtt);
        String dmk = packageElement.getAttributeValue(NamespaceUri.NULL, "dmk");
        if (dmk != null) {
            int licenseId = config.registerLocalLicense(dmk);
            pack.setLocalLicenseId(licenseId);
        }

        loadPackageElement(packageElement, pack);

        for (Map.Entry<Component, String> entry : externalReferences.entrySet()) {
            Component comp = entry.getKey();
            StringTokenizer tokenizer = new StringTokenizer(entry.getValue());
            while (tokenizer.hasMoreTokens()) {
                String token = tokenizer.nextToken();
                int target = Integer.parseInt(token);
                Component targetComponent = componentIdMap.get(target);
                if (targetComponent == null) {
                    throw new XPathException("Unresolved reference to component " + target, SaxonErrorCode.SXPK0005);
                }
                comp.getComponentBindings().add(new ComponentBinding(targetComponent.getActor().getSymbolicName(), targetComponent));
            }
        }
        return pack;

    }


    public void needsPELicense(String name) {
        int localLicenseId = getTopLevelPackage().getLocalLicenseId();
        config.checkLicensedFeature(Configuration.LicenseFeature.PROFESSIONAL_EDITION, name, localLicenseId);
    }

    public void needsEELicense(String name) {
        int localLicenseId = getTopLevelPackage().getLocalLicenseId();
        config.checkLicensedFeature(Configuration.LicenseFeature.ENTERPRISE_XSLT, name, localLicenseId);
    }

    public void loadPackageElement(NodeInfo packageElement, StylesheetPackage pack) throws XPathException {

        fixups.push(new ArrayList<>());
        String packageName = packageElement.getAttributeValue(NamespaceUri.NULL, "name");
        String packageId = packageElement.getAttributeValue(NamespaceUri.NULL, "id");
        String packageKey = packageId == null ? packageName : packageId; // for backwards compatibility with 9.8
        boolean relocatable = "true".equals(packageElement.getAttributeValue(NamespaceUri.NULL, "relocatable"));
        if (packageName != null) {
            pack.setPackageName(packageName);
            allPackages.put(packageKey, pack);
        }
        pack.setPackageVersion(
                new PackageVersion(packageElement.getAttributeValue(NamespaceUri.NULL, "packageVersion")));
        int xsltVersion = getIntegerAttribute(packageElement, "version");
        pack.setLanguageVersion(xsltVersion);
        pack.setSchemaAware("1".equals(packageElement.getAttributeValue(NamespaceUri.NULL, "schemaAware")));
        if (pack.isSchemaAware()) {
            needsEELicense("schema-awareness");
        }
        String implicitAtt = packageElement.getAttributeValue(NamespaceUri.NULL, "implicit");
        if (implicitAtt != null) {
            pack.setImplicitPackage(implicitAtt.equals("true"));
        } else {
            // For export files created prior to Saxon 9.9.1.2, we'll treat the package as implicit,
            // for compatibility: otherwise, setInitialTemplate("main") will fail when the main template
            // has no "visibility" attribute
            pack.setImplicitPackage(originalVersion.compareTo(SAXON9911) <= 0);
        }
        pack.setStripsTypeAnnotations("1".equals(packageElement.getAttributeValue(NamespaceUri.NULL, "stripType")));
        pack.setKeyManager(new KeyManager(pack.getConfiguration(), pack));
        pack.setDeclaredModes("1".equals(packageElement.getAttributeValue(NamespaceUri.NULL, "declaredModes")));
        for (NodeInfo usePack : packageElement.children(NodePredicateLambda.of(n -> ((NodeInfo) n).getLocalPart().equals("package")))) {
            StylesheetPackage subPack = config.makeStylesheetPackage();
            subPack.setRuleManager(new RuleManager(pack));
            subPack.setCharacterMapIndex(new CharacterMapIndex());
            subPack.setJustInTimeCompilation(false);
            packStack.push(subPack);
            loadPackageElement(usePack, subPack);
            packStack.pop();
            pack.addUsedPackage(subPack);
        }

        FunctionLibraryList functionLibrary = new FunctionLibraryList();
        int xpathVersion = (xsltVersion <= 31 ? 31 : 40);
        xsltVersion = (xsltVersion < 40 ? 30 : 40);
        functionLibrary.addFunctionLibrary(config.getXSLTFunctionSet(xsltVersion));
        addVendorFunctionLibrary(functionLibrary, config);
        functionLibrary.addFunctionLibrary(MapFunctionSet.getInstance(xpathVersion));
        functionLibrary.addFunctionLibrary(ArrayFunctionSet.getInstance(xpathVersion));
        functionLibrary.addFunctionLibrary(MathFunctionSet.getInstance(xpathVersion));
        //functionLibrary.addFunctionLibrary(overriding);
        functionLibrary.addFunctionLibrary(new StylesheetFunctionLibrary(pack, true));

        functionLibrary.addFunctionLibrary(new ConstructorFunctionLibrary(config));

        XQueryFunctionLibrary queryFunctions = new XQueryFunctionLibrary(config);
        functionLibrary.addFunctionLibrary(queryFunctions);
        functionLibrary.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(functionLibrary);
        //functionLibrary.addFunctionLibrary(underriding);
        functionLibrary.addFunctionLibrary(new StylesheetFunctionLibrary(pack, false));

        pack.setFunctionLibraryDetails(functionLibrary, overriding, underriding);

        RetainedStaticContext rsc = new RetainedStaticContext(config, pack);
        if (relocatable) {
            // For a relocatable package, take the base URI from the location of the SEF file
            relocatableBase = packageElement.getBaseURI();
            rsc.setStaticBaseUriString(relocatableBase);
        }
        rsc.setPackageData(pack);
        contextStack.push(rsc);
        localBindings = new Stack<>();

        readGlobalContext(packageElement);
        readSchemaNamespaces(packageElement);
        readSchemata(packageElement, usedSchemata);
        rsc.setImportedSchema(packStack.peek().getImportedSchema(""));
        readKeys(packageElement);
        readComponents(packageElement, false);
        NodeInfo overridden = (NodeInfo) packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "overridden", config)).next();
        if (overridden != null) {
            readComponents(overridden, true);
        }
        readAccumulators(packageElement);
        readOutputProperties(packageElement);
        readCharacterMaps(packageElement);
        readSpaceStrippingRules(packageElement);
        readDecimalFormats(packageElement);
        resolveFixups();
        fixups.pop();
        for (Action a : completionActions) {
            a.doAction();
        }

        StructuredQName defaultModeName = getQNameAttribute(packageElement, "defaultMode");
        pack.setDefaultMode(defaultModeName == null ? Mode.UNNAMED_MODE_NAME : defaultModeName);
    }

    protected void addVendorFunctionLibrary(FunctionLibraryList targetList, Configuration config) {
        // no action for HE
    }

    private void readGlobalContext(NodeInfo packageElement) throws XPathException {
        GlobalContextRequirement req = null;
        //NameTest condition = NameTest.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "glob", config.getNamePool());
        for (NodeInfo varElement : packageElement.children(NodePredicateLambda.of(n -> ((NodeInfo) n).getLocalPart().equals("glob")))) {
            if (req == null) {
                req = new GlobalContextRequirement();
                packStack.peek().setContextItemRequirements(req);
            }
            String use = varElement.getAttributeValue(NamespaceUri.NULL, "use");
            if ("opt".equals(use)) {
                req.setContextValueOptionality(Optionality.OPTIONAL);
            } else if ("pro".equals(use)) {
                req.setContextValueOptionality(Optionality.PROHIBITED);
            } else if ("req".equals(use)) {
                req.setContextValueOptionality(Optionality.REQUIRED);
            }
            ItemType requiredType = parseItemTypeAttribute(varElement, "type");
            if (requiredType != null) {
                req.addRequiredSequenceType(SequenceType.one(requiredType), true);
            }
        }
    }

    protected void readSchemaNamespaces(NodeInfo packageElement) throws XPathException {
        // No action in Saxon-HE
    }

    protected void readSchemata(NodeInfo packageElement, Map<String, Schema> usedSchemata) throws XPathException {
        // No action in Saxon-HE
    }

    private void readKeys(NodeInfo packageElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo keyElement;
        SequenceIterator iterator = packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "key", config));
        while ((keyElement = (NodeInfo) iterator.next()) != null) {
            StructuredQName keyName = getQNameAttribute(keyElement, "name");
            SymbolicName symbol = new SymbolicName(StandardNames.XSL_KEY, keyName);

            String flags = keyElement.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean backwards = flags != null && flags.contains("b");
            boolean range = flags != null && flags.contains("r");
            boolean reusable = flags != null && flags.contains("u");
            boolean composite = flags != null && flags.contains("c");
            boolean convertUntypedToOther = flags != null && flags.contains("v");
            boolean strictComparison = flags != null && flags.contains("s");
            Pattern match = getFirstChildPattern(keyElement);
            Expression use = getSecondChildExpression(keyElement);
            String collationName = keyElement.getAttributeValue(NamespaceUri.NULL, "collation");
            if (collationName == null) {
                collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
            StringCollator collation = config.getCollation(collationName);
            KeyDefinition keyDefinition = new KeyDefinition(symbol, match, use, collationName, collation);
            int slots = getIntegerAttribute(keyElement, "slots");
            if (slots != Integer.MIN_VALUE) {
                keyDefinition.setStackFrameMap(new SlotManager(slots));
            }
            String binds = keyElement.getAttributeValue(NamespaceUri.NULL, "binds");
            Component keyComponent = keyDefinition.makeDeclaringComponent(Visibility.PRIVATE, pack);
            externalReferences.put(keyComponent, binds);
            if (backwards) {
                keyDefinition.setBackwardsCompatible(true);
            }
            if (range) {
                keyDefinition.setRangeKey(true);
            }
            if (composite) {
                keyDefinition.setComposite(true);
            }
            keyDefinition.setStrictComparison(strictComparison);
            keyDefinition.setConvertUntypedToOther(convertUntypedToOther);
            pack.getKeyManager().addKeyDefinition(keyName, keyDefinition, reusable, pack.getConfiguration());
            //pack.addComponent(keyComponent);
        }

    }

    private void readComponents(NodeInfo packageElement, boolean overridden) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo child;
        SequenceIterator iterator = packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "co", config));
        while ((child = (NodeInfo) iterator.next()) != null) {
            int id = getIntegerAttribute(child, "id");
            String visAtt = child.getAttributeValue(NamespaceUri.NULL, "vis");
            Visibility vis = visAtt == null ? Visibility.PRIVATE : Visibility.valueOf(visAtt.toUpperCase());
            VisibilityProvenance provenance = visAtt == null ? VisibilityProvenance.DEFAULTED : VisibilityProvenance.EXPLICIT;
            String binds = child.getAttributeValue(NamespaceUri.NULL, "binds");
            String dPackKey = child.getAttributeValue(NamespaceUri.NULL, "dpack");
            StylesheetPackage declaringPackage;
            if (dPackKey == null) {
                declaringPackage = pack;
            } else if (allPackages.containsKey(dPackKey)) {
                declaringPackage = allPackages.get(dPackKey);
            } else {
                declaringPackage = config.makeStylesheetPackage();
                declaringPackage.setPackageName(dPackKey);
                declaringPackage.setTargetEdition(config.getEditionCode());
                declaringPackage.setJustInTimeCompilation(false);
            }
            Component component;
            int base = getIntegerAttribute(child, "base");
            if (base != Integer.MIN_VALUE) {
                // Note, this cannot be a forwards reference
                Component baseComponent = componentIdMap.get(base);
                if (baseComponent == null) {
                    throw new AssertionError(base + "");
                }
                component = Component.makeComponent(baseComponent.getActor(), vis, provenance, pack, declaringPackage);
                component.setBaseComponent(baseComponent);
                if (component instanceof Component.M) {
                    // Create the mode even if there are no mode children: test case override-v-015
                    pack.getRuleManager().obtainMode(baseComponent.getActor().getComponentName(), true);
                }
            } else {
                NodeInfo grandchild = (NodeInfo) child.iterateChildAxis(NodeKindType.ELEMENT).next();
                Actor cc;
                String kind = grandchild.getLocalPart();
                cc = switch (kind) {
                    case "template" -> readNamedTemplate(grandchild);
                    case "globalVariable" -> readGlobalVariable(grandchild);
                    case "globalParam" -> readGlobalParam(grandchild);
                    case "function" -> readGlobalFunction(grandchild);
                    case "mode" -> readMode(grandchild);
                    case "attributeSet" -> readAttributeSet(grandchild);
                    default -> throw new XPathException("unknown component kind " + kind);
                };
                component = Component.makeComponent(cc, vis, provenance, pack, declaringPackage);
                cc.setDeclaringComponent(component);
                cc.setDeclaredVisibility(vis);
            }
            externalReferences.put(component, binds);
            componentIdMap.put(id, component);
            if (overridden) {
                pack.addOverriddenComponent(component);
            } else {
                if (component.getVisibility() == Visibility.HIDDEN) {
                    pack.addHiddenComponent(component);
                } else {
                    pack.addComponent(component);
                }
            }
        }
    }

    private GlobalVariable readGlobalVariable(NodeInfo varElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        StructuredQName variableName = getQNameAttribute(varElement, "name");
        GlobalVariable var = new GlobalVariable();
        var.setVariableQName(variableName);
        var.setPackageData(pack);
        var.setRequiredType(parseAlphaCode(varElement, "as"));
        String flags = varElement.getAttributeValue(NamespaceUri.NULL, "flags");
        if (flags != null) {
            if (flags.contains("a")) {
                var.setAssignable(true);
            }
            if (flags.contains("x")) {
                var.setIndexedVariable();
            }
            if (flags.contains("r")) {
                var.setRequiredParam(true);
            }
        }
        int slots = getIntegerAttribute(varElement, "slots");
        if (slots > 0) {
            var.setContainsLocals(new SlotManager(slots));
        }
        NodeInfo bodyElement = (NodeInfo) varElement.iterateChildAxis(NodeKindType.ELEMENT).next();
        if (bodyElement == null) {
            var.setBody(Literal.makeEmptySequence());
        } else {
            Expression body = loadExpression(bodyElement);
            var.setBody(body);
            RetainedStaticContext rsc = body.getRetainedStaticContext();
            body.setRetainedStaticContext(rsc); // to propagate it to the subtree
        }

        pack.addGlobalVariable(var);
        return var;
    }

    private GlobalParam readGlobalParam(NodeInfo varElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        StructuredQName variableName = getQNameAttribute(varElement, "name");
        //System.err.println("Loading global variable " + variableName);
        localBindings = new Stack<>();
        GlobalParam var = new GlobalParam();
        var.setVariableQName(variableName);
        var.setPackageData(pack);
        var.setRequiredType(parseAlphaCode(varElement, "as"));
        String flags = varElement.getAttributeValue(NamespaceUri.NULL, "flags");
        if (flags != null) {
            if (flags.contains("a")) {
                var.setAssignable(true);
            }
            if (flags.contains("x")) {
                var.setIndexedVariable();
            }
            if (flags.contains("r")) {
                var.setRequiredParam(true);
            }
            if (flags.contains("i")) {
                var.setImplicitlyRequiredParam(true);
            }
        }
        int slots = getIntegerAttribute(varElement, "slots");
        if (slots > 0) {
            var.setContainsLocals(new SlotManager(slots));
        }
        NodeInfo bodyElement = (NodeInfo) varElement.iterateChildAxis(NodeKindType.ELEMENT).next();
        if (bodyElement == null) {
            var.setBody(Literal.makeEmptySequence());
        } else {
            Expression body = loadExpression(bodyElement);
            var.setBody(body);
            RetainedStaticContext rsc = body.getRetainedStaticContext();
            body.setRetainedStaticContext(rsc); // to propagate it to the subtree
        }
        return var;
    }

    private NamedTemplate readNamedTemplate(NodeInfo templateElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        localBindings = new Stack<>();
        StructuredQName templateName = getQNameAttribute(templateElement, "name");
        String flags = templateElement.getAttributeValue(NamespaceUri.NULL, "flags");
        int slots = getIntegerAttribute(templateElement, "slots");
        SequenceType contextType = parseAlphaCode(templateElement, "cxt");
        ItemType contextItemType;
        if (contextType == null) {
            contextItemType = AnyItemType.INSTANCE;
        } else {
            contextItemType = contextType.getPrimaryType();
        }

        NamedTemplate template = new NamedTemplate(templateName, getConfiguration());
        template.setStackFrameMap(new SlotManager(slots));
        template.setPackageData(pack);
        template.setRequiredType(parseAlphaCode(templateElement, "as"));
        Optionality optionality;
        // 'o' = may be absent; 's' = may be present
        if (flags.contains("o")) {
            optionality = flags.contains("s") ? Optionality.OPTIONAL : Optionality.PROHIBITED;
        } else {
            optionality = Optionality.REQUIRED;
        }
        template.setContextItemRequirements(contextItemType, optionality);
        NodeInfo bodyElement = getChildWithRole(templateElement, "body");
        if (bodyElement == null) {
            template.setBody(Literal.makeEmptySequence());
        } else {
            Expression body = loadExpression(bodyElement);
            template.setBody(body);
            RetainedStaticContext rsc = body.getRetainedStaticContext();
            body.setRetainedStaticContext(rsc); // to propagate it to the subtree
        }
        return template;
    }

    private UserFunction readGlobalFunction(NodeInfo functionElement) throws XPathException {
        localBindings = new Stack<>();
        UserFunction function = readFunction(functionElement);
        userFunctions.put(function.getSymbolicName(), function);
        underriding.addFunction(function);
        return function;
    }

    private UserFunction getUserFunction(SymbolicName.F name) {
        return userFunctions.get(name);
    }

    private UserFunction currentFunction;

    public UserFunction readFunction(NodeInfo functionElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        StructuredQName functionName = getQNameAttribute(functionElement, "name");
        int slots = getIntegerAttribute(functionElement, "slots");
        String flags = functionElement.getAttributeValue(NamespaceUri.NULL, "flags");
        if (flags == null) {
            flags = "";
        }
        final UserFunction function = makeFunction(flags);
        function.setFunctionName(functionName);
        function.setStackFrameMap(new SlotManager(slots));
        function.setPackageData(pack);
        function.setRetainedStaticContext(makeRetainedStaticContext(functionElement));
        function.setResultType(parseAlphaCode(functionElement, "as"));
        function.setDeclaredStreamability(FunctionStreamability.UNCLASSIFIED);
        function.incrementReferenceCount(); // ensure it's exported in any re-export
        int evalMode = getIntegerAttribute(functionElement, "eval");

        if (flags.contains("p")) {
            function.setDeterminism(UserFunction.Determinism.PROACTIVE);
        } else if (flags.contains("e")) {
            function.setDeterminism(UserFunction.Determinism.ELIDABLE);
        } else if (flags.contains("d")) {
            function.setDeterminism(UserFunction.Determinism.DETERMINISTIC);
        }
        // Ignore the "m" flag - handled in subclass for Saxon-PE

        boolean streaming = false;
        if (flags.contains("U")) {
            function.setDeclaredStreamability(FunctionStreamability.UNCLASSIFIED);
        } else if (flags.contains("A")) {
            function.setDeclaredStreamability(FunctionStreamability.ABSORBING);
            streaming = true;
        } else if (flags.contains("I")) {
            function.setDeclaredStreamability(FunctionStreamability.INSPECTION);
            streaming = true;
        } else if (flags.contains("F")) {
            function.setDeclaredStreamability(FunctionStreamability.FILTER);
            streaming = true;
        } else if (flags.contains("S")) {
            function.setDeclaredStreamability(FunctionStreamability.SHALLOW_DESCENT);
            streaming = true;
        } else if (flags.contains("D")) {
            function.setDeclaredStreamability(FunctionStreamability.DEEP_DESCENT);
            streaming = true;
        } else if (flags.contains("C")) {
            function.setDeclaredStreamability(FunctionStreamability.ASCENT);
            streaming = true;
        }

        //function.computeEvaluationMode();
        //Evaluator(Evaluators.getEvaluator(evalMode));

        currentFunction = function;
        List<UserFunctionParameter> params = new ArrayList<>();
        SequenceIterator argIterator = functionElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "arg", config));
        NodeInfo argElement;
        int slot = 0;
        int optionalArgs = 0;
        while ((argElement = (NodeInfo) argIterator.next()) != null) {
            UserFunctionParameter arg = new UserFunctionParameter();
            arg.setVariableQName(getQNameAttribute(argElement, "name"));
            arg.setRequiredType(parseAlphaCode(argElement, "as"));
            arg.setSlotNumber(slot++);
            Expression dflt = getFirstChildExpression(argElement);
            if (dflt != null) {
                arg.setDefaultValueExpression(() -> dflt);
                optionalArgs++;
            }
            params.add(arg);
            localBindings.push(arg);
        }
        function.setParameterDefinitions(params.toArray(new UserFunctionParameter[0]));
        if (optionalArgs > 0) {
            function.setMinimumArity(params.size() - optionalArgs);
        }
        if (streaming) {
            params.get(0).setFunctionStreamability(function.getDeclaredStreamability());
        }
        NodeInfo bodyElement = getChildWithRole(functionElement, "body");
        if (bodyElement == null) {
            function.setBody(Literal.makeEmptySequence());
        } else {
            Expression body = loadExpression(bodyElement);
            function.setBody(body);
            RetainedStaticContext rsc = body.getRetainedStaticContext();
            body.setRetainedStaticContext(rsc); // to propagate it to the subtree
        }

        for (int i = 0; i < function.getArity(); i++) {
            localBindings.pop();
        }
        if (function.getDeclaredStreamability() != FunctionStreamability.UNCLASSIFIED) {
            addCompletionAction(CSharp.methodRef(function::prepareForStreaming));
        }
        return function;
    }

    protected UserFunction makeFunction(String flags) {
        return new UserFunction();
    }

    private AttributeSet readAttributeSet(NodeInfo aSetElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        localBindings = new Stack<>();
        StructuredQName aSetName = getQNameAttribute(aSetElement, "name");
        int slots = getIntegerAttribute(aSetElement, "slots");
        //System.err.println("Loading attribute set " + aSetName);

        AttributeSet aSet = new AttributeSet();
        aSet.setName(aSetName);
        aSet.setStackFrameMap(new SlotManager(slots));
        aSet.setPackageData(pack);
        aSet.setBody(getFirstChildExpression(aSetElement));
        aSet.setDeclaredStreamable("s".equals(aSetElement.getAttributeValue(NamespaceUri.NULL, "flags")));

        return aSet;

    }

    private Mode readMode(NodeInfo modeElement) throws XPathException {
        final StylesheetPackage pack = packStack.peek();
        StructuredQName modeName = getQNameAttribute(modeElement, "name");
        if (modeName == null) {
            modeName = Mode.UNNAMED_MODE_NAME;
        }
        final SimpleMode mode = (SimpleMode) pack.getRuleManager().obtainMode(modeName, true);

        int patternSlots = getIntegerAttribute(modeElement, "patternSlots");
        mode.allocatePatternSlots(patternSlots);

        String flags = modeElement.getAttributeValue(NamespaceUri.NULL, "flags");
        if (flags == null) {
            flags = "";
        }

        String onNoMatch = modeElement.getAttributeValue(NamespaceUri.NULL, "onNo");
        BuiltInRuleSet base;
        if (onNoMatch != null) {
            String asAtt = modeElement.getAttributeValue(NamespaceUri.NULL, "as");
            SequenceType requiredType = asAtt == null ?
                    SequenceType.ANY_SEQUENCE :
                    AlphaCode.toSequenceType(asAtt, config, pack.getImportedSchema(""));
            boolean copyNamespaces = !flags.contains("n");
            base = Mode.getBuiltInRuleSetForCode(onNoMatch, requiredType, copyNamespaces);
            mode.setBuiltInRuleSet(base);
        }


        mode.setStreamable(flags.contains("s"));
        if (flags.contains("t")) {
            mode.setExplicitProperty("typed", "yes", 1);
        }
        if (flags.contains("u")) {
            mode.setExplicitProperty("typed", "no", 1);
        }
        if (flags.contains("F")) {
            mode.setRecoveryPolicy(RecoveryPolicy.DO_NOT_RECOVER);
        }
        if (flags.contains("W")) {
            mode.setRecoveryPolicy(RecoveryPolicy.RECOVER_WITH_WARNINGS);
        }
        if (flags.contains("e")) {
            mode.setHasRules(false);
        }


        final List<StructuredQName> accNames = getListOfQNameAttribute(modeElement, "useAcc");
        addCompletionAction(() -> {
            AccumulatorRegistry registry = pack.getAccumulatorRegistry();
            Set<Accumulator> accumulators = new HashSet<>();
            for (StructuredQName qn : accNames) {
                Accumulator acc = registry.getAccumulator(qn);
                accumulators.add(acc);

            }
            mode.setAccumulators(accumulators);
        });

        SequenceIterator iterator2 = modeElement.iterateDescendantAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "templateRule", config));
        NodeInfo templateRuleElement0;
        LinkedList<NodeInfo> ruleStack = new LinkedList<>();
        while ((templateRuleElement0 = (NodeInfo) iterator2.next()) != null) {
            // process rules in reverse order
            ruleStack.addFirst(templateRuleElement0);
        }
        for (NodeInfo templateRuleElement : ruleStack) {
            int precedence = getIntegerAttribute(templateRuleElement, "prec");
            int rank = getIntegerAttribute(templateRuleElement, "rank");
            String priorityAtt = templateRuleElement.getAttributeValue(NamespaceUri.NULL, "prio");
            double priority = Double.parseDouble(priorityAtt);
            int sequence = getIntegerAttribute(templateRuleElement, "seq");
            int part = getIntegerAttribute(templateRuleElement, "part");
            if (part == Integer.MIN_VALUE) {
                part = 0;
            }
            int minImportPrecedence = getIntegerAttribute(templateRuleElement, "minImp");
            int slots = getIntegerAttribute(templateRuleElement, "slots");
            boolean streamable = "1".equals(templateRuleElement.getAttributeValue(NamespaceUri.NULL, "streamable"));
            String tflags = templateRuleElement.getAttributeValue(NamespaceUri.NULL, "flags");
            SequenceType contextType = parseAlphaCode(templateRuleElement, "cxt");
            ItemType contextItemType;
            if (contextType == null) {
                contextItemType = AnyItemType.INSTANCE;
            } else {
                contextItemType = contextType.getPrimaryType();
            }

            NodeInfo matchElement = getChildWithRole(templateRuleElement, "match");
            Pattern match = loadPattern(matchElement);

            localBindings = new Stack<>();
            TemplateRule template = config.makeTemplateRule();
            template.setMatchPattern(match);
            template.setStackFrameMap(new SlotManager(slots));
            template.setPackageData(pack);
            template.setRequiredType(parseAlphaCode(templateRuleElement, "as"));
            template.setDeclaredStreamable(streamable);
            template.setContextItemRequirements(contextItemType, tflags.contains("s") ? Optionality.OPTIONAL : Optionality.PROHIBITED);
            NodeInfo bodyElement = getChildWithRole(templateRuleElement, "action");
            if (bodyElement == null) {
                template.setBody(Literal.makeEmptySequence());
            } else {
                Expression body = loadExpression(bodyElement);
                template.setBody(body);
                RetainedStaticContext rsc = body.getRetainedStaticContext();
                body.setRetainedStaticContext(rsc); // to propagate it to the subtree
            }
            Rule rule = mode.makeRule(match, template, precedence, minImportPrecedence, priority, sequence, part);
            rule.setRank(rank);
            mode.addRule(match, rule);
            mode.setHasRules(true);
        }

        addCompletionAction(CSharp.methodRef(mode::prepareStreamability));


        return mode;

    }

    private void readAccumulators(NodeInfo packageElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo accElement;
        SequenceIterator iterator = packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "accumulator", config));
        while ((accElement = (NodeInfo) iterator.next()) != null) {
            StructuredQName accName = getQNameAttribute(accElement, "name");
            Accumulator acc = new Accumulator();
            Component component = Component.makeComponent(acc, Visibility.PRIVATE, VisibilityProvenance.DEFAULTED, pack, pack);
            acc.setDeclaringComponent(component);
            int iniSlots = getIntegerAttribute(accElement, "slots");
            acc.setSlotManagerForInitialValueExpression(new SlotManager(iniSlots));
            acc.setAccumulatorName(accName);
            String binds = accElement.getAttributeValue(NamespaceUri.NULL, "binds");
            externalReferences.put(component, binds);
            boolean streamable = "1".equals(accElement.getAttributeValue(NamespaceUri.NULL, "streamable"));
            String flags = accElement.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean universal = flags != null && flags.contains("u");
            acc.setDeclaredStreamable(streamable);
            acc.setUniversallyApplicable(universal);
            Expression init = getExpressionWithRole(accElement, "init");
            acc.setInitialValueExpression(init);
            NodeInfo pre = getChild(accElement, 1);
            readAccumulatorRules(acc, pre);
            NodeInfo post = getChild(accElement, 2);
            readAccumulatorRules(acc, post);
            pack.getAccumulatorRegistry().addAccumulator(acc);
        }

    }

    private void readAccumulatorRules(Accumulator acc, NodeInfo owner) throws XPathException {
        SequenceIterator iterator = owner.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "accRule", config));
        NodeInfo accRuleElement;
        boolean preDescent = owner.getLocalPart().equals("pre");
        SimpleMode mode = preDescent ? acc.getPreDescentRules() : acc.getPostDescentRules();
        int patternSlots = getIntegerAttribute(owner, "slots");
        mode.setStackFrameSlotsNeeded(patternSlots);
        while ((accRuleElement = (NodeInfo) iterator.next()) != null) {
            int slots = getIntegerAttribute(accRuleElement, "slots");
            int rank = getIntegerAttribute(accRuleElement, "rank");
            String flags = accRuleElement.getAttributeValue(NamespaceUri.NULL, "flags");
            SlotManager sm = new SlotManager(slots);
            Pattern pattern = getFirstChildPattern(accRuleElement);
            Expression select = getSecondChildExpression(accRuleElement);
            AccumulatorRule rule = new AccumulatorRule(select, sm, !preDescent);
            if (flags != null && flags.contains("c")) {
                rule.setCapturing(true);
            }
            mode.addRule(pattern, mode.makeRule(pattern, rule, rank, 0, rank, 0, 0));
        }
        mode.computeRankings(1);
    }

    private void readOutputProperties(NodeInfo packageElement) {
        StylesheetPackage pack = packStack.peek();
        NodeInfo outputElement;
        SequenceIterator iterator = packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "output", config));
        while ((outputElement = (NodeInfo) iterator.next()) != null) {
            StructuredQName outputName = getQNameAttribute(outputElement, "name");
            Properties props = new Properties();
            NodeInfo propertyElement;
            SequenceIterator iterator1 = outputElement.iterateChildAxis(
                    NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "property", config));
            while ((propertyElement = (NodeInfo) iterator1.next()) != null) {
                String name = propertyElement.getAttributeValue(NamespaceUri.NULL, "name");
                if (name.startsWith("Q{")) {
                    name = name.substring(1);
                }
                if (name.equals("{http://saxon.sf.net/}stylesheet-version")) {
                    // may be found in old SEF files
                    name = SaxonOutputKeys.SPEC_VERSION;
                }
                String value = propertyElement.getAttributeValue(NamespaceUri.NULL, "value");
                if (name.startsWith("{http://saxon.sf.net/}") && !name.equals(SaxonOutputKeys.SPEC_VERSION)) {
                    needsPELicense("Saxon output properties");
                }
                props.setProperty(name, value);
            }
            if (outputName == null) {
                pack.setDefaultOutputProperties(props);
            } else {
                pack.setNamedOutputProperties(outputName, props);
            }
        }
    }

    private void readCharacterMaps(NodeInfo packageElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo charMapElement;
        SequenceIterator iterator = packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "charMap", config));
        while ((charMapElement = (NodeInfo) iterator.next()) != null) {
            StructuredQName mapName = getQNameAttribute(charMapElement, "name");
            NodeInfo mappingElement;
            SequenceIterator iterator1 = charMapElement.iterateChildAxis(
                    NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "m", config));
            IntHashMap<String> map = new IntHashMap<>();
            while ((mappingElement = (NodeInfo) iterator1.next()) != null) {
                int c = getIntegerAttribute(mappingElement, "c");
                String s = mappingElement.getAttributeValue(NamespaceUri.NULL, "s");
                map.put(c, s);
            }
            CharacterMap characterMap = new CharacterMap(mapName, map);
            pack.getCharacterMapIndex().putCharacterMap(mapName, characterMap);
        }
    }

    private void readSpaceStrippingRules(NodeInfo packageElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo element;
        SequenceIterator iterator = packageElement.iterateChildAxis(NodeKindType.ELEMENT);
        while ((element = (NodeInfo) iterator.next()) != null) {
            String s = element.getLocalPart();
            switch (s) {
                case "strip.all":
                    pack.setStripperRules(AllElementsSpaceStrippingRule.INSTANCE);
                    pack.setStripsWhitespace(true);
                    break;
                case "strip.none":
                    pack.setStripperRules(NoElementsSpaceStrippingRule.INSTANCE);
                    break;
                case "strip":
                    SequenceIterator iterator2 = element.iterateChildAxis(NodeKindType.ELEMENT);
                    NodeInfo element2;
                    SelectedElementsSpaceStrippingRule rules = new SelectedElementsSpaceStrippingRule(false);
                    while ((element2 = (NodeInfo) iterator2.next()) != null) {
                        Stripper.StripRuleTarget which = element2.getLocalPart().equals("s") ? Stripper.STRIP : Stripper.PRESERVE;
                        String value = element2.getAttributeValue(NamespaceUri.NULL, "test");
                        NodeTest t;
                        if (value.equals("*")) {
                            t = NodeKindType.ELEMENT;
                        } else {
                            // See bug 4096: this is not a true item type, it also allows *:name and name:*
                            t = (NodeTest) parseAlphaCodeForItemType(element2, "test");
                        }
                        int prec = getIntegerAttribute(element2, "prec");
                        NodeTestPattern pat = new NodeTestPattern(t);
                        rules.addRule(pat, which, prec, prec);
                    }
                    pack.setStripperRules(rules);
                    pack.setStripsWhitespace(true);
                    break;
            }

        }
    }

    private void readDecimalFormats(NodeInfo packageElement) throws XPathException {
        NodeInfo formatElement;
        DecimalFormatManager decimalFormatManager = packStack.peek().getDecimalFormatManager();
        SequenceIterator iterator = packageElement.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "decimalFormat", config));

        while ((formatElement = (NodeInfo) iterator.next()) != null) {
            StructuredQName name = getQNameAttribute(formatElement, "name");
            DecimalSymbols symbols;
            if (name == null) {
                symbols = decimalFormatManager.getDefaultDecimalFormat();
            } else {
                symbols = decimalFormatManager.obtainNamedDecimalFormat(name);
            }
            SequenceIterator attributeIter = formatElement.iterateAttributeAxis(AnyGNode.TEST);
            NodeInfo att;
            while ((att = (NodeInfo) attributeIter.next()) != null) {
                if (DecimalSymbols.isValidPropertyName(att.getLocalPart())) {
                    // In releases before 13.x, single-character properties were output as a decimal codepoint.
                    UnicodeString val = att.getUnicodeStringValue();
                    if (val.length() >= 2 && allNumeric.matches(val)) {
                        val = new UnicodeChar(Integer.parseInt(val.toString()));
                    }
                    symbols.setProperty(att.getLocalPart(), val);
                }
            }
            symbols.setHostLanguage(HostLanguage.XSLT, 31);

        }
    }

    private static ARegularExpression allNumeric = ARegularExpression.compile("[0-9]+", "");


    /**
     * Get the n'th element child of an element (zero-based)
     *
     * @param parent the parent element
     * @param n      which child to get (zero-based)
     * @return the n'th child, or null if not available
     */
    public NodeInfo getChild(NodeInfo parent, int n) {
        SequenceIterator iter = parent.iterateChildAxis(NodeKindType.ELEMENT);
        NodeInfo node = (NodeInfo) iter.next();
        for (int i = 0; i < n; i++) {
            node = (NodeInfo) iter.next();
        }
        return node;
    }

    public NodeInfo getChildWithRole(NodeInfo parent, String role) {
        SequenceIterator iter = parent.iterateChildAxis(NodeKindType.ELEMENT);
        NodeInfo node;
        while ((node = (NodeInfo) iter.next()) != null) {
            String roleAtt = node.getAttributeValue(NamespaceUri.NULL, "role");
            if (role.equals(roleAtt)) {
                return node;
            }
        }
        return null;
    }

    public Expression getFirstChildExpression(NodeInfo parent) throws XPathException {
        NodeInfo node = (NodeInfo) parent.iterateChildAxis(NodeKindType.ELEMENT).next();
        return loadExpression(node);
    }

    public Expression getSecondChildExpression(NodeInfo parent) throws XPathException {
        NodeInfo node = getChild(parent, 1);
        return loadExpression(node);
    }

    public Expression getNthChildExpression(NodeInfo parent, int n) throws XPathException {
        NodeInfo node = getChild(parent, n);
        return loadExpression(node);
    }

    public Expression getExpressionWithRole(NodeInfo parent, String role) throws XPathException {
        NodeInfo node = getChildWithRole(parent, role);
        return node == null ? null : loadExpression(node);
    }

    public Expression loadExpression(NodeInfo element) throws XPathException {
        if (element == null) {
            return null;
        }
        String tag = element.getLocalPart();
        ExpressionLoader loader = eMap.get(tag);
        if (loader == null) {
            String message = "Cannot load expression with tag " + tag;
            String req = licensableConstructs.get(tag);
            if (req != null) {
                message += ". The stylesheet uses Saxon-" + req + " features";
            }
            throw new XPathException(message, SaxonErrorCode.SXPK0002);
        } else {
            RetainedStaticContext rsc = makeRetainedStaticContext(element);
            contextStack.push(rsc);
            Expression exp = loader.loadFrom(this, element);
            exp.setRetainedStaticContextLocally(rsc);
            contextStack.pop();

            exp.setLocation(makeLocation(element));
            return exp;
        }
    }

    private Location makeLocation(NodeInfo element) {
        String lineAtt = getInheritedAttribute(element, "line");
        String moduleAtt = getInheritedAttribute(element, "module");
        if (lineAtt != null && moduleAtt != null) {
            int line = Integer.parseInt(lineAtt);
            return allocateLocation(moduleAtt, line);
        } else {
            return Loc.NONE;
        }
    }

    public RetainedStaticContext makeRetainedStaticContext(NodeInfo element) {
        StylesheetPackage pack = packStack.peek();
        String baseURIAtt = element.getAttributeValue(NamespaceUri.NULL, "baseUri");
        String defaultCollAtt = element.getAttributeValue(NamespaceUri.NULL, "defaultCollation");
        String defaultElementNS = element.getAttributeValue(NamespaceUri.NULL, "defaultElementNS");
        String nsAtt = element.getAttributeValue(NamespaceUri.NULL, "ns");
        String versionAtt = element.getAttributeValue(NamespaceUri.NULL, "vn");
        String schemaRoleAtt = element.getAttributeValue(NamespaceUri.NULL, "schemaRole");
        if (baseURIAtt != null || defaultCollAtt != null || nsAtt != null ||
                versionAtt != null || defaultElementNS != null || schemaRoleAtt != null ||
                contextStack.peek().getDecimalFormatManager() == null // implies not fully initialized
        ) {
            RetainedStaticContext rsc = new RetainedStaticContext(config, pack);
            rsc.setDefaultCollationName(defaultCollAtt == null ? NamespaceConstant.CODEPOINT_COLLATION_URI : defaultCollAtt);
            if (baseURIAtt != null) {
                rsc.setStaticBaseUriString(baseURIAtt);
            } else if (relocatableBase != null) {
                rsc.setStaticBaseUriString(relocatableBase);
            } else {
                String base = Navigator.getInheritedAttributeValue(element, NamespaceUri.NULL, "baseUri");
                if (base != null) {
                    rsc.setStaticBaseUriString(base);
                }
            }
            if (nsAtt == null) {
                nsAtt = Navigator.getInheritedAttributeValue(element, NamespaceUri.NULL, "ns");
            }
            if (nsAtt != null && !nsAtt.isEmpty()) {
                rsc.setNamespaces(fromExportedNamespaces(nsAtt));
            }
            if (defaultElementNS == null) {
                defaultElementNS = Navigator.getInheritedAttributeValue(element, NamespaceUri.NULL, "defaultElementNS");
            }
            if (defaultElementNS != null) {
                rsc.setDefaultElementNamespace(NamespaceUri.of(defaultElementNS));
            }
            if (schemaRoleAtt == null) {
                rsc.setImportedSchema(packStack.peek().getImportedSchema(""));
            } else {
                Schema schema = packStack.peek().getImportedSchema(schemaRoleAtt);
                if (schema == null) {
                    throw new IllegalStateException("Cannot find schema with role " + schemaRoleAtt);
                }
                rsc.setImportedSchema(schema);
            }
            rsc.setDecimalFormatManager(packStack.peek().getDecimalFormatManager());
            return rsc;
        } else {
            return contextStack.peek();
        }
    }

    public static NamespaceMap fromExportedNamespaces(String nsAtt) {
        NamespaceMap map = NamespaceMap.emptyMap();
        if (nsAtt != null && !nsAtt.isEmpty()) {
            String[] namespaces = nsAtt.split(" ");
            for (String ns : namespaces) {
                int eq = ns.indexOf('=');
                if (eq < 0) {
                    throw new IllegalStateException("ns=" + nsAtt);
                }
                String prefix = ns.substring(0, eq);
                String uri = ns.substring(eq + 1);
                if (uri.equals("~")) {
                    uri = NamespaceConstant.getUriForConventionalPrefix(prefix);
                }
                map = map.put(prefix, NamespaceUri.of(uri));
            }
        }
        return map;
    }

    private Pattern getFirstChildPattern(NodeInfo parent) throws XPathException {
        NodeInfo node = (NodeInfo) parent.iterateChildAxis(NodeKindType.ELEMENT).next();
        return loadPattern(node);
    }

    private Pattern getSecondChildPattern(NodeInfo parent) throws XPathException {
        NodeInfo node = getChild(parent, 1);
        return loadPattern(node);
    }

    public Pattern getPatternWithRole(NodeInfo parent, String role) throws XPathException {
        NodeInfo node = getChildWithRole(parent, role);
        return node == null ? null : loadPattern(node);
    }

    private Pattern loadPattern(NodeInfo element) throws XPathException {
        String tag = element.getLocalPart();
        PatternLoader loader = pMap.get(tag);
        if (loader == null) {
            //System.err.println("Cannot load pattern with tag " + tag);
            throw new XPathException("Cannot load pattern with tag " + tag, SaxonErrorCode.SXPK0002);
        } else {
            Pattern pat = loader.loadFrom(this, element);
            pat.setLocation(makeLocation(element));
            pat.setRetainedStaticContext(makeRetainedStaticContext(element));
            if (pat instanceof GeneralNodePattern) {
                addCompletionAction(CSharp.methodRef(((GeneralNodePattern) pat)::makeTopNodeEquivalent));
            }
            return pat;
        }
    }

    public SchemaType getTypeAttribute(NodeInfo element, String attName) {
        String val = element.getAttributeValue(NamespaceUri.NULL, attName);
        if (val == null) {
            return null;
        }
        if (val.startsWith("xs:")) {
            return getSchema().getSchemaType(new StructuredQName("xs", NamespaceUri.SCHEMA, val.substring(3)));
        } else {
            StructuredQName name = getQNameAttribute(element, attName);
            return getSchema().getSchemaType(name);
        }
    }

    public StructuredQName getQNameAttribute(NodeInfo element, String localName) {
        String val = element.getAttributeValue(NamespaceUri.NULL, localName);
        if (val == null) {
            return null;
        }
        return StructuredQName.fromEQName40((val));
    }

    public List<StructuredQName> getListOfQNameAttribute(NodeInfo element, String localName) throws XPathException {
        String val = element.getAttributeValue(NamespaceUri.NULL, localName);
        if (val == null) {
            return Collections.emptyList();
        }
        List<StructuredQName> result = new ArrayList<>();
        for (String s : val.split(" ")) {
            StructuredQName sq = resolveQName(s, element);
            result.add(sq);
        }
        return result;
    }

    private StructuredQName resolveQName(String val, NodeInfo element) throws XPathException {
        if (val.startsWith("Q{")) {
            return StructuredQName.fromEQName40((val));
        } else if (val.contains(":")) {
            return StructuredQName.fromLexicalQName((val), true, 0, element.getAllNamespaces());
        } else {
            return new StructuredQName("", NamespaceUri.NULL, val);
        }
    }

    /**
     * Read an integer-valued attribute
     *
     * @param element   the element on which the attribute appears
     * @param localName the name of the attribute
     * @return the integer value of the attribute if present and correct; or Integer.MIN_VALUE if absent
     * @throws XPathException if the attribute is present but not integer-valued.
     */

    public int getIntegerAttribute(NodeInfo element, String localName) throws XPathException {
        String val = element.getAttributeValue(NamespaceUri.NULL, localName);
        if (val == null) {
            return Integer.MIN_VALUE;
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw new XPathException("Expected integer value for " +
                                             element.getDisplayName() + "/" + localName +
                                             ", found '" + val + "'", SaxonErrorCode.SXPK0002);
        }
    }

    public long getLongAttribute(NodeInfo element, String localName) throws XPathException {
        String val = element.getAttributeValue(NamespaceUri.NULL, localName);
        if (val == null) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            throw new XPathException("Expected long integer value for " +
                                             element.getDisplayName() + "/" + localName +
                                             ", found '" + val + "'", SaxonErrorCode.SXPK0002);
        }
    }



    public String getInheritedAttribute(NodeInfo element, String localName) {
        while (element != null) {
            String val = element.getAttributeValue(NamespaceUri.NULL, localName);
            if (val != null) {
                return val;
            }
            element = (NodeInfo) element.getParent();
        }
        return null;
    }

    /**
     * Parse the SequenceType whose value is held in the attribute named "name"
     *
     * @param element the element containing this attribute
     * @param name    the local name of the attribute
     * @return the SequenceType held in the content of the attribute, or "item()*" if the attribute is absent
     * @throws XPathException if the sequence type is invalid
     */

    public SequenceType parseSequenceType(NodeInfo element, String name) throws XPathException {
        IndependentContext env = makeStaticContext(element);
        String attValue = element.getAttributeValue(NamespaceUri.NULL, name);
        if (attValue == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            return parser.parseSequenceType(attValue, env);
        }
    }

    /**
     * Parse the SequenceType whose value is held in the attribute named "name", as an alphacode
     *
     * @param element the element containing this attribute
     * @param name    the local name of the attribute
     * @return the SequenceType held in the content of the attribute, or "item()*" if the attribute is absent
     * @throws XPathException if the sequence type is invalid
     */

    public SequenceType parseAlphaCode(NodeInfo element, String name) throws XPathException {
        String attValue = element.getAttributeValue(NamespaceUri.NULL, name);
        if (attValue == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            try {
                return AlphaCode.toSequenceType(attValue, config, getSchema());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new XPathException("Invalid alpha code " + element.getDisplayName() + "/@" + name + "='" + attValue + "': " + e.getMessage());
            }
        }
    }

    public ItemType parseAlphaCodeForItemType(NodeInfo element, String name) throws XPathException {
        String attValue = element.getAttributeValue(NamespaceUri.NULL, name);
        if (attValue == null) {
            return AnyItemType.INSTANCE;
        } else {
            try {
                return AlphaCode.toItemType(attValue, config, getSchema());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new XPathException("Invalid alpha code " + element.getDisplayName() + "/@" + name + "='" + attValue + "': " + e.getMessage());
            }
        }
    }

    public NodeTest parseAlphaCodeForNodeTest(NodeInfo element, String name, int axis) throws XPathException {
        String attValue = element.getAttributeValue(NamespaceUri.NULL, name);
        if (attValue == null) {
            return AnyGNodeType.getInstance();
        } else {
            return parseAlphaCodeForNodeTest(attValue, axis);
        }

    }

    private IndependentContext makeStaticContext(NodeInfo element) {
        StylesheetPackage pack = packStack.peek();
        IndependentContext env = new IndependentContext(config);
        final NamespaceResolver resolver = element.getAllNamespaces();
        env.setNamespaceResolver(resolver);
        env.setXPathLanguageLevel(pack.getHostLanguageVersion());
        env.setImportedSchema(pack.getImportedSchema(""));
        parser.setQNameParser(parser.getQNameParser().withNamespaceResolver(resolver));
        return env;
    }

    private NodeTest parseAlphaCodeForNodeTest(String attValue, int axis) throws XPathException {
        if (attValue.startsWith("$ST(")) {
            // This is a SelectorTest
            int firstComma = attValue.indexOf(',');
            int secondComma = attValue.indexOf(',', firstComma + 1);
            int closeParen = attValue.indexOf(')', secondComma + 1);
            String names = attValue.substring(4, firstComma);
            String asNCName = attValue.substring(firstComma + 1, secondComma);
            String kind = attValue.substring(secondComma + 1, closeParen);
            return new SelectorTest(
                    parseQNameTestList(names, getConfiguration().getNamePool()),
                    asNCName.equals("true"),
                    Integer.parseInt(kind));
        } else if (attValue.startsWith("$NT-")) {
            return parseCombinedNodeTest(attValue, axis);
        } else {
            try {
                ItemType it = AlphaCode.toItemType(attValue, config, getSchema());
                if (it instanceof NodeTest nt) {
                    return nt;
                }
                if (it instanceof ChoiceItemType cit) {
                    MultipleNodeKindTest test = cit.toMultipleNodeKindTest();
                    if (test != null) {
                        return test;
                    }
                    List<? extends ItemType> memberTypes = cit.getMemberTypes();
                    if (memberTypes.size() == 2) {
                        if (memberTypes.get(0) == AnyJNodeType.getInstance() && memberTypes.get(1) == NodeKindType.ELEMENT) {
                            return new NodeTestStar(AxisInfo.principalNodeType[axis]);
                        }
                        if (memberTypes.get(1) == AnyJNodeType.getInstance() && memberTypes.get(0) == NodeKindType.ELEMENT) {
                            return new NodeTestStar(AxisInfo.principalNodeType[axis]);
                        }
                        if (memberTypes.get(0) == AnyJNodeType.getInstance() && memberTypes.get(1) == NodeKindType.ATTRIBUTE) {
                            return new NodeTestStar(AxisInfo.principalNodeType[axis]);
                        }
                        if (memberTypes.get(1) == AnyJNodeType.getInstance() && memberTypes.get(0) == NodeKindType.ATTRIBUTE) {
                            return new NodeTestStar(AxisInfo.principalNodeType[axis]);
                        }
                    }
                    List<QNameTest> qNameTests = new ArrayList<>();
                    boolean failed = false;
                    int kind = -1;
                    for (ItemType m : memberTypes) {
                        if (m instanceof NamedXNodeType namedXNodeType) {
                            if (kind == -1) {
                                kind = namedXNodeType.getNodeKind();
                            } else if (kind != namedXNodeType.getNodeKind()) {
                                failed = true;
                                break;
                            }
                            qNameTests.add(namedXNodeType.getQNameTest());
                        } else {
                            failed = true;
                            break;
                        }
                    }
                    if (!failed) {
                        UnionQNameTest union = new UnionQNameTest(qNameTests);
                        return new NamedXNodeType(kind, union, getConfiguration());
                    }
                }
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new XPathException("Invalid alpha code " + attValue + "': " + e.getMessage());
            }
        }
        throw new XPathException("Bad alpha code "+attValue);
    }

    private NodeTest parseCombinedNodeTest(String value, int axis) throws XPathException {
       int lparen = value.indexOf('(');
       String op = value.substring(4, lparen);
       int hash1 = value.indexOf('#', lparen + 1);
       int len1 = Integer.parseInt(value.substring(lparen + 1, hash1));
       int end1 = hash1 + 1 + len1;
       String nt1 = value.substring(hash1 + 1, end1);
       NodeTest test1 = parseAlphaCodeForNodeTest(nt1, axis);
       int comma = value.indexOf(',', end1);
       int hash2 = value.indexOf('#', comma + 1);
       int len2 = Integer.parseInt(value.substring(comma + 1, hash2));
       int end2 = hash2 + 1 + len2;
       String nt2 = value.substring(hash2 + 1, end2);
       NodeTest test2 = parseAlphaCodeForNodeTest(nt2, axis);
       OperatorSymbol symbol = switch(op) {
           case "union" -> OperatorSymbol.UNION;
           case "intersect" -> OperatorSymbol.INTERSECT;
           case "except" -> OperatorSymbol.EXCEPT;
           default -> throw new XPathException("Invalid operator symbol " + op);
       };
       return new CombinedNodeTest(test1, symbol, test2);
    }

    /**
     * Parse the ItemType whose value is held in the attribute named "name"
     *
     * @param element the element containing this attribute
     * @param attName the local name of the attribute
     * @return the SequenceType held in the content of the attribute, or "item()" if the attribute is absent
     * @throws XPathException if the item type is invalid
     */

    public ItemType parseItemTypeAttribute(NodeInfo element, String attName) throws XPathException {
        String attValue = element.getAttributeValue(NamespaceUri.NULL, attName);
        if (attValue == null) {
            return AnyItemType.INSTANCE;
        }
        return parseItemType(element, attValue);
    }

    private ItemType parseItemType(NodeInfo element, String attValue) throws XPathException {
        IndependentContext env = makeStaticContext(element);
        return parser.parseItemType(attValue, env);
    }

    public AtomicComparer makeAtomicComparer(String name, NodeInfo element) throws XPathException {
        if (name.equals("CCC")) {
            return CodepointCollatingComparer.getInstance();
        } else if (name.equals("CAVC")) {
            return ContextFreeAtomicComparer.getInstance();
        } else if (name.equals("CFAC40")) {
            return ContextFreeAtomicComparer40.getInstance();
        } else if (name.startsWith("GAC|")) {
            StringCollator collator = config.getCollation(name.substring(4));
            int version = packStack.peek().getHostLanguageVersion();
            return new GenericAtomicComparer(collator, version, null);
        } else if (name.equals("CalVC")) {
            return new CalendarValueComparer(null);
        } else if (name.equals("EQC")) {
            return EqualityComparer.getInstance();
        } else if (name.equals("NC")) {
            return NumericComparer.getInstance();
        } else if (name.equals("NC11")) {
            return NumericComparer11.getInstance();
        } else if (name.equals("QUNC")) {
            return new UntypedNumericComparer();
        } else if (name.equals("DblSC")) {
            return DoubleSortComparer.getInstance();
        } else if (name.equals("DecSC")) {
            return DecimalSortComparer.getDecimalSortComparerInstance();
        } else if (name.startsWith("CAC|")) {
            StringCollator collator = config.getCollation(name.substring(4));
            return new CollatingAtomicComparer(collator);
        } else if (name.startsWith("AtSC|")) {
            int nextBar = name.indexOf('|', 5);
            String fps = name.substring(5, nextBar);
            int fp = Integer.parseInt(fps);
            String collName = name.substring(nextBar + 1);
            int version = packStack.peek().getHostLanguageVersion();
            return AtomicSortComparer.makeSortComparer(
                    config.getCollation(collName), fp, version, new EarlyEvaluationContext(config));
        } else if (name.startsWith("DESC|")) {
            AtomicComparer base = makeAtomicComparer(name.substring(5), element);
            return new DescendingComparer(base);
        } else if (name.startsWith("TEXT|")) {
            AtomicComparer base = makeAtomicComparer(name.substring(5), element);
            return new TextComparer(base);
        } else {
            throw new XPathException("Unknown comparer " + name, SaxonErrorCode.SXPK0002);
        }
    }

    /**
     * Load a set of sort key definitions
     *
     * @param element the sort element containing the sort key definitions
     * @return the list of sort key definitions
     */

    private SortKeyDefinitionList loadSortKeyDefinitions(NodeInfo element) throws XPathException {
        List<SortKeyDefinition> skdl = new ArrayList<>(4);
        NodeInfo sortKeyElement;
        SequenceIterator iterator = element.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "sortKey", config));
        int version = packStack.peek().getHostLanguageVersion();
        while ((sortKeyElement = (NodeInfo) iterator.next()) != null) {
            SortKeyDefinition skd = new SortKeyDefinition(version);
            String compAtt = sortKeyElement.getAttributeValue(NamespaceUri.NULL, "comp");
            if (compAtt != null) {
                AtomicComparer ac = makeAtomicComparer(compAtt, sortKeyElement);
                skd.setFinalComparator(ac);
            }
            skd.setSortKey(getExpressionWithRole(sortKeyElement, "select"), true);
            skd.setOrder(getExpressionWithRole(sortKeyElement, "order"));
            skd.setLanguage(getExpressionWithRole(sortKeyElement, "lang"));
            skd.setCollationNameExpression(getExpressionWithRole(sortKeyElement, "collation"));
            skd.setCaseOrder(getExpressionWithRole(sortKeyElement, "caseOrder"));
            skd.setStable(getExpressionWithRole(sortKeyElement, "stable"));
            skd.setDataTypeExpression(getExpressionWithRole(sortKeyElement, "dataType"));
            skdl.add(skd);
        }
        return new SortKeyDefinitionList(skdl.toArray(new SortKeyDefinition[0]));
    }

    private WithParam[] loadWithParams(NodeInfo element, Expression parent, boolean needTunnel) throws XPathException {
        List<WithParam> wps = new ArrayList<>(4);
        NodeInfo wpElement;
        SequenceIterator iterator = element.iterateChildAxis(
                NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "withParam", config));
        while ((wpElement = (NodeInfo) iterator.next()) != null) {
            String flags = wpElement.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean isTunnel = flags != null && flags.contains("t");
            if (needTunnel == isTunnel) {
                WithParam wp = new WithParam();
                wp.setVariableQName(getQNameAttribute(wpElement, "name"));
                wp.setSelectExpression(parent, getFirstChildExpression(wpElement));
                wp.setRequiredType(parseAlphaCode(wpElement, "as"));
                wp.setTypeChecked(flags != null && flags.contains("c"));
                wps.add(wp);
            }
        }
        return wps.toArray(new WithParam[0]);
    }

    private Properties importProperties(String value) {
        try {
            StringReader reader = new StringReader(value);
            Properties props = new Properties();
            LineNumberReader lnr = new LineNumberReader(reader);
            String line;
            while ((line = lnr.readLine()) != null) {
                int eq = line.indexOf('=');
                String key = line.substring(0, eq);
                String val = eq == line.length() - 1 ? "" : line.substring(eq + 1);
                if (key.equals("item-separator") || key.equals("Q" + SaxonOutputKeys.NEWLINE)) {
                    try {
                        val = ExpressionPresenter.jsUnescape(val);
                    } catch (Exception ignored) {
                        // No action, leave unescaped
                    }
                }
                if (key.startsWith("Q{")) {
                    key = key.substring(1);
                }
                props.setProperty(key, val);
            }
            return props;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @FunctionalInterface
    @CSharpDelegate(true)
    public interface ExpressionLoader {
        Expression loadFrom(PackageLoaderHE loader, NodeInfo element) throws XPathException;
    }

    @FunctionalInterface
    @CSharpDelegate(true)
    public interface PatternLoader {
        Pattern loadFrom(PackageLoaderHE loader, NodeInfo element) throws XPathException;
    }

    protected static final Map<String, ExpressionLoader> eMap = new HashMap<>(200);

    protected static final Map<String, String> licensableConstructs = new HashMap<>(30);

    static {
        licensableConstructs.put("gcEE", "EE");
        licensableConstructs.put("indexedFilter", "EE");
        licensableConstructs.put("indexedFilter2", "EE");
        licensableConstructs.put("indexedLookup", "EE");
        licensableConstructs.put("stream", "EE");
        licensableConstructs.put("switch", "EE");

        licensableConstructs.put("assign", "PE");
        licensableConstructs.put("do", "PE");
        licensableConstructs.put("javaCall", "PE");
        licensableConstructs.put("while", "PE");
    }

    static {

        eMap.put("acFnRef", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            SchemaType type = loader.getSchema().getSchemaType(name);

            NamespaceResolver resolver = null;

            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags == null) {
                flags = "";
            }
            if (flags.contains("l")) {
                if (type instanceof ListType lt) {
                    if (lt.isNamespaceSensitive()) {
                        RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
                        resolver = rsc.getNamespaceMap();
                    }
                    ListConstructorFunction fn = new ListConstructorFunction(lt, resolver, flags.contains("e"));
                    return new FunctionLiteral(fn);
                } else {
                    throw new XPathException("No list type " + name.getEQName() + " found");
                }
            } else {
                if (type instanceof AtomicType at) {
                    if (at.isNamespaceSensitive()) {
                        RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
                        resolver = rsc.getNamespaceMap();
                    }
                    AtomicConstructorFunction fn = new AtomicConstructorFunction(at, resolver);
                    return new FunctionLiteral(fn);
                } else {
                    throw new XPathException("No atomic type " + name.getEQName() + " found");
                }
            }
        });

        eMap.put("among", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new SingletonIntersectExpression(lhs, OperatorSymbol.INTERSECT, rhs);
        });

        eMap.put("analyzeString", (loader, element) -> {
            Expression select = loader.getExpressionWithRole(element, "select");
            Expression regex = loader.getExpressionWithRole(element, "regex");
            Expression flags = loader.getExpressionWithRole(element, "flags");
            Expression matching = loader.getExpressionWithRole(element, "matching");
            Expression nonMatching = loader.getExpressionWithRole(element, "nonMatching");
            AnalyzeString instr = new AnalyzeString(select, regex, flags, matching, nonMatching, null);
            instr.precomputeRegex(loader.getConfiguration(), null);
            return instr;
        });

        eMap.put("and", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new AndExpression(lhs, rhs);
        });

        eMap.put("applyImports", (loader, element) -> {
            ApplyImports inst = new ApplyImports();

            WithParam[] actuals = loader.loadWithParams(element, inst, false);
            WithParam[] tunnels = loader.loadWithParams(element, inst, true);
            inst.setActualParams(actuals);
            inst.setTunnelParams(tunnels);
            return inst;
        });

        eMap.put("applyT", (loader, element) -> {
            StylesheetPackage pack = loader.packStack.peek();
            Expression select = loader.getFirstChildExpression(element);
            StructuredQName modeAtt = loader.getQNameAttribute(element, "mode");
            SimpleMode mode;
            if (modeAtt != null) {
                mode = (SimpleMode) pack.getRuleManager().obtainMode(modeAtt, true);
            } else {
                mode = (SimpleMode) pack.getRuleManager().obtainMode(null, true);
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags == null) {
                flags = "";
            }

            boolean useCurrentMode = flags.contains("c");
            boolean useTailRecursion = flags.contains("t");
            boolean implicitSelect = flags.contains("i");
            boolean inStreamableConstruct = flags.contains("d");

            ApplyTemplates inst = new ApplyTemplates(
                    select, useCurrentMode, useTailRecursion, implicitSelect, inStreamableConstruct, mode, loader.packStack.peek().getRuleManager());
            Expression sep = loader.getExpressionWithRole(element, "separator");
            if (sep != null) {
                inst.setSeparatorExpression(sep);
            }
            WithParam[] actuals = loader.loadWithParams(element, inst, false);
            WithParam[] tunnels = loader.loadWithParams(element, inst, true);
            inst.setActualParams(actuals);
            inst.setTunnelParams(tunnels);

            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            inst.setBindingSlot(bindingSlot);

            return inst;
        });

        eMap.put("arith", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            final String code = element.getAttributeValue(NamespaceUri.NULL, "calc");
            Calculator calc = Calculator.reconstructCalculator(code);
            int operator = Calculator.operatorFromCode(code.charAt(1));
            OperatorSymbol token = Calculator.getArithmeticOperatorSymbol(operator);
            ArithmeticExpression exp = new ArithmeticExpression(lhs, token, rhs);
            exp.setCalculator(calc);
            return exp;
        });

        eMap.put("arith10", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            final String code = element.getAttributeValue(NamespaceUri.NULL, "calc");
            Calculator calc = Calculator.reconstructCalculator(code);
            int operator = Calculator.operatorFromCode(code.charAt(1));
            OperatorSymbol token = Calculator.getArithmeticOperatorSymbol(operator);
            ArithmeticExpression10 exp = new ArithmeticExpression10(lhs, token, rhs);
            exp.setCalculator(calc);
            return exp;
        });

        eMap.put("array", (loader, element) -> {
            List<Expression> children = getChildExpressionList(loader, element);
            List<GroundedValue> values = new ArrayList<>(children.size());
            for (Expression child : children) {
                values.add(((Literal) child).getGroundedValue());
            }
            return Literal.makeLiteral(new SimpleArrayItem(values));
        });

        eMap.put("arrayBlock", (loader, element) -> {
            List<Expression> children = getChildExpressionList(loader, element);
            return new SquareArrayConstructor(children);
        });

        eMap.put("atomic", (loader, element) -> {
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "val");
            ItemType type = loader.parseAlphaCodeForItemType(element, "type");
            if (type instanceof AtomicType at) {
                AtomicValue val = at.getStringConverter(loader.config.getConversionRules())
                        .convertString(StringView.of(valAtt).tidy()).asAtomic();
                return Literal.makeLiteral(val);
            } else if (type instanceof EnumerationUnionType enut) {
                return Literal.makeLiteral(new StringValue(valAtt));
            } else {
                throw new IllegalStateException("Unrecognized atomic type: " + type);
            }
        });

        eMap.put("atomSing", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String savedRole = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            String cardAtt = element.getAttributeValue(NamespaceUri.NULL, "card");
            boolean allowEmpty = "?".equals(cardAtt);
            return new SingletonAtomizer(body, role, allowEmpty);
        });

        eMap.put("att", (loader, element) -> {
            String displayName = element.getAttributeValue(NamespaceUri.NULL, "name");
            String[] parts;
            try {
                parts = NameChecker.getQNameParts((displayName));
            } catch (QNameException err) {
                throw new XPathException(err);
            }
            String uri = element.getAttributeValue(NamespaceUri.NULL, "nsuri");
            if (uri == null) {
                uri = "";
            }
            StructuredQName name = new StructuredQName(parts[0], NamespaceUri.of(uri), parts[1]);
            NodeName attName = new FingerprintedQName(name, loader.config.getNamePool());
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            Expression content = loader.getFirstChildExpression(element);
            FixedAttribute att = new FixedAttribute(attName, validation, (SimpleType) schemaType);
            att.setSelect(content);
            return att;
        });

        eMap.put("attVal", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            FingerprintedQName attName = new FingerprintedQName(name, loader.config.getNamePool());
            return new AttributeGetter(attName);
        });

        eMap.put("axis", (loader, element) -> {
            String axisName = element.getAttributeValue(NamespaceUri.NULL, "name");
            int axis = AxisInfo.getAxisNumber(axisName);
            NodeTest nt = loader.parseAlphaCodeForNodeTest(element, "nodeTest", axis);
            return new AxisExpression(axis, nt);
        });

        eMap.put("axisGet", (loader, element) -> {
            String axisName = element.getAttributeValue(NamespaceUri.NULL, "name");
            int axis = AxisInfo.getAxisNumber(axisName);
            Expression selector = loader.getFirstChildExpression(element);
            return new AxisGetExpression(axis, selector);
        });

        eMap.put("break", (loader, element) -> new BreakInstr());

        eMap.put("callT", (loader, element) -> {
            StylesheetPackage pack = loader.packStack.peek();
            StructuredQName name = loader.getQNameAttribute(element, "name");
            SymbolicName symbol = new SymbolicName(StandardNames.XSL_TEMPLATE, name);
            Component target = pack.getComponent(symbol);
            NamedTemplate t;
            if (target == null) {
                t = new NamedTemplate(name, pack.getConfiguration());
            } else {
                t = (NamedTemplate) target.getActor();
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean useTailRecursion = flags != null && flags.contains("t");
            boolean inStreamableConstruct = flags != null && flags.contains("d");
            CallTemplate inst = new CallTemplate(t, name, useTailRecursion, inStreamableConstruct);
            WithParam[] actuals = loader.loadWithParams(element, inst, false);
            WithParam[] tunnels = loader.loadWithParams(element, inst, true);
            inst.setActualParameters(actuals, tunnels);
            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            inst.setBindingSlot(bindingSlot);

            loader.addComponentFixup(inst);
            return inst;
        });

        eMap.put("callable", (loader, element) -> {
            String tag = element.getAttributeValue(NamespaceUri.NULL, "tag");
            CallableFunction fn = switch (tag) {
                case "mapConstructorDuplicatesAction" -> MapItem.mapConstructorDuplicatesAction;
                case "xslMapDuplicatesAction" -> MapItem.xslMapDuplicatesAction;
                case "xslRecordDuplicatesAction" -> MapItem.xslRecordDuplicatesAction;
                default -> throw new IllegalStateException("Unknown callable function tag: " + tag);
            };
            return Literal.makeLiteral(fn);
        });

        eMap.put("cast", (loader, element) -> {
            // Expect either an "as" attribute containing the alphacode of an item type,
            // or a "to" attribute containing the name of a schema type
            Expression body = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean allowEmpty = flags.contains("e");
            String toAtt = element.getAttributeValue(NamespaceUri.NULL, "to");
            StructuredQName typeName = toAtt == null ? null : StructuredQName.fromEQName40(toAtt);
            String asAtt = element.getAttributeValue(NamespaceUri.NULL, "as");
            ItemType itemType = asAtt == null ? null : loader.parseAlphaCode(element, "as").getPrimaryType();
            if (flags.contains("a")) {
                // Atomic type
                return new CastExpression(body, (AtomicType) itemType, allowEmpty);
            } else if (flags.contains("l")) {
                if (toAtt != null) {
                    SchemaType type = loader.getSchema().getSchemaType(typeName);
                    NamespaceResolver resolver = element.getAllNamespaces();
                    ListConstructorFunction ucf = new ListConstructorFunction((ListType) type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                } else {
                    SchemaType type = (SchemaType) itemType;
                    NamespaceResolver resolver = element.getAllNamespaces();
                    ListConstructorFunction ucf = new ListConstructorFunction((ListType) type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                }
            } else if (flags.contains("u")) {
                if (toAtt != null) {
                    SchemaType type = loader.getSchema().getSchemaType(typeName);
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionConstructorFunction ucf = new UnionConstructorFunction((UnionType) type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                } else {
                    LocalUnionType type = (LocalUnionType) itemType;
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionConstructorFunction ucf = new UnionConstructorFunction(type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                }
            } else {
                throw new AssertionError("Unknown simple type variety " + flags);
            }
        });

        eMap.put("castable", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean allowEmpty = flags.contains("e");
            if (flags.contains("a")) {
                SequenceType seqType = loader.parseAlphaCode(element, "as");
                return new CastableExpression(body, (AtomicType) seqType.getPrimaryType(), allowEmpty);
            } else if (flags.contains("l")) {
                StructuredQName typeName = StructuredQName.fromEQName40((element.getAttributeValue(NamespaceUri.NULL, "as")));
                SchemaType type = loader.getSchema().getSchemaType(typeName);
                NamespaceResolver resolver = element.getAllNamespaces();
                ListCastableFunction ucf = new ListCastableFunction((ListType) type, resolver, allowEmpty);
                return new StaticFunctionCall(ucf, new Expression[]{body});
            } else if (flags.contains("u")) {
                if (element.getAttributeValue(NamespaceUri.NULL, "as") != null) {
                    StructuredQName typeName = StructuredQName.fromEQName40((element.getAttributeValue(NamespaceUri.NULL, "as")));
                    SchemaType type = loader.getSchema().getSchemaType(typeName);
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionCastableFunction ucf = new UnionCastableFunction((UnionType) type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                } else {
                    LocalUnionType type = (LocalUnionType) loader.parseAlphaCode(element, "to").getPrimaryType();
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionCastableFunction ucf = new UnionCastableFunction(type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                }
            } else {
                throw new AssertionError("Unknown simple type variety " + flags);
            }
        });

        eMap.put("check", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String cardAtt = element.getAttributeValue(NamespaceUri.NULL, "card");
            int c = switch (cardAtt) {
                case "?" -> StaticProperty.ALLOWS_ZERO_OR_ONE;
                case "*" -> StaticProperty.ALLOWS_ZERO_OR_MORE;
                case "+" -> StaticProperty.ALLOWS_ONE_OR_MORE;
                case "°", "0" -> StaticProperty.ALLOWS_ZERO;
                case "1" -> StaticProperty.EXACTLY_ONE;
                default -> throw new IllegalStateException("Occurrence indicator: '" + cardAtt + "'");
            };
            String savedRole = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            return CardinalityChecker.makeCardinalityChecker(body, c, role);
        });

        eMap.put("choose", (loader, element) -> {
            List<Expression> conditions = new ArrayList<>();
            List<Expression> actions = new ArrayList<>();
            SequenceIterator iter = element.iterateChildAxis(NodeKindType.ELEMENT);
            NodeInfo child;
            boolean odd = true;
            while ((child = (NodeInfo) iter.next()) != null) {
                if (odd) {
                    conditions.add(loader.loadExpression(child));
                } else {
                    actions.add(loader.loadExpression(child));
                }
                odd = !odd;
            }
            return new Choose(conditions.toArray(new Expression[0]),
                              actions.toArray(new Expression[0]));
        });

        eMap.put("coercer", (loader, element) -> {
            SequenceType type = loader.parseAlphaCode(element, "type");
            Expression target = loader.getFirstChildExpression(element);
            String savedRole = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean allow40 = flags != null && flags.contains("4");
            return SequenceCoercer.makeSequenceCoercer(target, type, role, allow40);
        });

        eMap.put("coercedFn", (loader, element) -> {
            ItemType type = loader.parseItemTypeAttribute(element, "type");
            Expression target = loader.getFirstChildExpression(element);
            FunctionItem targetFn;
            CoercedFunction coercedFn;
            if (target instanceof UserFunctionReference) {
                coercedFn = new CoercedFunction((SpecificFunctionType) type);
                final CoercedFunction coercedFn2 = coercedFn;
                final SymbolicName name = ((UserFunctionReference) target).getSymbolicName();
                loader.addCompletionAction(() -> coercedFn2.setTargetFunction(loader.getUserFunction((SymbolicName.F) name)));
            } else if (target instanceof Literal) {
                targetFn = (FunctionItem) ((Literal) target).getGroundedValue();
                coercedFn = new CoercedFunction(targetFn, (SpecificFunctionType) type, true, false);
            } else {
                throw new AssertionError();
            }
            return Literal.makeLiteral(coercedFn);
        });

        eMap.put("coerceToGNode", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String savedRole = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            return new GNodeSequenceConverter(body, role);
        });


        eMap.put("comment", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            Comment inst = new Comment();
            inst.setSelect(select);
            return inst;
        });

        eMap.put("compareToInt", (loader, element) -> {
            BigInteger i = new BigInteger(element.getAttributeValue(NamespaceUri.NULL, "val"));
            String opAtt = element.getAttributeValue(NamespaceUri.NULL, "op");
            Expression lhs = loader.getFirstChildExpression(element);
            return new CompareToIntegerConstant(lhs, parseValueComparisonOperator(opAtt), i.longValue());
        });

        eMap.put("compareToString", (loader, element) -> {
            String s = element.getAttributeValue(NamespaceUri.NULL, "val");
            String opAtt = element.getAttributeValue(NamespaceUri.NULL, "op");
            Expression lhs = loader.getFirstChildExpression(element);
            return new CompareToStringConstant(lhs, parseValueComparisonOperator(opAtt), StringView.tidy(s));
        });

        eMap.put("compAtt", (loader, element) -> {
            Expression name = loader.getExpressionWithRole(element, "name");
            Expression namespace = loader.getExpressionWithRole(element, "namespace");
            Expression content = loader.getExpressionWithRole(element, "select");
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            ComputedAttribute att = new ComputedAttribute(name, namespace, validation, (SimpleType) schemaType, false);
            att.setSelect(content);
            return att;
        });


        eMap.put("compElem", (loader, element) -> {
            Expression name = loader.getExpressionWithRole(element, "name");
            Expression namespace = loader.getExpressionWithRole(element, "namespace");
            Expression content = loader.getExpressionWithRole(element, "content");
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            ComputedElement inst = new ComputedElement(
                    name, namespace, loader.getSchema(), schemaType, validation, true, false);
            if (flags != null) {
                inst.setInheritanceFlags(flags);
            }
            inst.setContentExpression(content);
            return inst.simplify();
        });

        eMap.put("conditionalSort", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new ConditionalSorter(lhs, (DocumentSorter) rhs);
        });

        eMap.put("condCont", (loader, element) -> {
            Expression base = loader.getFirstChildExpression(element);
            return new WherePopulated(base);
        });

        eMap.put("condSeq", (loader, element) -> {
            Expression[] args = getChildExpressionArray(loader, element);
            return new ConditionalBlock(args);
        });

        eMap.put("consume", (loader, element) -> {
            Expression arg = loader.getFirstChildExpression(element);
            return new ConsumingOperand(arg);
        });

        eMap.put("convert", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            ItemType fromType = loader.parseAlphaCodeForItemType(element, "from");
            ItemType toType = loader.parseAlphaCodeForItemType(element, "to");
            AtomicSequenceConverter asc = new AtomicSequenceConverter(body, (PlainType) toType);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean allow40 = loader.topLevelPackage.getHostLanguageVersion() >= 40;
            if ("p".equals(flags)) {
                Converter promoter = TypeChecker.makePromotingConverter(fromType, toType.getPrimitiveType(), loader.config.getConversionRules(), allow40);
                asc.setConverter(promoter);
            } else if ("d".equals(flags)) {   // Bug 5968
                asc.setConverter(new Converter.DownCastingConverter((AtomicType) toType, loader.config.getConversionRules()));
            } else {
                Converter c = asc.allocateConverter(loader.config, false, fromType);
                asc.setConverter(c);
            }
            String diag = element.getAttributeValue(NamespaceUri.NULL, "diag");
            if (diag != null) {
                asc.setRoleDiagnostic(() -> RoleDiagnostic.reconstruct(diag));
            }
            return asc;
        });

        eMap.put("copy", (loader, element) -> {
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            String sType = element.getAttributeValue(NamespaceUri.NULL, "sit");

            Copy inst = new Copy(false, false, loader.getSchema(), schemaType, validation);
            inst.setContentExpression(loader.getFirstChildExpression(element));
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            inst.setCopyNamespaces(flags.contains("c"));
            inst.setBequeathNamespacesToChildren(flags.contains("i"));
            inst.setInheritNamespacesFromParent(flags.contains("n"));
            if (sType != null) {
                SequenceType st = AlphaCode.toSequenceType(sType, loader.getConfiguration(), loader.getSchema());
                inst.setSelectItemType(st.getPrimaryType());
            }
            return inst;
        });

        eMap.put("copyOf", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags == null) {
                flags = "";
            }
            boolean copyNamespaces = flags.contains("c");
            boolean rejectDups = flags.contains("d");
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            CopyOf inst = new CopyOf(select, copyNamespaces, validation, schemaType, rejectDups);
            inst.setCopyAccumulators(flags.contains("m"));
            inst.setCopyLineNumbers(flags.contains("l"));
            inst.setSchemaAware(flags.contains("s"));
            inst.setCopyForUpdate(flags.contains("u"));
            return inst;
        });

        eMap.put("currentGroup", (loader, element) -> new CurrentGroupCall());

        eMap.put("currentGroupingKey", (loader, element) -> new CurrentGroupingKeyCall());

        eMap.put("curriedFunc", (loader, element) -> {
            Expression target = loader.getFirstChildExpression(element);
            FunctionItem targetFn = (FunctionItem) ((Literal) target).getGroundedValue();
            NodeInfo args = loader.getChild(element, 1);
            int count = Count.count(args.iterateChildAxis(NodeKindType.ELEMENT));
            Sequence[] argValues = new Sequence[count];
            count = 0;
            for (NodeInfo child : args.children(NodeKindType.ELEMENT)) {
                if (child.getLocalPart().equals("x")) {
                    argValues[count++] = null;
                } else {
                    Expression arg = loader.loadExpression(child);
                    argValues[count++] = ((Literal) arg).getGroundedValue();
                }
            }
            FunctionItem f = new CurriedFunction(targetFn, argValues, null); // TODO: FIXME
            return Literal.makeLiteral(f);
        });


        eMap.put("cvUntyped", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            ItemType toType = loader.parseAlphaCodeForItemType(element, "to");
            if (((SimpleType) toType).isNamespaceSensitive()) {
                return UntypedSequenceConverter.makeUntypedSequenceRejector(loader.config, body, (PlainType) toType);
            } else {
                UntypedSequenceConverter cv = UntypedSequenceConverter.makeUntypedSequenceConverter(loader.config, body, (PlainType) toType);
                String diag = element.getAttributeValue(NamespaceUri.NULL, "diag");
                if (diag != null) {
                    cv.setRoleDiagnostic(() -> RoleDiagnostic.reconstruct(diag));
                }
                return cv;
            }
        });

        eMap.put("data", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String diag = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(diag);
            return new Atomizer(body, diag == null ? null : role);
        });

        eMap.put("dbl", (loader, element) -> {
            String val = element.getAttributeValue(NamespaceUri.NULL, "val");
            double d = StringToDouble.getInstance().stringToNumber(StringView.of(val).tidy());
            return Literal.makeLiteral(new DoubleValue(d));
        });

        eMap.put("dec", (loader, element) -> {
            String val = element.getAttributeValue(NamespaceUri.NULL, "val");
            AtomicValue av = BigDecimalValue.makeDecimalValue(val, false).asAtomic();
            if (av instanceof IntegerValue) {
                av = new BigDecimalValue(((IntegerValue) av).getDecimalValue());
            }
            return Literal.makeLiteral(av);
        });

        eMap.put("doc", (loader, element) -> {
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean textOnly = flags != null && flags.contains("t");
            String base = element.getAttributeValue(NamespaceUri.NULL, "base");
            String constantText = element.getAttributeValue(NamespaceUri.NULL, "text");
            Expression body = loader.getFirstChildExpression(element);
            DocumentInstr inst = new DocumentInstr(textOnly, constantText == null ? null : StringView.tidy(constantText));
            inst.setContentExpression(body);
            inst.setValidationAction(loader.getSchema(), validation, schemaType);
            return inst;
        });

        eMap.put("docOrder", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            boolean intra = element.getAttributeValue(NamespaceUri.NULL, "intra").equals("1");
            return new DocumentSorter(select, intra);
        });

        eMap.put("dot", (loader, element) -> {
            ContextItemExpression cie = new ContextItemExpression();
            SequenceType st = loader.parseAlphaCode(element, "type");
            ItemType type = st.getPrimaryType();
            boolean maybeAbsent = "a".equals(element.getAttributeValue(NamespaceUri.NULL, "flags"));
            ContextItemStaticInfo info = loader.getConfiguration()
                    .makeContextItemStaticInfo(type, maybeAbsent ? Optionality.OPTIONAL : Optionality.REQUIRED);
            if (maybeAbsent) {
                info = info.withStatusUnknown();
            }
            cie.setStaticInfo(info);
            return cie;
        });

        eMap.put("dott", (loader, element) -> {
            ContextValueExpression cie = new ContextValueExpression();
            /*SequenceType st = loader.parseAlphaCode(element, "type");
            ItemType type = st.getPrimaryType();
            boolean maybeAbsent = "a".equals(element.getAttributeValue(NamespaceUri.NULL, "flags"));
            ContextItemStaticInfo info = loader.getConfiguration()
                    .makeContextItemStaticInfo(type, maybeAbsent ? Optionality.OPTIONAL : Optionality.REQUIRED);
            if (maybeAbsent) {
                info = info.withStatusUnknown();
            }
            cie.setStaticInfo(info);*/
            return cie;
        });

        eMap.put("dynCall", (loader, element) -> {
            List<Expression> children = getChildExpressionList(loader, element);
            return new DynamicFunctionCall(children.get(0), children.subList(1, children.size()));
        });

        eMap.put("elem", (loader, element) -> {
            String displayName = element.getAttributeValue(NamespaceUri.NULL, "name");
            String[] parts;
            try {
                parts = NameChecker.getQNameParts((displayName));
            } catch (QNameException err) {
                throw new XPathException(err);
            }
            String nsuri = element.getAttributeValue(NamespaceUri.NULL, "nsuri");
            StructuredQName name = new StructuredQName(parts[0], NamespaceUri.of(nsuri), parts[1]);

            NodeName elemName = new FingerprintedQName(name, loader.config.getNamePool());
            String ns = element.getAttributeValue(NamespaceUri.NULL, "namespaces");
            NamespaceMap bindings = NamespaceMap.emptyMap();
            if (ns != null && !ns.isEmpty()) {
                String[] pairs = ns.split(" ");
                for (String pair : pairs) {
                    int eq = pair.indexOf('=');
                    if (eq >= 0) {
                        String prefix = pair.substring(0, eq);
                        if (prefix.equals("#")) {
                            prefix = "";
                        }
                        String uri = pair.substring(eq + 1);
                        if (uri.equals("~")) {
                            uri = NamespaceConstant.getUriForConventionalPrefix(prefix);
                        }
                        bindings = bindings.put(prefix, NamespaceUri.of(uri));
                    } else {
                        RetainedStaticContext rsc = loader.contextStack.peek();
                        String prefix = pair;
                        if (prefix.equals("#")) {
                            prefix = "";
                        }
                        NamespaceUri uri = rsc.getURIForPrefix(prefix, true);
                        bindings = bindings.put(prefix, uri);
                    }
                }
            }
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }

            Expression content = loader.getFirstChildExpression(element);
            FixedElement elem = new FixedElement(
                    elemName, bindings, true, true, loader.getSchema(), schemaType, validation);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags != null) {
                elem.setInheritanceFlags(flags);
            }
            elem.setContentExpression(content);
            return elem;
        });


        eMap.put("empty", (loader, element) -> Literal.makeLiteral(EmptySequence.INSTANCE));

        eMap.put("emptyTextNodeRemover", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            return new EmptyTextNodeRemover(body);
        });

        eMap.put("error", (loader, element) -> {
            String message = element.getAttributeValue(NamespaceUri.NULL, "message");
            String code = element.getAttributeValue(NamespaceUri.NULL, "code");
            if (code == null) {
                code = "XXXX9999";
            }
            boolean isTypeErr = "1".equals(element.getAttributeValue(NamespaceUri.NULL, "isTypeErr"));
            return new ErrorExpression(message, code, isTypeErr);
        });

        eMap.put("evaluate", (loader, element) -> {
            SequenceType required = loader.parseAlphaCode(element, "as");
            Expression xpath = loader.getExpressionWithRole(element, "xpath");
            Expression contextItem = loader.getExpressionWithRole(element, "cxt");
            Expression baseUri = loader.getExpressionWithRole(element, "baseUri");
            Expression namespaceContext = loader.getExpressionWithRole(element, "nsCxt");
            Expression schemaAware = loader.getExpressionWithRole(element, "sa");
            Expression dynamicParams = loader.getExpressionWithRole(element, "wp");
            Expression optionsOp = loader.getExpressionWithRole(element, "options");

            EvaluateInstr inst =
                    new EvaluateInstr(xpath, required, contextItem, baseUri, namespaceContext, schemaAware);
            if (optionsOp != null) {
                inst.setOptionsExpression(optionsOp);
            }
            String namespaces = element.getAttributeValue(NamespaceUri.NULL, "schNS");
            if (namespaces != null) {
                String[] uris = namespaces.split(" ");
                for (String nsUri : uris) {
                    //inst.importSchemaNamespace(nsUri.equals("##") ? NamespaceUri.NULL : NamespaceUri.of(nsUri));
                }
            }

            List<WithParam> nonTunnelParams = new ArrayList<>();
            int slotNumber = 0;
            for (NodeInfo wp : element.children(NodePredicateLambda.of(n -> ((NodeInfo) n).getLocalPart().equals("withParam")))) {
                WithParam withParam = new WithParam();
                StructuredQName paramName = loader.getQNameAttribute(wp, "name");
                withParam.setVariableQName(paramName);
                withParam.setSlotNumber(slotNumber++);
                SequenceType reqType = loader.parseAlphaCode(wp, "as");
                withParam.setRequiredType(reqType);
                withParam.setSelectExpression(inst, loader.getFirstChildExpression(wp));
                nonTunnelParams.add(withParam);
            }
            inst.setActualParameters(
                    nonTunnelParams.toArray(new WithParam[0]));
            if (dynamicParams != null) {
                inst.setDynamicParams(dynamicParams);
            }
            return inst;

        });

        eMap.put("every", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);

            int slot = loader.getIntegerAttribute(element, "slot");
            StructuredQName name = loader.getQNameAttribute(element, "var");
            SequenceType requiredType = loader.parseAlphaCode(element, "as");
            QuantifiedExpression qEx = new QuantifiedExpression();
            qEx.setOperator(QuantifiedExpression.Qualifier.EVERY);
            qEx.setSequence(select);
            qEx.setRequiredType(requiredType);
            qEx.setSlotNumber(slot);
            qEx.setVariableQName(name);

            loader.localBindings.push(qEx);
            Expression action = loader.getSecondChildExpression(element);
            loader.localBindings.pop();
            qEx.setAction(action);

            return qEx;
        });


        eMap.put("except", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new VennExpression(lhs, OperatorSymbol.EXCEPT, rhs);
        });

        eMap.put("false", (loader, element) -> Literal.makeLiteral(BooleanValue.FALSE));

        eMap.put("filter", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            FilterExpression fe = new FilterExpression(lhs, rhs);
            fe.setFlags(flags);
            return fe;
        });

        eMap.put("filterAM", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            FilterExpressionAM fe = new FilterExpressionAM(lhs, rhs);
            return fe;
        });

        eMap.put("first", (loader, element) -> {
            Expression base = loader.getFirstChildExpression(element);
            return FirstItemExpression.makeFirstItemExpression(base);
        });

        eMap.put("fn", (loader, element) -> {
            RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
            loader.contextStack.push(rsc);
            final Expression[] args = getChildExpressionArray(loader, element);
            String name = element.getAttributeValue(NamespaceUri.NULL, "name");
            if (name.equals("_STRING-JOIN_2.0")) {
                // encountered in files exported by Saxon 9.7
                name = "string-join";
            }
            if (rsc.getConfiguration().isDisabledFunction(NamespaceUri.FN.qName(name))) {
                throw new XPathException("System function " + name + " has been disabled", "XPST0017");
            }
            Expression e;
            try {
                e = SystemFunction.makeCall(name, rsc, args);
            } catch (IllegalArgumentException err) {
                throw new XPathException("Unknown system function " + name + "#" + args.length);
            }

            if (e instanceof SystemFunctionCall) {
                final SystemFunction fn = ((SystemFunctionCall) e).getTargetFunction();
                fn.setRetainedStaticContext(rsc);
                SequenceIterator iter = element.iterateAttributeAxis(AnyGNode.TEST);
                NodeInfo att;
                Properties props = new Properties();
                while ((att = (NodeInfo) iter.next()) != null) {
                    props.setProperty(att.getLocalPart(), att.getStringValue());
                }
                fn.importAttributes(props);
                loader.addCompletionAction(() -> fn.fixArguments(((SystemFunctionCall) e).getArguments()));
            }
            loader.contextStack.pop();
            return e;
        });

        eMap.put("fnCoercer", (loader, element) -> {
            SpecificFunctionType type = (SpecificFunctionType) loader.parseAlphaCode(element, "to").getPrimaryType();
            final String diag = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Expression arg = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean allow40 = flags != null && flags.contains("4");
            boolean acceptReducedArity = flags != null && flags.contains("r");
            return new FunctionSequenceCoercer(arg, type, () -> RoleDiagnostic.reconstruct(diag), allow40, acceptReducedArity);
        });

        eMap.put("fnRef", (loader, element) -> {
            loader.needsPELicense("higher order functions");
            String name = element.getAttributeValue(NamespaceUri.NULL, "name");
            int arity = loader.getIntegerAttribute(element, "arity");
            RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
            SystemFunction f = null;
            if (name.startsWith("Q{")) {
                StructuredQName qName = StructuredQName.fromEQName40((name));
                if (rsc.getConfiguration().isDisabledFunction(qName)) {
                    throw new XPathException("Function " + name + " has been disabled", "XPST0017");
                }
                NamespaceUri uri = qName.getNamespaceUri();
                if (uri == NamespaceUri.MATH) {
                    f = MathFunctionSet.getInstance(40).makeFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.MAP_FUNCTIONS) {
                    f = MapFunctionSet.getInstance(40).makeFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.ARRAY_FUNCTIONS) {
                    f = ArrayFunctionSet.getInstance(40).makeFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.SAXON) {
                    f = loader.getConfiguration().bindSaxonExtensionFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.EXPATH_BINARY) {
                    f = loader.makeEXPathBinaryFunction(qName, arity);
                } else if (uri == NamespaceUri.EXPATH_FILE) {
                    f = loader.makeEXPathFileFunction(qName, arity);
                }
                if (f != null) {
                    f.setRetainedStaticContext(rsc);
                }
            } else {
                f = SystemFunction.makeFunction(name, rsc, arity);
            }
            if (f == null) {
                throw new XPathException("Unknown system function " + name + "#" + arity, SaxonErrorCode.SXPK0002);
            }
            return new FunctionLiteral(f);
        });


        eMap.put("follows", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new IdentityComparison(lhs, OperatorSymbol.FOLLOWS, rhs);
        });

        eMap.put("follows-or-is", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new IdentityComparison(lhs, OperatorSymbol.FOLLOWS_OR_IS, rhs);
        });

        eMap.put("for", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);

            int slot = loader.getIntegerAttribute(element, "slot");
            StructuredQName name = loader.getQNameAttribute(element, "var");
            SequenceType requiredType = loader.parseAlphaCode(element, "as");
            ForExpression forEx = new ForExpression();
            forEx.setSequence(select);
            forEx.setRequiredType(requiredType);
            forEx.setSlotNumber(slot);
            forEx.setVariableQName(name);

            loader.localBindings.push(forEx);

            StructuredQName posName = loader.getQNameAttribute(element, "pos");
            if (posName != null) {
                LocalVariableBinding posVar = new LocalVariableBinding(posName, SequenceType.SINGLE_INTEGER);
                posVar.setSlotNumber(loader.getIntegerAttribute(element, "posSlot"));
                forEx.setPositionVariable(posVar);
                loader.localBindings.push(posVar);
            }


            Expression action = loader.getSecondChildExpression(element);
            loader.localBindings.pop();
            if (posName != null) {
                loader.localBindings.pop();
            }
            forEx.setAction(action);

            return forEx;
        });

        eMap.put("forEach", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);

            Expression threads = loader.getExpressionWithRole(element, "threads");
            if (threads == null) {
                ForEach forEach = new ForEach(lhs, rhs);
                Expression sep = loader.getExpressionWithRole(element, "separator");
                if (sep != null) {
                    forEach.setSeparatorExpression(sep);
                }
                String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
                boolean containsTailCall = flags != null && flags.contains("t");
                forEach.setContainsTailCall(containsTailCall);
                return forEach;
            } else {
                ForEach forEach = new ForEach(lhs, rhs, false, threads);
                Expression sep = loader.getExpressionWithRole(element, "separator");
                if (sep != null) {
                    forEach.setSeparatorExpression(sep);
                }
                return loader.getConfiguration().obtainOptimizer().generateMultithreadedInstruction(forEach);
            }
        });

        eMap.put("forEachGroup", (loader, element) -> {
            String algorithmAtt = element.getAttributeValue(NamespaceUri.NULL, "algorithm");
            byte algo;
            if ("by".equals(algorithmAtt)) {
                algo = ForEachGroup.GROUP_BY;
            } else if ("adjacent".equals(algorithmAtt)) {
                algo = ForEachGroup.GROUP_ADJACENT;
            } else if ("starting".equals(algorithmAtt)) {
                algo = ForEachGroup.GROUP_STARTING;
            } else if ("ending".equals(algorithmAtt)) {
                algo = ForEachGroup.GROUP_ENDING;
            } else if ("split".equals(algorithmAtt)) {
                algo = ForEachGroup.GROUP_SPLIT_WHEN;
            } else {
                throw new AssertionError("Unknown grouping algorithm: " + algorithmAtt);
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean composite = flags != null && flags.contains("c");
            boolean inFork = flags != null && flags.contains("k");
            Expression select = loader.getExpressionWithRole(element, "select");
            Expression key;
            if (algo == ForEachGroup.GROUP_BY || algo == ForEachGroup.GROUP_ADJACENT || algo == ForEachGroup.GROUP_SPLIT_WHEN) {
                key = loader.getExpressionWithRole(element, "key");
            } else {
                key = loader.getPatternWithRole(element, "match");
            }
            SortKeyDefinitionList sortKeys = loader.loadSortKeyDefinitions(element);
            if (sortKeys.size() == 0) {
                sortKeys = null;
            }
            Expression collationNameExp = loader.getExpressionWithRole(element, "collation");
            Expression content = loader.getExpressionWithRole(element, "content");
            StringCollator collator = null;
            if (collationNameExp instanceof StringLiteral) {
                String collationName = ((StringLiteral) collationNameExp).stringify();
                collator = loader.config.getCollation(collationName);
            }
            ForEachGroup feg = new ForEachGroup(
                    select, content, algo, key, collator, collationNameExp, sortKeys);
            feg.setComposite(composite);
            feg.setIsInFork(inFork);
            return feg;
        });

        eMap.put("fork", (loader, element) -> {
            Expression[] args = getChildExpressionArray(loader, element);
            return new Fork(args);
        });

        eMap.put("gc", (loader, element) -> {
            String opAtt = element.getAttributeValue(NamespaceUri.NULL, "op");
            OperatorSymbol op = getOperator(opAtt);
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String compAtt = element.getAttributeValue(NamespaceUri.NULL, "comp");
            AtomicComparer comp = loader.makeAtomicComparer(compAtt, element);
            GeneralComparison gc = new GeneralComparison20(lhs, op, rhs);
            gc.setAtomicComparer(comp);
            return gc;
        });


        eMap.put("gc10", (loader, element) -> {
            String opAtt = element.getAttributeValue(NamespaceUri.NULL, "op");
            OperatorSymbol op = getOperator(opAtt);
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String compAtt = element.getAttributeValue(NamespaceUri.NULL, "comp");
            GeneralComparison10 gc = new GeneralComparison10(lhs, op, rhs);
            AtomicComparer comp = loader.makeAtomicComparer(compAtt, element);
            gc.setAtomicComparer(comp);
            return gc;
        });

        eMap.put("gVarRef", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            GlobalVariableReference ref = new GlobalVariableReference(name);
            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            ref.setBindingSlot(bindingSlot);
            loader.addComponentFixup(ref);
            return ref;
        });

        eMap.put("homCheck", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            return new HomogeneityChecker(body);
        });

        eMap.put("ifCall", (loader, element) -> {
            Expression[] args = getChildExpressionArray(loader, element);
            StructuredQName name = loader.getQNameAttribute(element, "name");
            if (loader.getConfiguration().isDisabledFunction(name)) {
                throw new XPathException("Function " + name.getEQName() + " has been disabled", "XPST0017");
            }
            Expression exp = null;
            if (name.hasURI(NamespaceUri.MATH)) {
                exp = MathFunctionSet.getInstance(40).makeFunction(name.getLocalPart(), args.length).makeFunctionCall(args);
            } else if (name.hasURI(NamespaceUri.MAP_FUNCTIONS)) {
                exp = MapFunctionSet.getInstance(40).makeFunction(name.getLocalPart(), args.length).makeFunctionCall(args);
            } else if (name.hasURI(NamespaceUri.ARRAY_FUNCTIONS)) {
                exp = ArrayFunctionSet.getInstance(40).makeFunction(name.getLocalPart(), args.length).makeFunctionCall(args);
            } else if (name.hasURI(NamespaceUri.SAXON)) {
                if (name.getLocalPart().equals("apply")) {
                    // legacy saxon:apply function for dynamic function calls: generate fn:apply
                    exp = XPath30FunctionSet.getInstance().makeFunction("apply", 2).makeFunctionCall(args);
                    exp.setRetainedStaticContext(loader.makeRetainedStaticContext(element));
                } else {
                    loader.needsPELicense("Saxon extension functions");
                    exp = null;
                }
            }
            if (exp == null) {
                SymbolicName.F sName = new SymbolicName.F(name, args.length);
                SequenceType type = loader.parseAlphaCode(element, "type");
                IndependentContext ic = new IndependentContext(loader.config);
                RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
                int vn = loader.getTopLevelPackage().getHostLanguageVersion();
                ic.setBaseURI(rsc.getStaticBaseUriString());
                ic.setPackageData(rsc.getPackageData());
                ic.setXPathLanguageLevel(vn);
                ic.setDefaultElementNamespace(rsc.getDefaultElementNamespace());
                ic.setNamespaceResolver(rsc);
                ic.setBackwardsCompatibilityMode(rsc.isBackwardsCompatibility());
                ic.setDefaultCollationName(rsc.getDefaultCollationName());
                ic.setDecimalFormatManager(rsc.getDecimalFormatManager());
                List<String> reasons = new ArrayList<>();
                exp = loader.config.getIntegratedFunctionLibrary().bind(sName, args, null, ic, reasons);
                if (exp == null) {
                    exp = loader.config.getBuiltInExtensionLibraryList(vn).bind(sName, args, null, ic, reasons);
                }
                if (exp instanceof SystemFunctionCall) {
                    SystemFunction fn = ((SystemFunctionCall) exp).getTargetFunction();
                    fn.setRetainedStaticContext(loader.makeRetainedStaticContext(element));
                    SequenceIterator iter = element.iterateAttributeAxis(AnyGNode.TEST);
                    NodeInfo att;
                    Properties props = new Properties();
                    while ((att = (NodeInfo) iter.next()) != null) {
                        props.setProperty(att.getLocalPart(), att.getStringValue());
                    }
                    fn.importAttributes(props);
                }
                if (exp == null) {
                    StringBuilder msg = new StringBuilder("IntegratedFunctionCall to " + sName + " not found");
                    for (String reason : reasons) {
                        msg.append(". ").append(reason);
                    }
                    throw new XPathException(msg.toString());
                }
                if (exp instanceof IntegratedFunctionCall) {
                    ((IntegratedFunctionCall) exp).getFunction().supplyStaticContext(ic, -1, args);
                    ((IntegratedFunctionCall) exp).setResultType(type);
                }
            } else {
                exp.setRetainedStaticContext(loader.makeRetainedStaticContext(element));
            }
            return exp;
        });

        eMap.put("inlineFn", (loader, element) -> {
            NodeInfo first = loader.getChild(element, 0);
            UserFunction uf = loader.readFunction(first);
            return new UserFunctionReference(uf);
        });

        eMap.put("instance", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            SequenceType type = loader.parseAlphaCode(element, "of");
            return new InstanceOfExpression(body, type);
        });

        eMap.put("int", (loader, element) -> {
            BigInteger i = new BigInteger(element.getAttributeValue(NamespaceUri.NULL, "val"));
            return Literal.makeLiteral(IntegerValue.makeIntegerValue(i));
        });

        eMap.put("intersect", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new VennExpression(lhs, OperatorSymbol.INTERSECT, rhs);
        });

        eMap.put("intRangeTest", (loader, element) -> {
            Expression val = loader.getFirstChildExpression(element);
            Expression min = loader.getSecondChildExpression(element);
            Expression max = loader.getNthChildExpression(element, 2);
            return new IntegerRangeTest(val, min, max);
        });

        eMap.put("is", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String op = element.getAttributeValue(NamespaceUri.NULL, "op");
            IdentityComparison comp = new IdentityComparison(lhs, OperatorSymbol.IS, rhs);
            if ("is-g".equals(op)) {
                comp.setGenerateIdEmulation(true);
            }
            return comp;
        });

        eMap.put("is-not", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new IdentityComparison(lhs, OperatorSymbol.IS_NOT, rhs);
        });

        eMap.put("isLast", (loader, element) -> {
            boolean cond = element.getAttributeValue(NamespaceUri.NULL, "test").equals("1");
            return new IsLastExpression(cond);
        });

        eMap.put("iterate", (loader, element) -> {
            Expression select = loader.getExpressionWithRole(element, "select");
            LocalParamBlock params = (LocalParamBlock) loader.getExpressionWithRole(element, "params");
            Expression onCompletion = loader.getExpressionWithRole(element, "on-completion");
            Expression action = loader.getExpressionWithRole(element, "action");
            return new IterateInstr(select, params, action, onCompletion);
        });

        eMap.put("jnodeRoot", (loader, element) -> {
            Expression child = loader.getFirstChildExpression(element);
            MapOrArray content = (MapOrArray) ((Literal) child).getGroundedValue();
            RootJNode root = new RootJNode(content);
            return Literal.makeLiteral(root);
        });

        eMap.put("lastOf", (loader, element) -> {
            Expression base = loader.getFirstChildExpression(element);
            return new LastItemExpression(base);
        });

        eMap.put("let", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);

            int slot = loader.getIntegerAttribute(element, "slot");
            StructuredQName name = loader.getQNameAttribute(element, "var");
            SequenceType requiredType = loader.parseAlphaCode(element, "as");
            LetExpression let = new LetExpression();
            let.setSequence(select);
            let.setRequiredType(requiredType);
            let.setSlotNumber(slot);
            let.setVariableQName(name);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags != null) {
                let.setNeedsEagerEvaluation(flags.contains("e"));
                let.setNeedsLazyEvaluation(flags.contains("l"));
            }

            loader.localBindings.push(let);
            Expression action = loader.getSecondChildExpression(element);
            loader.localBindings.pop();
            let.setAction(action);

            return let;
        });

        eMap.put("literal", (loader, element) -> {
            List<Item> children = new ArrayList<>();
            SequenceIterator iter = element.iterateChildAxis(NodeKindType.ELEMENT);
            NodeInfo child;
            while ((child = (NodeInfo) iter.next()) != null) {
                Expression e = loader.loadExpression(child);
                children.add(((Literal) e).getGroundedValue().head());
            }
            return Literal.makeLiteral(SequenceExtent.makeSequenceExtent(children));
        });

        eMap.put("lookup", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            Expression key = loader.getSecondChildExpression(element);
            return new LookupExpression(select, key);
        });


        eMap.put("lookupAll", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            SequenceType type = SequenceType.ANY_SEQUENCE;
            String typeAtt = element.getAttributeValue(NamespaceUri.NULL, "type");
            if (typeAtt != null) {
                final StylesheetPackage pack = loader.getPackStack().peek();
                type = AlphaCode.toSequenceType(typeAtt, loader.getConfiguration(), pack.getImportedSchema(""));
            }
            return new LookupAllExpression(select);
        });

        eMap.put("map", (loader, element) -> {
            List<Expression> children = getChildExpressionList(loader, element);
            AtomicValue key = null;
            int version = loader.getPackStack().peek().getHostLanguageVersion();
            GeneralMapBuilder map = AbstractFixedMap.getBuilder(version);
            for (Expression child : children) {
                if (key == null) {
                    key = (AtomicValue) ((Literal) child).getGroundedValue();
                } else {
                    GroundedValue value = ((Literal) child).getGroundedValue();
                    map.put(key, value);
                    key = null;
                }
            }
            return Literal.makeLiteral(map.getCompletedMap());
        });

        eMap.put("merge", (loader, element) -> {
            final MergeInstr inst = new MergeInstr();
            SequenceIterator kids = element.iterateChildAxis(
                    NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "mergeSrc", loader.config));
            NodeInfo msElem;
            List<MergeInstr.MergeSource> list = new ArrayList<>();
            while ((msElem = (NodeInfo) kids.next()) != null) {
                final MergeInstr.MergeSource ms = new MergeInstr.MergeSource(inst);
                String mergeSourceName = msElem.getAttributeValue(NamespaceUri.NULL, "name");
                if (mergeSourceName != null) {
                    ms.sourceName = mergeSourceName;
                }
                String valAtt = msElem.getAttributeValue(NamespaceUri.NULL, "validation");
                if (valAtt != null) {
                    ms.validation = Validation.getCode(valAtt);
                }
                SchemaType schemaType = loader.getTypeAttribute(msElem, "type");
                if (schemaType != null) {
                    ms.schemaType = schemaType;
                    ms.validation = Validation.BY_TYPE;
                }
                String flagsAtt = msElem.getAttributeValue(NamespaceUri.NULL, "flags");
                ms.streamable = "s".equals(flagsAtt);
                if (ms.streamable) {
                    loader.addCompletionAction(CSharp.methodRef(ms::prepareForStreaming));
                }
                RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
                ms.baseURI = rsc.getStaticBaseUriString();

                String accumulatorNames = msElem.getAttributeValue(NamespaceUri.NULL, "accum");
                if (accumulatorNames == null) {
                    accumulatorNames = "";
                }
                final List<StructuredQName> accNameList = new ArrayList<>();
                StringTokenizer tokenizer = new StringTokenizer(accumulatorNames);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    StructuredQName name = StructuredQName.fromEQName40((token));
                    accNameList.add(name);
                }
                final StylesheetPackage pack = loader.getPackStack().peek();
                loader.addCompletionAction(() -> {
                    Set<Accumulator> accList = new HashSet<>();
                    for (StructuredQName sn : accNameList) {
                        for (Accumulator test : pack.getAccumulatorRegistry().getAllAccumulators()) {
                            if (test.getAccumulatorName().equals(sn)) {
                                accList.add(test);
                            }
                        }
                    }
                    ms.accumulators = accList;
                });
                Expression forEachItem = loader.getExpressionWithRole(msElem, "forEachItem");
                if (forEachItem != null) {
                    ms.initForEachItem(inst, forEachItem);
                }
                Expression forEachStream = loader.getExpressionWithRole(msElem, "forEachStream");
                if (forEachStream != null) {
                    ms.initForEachStream(inst, forEachStream);
                }
                Expression selectRows = loader.getExpressionWithRole(msElem, "selectRows");
                if (selectRows != null) {
                    ms.initRowSelect(inst, selectRows);
                }
                SortKeyDefinitionList keys = loader.loadSortKeyDefinitions(msElem);
                ms.setMergeKeyDefinitionSet(keys);
                list.add(ms);
            }
            Expression mergeAction = loader.getExpressionWithRole(element, "action");
            MergeInstr.MergeSource[] mergeSources = list.toArray(new MergeInstr.MergeSource[0]);
            inst.init(mergeSources, mergeAction);
            loader.addCompletionAction(CSharp.methodRef(inst::fixupGroupReferences));
            return inst;
        });

        eMap.put("mergeAdj", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            return new AdjacentTextNodeMerger(body);
        });

        eMap.put("message", (loader, element) -> {
            Expression select = loader.getExpressionWithRole(element, "select");
            Expression terminate = loader.getExpressionWithRole(element, "terminate");
            Expression error = loader.getExpressionWithRole(element, "error");
            return new MessageInstr(select, terminate, error);
        });

        eMap.put("minus", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            return new NegateExpression(body);
        });

        eMap.put("multiSubscript", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new MultiSubscriptExpression(lhs, rhs);
        });

        eMap.put("namespace", (loader, element) -> {
            Expression name = loader.getFirstChildExpression(element);
            Expression select = loader.getSecondChildExpression(element);
            NamespaceConstructor inst = new NamespaceConstructor(name);
            inst.setSelect(select);
            return inst;
        });

        eMap.put("nextIteration", (loader, element) -> {
            NextIteration inst = new NextIteration();
            SequenceIterator kids = element.iterateChildAxis(
                    NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "withParam", loader.config));
            NodeInfo wp;
            List<WithParam> params = new ArrayList<>();
            while ((wp = (NodeInfo) kids.next()) != null) {
                WithParam withParam = new WithParam();
                String flags = wp.getAttributeValue(NamespaceUri.NULL, "flags");
                StructuredQName paramName = loader.getQNameAttribute(wp, "name");
                withParam.setVariableQName(paramName);
                int slot = loader.getIntegerAttribute(wp, "slot");
                withParam.setSlotNumber(slot);
                withParam.setRequiredType(SequenceType.ANY_SEQUENCE);
                withParam.setSelectExpression(inst, loader.getFirstChildExpression(wp));
                withParam.setRequiredType(loader.parseAlphaCode(wp, "as"));
                withParam.setTypeChecked(flags != null && flags.contains("c"));
                params.add(withParam);
            }
            inst.setParameters(params.toArray(new WithParam[0]));
            return inst;
        });

        eMap.put("nextMatch", (loader, element) -> {
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean useTailRecursion = flags != null && flags.contains("t");
            NextMatch inst = new NextMatch(useTailRecursion);

            WithParam[] actuals = loader.loadWithParams(element, inst, false);
            WithParam[] tunnels = loader.loadWithParams(element, inst, true);
            inst.setActualParams(actuals);
            inst.setTunnelParams(tunnels);
            return inst;
        });


        eMap.put("node", (loader, element) -> {
            int kind = loader.getIntegerAttribute(element, "kind");
            String content = element.getAttributeValue(NamespaceUri.NULL, "content");
            String baseURI = element.getAttributeValue(NamespaceUri.NULL, "baseURI");
            NodeInfo node;
            switch (kind) {
                case Type.DOCUMENT:
                case Type.ELEMENT: {
                    StreamSource source = new StreamSource(new StringReader(content), baseURI);
                    node = loader.config.buildDocumentTree(source).getRootNode();
                    if (kind == Type.ELEMENT) {
                        node = VirtualCopy.makeVirtualCopy((NodeInfo) node.iterateChildAxis(NodeKindType.ELEMENT).next());
                    }
                    break;
                }
                case Type.TEXT:
                case Type.COMMENT: {
                    Orphan o = new Orphan(loader.getConfiguration());
                    o.setNodeKind((short) kind);
                    o.setStringValue(StringView.tidy(content));
                    node = o;
                    break;
                }
                default: {
                    Orphan o = new Orphan(loader.getConfiguration());
                    o.setNodeKind((short) kind);
                    o.setStringValue(StringView.tidy(content));
                    String prefix = element.getAttributeValue(NamespaceUri.NULL, "prefix");
                    String ns = element.getAttributeValue(NamespaceUri.NULL, "ns");
                    String local = element.getAttributeValue(NamespaceUri.NULL, "localName");
                    if (local != null) {
                        FingerprintedQName name = new FingerprintedQName(
                                prefix == null ? "" : prefix,
                                NamespaceUri.of(ns),
                                local);
                        o.setNodeName(name);
                    }
                    node = o;
                    break;
                }
            }

            return Literal.makeLiteral(node);
        });


        eMap.put("nodeNum", (loader, element) -> {
            String levelAtt = element.getAttributeValue(NamespaceUri.NULL, "level");
            int level = getLevelCode(levelAtt);

            Expression select = loader.getExpressionWithRole(element, "select");
            Pattern count = loader.getPatternWithRole(element, "count");
            Pattern from = loader.getPatternWithRole(element, "from");
            return new NumberInstruction(select, level, count, from);
        });

        eMap.put("numSeqFmt", (loader, element) -> {
            Expression value = loader.getExpressionWithRole(element, "value");
            Expression format = loader.getExpressionWithRole(element, "format");
            if (format == null) {
                format = new StringLiteral("1");
            }
            Expression groupSize = loader.getExpressionWithRole(element, "gpSize");
            Expression groupSeparator = loader.getExpressionWithRole(element, "gpSep");
            Expression letterValue = loader.getExpressionWithRole(element, "letterValue");
            Expression ordinal = loader.getExpressionWithRole(element, "ordinal");
            Expression startAt = loader.getExpressionWithRole(element, "startAt");
            Expression lang = loader.getExpressionWithRole(element, "lang");
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean backwardsCompatible = flags != null && flags.contains("1");
            NumberFormatter formatter = null; // gets initialized by the NumberSequenceFormatter when possible

            NumberSequenceFormatter ni = new NumberSequenceFormatter(value, format, groupSize, groupSeparator,
                                                                     letterValue, ordinal, startAt, lang, formatter, backwardsCompatible);
            ni.preallocateNumberer(loader.config);
            return ni;
        });

        eMap.put("onEmpty", (loader, element) -> {
            Expression base = loader.getFirstChildExpression(element);
            return new OnEmptyExpr(base);
        });

        eMap.put("onNonEmpty", (loader, element) -> {
            Expression base = loader.getFirstChildExpression(element);
            return new OnNonEmptyExpr(base);
        });

        eMap.put("or", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new OrExpression(lhs, rhs);
        });

        eMap.put("origF", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            String packKey = element.getAttributeValue(NamespaceUri.NULL, "pack");
            StylesheetPackage declPack = loader.getPackage(packKey);
            if (declPack == null) {
                throw new XPathException("Unknown package key " + packKey);
            }

            int arity = loader.getIntegerAttribute(element, "arity");
            SymbolicName sn = new SymbolicName.F(name, arity);
            Component target = declPack.getComponent(sn);
            OriginalFunction orig = new OriginalFunction(target);
            return new FunctionLiteral(orig);
        });

        eMap.put("origFC", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            String packKey = element.getAttributeValue(NamespaceUri.NULL, "pack");
            StylesheetPackage declPack = loader.getPackage(packKey);
            if (declPack == null) {
                throw new XPathException("Unknown package key " + packKey);
            }

            Expression[] args = getChildExpressionArray(loader, element);
            int arity = args.length;
            SymbolicName sn = new SymbolicName.F(name, arity);
            Component target = declPack.getComponent(sn);
            OriginalFunction orig = new OriginalFunction(target);
            return new StaticFunctionCall(orig, args);
        });


        eMap.put("param", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            int slot = loader.getIntegerAttribute(element, "slot");
            LocalParam param = new LocalParam();
            param.setLocation(element.saveLocation());
            param.setVariableQName(name);
            param.setSlotNumber(slot);
            Expression select = loader.getExpressionWithRole(element, "select");
            if (select != null) {
                param.setSelectExpression(select);
            }
            Expression convert = loader.getExpressionWithRole(element, "conversion");
            if (convert != null) {
                param.setConversion(convert);
            }
            param.setRequiredType(loader.parseAlphaCode(element, "as"));
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags != null) {
                param.setTunnel(flags.contains("t"));
                param.setRequiredParam(flags.contains("r"));
                param.setImplicitlyRequiredParam(flags.contains("i"));
            }
            loader.localBindings.push(param);
            return param;
        });

        eMap.put("params", (loader, element) -> {
            List<LocalParam> children = new ArrayList<>();
            SequenceIterator iter = element.iterateChildAxis(NodeKindType.ELEMENT);
            NodeInfo child;
            while ((child = (NodeInfo) iter.next()) != null) {
                children.add((LocalParam) loader.loadExpression(child));
            }
            return new LocalParamBlock(children.toArray(new LocalParam[0]));
        });

        eMap.put("partialApply", (loader, element) -> {
            int count = Count.count(element.iterateChildAxis(NodeKindType.ELEMENT));
            Expression base = null;
            Expression[] args = new Expression[count - 1];
            count = 0;
            for (NodeInfo child : element.children(NodeKindType.ELEMENT)) {
                if (count == 0) {
                    base = loader.loadExpression(child);
                } else if (child.getLocalPart().equals("null")) {
                    int newPos = loader.getIntegerAttribute(child, "at");
                    args[count - 1] = new PlaceHolder(newPos);
                } else {
                    args[count - 1] = loader.loadExpression(child);
                }
                count++;
            }
            return new DynamicPartialApply(base, args);
        });

        eMap.put("pipe", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new ContextValueSetter(lhs, rhs);
        });

        eMap.put("precedes", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new IdentityComparison(lhs, OperatorSymbol.PRECEDES, rhs);
        });

        eMap.put("precedes-or-is", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new IdentityComparison(lhs, OperatorSymbol.PRECEDES_OR_IS, rhs);
        });

        eMap.put("procInst", (loader, element) -> {
            Expression name = loader.getFirstChildExpression(element);
            Expression select = loader.getSecondChildExpression(element);
            ProcessingInstruction inst = new ProcessingInstruction(name);
            inst.setSelect(select);
            return inst;
        });

        eMap.put("qName", (loader, element) -> {
            String preAtt = element.getAttributeValue(NamespaceUri.NULL, "pre");
            String uriAtt = element.getAttributeValue(NamespaceUri.NULL, "uri");
            String locAtt = element.getAttributeValue(NamespaceUri.NULL, "loc");
            AtomicType type = BuiltInAtomicType.QNAME;
            if (element.getAttributeValue(NamespaceUri.NULL, "type") != null) {
                type = (AtomicType) loader.parseItemTypeAttribute(element, "type");
            }
            QualifiedNameValue val;
            if (type.getPrimitiveType() == StandardNames.XS_QNAME) {
                val = new QNameValue(preAtt, NamespaceUri.of(uriAtt), locAtt, type, false);
            } else {
                val = new NotationValue(preAtt, NamespaceUri.of(uriAtt), locAtt, type);
            }
            return Literal.makeLiteral(val);
        });

        eMap.put("range", (loader, element) -> {
            long from = loader.getLongAttribute(element, "from");
            long to = loader.getLongAttribute(element, "to");
            return Literal.makeLiteral(new IntegerRange(from, 1, to));
        });

        eMap.put("resultDoc", (loader, element) -> {
            loader.packStack.peek().setCreatesSecondaryResultDocuments(true);
            Expression href = null;
            Expression format = null;
            Expression content = null;
            String globalProps = element.getAttributeValue(NamespaceUri.NULL, "global");
            String localProps = element.getAttributeValue(NamespaceUri.NULL, "local");
            Properties globals = globalProps == null ? new Properties() : loader.importProperties(globalProps);
            Properties locals = localProps == null ? new Properties() : loader.importProperties(localProps);
            Map<StructuredQName, Expression> dynamicProperties = new HashMap<>();
            NodeInfo child;
            SequenceIterator iter = element.iterateChildAxis(NodeKindType.ELEMENT);
            while ((child = (NodeInfo) iter.next()) != null) {
                Expression exp = loader.loadExpression(child);
                String role = child.getAttributeValue(NamespaceUri.NULL, "role");
                if ("href".equals(role)) {
                    href = exp;
                } else if ("format".equals(role)) {
                    format = exp;
                } else if ("content".equals(role)) {
                    content = exp;
                } else {
                    StructuredQName name = StructuredQName.fromEQName40((role));
                    dynamicProperties.put(name, exp);
                }
            }
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL, "validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = null;
            StructuredQName typeAtt = loader.getQNameAttribute(element, "type");
            if (typeAtt != null) {
                schemaType = loader.getSchema().getSchemaType(typeAtt);
                validation = Validation.BY_TYPE;
            }
            ResultDocument instr = new ResultDocument(globals, locals, href, format, validation, loader.getSchema(), schemaType,
                                                      dynamicProperties, loader.packStack.peek().getCharacterMapIndex());
            instr.setContentExpression(content);
            if ("a".equals(element.getAttributeValue(NamespaceUri.NULL, "flags"))) {
                instr.setAsynchronous(true);
            }
            return instr;
        });

        eMap.put("root", (loader, element) -> new RootExpression());

        eMap.put("saxonDoctype", (loader, element) -> {
            Expression arg = loader.getFirstChildExpression(element);
            return new Doctype(arg);
        });

        eMap.put("sequence", (loader, element) -> {
            Expression[] args = getChildExpressionArray(loader, element);
            return new Block(args);
        });

        eMap.put("slash", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String simpleAtt = element.getAttributeValue(NamespaceUri.NULL, "simple");
            if ("1".equals(simpleAtt)) {
                return new SimpleStepExpression(lhs, rhs);
            } else {
                SlashExpression se = new SlashExpression(lhs, rhs);
                if ("2".equals(simpleAtt)) {
                    se.setContextFree(true);
                }
                return se;
            }
        });

        eMap.put("some", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);

            int slot = loader.getIntegerAttribute(element, "slot");
            StructuredQName name = loader.getQNameAttribute(element, "var");
            SequenceType requiredType = loader.parseAlphaCode(element, "as");
            QuantifiedExpression qEx = new QuantifiedExpression();
            qEx.setOperator(QuantifiedExpression.Qualifier.SOME);
            qEx.setSequence(select);
            qEx.setRequiredType(requiredType);
            qEx.setSlotNumber(slot);
            qEx.setVariableQName(name);

            loader.localBindings.push(qEx);
            Expression action = loader.getSecondChildExpression(element);
            loader.localBindings.pop();
            qEx.setAction(action);

            return qEx;
        });

        eMap.put("sort", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            SortKeyDefinitionList sortKeys = loader.loadSortKeyDefinitions(element);
            return new SortExpression(body, sortKeys);
        });

        eMap.put("sourceDoc", (loader, element) -> {
            int valSpecified = loader.getIntegerAttribute(element, "validation");
            int validation = valSpecified == Integer.MIN_VALUE ? Validation.SKIP : valSpecified;
            SchemaType schemaType = null;
            StructuredQName typeAtt = loader.getQNameAttribute(element, "schemaType");
            if (typeAtt != null) {
                schemaType = loader.getSchema().getSchemaType(typeAtt);
                validation = Validation.BY_TYPE;
            }
            ParseOptions options = loader.getConfiguration().getParseOptions()
                    .withSchemaValidationMode(validation)
                    .withTopLevelType(schemaType);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if (flags != null) {
                if (flags.contains("S")) {
                    options = options.withSpaceStrippingRule(AllElementsSpaceStrippingRule.INSTANCE);
                }
                if (flags.contains("l")) {
                    options = options.withLineNumbering(true);
                }
                options = options.withExpandAttributeDefaults(flags.contains("a"));
                if (flags.contains("d")) {
                    options = options.withDTDValidationMode(Validation.STRICT);
                }
                if (flags.contains("i")) {
                    options = options.withXIncludeAware(true);
                }
                if (validation != Validation.SKIP) {
                    options = options.withSchema(loader.getSchema());
                }
            }
            Expression body = loader.getExpressionWithRole(element, "body");
            Expression href = loader.getExpressionWithRole(element, "href");

            final SourceDocument inst = new SourceDocument(href, body, options);

            if (flags != null && flags.contains("s")) {
                loader.addCompletionAction(() -> inst.setSpaceStrippingRule(loader.getTopLevelPackage().getSpaceStrippingRule()));
            }
            String accumulatorNames = element.getAttributeValue(NamespaceUri.NULL, "accum");
            processAccumulatorList(loader, inst, accumulatorNames);
            return inst;
        });

        eMap.put("str", (loader, element) -> StringLiteral.makeLiteral(
                new StringValue(element.getAttributeValue(NamespaceUri.NULL, "val"))
        ));


        eMap.put("subscript", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new SubscriptExpression(lhs, rhs);
        });

        eMap.put("supplied", (loader, element) -> {
            int slot = loader.getIntegerAttribute(element, "slot");
            SuppliedParameterReference ref = new SuppliedParameterReference(slot);
            String sType = element.getAttributeValue(NamespaceUri.NULL, "sType");
            if (sType != null) {
                ref.setSuppliedType(AlphaCode.toSequenceType(sType, loader.getConfiguration(), loader.getSchema()));
            }
            return ref;
        });

        eMap.put("tail", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            int start = loader.getIntegerAttribute(element, "start");
            return new TailExpression(select, start);
        });

        eMap.put("tailCallLoop", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            return new TailCallLoop(loader.currentFunction, body);
        });

        eMap.put("to", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new RangeExpression(lhs, rhs);
        });

        eMap.put("treat", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            ItemType type = loader.parseAlphaCodeForItemType(element, "as");
            String savedRole = element.getAttributeValue(NamespaceUri.NULL, "diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            return new ItemChecker(body, type, role);
        });

        eMap.put("true", (loader, element) -> Literal.makeLiteral(BooleanValue.TRUE));

        eMap.put("try", (loader, element) -> {
            Expression tryExp = loader.getFirstChildExpression(element);
            TryCatch tryCatch = new TryCatch(tryExp);
            if ("r".equals(element.getAttributeValue(NamespaceUri.NULL, "flags"))) {
                tryCatch.setRollbackOutput(true);
            }
            SequenceIterator iter = element.iterateChildAxis(
                    NamedXNodeType.make(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "catch", loader.config));
            NodeInfo catchElement;
            NamePool pool = loader.getConfiguration().getNamePool();
            while ((catchElement = (NodeInfo) iter.next()) != null) {
                String errAtt = catchElement.getAttributeValue(NamespaceUri.NULL, "errors");
                final QNameTest test = parseQNameTestList(errAtt, pool);
                Expression catchExpr = loader.getFirstChildExpression(catchElement);
                tryCatch.addCatchExpression(test, catchExpr);
            }
            return tryCatch;
        });

        eMap.put("ufCall", (loader, element) -> {
            Expression[] args = getChildExpressionArray(loader, element);
            StructuredQName name = loader.getQNameAttribute(element, "name");
            UserFunctionCall call = new UserFunctionCall();
            call.setFunctionName(name);
            call.setArguments(args);
            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            call.setBindingSlot(bindingSlot);
//            String eval = element.getAttributeValue(NamespaceUri.NULL,"eval");
//            if (eval != null) {
//                String[] evals = eval.split(" ");
//                Evaluator[] evalModes = new Evaluator[evals.length];
//                for (int i = 0; i < evals.length; i++) {
//                    evalModes[i] = Evaluators.getEvaluator(Integer.parseInt(evals[i]));
//                }
//                call.setArgumentEvaluators(evalModes);
//            }
            loader.addComponentFixup(call);
            return call;
        });

        eMap.put("ufRef", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            int arity = loader.getIntegerAttribute(element, "arity");
            SymbolicName.F symbolicName = new SymbolicName.F(name, arity);
            UserFunctionReference call = new UserFunctionReference(symbolicName);
            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            call.setBindingSlot(bindingSlot);
            loader.addComponentFixup(call);
            return call;
        });

        eMap.put("union", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new VennExpression(lhs, OperatorSymbol.UNION, rhs);
        });

        eMap.put("useAS", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            boolean streamable = "s".equals(element.getAttributeValue(NamespaceUri.NULL, "flags"));
            UseAttributeSet use = new UseAttributeSet(name, streamable);
            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            use.setBindingSlot(bindingSlot);
            loader.addComponentFixup(use);
            return use;
        });

        eMap.put("valueOf", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            boolean doe = flags != null && flags.contains("d");
            boolean notIfEmpty = flags != null && flags.contains("e");
            ValueOf result = new ValueOf(select, doe, notIfEmpty);
            Expression cdata = loader.getExpressionWithRole(element, "cdata");
            if (cdata != null) {
                result.setCdataExpression(cdata);
            }
            return result;
        });

        eMap.put("varRef", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            LocalBinding binding = findLocalBinding(loader.localBindings, name);

            if (binding == null) {
                throw new XPathException("No binding found for local variable " + name);
            }
            int slot = loader.getIntegerAttribute(element, "slot");
            LocalVariableReference ref = new LocalVariableReference(binding);
            ref.setSlotNumber(slot);
            return ref;
        });

        eMap.put("vc", (loader, element) -> {
            String opAtt = element.getAttributeValue(NamespaceUri.NULL, "op");
            OperatorSymbol op = parseValueComparisonOperator(opAtt);
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            ValueComparison vc = new ValueComparison(lhs, op, rhs);
//            String compAtt = element.getAttributeValue(NamespaceUri.NULL,"comp");
//            AtomicComparer comp = loader.makeAtomicComparer(compAtt, element);
//            vc.setAtomicComparer(comp);
            String onEmptyAtt = element.getAttributeValue(NamespaceUri.NULL, "onEmpty");
            if (onEmptyAtt != null) {
                vc.setResultWhenEmpty(BooleanValue.get("1".equals(onEmptyAtt)));
            }
            return vc;
        });

    }

    private static QNameTest parseQNameTestList(String value, NamePool pool) {
        String[] tests = value.split(" ");
        List<QNameTest> list = new ArrayList<>();
        for (String t : tests) {
            if (t.equals("*")) {
                list.add(AnyQNameTest.getInstance());
            } else if (t.startsWith("*:")) {
                list.add(new LocalQNameTest(t.substring(2)));
            } else if (t.endsWith("}*")) {
                list.add(new NamespaceQNameTest(NamespaceUri.of(t.substring(2, t.length() - 2))));
            } else {
                StructuredQName qName = StructuredQName.fromEQName40((t));
                list.add(new SpecificQNameTest(qName, pool));
            }
        }
        QNameTest test;
        if (list.size() == 1) {
            test = list.get(0);
        } else {
            test = new UnionQNameTest(list);
        }
        return test;
    }

    protected SystemFunction makeEXPathBinaryFunction(StructuredQName qName, int arity) throws XPathException {
        throw new XPathException("Cannot reload SEF file: EXPath Binary functions require Saxon-PE/EE");
    }

    protected SystemFunction makeEXPathFileFunction(StructuredQName qName, int arity) throws XPathException {
        throw new XPathException("Cannot reload SEF file: EXPath File functions require Saxon-PE/EE");
    }


    private static int getLevelCode(String levelAtt) {
        if (levelAtt == null) {
            return NumberInstruction.SINGLE;
        } else {
            return switch (levelAtt) {
                case "single" -> NumberInstruction.SINGLE;
                case "multi" -> NumberInstruction.MULTI;
                case "any" -> NumberInstruction.ANY;
                case "simple" -> NumberInstruction.SIMPLE;
                default -> throw new AssertionError();
            };
        }
    }

    /**
     * Find a local binding of a variable, by name, searching downwards from the top of the stack
     * @param locals the stack to be searched
     * @param name the required variable name
     * @return the first (nearest-to-top) binding found with this name
     * @implNote Complicated by the difference between Java and C# stacks. Java stacks iterate
     * from bottom to top, C# stacks from top to bottom.
     */

    private static LocalBinding findLocalBinding(Stack<LocalBinding> locals, StructuredQName name) {
        for (LocalBinding b : new TopDownStackIterable<>(locals)) {
            if (b.getVariableQName().equals(name)) {
                return b;
            }
        }
        return null;
    }

//    //#if CSHARP==true
//    private static LocalBinding findLocalBindingTopDown(Stack<LocalBinding> locals, StructuredQName name) {
//        // This code is right for C#, wrong for Java, because the order of iteration over a stack
//        // is bottom-up on Java, top-down on C#
//        for (LocalBinding b : locals) {
//            if (b.getVariableQName().equals(name)) {
//                return b;
//            }
//        }
//        return null;
//    }
//    //#endif

    protected static List<Expression> getChildExpressionList(PackageLoaderHE loader, NodeInfo element) throws XPathException {
        List<Expression> children = new ArrayList<>();
        SequenceIterator iter = element.iterateChildAxis(NodeKindType.ELEMENT);
        NodeInfo child;
        while ((child = (NodeInfo) iter.next()) != null) {
            children.add(loader.loadExpression(child));
        }
        return children;
    }

    protected static Expression[] getChildExpressionArray(PackageLoaderHE loader, NodeInfo element) throws XPathException {
        List<Expression> children = getChildExpressionList(loader, element);
        return children.toArray(new Expression[0]);
    }

    protected static OperatorSymbol getOperator(String opAtt) {
        return switch (opAtt) {
            case "=" -> OperatorSymbol.EQUALS;
            case "!=" -> OperatorSymbol.NE;
            case "<=" -> OperatorSymbol.LE;
            case ">=" -> OperatorSymbol.GE;
            case "<" -> OperatorSymbol.LT;
            case ">" -> OperatorSymbol.GT;
            default -> throw new IllegalStateException();
        };
    }

    private static OperatorSymbol parseValueComparisonOperator(String opAtt) {
        return switch (opAtt) {
            case "eq" -> OperatorSymbol.FEQ;
            case "ne" -> OperatorSymbol.FNE;
            case "le" -> OperatorSymbol.FLE;
            case "ge" -> OperatorSymbol.FGE;
            case "lt" -> OperatorSymbol.FLT;
            case "gt" -> OperatorSymbol.FGT;
            default -> throw new IllegalStateException();
        };
    }

    static final Map<String, PatternLoader> pMap = new HashMap<>(200);

    static {

        pMap.put("p.anchor", (loader, element) -> AnchorPattern.getInstance());

        pMap.put("p.any", (loader, element) -> new UniversalPattern());

        pMap.put("p.booleanExp", (loader, element) -> {
            Expression exp = loader.getFirstChildExpression(element);
            return new BooleanExpressionPattern(exp);
        });

        pMap.put("p.genNode", (loader, element) -> {
            ItemType type = loader.parseAlphaCodeForItemType(element, "test");
            Expression exp = loader.getFirstChildExpression(element);
            return new GeneralNodePattern(exp, type);
        });

        pMap.put("p.genPos", (loader, element) -> {
            ItemType type = loader.parseAlphaCodeForItemType(element, "test");
            Expression exp = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            GeneralPositionalPattern gpp = new GeneralPositionalPattern((NodeTest)type, exp);
            gpp.setUsesPosition(!"P".equals(flags));
            return gpp;
        });

        pMap.put("p.nodeSet", (loader, element) -> {
            ItemType type = loader.parseAlphaCodeForItemType(element, "test");
            Expression select = loader.getFirstChildExpression(element);
            NodeSetPattern pat = new NodeSetPattern(select);
            pat.setItemType(type);
            return pat;
        });

        pMap.put("p.nodeTest", (loader, element) -> {
            ItemType test = loader.parseAlphaCodeForItemType(element, "test");
            String flags = element.getAttributeValue(NamespaceUri.NULL, "flags");
            if ("t".equals(flags) || !(test instanceof NodeTest)) {
                return new ItemTypePattern(test);
            } else {
                return new NodeTestPattern((NodeTest) test);
            }
        });

        pMap.put("p.venn", (loader, element) -> {
            Pattern p0 = loader.getFirstChildPattern(element);
            Pattern p1 = loader.getSecondChildPattern(element);
            String operator = element.getAttributeValue(NamespaceUri.NULL, "op");
            return switch (operator) {
                case "union" -> new UnionPattern(p0, p1);
                case "intersect" -> new IntersectPattern(p0, p1);
                case "except" -> new ExceptPattern(p0, p1);
                default -> null;
            };
        });

        pMap.put("p.simPos", (loader, element) -> {
            NodeTest test = (NodeTest) loader.parseAlphaCodeForItemType(element, "test");
            int pos = loader.getIntegerAttribute(element, "pos");
            return new SimplePositionalPattern(test, pos);
        });

        pMap.put("p.withCurrent", (loader, element) -> {
            LocalVariableBinding let = new LocalVariableBinding(Current.FN_CURRENT, SequenceType.SINGLE_ITEM);
            let.setSlotNumber(0);
            loader.localBindings.push(let);
            Pattern p0 = loader.getFirstChildPattern(element);
            loader.localBindings.pop();
            return new PatternThatSetsCurrent(p0, let);
        });

        pMap.put("p.withUpper", (loader, element) -> {
            String axisName = element.getAttributeValue(NamespaceUri.NULL, "axis");
            int axis = AxisInfo.getAxisNumber(axisName);
            Pattern basePattern = loader.getFirstChildPattern(element);
            Pattern upperPattern = loader.getSecondChildPattern(element);
            return new AncestorQualifiedPattern(basePattern, upperPattern, axis);
        });

        pMap.put("p.withPredicate", (loader, element) -> {
            Pattern basePattern = loader.getFirstChildPattern(element);
            Expression predicate = loader.getSecondChildExpression(element);
            return new BasePatternWithPredicate(basePattern, predicate);
        });

    }

    private void resolveFixups() throws XPathException {
        StylesheetPackage pack = packStack.peek();
        for (ComponentInvocation call : fixups.peek()) {
            processComponentReference(pack, call); // bug #5798
//            if (processComponentReference(pack, call)) {
//                break; // It will have a binding slot
//            }
        }
        pack.allocateBinderySlots();
    }

    protected boolean processComponentReference(StylesheetPackage pack, ComponentInvocation call) throws XPathException {
        SymbolicName sn = call.getSymbolicName();
        Component c = pack.getComponent(sn);
        if (c == null) {
            if (sn.getComponentName().hasURI(NamespaceUri.XSLT) && sn.getComponentName().getLocalPart().equals("original")) {
                return true;
            } else {
                throw new XPathException("Loading compiled package: unresolved component reference to " + sn);
            }
        }
        if (call instanceof GlobalVariableReference) {
            ((GlobalVariableReference) call).setTarget(c);
        } else if (call instanceof UserFunctionCall) {
            ((UserFunctionCall) call).setFunction((UserFunction) c.getActor());
            ((UserFunctionCall) call).setStaticType(((UserFunction) c.getActor()).getResultType());
        } else if (call instanceof UserFunctionReference) {
            ((UserFunctionReference) call).setFunction((UserFunction) c.getActor());
        } else if (call instanceof CallTemplate) {
            ((CallTemplate) call).setTargetTemplate((NamedTemplate) c.getActor());
        } else if (call instanceof UseAttributeSet) {
            ((UseAttributeSet) call).setTarget((AttributeSet) c.getActor());
        } else if (call instanceof ApplyTemplates) {
            ((ApplyTemplates) call).setMode((SimpleMode) c.getActor());
        } else {
            throw new XPathException("Unknown component reference " + call.getClass());
        }
        return false;
    }

    private Location allocateLocation(String module, int lineNumber) {
        IntHashMap<Location> lineMap = locationMap.get(module);
        if (lineMap == null) {
            lineMap = new IntHashMap<>();
            locationMap.put(module, lineMap);
        }
        Location loc = lineMap.get(lineNumber);
        if (loc == null) {
            loc = new Loc(module, lineNumber, -1);
            lineMap.put(lineNumber, loc);
        }
        return loc;
    }

}

// Copyright (c) 2018-2026 Saxonica Limited

