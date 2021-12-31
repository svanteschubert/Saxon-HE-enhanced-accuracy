////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.expr.ComponentBinding;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.XPathContextMajor;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.*;
import net.sf.saxon.style.StylesheetModule;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.rules.*;
import net.sf.saxon.tree.util.Navigator;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;
import net.sf.saxon.type.UType;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntHashMap;
import net.sf.saxon.z.IntIterator;

import java.util.*;

/**
 * A Mode is a collection of rules; the selection of a rule to apply to a given element
 * is determined by a Pattern. A SimpleMode is a mode contained within a single package,
 * as opposed to a CompoundMode which can combine rules from several packages
 */

public class SimpleMode extends Mode {
    // TODO:PERF the data structure does not cater well for a stylesheet making heavy use of
    // match="schema-element(X)". We should probably expand the substitution group.

    protected final RuleChain genericRuleChain = new RuleChain();
    protected RuleChain atomicValueRuleChain = new RuleChain();
    protected RuleChain functionItemRuleChain = new RuleChain();
    protected RuleChain documentRuleChain = new RuleChain();
    protected RuleChain textRuleChain = new RuleChain();
    protected RuleChain commentRuleChain = new RuleChain();
    protected RuleChain processingInstructionRuleChain = new RuleChain();
    protected RuleChain namespaceRuleChain = new RuleChain();
    protected RuleChain unnamedElementRuleChain = new RuleChain();
    protected RuleChain unnamedAttributeRuleChain = new RuleChain();
    protected IntHashMap<RuleChain> namedElementRuleChains = new IntHashMap<>(32);
    protected IntHashMap<RuleChain> namedAttributeRuleChains = new IntHashMap<>(8);
    protected Map<StructuredQName, RuleChain> qNamedElementRuleChains;
    protected Map<StructuredQName, RuleChain> qNamedAttributeRuleChains;

    private BuiltInRuleSet builtInRuleSet = TextOnlyCopyRuleSet.getInstance();
    private Rule mostRecentRule;
    private int mostRecentModuleHash;
    private int stackFrameSlotsNeeded = 0;
    private int highestRank;


    private Map<String, Integer> explicitPropertyPrecedences = new HashMap<>();
    private Map<String, String> explicitPropertyValues = new HashMap<>();



    /**
     * Default constructor - creates a Mode containing no rules
     *
     * @param modeName the name of the mode
     */

    public SimpleMode(StructuredQName modeName) {
        super(modeName);
    }

    /**
     * Set the built-in template rules to be used with this Mode in the case where there is no
     * explicit template rule
     *
     * @param defaultRules the built-in rule set
     */

    public void setBuiltInRuleSet(BuiltInRuleSet defaultRules) {
        this.builtInRuleSet = defaultRules;
        hasRules = true;    // if mode is explicitly declared, treat it as containing rules
    }

    /**
     * Get the built-in template rules to be used with this Mode in the case where there is no
     * explicit template rule
     *
     * @return the built-in rule set, defaulting to the TextOnlyCopyRuleSet if no other rule set has
     * been supplied
     */

    @Override
    public BuiltInRuleSet getBuiltInRuleSet() {
        return this.builtInRuleSet;
    }

    /**
     * Get the active component of this mode. For a simple mode this is the mode itself;
     * for a compound mode it is the "overriding" part
     */
    @Override
    public SimpleMode getActivePart() {
        return this;
    }

    /**
     * Check that the mode does not contain conflicting property values
     *
     * @throws XPathException if there are conflicts
     */

    public void checkForConflictingProperties() throws XPathException {
        for (Map.Entry<String, String> entry : getActivePart().explicitPropertyValues.entrySet()) {
            String prop = entry.getKey();
            String value = entry.getValue();
            if (value.equals("##conflict##")) {
                throw new XPathException(
                        "For " + getLabel() +
                                ", there are conflicting values for xsl:mode/@" +
                                prop +
                                " at the same import precedence", "XTSE0545");
            }
            if (prop.equals("typed")) {
                mustBeTyped = "yes".equals(value) || "strict".equals(value) || "lax".equals(value);
                mustBeUntyped = "no".equals(value);
            } else if (prop.equals("on-no-match")) {
                BuiltInRuleSet base = null;
                switch (value) {
                    case "text-only-copy":
                        base = TextOnlyCopyRuleSet.getInstance();
                        break;
                    case "shallow-copy":
                        base = ShallowCopyRuleSet.getInstance();
                        break;
                    case "deep-copy":
                        base = DeepCopyRuleSet.getInstance();
                        break;
                    case "shallow-skip":
                        base = ShallowSkipRuleSet.getInstance();
                        break;
                    case "deep-skip":
                        base = DeepSkipRuleSet.getInstance();
                        break;
                    case "fail":
                        base = FailRuleSet.getInstance();
                        break;
                    default:
                        // already validated
                        break;
                }
                if ("yes".equals(explicitPropertyValues.get("warning-on-no-match"))) {
                    base = new RuleSetWithWarnings(base);
                }
                setBuiltInRuleSet(base);
            }
        }
    }

    /**
     * Get an identifier for the mode for use in error messages
     * @return either the string "the unnamed mode" or the string "mode NNNN" where "NNNN"
     * is the display form of the mode's name.
     */
    public String getLabel() {
        return isUnnamedMode() ?
                "the unnamed mode" :
                "mode " + modeName.getDisplayName();
    }

    /**
     * Construct a new Mode, copying the contents of an existing Mode
     *
     * @param from the existing mode.
     * @param to   the name of the new mode to be created
     */

