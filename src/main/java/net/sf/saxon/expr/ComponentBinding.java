////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;


import net.sf.saxon.trans.SymbolicName;

/**
 * A ComponentBinding is a reference from one component to another; for example a variable
 * reference or function call. ComponentBindings are held in the binding vector of the calling
 * component, and are separate from the instruction/expression that contains the reference, to provide
 * a level of indirection; this means that when a component is re-bound in a using package, for example
 * to call overriding versions of templates or functions called from the component, the compiled code of
 * the calling component does not need to be changed, only the contents of the binding vector.
 *
 * <p>The ComponentBinding class is immutable.</p>
 */

public class ComponentBinding {

    private SymbolicName symbolicName;
    private Component target;

    /**
     * Create a ComponentBinding
     *
     * @param name the symbolic name of the component that is the target of this binding. This includes
     *             the component kind (e.g. function, template, variable), its name, and in the case of functions, its
     *             arity.
     * @param target  the target of the binding
     */

    public ComponentBinding(SymbolicName name, Component target) {
        this.symbolicName = name;
        this.target = target;
    }

    /**
     * Get the symbolic name of the component binding
     *
     * @return the symbolic name of the component that is the target of this binding. This includes
     *         the component kind (e.g. function, template, variable), its name, and in the case of functions, its
     *         arity.
     */

    public SymbolicName getSymbolicName() {
        return symbolicName;
    }

    /**
     * Get the target of the component binding, for example a template, function, or global variable.
     *
     * @return the target of the component binding
     */

    public Component getTarget() {
        return target;
    }


}

