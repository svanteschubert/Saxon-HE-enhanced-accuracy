using System;

using JConfiguration = net.sf.saxon.Configuration;
using JStaticContext = net.sf.saxon.expr.StaticContext;
using JXPathException = net.sf.saxon.trans.XPathException;
using JXPathContext = net.sf.saxon.expr.XPathContext;
using JExtensionFunctionDefinition = net.sf.saxon.lib.ExtensionFunctionDefinition;
using JExtensionFunction = net.sf.saxon.s9api.ExtensionFunction;
using JExtensionFunctionCall = net.sf.saxon.lib.ExtensionFunctionCall;
using JStructuredQName = net.sf.saxon.om.StructuredQName;
using JXdmSequenceIterator = net.sf.saxon.s9api.XdmSequenceIterator;
using JSequenceType = net.sf.saxon.value.SequenceType;
using JSequence = net.sf.saxon.om.Sequence;
using JExpression = net.sf.saxon.expr.Expression;
using JXdmSequenceType = net.sf.saxon.s9api.SequenceType;
using JItem = net.sf.saxon.om.Item;
using JResource = net.sf.saxon.lib.Resource;
using JResourceCollection = net.sf.saxon.lib.ResourceCollection;
using JResourceFactory = net.sf.saxon.lib.ResourceFactory;
using JStandardCollectionFinder = net.sf.saxon.resource.StandardCollectionFinder;
using JCollectionFinder = net.sf.saxon.lib.CollectionFinder;
using System.Collections.Generic;
using net.sf.saxon.om;
using java.util;
using net.sf.saxon;
using net.sf.saxon.resource;

namespace Saxon.Api
{

    /// <summary>
    /// The class <c>StaticContext</c> provides information about the static context of an expression
    /// </summary>

    public class StaticContext {

        private JStaticContext env;

        internal StaticContext(JStaticContext jsc) {
            env = jsc;
        }


        /// <summary>
        /// The URI of the module where an expression appears, suitable for use in diagnostics
        /// </summary>

        public Uri ModuleUri {
            get {
                return new Uri(env.getSystemId());
            }
        }


        /// <summary>
        /// The static base URI of the expression. Often the same as the URI of the containing module,
        /// but not necessarily so, for example in a stylesheet that uses external XML entities or the
        /// <c>xml:base</c> attribute
        /// </summary>

        public Uri BaseUri {
            get {
                return new Uri(env.getStaticBaseURI());
            }
        }

        /// <summary>
        /// Resolve an in-scope namespace prefix to obtain the corresponding namespace URI. If the prefix
        /// is a zero-length string, the default namespace for elements and types is returned.
        /// </summary>
        /// <param name="Prefix">The namespace prefix</param>
        /// <returns>The corresponding namespace URI if there is one, or null otherwise</returns>

        public String GetNamespaceForPrefix(string Prefix) {
            if (Prefix == "") {
                return env.getDefaultElementNamespace();
            }
            try {
                return env.getNamespaceResolver().getURIForPrefix(Prefix, false);
            } catch (JXPathException) {
                return null;
            }
        }

        /// <summary>
        /// The <c>Processor</c> that was used to create the query or stylesheet from which this extension
        /// function was invoked.
        /// </summary>
        /// <remarks>
        /// <para>This property is useful if the extension function wishes to create new nodes (the <code>Processor</code>
        /// can be used to obtain a <code>DocumentBuilder</code>), or to execute XPath expressions or queries.</para>
        /// <para>There may be circumstances in which the <c>Processor</c> is not available, in which case this method
        /// may return null, or may return a different <c>Processor</c>. This will happen only if low-level interfaces
        /// have been used to cause a <c>Configuration</c> to be shared between several <c>Processor</c> instances,
        /// or between a <c>Processor</c> and other applications.</para>
        /// </remarks>

        public Processor Processor
        {
            get
            {
                JConfiguration config = env.getConfiguration();
                Object p = config.getProcessor();
                if (p is Processor)
                {
                    return (Processor)p;
                }
                else
                {
                    return null;
                }

            }
        }

        /// <summary>
        /// The underlying object in the Saxon implementation, an instance of class
		/// <code>net.sf.saxon.expr.StaticContext</code>
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned object (and the objects it provides access to)
		/// is included in the Saxon JavaDoc documentation, see <see cref="net.sf.saxon.expr.StaticContext"/>.
        /// </para>
        /// </remarks>

        public JStaticContext Implementation
        {
            get { return env; }
        }
    }

    /// <summary>
    /// The class <c>DynamicContext</c> provides information about the dynamic context of an expression
    /// </summary>

    public class DynamicContext {

        internal JXPathContext context;

        internal DynamicContext(JXPathContext context) {
            this.context = context;
        }

