// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2026 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is “Incompatible With Secondary Licenses”, as defined by the Mozilla Public License, v. 2.0.
// //////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;

public class FunctionStreamabilityHelper {

    public static boolean isConsuming(FunctionStreamability value) {
        return value == FunctionStreamability.ABSORBING
                || value == FunctionStreamability.SHALLOW_DESCENT
                || value == FunctionStreamability.DEEP_DESCENT;
    }

    public static boolean isStreaming(FunctionStreamability value) {
        return value != FunctionStreamability.UNCLASSIFIED;
    }

    public static FunctionStreamability of(String v) {
        return switch (v) {
            case "unclassified" -> FunctionStreamability.UNCLASSIFIED;
            case "absorbing" -> FunctionStreamability.ABSORBING;
            case "inspection" -> FunctionStreamability.INSPECTION;
            case "filter" -> FunctionStreamability.FILTER;
            case "shallow-descent" -> FunctionStreamability.SHALLOW_DESCENT;
            case "deep-descent" -> FunctionStreamability.DEEP_DESCENT;
            case "ascent" -> FunctionStreamability.ASCENT;
            default -> throw new IllegalArgumentException();
        };
    }
}

//
// The contents of this file are subject to the Mozilla Public License Version 1.0 (the "License");
// you may not use this file except in compliance with the License. You may obtain a copy of the
// License at http://www.mozilla.org/MPL/
//
// Software distributed under the License is distributed on an "AS IS" basis,
// WITHOUT WARRANTY OF ANY KIND, either express or implied.
// See the License for the specific language governing rights and limitations under the License.
//
// The Original Code is: all this file.
//
// The Initial Developer of the Original Code is Saxonica Limited
//
// Portions created by (your name) are Copyright (C) (your legal entity). All Rights Reserved.
//
// Contributor(s): none.
//
