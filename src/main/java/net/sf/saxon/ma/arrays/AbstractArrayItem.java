////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.arrays;

import net.sf.saxon.functions.SaxonDeepEqual;
import net.sf.saxon.expr.*;
import net.sf.saxon.expr.sort.AtomicMatcher;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.om.*;
import net.sf.saxon.query.AnnotationList;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trace.ExpressionPresenter;
import net.sf.saxon.trans.UncheckedXPathException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.*;
import net.sf.saxon.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract implementation of XDM array items, containing methods that can be implemented generically.
 */
public abstract class AbstractArrayItem extends ArrayItem {

    // TODO: ArrayItem is itself now an a abstract class, can this be merged?

    private SequenceType memberType = null; // computed on demand

    /**
     * Get the roles of the arguments, for the purposes of streaming
     *
     * @return an array of OperandRole objects, one for each argument
     */
    @Override
    public OperandRole[] getOperandRoles() {
        return new OperandRole[]{OperandRole.SINGLE_ATOMIC};
    }

    /**
     * Atomize the item.
     *
     * @return the result of atomization
     * @throws XPathException if atomization is not allowed for this kind of item
     */
    @Override
    public AtomicSequence atomize() throws XPathException {
        List<AtomicValue> list = new ArrayList<>(arrayLength());
        for (GroundedValue seq : members()) {
            SequenceTool.supply(seq.iterate(), (ItemConsumer<? super Item>) item -> {
                AtomicSequence atoms = item.atomize();
                for (AtomicValue atom : atoms) {
                    list.add(atom);
                }
            });
        }
        return new AtomicArray(list);
    }


    /**
     * Get the function annotations (as defined in XQuery). Returns an empty
     * list if there are no function annotations.
     *
     * @return the function annotations
     */

    @Override
    public AnnotationList getAnnotations() {
        return AnnotationList.EMPTY;
    }

    /**
     * Get the item type of the function item
     *
     * @return the function item's type
     */

    @Override
    public FunctionItemType getFunctionItemType() {
        return ArrayItemType.ANY_ARRAY_TYPE;
    }

    /**
     * Get the name of the function, or null if it is anonymous
     *
     * @return the function name, or null for an anonymous function (which this one is)
     */


    @Override
    public StructuredQName getFunctionName() {
        return null;
    }

    /**
     * Get a description of this function for use in error messages. For named functions, the description
     * is the function name (as a lexical QName). For others, it might be, for example, "inline function",
     * or "partially-applied ends-with function".
     *
     * @return a description of the function for use in error messages
     */
    @Override
    public String getDescription() {
        return "array";
    }

    /**
     * Get the arity of the function
     *
     * @return the number of arguments in the function signature (in this case, 1 (one))
     */

    @Override
    public int getArity() {
        return 1;
    }

    /**
     * Prepare an XPathContext object for evaluating the function
     *
     * @param callingContext the XPathContext of the function calling expression
     * @param originator identifies the location of the caller for diagnostics
     * @return a suitable context for evaluating the function (which may or may
     * not be the same as the caller's context)
     */
    @Override
    public XPathContext makeNewContext(XPathContext callingContext, ContextOriginator originator) {
        return callingContext;
    }

    /**
     * Invoke the array in its role as a function
     *
     * @param context the XPath dynamic evaluation context
     * @param args    the actual arguments to be supplied (a single integer)
     * @return the result of invoking the function
     * @throws XPathException if a dynamic error occurs within the function
     */

    @Override
    public GroundedValue call(XPathContext context, Sequence[] args) throws XPathException {
        IntegerValue subscript = (IntegerValue) args[0].head();
        return get(ArrayFunctionSet.checkSubscript(subscript, arrayLength()) - 1);
    }

