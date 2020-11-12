////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom2;

import net.sf.saxon.Query;
import net.sf.saxon.trans.XPathException;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Variant of command line net.sf.saxon.Transform do build the source document
 * in JDOM2 and then proceed with the transformation. This class is provided largely for
 * testing purposes.
 */

public class JDOM2Query extends Query {

    public List preprocess(List sources) throws XPathException {
        try {
            ArrayList jdomSources = new ArrayList(sources.size());
            for (int i = 0; i < sources.size(); i++) {
                SAXSource ss = (SAXSource) sources.get(i);
                SAXBuilder builder = new SAXBuilder();
                org.jdom2.Document doc = builder.build(ss.getInputSource());
                doc.setBaseURI(ss.getSystemId());
                JDOM2DocumentWrapper jdom = new JDOM2DocumentWrapper(doc, getConfiguration());
                jdomSources.add(jdom);
            }
            return jdomSources;
        } catch (JDOMException e) {
            throw new XPathException(e);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    public static void main(String[] args) {
        new JDOM2Query().doQuery(args, "JDOM2Query");
    }
}


// Original Code is Copyright (c) 2009-2020 Saxonica Limited