        /// <summary>
        /// The context item. May be null if no context item is defined
        /// </summary>

        public XdmItem ContextItem {
            get {
                return context.getContextItem() == null ? null : (XdmItem)XdmItem.Wrap(context.getContextItem());
            }
        }

        /// <summary>
        /// The context position (equivalent to the XPath <c>position()</c> function).
        /// </summary>
        /// <remarks>Calling this method throws an exception if the context item is undefined.</remarks>

        public int ContextPosition {
            get {
                return context.getCurrentIterator().position();
            }
        }

        /// <summary>
        /// The context size (equivalent to the XPath <c>last()</c> function).
        /// </summary>
        /// <remarks>Calling this method throws an exception if the context item is undefined.</remarks>

        public int ContextSize {
            get {
                return context.getLast();
            }
        }

        /// <summary>
        /// The underlying object in the Saxon implementation, an instance of class
		/// <code>net.sf.saxon.expr.XPathContext</code>
        /// </summary>
        /// <remarks>
        /// <para>This property provides access to internal methods in the Saxon engine that are
        /// not specifically exposed in the .NET API. In general these methods should be
        /// considered to be less stable than the classes in the Saxon.Api namespace.</para> 
        /// <para>The internal methods follow
        /// Java naming conventions rather than .NET conventions.</para>
        /// <para>Information about the returned object (and the objects it provides access to)
		/// is included in the Saxon JavaDoc documentation, see <see cref="net.sf.saxon.expr.XPathContext"/>
        /// </para>
        /// </remarks>

        public JXPathContext Implementation
        {
            get { return context; }
        }

    }

    /// <summary>
    /// <para>Abstract superclass for user-written extension functions. An extension function may be implemented as a subclass
    /// of this class, with appropriate implementations of the defined methods.</para>
    /// <para>More precisely, a subclass of <c>ExtensionFunctionDefinition</c> identifies a family of extension functions
    /// with the same (namespace-qualified) name but potentially having different arity (number of arguments).</para>
    /// </summary>
    /// <remarks>
    /// <para>A user-defined extension function is typically implemented using a pair of classes: a class that extends 
    /// <code>ExtensionFunctionDefinition</code>, whose purpose is to define the properties of the extension function
    /// (in particular, its signature -- the types of its arguments and result); and a class that extends
    /// <code>ExtensionFunctionCall</code>, whose purpose is to perform the actual evaluation.</para> 
    /// <para>The <code>ExtensionFunctionDefinition</code> is immutable and will normally have a singleton instance
    /// for each subclass; this singleton instance is registered with the <code>Processor</code> to associate the
    /// name of the extension function with its definition.</para>
    /// <para>The <code>ExtensionFunctionCall</code> has one instance for each call on the extension function appearing
    /// in the source code of a stylesheet or query; this instance is created when Saxon calls the method <code>MakeFunctionCall</code>
    /// provided by the <code>ExtensionFunctionDefinition</code> object. The instance of <code>ExtensionFunctionCall</code>
    /// holds information about the static context of the function call, and its <code>Call</code> method is called
    /// (by Saxon) to evaluate the extension function at run-time.</para>
    /// </remarks>

    public abstract class ExtensionFunctionDefinition
    {
        /// <summary>
		/// Read-only property returning the name of the extension function, as a <c>QName</c>.
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>

        public abstract QName FunctionName { get; }

        /// <summary>
        /// Read-only property giving the minimum number of arguments in a call to this extension function.
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>

        public abstract int MinimumNumberOfArguments { get; }

        /// <summary>
        /// Read-only property giving the maximum number of arguments in a call to this extension function.
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
        /// </remarks>

        public abstract int MaximumNumberOfArguments { get; }

        /// <summary>
        /// Read-only property giving the required types of the arguments to this extension function. 
        /// If the number of items in the array is less than the maximum number of arguments, 
		/// then the last entry in the returned <c>ArgumentTypes</c> is assumed to apply to all the rest; 
        /// if the returned array is empty, then all arguments are assumed to be of type <c>item()*</c>
        /// </summary>
        /// <remarks>
        /// A getter for this property must be implemented in every subclass.
		/// </remarks>
		/// <returns>
		/// An array of <c>XdmSequenceType</c> objects representing the required types of the arguments 
		/// to the extension function.
		/// </returns>

        public abstract XdmSequenceType[] ArgumentTypes { get; }

        /// <summary>
        /// Method returning the declared type of the return value from the function. The type of the return
        /// value may be known more precisely if the types of the arguments are known (for example, some functions
		/// return a value that is the same type as the first argument). The method is therefore called supplying the
        /// static types of the actual arguments present in the call.
        /// </summary>
        /// <remarks>
        /// This method must be implemented in every subclass.
        /// </remarks>
        /// <param name="ArgumentTypes">
        /// The static types of the arguments present in the function call
        /// </param>
        /// <returns>
        /// An <c>XdmSequenceType</c> representing the declared return type of the extension function
        /// </returns>

