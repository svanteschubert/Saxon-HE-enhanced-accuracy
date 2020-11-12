////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

import net.sf.saxon.om.StandardNames;
import net.sf.saxon.om.StructuredQName;

/**
 * The symbolic name of a component consists of the component kind (e.g. function, template, attribute set),
 * the QName acting as the name of the template, function etc, and in the case of functions, the arity
 */
public class SymbolicName {

    private int kind;
    private StructuredQName name;

    /**
     * Create a symbolic name for a component other than a function.
     * @param kind the component kind, for example {@link StandardNames#XSL_TEMPLATE}
     * @param name the QName that is the "name" of the component
     */

    public SymbolicName(int kind, StructuredQName name) {
        this.kind = kind;
        this.name = name;
    }

    /**
     * Returns a hash code value for the object.
     */
    @Override
    public int hashCode() {
        return kind << 16 ^ name.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof SymbolicName
                && ((SymbolicName) obj).kind == this.kind
                && ((SymbolicName) obj).name.equals(this.name);
    }

    /**
     * Get the kind of component, for example {@link StandardNames#XSL_FUNCTION} or {@link StandardNames#XSL_VARIABLE}
     * @return the kind of component identified by this symbolic name
     */

    public int getComponentKind() {
        return kind;
    }

    /**
     * Get the QName part of the symbolic name of the component
     * @return the QName part of the name
     */

    public StructuredQName getComponentName() {
        return name;
    }

    /**
     * Get the name as a string.
     * @return a string typically in the form "template p:my-template" or "function f:my-function#2"
     */

    public String toString() {
        //noinspection UnnecessaryParentheses
        return StandardNames.getLocalName(kind) + " " +
                name.getDisplayName();
    }

    /**
     * Get a short name suitable for use in messages
     */

    public String getShortName() {
        return name.getDisplayName();
    }

    /**
     * Subclass of SymbolicName used for function names (including the arity)
     */

    public static class F extends SymbolicName {
        int arity;

        /**
         * Create a symbolic name for a function.
         *
         * @param name  the QName that is the "name" of the component
         * @param arity the number of arguments
         */

        public F(StructuredQName name, int arity) {
            super(StandardNames.XSL_FUNCTION, name);
            this.arity = arity;
        }

        /**
         * Get the arity, in the case of function components
         *
         * @return in the case of a function, the arity, otherwise -1.
         */

        public int getArity() {
            return arity;
        }

        /**
         * Returns a hash code value for the object.
         */
        @Override
        public int hashCode() {
            return super.hashCode() ^ arity;
        }

        /**
         * Indicates whether some other object is "equal to" this one.
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof SymbolicName.F
                    && super.equals(obj)
                    && ((SymbolicName.F) obj).arity == this.arity;
        }

        /**
         * Get the name as a string.
         *
         * @return a string typically in the form "template p:my-template" or "function f:my-function#2"
         */

        public String toString() {
            //noinspection UnnecessaryParentheses
            return super.toString() + "#" + arity;
        }

        /**
         * Get a short name suitable for use in messages
         */
        @Override
        public String getShortName() {
            return super.getShortName() + "#" + arity;
        }
    }
}