    public static void copyRules(final SimpleMode from, final SimpleMode to) {
        try {
            from.processRules(r -> {
                Rule r2 = r.copy(false);
                to.addRule(r2.getPattern(), r2);
            });
        } catch (XPathException e) {
            throw new AssertionError(e);
        }

        to.mostRecentRule = from.mostRecentRule;
        to.mostRecentModuleHash = from.mostRecentModuleHash;
    }

    /**
     * Generate a search state for processing a given node
     *
     * @return a new object capable of holding the state of a search for a rule
     */
    protected RuleSearchState makeRuleSearchState(RuleChain chain, XPathContext context) {
        return new RuleSearchState();
    }

    /**
     * Ask whether there are any template rules in this mode
     * (a mode could exist merely because it is referenced in apply-templates)
     *
     * @return true if no template rules exist in this mode
     */

    @Override
    public boolean isEmpty() {
        return !hasRules;
    }

    /**
     * Set an explicit property at a particular precedence. Used for detecting conflicts
     *
     * @param name       the name of the property
     * @param value      the value of the property
     * @param precedence the import precedence of this property value
     */

    public void setExplicitProperty(String name, String value, int precedence) {
        Integer p = explicitPropertyPrecedences.get(name);
        if (p != null) {
            if (p < precedence) {
                explicitPropertyPrecedences.put(name, precedence);
                explicitPropertyValues.put(name, value);
            } else if (p == precedence) {
                String v = explicitPropertyValues.get(name);
                if (v != null & !v.equals(value)) {
                    // We don't throw an exception, because the conflict is an error only if this
                    // is the highest-precedence declaration of this mode
                    explicitPropertyValues.put(name, "##conflict##");
                }
            } else {
                // no action
            }
        } else {
            explicitPropertyPrecedences.put(name, precedence);
            explicitPropertyValues.put(name, value);
        }

        final String typed = explicitPropertyValues.get("typed");
        mustBeTyped = "yes".equals(typed) || "strict".equals(typed) || "lax".equals(typed);
        mustBeUntyped = "no".equals(typed);

    }

    /**
     * Get the value of a property of this mode, e.g. the "typed" property
     *
     * @param name the property name, e.g. "typed"
     * @return the property value
     */

    public String getPropertyValue(String name) {
        return explicitPropertyValues.get(name);
    }


    /**
     * Get the "explicit namespaces" matched by this mode. Returns a set containing all the namespaces
     * matched by specific template rules in this mode
     *
     * @param pool the NamePool for the configuration
     * @return the set of all namespace URIs of names explicitly matched by rules in this mode
     */

    @Override
    public Set<String> getExplicitNamespaces(NamePool pool) {
        Set<String> namespaces = new HashSet<>();
        IntIterator ii = namedElementRuleChains.keyIterator();
        while (ii.hasNext()) {
            int fp = ii.next();
            namespaces.add(pool.getURI(fp));
        }
        return namespaces;
    }

    /**
     * Add a rule to the Mode.
     *
     * @param pattern    a Pattern
     * @param action     the Object to return from getRule() when the supplied node matches this Pattern
     * @param module     the stylesheet module containing the rule
     * @param precedence the import precedence of the rule
     * @param priority   the priority of the rule
     * @param position   the relative position of the rule in declaration order. If two rules have the same
     *                   position in declaration order, this indicates that they were formed by splitting
     *                   a single rule whose pattern is a union pattern
     * @param part       the relative position of a rule within a family of rules created by splitting a
     *                   single rule governed by a union pattern. This is used where the splitting of the
     *                   rule was mandated by the XSLT specification, that is, where there is no explicit
     *                   priority specified. In cases where Saxon splits a rule for optimization reasons,
     *                   the subrules will all have the same subsequence number.
     */

    public void addRule(Pattern pattern, RuleTarget action,
                        StylesheetModule module, int precedence, double priority, int position, int part) {

        hasRules = true;

        // Ignore a pattern that will never match, e.g. "@comment"

        if (pattern.getItemType() instanceof ErrorType) {
            return;
        }

        // for fast lookup, we maintain one list for each element name for patterns that can only
        // match elements of a given name, one list for each node type for patterns that can only
        // match one kind of non-element node, and one generic list.
        // Each list is sorted in precedence/priority order so we find the highest-priority rule first

        // This logic is designed to ensure that when a UnionPattern contains multiple branches
        // with the same priority, next-match doesn't select the same template twice (next-match-024)
        int moduleHash = module.hashCode();
//        int sequence;
//        if (mostRecentRule == null) {
//            sequence = 0;
//        } else if (action == mostRecentRule.getAction() && moduleHash == mostRecentModuleHash) {
//            sequence = mostRecentRule.getSequence();
//        } else {
//            sequence = mostRecentRule.getSequence() + 1;
//        }
        //int precedence = module.getPrecedence();
        int minImportPrecedence = module.getMinImportPrecedence();

        Rule newRule = makeRule(pattern, action, precedence, minImportPrecedence, priority, position, part);

        if (pattern instanceof NodeTestPattern) {
            ItemType test = pattern.getItemType();
            if (test instanceof AnyNodeTest) {
                newRule.setAlwaysMatches(true);
            } else if (test instanceof NodeKindTest) {
                newRule.setAlwaysMatches(true);
            } else if (test instanceof NameTest) {
                int kind = test.getPrimitiveType();
                if (kind == Type.ELEMENT || kind == Type.ATTRIBUTE) {
                    newRule.setAlwaysMatches(true);
                }
            }
        }
        mostRecentRule = newRule;
        mostRecentModuleHash = moduleHash;

        addRule(pattern, newRule);

    }

