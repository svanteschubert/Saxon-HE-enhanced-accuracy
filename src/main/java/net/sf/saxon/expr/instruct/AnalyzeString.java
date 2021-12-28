////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Outputter;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * An xsl:analyze-string element in the stylesheet. New at XSLT 2.0
 */

public class AnalyzeString extends Instruction implements ContextOriginator {

    private Operand selectOp;
    private Operand regexOp;
    private Operand flagsOp;
    private Operand matchingOp;
    private Operand nonMatchingOp;

    private final static OperandRole ACTION =
            new OperandRole(OperandRole.USES_NEW_FOCUS | OperandRole.HIGHER_ORDER, OperandUsage.NAVIGATION);
    private final static OperandRole SELECT =
            new OperandRole(OperandRole.SETS_NEW_FOCUS, OperandUsage.ABSORPTION, SequenceType.SINGLE_STRING);

    private RegularExpression pattern;

    /**
     * Construct an AnalyzeString instruction
     *
     * @param select      the expression containing the input string
     * @param regex       the regular expression
     * @param flags       the flags parameter
     * @param matching    actions to be applied to a matching substring. May be null.
     * @param nonMatching actions to be applied to a non-matching substring. May be null.
     * @param pattern     the compiled regular expression, if it was known statically
     */
    public AnalyzeString(Expression select,
                         Expression regex,
                         Expression flags,
                         Expression matching,
                         Expression nonMatching,
                         RegularExpression pattern) {
        selectOp = new Operand(this, select, SELECT);
        regexOp = new Operand(this, regex, OperandRole.SINGLE_ATOMIC);
        flagsOp = new Operand(this, flags, OperandRole.SINGLE_ATOMIC);
        if (matching != null) {
            matchingOp = new Operand(this, matching, ACTION);
        }
        if (nonMatching != null) {
            nonMatchingOp = new Operand(this, nonMatching, ACTION);
        }
        this.pattern = pattern;
    }

    public Expression getSelect() {
        return selectOp.getChildExpression();
    }

    public void setSelect(Expression select) {
        selectOp.setChildExpression(select);
    }

    public Expression getRegex() {
        return regexOp.getChildExpression();
    }

    public void setRegex(Expression regex) {
        regexOp.setChildExpression(regex);
    }

    public Expression getFlags() {
        return flagsOp.getChildExpression();
    }

    public void setFlags(Expression flags) {
        flagsOp.setChildExpression(flags);
    }

    public Expression getMatching() {
        return matchingOp == null ? null : matchingOp.getChildExpression();
    }

    public void setMatching(Expression matching) {
        if (matchingOp != null) {
            matchingOp.setChildExpression(matching);
        } else {
            matchingOp = new Operand(this, matching, ACTION);
        }
    }

    public Expression getNonMatching() {
        return nonMatchingOp == null ? null : nonMatchingOp.getChildExpression();
    }

    public void setNonMatching(Expression nonMatching) {
        if (nonMatchingOp != null) {
            nonMatchingOp.setChildExpression(nonMatching);
        } else {
            nonMatchingOp = new Operand(this, nonMatching, ACTION);
        }
    }

    @Override
    public int getInstructionNameCode() {
        return StandardNames.XSL_ANALYZE_STRING;
    }

    @Override
    public Iterable<Operand> operands() {
        return operandSparseList(selectOp, regexOp, flagsOp, matchingOp, nonMatchingOp);
    }

    /**
     * An implementation of Expression must provide at least one of the methods evaluateItem(), iterate(), or process().
     * This method indicates which of these methods is prefered.
     */

    @Override
    public int getImplementationMethod() {
        return Expression.PROCESS_METHOD | Expression.ITERATE_METHOD;
    }

    /**
     * @return the compiled regular expression, if it was known statically
     */
    public RegularExpression getPatternExpression() {
        return pattern;
    }

    /**
     * Ask whether common subexpressions found in the operands of this expression can
     * be extracted and evaluated outside the expression itself. The result is irrelevant
     * in the case of operands evaluated with a different focus, which will never be
     * extracted in this way, even if they have no focus dependency.
     *
     * @return false for this kind of expression
     */
    @Override
    public boolean allowExtractingCommonSubexpressions() {
        return false;
    }

    /*@NotNull*/
    @Override
    public Expression typeCheck(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        selectOp.typeCheck(visitor, contextInfo);
        regexOp.typeCheck(visitor, contextInfo);
        flagsOp.typeCheck(visitor, contextInfo);

        if (matchingOp != null) {
            matchingOp.typeCheck(visitor, config.makeContextItemStaticInfo(BuiltInAtomicType.STRING, false));
        }
        if (nonMatchingOp != null) {
            nonMatchingOp.typeCheck(visitor, config.makeContextItemStaticInfo(BuiltInAtomicType.STRING, false));
        }

        TypeChecker tc = visitor.getConfiguration().getTypeChecker(false);

        RoleDiagnostic role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "analyze-string/select", 0);
        SequenceType required = SequenceType.OPTIONAL_STRING;
        // see bug 7976
        setSelect(tc.staticTypeCheck(getSelect(), required, role, visitor));

