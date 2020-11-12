////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.trans;


import net.sf.saxon.Configuration;
import net.sf.saxon.Version;
import org.apache.xml.resolver.CatalogManager;
import org.apache.xml.resolver.helpers.Debug;

import javax.xml.transform.TransformerException;

/**
 * Provides the interface to the Apache catalog resolver. This is in a separate class to ensure that no failure
 * occurs if the resolver code is not on the classpath, unless catalog resolution is explicitly requested.
 * The catalog file we assume is a URI
 */

public class XmlCatalogResolver {

    public static void setCatalog(String catalog, final Configuration config, boolean isTracing) throws XPathException {
        System.setProperty("xml.catalog.files", catalog);
        Version.platform.setDefaultSAXParserFactory(config);
        if (isTracing) {
            // Customize the resolver to write messages to the Saxon logging destination
            CatalogManager.getStaticManager().debug = new Debug() {
                @Override
                public void message(int level, String message) {
                    if (level <= getDebug()) {
                        config.getLogger().info(message);
                    }
                }

                @Override
                public void message(int level, String message, String spec) {
                    if (level <= getDebug()) {
                        config.getLogger().info(message + ": " + spec);
                    }
                }

                @Override
                public void message(int level, String message, String spec1, String spec2) {
                    if (level <= getDebug()) {
                        config.getLogger().info(message + ": " + spec1);
                        config.getLogger().info("\t" + spec2);
                    }
                }
            };
            if (CatalogManager.getStaticManager().getVerbosity() < 2) {
                CatalogManager.getStaticManager().setVerbosity(2);
            }
        }
        config.setSourceParserClass("org.apache.xml.resolver.tools.ResolvingXMLReader");
        config.setStyleParserClass("org.apache.xml.resolver.tools.ResolvingXMLReader");
        try {
            config.setURIResolver(config.makeURIResolver("org.apache.xml.resolver.tools.CatalogResolver"));
        } catch (TransformerException err) {
            throw XPathException.makeXPathException(err);
        }
    }
}

