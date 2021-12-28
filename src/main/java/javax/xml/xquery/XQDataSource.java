/*
 * Copyright � 2003, 2004, 2005, 2006, 2007, 2008 Oracle.  All rights reserved.
 */

package javax.xml.xquery;

import java.sql.Connection;
import java.util.Properties;

/**
 * An <code>XQDataSource</code> is a factory for <code>XQConnection</code>
 * objects. The datasource may be obtained from a JNDI source or through other means.
 * <br/>
 * The XQuery connection (<code>XQConnection</code>) objects may be created using
 * an existing JDBC connection. This is an optional feature that may not be
 * supported by all implementations. If the implementation supports this
 * mechanism, then the XQuery connection may inherit some of the JDBC
 * connection's properties, such as login timeout, log writer etc.
 * <br/>
 */
public interface XQDataSource {
    /**
     * Attempts to create a connection to an XML datasource.
     *
     * @return a connection to the XML datasource
     * @throws XQException if a datasource access error occurs
     */
    public XQConnection getConnection() throws XQException;

    /**
     * Attempts to create a connection to an XML datasource using an
     * existing JDBC connection.
     * An XQJ implementation is not required to support this method.
     * If it is not supported, then an exception (<code>XQException</code>)
     * is thrown. The XQJ and JDBC connections will operate under the same
     * transaction context.
     *
     * @param con an existing JDBC connection
     * @return a connection to the XML datasource
     * @throws XQException if (1) a datasource access error occurs,
     *                     (2) the implementation does not support
     *                     this method of getting an
     *                     <code>XQConnection</code>, or (3) if
     *                     the <code>con</code> parameter is <code>null</code>
     */
    public XQConnection getConnection(Connection con) throws XQException;

    /**
     * Attempts to establish a connection to an XML datasource using the
     * supplied username and password.
     *
     * @param username the user on whose behalf the connection
     *                 is being made
     * @param passwd   the user's password
     * @return a connection to the XML datasource
     * @throws XQException if a datasource access error occurs
     */
    public XQConnection getConnection(String username, String passwd)
            throws XQException;

    /**
     * Gets the maximum time in seconds that this datasource can wait while
     * attempting to connect to a database.
     * A value of zero means that the timeout is the default system timeout
     * if there is one; otherwise, it means that there is no timeout.
     * When a XQDataSource object is created, the login timeout is
     * initially zero.
     * It is implementation-defined whether the returned login timeout is
     * actually used by the data source implementation.
     *
     * @return the datasource login time limit
     * @throws XQException if a datasource access error occurs
     */
    public int getLoginTimeout() throws XQException;

    /**
     * Retrieves the log writer for this <code>XQDataSource</code> object.
     * The log writer is a character output stream to which all logging and
     * tracing messages for this datasource will be printed. This includes
     * messages printed by the methods of this object, messages printed by
     * methods of other objects manufactured by this object, and so on.
     * When a <code>XQDataSource</code> object is created, the log writer is
     * initially <code>null</code>; in other words, the default is for logging
     * to be disabled.
     *
     * @return the log writer for this datasource or
     *         <code>null</code> if logging is disabled
     * @throws XQException if a datasource access error occurs
     */
    public java.io.PrintWriter getLogWriter() throws XQException;

    /**
     * Returns an array containing the property names supported by this
     * <code>XQDataSource</code>.
     * Implementations that support user name and password must recognize
     * the user name and password properties listed below.
     * <br>
     * <br>
     * <table>
     * <tr><td><code>user</code></td>
     * <td>the user name to use for creating a connection</td></tr>
     * <tr><td><code>password</code></td>
     * <td>the password to use for creating a connection</td></tr>
     * </table>
     * <br>
     * Any additional properties are implementation-defined.
     *
     * @return <code>String[]</code> an array of property names
     *         supported by this implementation
     */
    public String[] getSupportedPropertyNames();

    /**
     * Sets the named property to the specified value.
     * If a property with the same name was already set, then this method
     * will override the old value for that property with the new value.<p>
     * If the implementation does not support the given property or if it
     * can determine that the value given for this property is invalid, then
     * an exception is thrown. If an exception is thrown, then no previous
     * value is overwritten.
     *
     * @param name  the name of the property to set
     * @param value the value of the named property
     * @throws XQException if (1) the given property is not recognized,
     *                     (2) the value for the given property is
     *                     determined to be invalid, or (3) the <code>name</code>
     *                     parameter is <code>null</code>
     */
    public void setProperty(String name, String value) throws XQException;

    /**
     * Returns the current value of the named property if set, else
     * <code>null</code>. If the implementation does not support the
     * given property then an exception is raised.
     *
     * @param name the name of the property whose value is
     *             needed
     * @return <code>String</code> representing the value of
     *         the required property if set, else
     *         <code>null</code>
     * @throws XQException if (1) a given property is not supported, or
     *                     (2) the name parameter is <code>null</code>
     */
    public String getProperty(String name) throws XQException;

    /**
     * Sets the data source properties from the specified <code>Properties</code>
     * instance.  Properties set before this call will still apply but
     * those with the same name as any of these properties will be replaced.
     * Properties set after this call also apply and may
     * replace properties set during this call.<p>
     * If the implementation does not support one or more of the given
     * property names, or if it can determine that the value given for a
     * specific property is invalid, then an exception is thrown. If an
     * exception is thrown, then no previous value is overwritten.
     * is invalid, then an exception is raised.
     *
     * @param props the list of properties to set
     * @throws XQException if (1) a given property is not recognized,
     *                     (2) the value for a given property is
     *                     determined to be invalid, or (3) the
     *                     <code>props</code> parameter is <code>null</code>
     */
    public void setProperties(Properties props) throws XQException;

    /**
     * Sets the maximum time in seconds that this datasource will wait while
     * attempting to connect to a database. A value of zero specifies that
     * the timeout is the default system timeout if there is one; otherwise,
     * it specifies that there is no timeout. When a <code>XQDataSource</code>
     * object is created, the login timeout is initially zero.
     * It is implementation-defined whether the specified login timeout is
     * actually used by the data source implementation. If the connection is
     * created over an existing JDBC connection, then the login timeout
     * value from the underlying JDBC connection may be used.
     *
     * @param seconds the datasource login time limit
     * @throws XQException if a datasource access error occurs
     */
    public void setLoginTimeout(int seconds) throws XQException;

    /**
     * Sets the log writer for this <code>XQDataSource</code> object to the given
     * <code>java.io.PrintWriter</code> object. The log writer is a character output
     * stream to which all logging and tracing messages for this datasource
     * will be printed. This includes messages printed by the methods of this
     * object, messages printed by methods of other objects manufactured by
     * this object, and so on. When a <code>XQDataSource</code> object is created
     * the log writer is initially <code>null</code>; in other words, the default
     * is for logging to be disabled.
     *
     * @param out the new log writer; to disable logging, set to
     *            <code>null</code>
     * @throws XQException if a datasource access error occurs
     */
    public void setLogWriter(java.io.PrintWriter out) throws XQException;
}

