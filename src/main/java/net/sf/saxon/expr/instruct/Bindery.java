////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.PackageData;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.ObjectValue;


/**
 * The Bindery class holds information about variables and their values. It is
 * used only for global variables: local variables are held in the XPathContext object.
 *
 * <p>Variables are identified by a {@link GlobalVariable} object. Values will always be of class {@link Sequence}.</p>
 *
 * <p>The Bindery is a run-time object, but slot numbers within the bindery are allocated to global
 * variables at compile time. Because XSLT packages can be separately compiled, each package needs
 * to have its own bindery.</p>
 *
 * <p>From Saxon 9.7 there is one Bindery for the global variables in each separately-compiled package.
 * The Bindery is no longer used to hold supplied values of global parameters, but it does hold their
 * values after initialization, treating them as normal global variables. Management of dependencies
 * among global variables, and checking for dynamic circularities, has been moved to the {@link net.sf.saxon.Controller}.</p>
 */

public final class Bindery {

    private GroundedValue[] globals;          // values of global variables and parameters
    private long[] busy;                 // set to current thread id while variable is being evaluated


    public Bindery(PackageData pack) {
        allocateGlobals(pack.getGlobalSlotManager());
    }
    /**
     * Define how many slots are needed for global variables
     *
     * @param map the SlotManager that keeps track of slot allocation for global variables.
     */

    private void allocateGlobals(SlotManager map) {
        int n = map.getNumberOfVariables() + 1;
        globals = (GroundedValue[])new GroundedValue[n];
        busy = new long[n];
        for (int i = 0; i < n; i++) {
            globals[i] = null;
            busy[i] = -1L;
        }
    }

    /**
     * Provide a value for a global variable
     *
     * @param binding identifies the variable
     * @param value   the value of the variable
     */

    public void setGlobalVariable(GlobalVariable binding, GroundedValue value) {
        globals[binding.getBinderySlotNumber()] = value;
    }

    /**
     * Set/Unset a flag to indicate that a particular global variable is currently being
     * evaluated. Note that this code is not synchronized, so there is no absolute guarantee that
     * two threads will not both evaluate the same global variable; however, apart from wasted time,
     * it is harmless if they do.
     *
     * @param binding the global variable in question
     * @return true if evaluation of the variable should proceed; false if it is found that the variable has now been
     *         evaluated in another thread.
     * @throws net.sf.saxon.trans.XPathException
     *          If an attempt is made to set the flag when it is already set, this means
     *          the definition of the variable is circular.
     */

    public boolean setExecuting(GlobalVariable binding)
            throws XPathException {
        long thisThread = Thread.currentThread().getId();
        int slot = binding.getBinderySlotNumber();

        long busyThread = busy[slot];
        if (busyThread != -1L) {
            if (busyThread == thisThread) {
                // The global variable is being evaluated in this thread. This shouldn't happen, because
                // we have already tested for circularities. If it does happen, however, we fail cleanly.
                throw new XPathException.Circularity("Circular definition of variable "
                        + binding.getVariableQName().getDisplayName());
            } else {
                // The global variable is being evaluated in another thread. Give it a chance to finish.
                // It could be a circularity, or just an accident of timing. Note that in the latter case,
                // we will actually re-evaluate the variable; this normally does no harm, though there is a small
                // risk it could lead to problems with the identity of a node changing.
                for (int i = 0; i < 10; i++) {
                    try {
                        Thread.sleep(20 * i);
                    } catch (InterruptedException e) {
                        // no action
                    }
                    if (busy[slot] == -1L) {
                        // evaluation has finished in another thread
                        return false;
                    }
                }
                // We've waited long enough; there could be a deadlock if we wait any longer.
                // Continue with the evaluation; whichever thread completes the evaluation first will
                // save the value.
                return true;
            }
        }
        busy[slot] = thisThread;
        return true;
    }

    /**
     * Indicate that a global variable is not currently being evaluated
     *
     * @param binding the global variable
     */

    public void setNotExecuting(GlobalVariable binding) {
        int slot = binding.getBinderySlotNumber();
        busy[slot] = -1L;
    }


    /**
     * Save the value of a global variable, and mark evaluation as complete.
     *
     * @param binding the global variable in question
     * @param value   the value that this thread has obtained by evaluating the variable
     * @return the value actually given to the variable. Exceptionally this will be different from the supplied
     *         value if another thread has evaluated the same global variable concurrently. The returned value should be
     *         used in preference, to ensure that all threads agree on the value. They could be different if for example
     *         the variable is initialized using the collection() function.
     */

    public synchronized GroundedValue saveGlobalVariableValue(
            GlobalVariable binding, GroundedValue value) {
        int slot = binding.getBinderySlotNumber();
        if (globals[slot] != null) {
            // another thread has already evaluated the value
            return globals[slot];
        } else {
            busy[slot] = -1L;
            globals[slot] = value;
            return value;
        }
    }


    /**
     * Get the value of a global variable
     *
     * @param binding the Binding that establishes the unique instance of the variable
     * @return the Value of the variable if defined, null otherwise.
     */

    public GroundedValue getGlobalVariableValue(GlobalVariable binding) {
        return globals[binding.getBinderySlotNumber()];
    }

    /**
     * Get the value of a global variable whose slot number is known
     *
     * @param slot the slot number of the required variable
     * @return the Value of the variable if defined, null otherwise.
     */

    public GroundedValue getGlobalVariable(int slot) {
        return globals[slot];
    }

    /**
     * Get all the global variables, as an array. This is provided for use by debuggers.
     * The meaning of the result can be determined by use of data held in the corresponding PackageData.
     *
     * @return the array of global variables.
     */

    public GroundedValue[] getGlobalVariables() {
        return globals;
    }


    /**
     * A value that can be saved in a global variable to indicate that evaluation failed, and that
     * subsequent attempts at evaluation should also fail
     */

    public static class FailureValue extends ObjectValue<XPathException> {

        public FailureValue(XPathException err) {
            super(err);
        }
    }


}