    /**
     * Generate a new rule - so it can be overridden to make more specialist rules
     *
     * @param pattern             the pattern that this rule matches
     * @param action              the object invoked by this rule (usually a Template)
     * @param precedence          the precedence of the rule
     * @param minImportPrecedence the minimum import precedence for xsl:apply-imports
     * @param priority            the priority of the rule
     * @param sequence            a sequence number for ordering of rules
     * @param part                distinguishes rules formed by splitting a rule on a union pattern
     * @return the newly created rule
     */
    public Rule makeRule(/*@NotNull*/ Pattern pattern, /*@NotNull*/ RuleTarget action,
                                      int precedence, int minImportPrecedence, double priority, int sequence, int part) {
        return new Rule(pattern, action, precedence, minImportPrecedence, priority, sequence, part);
    }

    public void addRule(Pattern pattern, Rule newRule) {
        UType uType = pattern.getUType();
        if (uType.equals(UType.ELEMENT)) {
            int fp = pattern.getFingerprint();
            addRuleToNamedOrUnnamedChain(
                    newRule, fp, unnamedElementRuleChain, namedElementRuleChains);
        } else if (uType.equals(UType.ATTRIBUTE)) {
            int fp = pattern.getFingerprint();
            addRuleToNamedOrUnnamedChain(
                    newRule, fp, unnamedAttributeRuleChain, namedAttributeRuleChains);
        } else if (uType.equals(UType.DOCUMENT)) {
            addRuleToList(newRule, documentRuleChain);
        } else if (uType.equals(UType.TEXT)) {
            addRuleToList(newRule, textRuleChain);
        } else if (uType.equals(UType.COMMENT)) {
            addRuleToList(newRule, commentRuleChain);
        } else if (uType.equals(UType.PI)) {
            addRuleToList(newRule, processingInstructionRuleChain);
        } else if (uType.equals(UType.NAMESPACE)) {
            addRuleToList(newRule, namespaceRuleChain);
        } else if (UType.ANY_ATOMIC.subsumes(uType)) {
            addRuleToList(newRule, atomicValueRuleChain);
        } else if (UType.FUNCTION.subsumes(uType)) {
            addRuleToList(newRule, functionItemRuleChain);
        } else {
            addRuleToList(newRule, genericRuleChain);
        }
    }

    protected void addRuleToNamedOrUnnamedChain(
            Rule newRule, int fp, RuleChain unnamedRuleChain, IntHashMap<RuleChain> namedRuleChains) {
        if (fp == -1) {
            addRuleToList(newRule, unnamedRuleChain);
        } else {
            RuleChain chain = namedRuleChains.get(fp);
            if (chain == null) {
                chain = new RuleChain(newRule);
                namedRuleChains.put(fp, chain);
            } else {
                addRuleToList(newRule, chain);
            }
        }
    }

    /**
     * Insert a new rule into this list before others of the same precedence/priority
     *
     * @param newRule the new rule to be added into the list
     * @param list    the Rule at the head of the list, or null if the list is empty
     */


    private void addRuleToList(Rule newRule, RuleChain list) {
        if (list.head() == null) {
            list.setHead(newRule);
        } else {
            int precedence = newRule.getPrecedence();
            double priority = newRule.getPriority();
            Rule rule = list.head();
            Rule prev = null;
            while (rule != null) {
                if ((rule.getPrecedence() < precedence) ||
                        (rule.getPrecedence() == precedence && rule.getPriority() <= priority)) {
                    newRule.setNext(rule);
                    if (prev == null) {
                        list.setHead(newRule);
                    } else {
                        prev.setNext(newRule);
                    }
                    break;
                } else {
                    prev = rule;
                    rule = rule.getNext();
                }
            }
            if (rule == null) {
                assert prev != null;
                prev.setNext(newRule);
                newRule.setNext(null);
            }
        }
    }

    /**
     * Specify how many slots for local variables are required by a particular pattern
     *
     * @param slots the number of slots needed
     */

    public void allocatePatternSlots(int slots) {
        stackFrameSlotsNeeded = Math.max(stackFrameSlotsNeeded, slots);
    }

    /**
     * Get the rule corresponding to a given item, by finding the best pattern match.
     *
     * @param item    the item to be matched
     * @param context the XPath dynamic evaluation context
     * @return the best matching rule, if any (otherwise null).
     * @throws XPathException if an error occurs matching a pattern
     */

