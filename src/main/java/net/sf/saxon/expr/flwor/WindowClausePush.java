////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.event.Outputter;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement a sliding or tumbling window clause of a FLWOR expression in tuple-push mode. The entire window processing
 * is activated once for each input tuple, and it generates one output tuple for each identified window.
 */
public class WindowClausePush extends TuplePush {

    private WindowClause windowClause;
    private TuplePush destination;
    /*@Nullable*/ List<WindowClause.Window> currentWindows = new ArrayList<>();

    public WindowClausePush(Outputter outputter, TuplePush destination, WindowClause windowClause) {
        super(outputter);
        this.windowClause = windowClause;
        this.destination = destination;
    }

    /**
     * Notify the availability of the next tuple. Before calling this method,
     * the supplier of the tuples must set all the variables corresponding
     * to the supplied tuple in the local stack frame associated with the context object
     *
     * @param context the dynamic evaluation context
     */
    @Override
    public void processTuple(XPathContext context) throws XPathException {
        currentWindows = new ArrayList<>();
        boolean autoclose = windowClause.isTumblingWindow() && windowClause.getEndCondition() == null;
        Item previousPrevious = null;
        Item previous = null;
        Item current = null;
        Item next = null;
        int position = -1;
        SequenceIterator iter = windowClause.getSequence().iterate(context);
        boolean finished = false;
        while (!finished) {
            previousPrevious = previous;
            previous = current;
            current = next;
            next = iter.next();
            if (next == null) {
                finished = true;
                // but still complete this time round the loop
            }
            position++;
            if (position > 0) {
                if ((windowClause.isSlidingWindow() || currentWindows.isEmpty() || autoclose) &&
                        windowClause.matchesStart(previous, current, next, position, context)) {
                    if (autoclose && !currentWindows.isEmpty()) {
                        // automatically end the previous window
                        WindowClause.Window w = currentWindows.get(0);
                        w.endItem = previous;
                        w.endPreviousItem = previousPrevious;
                        w.endNextItem = current;
                        w.endPosition = position - 1;
                        despatch(w, getOutputter(), context);
                        currentWindows.clear();
                    }
                    WindowClause.Window window = new WindowClause.Window();
                    window.startPosition = position;
                    window.startItem = current;
                    window.startPreviousItem = previous;
                    window.startNextItem = next;
                    window.contents = new ArrayList<>();
                    currentWindows.add(window);
                }
                for (WindowClause.Window active : currentWindows) {
                    if (!active.isFinished()) {
                        active.contents.add(current);
                    }
                }
                if (windowClause.getEndCondition() != null) {
                    List<WindowClause.Window> removals = new ArrayList<>();
                    for (WindowClause.Window w : currentWindows) {
                        if (!w.isFinished() && windowClause.matchesEnd(w, previous, current, next, position, context)) {
                            w.endItem = current;
                            w.endPreviousItem = previous;
                            w.endNextItem = next;
                            w.endPosition = position;
                            despatch(w, getOutputter(), context);
                            if (w.isDespatched()) {
                                removals.add(w);
                            }
                        }
                    }
                    for (WindowClause.Window w : removals) {
                        currentWindows.remove(w);
                    }
                }
            }
        }
        // on completion, despatch unclosed windows if required
        if (windowClause.isIncludeUnclosedWindows()) {
            for (WindowClause.Window w : currentWindows) {
                w.endItem = current;
                w.endPreviousItem = previous;
                w.endNextItem = null;
                w.endPosition = position;
                despatch(w, getOutputter(), context);
            }
        }
    }


    /**
     * Despatch a window to the output tuple stream. This essentially involves setting the values of relevant local
     * variables to the appropriate properties of the window
     *
     * @param w       the window to be despatched
     * @param output the destination for the result
     * @param context the dynamic evaluation context
     * @throws XPathException if anything goes wrong, for example if the window contents have the wrong type
     */

    private void despatch(WindowClause.Window w, Outputter output, XPathContext context) throws XPathException {

        windowClause.checkWindowContents(w);

        // In ordered mode, we must despatch windows in order of start position not in order of end position.
        // So we don't despatch it yet if there are unfinished windows with an earlier start position

        while (true) {
            int earliestStart = Integer.MAX_VALUE;
            WindowClause.Window earliestWindow = null;
            for (WindowClause.Window u : currentWindows) {
                if (u.startPosition < earliestStart && !u.isDespatched()) {
                    earliestStart = u.startPosition;
                    earliestWindow = u;
                }
            }

            if (earliestWindow == null || !earliestWindow.isFinished()) {
                // if the earliest window is unfinished, we can't do anything yet
                return;
            } else {
                // otherwise we can process it now

                WindowClause clause = windowClause;
                LocalVariableBinding binding;
                binding = clause.getVariableBinding(WindowClause.WINDOW_VAR);
                context.setLocalVariable(binding.getLocalSlotNumber(), SequenceExtent.makeSequenceExtent(earliestWindow.contents));

                binding = clause.getVariableBinding(WindowClause.START_ITEM);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(earliestWindow.startItem));
                }
                binding = clause.getVariableBinding(WindowClause.START_ITEM_POSITION);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(earliestWindow.startPosition));
                }
                binding = clause.getVariableBinding(WindowClause.START_NEXT_ITEM);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(earliestWindow.startNextItem));
                }
                binding = clause.getVariableBinding(WindowClause.START_PREVIOUS_ITEM);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(earliestWindow.startPreviousItem));
                }
                binding = clause.getVariableBinding(WindowClause.END_ITEM);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(earliestWindow.endItem));
                }
                binding = clause.getVariableBinding(WindowClause.END_ITEM_POSITION);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(earliestWindow.endPosition));
                }
                binding = clause.getVariableBinding(WindowClause.END_NEXT_ITEM);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(earliestWindow.endNextItem));
                }
                binding = clause.getVariableBinding(WindowClause.END_PREVIOUS_ITEM);
                if (binding != null) {
                    context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(earliestWindow.endPreviousItem));
                }
                destination.processTuple(context);
                earliestWindow.isDespatched = true;
            }

        } // and loop round to see if there's another finished window that we can despatch
    }

    /**
     * Close the tuple stream, indicating that no more tuples will be supplied
     */
    @Override
    public void close() throws XPathException {
        currentWindows = null;
        destination.close();
    }


}

// Copyright (c) 2011-2020 Saxonica Limited
