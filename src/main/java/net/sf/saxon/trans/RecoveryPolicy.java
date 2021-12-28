////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

public enum RecoveryPolicy {
    /**
     * Constant indicating that the processor should take the recovery action
     * when a recoverable error occurs, with no warning message.
     */
    RECOVER_SILENTLY /*0*/,
    /**
     * Constant indicating that the processor should produce a warning
     * when a recoverable error occurs, and should then take the recovery
     * action and continue.
     */
    RECOVER_WITH_WARNINGS /*1*/,
    /**
     * Constant indicating that when a recoverable error occurs, the
     * processor should not attempt to take the defined recovery action,
     * but should terminate with an error.
     */
    DO_NOT_RECOVER /*2*/;

    public static RecoveryPolicy fromString(String s) {
        switch (s) {
            case "recoverSilently":
                return RECOVER_SILENTLY;
            case "recoverWithWarnings":
                return RECOVER_WITH_WARNINGS;
            case "doNotRecover":
                return DO_NOT_RECOVER;
            default:
                throw new IllegalArgumentException(
                        "Unrecognized value of RECOVERY_POLICY_NAME = '" + s + "'");
        }
    }
}

