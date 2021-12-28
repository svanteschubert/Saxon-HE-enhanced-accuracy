////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


package net.sf.saxon.serialize;

import net.sf.saxon.event.*;
import net.sf.saxon.lib.SaxonOutputKeys;

import javax.xml.transform.OutputKeys;
import java.util.Properties;

/**
 * Define a set of serialization properties. These may contain simple output properties such as {@code method}
 * or {@code indent}, Saxon extensions such as {@code saxon:indent-spaces}, and may also contain an index
 * of character maps.
 */

public class SerializationProperties {

    Properties properties;
    CharacterMapIndex charMapIndex;
    FilterFactory validationFactory;

    /**
     * Create a set of defaulted serialization parameters
     */

    public SerializationProperties() {
        this.properties = new Properties();
    }

    /**
     * Create a set of serialization parameters based on defined output properties,
     * with no character maps
     */

    public SerializationProperties(Properties props) {
        this.properties = props;
    }

    /**
     * Create a set of serialization parameters based on defined output properties,
     * with an index of named character maps that may be referred to from the
     * {@code USE_CHARACTER_MAPS} property
     */

    public SerializationProperties(Properties props, CharacterMapIndex charMapIndex) {
        this.properties = props;
        this.charMapIndex = charMapIndex;
    }

    /**
     * Set the value of a serialization property.
     * @param name the property name in Clark notation (if this is constant, the available
     *            constants are defined in classes {@link OutputKeys} and {@link SaxonOutputKeys}).
     * @param value the property value. In the case of QName-valued properties, such as
     *            {@code cdata-section-elements}, these should be in Clark notation
     */

    public void setProperty(String name, String value) {
        properties.setProperty(name, value);
    }

    /**
     * Get the value of a serialization property
     * @param name the property name in Clark notation (if this is constant, the available
     *         constants are defined in classes {@link OutputKeys} and {@link SaxonOutputKeys}).
     * @return the property value. In the case of QName-valued properties, such as
     *     {@code cdata-section-elements}, these should be in Clark notation
     */

    public String getProperty(String name) {
        return getProperties().getProperty(name);
    }

    /**
     * Get the simple output properties defined in these serialization parameters. The property
     * names will be in clark-name format, for example {@code "indent"} or
     * {@code "{http://saxon.sf.net/}indent-spaces"}
     * @return the output properties
     */

    public Properties getProperties() {
        return properties;
    }

    /**
     * Get the character map index, if any
     * @return the character map index, if one is present, otherwise null
     */

    public CharacterMapIndex getCharacterMapIndex() {
        return charMapIndex;
    }

    /**
     * Although validation is not normally part of serialization, the {@code xsl:result-document}
     * instruction allows a validator to be inserted into the serialization pipeline. This is achieved
     * by adding a request to insert a validation stage to the serialization parameters. The request
     * is in the form of a factory function that constructs the required validator
     * @param validationFactory a function that inserts a validator into a {@code Receiver} pipeline
     */

    public void setValidationFactory(FilterFactory validationFactory) {
        this.validationFactory = validationFactory;
    }

    /**
     * Get any validation factory that was added to the serialization parameters using
     * {@link #setValidationFactory(FilterFactory)}
     * @return the validation factory, or null if there is none.
     */

    public FilterFactory getValidationFactory() {
        return validationFactory;
    }

    /**
     * Convenience method to create an appropriate SequenceNormalizer, based
     * on the item-separator appearing in the serialization parameters. If the
     * serialization parameters include a request for validation, then a validator
     * will also be inserted into the pipeline immediately after the SequenceNormalizer,
     * as required by the rules for {@code xsl:result-document}.
     * @param next the next {@code Receiver in the pipeline}
     * @return the new {@code SequenceNormalizer}, feeding into the supplied {@code Receiver},
     * possibly via a new validating filter.
     */

    public SequenceNormalizer makeSequenceNormalizer(Receiver next) {
        if (getValidationFactory() != null) {
            next = getValidationFactory().makeFilter(next);
        }
        String itemSeparator = properties.getProperty(SaxonOutputKeys.ITEM_SEPARATOR);
        return itemSeparator == null || "#absent".equals(itemSeparator)
                ? new SequenceNormalizerWithSpaceSeparator(next)
                : new SequenceNormalizerWithItemSeparator(next, itemSeparator);
    }

    /**
     * Combine these serialization parameters with a set of default serialization
     * parameters to create a new set of serialization parameters. Neither of the
     * input parameter sets is modified
     * @param defaults the parameters to use when no explicit values are supplied
     */

    public SerializationProperties combineWith(SerializationProperties defaults) {
        CharacterMapIndex charMap = this.charMapIndex;
        if (charMap == null || charMap.isEmpty()) {
            charMap = defaults.getCharacterMapIndex();
        }
        FilterFactory validationFactory = this.validationFactory;
        if (validationFactory == null) {
            validationFactory = defaults.validationFactory;
        }
        Properties props = new Properties(defaults.getProperties());
        for (String prop : this.getProperties().stringPropertyNames()) {
            String value = this.getProperties().getProperty(prop);
            if (prop.equals(OutputKeys.CDATA_SECTION_ELEMENTS)
                    || prop.equals(SaxonOutputKeys.SUPPRESS_INDENTATION)) {
                String existing = defaults.getProperty(prop);
                if (existing == null || existing.equals(value)) {
                    props.setProperty(prop, value);
                } else {
                    props.setProperty(prop, existing + " " + value);
                }
            } else {
                props.setProperty(prop, value);
            }
        }
        SerializationProperties newParams = new SerializationProperties(props, charMap);
        newParams.setValidationFactory(validationFactory);
        return newParams;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (String k : properties.stringPropertyNames()) {
            sb.append(k).append("=").append(properties.getProperty(k)).append(" ");
        }
        if (charMapIndex != null) {
            for (CharacterMap cm : charMapIndex) {
                sb.append(cm.getName().getEQName()).append("={").append(cm.toString()).append("} ");
            }
        }
        return sb.toString();
    }


}

