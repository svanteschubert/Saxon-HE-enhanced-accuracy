////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.ReceiverOption;
import net.sf.saxon.expr.flwor.LocalVariableBinding;
import net.sf.saxon.expr.instruct.GlobalParam;
import net.sf.saxon.expr.instruct.LocalParam;
import net.sf.saxon.expr.instruct.LocalParamBlock;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.lib.StandardDiagnostics;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTest;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.Cardinality;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * Variable reference: a reference to a variable. This may be an XSLT-defined variable, a range
 * variable defined within the XPath expression, or a variable defined in some other static context.
 */

public abstract class VariableReference extends Expression implements BindingReference {

    /*@Nullable*/ protected Binding binding = null;     // This will be null until fixup() is called; it will also be null
    // if the variable reference has been inlined
    protected SequenceType staticType = null;
    protected GroundedValue constantValue = null;
    private StructuredQName variableName = null;
    private boolean flattened = false;
    private boolean inLoop = false;
    private boolean filtered = false;

    /**
     * Create a Variable Reference
     */

    public VariableReference(StructuredQName name) {
        variableName = name;
    }

    /**
     * Create a Variable Reference
     *
     * @param binding the variable binding to which this variable refers
     */

    public VariableReference(Binding binding) {
        //System.err.println("Creating varRef1");
        variableName = binding.getVariableQName();
        fixup(binding);
    }

    /**
     * Set the variable name
     *
     * @param name the name of the variable
     */

    public void setVariableName(StructuredQName name) {
        variableName = name;
    }

    /**
     * Get the variable name
     *
     * @return the name of the variable
     */

    public StructuredQName getVariableName() {
        return variableName;
    }

    /**
     * Create a clone copy of this VariableReference
     *
     * @return the cloned copy
     * @param rebindings variables that need to switch to new bindings
     */

    /*@NotNull*/
    @Override
    public abstract Expression copy(RebindingMap rebindings);


    @Override
    public int getNetCost() {
        return 0;
    }

    protected void copyFrom(VariableReference ref) {
        binding = ref.binding;
        staticType = ref.staticType;
        constantValue = ref.constantValue;
        variableName = ref.variableName;
        flattened = ref.flattened;
        inLoop = ref.inLoop;
        filtered = ref.filtered;
        ExpressionTool.copyLocationInfo(ref, this);
    }

    /**
     * Set static type. This is a callback from the variable declaration object. As well
     * as supplying the static type, it may also supply a compile-time value for the variable.
     * As well as the type information, other static properties of the value are supplied:
     * for example, whether the value is an ordered node-set.
     *  @param type       the static type of the variable
     * @param value      the value of the variable if this is a compile-time constant, or null otherwise
     * @param properties static properties of the expression to which the variable is bound
     */

    @Override
    public void setStaticType(SequenceType type, GroundedValue value, int properties) {
        // System.err.println(this + " Set static type = " + type);
        if (type == null) {
            type = SequenceType.ANY_SEQUENCE;
        }
        staticType = type;
        constantValue = value;
        // Although the variable may be a context document node-set at the point it is defined,
        // the context at the point of use may be different, so this property cannot be transferred.
        int dependencies = getDependencies();
        staticProperties = (properties & ~StaticProperty.CONTEXT_DOCUMENT_NODESET
                        & ~StaticProperty.ALL_NODES_NEWLY_CREATED) |
                StaticProperty.NO_NODES_NEWLY_CREATED |
                type.getCardinality() |
                dependencies;
    }

    /**
     * Mark an expression as being "flattened". This is a collective term that includes extracting the
     * string value or typed value, or operations such as simple value construction that concatenate text
     * nodes before atomizing. The implication of all of these is that although the expression might
     * return nodes, the identity of the nodes has no significance. This is called during type checking
     * of the parent expression. At present, only variable references take any notice of this notification.
     */

    @Override
    public void setFlattened(boolean flattened) {
        this.flattened = flattened;
    }

    /**
     * Test whether this variable reference is flattened - that is, whether it is atomized etc
     *
     * @return true if the value of the variable is atomized, or converted to a string or number
     */

    public boolean isFlattened() {
        return flattened;
    }

    /**
     * Mark an expression as filtered: that is, it appears as the base expression in a filter expression.
     * This notification currently has no effect except when the expression is a variable reference.
     */

