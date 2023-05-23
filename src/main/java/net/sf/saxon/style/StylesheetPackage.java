////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.PreparedStylesheet;
import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.ComponentBinding;
import net.sf.saxon.expr.PackageData;
import net.sf.saxon.expr.instruct.*;
import net.sf.saxon.functions.ExecutableFunctionLibrary;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.ConstructorFunctionLibrary;
import net.sf.saxon.om.Action;
import net.sf.saxon.om.SpaceStrippingRule;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.query.XQueryFunctionLibrary;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.serialize.CharacterMap;
import net.sf.saxon.serialize.CharacterMapIndex;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.*;
import net.sf.saxon.trans.rules.RuleManager;
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.type.Affinity;
import net.sf.saxon.type.TypeHierarchy;

import java.util.*;

import static net.sf.saxon.trans.Visibility.PRIVATE;

/**
 * A (compiled) stylesheet package. This may be created either by compiling a source XSLT package,
 * or by loading a saved package from disk. It therefore has no references to source XSLT documents.
 */
public class StylesheetPackage extends PackageData {

    private final static boolean TRACING = false;

    private PackageVersion packageVersion = null;
    private String packageName;
    private List<StylesheetPackage> usedPackages = new ArrayList<>();
    private int xsltVersion;

    private RuleManager ruleManager;
    private CharacterMapIndex characterMapIndex;

    private boolean createsSecondaryResultDocuments;

    private List<Action> completionActions = new ArrayList<>();
    protected GlobalContextRequirement globalContextRequirement = null;
    private boolean containsGlobalContextItemDeclaration = false;
    protected SpaceStrippingRule stripperRules;
    private boolean stripsWhitespace = false;
    private boolean stripsTypeAnnotations = false;
    protected Properties defaultOutputProperties;
    private StructuredQName defaultMode;
    private boolean declaredModes;
    protected Map<StructuredQName, Properties> namedOutputProperties = new HashMap<>(4);

    // table of imported schemas. The members of this set are strings holding the target namespace.
    protected Set<String> schemaIndex = new HashSet<>(10);

    private FunctionLibraryList functionLibrary;
    private XQueryFunctionLibrary queryFunctions;
    private ExecutableFunctionLibrary overriding;
    private ExecutableFunctionLibrary underriding;
    private int maxFunctionArity = -1;
    private boolean retainUnusedFunctions = false;
    private boolean implicitPackage;


    // Table of components declared in this package or imported from used packages. Key is the symbolic identifier of the
    // component; value is the component itself. Hidden components are not included in this table because their names
    // need not be unique, and because they are not available for reference by name.

    private HashMap<SymbolicName, Component> componentIndex = new HashMap<>(20);

    protected List<Component> hiddenComponents = new ArrayList<>();

    protected HashMap<SymbolicName, Component> overriddenComponents = new HashMap<>();

    private HashMap<SymbolicName, Component> abstractComponents = new HashMap<>();

    /**
     * Create a stylesheet package
     *
     * @param config the Saxon Configuration
     */

    public StylesheetPackage(Configuration config) {
        super(config);
        setHostLanguage(HostLanguage.XSLT);
        setAccumulatorRegistry(config.makeAccumulatorRegistry());
    }

    /**
     * Get a map containing all the components in this package, indexed by symbolic name
     *
     * @return a map containing all the components in the package. This does not include components
     * in used packages, except to the extent that they have been copied into this package.
     */

    public HashMap<SymbolicName, Component> getComponentIndex() {
        return componentIndex;
    }

    /**
     * Get the packages referenced from this package in an xsl:use-package declaration
     *
     * @return the packages used by this package
     */

    public Iterable<StylesheetPackage> getUsedPackages() {
        return usedPackages;
    }

    /**
     * Add a package to the list of packages directly used by this one
     * @param pack the used package
     */

    public void addUsedPackage(StylesheetPackage pack) {
        usedPackages.add(pack);
    }

    /**
     * Ask whether a given library package is within the package subtree rooted at this package
     * @param pack the library package
     * @return true if the given library package is in the transitive contents of this package
     */

