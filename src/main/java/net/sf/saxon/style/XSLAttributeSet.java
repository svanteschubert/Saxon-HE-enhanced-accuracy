////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.expr.Component;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.instruct.AttributeSet;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.UseAttributeSet;
import net.sf.saxon.expr.parser.Optimizer;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.value.Whitespace;

import java.util.ArrayList;
import java.util.List;

/**
 * An xsl:attribute-set element in the stylesheet. <br>
 */

public class XSLAttributeSet extends StyleElement implements StylesheetComponent {

    private String nameAtt;
    // the name of the attribute set as written

    /*@Nullable*/ private String useAtt;
    // the value of the use-attribute-sets attribute, as supplied

    private String visibilityAtt;
    // the value of the visibility attribute, trimmed

    private SlotManager stackFrameMap;
    // needed if variables are used

    private List<ComponentDeclaration> attributeSetElements = new ArrayList<ComponentDeclaration>();
    // list of Declarations of XSLAttributeSet objects referenced by this one, within the same package

    private StructuredQName[] useAttributeSetNames;

    private List<Expression> containedInstructions = new ArrayList<Expression>();
    // the compiled form of this attribute set

    private boolean validated = false;
    private Visibility visibility;
    private boolean streamable = false;

    /**
     * Get the corresponding Procedure object that results from the compilation of this
     * StylesheetProcedure
     */
    @Override
    public AttributeSet getActor() {
        return (AttributeSet)getPrincipalStylesheetModule().getStylesheetPackage().getComponent(
            new SymbolicName(StandardNames.XSL_ATTRIBUTE_SET, getObjectName())).getActor();
    }

    @Override
    public SymbolicName getSymbolicName() {
        return new SymbolicName(StandardNames.XSL_ATTRIBUTE_SET, getObjectName());
    }

    /**
     * Check the compatibility of this component with another component that it is overriding
     *
     * @param component the overridden component
     * @throws XPathException if the components are not compatible (differing signatures)
     */

    @Override
    public void checkCompatibility(Component component) throws XPathException {
        if (((AttributeSet)component.getActor()).isDeclaredStreamable() && !isDeclaredStreamable()) {
            compileError("The overridden attribute set is declared streamable, " +
                "so the overriding attribute set must also be declared streamable");
        }
    }

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Get the name of this attribute set
     *
     * @return the name of the attribute set, as a QName
     */

    public StructuredQName getAttributeSetName() {
        return getObjectName();
    }

    /**
     * Ask whether the attribute set is declared streamable
     * @return true if the attribute streamable="yes" is present
     */

    public boolean isDeclaredStreamable() {
        return streamable;
    }


    @Override
    public void prepareAttributes() {
        useAtt = null;
        String streamableAtt = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String f = attName.getDisplayName();
            String value = att.getValue();
            switch (f) {
                case "name":
                    nameAtt = Whitespace.trim(value);
                    break;
                case "use-attribute-sets":
                    useAtt = value;
                    break;
                case "streamable":
                    streamableAtt = value;
                    break;
                case "visibility":
                    visibilityAtt = Whitespace.trim(value);
                    break;
                default:
                    checkUnknownAttribute(attName);
                    break;
            }
        }

        if (nameAtt == null) {
            reportAbsence("name");
            setObjectName(new StructuredQName("", "", "attribute-set-error-name"));
            return;
        }

        if (visibilityAtt == null) {
            visibility = Visibility.PRIVATE;
        } else {
            visibility = interpretVisibilityValue(visibilityAtt, "");
        }

        if (streamableAtt != null) {
            streamable = processStreamableAtt(streamableAtt);
        }