    @Override
    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    }

    /**
     * Determine whether this variable reference is filtered
     *
     * @return true if the value of the variable is filtered by a predicate
     */

    public boolean isFiltered() {
        return filtered;
    }

    /**
     * Ask whether this variable reference appears in a loop relative to its declaration.
     * By default, when in doubt, returns true. This is calculated during type-checking.
     *
     * @return true if this variable reference occurs in a loop, where the variable declaration is
     * outside the loop
     */

    public boolean isInLoop() {
        return inLoop;
    }

    /**
     * Say whether this variable reference appears in a loop relative to its declaration.
     *
     * @param inLoop true if this variable reference occurs in a loop, where the variable declaration is
     * outside the loop
     */

    public void setInLoop(boolean inLoop) {
        this.inLoop = inLoop;
    }

    /**
     * Type-check the expression. At this stage details of the static type must be known.
     * If the variable has a compile-time value, this is substituted for the variable reference
     */

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {
        if (constantValue != null) {
            binding = null;
            return Literal.makeLiteral(constantValue, this);
        }
//        if (staticType == null) {
//            throw new IllegalStateException("Variable $" + getDisplayName() + " has not been fixed up");
//        }



//  following code removed because it causes error181 to blow the stack - need to check for circularities well
//            if (binding instanceof GlobalVariable) {
//                ((GlobalVariable)binding).typeCheck(visitor, AnyItemType.getInstance());
//            }

        if (binding != null) {
            recomputeInLoop();
            binding.addReference(this, inLoop);
        }

        return this;
    }

    public void recomputeInLoop() {
        inLoop = ExpressionTool.isLoopingReference(this, binding);
    }

    /**
     * Optimize the expression. At this stage details of the static type must be known.
     * If the variable has a compile-time value, this is substituted for the variable reference
     */

    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextItemType) throws XPathException {
        if (binding instanceof LetExpression &&
                ((LetExpression)binding).getSequence() instanceof Literal &&
                !((LetExpression) binding).isIndexedVariable) {
            Expression val = ((LetExpression) binding).getSequence();
            Optimizer.trace(visitor.getConfiguration(), "Replaced variable " + getDisplayName() + " by its value", val);
            binding = null;
            return val.copy(new RebindingMap());
        }
        if (constantValue != null) {
            binding = null;
            Expression result = Literal.makeLiteral(constantValue, this);
            ExpressionTool.copyLocationInfo(this, result);
            Optimizer.trace(visitor.getConfiguration(), "Replaced variable " + getDisplayName() + " by its value", result);
            return result;
        }
        if (binding instanceof GlobalParam && ((GlobalParam)binding).isStatic()) {
            Expression select = ((GlobalParam) binding).getBody();
            if (select instanceof Literal) {
                binding = null;
                Optimizer.trace(visitor.getConfiguration(), "Replaced static parameter " + getDisplayName() + " by its value", select);
                return select.copy(new RebindingMap());
            }
        }
        return this;
    }


    /**
     * Fix up this variable reference to a Binding object, which enables the value of the variable
     * to be located at run-time.
     */

    @Override
    public void fixup(Binding newBinding) {
        boolean indexed = binding instanceof LocalBinding && ((LocalBinding)binding).isIndexedVariable();
        this.binding = newBinding;
        if (indexed && newBinding instanceof LocalBinding) {
            ((LocalBinding)newBinding).setIndexedVariable();
        }
        resetLocalStaticProperties();
    }

    /**
     * Provide additional information about the type of the variable, typically derived by analyzing
     * the initializer of the variable binding
     *
     * @param type          the item type of the variable
     * @param cardinality   the cardinality of the variable
     * @param constantValue the actual value of the variable, if this is known statically, otherwise null
     * @param properties    additional static properties of the variable's initializer
     */

    public void refineVariableType(ItemType type, int cardinality, GroundedValue constantValue, int properties) {
        TypeHierarchy th = getConfiguration().getTypeHierarchy();
        ItemType oldItemType = getItemType();
        ItemType newItemType = oldItemType;
        if (th.isSubType(type, oldItemType)) {
            newItemType = type;
        }
        if (oldItemType instanceof NodeTest && type instanceof AtomicType) {
            // happens when all references are flattened
            newItemType = type;
        }
        int newcard = cardinality & getCardinality();
        if (newcard == 0) {
            // this will probably lead to a type error later
            newcard = getCardinality();
        }
        SequenceType seqType = SequenceType.makeSequenceType(newItemType, newcard);
        setStaticType(seqType, constantValue, properties);
    }

    /**
     * Determine the data type of the expression, if possible
     *
     * @return the type of the variable, if this can be determined statically;
     * otherwise Type.ITEM (meaning not known in advance)
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (staticType == null || staticType.getPrimaryType() == AnyItemType.getInstance()) {
            if (binding != null) {
                SequenceType st = binding.getRequiredType();
                if (st != null) {
                    return st.getPrimaryType();
                }
            }
            return AnyItemType.getInstance();
        } else {
            return staticType.getPrimaryType();
        }
    }

    /**
     * Get the static type of the expression as a UType, following precisely the type
     * inference rules defined in the XSLT 3.0 specification.
     *
     * @return the static item type of the expression according to the XSLT 3.0 defined rules
     * @param contextItemType the static context item type
     */
    @Override
    public UType getStaticUType(UType contextItemType) {
        if (binding != null) {
            if (binding.isGlobal() ||
                    binding instanceof LocalParam ||
                    (binding instanceof LetExpression && ((LetExpression)binding).isInstruction()) ||
                    binding instanceof LocalVariableBinding) {
                SequenceType st = binding.getRequiredType();
                if (st != null) {
                    return st.getPrimaryType().getUType();
                } else {
                    return UType.ANY;
                }
            } else if (binding instanceof Assignation) {
                return ((Assignation) binding).getSequence().getStaticUType(contextItemType);
            }
        }
        return UType.ANY;
    }

    /**
     * For an expression that returns an integer or a sequence of integers, get
     * a lower and upper bound on the values of the integers that may be returned, from
     * static analysis. The default implementation returns null, meaning "unknown" or
     * "not applicable". Other implementations return an array of two IntegerValue objects,
     * representing the lower and upper bounds respectively. The values
     * UNBOUNDED_LOWER and UNBOUNDED_UPPER are used by convention to indicate that
     * the value may be arbitrarily large. The values MAX_STRING_LENGTH and MAX_SEQUENCE_LENGTH
     * are used to indicate values limited by the size of a string or the size of a sequence.
     *
     * @return the lower and upper bounds of integer values in the result, or null to indicate
     * unknown or not applicable.
     */
    @Override
    public IntegerValue[] getIntegerBounds() {
        if (binding != null) {
            return binding.getIntegerBoundsForVariable();
        } else {
            return null;
        }
    }

    /**
     * Get the static cardinality
     */

    @Override
    public int computeCardinality() {
        if (staticType == null) {
            if (binding == null) {
                return StaticProperty.ALLOWS_ZERO_OR_MORE;
            } else if (binding instanceof LetExpression) {
                return binding.getRequiredType().getCardinality();
            } else if (binding instanceof Assignation) {
                return StaticProperty.EXACTLY_ONE;
            } else if (binding.getRequiredType() == null) {
                return StaticProperty.ALLOWS_ZERO_OR_MORE;
            } else {
                return binding.getRequiredType().getCardinality();
            }
        } else {
            return staticType.getCardinality();
        }
    }

    /**
     * Determine the special properties of this expression
     *
     * @return {@link StaticProperty#NO_NODES_NEWLY_CREATED} (unless the variable is assignable using saxon:assign)
     */

    @Override
    public int computeSpecialProperties() {
        int p = super.computeSpecialProperties();
        if (binding == null || !binding.isAssignable()) {
            // if the variable reference is assignable, we mustn't move it, or any expression that contains it,
            // out of a loop. The way to achieve this is to treat it as a "creative" expression, because the
            // optimizer recognizes such expressions and handles them with care...
            p |= StaticProperty.NO_NODES_NEWLY_CREATED;
        }
        if (binding instanceof Assignation) {
            Expression exp = ((Assignation) binding).getSequence();
            if (exp != null) {
                p |= exp.getSpecialProperties() & StaticProperty.NOT_UNTYPED_ATOMIC;
            }
        }
        if (staticType != null &&
                !Cardinality.allowsMany(staticType.getCardinality()) &&
                staticType.getPrimaryType() instanceof NodeTest) {
            p |= StaticProperty.SINGLE_DOCUMENT_NODESET;
        }
        return p &~ StaticProperty.ALL_NODES_NEWLY_CREATED;
    }

    /**
     * Test if this expression is the same as another expression.
     * (Note, we only compare expressions that
     * have the same static and dynamic context).
     */

    public boolean equals(Object other) {
        return other instanceof VariableReference &&
                binding == ((VariableReference) other).binding &&
                binding != null;
    }

    /**
     * get HashCode for comparing two expressions
     */

    @Override
    public int computeHashCode() {
        return binding == null ? 73619830 : binding.hashCode();
    }


    @Override
    public int getIntrinsicDependencies() {
        int d = 0;
        if (binding == null) {
            // assume the worst
            d |= StaticProperty.DEPENDS_ON_LOCAL_VARIABLES |
                    StaticProperty.DEPENDS_ON_ASSIGNABLE_GLOBALS |
                    StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT;
        } else if (binding.isGlobal()) {
            if (binding.isAssignable()) {
                d |= StaticProperty.DEPENDS_ON_ASSIGNABLE_GLOBALS;
            }
            if (binding instanceof GlobalParam) {
                d |= StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT;
            }
        } else {
            d |= StaticProperty.DEPENDS_ON_LOCAL_VARIABLES;
        }
        return d;
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is provided. This implementation provides both all three methods
     * natively.
     */

    @Override
    public int getImplementationMethod() {
        return (Cardinality.allowsMany(getCardinality()) ? 0 : EVALUATE_METHOD)
                | ITERATE_METHOD | PROCESS_METHOD;
    }

    /**
     * Get the innermost scoping expression of this expression, for expressions that directly
     * depend on something in the dynamic context. For example, in the case of a local variable
     * reference this is the expression that causes the relevant variable to be bound; for a
     * context item expression it is the innermost focus-setting container. For expressions
     * that have no intrinsic dependency on the dynamic context, the value returned is null;
     * the scoping container for such an expression is the innermost scoping container of its
     * operands.
     *
     * @return the innermost scoping container of this expression
     */
    @Override
    public Expression getScopingExpression() {
        if (binding instanceof Expression) {
            if (binding instanceof LocalParam && ((LocalParam)binding).getParentExpression() instanceof LocalParamBlock) {
                LocalParamBlock block = (LocalParamBlock)((LocalParam) binding).getParentExpression();
                return block.getParentExpression();
            } else {
                return (Expression) binding;
            }
        }
        Expression parent = getParentExpression();
        while (parent != null) {
            if (parent.hasVariableBinding(binding)) {
                return parent;
            }
            parent = parent.getParentExpression();
        }
        return null;
    }

    /**
     * Add a representation of this expression to a PathMap. The PathMap captures a map of the nodes visited
     * by an expression in a source tree.
     * <p>The default implementation of this method assumes that an expression does no navigation other than
     * the navigation done by evaluating its subexpressions, and that the subexpressions are evaluated in the
     * same context as the containing expression. The method must be overridden for any expression
     * where these assumptions do not hold. For example, implementations exist for AxisExpression, ParentExpression,
     * and RootExpression (because they perform navigation), and for the doc(), document(), and collection()
     * functions because they create a new navigation root. Implementations also exist for PathExpression and
     * FilterExpression because they have subexpressions that are evaluated in a different context from the
     * calling expression.</p>
     *
     * @param pathMap        the PathMap to which the expression should be added
     * @param pathMapNodeSet the PathMapNodeSet to which the paths embodied in this expression should be added
     * @return the pathMapNodeSet representing the points in the source document that are both reachable by this
     * expression, and that represent possible results of this expression. For an expression that does
     * navigation, it represents the end of the arc in the path map that describes the navigation route. For other
     * expressions, it is the same as the input pathMapNode.
     */

    @Override
    public PathMap.PathMapNodeSet addToPathMap(PathMap pathMap, PathMap.PathMapNodeSet pathMapNodeSet) {
        return pathMap.getPathForVariable(getBinding());
    }

    /**
     * Get the value of this variable in a given context.
     *
     * @param c the XPathContext which contains the relevant variable bindings
     * @return the value of the variable, if it is defined
     * @throws XPathException if the variable is undefined
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext c) throws XPathException {
        try {
            Sequence actual = evaluateVariable(c);
            assert actual != null;
            return actual.iterate();
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            throw err;
        } catch (NullPointerException err) {
            err.printStackTrace();
            String msg = "Internal error: no value for variable $" + getDisplayName() +
                    " at line " + getLocation().getLineNumber() + (getLocation().getSystemId() == null ? "" : " of " + getLocation().getSystemId());
            new StandardDiagnostics().printStackTrace(c, c.getConfiguration().getLogger(), 2);
            throw new AssertionError(msg);
        } catch (AssertionError err) {
            err.printStackTrace();
            String msg = err.getMessage() + ". Variable reference $" + getDisplayName() +
                    " at line " + getLocation().getLineNumber() + (getLocation().getSystemId() == null ? "" : " of " + getLocation().getSystemId());
            new StandardDiagnostics().printStackTrace(c, c.getConfiguration().getLogger(), 2);
            throw new AssertionError(msg);
        }
    }

    @Override
    public Item evaluateItem(XPathContext c) throws XPathException {
        try {
            Sequence actual = evaluateVariable(c);
            assert actual != null;
            return actual.head();
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            throw err;
        }
    }

    @Override
    public void process(Outputter output, XPathContext c) throws XPathException {
        try {
            SequenceIterator iter = evaluateVariable(c).iterate();
            Location loc = getLocation();
            iter.forEachOrFail(item -> output.append(item, loc, ReceiverOption.ALL_NAMESPACES));
        } catch (XPathException err) {
            err.maybeSetLocation(getLocation());
            throw err;
        }
    }

    /**
     * Evaluate this variable
     *
     * @param c the XPath dynamic context
     * @return the value of the variable
     * @throws XPathException if any error occurs
     */

    /*@NotNull*/
    public Sequence evaluateVariable(XPathContext c) throws XPathException {
        try {
            return binding.evaluateVariable(c);
        } catch (NullPointerException err) {
            if (binding == null) {
                throw new IllegalStateException("Variable $" + variableName.getDisplayName() + " has not been fixed up");
            } else {
                throw err;
            }
        }
    }

    /**
     * Get the object bound to the variable
     *
     * @return the Binding which declares this variable and associates it with a value
     */

    public Binding getBinding() {
        return binding;
    }

    /**
     * Get the display name of the variable. This is taken from the variable binding if possible
     *
     * @return the display name (a lexical QName
     */

    public String getDisplayName() {
        if (binding != null) {
            return binding.getVariableQName().getDisplayName();
        } else {
            return variableName.getDisplayName();
        }
    }

    /**
     * Get the EQName of the variable. This is taken from the variable binding if possible.
     * The returned name is in the format Q{uri}local if in a namespace, or the local name
     * alone if not.
     *
     * @return the EQName, or the local name if not in a namespace
     */

    public String getEQName() {
        if (binding != null) {
            StructuredQName q = binding.getVariableQName();
            if (q.hasURI("")) {
                return q.getLocalPart();
            } else {
                return q.getEQName();
            }
        } else {
            return variableName.getEQName();
        }
    }

    /**
     * The toString() method for an expression attempts to give a representation of the expression
     * in an XPath-like form, but there is no guarantee that the syntax will actually be true XPath.
     * In the case of XSLT instructions, the toString() method gives an abstracted view of the syntax
     */

    public String toString() {
        String d = getEQName();
        return "$" + (d == null ? "$" : d);
    }

    /**
     * Produce a short string identifying the expression for use in error messages
     *
     * @return a short string, sufficient to identify the expression
     */

    @Override
    public String toShortString() {
        return "$" + getDisplayName();
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in export() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "varRef";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter destination) throws XPathException {
        destination.startElement("varRef", this);
        destination.emitAttribute("name", variableName);
        if (binding instanceof LocalBinding) {
            destination.emitAttribute("slot", "" + ((LocalBinding) binding).getLocalSlotNumber());
        }

        destination.endElement();
    }

    /**
     * Get the (partial) name of a class that supports streaming of this kind of expression
     *
     * @return the partial name of a class that can be instantiated to provide streaming support in Saxon-EE,
     * or null if there is no such class
     */
    @Override
    public String getStreamerName() {
        return "VariableReference";
    }
}

