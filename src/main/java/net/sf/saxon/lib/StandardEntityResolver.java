////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.lib;

import net.sf.saxon.Configuration;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * This class is an EntityResolver used to resolve references to common
 * DTDs and entity files, using local copies provided with the Saxon product.
 * It has become necessary to do this because W3C is no longer serving
 * these files from its server. Ideally the job of caching these files
 * would belong to the XML parser, but because many of the parsers were
 * issued years ago, they cannot be relied on to do it.
 */
public class StandardEntityResolver implements EntityResolver {

    private static final HashMap<String, String> publicIds = new HashMap<>(30);
    private static final HashMap<String, String> systemIds = new HashMap<>(30);

    public Configuration config;

    /**
     * Register a DTD or other entity to be resolved by this
     * entity resolver
     *
     * @param publicId the public identifier of the DTD or entity
     * @param systemId the system identifier of the DTD or entity. For domains that are known
     *                 to redirect http: to https:, either scheme is accepted.
     * @param fileName the fileName of the Saxon local copy of the
     *                 resource, relative to the data directory in the JAR file
     */

    public static void register(
            /*@Nullable*/ String publicId,
            String systemId,
            String fileName) {
        if (publicId != null) {
            publicIds.put(publicId, fileName);
        }
        if (systemId != null) {
            systemIds.put(systemId, fileName);
            if (systemId.startsWith("http://www.w3.org/")) {
                String httpsId = "https://" + systemId.substring(7);
                systemIds.put(httpsId, fileName);
            }
        }
    }

