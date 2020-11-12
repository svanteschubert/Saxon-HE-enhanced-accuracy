////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.ApplyTemplates;
import net.sf.saxon.expr.instruct.CallTemplate;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.parser.*;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.lib.StringCollator;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeTestPattern;
import net.sf.saxon.pattern.Pattern;
import net.sf.saxon.trans.*;
import net.sf.saxon.type.BuiltInAtomicType;
import net.sf.saxon.type.ErrorType;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.Whitespace;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Predicate;

/**
 * Handler for xsl:key elements in stylesheet. <br>
 */

public class XSLKey extends StyleElement implements StylesheetComponent {

    private Pattern match;
    private Expression use;
    private String collationName;
    private StructuredQName keyName;
    private SlotManager stackFrameMap;
    private boolean rangeKey;
    private boolean composite = false;
    private KeyDefinition keyDefinition;


    /**
     * Get the corresponding Actor object that results from the compilation of this
     * StylesheetComponent
     *
     * @return the compiled ComponentCode
     * @throws UnsupportedOperationException for second-class components such as keys that support outwards references
     *                                       but not inwards references
     */
    @Override
    public Actor getActor() {
        throw new UnsupportedOperationException();
    }

    @Override
    public SymbolicName getSymbolicName() {
        return null;
    }

    @Override
    public void checkCompatibility(Component component) {
        // no action: keys cannot be overridden
    }

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Determine whether this type of element is allowed to contain a sequence constructor
     *
     * @return true: yes, it may contain a sequence constructor
     */

    @Override
    public boolean mayContainSequenceConstructor() {
        return true;
    }

    /**
     * Get the Procedure object that looks after any local variables declared in the content constructor
     */

    @Override
    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    @Override
    public void prepareAttributes() {

        String nameAtt = null;
        String matchAtt = null;
        String useAtt;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String uri = attName.getURI();
            String local = attName.getLocalPart();
            if ("".equals(uri)) {
                switch (local) {
                    case "name":
                        nameAtt = Whitespace.trim(value);
                        break;
                    case "use":
                        useAtt = value;
                        use = makeExpression(useAtt, att);
                        break;
                    case "match":
                        matchAtt = value;
                        break;
                    case "collation":
                        collationName = Whitespace.trim(value);
                        break;
                    case "composite":
                        composite = processBooleanAttribute("composite", value);
                        break;
                    default:
                        checkUnknownAttribute(attName);
                        break;
                }
            } else if (local.equals("range-key") && uri.equals(NamespaceConstant.SAXON)) {
                rangeKey = processBooleanAttribute("range-key", value);
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            nameAtt = "_dummy_key_name";
        }
        keyName = makeQName(nameAtt, null, "name");
        setObjectName(keyName);

        if (matchAtt == null) {
            reportAbsence("match");
            matchAtt = "*";
        }
        match = makePattern(matchAtt, "match");
        if (match == null) {
            // error has been reported
            match = new NodeTestPattern(ErrorType.getInstance());
        }

    }

