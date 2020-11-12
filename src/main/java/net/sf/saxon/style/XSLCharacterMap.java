////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.style;

import net.sf.saxon.om.*;
import net.sf.saxon.trans.Err;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Whitespace;
import net.sf.saxon.z.IntHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * An xsl:character-map declaration in the stylesheet. <br>
 */

public class XSLCharacterMap extends StyleElement {

    /*@Nullable*/ String use;
    // the value of the use-character-maps attribute, as supplied

    List<XSLCharacterMap> characterMapElements = null;
    // list of XSLCharacterMap objects referenced by this one

    boolean validated = false;
    // set to true once validate() has been called

    boolean redundant = false;
    // set to true if another character-map overrrides this one

    /**
     * Ask whether this node is a declaration, that is, a permitted child of xsl:stylesheet
     * (including xsl:include and xsl:import).
     *
     * @return true for this element
     */

    @Override
    public boolean isDeclaration() {
        return true;
    }

    /**
     * Get the fingerprint of the name of this character map
     *
     * @return the fingerprint value
     */

    public StructuredQName getCharacterMapName() {
        StructuredQName name = getObjectName();
        if (name == null) {
            return makeQName(getAttributeValue("", "name"), null, "name");
        }
        return name;
    }

    /**
     * Test whether this character map is redundant (because another with the
     * same name has higher import precedence). Note that a character map is not
     * considered redundant simply because it is not referenced in an xsl:output
     * declaration; we allow character-maps to be selected at run-time using the
     * setOutputProperty() API.
     *
     * @return true if this character map is redundant
     */

    public boolean isRedundant() {
        return redundant;
    }

    /**
     * Validate the attributes on this instruction
     */

    @Override
    public void prepareAttributes() {

        String name = null;
        use = null;

        for (AttributeInfo att : attributes()) {
            NodeName attName = att.getNodeName();
            String value = att.getValue();
            String f = attName.getDisplayName();
            if (f.equals("name")) {
                name = Whitespace.trim(value);
            } else if (f.equals("use-character-maps")) {
                use = value;
            } else {
                checkUnknownAttribute(attName);
            }
        }

        if (name == null) {
            reportAbsence("name");
            name = "unnamedCharacterMap_" + hashCode();
        }

        setObjectName(makeQName(name, null, "name"));

    }

    @Override
    public void validate(ComponentDeclaration decl) throws XPathException {

        if (validated) {
            return;
        }

        // check that this is a top-level declaration

        checkTopLevel("XTSE0010", false);

        // check that the only children are xsl:output-character elements

        for (NodeInfo child : children()) {
            if (!(child instanceof XSLOutputCharacter)) {
                compileError("Only xsl:output-character is allowed within xsl:character-map", "XTSE0010");
            }
        };

        // check that there isn't another character-map with the same name and import
        // precedence

        PrincipalStylesheetModule psm = getPrincipalStylesheetModule();
        ComponentDeclaration other = psm.getCharacterMap(getObjectName());
        if (other != null /* error path: see character-map-027 */ && other.getSourceElement() != this) {
            if (decl.getPrecedence() == other.getPrecedence()) {
                compileError("There are two character-maps with the same name and import precedence", "XTSE1580");
            } else if (decl.getPrecedence() < other.getPrecedence()) {
                redundant = true;
            }
        }

        // validate the use-character-maps attribute

        if (use != null) {

            // identify any character maps that this one refers to

            characterMapElements = new ArrayList<XSLCharacterMap>(5);
            StringTokenizer st = new StringTokenizer(use, " \t\n\r", false);

            while (st.hasMoreTokens()) {
                String displayname = st.nextToken();
                try {
                    String[] parts = NameChecker.getQNameParts(displayname);
                    String uri = getURIForPrefix(parts[0], false);
                    if (uri == null) {
                        compileError("Undeclared namespace prefix " + Err.wrap(parts[0])
                                + " in character map name", "XTSE0280");
                    }
                    StructuredQName qn = new StructuredQName(parts[0], uri, parts[1]);
                    ComponentDeclaration charMapDecl = psm.getCharacterMap(qn);
                    if (charMapDecl == null) {
                        compileError("No character-map named '" + displayname + "' has been defined", "XTSE1590");
                    } else {
                        XSLCharacterMap ref = (XSLCharacterMap) charMapDecl.getSourceElement();
                        characterMapElements.add(ref);
                    }
                } catch (QNameException err) {
                    compileError("Invalid character-map name. " + err.getMessage(), "XTSE1590");
                }
            }

            // check for circularity

            for (Object characterMapElement : characterMapElements) {
                ((XSLCharacterMap) characterMapElement).checkCircularity(this);
            }
        }

        validated = true;
    }

    /**
     * Check for circularity: specifically, check that this attribute set does not contain
     * a direct or indirect reference to the one supplied as a parameter
     *
     * @param origin the start point of the search
     * @throws net.sf.saxon.trans.XPathException
     *          if an error is detected
     */

    private void checkCircularity(XSLCharacterMap origin) throws XPathException {
        if (this == origin) {
            compileError("The definition of the character map is circular", "XTSE1600");
            characterMapElements = null;    // for error recovery
        } else {
            if (!validated) {
                // if this attribute set isn't validated yet, we don't check it.
                // The circularity will be detected when the last attribute set in the cycle
                // gets validated
                return;
            }
            if (characterMapElements != null) {
                for (Object characterMapElement : characterMapElements) {
                    ((XSLCharacterMap) characterMapElement).checkCircularity(origin);
                }
            }
        }
    }

    /**
     * Assemble all the mappings defined by this character map, adding them to a
     * HashMap that maps integer codepoints to strings
     *
     * @param map an IntHash map populated with the character mappings
     */

    public void assemble(IntHashMap<String> map) {
        if (characterMapElements != null) {
            for (XSLCharacterMap charmap : characterMapElements) {
                charmap.assemble(map);
            }
        }
        for (NodeInfo child : children()) {
            XSLOutputCharacter oc = (XSLOutputCharacter) child;
            map.put(oc.getCodePoint(), oc.getReplacementString());
        }
    }

    @Override
    public void compileDeclaration(Compilation compilation, ComponentDeclaration decl) throws XPathException {
        // no action
    }

}

