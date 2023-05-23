# Saxon Home e-Commerce Edition

## Purpose

This temporary fork of Michael Kay's Saxon is just a show case of using Saxon in the e-commerce domain requiring best numeric accuracy.

After convincing [CEN TC 434 WG1](https://standards.cen.eu/dyn/www/f?p=204:22:0::::FSP_ORG_ID,FSP_LANG_ID:1971326,25&cs=1F9CEADFE13744B476C348D55B8E70B74) to add decimal-based floating-point-support as recommendation of the [EU e-invoice standard (EN16931)](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/Compliance+with+eInvoicing+standard), this project aims to enhance [the EN16031 XSLT Schematron validation reference implementation](https://github.com/ConnectingEurope/eInvoicing-EN16931) with the support of decimal-based floating-point.

## Decimal-based floating-point

Decimal-based floating-point was invented for the commercial sector.
It missed the early [IEEE 754 standard](https://ieeexplore.ieee.org/document/8766229) in the late 80ths and still took 20 years until it was embraced by IEEE 754 in 2008.
Now being part of all major libraries as [Java](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/math/BigDecimal.html), [.Net](https://docs.microsoft.com/en-us/dotnet/api/system.decimal?view=net-5.0), [Intel](https://software.intel.com/content/www/us/en/develop/articles/intel-decimal-floating-point-math-library.html), etc.

### Invoice Example

~~~ Java
quantity = 1000000000.0 
priceAmount = 1.0 
baseQuantity = 3
~~~

#### XSLT using binary floating-point

~~~ Java
 $quantity * ($priceAmount div $baseQuantity)) = (1000000000.0 *(1.0 div 3 )) = 333333333.333333333333333333                                                                                                                                                          
($quantity *  $priceAmount div $baseQuantity)  = (1000000000.0 * 1.0 div 3 )  = 333333333.3333333333333333333333333333333333
~~~

##### Accuracy Problem

The above values should be the same, but differ by 0.0000000000000003333333333333333.
In the energy & pharma sector prices with 6 to 9 decimal places are often and also going along with high quantities.
By this, these errors show-up easily on Cent level.

#### XSLT using decimal-based floating-point (IEEE 754:2008 or later)

~~~ Java
 $quantity * ($priceAmount div $baseQuantity)) = (1000000000.0 *(1.0 div 3 )) = 333333333.3333333333333333333333333 
($quantity *  $priceAmount div $baseQuantity)  = (1000000000.0 * 1.0 div 3 )  = 333333333.3333333333333333333333333 
~~~

## For further information on decimal-based floating-point

* [http://speleotrove.com/decimal/decifaq.html](http://speleotrove.com/decimal/decifaq.html)
* [https://github.com/svanteschubert/DecimalFloatingPointExample](https://github.com/svanteschubert/DecimalFloatingPointExample)
* [https://dzone.com/articles/never-use-float-and-double-for-monetary-calculation](https://dzone.com/articles/never-use-float-and-double-for-monetary-calculation)
* [https://blogs.oracle.com/corejavatechtips/the-need-for-bigdecimal](https://blogs.oracle.com/corejavatechtips/the-need-for-bigdecimal)

## How Accuracy was improved in Saxon

This Saxon update is achieved by several minor enhancements:

1. Using solely decimal-based floating-point instead of binary floating-point.
   The fix was to [disable Double creation in NumericValue](https://github.com/svanteschubert/Saxon-HE/commit/fe8ca45c54622b467eb58fbaeae0d3edbe4461c7).
2. [Extending the existing BigDecimal implementation to full floating-point support](https://github.com/svanteschubert/Saxon-HE/commit/70d0a1197e298eb17dacf343553a2873352f2db2).
3. [Adding highest Java precision decimal-based floating-point support to multiplication and division of BigDecimals](https://github.com/svanteschubert/Saxon-HE/commit/68c538a364e8bfd8aa5598077521ad87fb297e88).
4. Added [round-half-up() function](https://docs.oracle.com/javase/8/docs/api/java/math/RoundingMode.html) as integrated extension functions of SAXON, as half-up rounding is the default rounding in EU e-commerce - the rounding that we had learned in school - and now also added as default rounding to the EN16931 specification. The  [W3C XPath round() function](https://www.w3.org/TR/xpath-functions-31/#func-round) is different by always rounding in the direction of positives, e.g. -1.5 becomes -1.

## Building Saxon from latest Sources

As the Saxon HE sources do not exist on GitHub, I downloaded the sources and the pom.xml from the [Maven Repository](https://mvnrepository.com/artifact/net.sf.saxon/Saxon-HE) into a Maven directory structure.
To make the JAR become useable, further artifacts had to be copied from the published SAXON JAR:

* META-INF folder - (but removing all signature information)
* src/main/resources/net/sf/saxon/data/

I have added a [smoke test case](https://github.com/svanteschubert/Saxon-HE/blob/main/src/test/java/net/sf/saxon/DecimalBasedFloatingPointTest.java) to ease debugging from the IDE. The output XML file will be generated as target/generated-sources/out.xml file.
[JDK 1.8](https://openjdk.java.net/install/) is required by the original [Saxon of Saxonica](http://saxon.sourceforge.net/) and [Maven](https://maven.apache.org/download.cgi?Preferred=ftp://ftp.osuosl.org/pub/apache/) as build environment.
Build & smoke test can be executed via command-line by calling: **mvn clean install**

## Updating Saxon Version

There is bash script '[saxon-update.sh](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/saxon-update.sh)', which download the specified Saxon-HE version [from Maven](https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/) and rebase our changes on top of it.

1. Two variables of next & current version of Saxon needs to be adopted (see [Maven for latest version](https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/)).
1. Sometimes there might be merge conflicts if Saxon changed a line we are adopting (the script will stop).
   In this case the last three lines (change of version in pom.xml and its commit) have to be done manually.
1. Test if the sources build & our test runs without error (there are errors in JavaDoc nevermind).
1. Tag manually the latest commit to trigger the GitHub automatic release deployment, see chapter GitHub Actions below.</br>
   **git tag -sm <TAG_MESSAGE> <TAG_LABEL>**</br>
       e.g. "*git tag -sm v10.6 v10.6*" # using -s to sign the tag & -m is taking the next parameter as message


## Git Branches

1. **accuracy-feature** (our feature branch - our feature on top of the Saxon functionality) - ***we only commit to this branch!***</br>
   Our feature branch that will be continously updated.
   Contains the script and everything on top of existing Saxon.
   Will be rebased on top of the SAXON sources (saxon-upstream).
1. **saxon-upstream** (automatic generated - don't touch)</br>
   As Saxon is not available on GitHub we need to create the sources from the Maven source & binary JAR (downloaded, extracted and normalized (dos2unix) via our bash script)
   Only the Java sources of Saxon (without the pom.xml resulting into continous merge conflicts (e.g. version number changes)).
   The required parts of the Maven Saxon sources and binaries JAR are being added on top of this branch.
1. **SAXON-HE-v&lt;VERSION&gt;** (original Saxon sources - ***could be used for other features on top of Saxon***)</br>
   Branch with original Saxon functionality.
   Forks the saxon-upstream of Saxon source & binary JARs with adding first the original Saxon pom.xml also downloaded from Maven.
   With an additional commit overwriting this pom.xml with our feature branch pom.xml to allow the Saxon sources to be able to build.
1. **SAXON-HE-accuracy-v&lt;VERSION&gt;** (our feature-enriched Saxon sources - ***could be used for maintenance***)</br>
This branch provides the maintenance branch of our enriched Saxon sources.
As we are rebasing our feature branch (accuracy-feature) always on top of the saxon-sources (saxon-upstream) all commits will get new hashes and the original branch (and commits) would get lost.
1. **prototyping** (deprecated (pure historical) - don't touch)</br>
   Initial work before it was later refactored to be automated by bash script 'saxon-update.sh'.
1. **basics** (deprecated (pure historical) - don't touch)</br>
   Branch the inital bash script was being started.

## GiHub Actions

There are two GitHub Actions

1. [Build](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/.github/workflows/maven.yml): Triggered by every push or pull-request on the default branch.
2. [Deployment](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/.github/workflows/deployment.yml): Triggered whenever a tag was pushed a GitHub release is being automated made using the version number extracted from the pom.xml file, for instance:
   1. **git tag -sm <TAG_MESSAGE> <TAG_LABEL>**</br> 
       e.g. "*git tag -sm v10.6 v10.6*" # using -s to sign the tag & -m is taking the next parameter as message
   2. **git push --follow-tags** # pushing also all tags
*Note*: The overwrite function does not work a release have to be manually deleted for the same version from pom.xml!

## Reports to Saxonica

* [https://saxonica.plan.io/issues/4823](https://saxonica.plan.io/issues/4823)
* [https://saxonica.plan.io/issues/5195](https://saxonica.plan.io/issues/5195)
