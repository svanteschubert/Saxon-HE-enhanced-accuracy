////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trace;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.Logger;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.trans.SimpleMode;

import java.util.Map;

/**
 * A class which implements efficient and thread-safe multi-cast event
 * dispatching for the TraceListener evants.
 */
public class TraceEventMulticaster implements TraceListener {

    protected final TraceListener a, b;

    /**
     * Creates an event multicaster instance which chains listener-a
     * with listener-b.
     *
     * @param a listener-a
     * @param b listener-b
     */
    protected TraceEventMulticaster(TraceListener a, TraceListener b) {
        this.a = a;
        this.b = b;
    }

    @Override
    public void setOutputDestination(Logger stream) {
        a.setOutputDestination(stream);
        b.setOutputDestination(stream);
    }

    /**
     * Removes a listener from this multicaster and returns the
     * resulting multicast listener.
     *
     * @param oldl the listener to be removed
     */
    /*@Nullable*/
    protected TraceListener remove(TraceListener oldl) {
        if (oldl == a) {
            return b;
        }
        if (oldl == b) {
            return a;
        }
        TraceListener a2 = removeInternal(a, oldl);
        TraceListener b2 = removeInternal(b, oldl);
        if (a2 == a && b2 == b) {
            return this;    // it's not here
        }
        return addInternal(a2, b2);
    }

    /**
     * Called at start
     */

    @Override
    public void open(Controller controller) {
        a.open(controller);
        b.open(controller);
    }

    /**
     * Called at end
     */

    @Override
    public void close() {
        a.close();
        b.close();
    }


    /**
     * Called when an element of the stylesheet gets processed
     */
    @Override
    public void enter(Traceable element, Map<String, Object> properties, XPathContext context) {
        a.enter(element, properties, context);
        b.enter(element, properties, context);
    }

    /**
     * Called after an element of the stylesheet got processed
     * @param element
     */
    @Override
    public void leave(Traceable element) {
        a.leave(element);
        b.leave(element);
    }

    /**
     * Called when an item becomes current
     */
    @Override
    public void startCurrentItem(Item item) {
        a.startCurrentItem(item);
        b.startCurrentItem(item);
    }

    /**
     * Called when an item ceases to be the current item
     */
    @Override
    public void endCurrentItem(Item item) {
        a.endCurrentItem(item);
        b.endCurrentItem(item);
    }
    /**
     * Called at the start of a rule search
     */
    @Override
    public void startRuleSearch() {
        a.startRuleSearch();
        b.startRuleSearch();
    }

    /**
     * Called at the end of a rule search
     * @param rule the rule (or possible built-in ruleset) that has been selected
     * @param mode
     * @param item
     */
    public void endRuleSearch(Object rule, SimpleMode mode, Item item) {
        a.endRuleSearch(rule, mode, item);
        b.endRuleSearch(rule, mode, item);
    }

    /**
     * Adds trace-listener-a with trace-listener-b and
     * returns the resulting multicast listener.
     *
     * @param a trace-listener-a
     * @param b trace-listener-b
     */
    public static TraceListener add(TraceListener a, TraceListener b) {
        return (TraceListener) addInternal(a, b);
    }

    /**
     * Removes the old trace-listener from trace-listener-l and
     * returns the resulting multicast listener.
     *
     * @param l    trace-listener-l
     * @param oldl the trace-listener being removed
     */
    public static TraceListener remove(TraceListener l, TraceListener oldl) {
        return (TraceListener) removeInternal(l, oldl);
    }

    /**
     * Returns the resulting multicast listener from adding listener-a
     * and listener-b together.
     * If listener-a is null, it returns listener-b;
     * If listener-b is null, it returns listener-a
     * If neither are null, then it creates and returns
     * a new EventMulticaster instance which chains a with b.
     *
     * @param a event listener-a
     * @param b event listener-b
     */
    protected static TraceListener addInternal(TraceListener a, TraceListener b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new TraceEventMulticaster(a, b);
    }

    /**
     * Returns the resulting multicast listener after removing the
     * old listener from listener-l.
     * If listener-l equals the old listener OR listener-l is null,
     * returns null.
     * Else if listener-l is an instance of SaxonEventMulticaster,
     * then it removes the old listener from it.
     * Else, returns listener l.
     *
     * @param l    the listener being removed from
     * @param oldl the listener being removed
     */

    protected static TraceListener removeInternal(TraceListener l, TraceListener oldl) {
        if (l == oldl || l == null) {
            return null;
        } else if (l instanceof TraceEventMulticaster) {
            return ((TraceEventMulticaster) l).remove(oldl);
        } else {
            return l;        // it's not here
        }
    }

}

