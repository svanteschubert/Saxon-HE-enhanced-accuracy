////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.dom;

import net.sf.saxon.Transform;
import net.sf.saxon.trans.XPathException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Variant of command line net.sf.saxon.Transform do build the source document
 * in DOM and then proceed with the transformation. This class is provided largely for
 * testing purposes.
 */

public class DOMTransform extends Transform {

    @Override
    public List<Source> preprocess(List<Source> sources) throws XPathException {
        try {
            ArrayList<Source> domSources = new ArrayList<>(sources.size());
            for (Object source : sources) {
                StreamSource src = (StreamSource) source;
                InputSource ins = new InputSource(src.getSystemId());

                // The following statement, if uncommented, forces use of the Xerces DOM.
                // This system property can also be set from the command line using the -D option

//                System.setProperty("javax.xml.parser.DocumentBuilderFactory",
//                        "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl");

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(ins);
                DocumentWrapper dom = new DocumentWrapper(doc, src.getSystemId(), getConfiguration());
                domSources.add(dom.getRootNode());
            }
            return domSources;
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new XPathException(e);
        }
    }

    public static void main(String[] args) {
        new DOMTransform().doTransform(args, "DOMTransform");
    }
}