    public boolean contains(StylesheetPackage pack) {
        for (StylesheetPackage p : usedPackages) {
            if (p == pack || p.contains(pack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the version of the XSLT language specification to which the package manifest conforms
     *
     * @param version the version (times ten) as an integer
     */
    public void setVersion(int version) {
        this.xsltVersion = version;
    }

    /**
     * Set the name of the package
     *
     * @param packageName the name of the package. This is supposed to be an absolute URI, but Saxon accepts any string.
     */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    /**
     * Get the version of the XSLT language specification to which the package manifest conforms
     *
     * @return the version of the xslPackage, times ten, as an integer
     */
    public int getVersion() {
        return xsltVersion;
    }

    /**
     * Get the package-version identifier appearing on the xsl:package element
     *
     * @return the version identifier as a structured entity
     */
    public PackageVersion getPackageVersion() {
        return packageVersion;
    }

    /**
     * Set the package-version identifier appearing on the xsl:package element
     *
     * @param version the version identifier as a structured entity
     */
    public void setPackageVersion(PackageVersion version) {
        packageVersion = version;
    }

    /**
     * Get the name identifier of the xsl:package as an absolute URI
     *
     * @return a string value. This is supposed to be an absolute URI, but Saxon accepts any string.
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Ask if this is an implicit package (one rooted at xsl:stylesheet or xsl:transform rather than xsl:package
     *
     * @return true if this package was rooted at an xsl:stylesheet or xsl:transform element
     */
    public boolean isImplicitPackage() {
        return implicitPackage;
    }

    /**
     * Say whether this is an implicit package (one rooted at xsl:stylesheet or xsl:transform rather than xsl:package
     *
     * @param implicitPackage true if this package was rooted at an xsl:stylesheet or xsl:transform element
     */

    public void setImplicitPackage(boolean implicitPackage) {
        this.implicitPackage = implicitPackage;
    }


    /**
     * Ask whether the package was compiled with just-in-time compilation of template rules enabled
     *
     * @return false (always) for Saxon-HE
     */

    public boolean isJustInTimeCompilation() {
        return false;
    }

    /**
     * Say whether the package was compiled with just-in-time compilation of template rules enabled.
     * This method is called by the compiler itself and should not be called by users. The setting is ignored
     * in Saxon-HE.
     *
     * @param justInTimeCompilation set to true if just-in-time compilation was enabled when the package was compiled
     */

    public void setJustInTimeCompilation(boolean justInTimeCompilation) {
        // no action
    }

    /**
     * Get the rule manager, which knows about all the modes present in the package
     * @return the rule manager
     */

    public RuleManager getRuleManager() {
        return ruleManager;
    }

    /**
     * Set the rule manager, which knows about all the modes present in the package
     * @param ruleManager the rule manager
     */

    public void setRuleManager(RuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }


    /**
     * Get the default mode for the package
     * @return the default mode for the package as a whole (this may change for individual elements/subtrees within
     * the package)
     */

    public StructuredQName getDefaultMode() {
        return defaultMode;
    }

    /**
     * Set the default mode for the package
     * @param defaultMode the default mode for the package
     */

    public void setDefaultMode(StructuredQName defaultMode) {
        this.defaultMode = defaultMode;
    }

    /**
     * Say whether it is required that modes be explicitly declared
     *
     * @param declared true if modes referenced within this package must be explicitly declared
     */

    public void setDeclaredModes(boolean declared) {
        declaredModes = declared;
    }

    /**
     * Ask whether it is required that modes be explicitly declared
     *
     * @return true if modes referenced within this package must be explicitly declared
     */

    public boolean isDeclaredModes() {
        return declaredModes;
    }

    /**
     * Get the whitespace stripping rules for this package
     *
     * @return the whitespace stripping rules (based on xsl:strip-space and xsl:preserve-space)
     */

    public SpaceStrippingRule getSpaceStrippingRule() {
        return stripperRules;
    }

    /**
     * Get the index of named character maps defined in this package
     * @return the index of character maps
     */

    public CharacterMapIndex getCharacterMapIndex() {
        return characterMapIndex;
    }

    /**
     * Set the index of named character maps defined in this package
     * @param characterMapIndex the index of named character maps
     */

    public void setCharacterMapIndex(CharacterMapIndex characterMapIndex) {
        this.characterMapIndex = characterMapIndex;
    }

    /**
     * Ask whether the package contains an xsl:result-document instruction
     * @return true if the package contains an xsl:result-document instruction
     */

    public boolean isCreatesSecondaryResultDocuments() {
        return createsSecondaryResultDocuments;
    }

    /**
     * Say whether the package contains an xsl:result-document instruction
     * @param createsSecondaryResultDocuments true if the package contains an xsl:result-document instruction
     */

    public void setCreatesSecondaryResultDocuments(boolean createsSecondaryResultDocuments) {
        this.createsSecondaryResultDocuments = createsSecondaryResultDocuments;
    }

    /**
     * Ask whether the package defines that type annotations should be stripped from input
     * documents loaded from within this package
     * @return true if documents loaded by this package should have any type annotations stripped.
     */

    public boolean isStripsTypeAnnotations() {
        return stripsTypeAnnotations;
    }

    /**
     * Say whether the package defines that type annotations should be stripped from input
     * documents loaded from within this package
     *
     * @param stripsTypeAnnotations set to true if documents loaded by this package
     *                              should have any type annotations stripped.
     */


    public void setStripsTypeAnnotations(boolean stripsTypeAnnotations) {
        this.stripsTypeAnnotations = stripsTypeAnnotations;
    }

    /**
     * Get the whitespace stripping rules for source documents loaded from within this package
     * @return the whitespace stripping rules for this package
     */

    public SpaceStrippingRule getStripperRules() {
        return stripperRules;
    }

    /**
     * Set the whitespace stripping rules for source documents loaded from within this package
     * @param stripperRules the whitespace stripping rules for this package
     */

    public void setStripperRules(SpaceStrippingRule stripperRules) {
        this.stripperRules = stripperRules;
    }

    /**
     * Set the default (unnamed) serialization properties to be used for documents output
     * using xsl:result-document instructions within this package
     * @param props the default serialization properties for this package
     */

    public void setDefaultOutputProperties(Properties props) {
        defaultOutputProperties = props;
    }

    /**
     * Define a named set serialization properties to be used for documents output
     * using xsl:result-document instructions within this package
     * @param name the name of the serialization property set
     * @param props the serialization properties
     */

    public void setNamedOutputProperties(StructuredQName name, Properties props) {
        namedOutputProperties.put(name, props);
    }

    /**
     * Get a set of named output properties
     * @param name the output declaration name
     * @return the corresponding properties; or null if the name is unknown
     */

    public Properties getNamedOutputProperties(StructuredQName name) {
        return namedOutputProperties.get(name);
    }

    /**
     * Get the set of namespaces of schema declarations imported into this package
     * @return the set of imported namespaces
     */

    public Set<String> getSchemaNamespaces() {
        return schemaIndex;
    }

    /**
     * Set the required context item type. Used when there is an xsl:global-context-item child element
     * @param requirement details of the requirement for the global context item
     */

    public void setContextItemRequirements(GlobalContextRequirement requirement) throws XPathException {
        if (containsGlobalContextItemDeclaration) {
            // the new requirements must be consistent with the existing requirements
            if ((!requirement.isAbsentFocus() && globalContextRequirement.isAbsentFocus()) ||
                    (requirement.isMayBeOmitted() && !globalContextRequirement.isMayBeOmitted())) {
                throw new XPathException(
                        "The package contains two xsl:global-context-item declarations with conflicting @use attributes", "XTSE3087");
            }
            TypeHierarchy th = getConfiguration().getTypeHierarchy();
            if (th.relationship(requirement.getRequiredItemType(), globalContextRequirement.getRequiredItemType()) != Affinity.SAME_TYPE) {
                throw new XPathException("The package contains two xsl:global-context-item declarations with conflicting item types", "XTSE3087");
            }
        }
        containsGlobalContextItemDeclaration = true;
        globalContextRequirement = requirement;
    }

    /**
     * Get the required context item type and other details of the global context item.
     * Used when there is an xsl:global-context-item child element
     * @return details of the requirement for the global context item
     */

    public GlobalContextRequirement getContextItemRequirements() {
        return globalContextRequirement;
    }

    /**
     * Say whether there is an xsl:strip-space declaration in the stylesheet package
     *
     * @param strips true if an xsl:strip-space declaration has been found
     */

    public void setStripsWhitespace(boolean strips) {
        this.stripsWhitespace = strips;
    }

    /**
     * Ask whether there is an xsl:strip-space declaration in the stylesheet package
     *
     * @return true if an xsl:strip-space declaration has been found
     */

    public boolean isStripsWhitespace() {
        return this.stripsWhitespace;
    }

    /**
     * Register a callback action to be performed during the completion phase of building the package
     *
     * @param action the callback action
     */

    public void addCompletionAction(Action action) {
        completionActions.add(action);
    }

    /**
     * Perform all registered completion actions for the package
     * @throws XPathException if any of these completion actions fails
     */

    protected void complete() throws XPathException {
        // Perform the completion actions
        for (Action a : completionActions) {
            a.doAction();
        }
        allocateBinderySlots();

    }

    /**
     * Allocate slots to global variables. Slot numbers are unique within a package
     */

    public void allocateBinderySlots() {
        SlotManager slotManager = getConfiguration().makeSlotManager();
        for (Component c : componentIndex.values()) {
            registerGlobalVariable(c, slotManager);
        }
        for (Component c : hiddenComponents) {
            registerGlobalVariable(c, slotManager);
        }
        setGlobalSlotManager(slotManager);
    }

    /**
     * Register a global variable declared within this package, allocating it a slot
     * number within the Bindery
     * @param c the component representing a global variable; it may also be another
     *          kind of component, but in this case it is ignored
     * @param slotManager the SlotManager defining the allocation of variables to
     *                    slots in the Bindery
     */

    private void registerGlobalVariable(Component c, SlotManager slotManager) {
        if (c.getActor() instanceof GlobalVariable) {
            GlobalVariable var = (GlobalVariable) c.getActor();
            int slot = slotManager.allocateSlotNumber(var.getVariableQName());
            var.setPackageData(this);
            var.setBinderySlotNumber(slot);
            if (c.getVisibility() != Visibility.HIDDEN) {
                addGlobalVariable(var);
            }
        }
    }

    /**
     * Add a component to the package
     * @param component the component to be added
     */

    public void addComponent(Component component) {
        SymbolicName name = component.getActor().getSymbolicName();
        componentIndex.put(name, component);
        if (component.getVisibility() == Visibility.ABSTRACT && component.getContainingPackage() == this) {
            abstractComponents.put(component.getActor().getSymbolicName(), component);
        }
    }

    @Override
    public void addGlobalVariable(GlobalVariable variable) {
        super.addGlobalVariable(variable);
        SymbolicName name = variable.getSymbolicName();
        if (componentIndex.get(name) == null) {
            Component comp = variable.getDeclaringComponent();
            if (comp == null) {
                comp = variable.makeDeclaringComponent(PRIVATE, this);
            }
            addComponent(comp);
        }
    }

    /**
     * Get the maximum arity of functions in this package
     * @return the maximum arity
     */

    public int getMaxFunctionArity() {
        if (maxFunctionArity == -1) {
            for (Component c : componentIndex.values()) {
                if (c.getActor() instanceof UserFunction) {
                    if (((UserFunction) c.getActor()).getArity() > maxFunctionArity) {
                        maxFunctionArity = ((UserFunction) c.getActor()).getArity();
                    }
                }
            }
        }
        return maxFunctionArity;
    }

    /**
     * Get the component within this package having a given symbolic name
     *
     * @param name the symbolic name of the required component
     * @return the requested component, or null if it does not exist in the package
     */

    public Component getComponent(SymbolicName name) {
        return componentIndex.get(name);
    }

//    public Component getHiddenComponent(SymbolicName name) {
//        return hiddenComponents.get(name);
//    }

    public void addHiddenComponent(Component component) {
        hiddenComponents.add(component);
    }

    /**
     * If this component overrides a component named N, get the component that N overrides (that
     * is, the component identified by xsl:original appearing within the overriding declaration of N)
     * @param name the name N
     * @return if there is an overriding declaration of N, then the original declaration of N from the
     * used package; otherwise null
     */

    public Component getOverriddenComponent(SymbolicName name) {
        return overriddenComponents.get(name);
    }

    public void addOverriddenComponent(Component comp) {
        overriddenComponents.put(comp.getActor().getSymbolicName(), comp);
    }

    /**
     * Add modified copies of components from a package that is referenced from this one in an xsl:use-package declaration
     *
     * @param usedPackage the used package
     * @param acceptors abstraction of a list of xsl:accept declarations, which can modify the visibility of accepted components
     * @param overrides the set of names of components from the used package that are overridden in the using package
     * @throws XPathException if duplicate components are found
     */

    public void addComponentsFromUsedPackage(StylesheetPackage usedPackage,
                                             List<XSLAccept> acceptors,
                                             final Set<SymbolicName> overrides) throws XPathException {
        usedPackages.add(usedPackage);

        trace("=== Adding components from " + usedPackage.getPackageName() + " to " + getPackageName() + " ===");

        // Create copies of the components in the used package, with suitably adjusted visibility

        // Create a mapping from components in the used package to their corresponding components
        // in the using package, so that we can re-bind the component bindings

        final Map<Component, Component> correspondence = new HashMap<>();

        for (Map.Entry<SymbolicName, Component> namedComponentEntry : usedPackage.componentIndex.entrySet()) {
            SymbolicName name = namedComponentEntry.getKey();
            Component oldC = namedComponentEntry.getValue();

            Visibility oldV = oldC.getVisibility();

            Visibility newV = null;

            if (overrides.contains(name) && !(oldC.getActor() instanceof Mode)) {
                newV = Visibility.HIDDEN;
            } else {
                Visibility acceptedVisibility = explicitAcceptedVisibility(name, acceptors);
                if (acceptedVisibility != null) {
                    if (!XSLAccept.isCompatible(oldV, acceptedVisibility)) {
                        throw new XPathException("Cannot accept a " + oldV.show() +
                                                         " component (" + name + ") from package " + usedPackage.getPackageName()
                                                         + " with visibility " + acceptedVisibility.show(), "XTSE3040");
                    }
                    newV = acceptedVisibility;
                } else {
                    acceptedVisibility = wildcardAcceptedVisibility(name, acceptors);
                    if (acceptedVisibility != null) {
                        if (XSLAccept.isCompatible(oldV, acceptedVisibility)) {
                            newV = acceptedVisibility;
                        }
                    }
                }

                if (newV == null) {
                    if (oldV == Visibility.PUBLIC || oldV == Visibility.FINAL) {
                        newV = Visibility.PRIVATE;
                    } else {
                        newV = Visibility.HIDDEN;
                    }
                }
            }

            trace(oldC.getActor().getSymbolicName() + " (" + oldV.show() + ") becomes " + newV.show());

            final Component newC = Component.makeComponent(oldC.getActor(), newV, VisibilityProvenance.DERIVED, this, oldC.getDeclaringPackage());
            correspondence.put(oldC, newC);
            newC.setBaseComponent(oldC);

            if (overrides.contains(name)) {
                // Note: overrides is all the overrides, not only those for this xsl:use-package;
                // but we have already checked that xsl:override declarations match something in the
                // right package.
                overriddenComponents.put(name, newC);
                if (newV != Visibility.ABSTRACT) {
                    abstractComponents.remove(name);
                }
            }
            if (newC.getVisibility() == Visibility.HIDDEN) {
                hiddenComponents.add(newC);
            } else if (componentIndex.get(name) != null) {
                if (!(oldC.getActor() instanceof Mode)) {
                    throw new XPathException("Duplicate " + namedComponentEntry.getKey(), "XTSE3050", oldC.getActor());
                }
            } else {
                componentIndex.put(name, newC);
                if (oldC.getActor() instanceof Mode && (oldV == Visibility.PUBLIC || oldV == Visibility.FINAL) ) {
                    Mode existing = getRuleManager().obtainMode(name.getComponentName(), false);
                    if (existing != null) {
                        throw new XPathException("Duplicate " + namedComponentEntry.getKey(), "XTSE3050", oldC.getActor());
                    } else {
                        //getRuleManager().registerMode((Mode)oldC.getCode());
                    }
                }
            }

            if (newC.getActor() instanceof Mode && overrides.contains(name)) {
                addCompletionAction(() -> {
                    trace("Doing mode completion for " + newC.getActor().getSymbolicName());
                    List<ComponentBinding> oldBindings = newC.getBaseComponent().getComponentBindings();
                    List<ComponentBinding> newBindings = newC.getComponentBindings();
                    for (int i=0; i<oldBindings.size(); i++) {
                        SymbolicName name12 = oldBindings.get(i).getSymbolicName();
                        Component target;
                        if (overrides.contains(name12)) {
                            // if there is an override in this package, we bind to it
                            target = getComponent(name12);
                            if (target == null) {
                                throw new AssertionError("We know there's an override for " + name12 + ", but we can't find it");
                            }
                        } else {
                            // otherwise we bind to the component in this package that corresponds to the component in the used package
                            target = correspondence.get(oldBindings.get(i).getTarget());
                            if (target == null) {
                                throw new AssertionError("Saxon can't find the new component corresponding to " + name12);
                            }
                        }
                        ComponentBinding newBinding = new ComponentBinding(name12, target);
                        newBindings.set(i, newBinding);
                    }

                });
            } else {
                addCompletionAction(() -> {
                    trace("Doing normal completion for " + newC.getActor().getSymbolicName());
                    List<ComponentBinding> oldBindings = newC.getBaseComponent().getComponentBindings();
                    List<ComponentBinding> newBindings = new ArrayList<>(oldBindings.size());
                    makeNewComponentBindings(overrides, correspondence, oldBindings, newBindings);
                    newC.setComponentBindings(newBindings);
                });
            }

        }

        for (Component oldC : usedPackage.hiddenComponents) {

            trace(oldC.getActor().getSymbolicName() + " (HIDDEN, declared in " + oldC.getDeclaringPackage().getPackageName() + ") becomes HIDDEN");

            final Component newC = Component.makeComponent(oldC.getActor(), Visibility.HIDDEN, VisibilityProvenance.DERIVED, this, oldC.getDeclaringPackage());
            correspondence.put(oldC, newC);
            newC.setBaseComponent(oldC);

            hiddenComponents.add(newC);

            addCompletionAction(() -> {
                List<ComponentBinding> oldBindings = newC.getBaseComponent().getComponentBindings();
                List<ComponentBinding> newBindings = new ArrayList<>(oldBindings.size());
                makeNewComponentBindings(overrides, correspondence, oldBindings, newBindings);
                newC.setComponentBindings(newBindings);
            });

        }

        if (usedPackage.isCreatesSecondaryResultDocuments()) {
            setCreatesSecondaryResultDocuments(true);
        }
    }

    private void makeNewComponentBindings(Set<SymbolicName> overrides, Map<Component, Component> correspondence, List<ComponentBinding> oldBindings, List<ComponentBinding> newBindings) {
        for (ComponentBinding oldBinding : oldBindings) {
            SymbolicName name = oldBinding.getSymbolicName();
            Component target;
            if (overrides.contains(name)) {
                // if there is an override in this package, we bind to it
                target = getComponent(name);
                if (target == null) {
                    throw new AssertionError("We know there's an override for " + name + ", but we can't find it");
                }
            } else {
                // otherwise we bind to the component in this package that corresponds to the component in the used package
                target = correspondence.get(oldBinding.getTarget());
                if (target == null) {
                    throw new AssertionError("Saxon can't find the new component corresponding to " + name);
                }
            }
            ComponentBinding newBinding = new ComponentBinding(name, target);
            newBindings.add(newBinding);
        }
    }

    private void trace(String message) {
        if (TRACING) {
            System.err.println(message);
        }
    }

    private Visibility explicitAcceptedVisibility(SymbolicName name, List<XSLAccept> acceptors) throws XPathException {
        for (XSLAccept acceptor : acceptors) {
            for (ComponentTest test : acceptor.getExplicitComponentTests()) {
                if (test.matches(name)) {
                    return acceptor.getVisibility();
                }
            }
        }
        return null;
    }

    private Visibility wildcardAcceptedVisibility(SymbolicName name, List<XSLAccept> acceptors) throws XPathException {
        // Note: last one wins
        Visibility vis = null;
        for (XSLAccept acceptor : acceptors) {
            for (ComponentTest test : acceptor.getWildcardComponentTests()) {
                if (((NodeTest) test.getQNameTest()).getDefaultPriority() == -0.25 && test.matches(name)) {
                    vis = acceptor.getVisibility();
                }
            }
        }
        if (vis != null) {
            return vis;
        }
        for (XSLAccept acceptor : acceptors) {
            for (ComponentTest test : acceptor.getWildcardComponentTests()) {
                if (test.matches(name)) {
                    vis = acceptor.getVisibility();
                }
            }
        }
        return vis;
    }


    /**
     * Create the function library containing stylesheet functions declared in this package
     */

    public void createFunctionLibrary() {

        FunctionLibraryList functionLibrary = new FunctionLibraryList();
        boolean includeHOF = !("HE".equals(getTargetEdition()) || "JS".equals(getTargetEdition()));
        functionLibrary.addFunctionLibrary(includeHOF ? config.getXSLT30FunctionSet() : new Configuration().getXSLT30FunctionSet());
        functionLibrary.addFunctionLibrary(new StylesheetFunctionLibrary(this, true));
        functionLibrary.addFunctionLibrary(config.getBuiltInExtensionLibraryList());
        functionLibrary.addFunctionLibrary(new ConstructorFunctionLibrary(config));
        if ("JS".equals(getTargetEdition())) {
            addIxslFunctionLibrary(functionLibrary);
        }

        queryFunctions = new XQueryFunctionLibrary(config);
        functionLibrary.addFunctionLibrary(queryFunctions);
        functionLibrary.addFunctionLibrary(config.getIntegratedFunctionLibrary());
        config.addExtensionBinders(functionLibrary);
        functionLibrary.addFunctionLibrary(new StylesheetFunctionLibrary(this, false));

        this.functionLibrary = functionLibrary;
    }

    protected void addIxslFunctionLibrary(FunctionLibraryList functionLibrary) {}

    /**
     * Get the function library.
     *
     * @return the function library
     */

    public FunctionLibraryList getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Get the library of functions exposed by this stylesheet package, that
     * is, functions whose visibility is public or final
     */

    public FunctionLibrary getPublicFunctions() {
        return new PublicStylesheetFunctionLibrary(functionLibrary);
    }

    /**
     * Get the library of functions imported from XQuery
     * @return the XQuery function library
     */

    public XQueryFunctionLibrary getXQueryFunctionLibrary() {
        return queryFunctions;
    }


    /**
     * Set details of functions available for calling anywhere in this package. This is the
     * function library used for resolving run-time references to functions, for example
     * from xsl:evaluate, function-available(), or function-lookup().
     * @param overriding      library of stylesheet functions declared with override=yes
     * @param underriding     library of stylesheet functions declared with override=no
     */


    public void setFunctionLibraryDetails(FunctionLibraryList library,
                                          ExecutableFunctionLibrary overriding,
                                          ExecutableFunctionLibrary underriding) {
        if (library != null) {
            this.functionLibrary = library;
        }
        this.overriding = overriding;
        this.underriding = underriding;
    }

    /**
     * Get the function with a given name and arity
     *
     * @param name the symbolic name of the function (QName plus arity)
     * @return the requested function, or null if none can be found
     */

    protected UserFunction getFunction(SymbolicName.F name) {
        if (name.getArity() == -1) {
            // supports the single-argument function-available() function
            int maximumArity = 20;
            for (int a = 0; a < maximumArity; a++) {
                SymbolicName.F sn = new SymbolicName.F(name.getComponentName(), a);
                UserFunction uf = getFunction(sn);
                if (uf != null) {
                    uf.incrementReferenceCount();
                    return uf;
                }
            }
            return null;
        } else {
            Component component = getComponentIndex().get(name);
            if (component != null) {
                UserFunction uf = (UserFunction) component.getActor();
                uf.incrementReferenceCount();
                return uf;
            } else {
                return null;
            }
        }
    }

    /**
     * Ask whether private functions for which there are no static references need to be
     * retained
     * @return true if such functions need to be retained, typically because the stylesheet
     * package uses xsl:evaluate or fn:function-lookup
     */

    public boolean isRetainUnusedFunctions() {
        return retainUnusedFunctions;
    }

    /**
     * Say that private functions need to be retained, typically because the stylesheet
     * package uses xsl:evaluate or fn:function-lookup
     */

    public void setRetainUnusedFunctions() {
        this.retainUnusedFunctions = true;
    }

    /**
     * Copy information from this package to the PreparedStylesheet.
     *
     * @param pss the PreparedStylesheet to be updated
     * @throws net.sf.saxon.trans.XPathException if the PreparedStylesheet cannot be updated
     */

    public void updatePreparedStylesheet(PreparedStylesheet pss) throws XPathException {

        for (Map.Entry<SymbolicName, Component> entry : componentIndex.entrySet()) {
            if (entry.getValue().getVisibility() == Visibility.ABSTRACT) {
                abstractComponents.put(entry.getKey(), entry.getValue());
            }
        }

        pss.setTopLevelPackage(this);
        if (isSchemaAware() || !schemaIndex.isEmpty()) {
            pss.setSchemaAware(true);
        }
        pss.setHostLanguage(HostLanguage.XSLT);

        //pss.setStylesheetFunctionLibrary(stylesheetFunctionLibrary);

        FunctionLibraryList libraryList = new FunctionLibraryList();
        for (FunctionLibrary lib : functionLibrary.getLibraryList()) {
            if (lib instanceof StylesheetFunctionLibrary) {
                if (((StylesheetFunctionLibrary) lib).isOverrideExtensionFunction()) {
                    libraryList.addFunctionLibrary(overriding);
                    //pss.getStylesheetFunctions().addFunctionLibrary(overriding);
                } else {
                    libraryList.addFunctionLibrary(underriding);
                    //pss.getStylesheetFunctions().addFunctionLibrary(underriding);
                }
            } else {
                libraryList.addFunctionLibrary(lib);
            }
        }
        pss.setFunctionLibrary(libraryList);

        if (!pss.createsSecondaryResult()) {
            pss.setCreatesSecondaryResult(mayCreateSecondaryResultDocuments());
        }
        pss.setDefaultOutputProperties(defaultOutputProperties);
        for (Map.Entry<StructuredQName, Properties> entry : namedOutputProperties.entrySet()) {
            pss.setOutputProperties(entry.getKey(), entry.getValue());
        }

        // Build the index of named character maps

        if (characterMapIndex != null) {
            for (CharacterMap cm : characterMapIndex) {
                pss.getCharacterMapIndex().putCharacterMap(cm.getName(), cm);
            }
        }

        // Finish off the lists of template rules

//        ruleManager.checkConsistency();
//        ruleManager.computeRankings();
//        ruleManager.invertStreamableTemplates();
//        if (config.obtainOptimizer().isOptionSet(OptimizerOptions.RULE_SET)) {
//            ruleManager.optimizeRules();
//        }
        pss.setRuleManager(ruleManager);


        // Add named templates to the prepared stylesheet

        for (Component comp : componentIndex.values()) {
            if (comp.getActor() instanceof NamedTemplate) {
                NamedTemplate t = (NamedTemplate) comp.getActor();
                pss.putNamedTemplate(t.getTemplateName(), t);
            }
        }

        // Share the component index with the prepared stylesheet

        pss.setComponentIndex(componentIndex);

        // Register stylesheet parameters

        for (Component comp : componentIndex.values()) {
            if (comp.getActor() instanceof GlobalParam) {
                GlobalParam gv = (GlobalParam) comp.getActor();
                pss.registerGlobalParameter(gv);
            }
        }

        // Set the requirements for the global context item

        if (globalContextRequirement != null) {
            pss.setGlobalContextRequirement(globalContextRequirement);
        }

    }

    private boolean mayCreateSecondaryResultDocuments() {
        if (createsSecondaryResultDocuments) {
            return true;
        }
        for (StylesheetPackage p : usedPackages) {
            if (p.mayCreateSecondaryResultDocuments()) {
                return true;
            }
        }
        return false;
    }

    public Map<SymbolicName, Component> getAbstractComponents() {
        return abstractComponents;
    }

    /**
     * Mark the package as non-exportable, supplying an error message to be reported if export is attempted
     * @param message the error message to report indicating why export is not possible
     * @param errorCode the error code to report if export is attempted
     */

    public void markNonExportable(String message, String errorCode) {}

    /**
     * Output the abstract expression tree to the supplied destination.
     *
     * @param presenter the expression presenter used to display the structure
     */

    public void export(ExpressionPresenter presenter) throws XPathException {
        throw new XPathException("Exporting a stylesheet requires Saxon-EE");
    }

    public void checkForAbstractComponents() throws XPathException {
        for (Map.Entry<SymbolicName, Component> entry : componentIndex.entrySet()) {
            if (entry.getValue().getVisibility() == Visibility.ABSTRACT && entry.getValue().getContainingPackage() == this) {
                abstractComponents.put(entry.getKey(), entry.getValue());
            }
        }
        if (!abstractComponents.isEmpty()) {
            FastStringBuffer buff = new FastStringBuffer(256);
            int count = 0;
            for (SymbolicName name : abstractComponents.keySet()) {
                if (count++ > 0) {
                    buff.append(", ");
                }
                buff.append(name.toString());
                if (buff.length() > 300) {
                    buff.append(" ...");
                    break;
                }
            }
            throw new XPathException(
                    "The package is not executable, because it contains abstract components: " +
                            buff, "XTSE3080");
        }
    }

    /**
     * Ask whether a non-streamable construct has been found, forcing the entire stylesheet
     * to fall back to unstreamed processing
     *
     * @return true if the stylesheet must fall back to unstreamed processing
     */

    public boolean isFallbackToNonStreaming() {
        return true;
    }

    public void setFallbackToNonStreaming() {
        // No action except in EE
    }


}

