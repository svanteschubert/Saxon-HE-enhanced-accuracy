using System;
using System.Collections.Generic;
using XPathException = net.sf.saxon.trans.XPathException;
using JException = java.lang.Exception;
using JErrorReporter = net.sf.saxon.lib.ErrorReporter;
using JInvalidityHandler = net.sf.saxon.lib.InvalidityHandler;
using JInvalidity = net.sf.saxon.lib.Invalidity;
using JValidationFailure = net.sf.saxon.type.ValidationFailure;
using JValidationException = net.sf.saxon.type.ValidationException;
using JSequence = net.sf.saxon.om.Sequence;
using JTransformerException = javax.xml.transform.TransformerException;
using JSourceLocator = javax.xml.transform.SourceLocator;
using JSaxonApiException = net.sf.saxon.s9api.SaxonApiException;
using JXmlProcessingError = net.sf.saxon.s9api.XmlProcessingError;

namespace Saxon.Api
{

    /// <summary>
	/// The <c>StaticError</c> class contains information about a static error detected during
    /// compilation of a stylesheet, query, or XPath expression.
    /// </summary>

    [Serializable]
    public class StaticError : Exception
    {

        private XPathException exception;
        internal bool isWarning;

        // internal constructor: Create a new StaticError, wrapping a Saxon XPathException

        internal StaticError(JException err)
        {
            if (err is XPathException)
            {
                this.exception = (XPathException)err;
            }
            else
            {
                this.exception = XPathException.makeXPathException(err);
            }
        }

        /// <summary>
		/// The error code, as a <c>QName</c>. May be null if no error code has been assigned.
        /// </summary>

        public QName ErrorCode
        {
            get
            {
                if (exception.getErrorCodeLocalPart() != null) {
                    return new QName("err",
                        exception.getErrorCodeNamespace(),
                        exception.getErrorCodeLocalPart());
                } else {
                    return null;
                }
            }
        }

        /// <summary>
        /// Return the message associated with this error.
        /// </summary>

        public override String Message
        {
            get
            {
                return exception.getMessage();
            }
        }


        /// <summary>
        /// Return the message associated with this error concatenated with the message from the causing exception.
        /// </summary> 
        public String InnerMessage
        {
            get {

                return exception.getMessage() + ": " + exception.getCause().Message;
            }

        }




        /// <summary>
        /// The URI of the query or stylesheet module in which the error was detected
        /// (as a string).
        /// </summary>
        /// <remarks>
        /// May be null if the location of the error is unknown, or if the error is not
        /// localized to a specific module, or if the module in question has no known URI
		/// (for example, if it was supplied as an anonymous <c>Stream</c>).
        /// </remarks>

        public String ModuleUri
        {
            get
            {
                if (exception.getLocator() == null)
                {
                    return null;
                }
                return exception.getLocator().getSystemId();
            }
        }

        /// <summary>
        /// The line number locating the error within a query or stylesheet module.
        /// </summary>
        /// <remarks>
        /// May be set to -1 if the location of the error is unknown.
        /// </remarks>        

        public int LineNumber
        {
            get
            {
                JSourceLocator loc = exception.getLocator();
                if (loc == null)
                {
                    if (exception.getException() is JTransformerException)
                    {
                        loc = ((JTransformerException)exception.getException()).getLocator();
                        if (loc != null)
                        {
                            return loc.getLineNumber();
                        }
                    }
                    return -1;
                }
                return loc.getLineNumber();
            }
        }


        /// <summary>
        /// The line number locating the error within a query or stylesheet module.
        /// </summary>
        /// <remarks>
        /// May be set to -1 if the location of the error is unknown.
        /// </remarks>        

        public int ColumnNumber
        {
            get
            {
                JSourceLocator loc = exception.getLocator();
                if (loc == null)
                {
                    if (exception.getException() is JTransformerException)
                    {
                        loc = ((JTransformerException)exception.getException()).getLocator();
                        if (loc != null)
                        {
                            return loc.getColumnNumber();
                        }
                    }
                    return -1;
                }
                return loc.getColumnNumber();
            }
        }

        /// <summary>
        /// Indicate whether this error is being reported as a warning condition. If so, applications
        /// may ignore the condition, though the results may not be as intended.
        /// </summary>

        public bool IsWarning
        {
            get
            {
                return isWarning;
            }
            set
            {
                isWarning = value;
            }
        }

        /// <summary>
        /// Indicate whether this condition is a type error.
        /// </summary>

