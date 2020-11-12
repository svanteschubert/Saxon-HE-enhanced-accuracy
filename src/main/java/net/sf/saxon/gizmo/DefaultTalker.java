////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.gizmo;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class DefaultTalker implements Talker {

    private Scanner in;
    private PrintStream output;

    public DefaultTalker() {
        new DefaultTalker(System.in, System.out);
    }

    public DefaultTalker(InputStream in, PrintStream out) {
        this.in = new Scanner(in);
        this.output = out;
    }

    /**
     * Send some output to the user and get some input back
     *
     * @param message output to be sent to the user
     * @return the user's response
     */
    @Override
    public String exchange(String message) {
        if (message != null && !message.isEmpty()) {
            output.println(message);
        }
        try {
            String response = in.nextLine();
            if (response == null) {
                return "";
            } else {
                return response.trim();
            }
        } catch (NoSuchElementException e) {
            return "";
        }
    }
}