    static {

        register("-//W3C//ENTITIES Latin 1 for XHTML//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml-lat1.ent",
                "w3c/xhtml-lat1.ent");

        register("-//W3C//ENTITIES Symbols for XHTML//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml-symbol.ent",
                "w3c/xhtml-symbol.ent");

        register("-//W3C//ENTITIES Special for XHTML//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml-special.ent",
                "w3c/xhtml-special.ent");

        register("-//W3C//DTD XHTML 1.0 Transitional//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd",
                "w3c/xhtml10/xhtml1-transitional.dtd");

        register("-//W3C//DTD XHTML 1.0 Strict//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd",
                "w3c/xhtml10/xhtml1-strict.dtd");

        register("-//W3C//DTD XHTML 1.0 Frameset//EN",
                "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd",
                "w3c/xhtml10/xhtml1-frameset.dtd");

        register("-//W3C//DTD XHTML Basic 1.0//EN",
                "http://www.w3.org/TR/xhtml-basic/xhtml-basic10.dtd",
                "w3c/xhtml10/xhtml-basic10.dtd");

        register("-//W3C//DTD XHTML 1.1//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml11.dtd",
                "w3c/xhtml11/xhtml11.dtd");

        register("-//W3C//DTD XHTML Basic 1.1//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-basic11.dtd",
                "w3c/xhtml11/xhtml-basic11.dtd");

        register("-//W3C//ELEMENTS XHTML Access Element 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-access-1.mod",
                "w3c/xhtml11/xhtml-access-1.mod");

        register("-//W3C//ENTITIES XHTML Access Attribute Qnames 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-access-qname-1.mod",
                "w3c/xhtml11/xhtml-access-qname-1.mod");

        register("-//W3C//ELEMENTS XHTML Java Applets 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-applet-1.mod",
                "w3c/xhtml11/xhtml-applet-1.mod");

        register("-//W3C//ELEMENTS XHTML Base Architecture 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-arch-1.mod",
                "w3c/xhtml11/xhtml-arch-1.mod");

        register("-//W3C//ENTITIES XHTML Common Attributes 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-attribs-1.mod",
                "w3c/xhtml11/xhtml-attribs-1.mod");

        register("-//W3C//ELEMENTS XHTML Base Element 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-base-1.mod",
                "w3c/xhtml11/xhtml-base-1.mod");

        register("-//W3C//ELEMENTS XHTML Basic Forms 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-basic-form-1.mod",
                "w3c/xhtml11/xhtml-basic-form-1.mod");

        register("-//W3C//ELEMENTS XHTML Basic Tables 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-basic-table-1.mod",
                "w3c/xhtml11/xhtml-basic-table-1.mod");

        register("-//W3C//ENTITIES XHTML Basic 1.0 Document Model 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-basic10-model-1.mod",
                "w3c/xhtml11/xhtml-basic10-model-1.mod");

        register("-//W3C//ENTITIES XHTML Basic 1.1 Document Model 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-basic11-model-1.mod",
                "w3c/xhtml11/xhtml-basic11-model-1.mod");

        register("-//W3C//ELEMENTS XHTML BDO Element 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-bdo-1.mod",
                "w3c/xhtml11/xhtml-bdo-1.mod");

        register("-//W3C//ELEMENTS XHTML BIDI Override Element 1.0//EN", //should be "BDO Element 1.0" not "BIDI Override Element 1.0"
                "http://www.w3.org/MarkUp/DTD/xhtml-bdo-1.mod",
                "w3c/xhtml11/xhtml-bdo-1.mod");

        register("-//W3C//ELEMENTS XHTML Block Phrasal 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-blkphras-1.mod",
                "w3c/xhtml11/xhtml-blkphras-1.mod");

        register("-//W3C//ELEMENTS XHTML Block Presentation 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-blkpres-1.mod",
                "w3c/xhtml11/xhtml-blkpres-1.mod");

        register("-//W3C//ELEMENTS XHTML Block Structural 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-blkstruct-1.mod",
                "w3c/xhtml11/xhtml-blkstruct-1.mod");

        register("-//W3C//ENTITIES XHTML Character Entities 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-charent-1.mod",
                "w3c/xhtml11/xhtml-charent-1.mod");

        register("-//W3C//ELEMENTS XHTML Client-side Image Maps 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-csismap-1.mod",
                "w3c/xhtml11/xhtml-csismap-1.mod");

        register("-//W3C//ENTITIES XHTML Datatypes 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-datatypes-1.mod",
                "w3c/xhtml11/xhtml-datatypes-1.mod");

        register("-//W3C//ELEMENTS XHTML Editing Markup 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-edit-1.mod",
                "w3c/xhtml11/xhtml-edit-1.mod");

        register("-//W3C//ELEMENTS XHTML Editing Elements 1.0//EN",   // should be "Editing Markup" not "Editing Elements", but allow both
                "http://www.w3.org/MarkUp/DTD/xhtml-edit-1.mod",
                "w3c/xhtml11/xhtml-edit-1.mod");

        register("-//W3C//ENTITIES XHTML Intrinsic Events 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-events-1.mod",
                "w3c/xhtml11/xhtml-events-1.mod");

        register("-//W3C//ELEMENTS XHTML Forms 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-form-1.mod",
                "w3c/xhtml11/xhtml-form-1.mod");

        register("-//W3C//ELEMENTS XHTML Frames 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-frames-1.mod",
                "w3c/xhtml11/xhtml-frames-1.mod");

        register("-//W3C//ENTITIES XHTML Modular Framework 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-framework-1.mod",
                "w3c/xhtml11/xhtml-framework-1.mod");

        register("-//W3C//ENTITIES XHTML HyperAttributes 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-hyperAttributes-1.mod",
                "w3c/xhtml11/xhtml-hyperAttributes-1.mod");

        register("-//W3C//ELEMENTS XHTML Hypertext 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-hypertext-1.mod",
                "w3c/xhtml11/xhtml-hypertext-1.mod");

        register("-//W3C//ELEMENTS XHTML Inline Frame Element 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-iframe-1.mod",
                "w3c/xhtml11/xhtml-iframe-1.mod");

        register("-//W3C//ELEMENTS XHTML Images 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-image-1.mod",
                "w3c/xhtml11/xhtml-image-1.mod");

        register("-//W3C//ELEMENTS XHTML Inline Phrasal 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-inlphras-1.mod",
                "w3c/xhtml11/xhtml-inlphras-1.mod");

        register("-//W3C//ELEMENTS XHTML Inline Presentation 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-inlpres-1.mod",
                "w3c/xhtml11/xhtml-inlpres-1.mod");

        register("-//W3C//ELEMENTS XHTML Inline Structural 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-inlstruct-1.mod",
                "w3c/xhtml11/xhtml-inlstruct-1.mod");

        register("-//W3C//ENTITIES XHTML Inline Style 1.0//EN",  // should be "ELEMENTS" not "ENTITIES", but allow either
                "http://www.w3.org/MarkUp/DTD/xhtml-inlstyle-1.mod",
                "w3c/xhtml11/xhtml-inlstyle-1.mod");

        register("-//W3C//ELEMENTS XHTML Inline Style 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-inlstyle-1.mod",
                "w3c/xhtml11/xhtml-inlstyle-1.mod");

        register("-//W3C//ELEMENTS XHTML Inputmode 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-inputmode-1.mod",
                "w3c/xhtml11/xhtml-inputmode-1.mod");

        register("-//W3C//ELEMENTS XHTML Legacy Markup 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-legacy-1.mod",
                "w3c/xhtml11/xhtml-legacy-1.mod");

        register("-//W3C//ELEMENTS XHTML Legacy Redeclarations 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-legacy-redecl-1.mod",
                "w3c/xhtml11/xhtml-legacy-redecl-1.mod");

        register("-//W3C//ELEMENTS XHTML Link Element 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-link-1.mod",
                "w3c/xhtml11/xhtml-link-1.mod");

        register("-//W3C//ELEMENTS XHTML Lists 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-list-1.mod",
                "w3c/xhtml11/xhtml-list-1.mod");

        register("-//W3C//ELEMENTS XHTML Metainformation 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-meta-1.mod",
                "w3c/xhtml11/xhtml-meta-1.mod");

        register("-//W3C//ELEMENTS XHTML Metainformation 2.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-meta-2.mod",
                "w3c/xhtml11/xhtml-meta-2.mod");

        register("-//W3C//ENTITIES XHTML MetaAttributes 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-metaAttributes-1.mod",
                "w3c/xhtml11/xhtml-metaAttributes-1.mod");

        register("-//W3C//ELEMENTS XHTML Name Identifier 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-nameident-1.mod",
                "w3c/xhtml11/xhtml-nameident-1.mod");

        register("-//W3C//NOTATIONS XHTML Notations 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-notations-1.mod",
                "w3c/xhtml11/xhtml-notations-1.mod");

        register("-//W3C//ELEMENTS XHTML Embedded Object 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-object-1.mod",
                "w3c/xhtml11/xhtml-object-1.mod");

        register("-//W3C//ELEMENTS XHTML Param Element 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-param-1.mod",
                "w3c/xhtml11/xhtml-param-1.mod");

        register("-//W3C//ELEMENTS XHTML Presentation 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-pres-1.mod",
                "w3c/xhtml11/xhtml-pres-1.mod");

        register("-//W3C//ENTITIES XHTML-Print 1.0 Document Model 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-print10-model-1.mod",
                "w3c/xhtml11/xhtml-print10-model-1.mod");

        register("-//W3C//ENTITIES XHTML Qualified Names 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-qname-1.mod",
                "w3c/xhtml11/xhtml-qname-1.mod");

        register("-//W3C//ENTITIES XHTML+RDFa Document Model 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-rdfa-model-1.mod",
                "w3c/xhtml11/xhtml-rdfa-model-1.mod");

        register("-//W3C//ENTITIES XHTML RDFa Attribute Qnames 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-rdfa-qname-1.mod",
                "w3c/xhtml11/xhtml-rdfa-qname-1.mod");

        register("-//W3C//ENTITIES XHTML Role Attribute 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-role-1.mod",
                "w3c/xhtml11/xhtml-role-1.mod");

        register("-//W3C//ENTITIES XHTML Role Attribute Qnames 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-role-qname-1.mod",
                "w3c/xhtml11/xhtml-role-qname-1.mod");

        register("-//W3C//ELEMENTS XHTML Ruby 1.0//EN",
                "http://www.w3.org/TR/ruby/xhtml-ruby-1.mod",
                "w3c/xhtml11/xhtml-ruby-1.mod");

        register("-//W3C//ELEMENTS XHTML Scripting 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-script-1.mod",
                "w3c/xhtml11/xhtml-script-1.mod");

        register("-//W3C//ELEMENTS XHTML Server-side Image Maps 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-ssismap-1.mod",
                "w3c/xhtml11/xhtml-ssismap-1.mod");

        register("-//W3C//ELEMENTS XHTML Document Structure 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-struct-1.mod",
                "w3c/xhtml11/xhtml-struct-1.mod");

        register("-//W3C//DTD XHTML Style Sheets 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-style-1.mod",
                "w3c/xhtml11/xhtml-style-1.mod");

        register("-//W3C//ELEMENTS XHTML Style Sheets 1.0//EN",  // should be "DTD XHTML" not "ELEMENTS XHTML"
                "http://www.w3.org/MarkUp/DTD/xhtml-style-1.mod",
                "w3c/xhtml11/xhtml-style-1.mod");

        register("-//W3C//ELEMENTS XHTML Tables 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-table-1.mod",
                "w3c/xhtml11/xhtml-table-1.mod");

        register("-//W3C//ELEMENTS XHTML Target 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-target-1.mod",
                "w3c/xhtml11/xhtml-target-1.mod");

        register("-//W3C//ELEMENTS XHTML Text 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml-text-1.mod",
                "w3c/xhtml11/xhtml-text-1.mod");

        register("-//W3C//ENTITIES XHTML 1.1 Document Model 1.0//EN",
                "http://www.w3.org/MarkUp/DTD/xhtml11-model-1.mod",
                "w3c/xhtml11/xhtml11-model-1.mod");

        register("-//W3C//MathML 1.0//EN",
                "http://www.w3.org/Math/DTD/mathml1/mathml.dtd",
                "w3c/mathml/mathml1/mathml.dtd");

        register("-//W3C//DTD MathML 2.0//EN",
                "http://www.w3.org/Math/DTD/mathml2/mathml2.dtd",
                "w3c/mathml/mathml2/mathml2.dtd");

        register("-//W3C//DTD MathML 3.0//EN",
                "http://www.w3.org/Math/DTD/mathml3/mathml3.dtd",
                "w3c/mathml/mathml3/mathml3.dtd");

        register("-//W3C//DTD SVG 1.0//EN",
                "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd",
                "w3c/svg10/svg10.dtd");

        register("-//W3C//DTD SVG 1.1//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd",
                "w3c/svg11/svg11.dtd");

        register("-//W3C//DTD SVG 1.1 Tiny//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-tiny.dtd",
                "w3c/svg11/svg11-tiny.dtd");

        register("-//W3C//DTD SVG 1.1 Basic//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-basic.dtd",
                "w3c/svg11/svg11-basic.dtd");

        register("-//W3C//ENTITIES SVG 1.1 Document Model//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-model.mod",
                "w3c/svg11/svg11-model.mod");

        register("-//W3C//ENTITIES SVG 1.1 Attribute Collection//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11-attribs.mod",
                "w3c/svg11/svg11-attribs.mod");

        register("-//W3C//ENTITIES SVG 1.1 Modular Framework//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-framework.mod",
                "w3c/svg11/svg-framework.mod");

        register("-//W3C//ENTITIES SVG 1.1 Datatypes//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-datatypes.mod",
                "w3c/svg11/svg-datatypes.mod");

        register("-//W3C//ENTITIES SVG 1.1 Qualified Name//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-qname.mod",
                "w3c/svg11/svg-qname.mod");

        register("-//W3C//ENTITIES SVG 1.1 Core Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-core-attrib.mod",
                "w3c/svg11/svg-core-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Container Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-container-attrib.mod",
                "w3c/svg11/svg-container-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Viewport Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-viewport-attrib.mod",
                "w3c/svg11/svg-viewport-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Paint Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-paint-attrib.mod",
                "w3c/svg11/svg-paint-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Paint Opacity Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-opacity-attrib.mod",
                "w3c/svg11/svg-opacity-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Graphics Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-graphics-attrib.mod",
                "w3c/svg11/svg-graphics-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Document Events Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-docevents-attrib.mod",
                "w3c/svg11/svg-docevents-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Graphical Element Events Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-graphevents-attrib.mod",
                "w3c/svg11/svg-graphevents-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 Animation Events Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-animevents-attrib.mod",
                "w3c/svg11/svg-animevents-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 XLink Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-xlink-attrib.mod",
                "w3c/svg11/svg-xlink-attrib.mod");

        register("-//W3C//ENTITIES SVG 1.1 External Resources Attribute//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-extresources-attrib.mod",
                "w3c/svg11/svg-extresources-attrib.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Structure//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-structure.mod",
                "w3c/svg11/svg-structure.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Conditional Processing//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-conditional.mod",
                "w3c/svg11/svg-conditional.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Image//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-image.mod",
                "w3c/svg11/svg-image.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Style//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-style.mod",
                "w3c/svg11/svg-style.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Shape//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-shape.mod",
                "w3c/svg11/svg-shape.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Text//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-text.mod",
                "w3c/svg11/svg-text.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Marker//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-marker.mod",
                "w3c/svg11/svg-marker.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Color Profile//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-profile.mod",
                "w3c/svg11/svg-profile.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Gradient//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-gradient.mod",
                "w3c/svg11/svg-gradient.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Pattern//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-pattern.mod",
                "w3c/svg11/svg-pattern.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Clip//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-clip.mod",
                "w3c/svg11/svg-clip.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Mask//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-mask.mod",
                "w3c/svg11/svg-mask.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Filter//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-filter.mod",
                "w3c/svg11/svg-filter.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Cursor//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-cursor.mod",
                "w3c/svg11/svg-cursor.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Hyperlinking//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-hyperlink.mod",
                "w3c/svg11/svg-hyperlink.mod");

        register("-//W3C//ELEMENTS SVG 1.1 View//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-view.mod",
                "w3c/svg11/svg-view.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Scripting//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-script.mod",
                "w3c/svg11/svg-script.mod");


        register("-//W3C//ELEMENTS SVG 1.1 Animation//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-animation.mod",
                "w3c/svg11/svg-animation.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Font//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-font.mod",
                "w3c/svg11/svg-font.mod");

        register("-//W3C//ELEMENTS SVG 1.1 Extensibility//EN",
                "http://www.w3.org/Graphics/SVG/1.1/DTD/svg-extensibility.mod",
                "w3c/svg11/svg-extensibility.mod");

        register("-//XML-DEV//ENTITIES RDDL Document Model 1.0//EN",
                "http://www.rddl.org/xhtml-rddl-model-1.mod",
                "w3c/rddl/xhtml-rddl-model-1.mod");

        register("-//XML-DEV//DTD XHTML RDDL 1.0//EN",
                "http://www.rddl.org/rddl-xhtml.dtd",
                "w3c/rddl/rddl-xhtml.dtd");

        register("-//XML-DEV//ENTITIES RDDL QName Module 1.0//EN",
                "http://www.rddl.org/rddl-qname-1.mod",
                "w3c/rddl/rddl-qname-1.mod");

        register("-//XML-DEV//ENTITIES RDDL Resource Module 1.0//EN",
                "http://www.rddl.org/rddl-resource-1.mod",
                "w3c/rddl/rddl-resource-1.mod");
        
        register("-//XML-DEV//ELEMENTS RDDL Resource 1.0//EN",
                 "http://www.rddl.org/rddl-resource-1.mod",
                 "w3c/rddl/rddl-resource-1.mod");

        register("-//XML-DEV//ENTITIES XLink Module 1.0//EN",
                 "http://www.rddl.org/rddl-resource-1.mod",
                 "w3c/rddl/xlink-module-1.mod");

        register("-//W3C//DTD Specification V2.10//EN",
                "http://www.w3.org/2002/xmlspec/dtd/2.10/xmlspec.dtd",
                "w3c/xmlspec/xmlspec.dtd");

        register("-//W3C//DTD XMLSCHEMA 200102//EN",
                "http://www.w3.org/2001/XMLSchema.dtd",
                "w3c/xmlschema10/XMLSchema.dtd");

        register("datatypes",
                "http://www.w3.org/2001/datatypes.dtd",
                "w3c/xmlschema10/datatypes.dtd");

        register("-//W3C//DTD XSD 1.1//EN",
                 "http://www.w3.org/TR/xmlschema11-1/XMLSchema.dtd",
                 "w3c/xmlschema11/XMLSchema.dtd");

        register("-//W3C//DTD XSD 1.1 Datatypes//EN",
                 "http://www.w3.org/TR/xmlschema11-1/datatypes.dtd",
                 "w3c/xmlschema11/datatypes.dtd");

        register("xpath-functions",
                "http://www.w3.org/2005/xpath-functions.xsd",
                "xpath-functions.xsd");

        register("json",
                 "http://www.w3.org/2005/json.xsd",
                 "xpath-functions.xsd");

        register("analyze-string",
                 "http://www.w3.org/2005/analyze-string.xsd",
                 "xpath-functions.xsd");

        register("xml-to-json",
                "xml-to-json.xsl",
                "xml-to-json.xsl");

        register("xml-to-json-indent",
                "xml-to-json-indent.xsl",
                "xml-to-json-indent.xsl");
    }