        public bool IsTypeError
        {
            get
            {
                return exception.isTypeError();
            }
        }

        /// <summary>
        /// Return the underlying exception. This is unstable as this is an internal object.
        /// </summary>
		/// <returns>The underlying <c>XPathException</c></returns>
        
		public XPathException UnderlyingException
        {
            get
            {
                return exception;
            }
        }

        /// <summary>
        /// Return the error message.
        /// </summary>

        public override String ToString()
        {
            return exception.getMessage();
        }
    }


    /// <summary>
	/// The <c>IErrorReporter</c> is a generic functional interface for reporting errors and warnings.
    /// </summary>
    /// <remarks>
    /// <p>The error or warning is reported in the form of an <c>XmlProcessingError</c> which
    /// adds the possibility to report errors without expensive construction of an exception object.</p>
    /// </remarks>
    public interface IErrorReporter {



        /// <summary>
        /// Report an error. This method is called by Saxon when an error needs to be reported to the calling application.
        /// </summary>
        /// <param name="error">Details of the error to be reported</param>
        /**public**/
        void report(XmlProcessingError error);

    }


    /// <summary>
	/// <c>ErrorReporter</c> wrapper class for internal use in Saxon.
    /// </summary>
	public class ErrorReporterWrapper : JErrorReporter

    {

        private IErrorReporter reporter;

        /// <summary>
		/// Create a standard <c>ErrorReporter</c>
        /// </summary>
		/// <param name="reporter">The .NET <c>IErrorReporter</c></param>
        public ErrorReporterWrapper(IErrorReporter reporter)
        {
            this.reporter = reporter;
        }

        public IErrorReporter ErrorReporter {
            get { return reporter; }
        }

		/// <summary>
		/// This method is called internally in Saxon to report an error by adding it to the error list.
		/// </summary>
		/// <param name="xpe">details of the error to be reported</param>
        public void report(JXmlProcessingError xpe)
        {
            reporter.report(new XmlProcessingError(xpe));
        }
    }


    /// <summary>
	/// The <c>ErrorReporter</c> is a generic functional class for reporting errors and warnings.
	/// This class wraps an <c>IList</c> of <c>XmlProcessingError</c> objects.
    /// It is designed to be used internally by Saxon.
    /// </summary>
    [Serializable]
	public class ErrorReporter : JErrorReporter
    {

        private IList<XmlProcessingError> errorList;

        /// <summary>
        /// Initializes a new instance of the <see cref="Saxon.Api.ErrorReporter"/> class.
        /// </summary>
        /// <param name="errorList">Error list.</param>
        public ErrorReporter(IList<XmlProcessingError> errorList)
        {
            this.errorList = errorList;
        }

        /// <summary>
        /// Get property on the wrapped error list.
        /// </summary>
        public IList<XmlProcessingError> ErrorList {
            get { return errorList; }
        }
        
        /// <summary>
        /// This method is called internally in Saxon to report an error by adding it to the error list.
        /// </summary>
		/// <param name="xpe">details of the error to be reported</param>
        public void report(JXmlProcessingError xpe)
        {
            errorList.Add(new XmlProcessingError(xpe));
        }
    }

    /// <summary>
	/// <c>ErrorReporter</c> for gathering errors as <c>XmlProcessorError</c> to then
	/// convert them to a list of <c>StaticError</c>s.
    /// </summary>
    [Serializable]
	public class ErrorReporterToStaticError : JErrorReporter
    {

        private IList<StaticError> errorList;

        /// <summary>
        /// Initializes a new instance of the <see cref="Saxon.Api.ErrorReporter"/> class.
        /// </summary>
        /// <param name="errorList">Error list.</param>
        public ErrorReporterToStaticError(IList<StaticError> errorList)
        {
            this.errorList = errorList;
        }

        public IList<StaticError> ErrorList
        {
            get { return errorList; }
        }

		/// <summary>
		/// This method is called internally in Saxon to report an error by adding it to the error list.
		/// </summary>
		/// <param name="xpe">details of the error to be reported</param>
        public void report(JXmlProcessingError xpe)
        {
            errorList.Add(new StaticError(net.sf.saxon.trans.XPathException.fromXmlProcessingError(xpe)));
        }
    }


    /// <summary>
	/// The <c>DynamicError</c> class contains information about a dynamic error detected during
    /// execution of a stylesheet, query, or XPath expression.
    /// </summary>

    [Serializable]
    public class DynamicError : Exception
    {