    /**
     * Test whether this array is deep-equal to another array,
     * under the rules of the deep-equal function
     *
     * @param other    the other function item
     * @param context  the dynamic evaluation context
     * @param comparer the object to perform the comparison
     * @param flags    options for how the comparison is performed
     * @return true if the two array items are deep-equal; false if they are not deep equal
     *         or if the other item is not an array
     * @throws XPathException if the comparison cannot be performed
     */


    @Override
    public boolean deepEquals(FunctionItem other, XPathContext context, AtomicMatcher comparer, int flags) throws XPathException {
        if (other instanceof ArrayItem) {
            ArrayItem that = (ArrayItem) other;
            if (this.arrayLength() != that.arrayLength()) {
                return false;
            }
            for (int i = 0; i < this.arrayLength(); i++) {
                if (!SaxonDeepEqual.deepEqual(this.get(i).iterate(), that.get(i).iterate(), comparer, context, flags)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean deepEqual40(FunctionItem other, XPathContext context, DeepEqual.DeepEqualOptions options) throws XPathException {
        if (other instanceof ArrayItem) {
            ArrayItem that = (ArrayItem) other;
            if (this.arrayLength() != that.arrayLength()) {
                return false;
            }
            for (int i = 0; i < this.arrayLength(); i++) {
                if (!DeepEqual.deepEqual(this.get(i).iterate(), that.get(i).iterate(), context, options)) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get the effective boolean value of this sequence
     *
     * @return the effective boolean value
     * @throws XPathException if the sequence has no effective boolean value (for example a sequence of two integers)
     */


    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException("Effective boolean value is not defined for arrays", "FORG0006");
    }

    /**
     * Get the value of the item as a string. For arrays, there is no string value,
     * so an exception is thrown.
     *
     * @return the string value of the item
     * @throws UncheckedXPathException if the item is an array (an unchecked exception
     *                                       is used here to avoid introducing exception handling to a large number of paths where it is not
     *                                       needed)
     * @since 8.4
     */

    @Override
    public UnicodeString getUnicodeStringValue() {
        throw new UncheckedXPathException(new XPathException("An array has no string value", "FOTY0014"));
    }

    /**
     * Output information about this function item to the diagnostic explain() output
     */
    @Override
    public void export(ExpressionPresenter out) throws XPathException {
        out.startElement("array");
        out.emitAttribute("size", arrayLength() + "");
        for (GroundedValue mem : members()) {
            Literal.exportValue(mem, out);
        }
        out.endElement();
    }

    @Override
    public boolean isTrustedResultType() {
        return false;
    }


    /**
     * Output a string representation of the array, suitable for diagnostics
     */

    public String toString() {
        StringBuilder buffer = new StringBuilder(256);
        buffer.append("[");
        for (GroundedValue seq : members()) {
            if (buffer.length() > 1) {
                buffer.append(", ");
            }
            buffer.append(seq.toString());
        }
        buffer.append("]");
        return buffer.toString();
    }

    /**
     * Get the lowest common item type of the members of the array
     * @param th the type hierarchy
     * @return the most specific type to which all the members belong.
     */

    @Override
    public SequenceType getMemberType(TypeHierarchy th) {
        //try {
            if (memberType == null) {
                if (isEmpty()) {
                    memberType = SequenceType.one(ErrorType.getInstance());
                } else {
                    ItemType contentType = null;
                    int contentCard = StaticProperty.EXACTLY_ONE;
                    for (GroundedValue s : members()) {
                        if (contentType == null) {
                            contentType = SequenceTool.getItemType(s, th);
                            contentCard = SequenceTool.getCardinality(s);
                        } else {
                            contentType = Type.getCommonSuperType(contentType, SequenceTool.getItemType(s, th));
                            contentCard = Cardinality.union(contentCard, SequenceTool.getCardinality(s));
                        }
                    }
                    memberType = SequenceType.makeSequenceType(contentType, contentCard);
                }
            }
            return memberType;
//        } catch (XPathException e) {
//            return SequenceType.ANY_SEQUENCE;
//        }
    }

}

