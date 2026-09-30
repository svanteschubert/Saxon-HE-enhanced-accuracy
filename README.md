# Saxon Home e-Commerce Edition

## Purpose

This is a fork from the XSLT processor (SAXON Home) to provide accuracy and legal conformatiy in commercial calculations.

1. The accuracy is being achieved by using for floating-point numbers the decimal-based implementation of IEEE 754 of Java instead of the inaccurate binary-based floating point.

2. In some EU countries - like in Germany - the VAT has to be rounded half-up (away from zero - 0,5 becomes 1 and -0,5 becomes -1). But XML is using round half-up towards infinity, where 0,5 becomes 1 and -0,5 becomes 0).
Therefore the (in Germany for VAT) legally required rounding had been added to SAXON HE.

This temporary fork of Michael Kay's Saxon is just a showcase of using Saxon in the e-commerce domain requiring the best numeric accuracy.

In the ZUGFeRD/Factur-X community this **Saxon** fork is known as **Svanton**, a name coined in honour of its author by Jochen Stärk, author of the [Mustang validator](https://github.com/ZUGFeRD/mustangproject).
It sounds like a Swedish furniture series, which fits: a Saxon that you assemble yourself.

After convincing [CEN TC 434 WG1](https://standards.cen.eu/dyn/www/f?p=204:22:0::::FSP_ORG_ID,FSP_LANG_ID:1971326,25&cs=1F9CEADFE13744B476C348D55B8E70B74) to add decimal-based floating-point-support as a recommendation of the [EU e-invoice standard (EN16931)](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/Compliance+with+eInvoicing+standard), this project aims to enhance [the EN16031 XSLT Schematron validation reference implementation](https://github.com/ConnectingEurope/eInvoicing-EN16931) with the support of decimal-based floating-point.

### Deliberate deviation from the W3C specifications

This fork deliberately deviates from the XPath and XSLT specifications. It is meant for
XSLT used in commercial applications, such as e-invoicing, that expect commercial
rounding and decimal floating-point:

* `round()` rounds halves away from zero by default (commercial rounding, "kaufmännisches
  Runden"): `round(-2.5)` is −3, not −2. The specified rule remains available as
  `round-half-to-positive-infinity()`.
* Numbers are calculated in decimal floating-point only. Untyped XML values, `number()`,
  scientific-notation literals and the arithmetic of `version="1.0"` stylesheets use
  `xs:decimal`. Binary floating-point remains only where a stylesheet asks for it
  explicitly with `xs:double` or `xs:float`, and for `NaN` and `INF`, which have no
  decimal equivalent.

Stylesheets that rely on the specified binary behaviour get different results with this
fork.

To make this behaviour available in every conforming processor without such a deviation,
[qt4cg/qtspecs#2935](https://github.com/qt4cg/qtspecs/issues/2935) proposes it for
XPath, XQuery and XSLT 4.0 as an opt-in per stylesheet or query: a default rounding mode
for `round()` and a decimal arithmetic mode that avoids binary floating-point. A copy of
the proposal is in
[docs/qt4cg-proposal-commercial-arithmetic.md](docs/qt4cg-proposal-commercial-arithmetic.md).


## Proof: simple invoice calculations that are incorrect in Saxon-HE 13.0

### The official EU validator accepts an incorrect VAT taxable amount

The unchanged [official EN16931 validation XSLT](https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/tag/validation-1.3.16)
(release 1.3.16) running on stock Saxon-HE 13.0 accepts a UBL invoice whose VAT category
taxable amount (BT-116) is off by exactly 1.00 EUR. Rule BR-S-08 only tolerates a
deviation *below* 1.00, and stock Saxon does reject the same invoice in CII syntax and
the same error in the other direction:

| 19% VAT taxable amount (the lines add up to 197.37) | UBL, Saxon-HE 13.0 | UBL, Svanton | CII, Saxon-HE 13.0 | CII, Svanton |
| --- | --- | --- | --- | --- |
| 197.37, correct | $\color{green}\textsf{valid}$ | $\color{green}\textsf{valid}$ | $\color{green}\textsf{valid}$ | $\color{green}\textsf{valid}$ |
| 196.37, 1.00 too low | $\color{red}\textsf{valid}$ $\color{red}\textsf{(incorrect)}$ | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ |
| 198.37, 1.00 too high | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ | $\color{green}\textsf{invalid}$ $\color{green}\textsf{(BR-S-08)}$ |

UBL BR-S-08 tests `xs:decimal(cbc:TaxableAmount + 1) > sum(…line amounts…)`. Without a
schema `cbc:TaxableAmount` is untyped, so stock Saxon adds 1 in binary floating-point:
196.37 + 1 = 197.37000000000000455, which is greater than 197.37. The fork adds in
decimal, and 197.37 is not greater than 197.37. About half of all two-decimal amounts
behave like this in one of the two directions.

UBL and CII get different verdicts on stock Saxon because the official rules implement
BR-S-08 differently for the two syntaxes. The CII rule compares the taxable amount
exactly, without tolerance, with the decimal sum of the line amounts: every deviation,
even a cent, is invalid, and binary arithmetic plays no role. Only the UBL rule has the
tolerance, and it computes `cbc:TaxableAmount + 1` before casting to `xs:decimal`. Only
this one value differs from the
[UBL](src/test/resources/en16931/invoice-2017-ubl.xml) and
[CII](src/test/resources/en16931/invoice-2017-cii.xml) baseline invoices. Run
`mvn -Pen16931-comparison -Dmaven.javadoc.skip=true verify` to validate all variants with
both engines; see the [comparison report](docs/en16931-comparison.md).

### Simple calculations

Each row is a test. [AccuracyExamplesTest](src/test/java/net/sf/saxon/AccuracyExamplesTest.java)
checks the fork column with `mvn test`, and the comparison above checks the Saxon-HE 13.0
column against the original JAR. Both read
[accuracy-examples.xml](src/test/resources/examples/accuracy-examples.xml); add a row there
and here to extend the proof.

#### Binary floating-point: the numbers come from XML

Without a schema every XML value is untyped, as in each Schematron validation. Stock Saxon turns it into a binary `xs:double`; the fork into an `xs:decimal`. The same happens to scientific notation such as `0.1e0` and to `number()`.

| # | XML input | XPath | Saxon-HE 13.0 (all incorrect) | Svanton (all correct) |
| --- | --- | --- | ---: | ---: |
| B01 | `<v a="0.1" b="0.2"/>` | `@a + @b` | $\color{red}\texttt{0.30000000000000004}$ | $\color{green}\texttt{0.3}$ |
| B02 | `<v a="0.1" b="0.2"/>` | `@a + @b = 0.3` | $\color{red}\texttt{false}$ | $\color{green}\texttt{true}$ |
| B03 | `<v a="0.1" b="0.2"/>` | `@a + @b > 0.3` | $\color{red}\texttt{true}$ | $\color{green}\texttt{false}$ |
| B04 | `<v net="0.10" vat="0.20" gross="0.30"/>` | `@net + @vat = @gross` | $\color{red}\texttt{false}$ | $\color{green}\texttt{true}$ |
| B05 | `<v a="0.1" b="0.2"/>` | `sum((@a, @b))` | $\color{red}\texttt{0.30000000000000004}$ | $\color{green}\texttt{0.3}$ |
| B06 | `<v a="0.1" b="0.2"/>` | `avg((@a, @b))` | $\color{red}\texttt{0.15000000000000002}$ | $\color{green}\texttt{0.15}$ |
| B07 | `<v a="0.1"/>` | `@a * 3` | $\color{red}\texttt{0.30000000000000004}$ | $\color{green}\texttt{0.3}$ |
| B08 | `<v x="1.1"/>` | `@x * @x` | $\color{red}\texttt{1.2100000000000002}$ | $\color{green}\texttt{1.21}$ |
| B09 | `<v a="1.0" b="0.9"/>` | `@a - @b` | $\color{red}\texttt{0.09999999999999998}$ | $\color{green}\texttt{0.1}$ |
| B10 | `<v a="0.3" b="0.1"/>` | `@a mod @b` | $\color{red}\texttt{0.09999999999999998}$ | $\color{green}\texttt{0}$ |
| B11 | `<v amount="133.70"/>` | `@amount * 100` | $\color{red}\texttt{13369.999999999998}$ | $\color{green}\texttt{13370}$ |
| B12 | `<v price="4.35"/>` | `@price * 100` | $\color{red}\texttt{434.99999999999994}$ | $\color{green}\texttt{435}$ |
| B13 | `<v a="0.7" b="0.1"/>` | `floor((@a + @b) * 10)` | $\color{red}\texttt{7}$ | $\color{green}\texttt{8}$ |
| B14 | `<v price="1.005"/>` | `round(@price, 2)` | $\color{red}\texttt{1}$ | $\color{green}\texttt{1.01}$ |
| B15 | `<v price="1.005"/>` | `round(@price * 100) div 100` | $\color{red}\texttt{1}$ | $\color{green}\texttt{1.01}$ |
| B16 | `<v qty="1" price="1.005"/>` | `round(@qty * @price, 2)` | $\color{red}\texttt{1}$ | $\color{green}\texttt{1.01}$ |
| B17 | – | `0.1e0 + 0.2e0` | $\color{red}\texttt{0.30000000000000004}$ | $\color{green}\texttt{0.3}$ |
| B18 | – | `round(1.005e0, 2)` | $\color{red}\texttt{1}$ | $\color{green}\texttt{1.01}$ |
| B19 | `<v a="0.1" b="0.2"/>` | `number(@a) + number(@b)` | $\color{red}\texttt{0.30000000000000004}$ | $\color{green}\texttt{0.3}$ |

#### Rounding: negative halves

XPath `round()` takes a half toward positive infinity, so −2.5 becomes −2. Commercial rounding (German VAT law, EN16931) takes it away from zero: −3. This hits every credit note.

| # | XML input | XPath | Saxon-HE 13.0 (all incorrect) | Svanton (all correct) |
| --- | --- | --- | ---: | ---: |
| R01 | – | `round(-0.5)` | $\color{red}\texttt{0}$ | $\color{green}\texttt{-1}$ |
| R02 | – | `round(-1.5)` | $\color{red}\texttt{-1}$ | $\color{green}\texttt{-2}$ |
| R03 | – | `round(-2.5)` | $\color{red}\texttt{-2}$ | $\color{green}\texttt{-3}$ |
| R04 | – | `round(-0.665, 2)` | $\color{red}\texttt{-0.66}$ | $\color{green}\texttt{-0.67}$ |
| R05 | – | `round(-0.665 * 100) div 100` | $\color{red}\texttt{-0.66}$ | $\color{green}\texttt{-0.67}$ |
| R06 | `<v qty="-3" price="2.125"/>` | `round(@qty * @price, 2)` | $\color{red}\texttt{-6.37}$ | $\color{green}\texttt{-6.38}$ |
| R07 | `<v net="-3.50" rate="19"/>` | `round(xs:decimal(@net) * xs:decimal(@rate) div 100, 2)` | $\color{red}\texttt{-0.66}$ | $\color{green}\texttt{-0.67}$ |
| R08 | – | `format-number(round(-1.005, 2), '0.00')` | $\color{red}\texttt{-1.00}$ | $\color{green}\texttt{-1.01}$ |

#### Division precision

Stock Saxon stops a nonterminating decimal division after 18 decimal places, the fork after 34.

| # | XML input | XPath | Saxon-HE 13.0 (all incorrect) | Svanton (all correct) |
| --- | --- | --- | ---: | ---: |
| P01 | – | `1 div 3` | $\color{red}\texttt{0.333333333333333333}$ | $\color{green}\texttt{0.3333333333333333333333333333333333}$ |
| P02 | – | `2 div 3` | $\color{red}\texttt{0.666666666666666667}$ | $\color{green}\texttt{0.6666666666666666666666666666666667}$ |
| P03 | – | `1000000000.0 * (1.0 div 3)` | $\color{red}\texttt{333333333.333333333}$ | $\color{green}\texttt{333333333.3333333333333333333333333}$ |

#### Same result on both engines

Explicit `xs:decimal` casts are the portable workaround for binary floating-point, and positive halves round the same way on both engines.

| # | XML input | XPath | Saxon-HE 13.0 | Svanton |
| --- | --- | --- | ---: | ---: |
| S01 | `<v a="0.1" b="0.2"/>` | `xs:decimal(@a) + xs:decimal(@b)` | $\color{green}\texttt{0.3}$ | $\color{green}\texttt{0.3}$ |
| S02 | – | `round(2.5)` | $\color{green}\texttt{3}$ | $\color{green}\texttt{3}$ |

## Try it: Maven snapshot release

A snapshot of this fork is available for testing from the Maven Central snapshot
repository:

~~~ xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases><enabled>false</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.schubert-consulting</groupId>
    <artifactId>Saxon-HE-accuracy</artifactId>
    <version>13.0.1-BETA</version>
  </dependency>
</dependencies>
~~~

The JAR is a drop-in replacement for `net.sf.saxon:Saxon-HE:13.0`: the Java packages
stay `net.sf.saxon`, so s9api, JAXP and the command line work unchanged. Use it
*instead of* Saxon-HE, never next to it on the same classpath; if another dependency
brings in Saxon-HE, exclude it there. XML Resolver 6.0.23 comes along as a dependency.
The snapshot requires Java 25 or newer.

A snapshot is for testing, not for production. Every upload replaces it under the same
version, and Maven Central deletes snapshots after 90 days. Please report results and
problems as [GitHub issues](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/issues).

### Without Maven: the standalone JAR

Starting Svanton with XML Resolver 6.0.23 bundled and Java 25 or newer:

~~~ bash
java -jar Saxon-HE-accuracy-13.0.1-BETA-standalone.jar -s:input.xml -xsl:stylesheet.xsl -o:output.xml
~~~

It is in the ZIP of each [GitHub release](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/releases),
next to the plain JAR on Maven Central with the classifier `standalone`, and in repository directory `target/` after `mvn package`.
Use it on the command line.

## Background

In the context of EU e-invoice standardisation the CEN Technical Committee 434 discussed for weeks, how it could be achieved that invoices created from different software could be identical in all data fields, especially the calculated amounts were often varying.
For weeks spreadsheets with various scenarios were exchanged and the tendency was towards a simple workaround using Slack (to accept the variations and provide a level of inaccuracy).

In the end, there were only three points to be taken care of:

1. No calculation of rounded values (e.g. no addition of line gross values - even if allowed by law as in the Netherlands)
2. Agree on a single rounding (there are more than a dozen different roundings - XML come up with its own but Germany requires by law for VAT the "half-up rounding away from zero" (or "kaufmännisches Runden") different to XML default rounding)
3. Use Instead of the usually used binary floating-point use the accurate decimal floating-point (part of IEEE 754 since 2008)

These recommendations are part of the EN16931 amendments - no mandatory requirement as the members were afraid that this standard would not be accepted by the e-receipt industry as their software is "too weak"!

## Decimal-based floating-point

Decimal-based floating-point was invented for the commercial sector.
It missed the early [IEEE 754 standard](https://ieeexplore.ieee.org/document/8766229) in the late 80ths and still took 20 years until it was embraced by IEEE 754 in 2008.
Now, being part of all major libraries as [Java](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/math/BigDecimal.html), [.Net](https://docs.microsoft.com/en-us/dotnet/api/system.decimal?view=net-5.0), [Intel](https://software.intel.com/content/www/us/en/develop/articles/intel-decimal-floating-point-math-library.html), etc.

### Invoice Example: the order of operations matters

An invoice line with a price per base quantity (EN16931 BT-146 per BT-149):

~~~ Java
quantity = 1000000000.0
priceAmount = 1.0
baseQuantity = 3
~~~

| Line amount | Saxon-HE 13.0 | Svanton |
| --- | --- | --- |
| `$quantity * ($priceAmount div $baseQuantity)`, dividing first | `333333333.333333333` | `333333333.3333333333333333333333333` |
| `$quantity * $priceAmount div $baseQuantity`, multiplying first | `333333333.333333333333333333` | `333333333.3333333333333333333333333333333333` |

Mathematically both are the same, but 1 ÷ 3 has no finite decimal result, so every
division like this has to round: stock Saxon after 18 decimal places, this fork after
34. Dividing first rounds the small value 0.333… and multiplies its rounding error by
one billion: only 9 (stock) or 25 (fork) decimal places stay correct. Multiplying first
and dividing last keeps all 18 or 34. The smallest example is
`(1.0 div 3) * 3 = 0.999999999999999999`, while `3 * 1.0 div 3 = 1`. Decimal arithmetic
makes the rounding error far smaller and predictable, but no finite precision removes it,
so the order still matters. In the energy and pharma sectors, prices with 6 to 9 decimal
places go along with high quantities, so such errors quickly reach cent level.

#### Principles for accurate invoice calculations

1. **Multiply first, divide last.** Enlarge the value (quantity × price) before a
   division that has to round (÷ base quantity, ÷ 1.19 from a gross to a net price,
   ÷ exchange rate). Dividing by 100 for a percentage never rounds in decimal.
2. **Round once, at the end.** Round each amount only when it is stated on the invoice,
   for example the line net amount to 2 decimal places, and never calculate further with
   rounded intermediate values, as the EN16931 amendments recommend.
3. **Round commercially.** Round halves away from zero, as German VAT law requires:
   −0.665 becomes −0.67, not −0.66.
4. **Calculate in decimal, not in binary.** Avoid `xs:double` and `xs:float`: binary
   floating-point cannot even store 0.1 exactly. This fork calculates XML input and
   `number()` in decimal; on other XSLT processors, cast XML input with `xs:decimal(.)`
   instead of `number(.)`.

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
4. Added [round-half-away-from-zero() function (in Java half-up)](https://docs.oracle.com/javase/8/docs/api/java/math/RoundingMode.html) as integrated extension functions of SAXON, as half-away-from-zero rounding is the default rounding in EU e-commerce - the rounding that we had likely learned in school - and now also added as default rounding to the EN16931 specification. The  [W3C XPath round() function](https://www.w3.org/TR/xpath-functions-31/#func-round) is different by always rounding in the direction of positives, e.g. -1.5 becomes -1.

## Saxon 13.0 accuracy fixes and remaining limits

Both `round(value)` and `round(value, precision)` use ties away from zero,
including dynamic function calls. Float rounding, large integer overflow, signed
zero, and extreme rounding precisions are covered by regression tests. The original
XPath rule remains available as `round-half-to-positive-infinity()`.

Scientific-notation literals such as `1e-40` now parse as decimals. Decimal
multiplication is exact; it no longer truncates operands to 34 decimal places.
Terminating decimal division is exact. Nonterminating division uses `HALF_UP`
with scale `max(34, dividend.scale - divisor.scale + 34)`.

These are `BigDecimal` arithmetic rules, not an IEEE 754 decimal128 implementation:
34 decimal places are different from 34 significant digits. The historical invoice
example above describes a goal; different orders of operations can still give
different results after nonterminating division.

Untyped XML content, as in every Schematron validation without a schema, is no longer
converted to `xs:double` when it is used as a number. Arithmetic, `sum()`, `avg()`,
`min()`, `max()`, comparisons with a decimal or integer, and `xs:numeric` arguments
such as `round(cbc:PriceAmount, 2)` convert a decimal lexical form (including
scientific notation up to exponent 400) to `xs:decimal`. `NaN`, `INF`, larger
exponents and invalid input keep the standard `xs:double` conversion. As with decimal
literals, dividing untyped input by zero now raises `FOAR0001` instead of returning `INF`.

`number()` returns an `xs:decimal` for a decimal or integer argument and for a string or
untyped value with a decimal lexical form, and the integer 1 or 0 for a boolean. Invalid
input still gives `NaN`. XPath 1.0 backwards-compatible mode (`version="1.0"`
stylesheets) defines its arithmetic and comparisons in terms of `number()`, so it
calculates in decimal, too. Binary floating-point remains only where a stylesheet asks
for it explicitly: `xs:double`, `xs:float`, and comparisons with such values.
Only the associativity example stays disabled in `AccuracyRegressionTest`.

The additional tests exercise XPath with optimization enabled/disabled, runtime
variables, XQuery, JAXP and the CLI. A test-only SchXslt dependency compiles
[invoice.sch](src/test/resources/schematron/invoice.sch) into XSLT and validates
positive and negative VAT midpoint cases. This focused fixture is not a complete
EN16931/XRechnung ruleset.

## Official EN16931 invoice comparison

The [UBL and CII examples and comparison report](docs/en16931-comparison.md)
use fictional German companies, 2017 dates, and 19%/7% VAT. Run
`mvn -Pen16931-comparison -Dmaven.javadoc.skip=true verify` with JDK 25 to compare the unchanged
EN16931 1.3.16 XSLT under stock Saxon-HE 13.0 and this fork in separate JVMs.
Both baseline invoices pass both engines; the VAT basis variants above show the
difference. The same run checks the stock column of the example tables above.

### Release check: only JARs, XML and XSLT

~~~ bash
mvn -Prelease-check verify
~~~

This validates every invoice in [cases.xml](src/test/resources/en16931/cases.xml) with the unchanged official
validator XSLT, running each engine exactly as a user would:
`java -jar <engine>.jar -s:<invoice> -xsl:EN16931-UBL-validation.xslt -o:<report>`.
The two engines are Saxon-HE 13.0 as Saxonica distributes it and the standalone Svanton JAR of this build. Both engines also compute the example tables above. A stylesheet run by stock Saxon compares all results with the expected
ones, writes `target/release-check/report.html`, and fails the build on any mismatch. No Java code of this
project is involved. To test an already built or downloaded JAR without rebuilding:

~~~ bash
mvn -Prelease-check antrun:run@release-check -Dsvanton.jar=/absolute/path/to/Saxon-HE-accuracy-13.0.1-BETA-standalone.jar
~~~

See the [comparison report](docs/en16931-comparison.md#release-check-only-jars-xml-and-xslt) for details
and a negative control.

## Building Saxon from latest Sources

As the Saxon HE sources do not exist on GitHub, I downloaded the sources and the pom.xml from the [Maven Repository](https://mvnrepository.com/artifact/net.sf.saxon/Saxon-HE) into a Maven directory structure.
To make the JAR become useable, further artifacts had to be copied from the published SAXON JAR:

* META-INF folder - (but removing all signature information)
* src/main/resources/net/sf/saxon/data/

I have added a [smoke test case](https://github.com/svanteschubert/Saxon-HE/blob/main/src/test/java/net/sf/saxon/DecimalBasedFloatingPointTest.java) to ease debugging from the IDE. The output XML file will be generated as target/generated-sources/out.xml file.
[JDK 25](https://adoptium.net/) (enforced by the pom.xml and pinned for [jenv](https://www.jenv.be/) via the '.java-version' file) and [Maven](https://maven.apache.org/download.cgi) are required as build environment. The original [Saxon of Saxonica](http://saxon.sourceforge.net/) 13 requires at least JDK 17.
Build & smoke test can be executed via command-line by calling: **mvn clean install**

## Updating Saxon Version

There is bash script '[saxon-update.sh](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/saxon-update.sh)', which download the specified Saxon-HE version [from Maven](https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/) and rebase our changes on top of it.

1. Two variables of next & current version of Saxon needs to be adopted (see [Maven for latest version](https://repo1.maven.org/maven2/net/sf/saxon/Saxon-HE/)). In addition, this change of the '[saxon-update.sh](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/saxon-update.sh)' must be first commited on the **accuracy-feature** branch, otherwise the script will not start.
1. Sometimes there might be merge conflicts if Saxon changed a line we are adopting (the script will stop).
   In this case the last three lines (change of version in pom.xml and its commit) have to be done manually, after resolving prior the rebase conflicts manually.
1. Test if the sources build & our test runs without error (there are errors in JavaDoc nevermind).
1. Tag manually the latest commit to trigger the GitHub automatic release deployment, see chapter GitHub Actions below.</br>
   **git tag -sm <TAG_MESSAGE> <TAG_LABEL>**</br>
       e.g. "*git tag -sm v12.4 v12.4*" # using -s to sign the tag & -m is taking the next parameter as message

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
2. [Deployment](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/.github/workflows/deployment.yml): Triggered by pushing a tag `v<version>`, where `<version>` must be the version of the pom.xml.
   It runs `mvn -Prelease-check verify` and creates a **draft** GitHub release with the ZIP, the standalone JAR and
   the release check report. A version with `-alpha`, `-beta`, `-BETA`, `-rc` or `-SNAPSHOT` is marked as a pre-release.
   Review the draft on GitHub and publish it yourself. Maven Central is not touched. For instance:
   1. **git tag -sm <TAG_MESSAGE> <TAG_LABEL>**</br>
       e.g. "*git tag -sm v13.0.1-BETA v13.0.1-BETA*" # using -s to sign the tag & -m is taking the next parameter as message
   2. **git push --force --follow-tags --all origin** # pushing with force (as we rebased our feature branch "accuracy-feature") with all tags & all branches to origin (this repo)

## Reports to Saxonica and the QT4 Community Group

* [https://saxonica.plan.io/issues/4823](https://saxonica.plan.io/issues/4823)
* [https://saxonica.plan.io/issues/5195](https://saxonica.plan.io/issues/5195)
* [https://saxonica.plan.io/issues/6408](https://saxonica.plan.io/issues/6408), which led to the
  rounding modes of `fn:round` in XPath 4.0 ([qt4cg/qtspecs#1187](https://github.com/qt4cg/qtspecs/issues/1187))
* [qt4cg/qtspecs#2935](https://github.com/qt4cg/qtspecs/issues/2935): proposal for opt-in commercial
  arithmetic in XPath, XQuery and XSLT 4.0

## Acknowledgement

> [!NOTE]
> 🇪🇺 Many thanks to [StandICT.eu](https://www.standict.eu/), whose fellowship supported this Saxon fork:
> fellowship project 02-300 of Open Call #2, from 15 May to 15 November 2021.
> StandICT.eu 2023 is funded by the European Union under Grant Agreement no. 951972.
