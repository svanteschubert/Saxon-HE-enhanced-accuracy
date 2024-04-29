////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.om.*;
import net.sf.saxon.regex.RegexIterator;
import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.SaxonErrorCode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.type.*;
import net.sf.saxon.value.SequenceType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * An xsl:analyze-string element in the stylesheet. New at XSLT 2.0
 */

public class AnalyzeString extends Instruction implements ContextOriginator {

    private final Operand selectOp;
    private final Operand regexOp;
    private final Operand flagsOp;
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

        Supplier<RoleDiagnostic> role = () -> new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "analyze-string/select", 0);
        SequenceType required = SequenceType.OPTIONAL_STRING;
        // see bug 7976
        setSelect(tc.staticTypeCheck(getSelect(), required, role, visitor));

        role = () -> new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "analyze-string/regex", 0);
        setRegex(tc.staticTypeCheck(getRegex(), SequenceType.SINGLE_STRING, role, visitor));

        role = () -> new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "analyze-string/flags", 0);
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
            visitor.getStaticContext().issueWarning(w, SaxonErrorCode.SXWN9022, getLocation());
        }

        return this;
    }

    public void precomputeRegex(Configuration config, List<String> warnings) throws XPathException {
        if (pattern == null && getRegex() instanceof StringLiteral && getFlags() instanceof StringLiteral) {
            try {
                final String regex = ((StringLiteral) this.getRegex()).stringify();
                final String flagstr = ((StringLiteral) getFlags()).stringify();

                String hostLang = "XP30";
                pattern = config.compileRegularExpression(StringView.tidy(regex), flagstr, hostLang, warnings);

            } catch (XPathException err) {
                if (err.hasErrorCode("XTDE1150")) {
                    throw err;
                }
                if (err.hasErrorCode("FORX0001")) {
                    invalidRegex("Error in regular expression flags: " + err, err.getErrorCodeQName());
                } else {
                    invalidRegex("Error in regular expression: " + err, err.getErrorCodeQName());
                }
            }
        }
    }

    private void invalidRegex(String message, StructuredQName errorCode) throws XPathException {
        pattern = null;
        throw new XPathException(message)
                .withErrorCode(errorCode)
                .withLocation(getLocation());
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
        return makeElaborator().elaborateForPull().iterate(context);
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

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new AnalyzeStringElaborator();
    }

    @FunctionalInterface
    private interface RegexEvaluator {
        RegularExpression compileRegex(XPathContext context) throws XPathException;
    }

    public static class AnalyzeStringElaborator extends PullElaborator {

        @Override
        public PullEvaluator elaborateForPull() {
            AnalyzeString expr = (AnalyzeString) getExpression();
            UnicodeStringEvaluator input = expr.getSelect().makeElaborator().elaborateForUnicodeString(true);
            PullEvaluator matching = expr.getMatching() == null
                    ? null
                    : expr.getMatching().makeElaborator().elaborateForPull();
            PullEvaluator nonMatching = expr.getNonMatching() == null
                    ? null
                    : expr.getNonMatching().makeElaborator().elaborateForPull();
            RegexEvaluator regexSupplier = getRegexSupplier(expr);

            return context -> {
                RegularExpression re = regexSupplier.compileRegex(context);
                UnicodeString in = input.eval(context);
                RegexIterator iter = re.analyze(in);
                XPathContextMajor c2 = context.newContext();
                c2.setOrigin(expr);
                c2.trackFocus(iter);
                c2.setCurrentRegexIterator(iter);

                return new ContextMappingIterator(cxt -> {
                    if (iter.isMatching()) {
                        if (matching != null) {
                            return matching.iterate(c2);
                        }
                    } else {
                        if (nonMatching != null) {
                            return nonMatching.iterate(c2);
                        }
                    }
                    return EmptyIterator.getInstance();
                }, c2);
            };
        }

        @Override
        public PushEvaluator elaborateForPush() {
            AnalyzeString expr = (AnalyzeString) getExpression();
            UnicodeStringEvaluator input = expr.getSelect().makeElaborator().elaborateForUnicodeString(true);
            PushEvaluator matching = expr.getMatching() == null
                    ? null
                    : expr.getMatching().makeElaborator().elaborateForPush();
            PushEvaluator nonMatching = expr.getNonMatching() == null
                    ? null
                    : expr.getNonMatching().makeElaborator().elaborateForPush();

            RegexEvaluator regexSupplier = getRegexSupplier(expr);

            return (out, context) -> {
                RegularExpression re = regexSupplier.compileRegex(context);
                UnicodeString in = input.eval(context);
                RegexIterator iter = re.analyze(in);
                XPathContextMajor c2 = context.newContext();
                c2.setOrigin(expr);
                FocusIterator focus = c2.trackFocus(iter);
                c2.setCurrentRegexIterator(iter);

                while(focus.next() != null) {
                    if (iter.isMatching()) {
                        if (matching != null) {
                            dispatchTailCall(matching.processLeavingTail(out, c2));
                        }
                    } else {
                        if (nonMatching != null) {
                            dispatchTailCall(nonMatching.processLeavingTail(out, c2));
                        }
                    }
                }
                return null;
            };

        }

        private RegexEvaluator getRegexSupplier(AnalyzeString expr) {
            RegularExpression pattern = expr.getPatternExpression();
            RegexEvaluator regexSupplier;
            if (expr.pattern != null) {
                // regex and flags were known statically
                regexSupplier = context -> pattern;
            } else {
                // regex or flags is dynamic
                StringEvaluator flagsEval = expr.getFlags().makeElaborator().elaborateForString(true);
                UnicodeStringEvaluator regexEval = expr.getRegex().makeElaborator().elaborateForUnicodeString(false);
                regexSupplier = context -> {
                    String flagsStr = flagsEval.eval(context);
                    UnicodeString regexStr = regexEval.eval(context);
                    return context.getConfiguration().compileRegularExpression(
                            regexStr, flagsStr, "XP31", null);
                };
            }
            return regexSupplier;
        }
    }

}

