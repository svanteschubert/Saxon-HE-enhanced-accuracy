////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.option.jdom2;

import net.sf.saxon.Transform;
import net.sf.saxon.trans.XPathException;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Variant of command line net.sf.saxon.Transform do build the source document
 * in JDOM2 and then proceed with the transformation. This class is provided largely for
 * testing purposes.
 */

public class JDOM2Transform extends Transform {

    @Override
    public List preprocess(List sources) throws XPathException {
        try {
            ArrayList jdomSources = new ArrayList(sources.size());
            for (int i = 0; i < sources.size(); i++) {
                Source src = (Source) sources.get(i);
                InputSource is;
                if (src instanceof SAXSource) {
                    SAXSource ss = (SAXSource) sources.get(i);
                    is = ss.getInputSource();
                } else if (src instanceof StreamSource) {
                    StreamSource ss = (StreamSource) src;
                    if (ss.getInputStream() != null) {
                        is = new InputSource(ss.getInputStream());
                    } else if (ss.getReader() != null) {
                        is = new InputSource(ss.getReader());
                    } else {
                        is = new InputSource(ss.getSystemId());
                    }
                } else {
                    throw new IllegalArgumentException("Unknown kind of source");
                }
                is.setSystemId(src.getSystemId());
                SAXBuilder builder = new SAXBuilder();
                org.jdom2.Document doc = builder.build(is);
                doc.setBaseURI(is.getSystemId());
                net.sf.saxon.option.jdom2.JDOM2DocumentWrapper jdom = new JDOM2DocumentWrapper(doc, getConfiguration());
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
        new JDOM2Transform().doTransform(args, "JDOM2Transform");
    }
}

// Original Code is Copyright (c) 2009-2020 Saxonica Limited
