////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon;

import net.sf.saxon.jaxp.SaxonTransformerFactory;


/**
 * A TransformerFactory instance can be used to create Transformer and Template
 * objects.
 * <p>This implementation of the JAXP TransformerFactory interface provides Saxon-HE,
 * Saxon-PE, or Saxon-EE capability, depending on what software has been loaded from
 * the classpath, and what license key is available.</p>
 * <p>An application that explicitly wants Saxon-HE, Saxon-PE, or Saxon-EE capability
 * regardless of what is available at run-time should instantiate <code>BasicTransformerFactory</code>,
 * <code>ProfessionalTransformerFactory</code>, or <code>EnterpriseTransformerFactory</code>
 * as appropriate.</p>
 * <p>The system property that determines which Factory implementation
 * to create is named "javax.xml.transform.TransformerFactory". This
 * property names a concrete subclass of the TransformerFactory abstract
 * class. If the property is not defined, a platform default is be used.</p>
 * <p>This implementation class implements the abstract methods on both the
 * javax.xml.transform.TransformerFactory and javax.xml.transform.sax.SAXTransformerFactory
 * classes.</p>
 * <p>Since Saxon 9.6, the JAXP transformation interface is re-implemented as a layer
 * on top of the s9api interface. This will affect applications that attempt to
 * down-cast from JAXP interfaces to the underlying implementation classes.</p>
 * <p>This class is Saxon's "public" implementation of the TransformerFactory
 * interface. It is a trivial subclass of the internal class
 * {@link net.sf.saxon.jaxp.SaxonTransformerFactory}, which is in a separate package
 * along with the implementation classes to which it has protected access.</p>
 */

public class TransformerFactoryImpl extends SaxonTransformerFactory {

    public TransformerFactoryImpl() {
        super();
    }

    public TransformerFactoryImpl(Configuration config) {
        super(config);
    }

}

