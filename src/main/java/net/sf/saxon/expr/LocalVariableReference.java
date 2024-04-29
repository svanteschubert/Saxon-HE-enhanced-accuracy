////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2023 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.expr.elab.*;
import net.sf.saxon.expr.parser.RebindingMap;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;

/**
 * Variable reference: a reference to a local variable. This subclass of VariableReference
 * bypasses the Binding object to get the value directly from the relevant slot in the local
 * stackframe.
 */

public class LocalVariableReference extends VariableReference {

    int slotNumber = -999;

    /**
     * Create a local variable reference. The binding and slot number will be supplied later
     * @param name the name of the local variable
     */

    public LocalVariableReference(StructuredQName name) {
        super(name);
    }

    /**
     * Create a LocalVariableReference bound to a given Binding
     *
     * @param binding the binding (that is, the declaration of this local variable)
     */

    public LocalVariableReference(LocalBinding binding) {
        super(binding);
    }


    /**
     * Create a clone copy of this VariableReference
     *
     * @return the cloned copy
     * @param rebindings a mutable list of (old binding, new binding) pairs
     *                   that is used to update the bindings held in any
     *                   local variable references that are copied.
     */

    /*@NotNull*/
    @Override
    public Expression copy(RebindingMap rebindings) {
        if (binding == null) {
            throw new UnsupportedOperationException("Cannot copy a variable reference whose binding is unknown");
        }
//        if (slotNumber < 0) {
//            // slot numbers have not yet been allocated. This is messy. For the time being, the safest
//            // thing is to reuse the existing variable reference, which usually works, rather than copying
//            // it, which will almost certainly fail. See XSLT streaming test case si-attribute-053
//            return this;
//        }
        LocalVariableReference ref = new LocalVariableReference(getVariableName());
        ref.copyFrom(this);
        ref.slotNumber = slotNumber;
        Binding newBinding = rebindings.get(binding);
        if (newBinding != null) {
            ref.binding = newBinding;
        }
        ref.binding.addReference(ref, isInLoop());
        return ref;
    }

    public void setBinding(LocalBinding binding) {
        this.binding = binding;
    }

    @Override
    public LocalBinding getBinding() {
        return (LocalBinding)super.getBinding();
    }

    /**
     * Set the slot number for this local variable, that is, its position in the local stack frame
     *
     * @param slotNumber the slot number to be used
     */

    public void setSlotNumber(int slotNumber) {
        this.slotNumber = slotNumber;
    }

    /**
     * Get the slot number allocated to this local variable
     *
     * @return the slot number
     */

    public int getSlotNumber() {
        return slotNumber;
    }

    /**
     * Return the value of the variable
     *
     * @param c the XPath dynamic context
     * @return the value of the variable
     * @throws XPathException if any dynamic error occurs while evaluating the variable
     */

    /*@NotNull*/
    @Override
    public Sequence evaluateVariable(XPathContext c) throws XPathException {
        try {
            return c.getStackFrame().slots[slotNumber];
        } catch (ArrayIndexOutOfBoundsException err) {
            if (slotNumber == -999) {
                if (binding != null) {
                    try {
                        slotNumber = getBinding().getLocalSlotNumber();
                        return c.getStackFrame().slots[slotNumber];
                    } catch (ArrayIndexOutOfBoundsException err2) {
                        // fall through
                    }
                }
                throw new ArrayIndexOutOfBoundsException("Local variable $" + getDisplayName() + " has not been allocated a stack frame slot");
            } else {
                int actual = c.getStackFrame().slots.length;
                throw new ArrayIndexOutOfBoundsException("Local variable $" + getDisplayName() + " uses slot "
                        + slotNumber + " but " + (actual == 0 ? "no" : "only " + c.getStackFrame().slots.length) + " slots" +
                        " are allocated on the stack frame");
            }
        }
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
        return "locVarRef";
    }


    /**
     * Replace this VariableReference where appropriate by a more efficient implementation. This
     * can only be done after all slot numbers are allocated. The efficiency is gained by binding the
     * VariableReference directly to a local or global slot, rather than going via the Binding object
     *
     * @param parent the parent expression of this variable reference
     */

//    public void refineVariableReference(Expression parent) {
//        // no-op
//    }

    /**
     * Make an elaborator for this expression
     *
     * @return a suitable elaborator
     */

    @Override
    public Elaborator getElaborator() {
        return new LocalVariableReferenceElaborator();
    }

    /**
     * Elaborator for a local variable reference, for example {@code $var}.
     */

    public static class LocalVariableReferenceElaborator extends PullElaborator {

        @Override
        public void setExpression(Expression expr) {
            super.setExpression(expr);
            if (((LocalVariableReference)expr).getSlotNumber() < 0) {
                throw new IllegalStateException("Can't elaborate a local variable reference before slot numbers have been allocated");
            }
        }

        @Override
        public SequenceEvaluator eagerly() {
            LocalVariableReference varRef = (LocalVariableReference) getExpression();
            int slot = varRef.getSlotNumber();
            return new LocalVariableEvaluator(slot);
        }

        @Override
        public SequenceEvaluator lazily(boolean repeatable, boolean lazyEvaluationRequired) {
            return eagerly();
        }

        @Override
        public PullEvaluator elaborateForPull() {
            LocalVariableReference varRef = (LocalVariableReference)getExpression();
            int slot = varRef.getSlotNumber();
            return context -> {
                try {
                    return context.evaluateLocalVariable(slot).iterate();
                } catch (UncheckedXPathException e) {
                    throw e.getXPathException()
                            .maybeWithLocation(getExpression().getLocation())
                            .maybeWithContext(context);
                }
            };
        }

        @Override
        public PushEvaluator elaborateForPush() {
            LocalVariableReference varRef = (LocalVariableReference) getExpression();
            int slot = varRef.getSlotNumber();
            return (out, context) -> {
                try {
                    SequenceIterator value = context.evaluateLocalVariable(slot).iterate();
                    for (Item it; (it = value.next()) != null; ) {
                        out.append(it);
                    }
                    return null;
                } catch (UncheckedXPathException e) {
                    throw e.getXPathException()
                            .maybeWithLocation(getExpression().getLocation())
                            .maybeWithContext(context);
                } catch (XPathException e) {
                    throw e.maybeWithLocation(getExpression().getLocation())
                            .maybeWithContext(context);
                }
            };
        }

        @Override
        public ItemEvaluator elaborateForItem() {
            LocalVariableReference varRef = (LocalVariableReference) getExpression();
            int slot = varRef.getSlotNumber();
            return context -> {
                try {
                    return context.evaluateLocalVariable(slot).head();
                } catch (UncheckedXPathException e) {
                    throw e.getXPathException()
                            .maybeWithLocation(getExpression().getLocation())
                            .maybeWithContext(context);
                } catch (XPathException e) {
                    throw e.maybeWithLocation(getExpression().getLocation())
                            .maybeWithContext(context);
                }
            };
        }

    }
}