        private XPathException exception;
        internal bool isWarning;

        /// <summary>
		/// Create a new <c>DynamicError</c>, specifying the error message.
        /// </summary>
        /// <param name="message">The error message</param>

        public DynamicError(String message)
        {
            exception = new XPathException(message);
        }

        // internal constructor: Create a new DynamicError, wrapping a Saxon XPathException

        internal DynamicError(JTransformerException err)
        {
            if (err is XPathException)
            {
                this.exception = (XPathException)err;
            }
            else
            {
                this.exception = XPathException.makeXPathException(err);
            }
        }

        // internal constructor: Create a new DynamicError, wrapping an SaxonApiException

        internal DynamicError(JSaxonApiException err)
        {
            if (err.getCause() is XPathException)
            {   
                this.exception = (XPathException)err.getCause();
            }
            else
            {
                this.exception = XPathException.makeXPathException(err);
            }
        }

        /// <summary>
		/// The error code, as a <c>QName</c>. May be null if no error code has been assigned.
        /// </summary>

        public QName ErrorCode
        {
            get
            {

                String errorCode = exception.getErrorCodeLocalPart();
                if (errorCode == null) {
                    return null;
                }
                return new QName("err",
                        exception.getErrorCodeNamespace(),
                        errorCode);
            }
        }

        /// <summary>
        /// Return the message associated with this error.
        /// </summary>

        public override String Message
        {
            get
            {
                return exception.getMessage();
            }
        }

        /// <summary>
        /// The URI of the query or stylesheet module in which the error was detected
        /// (as a string).
        /// </summary>
        /// <remarks>
        /// May be null if the location of the error is unknown, or if the error is not
        /// localized to a specific module, or if the module in question has no known URI
		/// (for example, if it was supplied as an anonymous <c>Stream</c>).
        /// </remarks>

        public String ModuleUri
        {
            get
            {
                if (exception.getLocator() == null)
                {
                    return null;
                }
                return exception.getLocator().getSystemId();
            }
        }

        /// <summary>
        /// The line number locating the error within a query or stylesheet module.
        /// </summary>
        /// <remarks>
        /// May be set to -1 if the location of the error is unknown.
        /// </remarks>        

        public int LineNumber
        {
            get
            {
                JSourceLocator loc = exception.getLocator();
                if (loc == null)
                {
                    if (exception.getException() is JTransformerException)
                    {
                        loc = ((JTransformerException)exception.getException()).getLocator();
                        if (loc != null)
                        {
                            return loc.getLineNumber();
                        }
                    }
                    return -1;
                }
                return loc.getLineNumber();
            }
        }

        /// <summary>
        /// Indicate whether this error is being reported as a warning condition. If so, applications
        /// may ignore the condition, though the results may not be as intended.
        /// </summary>

        public bool IsWarning
        {
            get
            {
                return isWarning;
            }
            set
            {
                isWarning = value;
            }
        }

        /// <summary>
        /// Indicate whether this condition is a type error.
        /// </summary>

        public bool IsTypeError
        {
            get
            {
                return exception.isTypeError();
            }
        }

        /// <summary>
        /// Return the error message.
        /// </summary>

        public override String ToString()
        {
            return exception.getMessage();
        }

        /// <summary>
        /// Return the underlying exception. This is unstable as this is an internal object.
        /// </summary>
		/// <returns>The underlying <c>XPathException</c></returns>
        
		public XPathException UnderlyingException
        {
            get
            {
                return exception;
            }
        }



    }

    /// <summary>
	/// The <c>XmlProcessorError</c> class contains information about an error detected during
    /// compilation or execution of a stylesheet, query, XPath expression, or schema.
    /// </summary>
    public class XmlProcessingError : StaticError
    {
        JXmlProcessingError error;
        internal XmlProcessingError(JXmlProcessingError err) : base(XPathException.fromXmlProcessingError(err))
        {
            this.error = err;
        }

        /// <summary>
		/// This get property returns a <c>XmlProcessingError</c> containing the same information, but to be treated as a warning condition.
        /// </summary>
        public XmlProcessingError AsWarning {
            get {
                XmlProcessingError e2 = new XmlProcessingError(error);
                e2.IsWarning = true;
                return e2;
            }
        }

        /// <summary>
        /// Property to get the host language where this error originates from.
        /// </summary>
        public HostLanguage HostLanguage {

            get {
                if (error.getHostLanguage() == net.sf.saxon.s9api.HostLanguage.XSLT)
                {
                    return HostLanguage.XSLT;
                }
                else {
                    return HostLanguage.XPATH;
                }
            }
        }