        public abstract XdmSequenceType ResultType(XdmSequenceType[] ArgumentTypes);

        /// <summary>
        /// This property may return true for a subclass if it guarantees that the returned result of the function
        /// will always be of the declared return type: setting this to true by-passes the run-time checking of the type
        /// of the value, together with code that would otherwise perform atomization, numeric type promotion, and similar
        /// conversions. If the value is set to true and the value is not of the correct type, the effect is unpredictable
        /// and probably disastrous.
        /// </summary>
        /// <remarks>
        /// The default value of this property is <c>false</c>. A getter for this property may be implemented in a subclass
        /// to return a different value.
        /// </remarks>

        public virtual Boolean TrustResultType {
            get { return false; }
        }

        /// <summary>
        /// This property must return true for a subclass if the evaluation of the function makes use of the context
        /// item, position, or size from the dynamic context. It should also return true (despite the property name)
        /// if the function makes use of parts of the static context that vary from one part of the query or stylesheet
        /// to another. Setting the property to true inhibits certain Saxon optimizations, such as extracting the call
        /// from a loop, or moving it into a global variable.
        /// </summary>
        /// <remarks>
        /// The default value of this property is <c>false</c>. A getter for this property may be implemented in a subclass
        /// to return a different value.
        /// </remarks>

        public virtual Boolean DependsOnFocus {
            get { return false; }
        }

        /// <summary>
        /// This property should return true for a subclass if the evaluation of the function has side-effects.
        /// Saxon never guarantees the result of calling functions with side-effects, but if this property is set,
        /// then certain aggressive optimizations will be avoided, making it more likely that the function behaves
        /// as expected.
        /// </summary>
        /// <remarks>
        /// The default value of this property is <c>false</c>. A getter for this property may be implemented in a subclass
        /// to return a different value.
        /// </remarks>

        public virtual Boolean HasSideEffects {
            get { return false; }
        }

        /// <summary>
        /// Factory method to create an <c>ExtensionFunctionCall</c> object, representing a specific function call in the XSLT or XQuery
        /// source code. Saxon will call this method once it has identified that a specific call relates to this extension
        /// function.
        /// </summary>
        /// <remarks>
        /// This method must be implemented in every subclass. The implementation should normally instantiate the relevant subclass
        /// of <code>ExtensionFunctionCall</code>, and return the new instance.
        /// </remarks>
        /// <returns>
        /// An instance of the appropriate implementation of <code>ExtensionFunctionCall</code>
        /// </returns>

        public abstract ExtensionFunctionCall MakeFunctionCall();
    }

    /// <summary>
    /// An instance of this class will be created by the compiler for each function call to this extension function
    /// that is found in the source code. The class is always instantiated by calling the method <c>MakeFunctionCall()</c>
    /// of the corresponding <c>ExtensionFunctionDefinition</c>. 
    /// The implementation may therefore retain information about the static context of the
    /// call. Once compiled, however, the instance object must be immutable.
    /// </summary>

    public abstract class ExtensionFunctionCall
    {

        /// <summary>
        /// Method called by the compiler (at compile time) to provide information about the static context of the
        /// function call. The implementation may retain this information for use at run-time, if the result of the
        /// function depends on information in the static context.
        /// </summary>
        /// <remarks>
        /// For efficiency, the implementation should only retain copies of the information that it actually needs. It
        /// is not a good idea to hold a reference to the static context itself, since that can result in a great deal of
        /// compile-time information being locked into memory during run-time execution.
        /// </remarks>
        /// <param name="context">Information about the static context in which the function is called</param>

        public virtual void SupplyStaticContext(StaticContext context)
        {
            // default: no action
        }

        /// <summary>
        /// A subclass must implement this method if it retains any local data at the instance level. On some occasions
        /// (for example, when XSLT or XQuery code is inlined), Saxon will make a copy of an <c>ExtensionFunction</c> object.
        /// It will then call this method on the old object, supplying the new object as the value of the argument, and the
        /// method must copy all local data items from the old object to the new.
        /// </summary>
        /// <param name="destination">The new extension function object. This will always be an instance of the same
        /// class as the existing object.</param>

        public virtual void CopyLocalData(ExtensionFunctionCall destination) { }