    @Override
    public Rule getRule(Item item, XPathContext context) throws XPathException {

        // If there are match patterns in the stylesheet that use local variables, we need to allocate
        // a new stack frame for evaluating the match patterns. We base this on the match pattern with
        // the highest number of range variables, so we can reuse the same stack frame for all rules
        // that we test against. If no patterns use range variables, we don't bother allocating a new
        // stack frame.

        // Note, this method isn't functionally necessary; we could call the 3-argument version
        // with a filter that always returns true. But this is the common path for apply-templates,
        // and we want to squeeze every drop of performance from it.

        if (stackFrameSlotsNeeded > 0) {
            context = makeNewContext(context);
        }

        // search the specific list for this node type / node name

        RuleChain unnamedNodeChain;
        Rule bestRule = null;

        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    unnamedNodeChain = documentRuleChain;
                    break;

                case Type.ELEMENT: {
                    unnamedNodeChain = unnamedElementRuleChain;
                    RuleChain namedNodeChain;
                    if (node.hasFingerprint()) {
                        namedNodeChain = namedElementRuleChains.get(node.getFingerprint());
                    } else {
                        namedNodeChain = getNamedRuleChain(context, Type.ELEMENT, node.getURI(), node.getLocalPart());
                    }
                    if (namedNodeChain != null) {
                        bestRule = searchRuleChain(node, context, null, namedNodeChain);
                    }
                    break;
                }
                case Type.ATTRIBUTE: {
                    unnamedNodeChain = unnamedAttributeRuleChain;
                    RuleChain namedNodeChain;
                    if (node.hasFingerprint()) {
                        namedNodeChain = namedAttributeRuleChains.get(node.getFingerprint());
                    } else {
                        namedNodeChain = getNamedRuleChain(context, Type.ATTRIBUTE, node.getURI(), node.getLocalPart());
                    }
                    if (namedNodeChain != null) {
                        bestRule = searchRuleChain(node, context, null, namedNodeChain);
                    }
                    break;
                }
                case Type.TEXT:
                    unnamedNodeChain = textRuleChain;
                    break;
                case Type.COMMENT:
                    unnamedNodeChain = commentRuleChain;
                    break;
                case Type.PROCESSING_INSTRUCTION:
                    unnamedNodeChain = processingInstructionRuleChain;
                    break;
                case Type.NAMESPACE:
                    unnamedNodeChain = namespaceRuleChain;
                    break;
                default:
                    throw new AssertionError("Unknown node kind");
            }

            // search the list for unnamed nodes of a particular kind

            if (unnamedNodeChain != null) {
                bestRule = searchRuleChain(node, context, bestRule, unnamedNodeChain);
            }

            // Search the list for rules for nodes of unknown node kind

