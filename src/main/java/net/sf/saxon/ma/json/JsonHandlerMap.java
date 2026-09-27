////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.ma.json;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.ma.map.MapFunctionSet;
import net.sf.saxon.ma.map.StringMapBuilder;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.str.UnicodeString;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Event handler for the JSON parser which constructs a structure of maps and arrays
 * representing the content of the JSON text.
 */
public class JsonHandlerMap extends JsonHandler {
    final JsonParser parser;
    Stack<Object> stack;

    protected Stack<UnicodeString> keys;
    private GroundedValue nullRepresentation = EmptySequence.INSTANCE;
    private final int flags;
    private MapFunctionSet.OnDuplicatesAction duplicatesCombiner = null;

    public JsonHandlerMap(XPathContext context, JsonParser parser, int flags) {
        setContext(context);
        stack = new Stack<>();
        keys = new Stack<>();
        this.parser = parser;
        this.flags = flags;
        if ((flags & JsonParser.DUPLICATES_FIRST) != 0) {
            duplicatesCombiner = (a, b, cxt) -> a;
        } else if ((flags & JsonParser.DUPLICATES_LAST) != 0) {
            duplicatesCombiner = (a, b, cxt) -> b;
        }
        escape = (flags & JsonParser.ESCAPE) != 0;
        charChecker = context.getConfiguration().getValidCharacterChecker();
    }

    public void setNullRepresentation(GroundedValue representation) {
        this.nullRepresentation = representation;
    }

    @Override
    public Sequence getResult() {
        return (Sequence)stack.peek();
    }

    /**
     * Set the key to be written for the next entry in an object/map
     *
     * @param key the key for the entry (null implies no key) in unescaped form (backslashes,
     *            if present, do not signal an escape sequence)
     * @return true if the key is already present in the map, false if it is not
     */
    @Override
    public boolean setKey(UnicodeString key) {
        this.keys.push(key);
        return false;
    }

    /**
     * Open a new array
     *
     */
    @Override
    public void startArray() {
        List<GroundedValue> memberList = new ArrayList<>();
        ArrayItem arrayItem = new SimpleArrayItem(memberList);
        stack.push(arrayItem);
    }

    /**
     * Close the current array
     */
    @Override
    public void endArray() throws XPathException {
        ArrayItem arrayItem = (ArrayItem) stack.pop();
        if (stack.empty()) {
            stack.push(arrayItem); // the end
        } else {
            writeItem(arrayItem);
        }
    }

    /**
     * Start a new object/map
     */
    @Override
    public void startMap() {
        StringMapBuilder mapBuilder = new StringMapBuilder(40);
        mapBuilder.setCombiner(duplicatesCombiner);
        stack.push(mapBuilder);
    }

    /**
     * Close the current object/map
     */
    @Override
    public void endMap() throws XPathException {
        StringMapBuilder map = (StringMapBuilder) stack.pop();
        if (stack.empty()) {
            stack.push(map.getCompletedMap()); // the end
        } else {
            writeItem(map.getCompletedMap());
        }
    }

    /**
     * Write an item into the current map, with the preselected key
     * @param val   the value/map to be written
     */
    private void writeItem(GroundedValue val) throws XPathException {
        if (stack.empty()) {
            stack.push(val);
        } else if (stack.peek() instanceof ArrayItem) {
            SimpleArrayItem array = (SimpleArrayItem) stack.peek();
            array.getMembers().add(val.materialize());
        } else {
            StringMapBuilder map = (StringMapBuilder) stack.peek();
            map.put(keys.pop(), val);
        }
    }

    /**
     * Write a numeric value
     *  @param asString the string representation of the value
     * @param parsedValue the double representation of the value
     */
    @Override
    public void writeNumeric(String asString, Item parsedValue) throws XPathException {
        writeItem(parsedValue);
    }

    /**
     * Write a string value
     *
     * @param val The string to be written (which may or may not contain JSON escape sequences, according to the
     * options that were set)
     * @throws XPathException if a dynamic error occurs
     */
    @Override
    public void writeString(UnicodeString val) throws XPathException {
        //writeItem(new StringValue(reEscape(val)));
        writeItem(new StringValue(val));
    }

    /**
     * Write a boolean value
     *
     * @param value the boolean value to be written
     */
    @Override
    public void writeBoolean(boolean value) throws XPathException {
        writeItem(BooleanValue.get(value));
    }

    /**
     * Write a null value
     */
    @Override
    public void writeNull() throws XPathException {
        writeItem(nullRepresentation);
    }


}