        /// <summary>
        /// Method called at run time to evaluate the function.
        /// </summary>
        /// <param name="arguments">The values of the arguments to the function, supplied as iterators over XPath
        /// sequence values.</param>
        /// <param name="context">The dynamic context for evaluation of the function. This provides access
        /// to the context item, position, and size, and if required to internal data maintained by the Saxon
        /// engine.</param>
        /// <returns>An iterator over a sequence, representing the result of the extension function.
        /// Note that Saxon does not guarantee to read this sequence to completion, so calls on the iterator
        /// must have no side-effects. In rare circumstances (for example, when <code>last()</code> is
        /// used) Saxon may clone the returned iterator by calling its <c>GetAnother()</c> method, 
        /// allowing the function results to be read more than once.</returns>

        public abstract IEnumerator<XdmItem> Call(IEnumerator<XdmItem>[] arguments, DynamicContext context);
    }



    /// <summary>
    ///  An instance of CollectionFinder can be registered with the Saxon configuration; it is called in response
    ///  to calls on the fn:collection() and fn:uri-collection() functions.
    ///  When these functions are called, the <c>FindCollection(XPathContext, String)</c> method is
    ///  called to get a <c>ResourceCollection</c>
    ///  object representing the collection of resources identified by the supplied collection URI.
    /// </summary>
    public interface ICollectionFinder
    {


        /// <summary>
        /// Locate the collection of resources corresponding to a collection URI.
        /// </summary>
        /// <param name="context">The XPath dynamic evaluation context</param>
        /// <param name="collectionURI">The collection URI: an absolute URI, formed by resolving the argument
        /// supplied to the fn:collection or fn:uri-collection against the staticbase URI</param>
        /// <returns>a ResourceCollection object representing the resources in the collection identified
        ///  by this collection URI. Result should not be null.</returns>
		/**public**/ IResourceCollection FindCollection(DynamicContext context, String collectionURI);


    }


    /// <summary>
    /// This interface defines a ResourceCollection. This class
    /// is used to map the URI of collection into a sequence of Resource objects.
    /// It is used to support the fn:collection() and fn:uri-collection() functions.
    /// </summary>
    public interface IResourceCollection
    {
        /// <summary>
        /// Get the URI of the collection
        /// </summary>
        /// <returns> The URI as passed to the fn:collection() or fn:uri-collection()
        /// function, resolved if it is relative against the static base URI.
        /// If the collection() or uri-collection() function
        /// was called with no arguments(to get the "default collection") this
        /// will be the URI of the default collection registered with the Configuration.</returns>
		/**public**/ String CollectionURI();

        /// <summary>
        /// Get the URIs of the resources in the collection. This supports the fn:uri-collection()
        /// function.It is not required that all collections expose a list of URIs in this way, or
        /// that the URIs bear any particular relationship to the resources returned by the
        /// getResources() method for the same collection URI.The URIs that are returned should be
        /// suitable for passing to the registered URIResolver (in the case of XML resources),
        /// or the { @link UnparsedTextURIResolver }
        /// (in the case of unparsed text and JSON resources), etc.
        /// </summary>
        /// <param name="context">context the XPath evaluation context</param>
        /// <returns>an iterator over the URIs of the resources in the collection. The URIs are represented
        /// as Strings.They should preferably be absolute URIs.</returns>
		/**public**/ List<String> GetResourceURIs(DynamicContext context);

        /// <summary>
        /// Get the resources in the collection. This supports the fn:collection() function. It is not
        /// required that all collections expose a set of resources in this way, or that the resources
        /// returned bear any particular relationship to the URIs returned by the getResourceURIs() method
        /// for the same collection URI.
        /// </summary>
        /// <param name="context">the XPath evaluation context</param>
        /// <returns>a List over the resources in the collection. This returns objects of class
        /// <c>Riesource</c>.</returns>
		/**public**/ List<IResource> GetResources(DynamicContext context);


    }


    /// <summary>
    /// This class represents a resource collection containing all, or selected, files within a filestore directory
    /// </summary>
    public class DirectoryCollection : ResourceCollection
    {
       
        /// <summary>
        /// Create a directory collection
        /// </summary>
        /// <param name="proc">Processor object for configuration properties</param>
        /// <param name="collectionURI">the collection URI</param>
        /// <param name="directoryName">the directory containing the files</param>
        /// <param name="parameters">query parameter supplied as part of the URI</param>
        public DirectoryCollection(Processor proc, String collectionURI, String directoryName, URIQueryParameters parameters)
        {
            resourceCollection = new net.sf.saxon.resource.DirectoryCollection(proc.Implementation, collectionURI, new  java.io.File(directoryName), parameters.Implementation());
            
        }

     
    }

    /// <summary>
    /// A JarCollection represents a collection of resources held in a JAR or ZIP archive, accessess typically
    /// using a URI using the "jar" URI scheme, or simply a "file" URI where the target file is a JAR or ZIP file.
    /// </summary>
    public class JarCollection : ResourceCollection
    {

