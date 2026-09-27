// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.type;

import net.sf.saxon.lib.ConversionRules;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.StandardNames;
import net.sf.saxon.str.StringTool;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.Err;
import net.sf.saxon.type.coercion.CoercionPlan;
import net.sf.saxon.type.coercion.EnumUnionCoercionPlan;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EnumerationUnionType extends LocalUnionType {

    final HashMap<UnicodeString, SingletonEnumType> dictionary;

    public EnumerationUnionType(List<SingletonEnumType> singletonEnums) {
        super(new ArrayList<ItemType>(singletonEnums));
        dictionary = new HashMap<>(singletonEnums.size());
        for (SingletonEnumType type : singletonEnums) {
            dictionary.put(type.getValue(), type);
        }
    }

    /**
     * Create an enumeration type permitting a defined set of values
     *
     * @param values the values to be permitted. Must be Latin1 strings.
     */
    public static EnumerationUnionType of(String... values) {
        List<SingletonEnumType> types = new ArrayList<>();
        for (String s : values) {
            types.add(new SingletonEnumType(StringTool.fromLatin1(s)));
        }
        return new EnumerationUnionType(types);
    }

    @Override
    public boolean isAtomicType() {
        return true;
    }

    @Override
    public double getDefaultPriority() {
        return 0.25;
    }

    /**
     * Ask whether this type is an ID type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:ID: that is, it includes types derived
     * from ID by restriction, list, or union. Note that for a node to be treated
     * as an ID in XSD 1.0, its typed value must be a *single* atomic value of type ID; the type of the
     * node, however, can still allow a list or union. This changes in XSD 1.1, where a list of IDs is allowed.
     */

    @Override
    public boolean isIdType() {
        return false;
    }

    /**
     * Ask whether this type is an IDREF or IDREFS type. This is defined to be any simple type
     * who typed value may contain atomic values of type xs:IDREF: that is, it includes types derived
     * from IDREF or IDREFS by restriction, list, or union
     */

    public boolean isIdRefType() {
        return false;
    }

    /**
     * Test whether this type is namespace sensitive, that is, if a namespace context is needed
     * to translate between the lexical space and the value space. This is true for types derived
     * from, or containing, QNames and NOTATIONs
     *
     * @return true if any of the member types is namespace-sensitive, or if namespace sensitivity
     * cannot be determined because there are components missing from the schema.
     */

    @Override
    public boolean isNamespaceSensitive() {
        return false;
    }

    /**
     * Check whether a given input string is valid according to this SimpleType
     *
     * @param value      the input string to be checked
     * @param nsResolver a namespace resolver used to resolve namespace prefixes if the type
     *                   is namespace sensitive. The value supplied may be null; in this case any namespace-sensitive
     *                   content will throw an UnsupportedOperationException.
     * @param rules      the configuration-wide conversion rules
     * @return null if validation succeeds; return a ValidationFailure describing the validation failure
     * if validation fails
     * @throws UnsupportedOperationException if the type is namespace-sensitive and no namespace
     *                                       resolver is supplied
     */

    /*@Nullable*/
    @Override
    public ValidationFailure validateContent(UnicodeString value, NamespaceResolver nsResolver, ConversionRules rules) {
        if (dictionary.containsKey(value)) {
            return null;
        }
        return new ValidationFailure(
                "Value " + Err.wrap(value, Err.VALUE) +
                        " does not match any member of enumeration type " + toString());

    }

    /**
     * Get the typed value corresponding to a given string value, assuming it is
     * valid against this type
     *
     * @param value    the string value
     * @param resolver a namespace resolver used to resolve any namespace prefixes appearing
     *                 in the content of values. Can supply null, in which case any namespace-sensitive content
     *                 will be rejected.
     * @param rules    the conversion rules from the configuration
     * @return the atomic sequence comprising the typed value. The objects
     * returned by this SequenceIterator will all be of type {@link net.sf.saxon.value.AtomicValue},
     * @throws ValidationException if the supplied value is not in the lexical space of the data type
     */

    public StringValue getTypedValue(UnicodeString value, NamespaceResolver resolver, ConversionRules rules)
            throws ValidationException {
        SingletonEnumType type = dictionary.get(value);
        if (type == null) {
            throw new ValidationFailure(
                    "Value " + Err.wrap(value, Err.VALUE) +
                            " does not match any member of enumeration type " + getDescription(), "XPTY0004")
                    .makeException();
        } else {
            return type.getInstance();
        }
    }

    /**
     * Test whether a given item conforms to this type
     *
     * @param item The item to be tested
     * @return true if the item is an instance of this type; false otherwise
     */
    @Override
    public boolean matches(Item item) {
        if (item instanceof StringValue && BuiltInAtomicType.STRING.matches(item)) {
            return dictionary.containsKey(item.getUnicodeStringValue());
        } else {
            return false;
        }
    }

    /**
     * Method defined in ItemType: get a primitive supertype in the ItemType type hierarchy
     *
     * @return BuiltInAtomicType.ANY_ATOMIC
     */

    /*@NotNull*/
    @Override
    public AtomicType getPrimitiveItemType() {
        return BuiltInAtomicType.STRING;
    }

    /**
     * Method defined in ItemType: get a primitive supertype in the ItemType type hierarchy
     *
     * @return StandardNames.XS_ANY_ATOMIC_TYPE
     */

    @Override
    public int getPrimitiveType() {
        return StandardNames.XS_STRING;
    }

    /**
     * Get the coercion plan for use when this type is the required type for (say) coercion
     * of arguments in a function call
     *
     * @param version the XPath language version (40 or 31)
     */
    @Override
    public CoercionPlan getCoercionPlan(int version) {
        return EnumUnionCoercionPlan.INSTANCE;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("enum(");
        for (ItemType et : getMemberTypes()) {
            sb.append('"').append(((SingletonEnumType)et).getValue()).append('"').append(',');
        }
        sb.setCharAt(sb.length()-1, ')');
        return sb.toString();
    }

    /**
     * Produce a string representation of the type name. If the type is anonymous, an internally-allocated
     * type name will be returned.
     *
     * @return the name of the atomic type in the form Q{uri}local
     */

    public String toString() {
        return getDescription();
    }
}