        /// <summary>
        /// Property to indicate that this error is to be treated as fatal, that is,
        /// execution will be abandoned after reporting this error. This method may be called
        /// by <c>ErrorReporter</c>, for example if the error is considered so server that further processing
        /// is not worthwhile, or if too many errors have been signalled. there is no absolute guarantee that
        /// setting this property will cause execution to be abandoned.
        /// It can also be used to ask whether this error is to be treated as fatal, 
        /// and if so, return the relevant message.
        /// </summary>
        public String FatalMessage {

            set {
                error.setFatal(value);
            }
            get {
                return error.getFatalErrorMessage();
            }
        }

        /// <summary>
        /// Property to check if this error is a <c>StaticError</c>.
        /// </summary>
        public bool StaticError {
            get { return error.isStaticError(); }
        }

        /// <summary>
        /// Property which indicates whether this condition is a type error.
        /// </summary>
        public bool TypeError {
            get { return error.isTypeError(); }
        }


        /// <summary>
        /// Ask whether this static error has already been reported.
        /// </summary>
        /// <returns></returns>
        public bool IsAlreadyReported() {
            return error.isAlreadyReported();
        }


        /// <summary>
        /// Say whether this error has already been reported.
        /// </summary>
        /// <param name="reported">true if the error has been reported</param>
        public void SetAlreadyReported(bool reported)
        {
            error.setAlreadyReported(reported);
        }

    }


    /// <summary>
    /// This exception indicates a failure when validating an instance against a type
    ///  defined in a schema.
    /// </summary>
	/// <remarks>
	/// This class holds the same information as a <c>ValidationException</c>, except that it is not an exception,
	///  and does not carry system overheads such as a stack trace. It is used because operations such as "castable",
	///  and validation of values in a union, cause validation failures on a success path and it is costly to throw,
	///  or even to create, exception objects on a success path.
	/// </remarks>
    public class ValidationFailure
    {
        private JValidationFailure failure;

        internal ValidationFailure(JValidationFailure failure) {
            this.failure = failure; 
        }

        /// <summary>
        /// Return the character position where the current document event ends.
        /// </summary>
        /// <returns>the column number, or -1 if none is available</returns>
        public int GetColumnNumber()
        {
            return failure.getColumnNumber();
        }

        /// <summary>
        /// Get the constraint clause number.
        /// </summary>
        /// <returns>The section number of the clause containing the constraint that has been violated.
        /// Generally a decimal number in the form n.n.n; possibly a sequence of such numbers separated
        ///  by semicolons.Or null if the information is not available.</returns>
        public String GetConstraintClauseNumber()
        {
            return failure.getConstraintClauseNumber();
        }


        /// <summary>
        /// Get the constraint name.
        /// </summary>
        /// <returns>The name of the violated constraint, in the form of a fragment identifier within
        ///  the published XML Schema specification; or null if the information is not available.</returns>
        public String GetConstraintName()
        {
            return failure.getConstraintName();
        }

        /// <summary>
        /// Get the constraint name and clause in the format defined in XML Schema Part C (Outcome Tabulations).
        /// This mandates the format validation-rule-name.clause-number
        /// </summary>
        /// <returns>The constraint reference, for example "cos-ct-extends.1.2"; or null if the reference
        ///  is not known.</returns>
        public String getConstraintReference()
        {
            return failure.getConstraintReference();
        }

        /// <summary>
        /// Get additional location text, if any.
        /// </summary>
        /// <returns>Additional information about the location of the error, designed to be output
        /// as a prefix to the error message if desired.</returns>
        public String GetContextPath()
        {
            return failure.getContextPath().toString();
        }


        /// <summary>
        /// Get the error code associated with the validity error. This is relevant only when validation
        /// is run from within XSLT or XQuery, which define different error codes for validation errors.
        /// </summary>
        /// <returns>The error code associated with the error, if any. The error is returned as a simple
        /// string if it is in the standard error namespace, or as an EQName (that is Q{uri}local) 
		/// otherwise.</returns>
        public String GetErrorCode()
        {
            return failure.getErrorCode();
        }

        /// <summary>
        /// Return the line number where the current document event ends.
        /// </summary>
        /// <returns>The line number, or -1 if none is available.</returns>
        public int GetLineNumber()
        {
            return failure.getLineNumber();
        }