		/// <summary>
		/// Create a JarCollection
		/// </summary>
		/// <param name="context">The XPath dynamic context</param>
		/// <param name="collectionURI">the collection URI used to identify this collection 
		/// (typically but not necessarily the location of the JAR file)</param>
		/// <param name="parameters">URI query parameters appearing on the collection URI</param>
        public JarCollection(DynamicContext context, String collectionURI, URIQueryParameters parameters)
        {
            resourceCollection = new net.sf.saxon.resource.JarCollection(context.context, collectionURI, parameters.Implementation());

        }

    }

	/// <summary>
	/// CatalogCollection
	/// </summary>
    public class CatalogCollection : ResourceCollection
    {

        /// <summary>
        /// Constructor
        /// </summary>
        /// <param name="proc">Saxon Processor object</param>
        /// <param name="collectionURI">The URI for the collection</param>
        public CatalogCollection(Processor proc, String collectionURI)
        {
            resourceCollection = new net.sf.saxon.resource.CatalogCollection(proc.Implementation, collectionURI);

        }


    }


    /// <summary>
    /// Default implementation of the CollectionFinder interface. 
    /// </summary>
    public class StandardCollectionFinder : ICollectionFinder
    {

        JStandardCollectionFinder collectionFinder;

        /// <summary>
        /// Default constructor to create a wrapped Java StandardCollectionFinder
        /// </summary>
        public StandardCollectionFinder() {
            collectionFinder = new JStandardCollectionFinder();
        }

        internal StandardCollectionFinder(JCollectionFinder cf)
        {
            collectionFinder = (JStandardCollectionFinder)cf;
        }

        /// <summary>
        /// Locate the collection of  resources corresponding to a collection URI
        /// </summary>
        /// <param name="context">The XPath dynamic evaluation context</param>
        /// <param name="collectionURI">The collection URI: an absolute URI, formed by resolving the argument
        /// supplied to the fn:collection or fn:uri-collection against the static base URI</param>
        /// <returns>a ResourceCollection object representing resources in the collection identified by this collection URI</returns>
        public IResourceCollection FindCollection(DynamicContext context, string collectionURI)
        {
            return new ResourceCollection(collectionFinder.findCollection(context.context, collectionURI));
        }

        /// <summary>
        /// The underlying java CollectionFinder object
        /// </summary>
        public JCollectionFinder Implementation {
            get {
                return collectionFinder;
            }
        }

        /// <summary>
        /// Register a specific URI and bind it to a specific ResourceCollection
        /// </summary>
        /// <param name="collectionURI">collectionURI the collection URI to be registered. Must not be null.</param>
        /// <param name="collection">collection the ResourceCollection to be associated with this URI. Must not be null.</param>
        public void RegisterCollection(String collectionURI, IResourceCollection collection) {

            collectionFinder.registerCollection(collectionURI, new ResourceCollectionWrapper(collection));

        }

    }


    internal class ResourceCollectionWrapper : JResourceCollection
    {

        IResourceCollection resourceCollection;

        public ResourceCollectionWrapper(IResourceCollection rc) {
            resourceCollection = rc;
        }

        public string getCollectionURI()
        {
            return resourceCollection.CollectionURI();
        }

        public Iterator getResources(JXPathContext xpc)
        {
            java.util.List iter = new java.util.ArrayList();
            if (resourceCollection != null)
            {
                List<IResource> list = resourceCollection.GetResources(new DynamicContext(xpc));
                foreach (IResource src in list)
                {
                    iter.add(new ResourceWrapper(src));
                }
            }

            return iter.iterator();

        }

        public Iterator getResourceURIs(JXPathContext xpc)
        {
            java.util.List iter = new java.util.ArrayList();
            if (resourceCollection != null)
            {
                List<string> list = resourceCollection.GetResourceURIs(new DynamicContext(xpc));
                foreach (string src in list)
                {
                    iter.add(src);
                }
            }

            return iter.iterator();
        }

        public bool isStable(JXPathContext xpc)
        {
           return false;
        }

        public bool stripWhitespace(net.sf.saxon.om.SpaceStrippingRule ssr)
        {
            return false;
        }
    }


    /// <summary>
    /// This interface defines a Resource. The Resource objects belong to a collection.
    /// It is used to support the fn:collection() and fn:uri-collection() functions.
    /// <para>It is recommended (but is not universally the case) that fetching (and where necessary parsing)
    /// the content of a Resource should be delayed until the <c>getItem</c> method is called. This means
    /// that errors in fetching the resource or parsing its contents may go undetected until the resource
    /// is materialized in this way.</para>
    /// </summary>
    public interface IResource
    {
        /// <summary>
        /// Get a URI that identifies this resource
        /// </summary>
        /// <returns>a URI identifying the resource</returns>
		/**public**/ String GetResourceURI();