    public StandardEntityResolver(Configuration config) {
        Objects.requireNonNull(config);
        this.config = config;
    }

    /**
     * Set configuration details. This is used to control tracing of accesses to files
     *
     * @param config the Saxon configuration
     */

    public void setConfiguration(Configuration config) {
        this.config = config;
    }

    /**
     * Allow the application to resolve external entities.
     * <p>The parser will call this method before opening any external
     * entity except the top-level document entity.  Such entities include
     * the external DTD subset and external parameter entities referenced
     * within the DTD (in either case, only if the parser reads external
     * parameter entities), and external general entities referenced
     * within the document element (if the parser reads external general
     * entities).  The application may request that the parser locate
     * the entity itself, that it use an alternative URI, or that it
     * use data provided by the application (as a character or byte
     * input stream).</p>
     * <p>Application writers can use this method to redirect external
     * system identifiers to secure and/or local URIs, to look up
     * public identifiers in a catalogue, or to read an entity from a
     * database or other input source (including, for example, a dialog
     * box).  Neither XML nor SAX specifies a preferred policy for using
     * public or system IDs to resolve resources.  However, SAX specifies
     * how to interpret any InputSource returned by this method, and that
     * if none is returned, then the system ID will be dereferenced as
     * a URL.  </p>
     * <p>If the system identifier is a URL, the SAX parser must
     * resolve it fully before reporting it to the application.</p>
     *
     * @param publicId The public identifier of the external entity
     *                 being referenced, or null if none was supplied.
     * @param systemId The system identifier of the external entity
     *                 being referenced.
     * @return An InputSource object describing the new input source,
     *         or null to request that the parser open a regular
     *         URI connection to the system identifier.
     * @throws org.xml.sax.SAXException Any SAX exception, possibly
     *                                  wrapping another exception.
     * @see org.xml.sax.InputSource
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
        // See if it's a known public ID
        String fileName = publicIds.get(publicId);
        if (fileName != null) {
            return fetch(fileName, config);
        }

        // See if it's a known system ID
        fileName = systemIds.get(systemId);
        if (fileName != null) {
            return fetch(fileName, config);
        }

        // If this is a W3C URI, Saxon ought really to have a copy...
        if (systemId.startsWith("http://www.w3.org/") && config.isTiming()) {
            config.getLogger().info("Saxon does not have a local copy of PUBLIC " + publicId + " SYSTEM " + systemId);
        }

        try {
            URI uri = new URI(systemId);
            if (uri.isAbsolute() && !config.getAllowedUriTest().test(uri)) {
                throw new SAXException("URI scheme '" + uri.getScheme() + "' has been disallowed");
            }
        } catch (URISyntaxException err) {
            // no action, bypass the check if it's not a URI
        }

        // If it's a classpath URI, handle it here
        if (systemId.startsWith("classpath:") && systemId.length() > 10) {
            return fetch(systemId.substring(10), config);
        }

        // Otherwise, leave the parser to resolve the URI in the normal way
        return null;
    }

    /**
     * Get the source from a given file.
     * @param filename the name of the file
     * @param config the Saxon Configuration
     * @return an InputSource representing the contents of the file.
     */

