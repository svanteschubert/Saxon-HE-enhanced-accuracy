////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.instruct.Actor;
import net.sf.saxon.expr.instruct.AttributeSet;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.om.Function;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.style.StylesheetPackage;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.Mode;
import net.sf.saxon.trans.Visibility;
import net.sf.saxon.trans.VisibilityProvenance;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.util.FastStringBuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a component as defined in the XSLT 3.0 specification: for example a function, a named template,
 * an attribute set, a global variable.
 */

public class Component {

    protected Actor actor;
    private Visibility visibility;
    private List<ComponentBinding> bindings = new ArrayList<>();
    private StylesheetPackage containingPackage;
    private StylesheetPackage declaringPackage;
    private VisibilityProvenance provenance;
    private Component baseComponent;


    private Component() {}

    /**
     * Create a component
     * @param actor             the compiled code that implements the component, for example a Template or Function
     * @param visibility        the visibility of the component
     * @param provenance        where the visibility property came from
     * @param containingPackage the package to which this component belongs
     * @param declaringPackage  the package in which the original declaration of the component appears
     */

    public static Component makeComponent(
            Actor actor, Visibility visibility, VisibilityProvenance provenance, StylesheetPackage containingPackage, StylesheetPackage declaringPackage) {
        Component c;
        if (actor instanceof Mode) {
            c = new M();
        } else {
            c = new Component();
        }
        c.actor = actor;
        c.visibility = visibility;
        c.provenance = provenance;
        c.containingPackage = containingPackage;
        c.declaringPackage = declaringPackage;
        return c;
    }

    /**
     * Get the component's binding vector; that is the list of external references to other components
     *
     * @return the binding vector, a list of component bindings. These are identified by a binding
     *         slot number held with the individual instruction (such as a call-template instruction or a global
     *         variable reference) that contains the external component reference.
     */

    public List<ComponentBinding> getComponentBindings() {
        return bindings;
    }

    /**
     * Set the component's binding vector; that is the list of external references to other components
     *
     * @param bindings the binding vector, a list of component bindings. These are identified by a binding
     *                 slot number held with the individual instruction (such as a call-template instruction or a global
     *                 variable reference) that contains the external component reference.
     */

    public void setComponentBindings(List<ComponentBinding> bindings) {
        this.bindings = bindings;
    }

    /**
     * Set the visibility of the component, and say whether it is explicit or defaulted
     * @param visibility the visibility of the component
     * @param provenance where the visibility property came from
     */

    public void setVisibility(Visibility visibility, VisibilityProvenance provenance) {
        this.visibility = visibility;
        this.provenance = provenance;
    }

    /**
     * Get the visibility of the component
     *
     * @return the component's visibility. In the declaring package this will be the original
     *         declared or exposed visibility; in a using package, it will be the visibility of the component
     *         within that package.
     */

    public Visibility getVisibility() {
        return visibility;
    }

    /**
     * Determine whether the visibility of the component is due to an explicit visibility attribute on the component
     * declaration
     * @return the origin of the visibility information
     */

    public VisibilityProvenance getVisibilityProvenance() {
        return provenance;
    }

    /**
     * Ask whether this is a hidden component derived from an abstract component with no
     * implementation
     * @return true if this component is hidden, and its base component is abstract
     */

    public boolean isHiddenAbstractComponent() {
        return visibility == Visibility.HIDDEN && baseComponent != null &&
                baseComponent.getVisibility() == Visibility.ABSTRACT;
    }

    /**
     * Get the actor (for example a compiled template, function, or variable) that is executed
     * when this component is called
     *
     * @return the code forming the implementation of this component
     */

    public Actor getActor() {
        return actor;
    }

    /**
     * Get the declaring package of this component
     *
     * @return the package in which the code of the component was originally declared
     */

    public StylesheetPackage getDeclaringPackage() {
        return declaringPackage;
    }

    /**
     * Get the containing package of this component
     *
     * @return the package that contains this (version of the) component
     */

    public StylesheetPackage getContainingPackage() {
        return containingPackage;
    }

    /**
     * Get the component from which this one is derived
     * @return the component from which this one is derived. This is set when the component appears in the
     * package as a result of xsl:use-package; a component C in the used package is modified to create a
     * component D in the using package, and D.getBaseComponent() returns C. The value will be null
     * in the case of a component whose declaring package is the same as its containing package, that is,
     * an "original" component (including an overriding component)
     */

    public Component getBaseComponent() {
        return baseComponent;
    }

    /**
     * Set the component from which this one is derived
     *
     * @param original the component from which this one is derived. This is set when the component appears in the
     * package as a result of xsl:use-package; a component C in the used package is modified to create a
     * component D in the using package, and D.getBaseComponent() returns C. The value will be null
     * in the case of a component whose declaring package is the same as its containing package, that is,
     * an "original" component (including an overriding component)
     */

    public void setBaseComponent(Component original) {
        baseComponent = original;
    }

    public void export(ExpressionPresenter out, Map<Component, Integer> componentIdMap,
                       Map<StylesheetPackage, Integer> packageIdMap) throws XPathException {
        out.startElement("co");
        int id = obtainComponentId(this, componentIdMap);
        out.emitAttribute("id", ""+id);
        if (provenance != VisibilityProvenance.DEFAULTED) {
            out.emitAttribute("vis", getVisibility().toString());
        }
        String refs = listComponentReferences(componentIdMap);
        out.emitAttribute("binds", refs);
        if (baseComponent != null && getActor() == baseComponent.getActor()) {
            int baseId = obtainComponentId(baseComponent, componentIdMap);
            out.emitAttribute("base", ""+baseId);
            out.emitAttribute("dpack", packageIdMap.get(declaringPackage)+"");
        } else {
            actor.export(out);
        }
        out.endElement();
    }

    public String listComponentReferences(Map<Component, Integer> componentIdMap) {
        FastStringBuffer fsb = new FastStringBuffer(128);
        for (ComponentBinding ref : getComponentBindings()) {
            Component target = ref.getTarget();
            int targetId = obtainComponentId(target, componentIdMap);
            if (fsb.length() != 0) {
                fsb.append(" ");
            }
            fsb.append("" + targetId);
        }
        return fsb.toString();
    }

    private int obtainComponentId(Component component, Map<Component, Integer> componentIdMap) {
        Integer id = componentIdMap.get(component);
        if (id == null) {
            id = componentIdMap.size();
            componentIdMap.put(component, id);
        }
        return id;
    }

    public static class M extends Component {

        /**
         * Get the actor (in this case a Mode) that is executed
         * when this component is called
         *
         * @return the code forming the implementation of this component
         */
        @Override
        public Mode getActor() {
            return (Mode)super.getActor();
        }

        public void setActor(Mode m) {
            this.actor = m;
        }
    }

    public int getComponentKind() {
        if (actor instanceof NamedTemplate) {
            return StandardNames.XSL_TEMPLATE;
        } else if (actor instanceof GlobalVariable) {
            return StandardNames.XSL_VARIABLE;
        } else if (actor instanceof Function) {
            return StandardNames.XSL_FUNCTION;
        } else if (actor instanceof AttributeSet) {
            return StandardNames.XSL_ATTRIBUTE_SET;
        } else if (actor instanceof Mode) {
            return StandardNames.XSL_MODE;
        } else {
            return -1;
        }
    }


}

