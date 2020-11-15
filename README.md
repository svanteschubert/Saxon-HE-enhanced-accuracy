# Saxon Home 10.3 e-Commerce Edition 

## Purpose
This temporary fork of Michael Kay's Saxon is just a show case of using Saxon in the e-commerce domain requiring best numeric accuracy.

After convincing [CEN TC 434 WG1](https://standards.cen.eu/dyn/www/f?p=204:22:0::::FSP_ORG_ID,FSP_LANG_ID:1971326,25&cs=1F9CEADFE13744B476C348D55B8E70B74) to add decimal-based floating-point-support as recommendation of the [EU e-invoice standard (EN16931)](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/Compliance+with+eInvoicing+standard), this project aims to enhance [the EN16031 XSLT Schematron validation reference implementation](https://github.com/ConnectingEurope/eInvoicing-EN16931) with the support of decimal-based floating-point.

## Decimal-based floating-point

Decimal-based floating-point was invented for the commercial sector.
It missed the early [IEEE 754 standard](https://ieeexplore.ieee.org/document/8766229) in the late 80ths and still took 20 years until it was embraced by IEEE 754 in 2008.
Now being part of all major libraries as [Java](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/math/BigDecimal.html), [.Net](https://docs.microsoft.com/en-us/dotnet/api/system.decimal?view=net-5.0), etc.

### Invoice Example

~~~
quantity = 1000000000.0 
priceAmount = 1.0 
baseQuantity = 3
~~~

#### XSLT using binary floating-point

~~~
 $quantity * ($priceAmount div $baseQuantity)) = (1000000000.0 *(1.0 div 3 )) = 333333333.333333333333333333                                                                                                                                                          
($quantity *  $priceAmount div $baseQuantity)  = (1000000000.0 * 1.0 div 3 )  = 333333333.3333333333333333333333333333333333
~~~

##### Accuracy Problem

The above values should be the same, but differ by 0.0000000000000003333333333333333.
In the energy & pharma sector prices with 6 to 9 decimal places are often and also going along with high quantities. 
By this, these errors show-up easily on Cent level.

#### XSLT using decimal-based floating-point (IEEE 754:2008 or later)
~~~
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
4. **NOTE:** As half-up rounding is the default in EU e-commerce and now also added as default rounding to the EN16931 specification, this implementation is not implementing the [W3C XSLT round() function](https://www.w3.org/TR/xpath-functions-31/#func-round) but using a [half-up rounding](https://docs.oracle.com/javase/8/docs/api/java/math/RoundingMode.html) instead. This hack was necessary as SAXON user functions are AFAIK not an open-source feature, but hopefully Saxon will offer free rounding functions in the future at least [half-up](https://docs.oracle.com/javase/7/docs/api/java/math/RoundingMode.html#HALF_UP) for Europe and [half-even](https://docs.oracle.com/javase/7/docs/api/java/math/RoundingMode.html#HALF_EVEN) for the US.

## Building Saxon from latest Sources

As the Saxon HE sources do not exist on GitHub, I downloaded the sources and the pom.xml from the [Maven Repository](https://mvnrepository.com/artifact/net.sf.saxon/Saxon-HE) into a Maven directory structure.
To make the JAR become useable, further artifacts had to be copied from the binary JAR:

* META-INF folder - (but removing all signature information)
* src/main/resources/net/sf/saxon/data/

I have added a [smoke test case](https://github.com/svanteschubert/Saxon-HE/blob/main/src/test/java/net/sf/saxon/DecimalBasedFloatingPointTest.java) to ease debugging from the IDE. The output XML file will be generated as target/generated-sources/out.xml file.
[JDK 1.8](https://openjdk.java.net/install/) is required by the original [Saxon of Saxonica](http://saxon.sourceforge.net/) and [Maven](https://maven.apache.org/download.cgi?Preferred=ftp://ftp.osuosl.org/pub/apache/) as build environment.
Build & smoke test can be executed via command-line by calling: **mvn clean install**

## Report to Saxonica

[https://saxonica.plan.io/issues/4823](https://saxonica.plan.io/issues/4823)
