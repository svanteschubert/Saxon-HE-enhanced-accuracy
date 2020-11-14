# Saxon Home 10.3 e-Commerce Edition 

## Purpose
This temporary fork of Michael Kay's Saxon is just a show case of using Saxon in the e-Commerce domain requiring best numeric accuracy.

After succeeding to convince the [CEN TC 434 WG1](https://standards.cen.eu/dyn/www/f?p=204:22:0::::FSP_ORG_ID,FSP_LANG_ID:1971326,25&cs=1F9CEADFE13744B476C348D55B8E70B74) to add decimal-based floating-point support as recommendation of the EU e-invoice standard (EN16931), I am now aiming to enhance [its reference implementation of validation artifacts](https://github.com/ConnectingEurope/eInvoicing-EN16931) to support decimal-based floating-point.

## Decimal-based floating-point

Decimal-based floating-point missted the early IEEE 754 standard in the late 80ths and it took 20 years later in 2008 until decimal-based floating-point was embraced.
Now being part of all major library as Java, .Net, etc.

For further information on Decimal floating points, see

* [http://speleotrove.com/decimal/decifaq.html](http://speleotrove.com/decimal/decifaq.html)
* [https://github.com/svanteschubert/DecimalFloatingPointExample](https://github.com/svanteschubert/DecimalFloatingPointExample)
* [https://dzone.com/articles/never-use-float-and-double-for-monetary-calculation](https://dzone.com/articles/never-use-float-and-double-for-monetary-calculation)
* [https://blogs.oracle.com/corejavatechtips/the-need-for-bigdecimal](https://blogs.oracle.com/corejavatechtips/the-need-for-bigdecimal)

## How Accuracy was improved in Saxon

This Saxon update is achieved by several minor enhancements:

1. Using solely decimal-based floating-point instead of floating-point.
   The fix was to [disable Double creation in NumericValue](https://github.com/svanteschubert/Saxon-HE/commit/fe8ca45c54622b467eb58fbaeae0d3edbe4461c7).
2. [Extending the existing BigDecimal implementation to full floating-point support](https://github.com/svanteschubert/Saxon-HE/commit/70d0a1197e298eb17dacf343553a2873352f2db2).
3. [Adding highest Java precision decimal-based floating-point support to multiplication and division of BigDecimals](https://github.com/svanteschubert/Saxon-HE/commit/68c538a364e8bfd8aa5598077521ad87fb297e88).

## Building Saxon from latest Sources

As the Saxon HE sources do not exist on GitHub, I downloaded the sources and the pom.xml from the [Maven Repository](https://mvnrepository.com/artifact/net.sf.saxon/Saxon-HE) into a Maven directory structure.
To make the JAR become useable in addition further artifacts had to be copied from the binary:

* META-INF folder - (removing signature info afterwards)
* src/main/resources/net/sf/saxon/data/

I have added a test case to ease debugging from the IDE.