        /// <summary>
        /// Get an XDM Item holding the contents of this resource.
        /// </summary>
        /// <param name="context">the XPath evaluation context</param>
        /// <returns>an item holding the contents of the resource. The type of item will
        /// reflect the type of the resource: a document node for XML resources, a string
        /// for text resources, a map or array for JSON resources, a base64Binary value
        /// for binary resource.May also return null if the resource cannot be materialized
        /// and this is not to be treated as an error.</returns>
		/**public**/ XdmItem GetXdmItem(DynamicContext context);

        /// <summary> 
        /// Get the media type (MIME type) of the resource if known
        /// </summary>
        /// <returns>the media type if known; otherwise null</returns>
		/**public**/ String GetContentType();
    }

	/// <summary>
	/// ResourceCollection class that implements the IResourcecollection interface.
    /// This class is used to map the URI of collection into a sequence of Resource objects.
    /// It is used to support the fn:collection() and fn:uri-collection() functions.
	/// </summary>
    public class ResourceCollection : IResourceCollection
    {
		protected JResourceCollection resourceCollection;

        internal ResourceCollection()
        {
        }


		internal ResourceCollection(JResourceCollection rc) {
            resourceCollection = rc;
        }

        /// <summary>
        /// Get the URI of the collection
        /// </summary>
        /// <returns>The URI as passed to the fn:collection() or fn:uri-collection() function, resolved if it is relative
        /// against the static base URI. If the collection() or uri-collection() function was called with no argument (to get the 
        /// 'default collection') this will be the URI of the default collection registered with the Configuration</returns>
        public string CollectionURI()
        {
            return resourceCollection.getCollectionURI();
        }

        /// <summary>
        /// Get the URIs of the resources in the collection.  It is
        /// not required that all collections expose a list of URIs in this way, or that the URIs bear any particular
        /// relationship to the resources returned by the getResources() method for the same collection URI.
        /// </summary>
        /// <param name="context">the XPath evaluation context</param>
        /// <returns>List over the URIs of the resources in the collection. The URIs are represented as strings.
        /// They should preferably be absolute URIs.</returns>
        public List<string> GetResourceURIs(DynamicContext context)
        {
            List<string> list = new List<string>();
            if (resourceCollection != null)
            {
                var jIter = resourceCollection.getResourceURIs(context.context);
                while (jIter.hasNext())
                {

                    string uri = (string)jIter.next();
                    list.Add(uri);
                }
            }
            return list;
        }

        /// <summary>
        /// Get the resources in the collection. This supports the fn:collection() function. It is not
        /// required that all collections expose a set of resources in this way, or that the resources
        /// returned bear any particular relationship to the URIs returned by the getResourceURIs() method
        /// for the same collection URI.
        /// </summary>
        /// <param name="context">context the XPath evaluation context</param>
        /// <returns>a list over the resources in the collection. This returns objects of implementations of the interface <code>IResource</code></returns>
        public List<IResource> GetResources(DynamicContext context)
        {
            List<IResource> list = new List<IResource>();
            if (resourceCollection != null)
            {
                var jIter = resourceCollection.getResources(context.context);
                while (jIter.hasNext())
                {

                    JResource jresource = (JResource)jIter.next();
                    list.Add(new Resource(jresource));
                }
            }
            return list;
        }

        public bool IsStable(DynamicContext context)
        {
            return resourceCollection.isStable(context.context);
        }


    }

    /// <summary>
    /// A ResourceFactory is used for constructing a particular type of resource
    /// </summary>
    public interface IResourceFactory {


        /// <summary>
        /// Create a Resource with given content
        /// </summary>
        /// <param name="proc">the Saxon Processor</param>
        /// <returns>the resource</returns>
		/**public**/ IResource MakeResource(Processor proc);

    }

    internal class ResourceFactoryWrapper : JResourceFactory
    {
        private IResourceFactory resourceFactory;
        private Processor proc;

        internal ResourceFactoryWrapper(IResourceFactory factory, Processor proc) {
            this.resourceFactory = factory;
        }

        public JResource makeResource(JConfiguration c, AbstractResourceCollection.InputDetails details)
        {
            return new ResourceWrapper(resourceFactory.MakeResource(proc));
        }
    }

    /// <summary>
    /// Resource Wrapper class for a Java Resource object
    /// </summary>
    public class Resource : IResource
    {
        internal JResource resource = null;

        //Default constructor
        public Resource() 
		{ 
			resource = null; 
		}

        /// <summary>
        /// Constructor to  wrap a Java Resource
        /// </summary>
        /// <param name="rc">Java Resource object</param>
        public Resource(JResource rc) 
		{
            resource = rc;
        }

