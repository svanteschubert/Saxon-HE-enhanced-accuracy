////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr.instruct;

import net.sf.saxon.expr.ContextOriginator;

/**
 * An interface satisfied by all instructions that invoke templates: apply-templates,
 * call-template. apply-imports, next-match
 */

public interface ITemplateCall extends ContextOriginator {

    /**
     * Get the actual parameters passed to the called template
     *
     * @return the non-tunnel parameters
     */

    /*@NotNull*/
    WithParam[] getActualParams();

    /**
     * Get the tunnel parameters passed to the called template
     *
     * @return the tunnel parameters
     */

    /*@NotNull*/
    WithParam[] getTunnelParams();
}

