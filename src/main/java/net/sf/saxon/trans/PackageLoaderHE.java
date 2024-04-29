////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

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
import net.sf.saxon.ma.arrays.ArrayFunctionSet;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.arrays.SquareArrayConstructor;
import net.sf.saxon.ma.json.JsonParser;
import net.sf.saxon.ma.map.HashTrieMap;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.str.StringView;
import net.sf.saxon.style.PackageVersion;
import net.sf.saxon.style.StylesheetFunctionLibrary;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.sxpath.IndependentContext;
import net.sf.saxon.trans.packages.IPackageLoader;
import net.sf.saxon.trans.rules.BuiltInRuleSet;
import net.sf.saxon.trans.rules.Rule;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.transpile.CSharp;
import net.sf.saxon.transpile.CSharpDelegate;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.tree.jiter.TopDownStackIterable;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.tree.util.Orphan;
import net.sf.saxon.tree.wrapper.VirtualCopy;
import net.sf.saxon.type.*;
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

    private final static NestedIntegerValue SAXON9911 = new NestedIntegerValue(new int[]{9,9,1,1});

    private final Configuration config;
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

    public PackageLoaderHE(Configuration config) {
        this.config = config;
        overriding = new ExecutableFunctionLibrary(config);
        underriding = new ExecutableFunctionLibrary(config);
        try {
            parser = config.newExpressionParser("XP", false, new IndependentContext(config));
            QNameParser qNameParser = new QNameParser(null).withAcceptEQName(true);
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
                StructuredQName name = StructuredQName.fromEQName((token));
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
    public StylesheetPackage loadPackage(Source source) throws XPathException {

        ParseOptions options = new ParseOptions()
                .withSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance())
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
        if (packStack.isEmpty()) {
            topLevelPackage = pack;
        }
        packStack.push(pack);
        NodeInfo packageElement = doc.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
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
        String saxonVersionAtt = packageElement.getAttributeValue(NamespaceUri.NULL,"saxonVersion");
        if (saxonVersionAtt == null) {
            saxonVersionAtt = "9.8.0.1"; //Arbitrarily; older SEF files do not have this attribute
        }
        originalVersion = NestedIntegerValue.parse(saxonVersionAtt);
        String dmk = packageElement.getAttributeValue(NamespaceUri.NULL,"dmk");
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
        String packageName = packageElement.getAttributeValue(NamespaceUri.NULL,"name");
        String packageId = packageElement.getAttributeValue(NamespaceUri.NULL,"id");
        String packageKey = packageId == null ? packageName : packageId; // for backwards compatibility with 9.8
        boolean relocatable = "true".equals(packageElement.getAttributeValue(NamespaceUri.NULL,"relocatable"));
        if (packageName != null) {
            pack.setPackageName(packageName);
            allPackages.put(packageKey, pack);
        }
        pack.setPackageVersion(
                new PackageVersion(packageElement.getAttributeValue(NamespaceUri.NULL,"packageVersion")));
        int xsltVersion = getIntegerAttribute(packageElement, "version");
        pack.setLanguageVersion(xsltVersion);
        pack.setSchemaAware("1".equals(packageElement.getAttributeValue(NamespaceUri.NULL,"schemaAware")));
        if (pack.isSchemaAware()) {
            needsEELicense("schema-awareness");
        }
        String implicitAtt = packageElement.getAttributeValue(NamespaceUri.NULL,"implicit");
        if (implicitAtt != null) {
            pack.setImplicitPackage(implicitAtt.equals("true"));
        } else {
            // For export files created prior to Saxon 9.9.1.2, we'll treat the package as implicit,
            // for compatibility: otherwise, setInitialTemplate("main") will fail when the main template
            // has no "visibility" attribute
            pack.setImplicitPackage(originalVersion.compareTo(SAXON9911) <= 0);
        }
        pack.setStripsTypeAnnotations("1".equals(packageElement.getAttributeValue(NamespaceUri.NULL,"stripType")));
        pack.setKeyManager(new KeyManager(pack.getConfiguration(), pack));
        pack.setDeclaredModes("1".equals(packageElement.getAttributeValue(NamespaceUri.NULL,"declaredModes")));
        for (NodeInfo usePack : packageElement.children(NodeSelector.of(n -> n.getLocalPart().equals("package")))) {
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
        functionLibrary.addFunctionLibrary(MathFunctionSet.getInstance());
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

        RetainedStaticContext rsc = new RetainedStaticContext(config);
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
        readKeys(packageElement);
        readComponents(packageElement, false);
        NodeInfo overridden = packageElement.iterateAxis(AxisInfo.CHILD,
                                                         new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "overridden", config.getNamePool())).next();
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
        //NameTest condition = new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "glob", config.getNamePool());
        for (NodeInfo varElement : packageElement.children(NodeSelector.of(n -> n.getLocalPart().equals("glob")))) {
            if (req == null) {
                req = new GlobalContextRequirement();
                packStack.peek().setContextItemRequirements(req);
            }
            String use = varElement.getAttributeValue(NamespaceUri.NULL,"use");
            if ("opt".equals(use)) {
                req.setMayBeOmitted(true);
                req.setAbsentFocus(false);
            } else if ("pro".equals(use)) {
                req.setMayBeOmitted(true);
                req.setAbsentFocus(true);
            } else if ("req".equals(use)) {
                req.setMayBeOmitted(false);
                req.setAbsentFocus(false);
            }
            ItemType requiredType = parseItemTypeAttribute(varElement, "type");
            if (requiredType != null) {
                req.addRequiredItemType(requiredType);
            }
        }
    }

    protected void readSchemaNamespaces(NodeInfo packageElement) throws XPathException {
        // No action in Saxon-HE
    }

    private void readKeys(NodeInfo packageElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo keyElement;
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD,
                                                           new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "key", config.getNamePool()));
        while ((keyElement = iterator.next()) != null) {
            StructuredQName keyName = getQNameAttribute(keyElement, "name");
            SymbolicName symbol = new SymbolicName(StandardNames.XSL_KEY, keyName);

            String flags = keyElement.getAttributeValue(NamespaceUri.NULL,"flags");
            boolean backwards = flags != null && flags.contains("b");
            boolean range = flags != null && flags.contains("r");
            boolean reusable = flags != null && flags.contains("u");
            boolean composite = flags != null && flags.contains("c");
            boolean convertUntypedToOther = flags != null && flags.contains("v");
            boolean strictComparison = flags != null && flags.contains("s");
            Pattern match = getFirstChildPattern(keyElement);
            Expression use = getSecondChildExpression(keyElement);
            String collationName = keyElement.getAttributeValue(NamespaceUri.NULL,"collation");
            if (collationName == null) {
                collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
            StringCollator collation = config.getCollation(collationName);
            KeyDefinition keyDefinition = new KeyDefinition(symbol, match, use, collationName, collation);
            int slots = getIntegerAttribute(keyElement, "slots");
            if (slots != Integer.MIN_VALUE) {
                keyDefinition.setStackFrameMap(new SlotManager(slots));
            }
            String binds = keyElement.getAttributeValue(NamespaceUri.NULL,"binds");
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
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD,
                                                           new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "co", config.getNamePool()));
        while ((child = iterator.next()) != null) {
            int id = getIntegerAttribute(child, "id");
            String visAtt = child.getAttributeValue(NamespaceUri.NULL,"vis");
            Visibility vis = visAtt == null ? Visibility.PRIVATE : Visibility.valueOf(visAtt.toUpperCase());
            VisibilityProvenance provenance = visAtt == null ? VisibilityProvenance.DEFAULTED : VisibilityProvenance.EXPLICIT;
            String binds = child.getAttributeValue(NamespaceUri.NULL,"binds");
            String dPackKey = child.getAttributeValue(NamespaceUri.NULL,"dpack");
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
                    throw new AssertionError(base+"");
                }
                component = Component.makeComponent(baseComponent.getActor(), vis, provenance, pack, declaringPackage);
                component.setBaseComponent(baseComponent);
                if (component instanceof Component.M) {
                    // Create the mode even if there are no mode children: test case override-v-015
                    pack.getRuleManager().obtainMode(baseComponent.getActor().getComponentName(), true);
                }
            } else {
                NodeInfo grandchild = child.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
                Actor cc;
                String kind = grandchild.getLocalPart();
                switch (kind) {
                    case "template":
                        cc = readNamedTemplate(grandchild);
                        break;
                    case "globalVariable":
                        cc = readGlobalVariable(grandchild);
                        break;
                    case "globalParam":
                        cc = readGlobalParam(grandchild);
                        break;
                    case "function":
                        cc = readGlobalFunction(grandchild);
                        break;
                    case "mode":
                        cc = readMode(grandchild);
                        break;
                    case "attributeSet":
                        cc = readAttributeSet(grandchild);
                        break;
                    default:
                        throw new XPathException("unknown component kind " + kind);
                }
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
        String flags = varElement.getAttributeValue(NamespaceUri.NULL,"flags");
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
        NodeInfo bodyElement = varElement.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
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
        String flags = varElement.getAttributeValue(NamespaceUri.NULL,"flags");
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
        NodeInfo bodyElement = varElement.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
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
        String flags = templateElement.getAttributeValue(NamespaceUri.NULL,"flags");
        int slots = getIntegerAttribute(templateElement, "slots");
        SequenceType contextType = parseAlphaCode(templateElement, "cxt");
        ItemType contextItemType = contextType == null ? AnyItemType.getInstance() : contextType.getPrimaryType();

        NamedTemplate template = new NamedTemplate(templateName, getConfiguration());
        template.setStackFrameMap(new SlotManager(slots));
        template.setPackageData(pack);
        template.setRequiredType(parseAlphaCode(templateElement, "as"));
        template.setContextItemRequirements(contextItemType, flags.contains("o"), !flags.contains("s"));
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
        String flags = functionElement.getAttributeValue(NamespaceUri.NULL,"flags");
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
        AxisIterator argIterator = functionElement.iterateAxis(AxisInfo.CHILD,
                                                               new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "arg", config.getNamePool()));
        NodeInfo argElement;
        int slot = 0;
        while ((argElement = argIterator.next()) != null) {
            UserFunctionParameter arg = new UserFunctionParameter();
            arg.setVariableQName(getQNameAttribute(argElement, "name"));
            arg.setRequiredType(parseAlphaCode(argElement, "as"));
            arg.setSlotNumber(slot++);
            params.add(arg);
            localBindings.push(arg);
        }
        function.setParameterDefinitions(params.toArray(new UserFunctionParameter[0]));
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
        aSet.setDeclaredStreamable("s".equals(aSetElement.getAttributeValue(NamespaceUri.NULL,"flags")));

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

        String onNoMatch = modeElement.getAttributeValue(NamespaceUri.NULL,"onNo");
        BuiltInRuleSet base;
        if (onNoMatch != null) {
            base = mode.getBuiltInRuleSetForCode(onNoMatch);
            mode.setBuiltInRuleSet(base);
        }

        String flags = modeElement.getAttributeValue(NamespaceUri.NULL,"flags");
        if (flags != null) {
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

        AxisIterator iterator2 = modeElement.iterateAxis(AxisInfo.DESCENDANT,
                                                         new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "templateRule", config.getNamePool()));
        NodeInfo templateRuleElement0;
        LinkedList<NodeInfo> ruleStack = new LinkedList<>();
        while ((templateRuleElement0 = iterator2.next()) != null) {
            // process rules in reverse order
            ruleStack.addFirst(templateRuleElement0);
        }
        for (NodeInfo templateRuleElement : ruleStack) {
            int precedence = getIntegerAttribute(templateRuleElement, "prec");
            int rank = getIntegerAttribute(templateRuleElement, "rank");
            String priorityAtt = templateRuleElement.getAttributeValue(NamespaceUri.NULL,"prio");
            double priority = Double.parseDouble(priorityAtt);
            int sequence = getIntegerAttribute(templateRuleElement, "seq");
            int part = getIntegerAttribute(templateRuleElement, "part");
            if (part == Integer.MIN_VALUE) {
                part = 0;
            }
            int minImportPrecedence = getIntegerAttribute(templateRuleElement, "minImp");
            int slots = getIntegerAttribute(templateRuleElement, "slots");
            boolean streamable = "1".equals(templateRuleElement.getAttributeValue(NamespaceUri.NULL,"streamable"));
            String tflags = templateRuleElement.getAttributeValue(NamespaceUri.NULL,"flags");
            SequenceType contextType = parseAlphaCode(templateRuleElement, "cxt");
            ItemType contextItemType = contextType == null ? AnyItemType.getInstance() : contextType.getPrimaryType();

            NodeInfo matchElement = getChildWithRole(templateRuleElement, "match");
            Pattern match = loadPattern(matchElement);

            localBindings = new Stack<>();
            TemplateRule template = config.makeTemplateRule();
            template.setMatchPattern(match);
            template.setStackFrameMap(new SlotManager(slots));
            template.setPackageData(pack);
            template.setRequiredType(parseAlphaCode(templateRuleElement, "as"));
            template.setDeclaredStreamable(streamable);
            template.setContextItemRequirements(contextItemType, !tflags.contains("s"));
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
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD,
                                                           new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "accumulator", config.getNamePool()));
        while ((accElement = iterator.next()) != null) {
            StructuredQName accName = getQNameAttribute(accElement, "name");
            Accumulator acc = new Accumulator();
            Component component = Component.makeComponent(acc, Visibility.PRIVATE, VisibilityProvenance.DEFAULTED, pack, pack);
            acc.setDeclaringComponent(component);
            int iniSlots = getIntegerAttribute(accElement, "slots");
            acc.setSlotManagerForInitialValueExpression(new SlotManager(iniSlots));
            acc.setAccumulatorName(accName);
            String binds = accElement.getAttributeValue(NamespaceUri.NULL,"binds");
            externalReferences.put(component, binds);
            boolean streamable = "1".equals(accElement.getAttributeValue(NamespaceUri.NULL,"streamable"));
            String flags = accElement.getAttributeValue(NamespaceUri.NULL,"flags");
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
        AxisIterator iterator = owner.iterateAxis(AxisInfo.CHILD,
                                                  new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "accRule", config.getNamePool()));
        NodeInfo accRuleElement;
        boolean preDescent = owner.getLocalPart().equals("pre");
        SimpleMode mode = preDescent ? acc.getPreDescentRules() : acc.getPostDescentRules();
        int patternSlots = getIntegerAttribute(owner, "slots");
        mode.setStackFrameSlotsNeeded(patternSlots);
        while ((accRuleElement = iterator.next()) != null) {
            int slots = getIntegerAttribute(accRuleElement, "slots");
            int rank = getIntegerAttribute(accRuleElement, "rank");
            String flags = accRuleElement.getAttributeValue(NamespaceUri.NULL,"flags");
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
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD,
                                                           new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "output", config.getNamePool()));
        while ((outputElement = iterator.next()) != null) {
            StructuredQName outputName = getQNameAttribute(outputElement, "name");
            Properties props = new Properties();
            NodeInfo propertyElement;
            AxisIterator iterator1 = outputElement.iterateAxis(AxisInfo.CHILD,
                                                               new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "property", config.getNamePool()));
            while ((propertyElement = iterator1.next()) != null) {
                String name = propertyElement.getAttributeValue(NamespaceUri.NULL,"name");
                if (name.startsWith("Q{")) {
                    name = name.substring(1);
                }
                String value = propertyElement.getAttributeValue(NamespaceUri.NULL,"value");
                if (name.startsWith("{http://saxon.sf.net/}") && !name.equals(SaxonOutputKeys.STYLESHEET_VERSION)) {
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
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD,
                                                           new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "charMap", config.getNamePool()));
        while ((charMapElement = iterator.next()) != null) {
            StructuredQName mapName = getQNameAttribute(charMapElement, "name");
            NodeInfo mappingElement;
            AxisIterator iterator1 = charMapElement.iterateAxis(AxisInfo.CHILD,
                                                                new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "m", config.getNamePool()));
            IntHashMap<String> map = new IntHashMap<>();
            while ((mappingElement = iterator1.next()) != null) {
                int c = getIntegerAttribute(mappingElement, "c");
                String s = mappingElement.getAttributeValue(NamespaceUri.NULL,"s");
                map.put(c, s);
            }
            CharacterMap characterMap = new CharacterMap(mapName, map);
            pack.getCharacterMapIndex().putCharacterMap(mapName, characterMap);
        }
    }

    private void readSpaceStrippingRules(NodeInfo packageElement) throws XPathException {
        StylesheetPackage pack = packStack.peek();
        NodeInfo element;
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        while ((element = iterator.next()) != null) {
            String s = element.getLocalPart();
            switch (s) {
                case "strip.all":
                    pack.setStripperRules(new AllElementsSpaceStrippingRule());
                    pack.setStripsWhitespace(true);
                    break;
                case "strip.none":
                    pack.setStripperRules(new NoElementsSpaceStrippingRule());
                    break;
                case "strip":
                    AxisIterator iterator2 = element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
                    NodeInfo element2;
                    SelectedElementsSpaceStrippingRule rules = new SelectedElementsSpaceStrippingRule(false);
                    while ((element2 = iterator2.next()) != null) {
                        Stripper.StripRuleTarget which = element2.getLocalPart().equals("s") ? Stripper.STRIP : Stripper.PRESERVE;
                        String value = element2.getAttributeValue(NamespaceUri.NULL,"test");
                        NodeTest t;
                        if (value.equals("*")) {
                            t = NodeKindTest.ELEMENT;
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
        AxisIterator iterator = packageElement.iterateAxis(AxisInfo.CHILD,
                                                           new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "decimalFormat", config.getNamePool()));

        String[] propertyNames = DecimalSymbols.propertyNames;
        while ((formatElement = iterator.next()) != null) {
            StructuredQName name = getQNameAttribute(formatElement, "name");
            DecimalSymbols symbols;
            if (name == null) {
                symbols = decimalFormatManager.getDefaultDecimalFormat();
            } else {
                symbols = decimalFormatManager.obtainNamedDecimalFormat(name);
            }
            symbols.setHostLanguage(HostLanguage.XSLT, 31);
            for (String p : propertyNames) {
                if (formatElement.getAttributeValue(NamespaceUri.NULL,p) != null) {
                    switch (p) {
                        case "NaN":
                            symbols.setNaN(formatElement.getAttributeValue(NamespaceUri.NULL,"NaN"));
                            break;
                        case "infinity":
                            symbols.setInfinity(formatElement.getAttributeValue(NamespaceUri.NULL,"infinity"));
                            break;
                        case "name":
                            // no action
                            break;
                        default:
                            symbols.setIntProperty(p, getIntegerAttribute(formatElement, p));
                            break;
                    }
                }
            }
        }
    }


    /**
     * Get the n'th element child of an element (zero-based)
     *
     * @param parent the parent element
     * @param n      which child to get (zero-based)
     * @return the n'th child, or null if not available
     */
    public NodeInfo getChild(NodeInfo parent, int n) {
        AxisIterator iter = parent.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo node = iter.next();
        for (int i = 0; i < n; i++) {
            node = iter.next();
        }
        return node;
    }

    public NodeInfo getChildWithRole(NodeInfo parent, String role) {
        AxisIterator iter = parent.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo node;
        while ((node = iter.next()) != null) {
            String roleAtt = node.getAttributeValue(NamespaceUri.NULL,"role");
            if (role.equals(roleAtt)) {
                return node;
            }
        }
        return null;
    }

    public Expression getFirstChildExpression(NodeInfo parent) throws XPathException {
        NodeInfo node = parent.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
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
        String baseURIAtt = element.getAttributeValue(NamespaceUri.NULL,"baseUri");
        String defaultCollAtt = element.getAttributeValue(NamespaceUri.NULL,"defaultCollation");
        String defaultElementNS = element.getAttributeValue(NamespaceUri.NULL,"defaultElementNS");
        String nsAtt = element.getAttributeValue(NamespaceUri.NULL,"ns");
        String versionAtt = element.getAttributeValue(NamespaceUri.NULL,"vn");
        if (baseURIAtt != null || defaultCollAtt != null || nsAtt != null ||
                versionAtt != null || defaultElementNS != null ||
                contextStack.peek().getDecimalFormatManager() == null // implies not fully initialized
        ) {
            RetainedStaticContext rsc = new RetainedStaticContext(config);
            rsc.setPackageData(pack);
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
            rsc.setDecimalFormatManager(packStack.peek().getDecimalFormatManager());
            return rsc;
        } else {
            return contextStack.peek();
        }
    }

    public static NamespaceMap fromExportedNamespaces(String nsAtt) {
        NamespaceMap map = NamespaceMap.emptyMap();
        if (nsAtt != null) {
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
        NodeInfo node = parent.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next();
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
        String val = element.getAttributeValue(NamespaceUri.NULL,attName);
        if (val == null) {
            return null;
        }
        if (val.startsWith("xs:")) {
            return config.getSchemaType(new StructuredQName("xs", NamespaceUri.SCHEMA, val.substring(3)));
        } else {
            StructuredQName name = getQNameAttribute(element, attName);
            return config.getSchemaType(name);
        }
    }

    public StructuredQName getQNameAttribute(NodeInfo element, String localName) {
        String val = element.getAttributeValue(NamespaceUri.NULL,localName);
        if (val == null) {
            return null;
        }
        return StructuredQName.fromEQName((val));
    }

    public List<StructuredQName> getListOfQNameAttribute(NodeInfo element, String localName) throws XPathException {
        String val = element.getAttributeValue(NamespaceUri.NULL,localName);
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
            return StructuredQName.fromEQName((val));
        } else if (val.contains(":")) {
            return StructuredQName.fromLexicalQName((val), true, true, element.getAllNamespaces());
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
        String val = element.getAttributeValue(NamespaceUri.NULL,localName);
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

    public String getInheritedAttribute(NodeInfo element, String localName) {
        while (element != null) {
            String val = element.getAttributeValue(NamespaceUri.NULL,localName);
            if (val != null) {
                return val;
            }
            element = element.getParent();
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
        String attValue = element.getAttributeValue(NamespaceUri.NULL,name);
        if (attValue == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            return parser.parseExtendedSequenceType(attValue, env);
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
        String attValue = element.getAttributeValue(NamespaceUri.NULL,name);
        if (attValue == null) {
            return SequenceType.ANY_SEQUENCE;
        } else {
            try {
                return AlphaCode.toSequenceType(attValue, config);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new XPathException("Invalid alpha code " + element.getDisplayName() + "/@" + name + "='" + attValue + "': " + e.getMessage());
            }
        }
    }

    public ItemType parseAlphaCodeForItemType(NodeInfo element, String name) throws XPathException {
        String attValue = element.getAttributeValue(NamespaceUri.NULL,name);
        if (attValue == null) {
            return AnyItemType.getInstance();
        } else {
            try {
                return AlphaCode.toItemType(attValue, config);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new XPathException("Invalid alpha code " + element.getDisplayName() + "/@" + name + "='" + attValue + "': " + e.getMessage());
            }
        }
    }

    private IndependentContext makeStaticContext(NodeInfo element) {
        StylesheetPackage pack = packStack.peek();
        IndependentContext env = new IndependentContext(config);
        final NamespaceResolver resolver = element.getAllNamespaces();
        env.setNamespaceResolver(resolver);
        env.setImportedSchemaNamespaces(pack.getSchemaNamespaces());
        env.getImportedSchemaNamespaces().add(NamespaceUri.ANONYMOUS);
        parser.setQNameParser(parser.getQNameParser().withNamespaceResolver(resolver));
        return env;
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
        String attValue = element.getAttributeValue(NamespaceUri.NULL,attName);
        if (attValue == null) {
            return AnyItemType.getInstance();
        }
        return parseItemType(element, attValue);
    }

    private ItemType parseItemType(NodeInfo element, String attValue) throws XPathException {
        IndependentContext env = makeStaticContext(element);
        return parser.parseExtendedItemType(attValue, env);
    }

    public AtomicComparer makeAtomicComparer(String name, NodeInfo element) throws XPathException {
        if (name.equals("CCC")) {
            return CodepointCollatingComparer.getInstance();
        } else if (name.equals("CAVC")) {
            return ContextFreeAtomicComparer.getInstance();
        } else if (name.startsWith("GAC|")) {
            StringCollator collator = config.getCollation(name.substring(4));
            return new GenericAtomicComparer(collator, null);
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
            return AtomicSortComparer.makeSortComparer(config.getCollation(collName), fp, new EarlyEvaluationContext(config));
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
        AxisIterator iterator = element.iterateAxis(AxisInfo.CHILD,
                                                    new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "sortKey", config.getNamePool()));
        while ((sortKeyElement = iterator.next()) != null) {
            SortKeyDefinition skd = new SortKeyDefinition();
            String compAtt = sortKeyElement.getAttributeValue(NamespaceUri.NULL,"comp");
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
        AxisIterator iterator = element.iterateAxis(AxisInfo.CHILD,
                                                    new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "withParam", config.getNamePool()));
        while ((wpElement = iterator.next()) != null) {
            String flags = wpElement.getAttributeValue(NamespaceUri.NULL,"flags");
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
                String val = eq == line.length() - 1 ? "" : line.substring(eq+1);
                if (key.equals("item-separator") || key.equals("Q" + SaxonOutputKeys.NEWLINE)) {
                    try {
                        val = JsonParser.unescape(val, 0, "", -1);
                    } catch (XPathException ignored) {
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

        licensableConstructs.put("acFnRef", "PE");
        licensableConstructs.put("assign", "PE");
        licensableConstructs.put("do", "PE");
        licensableConstructs.put("javaCall", "PE");
        licensableConstructs.put("while", "PE");
    }

    static {

        eMap.put("among", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new SingletonIntersectExpression(lhs, Token.INTERSECT, rhs);
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
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
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
            final String code = element.getAttributeValue(NamespaceUri.NULL,"calc");
            Calculator calc = Calculator.reconstructCalculator(code);
            int operator = Calculator.operatorFromCode(code.charAt(1));
            int token = Calculator.getTokenFromOperator(operator);
            ArithmeticExpression exp = new ArithmeticExpression(lhs, token, rhs);
            exp.setCalculator(calc);
            return exp;
        });

        eMap.put("arith10", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            final String code = element.getAttributeValue(NamespaceUri.NULL,"calc");
            Calculator calc = Calculator.reconstructCalculator(code);
            int operator = Calculator.operatorFromCode(code.charAt(1));
            int token = Calculator.getTokenFromOperator(operator);
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
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"val");
            AtomicType type = (AtomicType)loader.parseAlphaCodeForItemType(element, "type");
            AtomicValue val = type.getStringConverter(loader.config.getConversionRules())
                    .convertString(StringView.of(valAtt).tidy()).asAtomic();
            return Literal.makeLiteral(val);
        });

        eMap.put("atomSing", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String savedRole = element.getAttributeValue(NamespaceUri.NULL,"diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            String cardAtt = element.getAttributeValue(NamespaceUri.NULL,"card");
            boolean allowEmpty = "?".equals(cardAtt);
            return new SingletonAtomizer(body, role, allowEmpty);
        });

        eMap.put("att", (loader, element) -> {
            String displayName = element.getAttributeValue(NamespaceUri.NULL,"name");
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
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
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
            String axisName = element.getAttributeValue(NamespaceUri.NULL,"name");
            int axis = AxisInfo.getAxisNumber(axisName);
            NodeTest nt = (NodeTest) loader.parseAlphaCodeForItemType(element, "nodeTest");
            return new AxisExpression(axis, nt);
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
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
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

        eMap.put("cast", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            boolean allowEmpty = flags.contains("e");
            if (flags.contains("a")) {
                SequenceType seqType = loader.parseAlphaCode(element, "as");
                return new CastExpression(body, (AtomicType) seqType.getPrimaryType(), allowEmpty);
            } else if (flags.contains("l")) {
                StructuredQName typeName = StructuredQName.fromEQName((element.getAttributeValue(NamespaceUri.NULL,"as")));
                SchemaType type = loader.config.getSchemaType(typeName);
                NamespaceResolver resolver = element.getAllNamespaces();
                ListConstructorFunction ucf = new ListConstructorFunction((ListType) type, resolver, allowEmpty);
                return new StaticFunctionCall(ucf, new Expression[]{body});
            } else if (flags.contains("u")) {
                if (element.getAttributeValue(NamespaceUri.NULL,"as") != null) {
                    StructuredQName typeName = StructuredQName.fromEQName((element.getAttributeValue(NamespaceUri.NULL,"as")));
                    SchemaType type = loader.config.getSchemaType(typeName);
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionConstructorFunction ucf = new UnionConstructorFunction((UnionType) type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                } else {
                    LocalUnionType type = (LocalUnionType) loader.parseAlphaCode(element, "to").getPrimaryType();
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
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            boolean allowEmpty = flags.contains("e");
            if (flags.contains("a")) {
                SequenceType seqType = loader.parseAlphaCode(element, "as");
                return new CastableExpression(body, (AtomicType) seqType.getPrimaryType(), allowEmpty);
            } else if (flags.contains("l")) {
                StructuredQName typeName = StructuredQName.fromEQName((element.getAttributeValue(NamespaceUri.NULL,"as")));
                SchemaType type = loader.config.getSchemaType(typeName);
                NamespaceResolver resolver = element.getAllNamespaces();
                ListCastableFunction ucf = new ListCastableFunction((ListType) type, resolver, allowEmpty);
                return new StaticFunctionCall(ucf, new Expression[]{body});
            } else if (flags.contains("u")) {
                if (element.getAttributeValue(NamespaceUri.NULL,"as") != null) {
                    StructuredQName typeName = StructuredQName.fromEQName((element.getAttributeValue(NamespaceUri.NULL,"as")));
                    SchemaType type = loader.config.getSchemaType(typeName);
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionCastableFunction ucf = new UnionCastableFunction((UnionType) type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                } else {
                    LocalUnionType type = (LocalUnionType)loader.parseAlphaCode(element, "to").getPrimaryType();
                    NamespaceResolver resolver = element.getAllNamespaces();
                    UnionCastableFunction ucf = new UnionCastableFunction(type, resolver, allowEmpty);
                    return new StaticFunctionCall(ucf, new Expression[]{body});
                }
            } else {
                throw new AssertionError("Unknown simple type variety " + flags);
            }
//            Expression body = loader.getFirstChildExpression(element);
//            SchemaType st = loader.getTypeAttribute(element, "as");
//            boolean allowEmpty = element.getAttributeValue(NamespaceUri.NULL,"emptiable").equals("1");
//            if (st == null) {
//                throw new AssertionError("Unknown simple type " + element.getAttributeValue(NamespaceUri.NULL,"as"));
//            } else if (st instanceof AtomicType) {
//                return new CastableExpression(body, (AtomicType) st, allowEmpty);
//            } else if (st instanceof ListType) {
//                NamespaceResolver resolver = element.getAllNamespaces();
//                ListCastableFunction ucf = new ListCastableFunction((ListType) st, resolver, allowEmpty);
//                return new StaticFunctionCall(ucf, new Expression[]{body});
//            } else if (st instanceof UnionType) {
//                NamespaceResolver resolver = element.getAllNamespaces();
//                UnionCastableFunction ucf = new UnionCastableFunction((UnionType) st, resolver, allowEmpty);
//                return new StaticFunctionCall(ucf, new Expression[]{body});
//            } else {
//                throw new AssertionError("Unknown simple type variety " + st.getClass());
//            }
        });

        eMap.put("check", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String cardAtt = element.getAttributeValue(NamespaceUri.NULL,"card");
            int c;
            switch (cardAtt) {
                case "?":
                    c = StaticProperty.ALLOWS_ZERO_OR_ONE;
                    break;
                case "*":
                    c = StaticProperty.ALLOWS_ZERO_OR_MORE;
                    break;
                case "+":
                    c = StaticProperty.ALLOWS_ONE_OR_MORE;
                    break;
                case "\u00B0":   // Obsolescent, drop this
                case "0":
                    c = StaticProperty.ALLOWS_ZERO;
                    break;
                case "1":
                    c = StaticProperty.EXACTLY_ONE;
                    break;
                default:
                    throw new IllegalStateException("Occurrence indicator: '" + cardAtt + "'");
            }
            String savedRole = element.getAttributeValue(NamespaceUri.NULL,"diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            return CardinalityChecker.makeCardinalityChecker(body, c, role);
        });

        eMap.put("choose", (loader, element) -> {
            List<Expression> conditions = new ArrayList<>();
            List<Expression> actions = new ArrayList<>();
            AxisIterator iter = element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
            NodeInfo child;
            boolean odd = true;
            while ((child = iter.next()) != null) {
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

        eMap.put("coercedFn", (loader, element) -> {
            ItemType type = loader.parseItemTypeAttribute(element, "type");
            Expression target = loader.getFirstChildExpression(element);
            FunctionItem targetFn;
            CoercedFunction coercedFn;
            if (target instanceof UserFunctionReference) {
                coercedFn = new CoercedFunction((SpecificFunctionType) type);
                final CoercedFunction coercedFn2 = coercedFn;
                final SymbolicName name = ((UserFunctionReference) target).getSymbolicName();
                loader.addCompletionAction(() -> coercedFn2.setTargetFunction(loader.getUserFunction((SymbolicName.F)name)));
            } else if (target instanceof Literal) {
                targetFn = (FunctionItem) ((Literal) target).getGroundedValue();
                coercedFn = new CoercedFunction(targetFn, (SpecificFunctionType) type, true);
            } else {
                throw new AssertionError();
            }
            return Literal.makeLiteral(coercedFn);
        });


        eMap.put("comment", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            Comment inst = new Comment();
            inst.setSelect(select);
            return inst;
        });

        eMap.put("compareToInt", (loader, element) -> {
            BigInteger i = new BigInteger(element.getAttributeValue(NamespaceUri.NULL,"val"));
            String opAtt = element.getAttributeValue(NamespaceUri.NULL,"op");
            Expression lhs = loader.getFirstChildExpression(element);
            return new CompareToIntegerConstant(lhs, parseValueComparisonOperator(opAtt), i.longValue());
        });

        eMap.put("compareToString", (loader, element) -> {
            String s = element.getAttributeValue(NamespaceUri.NULL,"val");
            String opAtt = element.getAttributeValue(NamespaceUri.NULL,"op");
            Expression lhs = loader.getFirstChildExpression(element);
            return new CompareToStringConstant(lhs, parseValueComparisonOperator(opAtt), StringView.tidy(s));
        });

        eMap.put("compAtt", (loader, element) -> {
            Expression name = loader.getExpressionWithRole(element, "name");
            Expression namespace = loader.getExpressionWithRole(element, "namespace");
            Expression content = loader.getExpressionWithRole(element, "select");
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
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
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            ComputedElement inst = new ComputedElement(name, namespace, schemaType, validation, true, false);
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
                asc.setConverter(new Converter.DownCastingConverter((AtomicType)toType, loader.config.getConversionRules()));
            } else {
                Converter c = asc.allocateConverter(loader.config, false, fromType);
                asc.setConverter(c);
            }
            String diag = element.getAttributeValue(NamespaceUri.NULL,"diag");
            if (diag != null) {
                asc.setRoleDiagnostic(() -> RoleDiagnostic.reconstruct(diag));
            }
            return asc;
        });

        eMap.put("copy", (loader, element) -> {
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            String sType = element.getAttributeValue(NamespaceUri.NULL,"sit");

            Copy inst = new Copy(false, false, schemaType, validation);
            inst.setContentExpression(loader.getFirstChildExpression(element));
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            inst.setCopyNamespaces(flags.contains("c"));
            inst.setBequeathNamespacesToChildren(flags.contains("i"));
            inst.setInheritNamespacesFromParent(flags.contains("n"));
            if (sType != null) {
                SequenceType st = AlphaCode.toSequenceType(sType, loader.getConfiguration());
                inst.setSelectItemType(st.getPrimaryType());
            }
            return inst;
        });

        eMap.put("copyOf", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            if (flags == null) {
                flags = "";
            }
            boolean copyNamespaces = flags.contains("c");
            boolean rejectDups = flags.contains("d");
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
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
            int count = Count.count(args.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT));
            Sequence[] argValues = new Sequence[count];
            count = 0;
            for (NodeInfo child : args.children(NodeKindTest.ELEMENT)) {
                if (child.getLocalPart().equals("x")) {
                    argValues[count++] = null;
                } else {
                    Expression arg = loader.loadExpression(child);
                    argValues[count++] = ((Literal) arg).getGroundedValue();
                }
            }
            FunctionItem f = new CurriedFunction(targetFn, argValues);
            return Literal.makeLiteral(f);
        });


        eMap.put("cvUntyped", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            ItemType toType = loader.parseAlphaCodeForItemType(element, "to");
            if (((SimpleType) toType).isNamespaceSensitive()) {
                return UntypedSequenceConverter.makeUntypedSequenceRejector(loader.config, body, (PlainType) toType);
            } else {
                UntypedSequenceConverter cv = UntypedSequenceConverter.makeUntypedSequenceConverter(loader.config, body, (PlainType) toType);
                String diag = element.getAttributeValue(NamespaceUri.NULL,"diag");
                if (diag != null) {
                    cv.setRoleDiagnostic(() -> RoleDiagnostic.reconstruct(diag));
                }
                return cv;
            }
        });

        eMap.put("data", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            String diag = element.getAttributeValue(NamespaceUri.NULL,"diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(diag);
            return new Atomizer(body, diag==null ? null : role);
        });

        eMap.put("dbl", (loader, element) -> {
            String val = element.getAttributeValue(NamespaceUri.NULL,"val");
            double d = StringToDouble.getInstance().stringToNumber(StringView.of(val).tidy());
            return Literal.makeLiteral(new DoubleValue(d));
        });

        eMap.put("dec", (loader, element) -> {
            String val = element.getAttributeValue(NamespaceUri.NULL,"val");
            return Literal.makeLiteral(BigDecimalValue.makeDecimalValue(val, false).asAtomic());
        });

        eMap.put("doc", (loader, element) -> {
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            boolean textOnly = flags != null && flags.contains("t");
            String base = element.getAttributeValue(NamespaceUri.NULL,"base");
            String constantText = element.getAttributeValue(NamespaceUri.NULL,"text");
            Expression body = loader.getFirstChildExpression(element);
            DocumentInstr inst = new DocumentInstr(textOnly, constantText == null ? null : StringView.tidy(constantText));
            inst.setContentExpression(body);
            inst.setValidationAction(validation, schemaType);
            return inst;
        });

        eMap.put("docOrder", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            boolean intra = element.getAttributeValue(NamespaceUri.NULL,"intra").equals("1");
            return new DocumentSorter(select, intra);
        });

        eMap.put("dot", (loader, element) -> {
            ContextItemExpression cie = new ContextItemExpression();
            SequenceType st = loader.parseAlphaCode(element, "type");
            ItemType type = st.getPrimaryType();
            boolean maybeAbsent = "a".equals(element.getAttributeValue(NamespaceUri.NULL, "flags"));
            ContextItemStaticInfo info = loader.getConfiguration().makeContextItemStaticInfo(type, maybeAbsent);
            cie.setStaticInfo(info);
            return cie;
        });

        eMap.put("dynCall", (loader, element) -> {
            List<Expression> children = getChildExpressionList(loader, element);
            return new DynamicFunctionCall(children.get(0), children.subList(1, children.size()));
        });

        eMap.put("elem", (loader, element) -> {
            String displayName = element.getAttributeValue(NamespaceUri.NULL,"name");
            String[] parts;
            try {
                parts = NameChecker.getQNameParts((displayName));
            } catch (QNameException err) {
                throw new XPathException(err);
            }
            String nsuri = element.getAttributeValue(NamespaceUri.NULL,"nsuri");
            StructuredQName name = new StructuredQName(parts[0], NamespaceUri.of(nsuri), parts[1]);

            NodeName elemName = new FingerprintedQName(name, loader.config.getNamePool());
            String ns = element.getAttributeValue(NamespaceUri.NULL,"namespaces");
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
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = loader.getTypeAttribute(element, "type");
            if (schemaType != null) {
                validation = Validation.BY_TYPE;
            }

            Expression content = loader.getFirstChildExpression(element);
            FixedElement elem = new FixedElement(elemName, bindings, true, true, schemaType, validation);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            if (flags != null) {
                elem.setInheritanceFlags(flags);
            }
            elem.setContentExpression(content);
            return elem;
        });


        eMap.put("empty", (loader, element) -> Literal.makeLiteral(EmptySequence.getInstance()));

        eMap.put("emptyTextNodeRemover", (loader, element) -> {
            Expression body = loader.getFirstChildExpression(element);
            return new EmptyTextNodeRemover(body);
        });

        eMap.put("error", (loader, element) -> {
            String message = element.getAttributeValue(NamespaceUri.NULL,"message");
            String code = element.getAttributeValue(NamespaceUri.NULL,"code");
            boolean isTypeErr = "1".equals(element.getAttributeValue(NamespaceUri.NULL,"isTypeErr"));
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
            String namespaces = element.getAttributeValue(NamespaceUri.NULL,"schNS");
            if (namespaces != null) {
                String[] uris = namespaces.split(" ");
                for (String nsUri : uris) {
                    inst.importSchemaNamespace(nsUri.equals("##") ? NamespaceUri.NULL : NamespaceUri.of(nsUri));
                }
            }

            List<WithParam> nonTunnelParams = new ArrayList<>();
            int slotNumber = 0;
            for (NodeInfo wp : element.children(NodeSelector.of(n -> n.getLocalPart().equals("withParam")))) {
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
            qEx.setOperator(Token.EVERY);
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
            return new VennExpression(lhs, Token.EXCEPT, rhs);
        });

        eMap.put("false", (loader, element) -> Literal.makeLiteral(BooleanValue.FALSE));

        eMap.put("filter", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            FilterExpression fe = new FilterExpression(lhs, rhs);
            fe.setFlags(flags);
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
            String name = element.getAttributeValue(NamespaceUri.NULL,"name");
            if (name.equals("_STRING-JOIN_2.0")) {
                // encountered in files exported by Saxon 9.7
                name = "string-join";
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
                SequenceIterator iter = element.iterateAxis(AxisInfo.ATTRIBUTE);
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
            final String diag = element.getAttributeValue(NamespaceUri.NULL,"diag");
            Expression arg = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue("", "flags");
            boolean allow40 = flags != null && flags.contains("4");
            return new FunctionSequenceCoercer(arg, type, () -> RoleDiagnostic.reconstruct(diag), allow40);
        });

        eMap.put("fnRef", (loader, element) -> {
            loader.needsPELicense("higher order functions");
            String name = element.getAttributeValue(NamespaceUri.NULL,"name");
            int arity = loader.getIntegerAttribute(element, "arity");
            RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
            SystemFunction f = null;
            if (name.startsWith("Q{")) {
                StructuredQName qName = StructuredQName.fromEQName((name));
                NamespaceUri uri = qName.getNamespaceUri();
                if (uri == NamespaceUri.MATH) {
                    f = MathFunctionSet.getInstance().makeFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.MAP_FUNCTIONS) {
                    f = MapFunctionSet.getInstance(40).makeFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.ARRAY_FUNCTIONS) {
                    f = ArrayFunctionSet.getInstance(40).makeFunction(qName.getLocalPart(), arity);
                } else if (uri == NamespaceUri.SAXON) {
                    f = loader.getConfiguration().bindSaxonExtensionFunction(qName.getLocalPart(), arity);
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
            return new IdentityComparison(lhs, Token.FOLLOWS, rhs);
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
            Expression action = loader.getSecondChildExpression(element);
            loader.localBindings.pop();
            forEx.setAction(action);

            return forEx;
        });

        eMap.put("forEach", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);

            Expression threads = loader.getExpressionWithRole(element, "threads");
            if (threads == null) {
                ForEach forEach =new ForEach(lhs, rhs);
                Expression sep = loader.getExpressionWithRole(element, "separator");
                if (sep != null) {
                    forEach.setSeparatorExpression(sep);
                }
                String flags = element.getAttributeValue("", "flags");
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
            String algorithmAtt = element.getAttributeValue(NamespaceUri.NULL,"algorithm");
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
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
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
                String collationName = ((StringLiteral) collationNameExp).getString().toString();
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
            String opAtt = element.getAttributeValue(NamespaceUri.NULL,"op");
            int op = getOperator(opAtt);
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String compAtt = element.getAttributeValue(NamespaceUri.NULL,"comp");
            AtomicComparer comp = loader.makeAtomicComparer(compAtt, element);
            GeneralComparison gc = new GeneralComparison20(lhs, op, rhs);
            gc.setAtomicComparer(comp);
            return gc;
        });


        eMap.put("gc10", (loader, element) -> {
            String opAtt = element.getAttributeValue(NamespaceUri.NULL,"op");
            int op = getOperator(opAtt);
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            String compAtt = element.getAttributeValue(NamespaceUri.NULL,"comp");
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
            Expression exp = null;
            if (name.hasURI(NamespaceUri.MATH)) {
                exp = MathFunctionSet.getInstance().makeFunction(name.getLocalPart(), args.length).makeFunctionCall(args);
            } else if (name.hasURI(NamespaceUri.MAP_FUNCTIONS)) {
                exp = MapFunctionSet.getInstance(40).makeFunction(name.getLocalPart(), args.length).makeFunctionCall(args);
            } else if (name.hasURI(NamespaceUri.ARRAY_FUNCTIONS)) {
                exp = ArrayFunctionSet.getInstance(40).makeFunction(name.getLocalPart(), args.length).makeFunctionCall(args);
            } else if (name.hasURI(NamespaceUri.SAXON)) {
                if (name.getLocalPart().equals("apply")) {
                    // legacy saxon:apply function for dynamic function calls: generate fn:apply
                    exp = XPath30FunctionSet.getInstance().makeFunction("apply", 2).makeFunctionCall(args);
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
                ic.setBaseURI(rsc.getStaticBaseUriString());
                ic.setPackageData(rsc.getPackageData());
                ic.setXPathLanguageLevel(31);
                ic.setDefaultElementNamespace(rsc.getDefaultElementNamespace());
                ic.setNamespaceResolver(rsc);
                ic.setBackwardsCompatibilityMode(rsc.isBackwardsCompatibility());
                ic.setDefaultCollationName(rsc.getDefaultCollationName());
                ic.setDefaultFunctionNamespace(rsc.getDefaultFunctionNamespace());
                ic.setDecimalFormatManager(rsc.getDecimalFormatManager());
                List<String> reasons = new ArrayList<>();
                exp = loader.config.getIntegratedFunctionLibrary().bind(sName, args, null, ic, reasons);
                if (exp == null) {
                    exp = loader.config.getBuiltInExtensionLibraryList(31).bind(sName, args, null, ic, reasons);
                }
                if (exp instanceof SystemFunctionCall) {
                    SystemFunction fn = ((SystemFunctionCall) exp).getTargetFunction();
                    fn.setRetainedStaticContext(loader.makeRetainedStaticContext(element));
                    SequenceIterator iter = element.iterateAxis(AxisInfo.ATTRIBUTE);
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
            }
//            if (exp instanceof SystemFunctionCall) {
//                ((SystemFunctionCall)exp).allocateArgumentEvaluators(args);
//            }
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
            BigInteger i = new BigInteger(element.getAttributeValue(NamespaceUri.NULL,"val"));
            return Literal.makeLiteral(IntegerValue.makeIntegerValue(i));
        });

        eMap.put("intersect", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new VennExpression(lhs, Token.INTERSECT, rhs);
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
            return new IdentityComparison(lhs, Token.IS, rhs);
        });

        eMap.put("isLast", (loader, element) -> {
            boolean cond = element.getAttributeValue(NamespaceUri.NULL,"test").equals("1");
            return new IsLastExpression(cond);
        });

        eMap.put("iterate", (loader, element) -> {
            Expression select = loader.getExpressionWithRole(element, "select");
            LocalParamBlock params = (LocalParamBlock) loader.getExpressionWithRole(element, "params");
            Expression onCompletion = loader.getExpressionWithRole(element, "on-completion");
            Expression action = loader.getExpressionWithRole(element, "action");
            return new IterateInstr(select, params, action, onCompletion);
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
            AxisIterator iter = element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
            NodeInfo child;
            while ((child = iter.next()) != null) {
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
            return new LookupAllExpression(select);
        });

        eMap.put("map", (loader, element) -> {
            List<Expression> children = getChildExpressionList(loader, element);
            AtomicValue key = null;
            HashTrieMap map = new HashTrieMap();
            for (Expression child : children) {
                if (key == null) {
                    key = (AtomicValue)((Literal)child).getGroundedValue();
                } else {
                    GroundedValue value = ((Literal) child).getGroundedValue();
                    map.initialPut(key, value);
                    key = null;
                }
            }
            return Literal.makeLiteral(map);
        });

        eMap.put("merge", (loader, element) -> {
            final MergeInstr inst = new MergeInstr();
            AxisIterator kids = element.iterateAxis(AxisInfo.CHILD,
                                                    new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "mergeSrc", loader.config.getNamePool()));
            NodeInfo msElem;
            List<MergeInstr.MergeSource> list = new ArrayList<>();
            while ((msElem = kids.next()) != null) {
                final MergeInstr.MergeSource ms = new MergeInstr.MergeSource(inst);
                String mergeSourceName = msElem.getAttributeValue(NamespaceUri.NULL,"name");
                if (mergeSourceName != null) {
                    ms.sourceName = mergeSourceName;
                }
                String valAtt = msElem.getAttributeValue(NamespaceUri.NULL,"validation");
                if (valAtt != null) {
                    ms.validation = Validation.getCode(valAtt);
                }
                SchemaType schemaType = loader.getTypeAttribute(msElem, "type");
                if (schemaType != null) {
                    ms.schemaType = schemaType;
                    ms.validation = Validation.BY_TYPE;
                }
                String flagsAtt = msElem.getAttributeValue(NamespaceUri.NULL,"flags");
                ms.streamable = "s".equals(flagsAtt);
                if (ms.streamable) {
                    loader.addCompletionAction(CSharp.methodRef(ms::prepareForStreaming));
                }
                RetainedStaticContext rsc = loader.makeRetainedStaticContext(element);
                ms.baseURI = rsc.getStaticBaseUriString();

                String accumulatorNames = msElem.getAttributeValue(NamespaceUri.NULL,"accum");
                if (accumulatorNames == null) {
                    accumulatorNames = "";
                }
                final List<StructuredQName> accNameList = new ArrayList<>();
                StringTokenizer tokenizer = new StringTokenizer(accumulatorNames);
                while (tokenizer.hasMoreTokens()) {
                    String token = tokenizer.nextToken();
                    StructuredQName name = StructuredQName.fromEQName((token));
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

        eMap.put("namespace", (loader, element) -> {
            Expression name = loader.getFirstChildExpression(element);
            Expression select = loader.getSecondChildExpression(element);
            NamespaceConstructor inst = new NamespaceConstructor(name);
            inst.setSelect(select);
            return inst;
        });

        eMap.put("nextIteration", (loader, element) -> {
            NextIteration inst = new NextIteration();
            AxisIterator kids = element.iterateAxis(AxisInfo.CHILD,
                                                    new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "withParam", loader.config.getNamePool()));
            NodeInfo wp;
            List<WithParam> params = new ArrayList<>();
            while ((wp = kids.next()) != null) {
                WithParam withParam = new WithParam();
                String flags = wp.getAttributeValue(NamespaceUri.NULL,"flags");
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
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
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
            String content = element.getAttributeValue(NamespaceUri.NULL,"content");
            String baseURI = element.getAttributeValue(NamespaceUri.NULL,"baseURI");
            NodeInfo node;
            switch (kind) {
                case Type.DOCUMENT:
                case Type.ELEMENT: {
                    StreamSource source = new StreamSource(new StringReader(content), baseURI);
                    node = loader.config.buildDocumentTree(source).getRootNode();
                    if (kind == Type.ELEMENT) {
                        node = VirtualCopy.makeVirtualCopy(node.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT).next());
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
                    String prefix = element.getAttributeValue(NamespaceUri.NULL,"prefix");
                    String ns = element.getAttributeValue(NamespaceUri.NULL, "ns");
                    String local = element.getAttributeValue(NamespaceUri.NULL,"localName");
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
            String levelAtt = element.getAttributeValue(NamespaceUri.NULL,"level");
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
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
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
            String packKey = element.getAttributeValue(NamespaceUri.NULL,"pack");
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
            String packKey = element.getAttributeValue(NamespaceUri.NULL,"pack");
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
            param.setVariableQName(name);
            param.setSlotNumber(slot);
            Expression select = loader.getExpressionWithRole(element, "select");
            if (select != null) {
                param.setSelectExpression(select);
                param.computeEvaluationMode();
            }
            Expression convert = loader.getExpressionWithRole(element, "conversion");
            if (convert != null) {
                param.setConversion(convert);
            }
            param.setRequiredType(loader.parseAlphaCode(element, "as"));
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
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
            AxisIterator iter = element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
            NodeInfo child;
            while ((child = iter.next()) != null) {
                children.add((LocalParam) loader.loadExpression(child));
            }
            return new LocalParamBlock(children.toArray(new LocalParam[0]));
        });

        eMap.put("partialApply", (loader, element) -> {
            int count = Count.count(element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT));
            Expression base = null;
            Expression[] args = new Expression[count - 1];
            count = 0;
            for (NodeInfo child : element.children(NodeKindTest.ELEMENT)) {
                if (count == 0) {
                    base = loader.loadExpression(child);
                } else if (child.getLocalPart().equals("null")) {
                    args[count - 1] = null;
                } else {
                    args[count - 1] = loader.loadExpression(child);
                }
                count++;
            }
            return new PartialApply(base, args);
        });

        eMap.put("precedes", (loader, element) -> {
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            return new IdentityComparison(lhs, Token.PRECEDES, rhs);
        });

        eMap.put("procInst", (loader, element) -> {
            Expression name = loader.getFirstChildExpression(element);
            Expression select = loader.getSecondChildExpression(element);
            ProcessingInstruction inst = new ProcessingInstruction(name);
            inst.setSelect(select);
            return inst;
        });

        eMap.put("qName", (loader, element) -> {
            String preAtt = element.getAttributeValue(NamespaceUri.NULL,"pre");
            String uriAtt = element.getAttributeValue(NamespaceUri.NULL,"uri");
            String locAtt = element.getAttributeValue(NamespaceUri.NULL,"loc");
            AtomicType type = BuiltInAtomicType.QNAME;
            if (element.getAttributeValue(NamespaceUri.NULL,"type") != null) {
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
            int from = loader.getIntegerAttribute(element, "from");
            int to = loader.getIntegerAttribute(element, "to");
            return Literal.makeLiteral(new IntegerRange(from, 1, to));
        });

        eMap.put("resultDoc", (loader, element) -> {
            loader.packStack.peek().setCreatesSecondaryResultDocuments(true);
            Expression href = null;
            Expression format = null;
            Expression content = null;
            String globalProps = element.getAttributeValue(NamespaceUri.NULL,"global");
            String localProps = element.getAttributeValue(NamespaceUri.NULL,"local");
            Properties globals = globalProps == null ? new Properties() : loader.importProperties(globalProps);
            Properties locals = localProps == null ? new Properties() : loader.importProperties(localProps);
            Map<StructuredQName, Expression> dynamicProperties = new HashMap<>();
            NodeInfo child;
            AxisIterator iter = element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
            while ((child = iter.next()) != null) {
                Expression exp = loader.loadExpression(child);
                String role = child.getAttributeValue(NamespaceUri.NULL,"role");
                if ("href".equals(role)) {
                    href = exp;
                } else if ("format".equals(role)) {
                    format = exp;
                } else if ("content".equals(role)) {
                    content = exp;
                } else {
                    StructuredQName name = StructuredQName.fromEQName((role));
                    dynamicProperties.put(name, exp);
                }
            }
            int validation = Validation.SKIP;
            String valAtt = element.getAttributeValue(NamespaceUri.NULL,"validation");
            if (valAtt != null) {
                validation = Validation.getCode(valAtt);
            }
            SchemaType schemaType = null;
            StructuredQName typeAtt = loader.getQNameAttribute(element, "type");
            if (typeAtt != null) {
                schemaType = loader.config.getSchemaType(typeAtt);
                validation = Validation.BY_TYPE;
            }
            ResultDocument instr = new ResultDocument(globals, locals, href, format, validation, schemaType,
                                                      dynamicProperties, loader.packStack.peek().getCharacterMapIndex());
            instr.setContentExpression(content);
            if ("a".equals(element.getAttributeValue(NamespaceUri.NULL,"flags"))) {
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
            String simpleAtt = element.getAttributeValue(NamespaceUri.NULL,"simple");
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
            qEx.setOperator(Token.SOME);
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
                schemaType = loader.getConfiguration().getSchemaType(typeAtt);
                validation = Validation.BY_TYPE;
            }
            ParseOptions options = loader.getConfiguration().getParseOptions()
                    .withSchemaValidationMode(validation)
                    .withTopLevelType(schemaType);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            if (flags != null) {
                if (flags.contains("S")) {
                    options = options.withSpaceStrippingRule(AllElementsSpaceStrippingRule.getInstance());
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
            }
            Expression body = loader.getExpressionWithRole(element, "body");
            Expression href = loader.getExpressionWithRole(element, "href");

            final SourceDocument inst = new SourceDocument(href, body, options);

            if (flags != null && flags.contains("s")) {
                loader.addCompletionAction(() -> inst.setSpaceStrippingRule(loader.getTopLevelPackage().getSpaceStrippingRule()));
            }
            String accumulatorNames = element.getAttributeValue(NamespaceUri.NULL,"accum");
            processAccumulatorList(loader, inst, accumulatorNames);
            return inst;
        });

        eMap.put("str", (loader, element) -> StringLiteral.makeLiteral(
                new StringValue(element.getAttributeValue(NamespaceUri.NULL,"val"))
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
                ref.setSuppliedType(AlphaCode.toSequenceType(sType, loader.getConfiguration()));
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
            String savedRole = element.getAttributeValue(NamespaceUri.NULL,"diag");
            Supplier<RoleDiagnostic> role = () -> RoleDiagnostic.reconstruct(savedRole);
            return new ItemChecker(body, type, role);
        });

        eMap.put("true", (loader, element) -> Literal.makeLiteral(BooleanValue.TRUE));

        eMap.put("try", (loader, element) -> {
            Expression tryExp = loader.getFirstChildExpression(element);
            TryCatch tryCatch = new TryCatch(tryExp);
            if ("r".equals(element.getAttributeValue(NamespaceUri.NULL,"flags"))) {
                tryCatch.setRollbackOutput(true);
            }
            AxisIterator iter = element.iterateAxis(
                    AxisInfo.CHILD, new NameTest(Type.ELEMENT, NamespaceUri.SAXON_XSLT_EXPORT, "catch", loader.config.getNamePool()));
            NodeInfo catchElement;
            NamePool pool = loader.getConfiguration().getNamePool();
            while ((catchElement = iter.next()) != null) {
                String errAtt = catchElement.getAttributeValue(NamespaceUri.NULL,"errors");
                String[] tests = errAtt.split(" ");
                List<QNameTest> list = new ArrayList<>();
                for (String t : tests) {
                    if (t.equals("*")) {
                        list.add(AnyNodeTest.getInstance());
                    } else if (t.startsWith("*:")) {
                        list.add(new LocalNameTest(pool, Type.ELEMENT, t.substring(2)));
                    } else if (t.endsWith("}*")) {
                        list.add(new NamespaceTest(pool, Type.ELEMENT, NamespaceUri.of(t.substring(2, t.length()-2))));
                    } else {
                        StructuredQName qName = StructuredQName.fromEQName((t));
                        list.add(new NameTest(Type.ELEMENT, new FingerprintedQName(qName, pool), pool));
                    }
                }
                QNameTest test;
                if (list.size() == 1) {
                    test = list.get(0);
                } else {
                    test = new UnionQNameTest(list);
                }
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
            return new VennExpression(lhs, Token.UNION, rhs);
        });

        eMap.put("useAS", (loader, element) -> {
            StructuredQName name = loader.getQNameAttribute(element, "name");
            boolean streamable = "s".equals(element.getAttributeValue(NamespaceUri.NULL,"flags"));
            UseAttributeSet use = new UseAttributeSet(name, streamable);
            int bindingSlot = loader.getIntegerAttribute(element, "bSlot");
            use.setBindingSlot(bindingSlot);
            loader.addComponentFixup(use);
            return use;
        });

        eMap.put("valueOf", (loader, element) -> {
            Expression select = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            boolean doe = flags != null && flags.contains("d");
            boolean notIfEmpty = flags != null && flags.contains("e");
            return new ValueOf(select, doe, notIfEmpty);
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
            String opAtt = element.getAttributeValue(NamespaceUri.NULL,"op");
            int op;
            op = parseValueComparisonOperator(opAtt);
            Expression lhs = loader.getFirstChildExpression(element);
            Expression rhs = loader.getSecondChildExpression(element);
            ValueComparison vc = new ValueComparison(lhs, op, rhs);
//            String compAtt = element.getAttributeValue(NamespaceUri.NULL,"comp");
//            AtomicComparer comp = loader.makeAtomicComparer(compAtt, element);
//            vc.setAtomicComparer(comp);
            String onEmptyAtt = element.getAttributeValue(NamespaceUri.NULL,"onEmpty");
            if (onEmptyAtt != null) {
                vc.setResultWhenEmpty(BooleanValue.get("1".equals(onEmptyAtt)));
            }
            return vc;
        });

    }

    private static int getLevelCode(String levelAtt) {
        if (levelAtt == null) {
            return NumberInstruction.SINGLE;
        } else {
            switch (levelAtt) {
                case "single":
                    return NumberInstruction.SINGLE;
                case "multi":
                    return NumberInstruction.MULTI;
                case "any":
                    return NumberInstruction.ANY;
                case "simple":
                    return NumberInstruction.SIMPLE;
                default:
                    throw new AssertionError();
            }
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
        AxisIterator iter = element.iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        NodeInfo child;
        while ((child = iter.next()) != null) {
            children.add(loader.loadExpression(child));
        }
        return children;
    }

    protected static Expression[] getChildExpressionArray(PackageLoaderHE loader, NodeInfo element) throws XPathException {
        List<Expression> children = getChildExpressionList(loader, element);
        return children.toArray(new Expression[0]);
    }

    protected static int getOperator(String opAtt) {
        int op;
        switch (opAtt) {
            case "=":
                op = Token.EQUALS;
                break;
            case "!=":
                op = Token.NE;
                break;
            case "<=":
                op = Token.LE;
                break;
            case ">=":
                op = Token.GE;
                break;
            case "<":
                op = Token.LT;
                break;
            case ">":
                op = Token.GT;
                break;
            default:
                throw new IllegalStateException();
        }
        return op;
    }

    private static int parseValueComparisonOperator(String opAtt) {
        int op;
        switch (opAtt) {
            case "eq":
                op = Token.FEQ;
                break;
            case "ne":
                op = Token.FNE;
                break;
            case "le":
                op = Token.FLE;
                break;
            case "ge":
                op = Token.FGE;
                break;
            case "lt":
                op = Token.FLT;
                break;
            case "gt":
                op = Token.FGT;
                break;
            default:
                throw new IllegalStateException();
        }
        return op;
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
            NodeTest type = (NodeTest) loader.parseAlphaCodeForItemType(element, "test");
            Expression exp = loader.getFirstChildExpression(element);
            return new GeneralNodePattern(exp, type);
        });

        pMap.put("p.genPos", (loader, element) -> {
            NodeTest type = (NodeTest) loader.parseAlphaCodeForItemType(element, "test");
            Expression exp = loader.getFirstChildExpression(element);
            String flags = element.getAttributeValue(NamespaceUri.NULL,"flags");
            GeneralPositionalPattern gpp = new GeneralPositionalPattern(type, exp);
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
            if (test instanceof NodeTest) {
                return new NodeTestPattern((NodeTest) test);
            } else {
                return new ItemTypePattern(test);
            }
        });

        pMap.put("p.venn", (loader, element) -> {
            Pattern p0 = loader.getFirstChildPattern(element);
            Pattern p1 = loader.getSecondChildPattern(element);
            String operator = element.getAttributeValue(NamespaceUri.NULL,"op");
            switch (operator) {
                case "union":
                    return new UnionPattern(p0, p1);
                case "intersect":
                    return new IntersectPattern(p0, p1);
                case "except":
                    return new ExceptPattern(p0, p1);
            }
            return null;
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
            String axisName = element.getAttributeValue(NamespaceUri.NULL,"axis");
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

// Copyright (c) 2018-2023 Saxonica Limited