            bestRule = searchRuleChain(node, context, bestRule, genericRuleChain);

        } else if (item instanceof AtomicValue) {
            if (atomicValueRuleChain != null) {
                bestRule = searchRuleChain(item, context, bestRule, atomicValueRuleChain);
            }
            bestRule = searchRuleChain(item, context, bestRule, genericRuleChain);

        } else if (item instanceof Function) {
            if (functionItemRuleChain != null) {
                bestRule = searchRuleChain(item, context, bestRule, functionItemRuleChain);
            }
            bestRule = searchRuleChain(item, context, bestRule, genericRuleChain);
        }

        return bestRule;
    }


    /**
     * Get a rule chain for a particular node name without allocating a fingerprint from the name pool
     *
     * @param kind  the kind of node (element or attribute)
     * @param uri   the namespace URI of the node
     * @param local the local name of the node
     * @return the Rule at the head of the rule chain for nodes of this name, or null if there are no rules
     * to consider
     */

    protected RuleChain getNamedRuleChain(XPathContext c, int kind, String uri, String local) {
        // If this is the first attempt to match a non-fingerprinted node, build indexes
        // to the rule chains based on StructuredQName rather than fingerprint
        synchronized(this) {
            if (qNamedElementRuleChains == null) {
                qNamedElementRuleChains = new HashMap<>(namedElementRuleChains.size());
                qNamedAttributeRuleChains = new HashMap<>(namedAttributeRuleChains.size());
                NamePool pool = c.getNamePool();
                indexByQName(pool, namedElementRuleChains, qNamedElementRuleChains);
                indexByQName(pool, namedAttributeRuleChains, qNamedAttributeRuleChains);
            }
        }
        return (kind == Type.ELEMENT ? qNamedElementRuleChains : qNamedAttributeRuleChains)
                .get(new StructuredQName("", uri, local));
    }

    private static void indexByQName(NamePool pool, IntHashMap<RuleChain> indexByFP, Map<StructuredQName, RuleChain> indexByQN) {
        IntIterator ii = indexByFP.keyIterator();
        while (ii.hasNext()) {
            int fp = ii.next();
            RuleChain eChain = indexByFP.get(fp);
            StructuredQName name = pool.getStructuredQName(fp);
            indexByQN.put(name, eChain);
        }
    }

    /**
     * Search a chain of rules
     *
     * @param item            the item being matched
     * @param context         XPath dynamic context
     * @param bestRule        the best rule so far in terms of precedence and priority (may be null)
     * @param chain           the chain to be searched
     * @return the best match rule found in the chain, or the previous best rule, or null
     * @throws XPathException if an error occurs matching a pattern
     */

    protected Rule searchRuleChain(Item item, XPathContext context, /*@Nullable*/ Rule bestRule, RuleChain chain) throws XPathException {

        while (!(context instanceof XPathContextMajor)) {
            context = context.getCaller();
        }

        // Get the rule search state object - this could be reusable within a rule chain.
        RuleSearchState ruleSearchState = makeRuleSearchState(chain, context);

        Rule head = chain == null ? null : chain.head();
        while (head != null) {
            if (bestRule != null) {
                int rank = head.compareRank(bestRule);
                if (rank < 0) {
                    // if we already have a match, and the precedence or priority of this
                    // rule is lower, quit the search
                    break;
                } else if (rank == 0) {
                    // this rule has the same precedence and priority as the matching rule already found
                    if (ruleMatches(head, item, (XPathContextMajor) context, ruleSearchState)) {
                        if (head.getSequence() != bestRule.getSequence()) {
                            reportAmbiguity(item, bestRule, head, context);
                        }
                        // choose whichever one comes last (assuming the error wasn't fatal)
                        int seqComp = Integer.compare(bestRule.getSequence(), head.getSequence());
                        if (seqComp > 0) {
                            return bestRule;
                        } else if (seqComp < 0) {
                            return head;
                        } else {
                            // we're dealing with two rules formed by partitioning a union pattern
                            bestRule = bestRule.getPartNumber() > head.getPartNumber() ? bestRule : head;
                        }
                        break;
                    } else {
                        // keep searching other rules of the same precedence and priority
                    }
                } else {
                    // this rule has higher rank than the matching rule already found
                    if (ruleMatches(head, item, (XPathContextMajor) context, ruleSearchState)) {
                        bestRule = head;
                    }
                }
            } else if (ruleMatches(head, item, (XPathContextMajor) context, ruleSearchState)) {
                bestRule = head;
                if (getRecoveryPolicy() == RecoveryPolicy.RECOVER_SILENTLY) {
                    //ruleSearchState.count();
                    break;   // choose the first match; rules within a chain are in order of rank
                }
            }
            //ruleSearchState.count();// Keep tab of the number of checks
            head = head.getNext();
        }

        return bestRule;
    }

    /**
     * Does this rule match the given item? Can be overridden
     *
     * @param r       the rule to check
     * @param item    the context item
     * @param context the static context for evaluation
     * @param pre     An appropriate matcher for preconditions in this mode
     * @return true if this rule does match
     * @throws XPathException if a dynamic error occurs while matching the pattern
     */

    protected boolean ruleMatches(Rule r, Item item, XPathContextMajor context, RuleSearchState pre) throws XPathException {
        return r.isAlwaysMatches() || r.matches(item, context);
    }

    /**
     * Get the rule corresponding to a given item, by finding the best Pattern match.
     *
     * @param item    the item to be matched
     * @param context the XPath dynamic evaluation context
     * @param filter  a filter to select which rules should be considered
     * @return the best matching rule, if any (otherwise null).
     * @throws XPathException if an error occurs
     */


    /*@Nullable*/
    @Override
    public Rule getRule(Item item, XPathContext context, RuleFilter filter) throws XPathException {

        // If there are match patterns in the stylesheet that use local variables, we need to allocate
        // a new stack frame for evaluating the match patterns. We base this on the match pattern with
        // the highest number of range variables, so we can reuse the same stack frame for all rules
        // that we test against. If no patterns use range variables, we don't bother allocating a new
        // stack frame.

        if (stackFrameSlotsNeeded > 0) {
            context = makeNewContext(context);
        }

        // Get the rule search state object
        RuleSearchState ruleSearchState;

        // search the specific list for this node type / node name

        Rule bestRule = null;
        RuleChain unnamedNodeChain;


        // Search the list for unnamed nodes of a particular kind

        if (item instanceof NodeInfo) {
            NodeInfo node = (NodeInfo) item;
            switch (node.getNodeKind()) {
                case Type.DOCUMENT:
                    unnamedNodeChain = documentRuleChain;
                    break;
                case Type.ELEMENT: {
                    unnamedNodeChain = unnamedElementRuleChain;
                    RuleChain namedNodeChain;
                    if (node.hasFingerprint()) {
                        namedNodeChain = namedElementRuleChains.get(node.getFingerprint());
                    } else {
                        namedNodeChain = getNamedRuleChain(context, Type.ELEMENT, node.getURI(), node.getLocalPart());
                    }
                    if (namedNodeChain != null) {
                        ruleSearchState = makeRuleSearchState(namedNodeChain, context);
                        bestRule = searchRuleChain(item, context, null, namedNodeChain, ruleSearchState, filter);
                    }
                    break;
                }
                case Type.ATTRIBUTE: {
                    unnamedNodeChain = unnamedAttributeRuleChain;
                    RuleChain namedNodeChain;
                    if (node.hasFingerprint()) {
                        namedNodeChain = namedAttributeRuleChains.get(node.getFingerprint());
                    } else {
                        namedNodeChain = getNamedRuleChain(context, Type.ATTRIBUTE, node.getURI(), node.getLocalPart());
                    }
                    if (namedNodeChain != null) {
                        ruleSearchState = makeRuleSearchState(namedNodeChain, context);
                        bestRule = searchRuleChain(item, context, null, namedNodeChain, ruleSearchState, filter);
                    }
                    break;
                }
                case Type.TEXT:
                    unnamedNodeChain = textRuleChain;
                    break;
                case Type.COMMENT:
                    unnamedNodeChain = commentRuleChain;
                    break;
                case Type.PROCESSING_INSTRUCTION:
                    unnamedNodeChain = processingInstructionRuleChain;
                    break;
                case Type.NAMESPACE:
                    unnamedNodeChain = namespaceRuleChain;
                    break;
                default:
                    throw new AssertionError("Unknown node kind");
            }

            ruleSearchState = makeRuleSearchState(unnamedNodeChain, context);
            bestRule = searchRuleChain(item, context, bestRule, unnamedNodeChain, ruleSearchState, filter);

            // Search the list for rules for nodes of unknown node kind

            ruleSearchState = makeRuleSearchState(genericRuleChain, context);
            return searchRuleChain(item, context, bestRule, genericRuleChain, ruleSearchState, filter);

        } else if (item instanceof AtomicValue) {
            if (atomicValueRuleChain != null) {
                ruleSearchState = makeRuleSearchState(atomicValueRuleChain, context);
                bestRule = searchRuleChain(item, context, bestRule, atomicValueRuleChain, ruleSearchState, filter);
            }
            ruleSearchState = makeRuleSearchState(genericRuleChain, context);
            bestRule = searchRuleChain(item, context, bestRule, genericRuleChain, ruleSearchState, filter);
            return bestRule;

        } else if (item instanceof Function) {
            if (functionItemRuleChain != null) {
                ruleSearchState = makeRuleSearchState(functionItemRuleChain, context);
                bestRule = searchRuleChain(item, context, bestRule, functionItemRuleChain, ruleSearchState, filter);
            }
            ruleSearchState = makeRuleSearchState(genericRuleChain, context);
            bestRule = searchRuleChain(item, context, bestRule, genericRuleChain, ruleSearchState, filter);
            return bestRule;

        } else {
            return null;
        }

    }

    /**
     * Search a chain of rules
     *
     * @param item            the item being matched
     * @param context         XPath dynamic context
     * @param bestRule        the best rule so far in terms of precedence and priority (may be null)
     * @param chain           the chain to be searched
     * @param ruleSearchState An appropriate ruleState in this mode
     * @param filter          filter used to select which rules are candidates to be searched
     * @return the best match rule found in the chain, or the previous best rule, or null
     * @throws XPathException if an error occurs while matching a pattern
     */

    protected Rule searchRuleChain(Item item, XPathContext context,
                                   Rule/*@Nullable*/ bestRule, RuleChain chain, RuleSearchState ruleSearchState, RuleFilter filter) throws XPathException {
        Rule head = chain == null ? null : chain.head();
        while (!(context instanceof XPathContextMajor)) {
            context = context.getCaller();
        }
        while (head != null) {
            if (filter == null || filter.testRule(head)) {
                if (bestRule != null) {
                    int rank = head.compareRank(bestRule);
                    if (rank < 0) {
                        // if we already have a match, and the precedence or priority of this
                        // rule is lower, quit the search
                        break;
                    } else if (rank == 0) {
                        // this rule has the same precedence and priority as the matching rule already found
                        if (ruleMatches(head, item, (XPathContextMajor) context, ruleSearchState)) {
                            reportAmbiguity(item, bestRule, head, context);
                            // choose whichever one comes last (assuming the error wasn't fatal)
                            bestRule = bestRule.getSequence() > head.getSequence() ? bestRule : head;
                            break;
                        } else {
                            // keep searching other rules of the same precedence and priority
                        }
                    } else {
                        // this rule has higher rank than the matching rule already found
                        if (ruleMatches(head, item, (XPathContextMajor) context, ruleSearchState)) {
                            bestRule = head;
                        }
                    }
                } else if (ruleMatches(head, item, (XPathContextMajor) context, ruleSearchState)) {
                    bestRule = head;
                    if (getRecoveryPolicy() == RecoveryPolicy.RECOVER_SILENTLY) {
                        break;   // choose the first match; rules within a chain are in order of rank
                    }
                }
            }
            head = head.getNext();
        }
        return bestRule;
    }


    /**
     * Report an ambiguity, that is, the situation where two rules of the same
     * precedence and priority match the same node
     *
     * @param item The item that matches two or more rules
     * @param r1   The first rule that the node matches
     * @param r2   The second rule that the node matches
     * @param c    The context for the transformation
     * @throws XPathException if the system is configured to treat ambiguous template matching as a
     *                        non-recoverable error
     */

    protected void reportAmbiguity(Item item, Rule r1, Rule r2, XPathContext c)
            throws XPathException {

        // Save the effort of constructing the message if it's not going to be reported anyway
        if (getRecoveryPolicy() == RecoveryPolicy.RECOVER_SILENTLY) {
            return;
        }
        // don't report an error if the conflict is between two branches of the same Union pattern
        if (r1.getAction() == r2.getAction() && r1.getSequence() == r2.getSequence()) {
            return;
        }
        String path;
        String errorCode = "XTDE0540";

        if (item instanceof NodeInfo) {
            path = Navigator.getPath((NodeInfo) item);
        } else {
            path = item.toShortString();
        }

        Pattern pat1 = r1.getPattern();
        Pattern pat2 = r2.getPattern();

        String message;
        if (r1.getAction() == r2.getAction()) {
            message = "Ambiguous rule match for " + path + ". " +
                    "Matches \"" + showPattern(pat1) + "\" on line " + pat1.getLocation().getLineNumber() +
                    " of " + pat1.getLocation().getSystemId() +
                    ", a rule which appears in the stylesheet more than once, because the containing module was included more than once";
        } else {
            message = "Ambiguous rule match for " + path + '\n' +
                    "Matches both \"" + showPattern(pat1) + "\" on line " + pat1.getLocation().getLineNumber() +
                    " of " + pat1.getLocation().getSystemId() +
                    "\nand \"" + showPattern(pat2) + "\" on line " + pat2.getLocation().getLineNumber() +
                    " of " + pat2.getLocation().getSystemId();
        }

        switch (getRecoveryPolicy()) {
            case DO_NOT_RECOVER:
                throw new XPathException(message, errorCode, getLocation());
            case RECOVER_WITH_WARNINGS:
                c.getController().warning(message, errorCode, getLocation());
                break;
            case RECOVER_SILENTLY:
            default:
                // no action
        }
    }

    private static String showPattern(Pattern p) {
        // Complex patterns can be laid out with lots of whitespace, which looks messy in the error message
        return Whitespace.collapseWhitespace(p.toShortString()).toString();
    }

    /**
     * Prepare for possible streamability - null here, but can be subclassed
     *
     * @throws XPathException if a failure occurs
     */
    public void prepareStreamability() throws XPathException {
    }


    /**
     * Allocate slot numbers to all the external component references in this component
     *
     * @param pack the containing package
     */

    @Override
    public void allocateAllBindingSlots(final StylesheetPackage pack) {
        if (getDeclaringComponent().getDeclaringPackage() == pack && !bindingSlotsAllocated) {
            forceAllocateAllBindingSlots(pack, this, getDeclaringComponent().getComponentBindings());
            bindingSlotsAllocated = true;
        }
    }

    public static void forceAllocateAllBindingSlots(
            final StylesheetPackage pack, final SimpleMode mode, final List<ComponentBinding> bindings) {
        final Set<TemplateRule> rulesProcessed = new HashSet<>();
        final IdentityHashMap<Pattern, Boolean> patternsProcessed = new IdentityHashMap<>();
        try {
            mode.processRules(r -> {
                // A rule can appear twice, for example at different import precedences or
                // because the match pattern is a union pattern; only allocate slots once
                Pattern pattern = r.getPattern();
                if (!patternsProcessed.containsKey(pattern)) {
                    allocateBindingSlotsRecursive(pack, mode, pattern, bindings);
                    patternsProcessed.put(pattern, true);
                }
                TemplateRule tr = (TemplateRule) r.getAction();
                if (tr.getBody() != null && !rulesProcessed.contains(tr)) {
                    allocateBindingSlotsRecursive(pack, mode, tr.getBody(), bindings);
                    rulesProcessed.add(tr);
                }
            });
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }
    
    /**
     * Compute the streamability of all template rules. No action in Saxon-HE.
     */

    public void computeStreamability() throws XPathException {
        // Implemented in Saxon-EE
    }

    /**
     * For a streamable mode, invert all the templates to generate streamable code.
     * No action in Saxon-HE.
     *
     * @throws XPathException if there is a non-streamable template in the mode
     */

    public void invertStreamableTemplates() throws XPathException {
        // Implemented in Saxon-EE
    }

    /**
     * Explain all template rules in this mode by showing their
     * expression tree represented in XML. Note that this produces more information
     * than the simpler exportTemplateRules() method: this method is intended for
     * the human reader wanting diagnostic explanations, whereas exportTemplateRules()
     * is designed to produce a package that can be re-imported.
     *
     * @param out used to display the expression tree
     */

    @Override
    public void explainTemplateRules(final ExpressionPresenter out) throws XPathException {
        RuleAction action = r -> r.export(out, isDeclaredStreamable());
        RuleGroupAction group = new RuleGroupAction() {
            String type;

            @Override
            public void start() {
                out.startElement("ruleSet");
                out.emitAttribute("type", type);
            }

            @Override
            public void setString(String type) {
                this.type = type;
            }

            @Override
            public void start(int i) {
                out.startElement("ruleChain");
                out.emitAttribute("key", out.getNamePool().getClarkName(i));
            }

            @Override
            public void end() {
                out.endElement();
            }
        };
        try {
            processRules(action, group);
        } catch (XPathException err) {
            // can't happen, and doesn't matter if it does
        }

    }

    /**
     * Export all template rules in this mode in a form that can be re-imported.
     * Note that template rules with union patterns may have been split into multiple
     * rules. We need to avoid outputting them more than once.
     *
     * @param out used to display the expression tree
     */

    @Override
    public void exportTemplateRules(final ExpressionPresenter out) throws XPathException {
        //final Set<RuleTarget> processedRules = new HashSet<RuleTarget>();
        // TODO: if two rules share the same template, avoid duplicate output. This can happen with union patterns, and also
        // when a template is present in more than one mode.
        RuleAction action = r -> {
            // if (processedRules.add(r.getAction())) {
            r.export(out, isDeclaredStreamable());
            // }
        };

        processRules(action);

    }

    /**
     * Walk over all the rules, applying a specified action to each one.
     *
     * @param action an action that is to be applied to all the rules in this Mode
     * @throws XPathException if an error occurs processing any of the rules
     */
    @Override
    public void processRules(RuleAction action) throws XPathException {
        processRules(action, null);
    }

    /**
     * Walk over all the rules, applying a specified action to each one.
     *
     * @param action an action that is to be applied to all the rules in this Mode
     * @param group  - actions to be performed at group start and group end
     * @throws XPathException if an error occurs processing any of the rules
     */
    public void processRules(RuleAction action, RuleGroupAction group) throws XPathException {
        processRuleChain(documentRuleChain, action, setGroup(group, "document-node()"));
        processRuleChain(unnamedElementRuleChain, action, setGroup(group, "element()"));
        processRuleChains(namedElementRuleChains, action, setGroup(group, "namedElements"));
        processRuleChain(unnamedAttributeRuleChain, action, setGroup(group, "attribute()"));
        processRuleChains(namedAttributeRuleChains, action, setGroup(group, "namedAttributes"));
        processRuleChain(textRuleChain, action, setGroup(group, "text()"));
        processRuleChain(commentRuleChain, action, setGroup(group, "comment()"));
        processRuleChain(processingInstructionRuleChain, action, setGroup(group, "processing-instruction()"));
        processRuleChain(namespaceRuleChain, action, setGroup(group, "namespace()"));
        processRuleChain(genericRuleChain, action, setGroup(group, "node()"));
        processRuleChain(atomicValueRuleChain, action, setGroup(group, "atomicValue"));
        processRuleChain(functionItemRuleChain, action, setGroup(group, "function()"));
    }

    /**
     * Set the string associated with a rule group
     *
     * @param group the group action object
     * @param type  the type of the rule group
     * @return modified rulegroup action
     */
    protected RuleGroupAction setGroup(RuleGroupAction group, String type) {
        if (group != null) {
            group.setString(type);
        }
        return group;
    }


    public void processRuleChains(IntHashMap<RuleChain> chains, RuleAction action, RuleGroupAction group) throws XPathException {
        if (chains.size() > 0) {
            if (group != null) {
                group.start();
            }
            IntIterator ii = chains.keyIterator();
            while (ii.hasNext()) {
                int i = ii.next();
                if (group != null) {
                    group.start(i);
                }
                RuleChain r = chains.get(i);
                processRuleChain(r, action, null);
                if (group != null) {
                    group.end();
                }
            }
            if (group != null) {
                group.end();
            }
        }
    }

    /**
     * Walk over all the rules, applying a specified action to each one.
     *
     * @param action an action that is to be applied to all the rules in this Mode
     * @throws XPathException if an error occurs processing any of the rules
     */
    public void processRuleChain(RuleChain chain, RuleAction action) throws XPathException {
        Rule r = chain == null ? null : chain.head();
        while (r != null) {
            action.processRule(r);
            r = r.getNext();
        }
    }


    public void processRuleChain(RuleChain chain, RuleAction action, RuleGroupAction group) throws XPathException {
        Rule r = chain == null ? null : chain.head();
        if (r != null) {
            if (group != null) {
                group.start();
            }
            while (r != null) {
                action.processRule(r);
                r = r.getNext();
            }
            if (group != null) {
                group.end();
            }
        }
    }

    /**
     * Perform optimization on the complete set of rules comprising this Mode. This is a null operation
     * in Saxon-HE.
     */

    public void optimizeRules() {
    }

    @Override
    public int getMaxPrecedence() {
        try {
            MaxPrecedenceAction action = new MaxPrecedenceAction();
            processRules(action);
            return action.max;
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
    }

    private static class MaxPrecedenceAction implements RuleAction {
        public int max = 0;

        @Override
        public void processRule(Rule r) {
            if (r.getPrecedence() > max) {
                max = r.getPrecedence();
            }
        }
    }

    /**
     * Compute a rank for each rule, as a combination of the precedence and priority, to allow
     * rapid comparison.  This method also checks that there are no conflicts for
     * property values in different xsl:mode declarations
     *
     * @param start the lowest rank to use
     * @throws XPathException if an error occurs processing the rules
     */

    @Override
    public void computeRankings(int start) throws XPathException {
        // Now sort the rules into ranking order
        final RuleSorter sorter = new RuleSorter(start);
        // add all the rules in this Mode to the sorter
        processRules(sorter::addRule);
        // now allocate ranks to all the rules in this Mode
        sorter.allocateRanks();
        highestRank = start + sorter.getNumberOfRules();
    }

    @Override
    public int getMaxRank() {
        return highestRank;
    }

    /**
     * Supporting class used at compile time to sort all the rules into precedence/priority
     * order and allocate a rank to each one, so that at run-time, comparing two rules to see
     * which has higher precedence/priority is a simple integer subtraction.
     */

    private static class RuleSorter {
        public ArrayList<Rule> rules = new ArrayList<>(100);
        private int start;

        public RuleSorter(int start) {
            this.start = start;
        }

        public void addRule(Rule rule) {
            rules.add(rule);
        }

//        public int compare(int a, int b) {
//            return rules.get(a).compareComputedRank(rules.get(b));
//        }
//
//        public void swap(int a, int b) {
//            Rule temp = rules.get(a);
//            rules.set(a, rules.get(b));
//            rules.set(b, temp);
//        }

        public void allocateRanks() {
            rules.sort(Rule::compareComputedRank);
            //GenericSorter.quickSort(0, rules.size(), this);
            int rank = start;
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0 && rules.get(i - 1).compareComputedRank(rules.get(i)) != 0) {
                    rank++;
                }
                rules.get(i).setRank(rank);
            }
        }

        public int getNumberOfRules() {
            return rules.size();
        }
    }


    /**
     * Interface used around a group of rules - principally at the
     * group start and the group end
     */
    public interface RuleGroupAction {
        /**
         * Set some string parameter for the group
         *
         * @param s a string value to be used for the group
         */
        void setString(String s);

        /**
         * Start of a generic group
         */
        void start();

        /**
         * Start of a group characterised by some integer parameter
         * @param i characteristic of this group
         */
        void start(int i);

        /**
         * Invoked at the end of a group
         */
        void end();

    }

    /**
     * Allocate slots for local variables in all patterns used by the rules in this mode.
     * Currently used only for accumulator rules
     */

    public void allocateAllPatternSlots() {
        final List<Integer> count = new ArrayList<>(1);  // used to allow inner class to have side-effects
        count.add(0);
        final SlotManager slotManager = new SlotManager(); // TODO: allocate this via the Configuration
        final RuleAction slotAllocator = r -> {
            int slots = r.getPattern().allocateSlots(slotManager, 0);
            int max = Math.max(count.get(0), slots);
            count.set(0, max);
        };
        try {
            processRules(slotAllocator);
        } catch (XPathException e) {
            throw new AssertionError(e);
        }
        stackFrameSlotsNeeded = count.get(0);
    }

    @Override
    public int getStackFrameSlotsNeeded() {
        return stackFrameSlotsNeeded;
    }

    public void setStackFrameSlotsNeeded(int slots) {
        this.stackFrameSlotsNeeded = slots;
    }


}