        /// <summary>
        /// Get the media type (MIME type) of the resource if known
        /// </summary>
        /// <returns>the media type if known; otherwise null</returns>
        public string GetContentType()
        {
            return resource.getContentType();
        }

        /// <summary>
        /// Get a URI that identifies this resource
        /// </summary>
        /// <returns>a URI identifying this resource</returns>
        public string GetResourceURI()
        {
            return resource.getResourceURI();
        }

        public XdmItem GetXdmItem(DynamicContext context)
        {
            return XdmItem.FromGroundedValue(resource.getItem(context.context).materialize()).ItemAt(0);
        }
    }

	/// <summary>
	/// BinaryResource
	/// </summary>
    public class BinaryResource : Resource {


        /// <summary>
        /// Create a binary resource
        /// </summary>
        /// <param name="href">href of the resource</param>
        /// <param name="contentType">The MIME  type for the Binary resource</param>
        /// <param name="content">content given as byte array</param>
        public BinaryResource(String href, String contentType, byte[] content) {
            resource = new net.sf.saxon.resource.BinaryResource(href, contentType, content); 
        }


    }


    /// <summary>
    /// The class is an implementation of the generic Resource object (typically an item in a collection)
    /// representing an XML document
    /// </summary>
    public class XmlResource : Resource
    {


        /// <summary>
        /// Constructor to create an  Xml resource using a specific node
        /// </summary>
        /// <param name="doc">the node in question (usually but not necessarily a document node)</param>
        public XmlResource(XdmNode doc)
        {
            resource = new net.sf.saxon.resource.XmlResource(doc.Implementation);
        }


    }


    internal class ResourceWrapper : JResource
    {

        IResource resource;

        public ResourceWrapper(IResource resource) {
            this.resource = resource;
        }
        public string getContentType()
        {
            return resource.GetContentType();
        }

        public JItem getItem(JXPathContext xpc)
        {
            return (JItem) resource.GetXdmItem(new DynamicContext(xpc)).Unwrap();
        }

        public string getResourceURI()
        {
            return resource.GetResourceURI();
        }
    }
    

    internal class CollectionFinderWrapper : JCollectionFinder
    {

        ICollectionFinder collectionFinder;

        public CollectionFinderWrapper(ICollectionFinder cf) {
            collectionFinder = cf;
        }

        public JResourceCollection findCollection(JXPathContext xpc, string str)
        {
            IResourceCollection rcollection = collectionFinder.FindCollection(new DynamicContext(xpc), str);

            return new ResourceCollectionWrapper(rcollection);
        }
    }

    /// <summary>
    /// This is an interface for simple external/extension functions.
    /// Users can implement this interface and register the implementation with the <see cref="Saxon.Api.Processor"/>;
    /// the function will then be available for calling from all queries, stylesheets, and XPath expressions compiled
    /// under this Processor.
    /// </summary>
    /// <remarks>
    /// <para>Extension functions implemented using this interface are expected to be free of side-effects,
    /// and to have no dependencies on the static or dynamic context. A richer interface for extension
    /// functions is provided via the <see cref="Saxon.Api.ExtensionFunctionDefinition"/> class.</para>
    /// </remarks>

    public interface ExtensionFunction
    {

        /// <summary>
        /// Return the name of the external function
        /// </summary>
        /// <returns>the name of the function, as a <c>QName</c></returns>

		/**public**/ 
		QName GetName();


        /// <summary>
        /// Declare the result type of the external function
        /// </summary>
        /// <returns>the result type of the external function</returns>

		/**public**/ 
		XdmSequenceType GetResultType();

        /// <summary>
        /// Declare the type of the arguments
        /// </summary>
        /// <returns>an array of <c>XdmSequenceType</c> objects, one for each argument to the function,
        /// representing the expected types of the arguments</returns>

		/**public**/ 
		XdmSequenceType[] GetArgumentTypes();


        /// <summary>
        /// Method called at run time to evaluate the function.
        /// </summary>
        /// <param name="arguments">The values of the arguments supplied in the XPath function call.</param>
        /// <returns>An <c>XdmValue</c>, representing the result of the extension function. 
        /// (Note: in many cases it will be convenient to return an <c>XdmAtomicValue</c> or <c>XdmNode</c>, 
        /// both of which are instances of <c>XdmValue</c>).</returns>

		/**public**/ 
		XdmValue Call(XdmValue[] arguments);
    }



    internal class WrappedExtensionFunction : JExtensionFunction
    {
        ExtensionFunction definition;

        public WrappedExtensionFunction(ExtensionFunction definition)
        {
            this.definition = definition;
        }