        /// <summary>
        /// Get the text of a message explaining what is wrong.
        /// </summary>
        /// <returns>A human-readable message explaining the validity error.</returns>
        public String GetMessage()
        {
            return failure.getMessage();
        }

        /// <summary>
        /// Get a hierarchic path giving the logical position in the instance document where the
        /// validation error was found.
        /// </summary>
        /// <returns>A path to the location in the document.</returns>
        public String GetPath()
        {
            return failure.getPath().toString();
        }


        /// <summary>
        /// Return the public identifier for the current document event.
        /// </summary>
        /// <returns>A string containing the public identifier, or null if none is available.</returns>
        public String GetPublicId()
        {
            return failure.getPublicId();
        }

        /// <summary>
        /// Get the "schema part" component of the constraint reference.
        /// </summary>
        /// <returns>1 or 2 depending on whether the violated constraint is in XML Schema Part 1 or Part 2;
        /// or -1 if there is no constraint reference.</returns>
        public int GetSchemaPart()
        {
            return failure.getSchemaPart();
        }

        /// <summary>
        /// Return the system identifier for the current document event.
        /// </summary>
        /// <returns>A string containing the system identifier, or null if none is available.</returns>
        public String GetSystemId()
        {
            return failure.getSystemId();
        }
    }

    /// <summary>
    /// Error gatherer. This class is used to provide customized error handling. 
	/// </summary>
    /// <remarks><para>If an application does <em>not</em> register its own custom
    /// <code>ErrorListener</code>, the default <code>ErrorGatherer</code>
    /// is used which keeps track of all warnings and errors in a list,
    /// and does not throw any <code>Exception</code>s.
    /// Applications are <em>strongly</em> encouraged to register and use
    /// <code>ErrorListener</code>s that insure proper behavior for warnings and
    /// errors.</para>
    /// </remarks>
    [Serializable]
    internal class ErrorGatherer : javax.xml.transform.ErrorListener
    {

        private IList<StaticError> errorList;


        /// <summary>
        /// Initializes a new instance of the <see cref="Saxon.Api.ErrorGatherer"/> class.
        /// </summary>
        /// <param name="errorList">Error list.</param>
        public ErrorGatherer(IList<StaticError> errorList)
        {
            this.errorList = errorList;
        }

        /// <summary>
        /// Warning the specified exception.
        /// </summary>
        /// <param name="exception">TransformerException.</param>
        public void warning(JTransformerException exception)
        {
            StaticError se = new StaticError(exception);
            // if(exception)
            se.IsWarning = true;
            //Console.WriteLine("(Adding warning " + exception.getMessage() + ")");
            errorList.Add(se);
        }

        /// <summary>
        /// Report a Transformer exception thrown.
        /// </summary>
        /// <param name="error">Error.</param>
        public void error(JTransformerException error)
        {
            StaticError se = new StaticError(error);
            se.IsWarning = false;
            //Console.WriteLine("(Adding error " + error.getMessage() + ")");
            errorList.Add(se);
        }

        /// <summary>
        /// Report a fatal exception thrown.
        /// </summary>
        /// <param name="error">TransformerException.</param>
        public void fatalError(JTransformerException error)
        {
            StaticError se = new StaticError(error);
            se.IsWarning = false;
            errorList.Add(se);
            //Console.WriteLine("(Adding fatal error " + error.getMessage() + ")");
        }


        /// <summary>
        /// Gets the error list.
        /// </summary>
        /// <returns>Returns the error list</returns>
        public IList<StaticError> ErrorList {

            get
            {
                return errorList;

            }
        }
    }


	/// <summary>
	/// Interface for reporting validation errors found during validation of an instance document
	/// against a schema.
	/// </summary>
	public interface IInvalidityHandler {

        /// <summary>
        /// At the start of a validation episode, initialize the handler.
        /// </summary>
        /// <param name="systemId">Optional; may be used to represent the destination of any
        /// report produced.</param>
		/**public**/ void startReporting(String systemId);


		/// <summary>
		/// Report a validation error found during validation of an instance document
		/// against a schema.
		/// </summary>
		/// <param name="i">Details of the validation error.</param>
		/**public**/ void reportInvalidity (ValidationFailure i);
		

		/// <summary>
		/// At the end of a validation episode, do any closedown actions, and optionally return
		/// information collected in the course of validation (for example a list of error messages).
		/// </summary>
		/// <returns>A value to be associated with a validation exception. May be the empty sequence.
		/// In the case of the <c>InvalidityReportGenerator</c>, this returns the XML document
		/// containing the validation report. This will be the value returned as the value of
		/// the variable <c>$err:value</c> during try/catch processing.</returns>
		/**public**/ XdmValue endReporting(); 
		



	}