    public StructuredQName getKeyName() {
        //We use null to mean "not yet evaluated"
        if (getObjectName() == null) {
            // allow for forwards references
            String nameAtt = getAttributeValue("", "name");
            if (nameAtt != null) {
                setObjectName(makeQName(nameAtt, null, "name"));
            }
        }
        return getObjectName();
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        Configuration config = getConfiguration();
        stackFrameMap = config.makeSlotManager();

        checkTopLevel("XTSE0010", false);
        if (use != null) {
            // the value can be supplied as a content constructor in place of a use expression
            if (hasChildNodes()) {
                compileError("An xsl:key element with a @use attribute must be empty", "XTSE1205");
            }
            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:key/use", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                use = config.getTypeChecker(false).staticTypeCheck(
                        use,
                        SequenceType.ATOMIC_SEQUENCE,
                        role, makeExpressionVisitor());
            } catch (XPathException err) {
                compileError(err);
            }
        } else {
            if (!hasChildNodes()) {
                compileError("An xsl:key element must either have a @use attribute or have content", "XTSE1205");
            }
        }
        use = typeCheck("use", use);
        match = typeCheck("match", match);

        // Do a further check that the use expression makes sense in the context of the match pattern
        if (use != null) {
            use = use.typeCheck(makeExpressionVisitor(), config.makeContextItemStaticInfo(match.getItemType(), false));
        }

        if (collationName != null) {
            URI collationURI;
            try {
                collationURI = new URI(collationName);
                if (!collationURI.isAbsolute()) {
                    URI base = new URI(getBaseURI());
                    collationURI = base.resolve(collationURI);
                    collationName = collationURI.toString();
                }
            } catch (URISyntaxException err) {
                compileError("Collation name '" + collationName + "' is not a valid URI");
                //collationName = NamespaceConstant.CODEPOINT_COLLATION_URI;
            }
        } else {
            collationName = getDefaultCollationName();
        }

    }

    private static class ContainsGlobalVariable implements Predicate<Expression> {
        @Override
        public boolean test(Expression e) {
            return e instanceof GlobalVariableReference ||
                    e instanceof UserFunctionCall ||
                    e instanceof CallTemplate ||
                    e instanceof ApplyTemplates;
        }
    }

    private static ContainsGlobalVariable containsGlobalVariable = new ContainsGlobalVariable();

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) {
        StructuredQName keyName = getKeyName();
        if (keyName != null) {
            top.getKeyManager().preRegisterKeyDefinition(keyName);
        }
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        StaticContext env = getStaticContext();
        Configuration config = env.getConfiguration();
        StringCollator collator = null;
        if (collationName != null) {
            collator = findCollation(collationName, getBaseURI());
            if (collator == null) {
                compileError("The collation name " + Err.wrap(collationName, Err.URI) + " is not recognized", "XTSE1210");
                collator = CodepointCollator.getInstance();
            }
            if (collator instanceof CodepointCollator) {
                // if the user explicitly asks for the codepoint collation, treat it as if they hadn't asked
                collator = null;
                collationName = null;

            } else if (!Version.platform.canReturnCollationKeys(collator)) {
                compileError("The collation used for xsl:key must be capable of generating collation keys", "XTSE1210");
            }
        }

        if (use == null) {
            Expression body = compileSequenceConstructor(compilation, decl, true);

            try {
                use = Atomizer.makeAtomizer(body, null);
                use = use.simplify();
            } catch (XPathException e) {
                compileError(e);
            }

            try {
                RoleDiagnostic role =
                        new RoleDiagnostic(RoleDiagnostic.INSTRUCTION, "xsl:key/use", 0);
                //role.setSourceLocator(new ExpressionLocation(this));
                use = config.getTypeChecker(false).staticTypeCheck(
                        use,
                        SequenceType.ATOMIC_SEQUENCE,
                        role, makeExpressionVisitor());
                // Do a further check that the use expression makes sense in the context of the match pattern
                assert match != null;
                use = use.typeCheck(makeExpressionVisitor(), config.makeContextItemStaticInfo(match.getItemType(), false));


            } catch (XPathException err) {
                compileError(err);
            }
        }

        ItemType useItemType = use.getItemType();
        if (useItemType == ErrorType.getInstance()) {
            useItemType = BuiltInAtomicType.STRING; // corner case, prevents crashing
        }
        BuiltInAtomicType useType = (BuiltInAtomicType) useItemType.getPrimitiveItemType();
        if (xPath10ModeIsEnabled()) {
            if (!useType.equals(BuiltInAtomicType.STRING) && !useType.equals(BuiltInAtomicType.UNTYPED_ATOMIC)) {
                use = new AtomicSequenceConverter(use, BuiltInAtomicType.STRING);
                ((AtomicSequenceConverter) use).allocateConverter(config, false);
                useType = BuiltInAtomicType.STRING;
            }
        }

        // first slot in pattern is reserved for current()
        int nextFree = 0;
        if ((match.getDependencies() & StaticProperty.DEPENDS_ON_CURRENT_ITEM) != 0) {
            nextFree = 1;
        }
        match.allocateSlots(stackFrameMap, nextFree);

        // If either the match pattern or the use expression references a global variable or parameter,
        // or a call on a function or template that might reference one, then
        // the key indexes cannot be reused across multiple transformations. See Saxon bug 1968.

        boolean sensitive =
                ExpressionTool.contains(use, false, containsGlobalVariable) ||
                ExpressionTool.contains(match, false, containsGlobalVariable);


        KeyManager km = getCompilation().getPrincipalStylesheetModule().getKeyManager();
        SymbolicName symbolicName = new SymbolicName(StandardNames.XSL_KEY, keyName);
        KeyDefinition keydef = new KeyDefinition(symbolicName, match, use, collationName, collator);
        keydef.setPackageData(getCompilation().getPackageData());
        keydef.setRangeKey(rangeKey);
        keydef.setIndexedItemType(useType);
        keydef.setStackFrameMap(stackFrameMap);
        keydef.setLocation(getSystemId(), getLineNumber());
        keydef.setBackwardsCompatible(xPath10ModeIsEnabled());
        keydef.setComposite(composite);
        keydef.obtainDeclaringComponent(this);
        try {
            km.addKeyDefinition(keyName, keydef, !sensitive, compilation.getConfiguration());
        } catch (XPathException err) {
            compileError(err);
        }
        keyDefinition = keydef;
    }

    /**
     * Optimize the stylesheet construct
     *
     * @param declaration this xsl:key declaration
     */

    @Override
    public void optimize(ComponentDeclaration declaration) throws XPathException {
        ExpressionVisitor visitor = makeExpressionVisitor();
        ContextItemStaticInfo contextItemType = getConfiguration().makeContextItemStaticInfo(match.getItemType(), false);
        Expression useExp = keyDefinition.getUse();
        useExp = useExp.optimize(visitor, contextItemType);
        allocateLocalSlots(useExp);
        keyDefinition.setBody(useExp);
    }


    /**
     * Generate byte code if appropriate
     *
     * @param opt the optimizer
     */
    @Override
    public void generateByteCode(Optimizer opt) {}
}
