////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.flwor;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;
import net.sf.saxon.value.SequenceExtent;

import java.util.ArrayList;
import java.util.List;

/**
 * Implement a sliding or tumbling window clause of a FLWOR expression in tuple-pull mode. The entire window processing
 * is activated once for each input tuple, and it generates one output tuple for each identified window.
 */
public class WindowClausePull extends TuplePull {

    private WindowClause windowClause;
    private TuplePull source;
    private SequenceIterator baseIterator;
    private boolean finished = false;
    private XPathContext context;
    private Item previous = null;
    private Item current = null;
    private Item next = null;
    private int position = -1;
    private List<WindowClause.Window> currentWindows = new ArrayList<>();


    WindowClausePull(TuplePull source, WindowClause windowClause, XPathContext context) {
        this.windowClause = windowClause;
        this.source = source;
        this.context = context;
    }

    /**
     * Move on to the next tuple. Before returning, this method must set all the variables corresponding
     * to the "returned" tuple in the local stack frame associated with the context object
     *
     * @param context the dynamic evaluation context
     * @return true if another tuple has been generated; false if the tuple stream is exhausted. If the
     * method returns false, the values of the local variables corresponding to this tuple stream
     * are undefined.
     */
    @Override
    public boolean nextTuple(XPathContext context) throws XPathException {

        // First see if there are any windows waiting to be delivered

        WindowClause.Window earliest = lookForEarliest();
        if (earliest != null) {
            processWindow(earliest, context);
            return true;
        }

        // If we're not currently processing an input sequence, try to get a new one

        if (finished || baseIterator == null) {
            // advance the previous step in the pipeline
            if (source.nextTuple(context)) {
                // get an iterator over the input sequence
                baseIterator = windowClause.getSequence().iterate(context);
                finished = false;
                previous = null;
                position = -1;
                current = null;
                next = null;
            }
        }

        while (!finished) {

            // advance the input sequence

            boolean autoclose = windowClause.isTumblingWindow() && windowClause.getEndCondition() == null;

            Item oldPrevious = previous;
            previous = current;
            current = next;
            next = baseIterator.next();
            if (next == null) {
                finished = true;
                // but still complete this time round the loop
            }
            position++;
            if (position > 0) {
                // See if we need to start a new window
                if ((windowClause.isSlidingWindow() || currentWindows.isEmpty() || autoclose) &&
                        windowClause.matchesStart(previous, current, next, position, context)) {
                    // See if we need to end the previous window
                    if (autoclose && !currentWindows.isEmpty()) {
                        // automatically end the previous window
                        WindowClause.Window w = currentWindows.get(0);
                        w.endItem = previous;
                        w.endPreviousItem = oldPrevious;
                        w.endNextItem = current;
                        w.endPosition = position - 1;
                        earliest = despatch(w, context);
                        currentWindows.clear();
                    }
                    // Create the new window
                    WindowClause.Window window = new WindowClause.Window();
                    window.startPosition = position;
                    window.startItem = current;
                    window.startPreviousItem = previous;
                    window.startNextItem = next;
                    window.contents = new ArrayList<>();
                    currentWindows.add(window);
                }
                // Add the current item to all active windows
                for (WindowClause.Window active : currentWindows) {
                    if (!active.isFinished()) {
                        active.contents.add(current);
                    }
                }
                // See if this item marks the end of any active windows
                boolean explicitEndCondition = windowClause.getEndCondition() != null;
                boolean implicitEndCondition = finished && windowClause.isIncludeUnclosedWindows();
                if (explicitEndCondition || implicitEndCondition) {
                    List<WindowClause.Window> removals = new ArrayList<>();
                    for (WindowClause.Window w : currentWindows) {
                        if (!w.isFinished() &&
                                (implicitEndCondition ||
                                         windowClause.matchesEnd(w, previous, current, next, position, context))) {
                            w.endItem = current;
                            w.endPreviousItem = previous;
                            w.endNextItem = next;
                            w.endPosition = position;
                            if (earliest == null) {
                                earliest = despatch(w, context);
                                if (w.isDespatched()) {
                                    removals.add(w);
                                }
                            }
                        }
                    }
                    for (WindowClause.Window w : removals) {
                        currentWindows.remove(w);
                    }
                }
                // if there's a window ready to be delivered, deliver it
                if (earliest != null) {
                    processWindow(earliest, context);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Deliver a tuple (representing a window)
     *
     * @param w       a Window whose end conditions have just been satisfied
     * @param context dynamic evaluation context
     * @return true if a Tuple has been created by writing variables to the context stack; false otherwise
     * @throws XPathException if anything goes wrong
     */

    private WindowClause.Window despatch(WindowClause.Window w, XPathContext context) throws XPathException {

        windowClause.checkWindowContents(w);

        // In ordered mode, we must despatch windows in order of start position not in order of end position.
        // So we don't despatch it yet if there are unfinished windows with an earlier start position

        return lookForEarliest();
    }

    /**
     * Identity the earliest window (that is the one whose start position comes earlier in the input sequence
     * than any other window) that is available to be despatched to the output tuple stream
     *
     * @return true if an earliest window was found; false if there are no windows ready to be despatched
     * @throws XPathException if anything goes wrong
     */

    private WindowClause.Window lookForEarliest() throws XPathException {

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
            return null;
        } else {
            // otherwise we can process it now
            earliestWindow.isDespatched = true;
            return earliestWindow;
        }
    }

    /**
     * Despatch a window to the output tuple stream. This essentially means setting the relevant local variables
     * on the context stack from information held in the window object.
     *
     * @param w       the window to be despatched
     * @param context the dynamic evaluation context
     * @throws XPathException if anything goes wrong
     */

    private void processWindow(WindowClause.Window w, XPathContext context) throws XPathException {

        WindowClause clause = windowClause;
        LocalVariableBinding binding;
        binding = clause.getVariableBinding(WindowClause.WINDOW_VAR);
        context.setLocalVariable(binding.getLocalSlotNumber(), SequenceExtent.makeSequenceExtent(w.contents));

        binding = clause.getVariableBinding(WindowClause.START_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(w.startItem));
        }
        binding = clause.getVariableBinding(WindowClause.START_ITEM_POSITION);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(w.startPosition));
        }
        binding = clause.getVariableBinding(WindowClause.START_NEXT_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(w.startNextItem));
        }
        binding = clause.getVariableBinding(WindowClause.START_PREVIOUS_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(w.startPreviousItem));
        }
        binding = clause.getVariableBinding(WindowClause.END_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(w.endItem));
        }
        binding = clause.getVariableBinding(WindowClause.END_ITEM_POSITION);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), new Int64Value(w.endPosition));
        }
        binding = clause.getVariableBinding(WindowClause.END_NEXT_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(w.endNextItem));
        }
        binding = clause.getVariableBinding(WindowClause.END_PREVIOUS_ITEM);
        if (binding != null) {
            context.setLocalVariable(binding.getLocalSlotNumber(), WindowClause.makeValue(w.endPreviousItem));
        }
        w.isDespatched = true;
    }

    /**
     * Close the tuple stream, indicating that no more tuples will be supplied
     */
    @Override
    public void close() {
        baseIterator.close();
        source.close();
    }

}

// Copyright (c) 2011-2020 Saxonica Limited
