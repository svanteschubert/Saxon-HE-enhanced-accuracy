using System;
using System.Text;
using com.saxonica;

namespace Saxon.Cmd
{

    /// <summary>
    /// This class provides the command line interface for the .NET executable
    /// </summary>

    public class DotNetValidate : com.saxonica.Validate
    {

        /// <summary>
        /// Private constructor, ensuring the class can only be used via its "main" method.
        /// </summary>

        private DotNetValidate()
        {
        }

        /// <summary>
        /// Create the configuration. This method is intended to be overridden in a subclass
        /// </summary>

        protected override void setConfiguration()
        {
            base.setConfiguration();
            //config.setPlatform(DotNetPlatform.getInstance());
        }

        /// <summary>
        /// Entry point for use from the .NET command line
        /// <param name="args">command line arguments</param>
        /// </summary>

        public static void Main(String[] args)
        {
            if (args.Length > 0 && args[0] == "-???")
            {
                // Obtain the assembly qualified name of the schema-aware configuration class.
                // This is needed for the build: it must be copied into a string constant in
                // net.sf.saxon.dotnet.DotNetPlatform.java
                Console.WriteLine(typeof(com.saxonica.config.EnterpriseConfiguration).AssemblyQualifiedName);
            }
            new DotNetValidate().doValidate(args);
        }
    }

}