    public static InputSource fetch(String filename, Configuration config) {
        boolean tracing = false;
        Logger traceDestination = null;
        if (config != null) {
            tracing = config.isTiming();
            traceDestination = config.getLogger();
        }
        if (tracing) {
            if (traceDestination == null) {
                traceDestination = new StandardLogger();
            }
            traceDestination.info("Fetching Saxon copy of " + filename);
        }
        List<String> messages = new ArrayList<>();
        List<ClassLoader> classLoaders = new ArrayList<>();
        InputStream in = Configuration.locateResource(filename, messages, classLoaders);
        if (tracing) {
            for (String s : messages) {
                traceDestination.info(s);
            }
        }
        if (in == null) {
            return null;
        }
        InputSource result = new InputSource(in);
        result.setSystemId("classpath:" + filename);
        return result;
    }

    /**
     * Get a resource from the classpath. This method is called if the URI uses the (Spring-defined)
     * "classpath" URI scheme. It attempts to locate the resource on the
     * classpath and returns an InputSource containing the relevant InputStream. If the
     * inputstream cannot be located, it returns null. A subclass that does not want classpath
     * URIs to be resolved in this way should override this method to return null unconditionally.
     *
     * @param resourceName    the resource to be fetched from the classpath
     * @param config the Saxon Configuration object
     * @return an InputSource representing the named resource (fetched from the classpath)
     * or null if no resource is available.
     */

    protected InputSource getResource(String resourceName, Configuration config) {
        InputStream inputStream = config.getDynamicLoader().getResourceAsStream(resourceName);
        if (inputStream != null) {
            InputSource inputSource = new InputSource(inputStream);
            inputSource.setSystemId("classpath:" + resourceName);
            return inputSource;
        } else {
            return null;
        }
    }
}

