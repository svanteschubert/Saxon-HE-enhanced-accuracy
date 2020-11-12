////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2018-2020 Saxonica Limited
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package net.sf.saxon.expr;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.accum.AccumulatorRegistry;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.s9api.HostLanguage;
import net.sf.saxon.trans.DecimalFormatManager;
import net.sf.saxon.trans.KeyManager;
import net.sf.saxon.trans.TypeAliasManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Information about a unit of compilation: in XSLT, a package; in XQuery, a module. May also be a single
 * free-standing XPath expression.
 */
public class PackageData {

    protected Configuration config;
    private HostLanguage hostLanguage;
    private boolean isSchemaAware;
    private DecimalFormatManager decimalFormatManager = null;
    protected KeyManager keyManager = null;
    private AccumulatorRegistry accumulatorRegistry = null;
    private List<GlobalVariable> globalVariables = new ArrayList<>();
    private SlotManager globalSlotManager;
    private int localLicenseId = -1;
    private String targetEdition;
    private boolean relocatable;
    private TypeAliasManager typeAliasManager;


    /**
     * Create a PackageData object
     * @param config the Saxon configuration
     */

    public PackageData(Configuration config) {
        if (config == null) {
            throw new NullPointerException();
        }
        this.config = config;
        targetEdition = config.getEditionCode();
        globalSlotManager = config.makeSlotManager();
    }

    /**
     * Create a PackageData object as a copy of an existing PackageData
     * @param p the existing PackageData object
     */

    public PackageData(PackageData p) {

    }

    /**
     * Get the Configuration to which this package belongs
     * @return the Saxon configuration
     */

    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Set the Configuration to which this package belongs
     * @param configuration the Saxon configuration
     */

    public void setConfiguration(Configuration configuration) {
        this.config = configuration;
    }

    /**
     * Get the language in which this package is written
     * @return typically {@link HostLanguage#XSLT}, {@link HostLanguage#XQUERY}, or {@link HostLanguage#XPATH}
     */

    public HostLanguage getHostLanguage() {
        return hostLanguage;
    }

    /**
     * Ask if the host language is XSLT
     * @return true if the host language is XSLT
     */

    public boolean isXSLT() {
        return hostLanguage == HostLanguage.XSLT;
    }

    /**
     * Set the language in which this package is written
     * @param hostLanguage typically {@link HostLanguage#XSLT}, {@link HostLanguage#XQUERY}, or {@link HostLanguage#XPATH}
     */

    public void setHostLanguage(HostLanguage hostLanguage) {
        this.hostLanguage = hostLanguage;
    }

    /**
     * Set the local license id, identifying any local license in the case
     * where this package was loaded from a compiled package that contained its own
     * embedded license
     * @param id identifier used to distinguish this local license
     */
    public void setLocalLicenseId(int id) {
        localLicenseId = id;
    }

    /**
     * Get the local license id, identifying any local license in the case
     * where this package was loaded from a compiled package that contained its own
     * embedded license
     * @return integer identifying the local license, or -1 if there is none
     */

    public int getLocalLicenseId() {
        return localLicenseId;
    }

    /**
     * Set the target Saxon edition under which this package will execute
     *
     * @param edition the Saxon edition e.g. "HE" or "JS"
     */

    public void setTargetEdition(String edition) {
        this.targetEdition = edition;
    }

    /**
     * Get the target Saxon edition under which this package will execute
     *
     * @return the Saxon edition e.g. "HE" or "JS"
     */

    public String getTargetEdition() {
        return this.targetEdition;
    }

    /**
     * Ask whether the package can be deployed to a different location, with a different base URI
     *
     * @return if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     */

    public boolean isRelocatable() {
        return relocatable;
    }

    /**
     * Say whether the package can be deployed to a different location, with a different base URI
     *
     * @param relocatable if true then static-base-uri() represents the deployed location of the package,
     *                    rather than its compile time location
     */

    public void setRelocatable(boolean relocatable) {
        this.relocatable = relocatable;
    }



    /**
     * Ask whether the package is schema-aware
     * @return true if the package is schema-aware
     */

    public boolean isSchemaAware() {
        return isSchemaAware;
    }

    /**
     * Say whether the package is schema-aware
     * @param schemaAware set to true if the package is schema-aware
     */

    public void setSchemaAware(boolean schemaAware) {
        isSchemaAware = schemaAware;
    }

    /**
     * Get a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @return the decimal format manager for this static context; a newly created empty
     * DecimalFormatManager if none has been supplied
     * @since 9.2
     */

    public DecimalFormatManager getDecimalFormatManager() {
        if (decimalFormatManager == null) {
            decimalFormatManager = new DecimalFormatManager(hostLanguage, 31);
        }
        return decimalFormatManager;
    }

    /**
     * Set a DecimalFormatManager to resolve the names of decimal formats used in calls
     * to the format-number() function.
     *
     * @param manager the decimal format manager for this static context
     */

    public void setDecimalFormatManager(DecimalFormatManager manager) {
        decimalFormatManager = manager;
    }

    /**
     * Get the KeyManager, containing definitions of keys available for use.
     *
     * @return the KeyManager. This is used to resolve key names, both explicit calls
     * on key() used in XSLT, and system-generated calls on key() which may
     * also appear in XQuery and XPath
     */
    public KeyManager getKeyManager() {
        if (keyManager == null) {
            keyManager = new KeyManager(getConfiguration(), this);
        }
        return keyManager;
    }

    /**
     * Set the KeyManager, containing definitions of keys available for use.
     *
     * @param manager the KeyManager. This is used to resolve key names, both explicit calls
     * on key() used in XSLT, and system-generated calls on key() which may
     * also appear in XQuery and XPath
     */
    public void setKeyManager(KeyManager manager) {
        keyManager = manager;
    }

    /**
     * Get the object that manages accumulator functions
     *
     * @return the class that manages accumulators.
     */

    public AccumulatorRegistry getAccumulatorRegistry() {
        return accumulatorRegistry;
    }

    /**
     * Set the object that manages accumulator functions
     *
     * @param accumulatorRegistry the manager of accumulator functions
     */

    public void setAccumulatorRegistry(AccumulatorRegistry accumulatorRegistry) {
        this.accumulatorRegistry = accumulatorRegistry;
    }

    /**
     * Get the SlotManager used to record the names and slot numbers of all global variables
     * @return the SlotManager used for global variables contained in this package
     */

    public SlotManager getGlobalSlotManager() {
        return globalSlotManager;
    }

    /**
     * Set the SlotManager to be used to record the names and slot numbers of all global variables
     * @param manager the SlotManager used for global variables contained in this package
     */

    public void setGlobalSlotManager(SlotManager manager) {
        this.globalSlotManager = manager;
    }

    /**
     * Add a global variable to the list of global variables contained in this package
     * @param variable the global variable to be added
     */

    public void addGlobalVariable(GlobalVariable variable) {
        globalVariables.add(variable);
    }

    /**
     * Get the list of global variables contained in this package
     * @return the list of global variables
     */

    public List<GlobalVariable> getGlobalVariableList() {
        return globalVariables;
    }

    public void setTypeAliasManager(TypeAliasManager manager) {
        this.typeAliasManager = manager;
    }

    /**
     * Get the type alias manager if one already exists, creating it if it does not already exist
     * @return a type alias manager, newly created if necessary
     */

    public TypeAliasManager obtainTypeAliasManager() {
        if (typeAliasManager == null) {
            typeAliasManager = config.makeTypeAliasManager();
        }
        return typeAliasManager;
    }


}