        role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "analyze-string/regex", 0);
        setRegex(tc.staticTypeCheck(getRegex(), SequenceType.SINGLE_STRING, role, visitor));

        role = new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "analyze-string/flags", 0);
        setFlags(tc.staticTypeCheck(getFlags(), SequenceType.SINGLE_STRING, role, visitor));

        return this;
    }


    /*@NotNull*/
    @Override
    public Expression optimize(ExpressionVisitor visitor, ContextItemStaticInfo contextInfo) throws XPathException {

        Configuration config = visitor.getConfiguration();
        selectOp.optimize(visitor, contextInfo);
        regexOp.optimize(visitor, contextInfo);
        flagsOp.optimize(visitor, contextInfo);

        if (matchingOp != null) {
            matchingOp.optimize(visitor, config.makeContextItemStaticInfo(BuiltInAtomicType.STRING, false));
        }
        if (nonMatchingOp != null) {
            nonMatchingOp.optimize(visitor, config.makeContextItemStaticInfo(BuiltInAtomicType.STRING, false));
        }

        List<String> warnings = new ArrayList<>();
        precomputeRegex(config, warnings);
        for (String w : warnings) {
            visitor.getStaticContext().issueWarning(w, getLocation());
        }

        return this;
    }

    public void precomputeRegex(Configuration config, List<String> warnings) throws XPathException {
        if (pattern == null && getRegex() instanceof StringLiteral && getFlags() instanceof StringLiteral) {
            try {
                final CharSequence regex = ((StringLiteral) this.getRegex()).getStringValue();
                final CharSequence flagstr = ((StringLiteral) getFlags()).getStringValue();

                String hostLang = "XP30";
                pattern = config.compileRegularExpression(regex, flagstr.toString(), hostLang, warnings);

            } catch (XPathException err) {
                if ("XTDE1150".equals(err.getErrorCodeLocalPart())) {
                    throw err;
                }
                if ("FORX0001".equals(err.getErrorCodeLocalPart())) {
                    invalidRegex("Error in regular expression flags: " + err, "FORX0001");
                } else {
                    invalidRegex("Error in regular expression: " + err, err.getErrorCodeLocalPart());
                }
            }
        }
    }

    private void invalidRegex(String message, String errorCode) throws XPathException {
        pattern = null;
        XPathException err = new XPathException(message, errorCode);
        err.setLocation(getLocation());
        throw err;
    }

    /**
     * Copy an expression. This makes a deep copy.
     *
     * @return the copy of the original expression
     * @param rm the rebinding map
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rm) {
        AnalyzeString a2 = new AnalyzeString(
                copy(getSelect(), rm), copy(getRegex(), rm), copy(getFlags(), rm), copy(getMatching(), rm), copy(getNonMatching(), rm), pattern);
        ExpressionTool.copyLocationInfo(this, a2);
        return a2;
    }

    private Expression copy(Expression exp, RebindingMap rebindings) {
        return exp == null ? null : exp.copy(rebindings);
    }


    /**
     * Check that any elements and attributes constructed or returned by this expression are acceptable
     * in the content model of a given complex type. It's always OK to say yes, since the check will be
     * repeated at run-time. The process of checking element and attribute constructors against the content
     * model of a complex type also registers the type of content expected of those constructors, so the
     * static validation can continue recursively.
     */

    @Override
    public void checkPermittedContents(SchemaType parentType, boolean whole) throws XPathException {
        if (getMatching() != null) {
            getMatching().checkPermittedContents(parentType, false);
        }
        if (getNonMatching() != null) {
            getNonMatching().checkPermittedContents(parentType, false);
        }
    }

    /**
     * Get the item type of the items returned by evaluating this instruction
     *
     * @return the static item type of the instruction
     */

    /*@NotNull*/
    @Override
    public ItemType getItemType() {
        if (getMatching() != null) {
            if (getNonMatching() != null) {
                TypeHierarchy th = getConfiguration().getTypeHierarchy();
                return Type.getCommonSuperType(getMatching().getItemType(), getNonMatching().getItemType(), th);
            } else {
                return getMatching().getItemType();
            }
        } else {
            if (getNonMatching() != null) {
                return getNonMatching().getItemType();
            } else {
                return ErrorType.getInstance();
            }
        }
    }

    /**
     * Compute the dependencies of an expression, as the union of the
     * dependencies of its subexpressions. (This is overridden for path expressions
     * and filter expressions, where the dependencies of a subexpression are not all
     * propogated). This method should be called only once, to compute the dependencies;
     * after that, getDependencies should be used.
     *
     * @return the depencies, as a bit-mask
     */

    @Override
    public int computeDependencies() {
        // some of the dependencies in the "action" part and in the grouping and sort keys aren't relevant,
        // because they don't depend on values set outside the for-each-group expression
        int dependencies = 0;
        dependencies |= getSelect().getDependencies();
        dependencies |= getRegex().getDependencies();
        dependencies |= getFlags().getDependencies();
        if (getMatching() != null) {
            dependencies |= getMatching().getDependencies() & ~
                    (StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_REGEX_GROUP);
        }
        if (getNonMatching() != null) {
            dependencies |= getNonMatching().getDependencies() & ~
                    (StaticProperty.DEPENDS_ON_FOCUS | StaticProperty.DEPENDS_ON_REGEX_GROUP);
        }
        return dependencies;
    }


    /**
     * ProcessLeavingTail: called to do the real work of this instruction. This method
     * must be implemented in each subclass. The results of the instruction are written
     * to the current Receiver, which can be obtained via the Controller.
     *
     *
     * @param output the destination for the result
     * @param context The dynamic context of the transformation, giving access to the current node,
     *                the current variables, etc.
     * @return null if the instruction has completed execution; or a TailCall indicating
     *         a function call or template call that is delegated to the caller, to be made after the stack has
     *         been unwound so as to save stack space.
     */

    @Override
    public TailCall processLeavingTail(Outputter output, XPathContext context) throws XPathException {
        RegexIterator iter = getRegexIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        FocusIterator focusIter = c2.trackFocus(iter);
        c2.setCurrentRegexIterator(iter);

        PipelineConfiguration pipe = output.getPipelineConfiguration();
        pipe.setXPathContext(c2);

        Item it;
        while ((it = focusIter.next()) != null) {
            if (iter.isMatching()) {
                if (getMatching() != null) {
                    getMatching().process(output, c2);
                }
            } else {
                if (getNonMatching() != null) {
                    getNonMatching().process(output, c2);
                }
            }
        }

        pipe.setXPathContext(context);
        return null;

    }

    /**
     * Get an iterator over the substrings defined by the regular expression
     *
     * @param context the evaluation context
     * @return an iterator that returns matching and nonmatching substrings
     * @throws XPathException if evaluation fails with a dynamic error
     */

    private RegexIterator getRegexIterator(XPathContext context) throws XPathException {
        CharSequence input = getSelect().evaluateAsString(context);

        RegularExpression re = pattern;
        if (re == null) {
            String flagstr = getFlags().evaluateAsString(context).toString();
            StringValue regexString = (StringValue)getRegex().evaluateItem(context);
            re = context.getConfiguration().compileRegularExpression(
                        getRegex().evaluateAsString(context), flagstr, "XP30", null);
        }

        return re.analyze(input);
    }

    /**
     * Return an Iterator to iterate over the values of a sequence. The value of every
     * expression can be regarded as a sequence, so this method is supported for all
     * expressions. This default implementation handles iteration for expressions that
     * return singleton values: for non-singleton expressions, the subclass must
     * provide its own implementation.
     *
     * @param context supplies the context for evaluation
     * @return a SequenceIterator that can be used to iterate over the result
     *         of the expression
     * @throws net.sf.saxon.trans.XPathException
     *          if any dynamic error occurs evaluating the
     *          expression
     */

    /*@NotNull*/
    @Override
    public SequenceIterator iterate(XPathContext context) throws XPathException {
        RegexIterator iter = getRegexIterator(context);
        XPathContextMajor c2 = context.newContext();
        c2.setOrigin(this);
        c2.trackFocus(iter);
        c2.setCurrentRegexIterator(iter);

        AnalyzeMappingFunction fn = new AnalyzeMappingFunction(iter, c2, getNonMatching(), getMatching());
        return new ContextMappingIterator(fn, c2);
    }

    /**
     * Get a name identifying the kind of expression, in terms meaningful to a user.
     *
     * @return a name identifying the kind of expression, in terms meaningful to a user.
     * The name will always be in the form of a lexical XML QName, and should match the name used
     * in explain() output displaying the expression.
     */
    @Override
    public String getExpressionName() {
        return "analyzeString";
    }

    /**
     * Diagnostic print of expression structure. The abstract expression tree
     * is written to the supplied output destination.
     */

    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("analyzeString", this);
        out.setChildRole("select");
        getSelect().export(out);
        out.setChildRole("regex");
        getRegex().export(out);
        out.setChildRole("flags");
        getFlags().export(out);
        if (getMatching() != null) {
            out.setChildRole("matching");
            getMatching().export(out);
        }
        if (getNonMatching() != null) {
            out.setChildRole("nonMatching");
            getNonMatching().export(out);
        }
        out.endElement();
    }


}