    /// <summary>
	/// This class <c>InvalidityHandlerWrapper</c> extends the standard error handler for errors found during
    /// validation of an instance document against a schema, used if a user specifies the -report option on validate.
    /// Its effect is to output the validation errors found into the filename specified, in an XML format.
	/// This is a wrapper class to wrap a .NET <c>InvalidityHandler</c> class for interfacing within Java.
    /// </summary>
	public class InvalidityHandlerWrapper : JInvalidityHandler
    {

        private IInvalidityHandler inHandler;

        /// <summary>
        /// Create a standard Invalidity Handler.
        /// </summary>
		/// <param name="inHandler">The .NET <c>IInvalidityHandler</c>.</param>
        public InvalidityHandlerWrapper(IInvalidityHandler inHandler) {
            this.inHandler = inHandler;
        }


        /// <summary>
        /// Get the value to be associated with a validation exception. May return null.
		/// In the case of the <c>InvalidityGenerator</c>, this returns the XML document
        /// containing the validation report.
        /// </summary>
        /// <returns>A value (or null). This will be the value returned as the value of the variable
		/// <c>$err:value</c> during try/catch processor.</returns>
        public JSequence endReporting()
        {
            XdmValue value = inHandler.endReporting();

            if (value != null)
            {
                return inHandler.endReporting().Unwrap();
            }
            return null;

        }

        /// <summary>
        /// Receive notification of a validity error.
        /// </summary>
        /// <param name="i">Information about the nature of the invalidity.</param>
        public void reportInvalidity(JInvalidity i)
        {
            if (i is JValidationFailure) { 
                ValidationFailure error = new ValidationFailure((JValidationFailure)i);
                inHandler.reportInvalidity(error);
            }

            
        }


        /// <summary>
        /// At the start of a validation episode, initialize the handler.
        /// </summary>
        /// <param name="systemId">Optional; may be used to represent the destination of any report produced.</param>
        public void startReporting(string systemId)
        {
            inHandler.startReporting(systemId);
        }

        
	}






	/// <summary>
	/// <para>If an application does <em>not</em> register its own custom
	/// <code>InvalidityHandler</code>, the default <code>InvalidityGatherer</code>
	/// is used which keeps track of all warnings and errors in a list,
	/// and does not throw any <code>Exception</code>s.
	/// Applications are <em>strongly</em> encouraged to register and use
	/// <code>InvalidityHandler</code>s that insure proper behavior for warnings and
	/// errors.</para>
	/// </summary>
	[Serializable]
	internal class InvalidityGatherer : JInvalidityHandler
	{


		private IList<ValidationFailure> errorList;



		/// <summary>
		/// Initializes a new instance of the <see cref="Saxon.Api.InvalidityGatherer"/> class.
		/// </summary>

		public InvalidityGatherer(IList<ValidationFailure> errorList)
		{
            this.errorList = errorList;
		}

		public void startReporting(String systemId) {
			//invalidityHandler.startReporting (systemId);
		}

		public JSequence endReporting() {
            //return invalidityHandler.endReporting ();
            return null;
		}

        /// <summary>
		/// List of errors. The caller may supply an empty list before calling <c>Compile</c>;
        /// the processor will then populate the list with error information obtained during
		/// the schema compilation. Each error will be included as an object of type <c>StaticError</c>.
        /// If no error list is supplied by the caller, error information will be written to
        /// the standard error stream.
        /// </summary>
        /// <remarks>
		/// <para>By supplying a custom <c>List</c> with a user-written <c>add()</c> method, it is possible to
        /// intercept error conditions as they occur.</para>
        /// <para>Note that this error list is used only for errors detected while 
        /// using the schema to validate a source document. It is not used to report errors
        /// in the schema itself.</para>
        /// </remarks>

        public IList<ValidationFailure> ErrorList
        {
            set
            {
                errorList = value;
            }
            get
            {
                return errorList;
            }
        }



        /// <summary>
        /// 
        /// </summary>
        /// <param name="failure">net.sf.saxon.type.ValidationFailure.</param>
        public void reportInvalidity(JInvalidity failure)
		{
            ValidationFailure se = new ValidationFailure((JValidationFailure)failure);
            errorList.Add(se);

			//invalidityHandler.reportInvalidity (se);
		}


	}




}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////