        setObjectName(makeQName(nameAtt, null, "name"));

    }

    /**
     * Get a name identifying the object of the expression, for example a function name, template name,
     * variable name, key name, element name, etc. This is used only where the name is known statically.
     * If there is no name, the value will be null.
     *
     * @return the name of the object declared in this element, if any
     */

    @Override
    public StructuredQName getObjectName() {
        StructuredQName o = super.getObjectName();
        if (o == null) {
            prepareAttributes();
            o = getObjectName();
        }
        return o;
    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        if (validated) {
            return;
        }

        checkTopLevel("XTSE0010", true);

        stackFrameMap = getConfiguration().makeSlotManager();

        for (NodeInfo child : children()) {
            if (child instanceof XSLAttribute) {
                if (visibility == Visibility.ABSTRACT) {
                    compileError("An abstract attribute-set must contain no xsl:attribute instructions");
                }
            } else {
                compileError("Only xsl:attribute is allowed within xsl:attribute-set", "XTSE0010");
            }
        }

        if (useAtt != null) {
            if (visibility == Visibility.ABSTRACT) {
                compileError("An abstract attribute-set must have no @use-attribute-sets attribute");
            }

            // identify any attribute sets that this one refers to

            useAttributeSetNames = getUsedAttributeSets(useAtt);
        }

        validated = true;
    }

    public StructuredQName[] getUseAttributeSetNames() {
        return useAttributeSetNames;
    }

    @Override
    public void index(ComponentDeclaration decl, PrincipalStylesheetModule top) throws XPathException {
        top.indexAttributeSet(decl);
    }


    /**
     * Check for circularity: specifically, check that this attribute set does not contain
     * a direct or indirect reference to the one supplied as a parameter
     *
     * @param origin the place from which the search started
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is found
     */

    public void checkCircularity(XSLAttributeSet origin) throws XPathException {
        if (this == origin) {
            compileError("The definition of the attribute set is circular", "XTSE0720");
        } else {
            if (!validated) {
                // if this attribute set isn't validated yet, we don't check it.
                // The circularity will be detected when the last attribute set in the cycle
                // gets validated
                return;
            }
            if (attributeSetElements != null) {
                for (ComponentDeclaration attributeSetElement : attributeSetElements) {
                    XSLAttributeSet element = (XSLAttributeSet) attributeSetElement.getSourceElement();
                    element.checkCircularity(origin);
                    if (streamable && !element.streamable) {
                        compileError("Attribute-set is declared streamable but references a non-streamable attribute set " +
                                element.getAttributeSetName().getDisplayName(), "XTSE3430");
                    }
                }
            }
        }
    }

    /**
     * Get the contained xsl:attribute instructions, in compiled form
     * @return the list of contained instructions. This does not include any xsl:use-attribute-set references.
     */

    public List<Expression> getContainedInstructions() {
        return containedInstructions;
    }

    /**
     * Get details of stack frame
     */

    @Override
    public SlotManager getSlotManager() {
        return stackFrameMap;
    }

    /**
     * Compile the attribute set
     *
     * @param compilation the current compilation episode
     * @param decl        this attribute set declaration
     * @throws XPathException if a failure is detected
     */
    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {

        if (isActionCompleted(ACTION_COMPILE)) {
            return;
        }

        if (useAtt != null) {

            // identify any attribute sets that this one refers to

            List<UseAttributeSet> invocations = UseAttributeSet.makeUseAttributeSetInstructions(useAttributeSetNames, this);
            if (!invocations.isEmpty()) {
                containedInstructions.add(UseAttributeSet.makeCompositeExpression(invocations));
            }

            // check for circularity, to the extent possible within a single package

            for (StructuredQName name : useAttributeSetNames) {
                getPrincipalStylesheetModule().getAttributeSets(name, attributeSetElements);
            }

            for (ComponentDeclaration attributeSetElement : attributeSetElements) {
                ((XSLAttributeSet) attributeSetElement.getSourceElement()).checkCircularity(this);
            }

            // check for consistency of streamability attribute

            if (streamable) {
                for (ComponentDeclaration attributeSetElement : attributeSetElements) {
                    if (!((XSLAttributeSet) attributeSetElement.getSourceElement()).streamable) {
                        compileError("Attribute set is declared streamable, " +
                            "but references an attribute set that is not declared streamable", "XTSE0730");
                    }
                }
            }

        }

        XSLAttribute node;
        AxisIterator iter = iterateAxis(AxisInfo.CHILD, NodeKindTest.ELEMENT);
        while ((node = (XSLAttribute) iter.next()) != null) {
            Expression inst = node.compile(compilation, decl);
            inst.setRetainedStaticContext(makeRetainedStaticContext());
            inst = inst.simplify();
            if (compilation.getCompilerInfo().isCompileWithTracing()) {
                inst = makeTraceInstruction(this, inst);
            }
            containedInstructions.add(inst);
        }

        setActionCompleted(ACTION_COMPILE);
    }

    /**
     * Optimize the stylesheet construct
     *
     * @param declaration this attribute set declaration
     */

    @Override
    public void optimize(ComponentDeclaration declaration) throws XPathException {
        // Already done earlier
    }


    /**
     * Generate byte code if appropriate
     *
     * @param opt the optimizer
     */
    @Override
    public void generateByteCode(Optimizer opt) {}

//    private void checkStreamability() throws XPathException {
////#ifdefined STREAM
//         if (streamable) {
//             ContextItemStaticInfo info = new ContextItemStaticInfo(AnyItemType.getInstance(), false, true);
//             procedure.getBody().getStreamability(false, info, null);
//             if (procedure.getBody().getSweep() != Sweep.MOTIONLESS) {
//                 compileError("The attribute set is declared streamable but it is not motionless", "XTSE3430");
//             }
//         }
////#endif
//     }



}

