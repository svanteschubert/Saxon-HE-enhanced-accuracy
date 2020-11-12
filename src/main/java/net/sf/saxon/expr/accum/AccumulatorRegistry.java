////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.accum;

import net.sf.saxon.functions.AccumulatorFn;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.style.StyleElement;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;

import java.util.*;

/**
 * Static registry for accumulators (XSLT 3.0) defined within a single package. Generally accessed
 * via the PackageData object for the package.
 */
public class AccumulatorRegistry {

    protected Map<StructuredQName, Accumulator> accumulatorsByName = new HashMap<StructuredQName, Accumulator>();


    public AccumulatorRegistry() {
        //
    }

    /**
     * Process the use-accumulators attribute of instructions such as xsl:stream, xsl:mode, etc
     * @param useAccumulatorsAtt the value of the use-accumulators attribute
     * @return the list of accumulators referenced
     */

    public Set<Accumulator> getUsedAccumulators(String useAccumulatorsAtt, StyleElement styleElement) {
        Set<Accumulator> accumulators = new HashSet<>();
        String attNames = Whitespace.trim(useAccumulatorsAtt);
        String[] tokens = attNames.split("[ \t\r\n]+");
        if (tokens.length == 1 && tokens[0].equals("#all")) {
            for (Accumulator acc : getAllAccumulators()) {
                accumulators.add(acc);
            }
        } else if (tokens.length == 1 && tokens[0].isEmpty()) {
            // do nothing - empty list
        } else {
            List<StructuredQName> names = new ArrayList<>(tokens.length);
            for (String token : tokens) {
                if (token.equals("#all")) {
                    styleElement.compileErrorInAttribute(
                            "If use-accumulators contains the token '#all', it must be the only token", "XTSE3300", "use-accumulators");
                    break;
                }
                StructuredQName name = styleElement.makeQName(token, "XTSE3300", "use-accumulators");
                if (names.contains(name)) {
                    styleElement.compileErrorInAttribute(
                            "Duplicate QName in use-accumulators attribute: " + token, "XTSE3300", "use-accumuators");
                    break;
                }
                Accumulator acc = getAccumulator(name);
                if (acc == null) {
                    styleElement.compileErrorInAttribute(
                            "Unknown accumulator name: " + token, "XTSE3300", "use-accumulators");
                    break;
                }
                names.add(name);
                accumulators.add(acc);
            }
        }
        return accumulators;
    }


    /**
     * Register an accumulator
     *
     * @param acc the accumulator to be registered
     */

    public void addAccumulator(Accumulator acc) {
        if (acc.getAccumulatorName() != null) {
            accumulatorsByName.put(acc.getAccumulatorName(), acc);
        }
    }

    /**
     * Get the accumulator with a given name
     * @param name the name of the accumulator
     * @return the accumulator with this name
     */

    public Accumulator getAccumulator(StructuredQName name) {
        return accumulatorsByName.get(name);
    }

    /**
     * Get all the registered accumulators
     * @return a collection of accumulators
     */
    public Iterable<Accumulator> getAllAccumulators() {
        return accumulatorsByName.values();
    }

    /**
     * Get the run-time value of a streamed accumulator
     *
     * @param node           the context node, which must be a streamed node
     * @param accumulator the accumulator whose value is required
     * @param phase       pre-descent or post-descent
     * @return the value of the accumulator, or null if the context node is not streamed
     * @throws XPathException if a dynamic error occurs
     */

    public Sequence getStreamingAccumulatorValue(NodeInfo node, Accumulator accumulator, AccumulatorFn.Phase phase)
            throws XPathException {
        return null;
    }

}