        public net.sf.saxon.s9api.XdmValue call(net.sf.saxon.s9api.XdmValue[] xvarr)
        {
            XdmValue[] values = new XdmValue[xvarr.Length];
            int len = xvarr.Length;
            for (int i = 0; i < len; i++) {
                values[i] = XdmValue.Wrap(xvarr[i].getUnderlyingValue());
            }
            try {
                XdmValue result = definition.Call(values);
                return XdmValue.FromGroundedValueToJXdmValue(result.value);
            }
            catch (Exception ex) {
                throw new DynamicError(ex.Message);
            }
            
            }

        public JXdmSequenceType[] getArgumentTypes()
        {
            XdmSequenceType [] types = definition.GetArgumentTypes();
            JXdmSequenceType[] results = new JXdmSequenceType[types.Length];
            for (int i = 0; i < types.Length; i++) {
                results[i] = types[i].ToJXdmSequenceType();
            }
            return results;
        }

        public net.sf.saxon.s9api.QName getName()
        {
            return definition.GetName().UnderlyingQName();
        }

        public JXdmSequenceType getResultType()
        {
            JXdmSequenceType declaredResult = definition.GetResultType().ToJXdmSequenceType();
            return declaredResult;
        }
    }

        internal class WrappedExtensionFunctionDefinition : JExtensionFunctionDefinition
    {
        ExtensionFunctionDefinition definition;

        public WrappedExtensionFunctionDefinition(ExtensionFunctionDefinition definition)
        {
            this.definition = definition;
        }

        public override JStructuredQName getFunctionQName()
        {
            return definition.FunctionName.ToStructuredQName();
        }

        public override int getMinimumNumberOfArguments()
        {
            return definition.MinimumNumberOfArguments;
        }

        public override int getMaximumNumberOfArguments()
        {
            return definition.MaximumNumberOfArguments;
        }

        public override JSequenceType[] getArgumentTypes()
        {
            XdmSequenceType[] dt = definition.ArgumentTypes;
            JSequenceType[] jt = new JSequenceType[dt.Length];
            for (int i = 0; i < dt.Length; i++)
            {
                jt[i] = dt[i].ToSequenceType();
            }
            return jt;
        }

        public override JSequenceType getResultType(JSequenceType[] argumentTypes)
        {
            XdmSequenceType[] dt = new XdmSequenceType[argumentTypes.Length];
            for (int i = 0; i < dt.Length; i++)
            {
                dt[i] = XdmSequenceType.FromSequenceType(argumentTypes[i]);
            }

            XdmSequenceType rt = definition.ResultType(dt);
            return rt.ToSequenceType();
        }

        public override Boolean trustResultType()
        {
            return definition.TrustResultType;
        }

        public override Boolean dependsOnFocus()
        {
            return definition.DependsOnFocus;
        }

        public override Boolean hasSideEffects()
        {
            return definition.HasSideEffects;
        }

        public override JExtensionFunctionCall makeCallExpression()
        {
            return new WrappedExtensionFunctionCall(definition.MakeFunctionCall());
        }

    }

    internal class Mapper : net.sf.saxon.dotnet.DotNetIterator.Mapper
    {
        public object convert(object obj)
        {
            XdmItem i = (XdmItem)obj;
            return (JItem)i.Unwrap();
        }
    }

    internal class WrappedExtensionFunctionCall : JExtensionFunctionCall {

        ExtensionFunctionCall functionCall;

        public WrappedExtensionFunctionCall(ExtensionFunctionCall call)
        {
            this.functionCall = call;
        }
        
        public override void supplyStaticContext(JStaticContext context, int locationId, JExpression[] arguments)
        {
            StaticContext sc = new StaticContext(context);
            functionCall.SupplyStaticContext(sc);
        }

        public override void copyLocalData(JExtensionFunctionCall destination)
        {
            functionCall.CopyLocalData(((WrappedExtensionFunctionCall)destination).functionCall);
        }

        public override JSequence call(JXPathContext context, JSequence [] argument)
        {
            SequenceEnumerator<XdmItem>[] na = new SequenceEnumerator<XdmItem>[argument.Length];
            for (int i = 0; i < na.Length; i++)
            {
                na[i] = new SequenceEnumerator<XdmItem>((JXdmSequenceIterator)XdmValue.FromGroundedValueToJXdmValue(argument[i].materialize()).iterator());
            }
            DynamicContext dc = new DynamicContext(context);
            IEnumerator<XdmItem> result = functionCall.Call(na, dc);
            return new net.sf.saxon.om.LazySequence(new net.sf.saxon.om.IteratorWrapper(new net.sf.saxon.dotnet.DotNetIterator(result, new Mapper())));
        }
    }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2020 Saxonica Limited.
// This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
// If a copy of the MPL was not distributed with this file, You can obtain one at http://mozilla.org/MPL/2.0/.
// This Source Code Form is "Incompatible With Secondary Licenses", as defined by the Mozilla Public License, v. 2.0.
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
