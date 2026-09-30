# EN16931:2017 invoice comparison on Saxon 13.0

The two baseline invoices pass XML Schema validation and the unchanged official
EN16931 1.3.16 XSLT on **both stock Saxon-HE 13.0 and this enhanced-accuracy fork**.
Changing a single value shows a failure of stock Saxon: with a 19% VAT taxable amount
of 196.37 instead of 197.37, the official UBL rules accept the invoice on stock Saxon
and reject it (`BR-S-08`) on the fork, as they do for the CII invoice on both engines.
The official rules accept both rounding outcomes at the negative VAT midpoint tested
here. The [example tables in the README](../README.md#simple-calculations) show the
fork's arithmetic changes without modifying or misrepresenting the official validator.

## Invoices and input mapping

- [UBL 2.1 invoice](../src/test/resources/en16931/invoice-2017-ubl.xml)
- [UN/CEFACT CII D16B invoice](../src/test/resources/en16931/invoice-2017-cii.xml)
- [Diagnostic variants](../src/test/resources/en16931/variants/) of both, each changing only the
  values named in [cases.xml](../src/test/resources/en16931/cases.xml). That file also lists the rule
  IDs each engine is expected to report.

Both encode invoice `TEST-2017-0001`, issued and delivered on 15 November 2017,
due on 15 December 2017, in EUR. “2017” is interpreted as both EN16931:2017 and
the invoice year. Seller **Fiktiver Musterhandel Elbwolke GmbH**, Hamburg, and buyer
**Fiktive Beispielwerkstatt Mainstern GmbH**, Frankfurt am Main, are invented.
Their addresses and VAT identifiers are synthetic test data. Both countries are
`DE`; no live registration or payment details are used.

The source workbook and its Excel lock file are input-only and are not part of
the deliverable. Neither building nor running this comparison requires them.
The source mapping is `1 Original Calculation!B4:H6`, the VAT rate table at
`J4:K5`, the category calculations at `A9:C10`, and totals at `A12:B17`.
The five-decimal net unit prices follow `2 Net from Gross!B4` and its rows 7–9.
Business decimal values (2.049 EUR and 7%) are used, rather than Excel's serialized
binary approximations (2.0489999999999999 and 0.070000000000000007).

| Item | Quantity / unit | Source gross unit price | VAT | Invoice net unit price | Rounded net line amount |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tobacco | 3 / C62 | 9.99 | 19% | 8.39496 | 25.18 |
| c't magazin für computer technik | 1 / C62 | 6.50 | 7% | 6.07477 | 6.07 |
| Gasoline | 100 / LTR | 2.049 | 19% | 1.72185 | 172.19 |

Net unit price = gross unit price / (1 + rate / 100), rounded half away from zero
to five decimal places. Line amounts are rounded to two decimal places. Both
19% and 7% use VAT category `S` (taxed at a positive rate).
The VAT-inclusive source prices are described in the invoice note, not put in the
UBL/CII “gross price” field: that business term means price before discounts,
**excluding VAT**.

There is a real one-cent difference between the workbook's gross-first calculation
and tax calculated from the invoice's rounded net amounts:

| Total | Workbook gross-first calculation | Baseline invoices |
| --- | ---: | ---: |
| 19% taxable amount | 197.37 | 197.37 |
| 19% VAT | 37.50 | 37.50 |
| 7% taxable amount | 6.07 | 6.07 |
| 7% VAT | 0.43 | 0.42 |
| Net total | 203.44 | 203.44 |
| VAT total | 37.93 | 37.92 |
| Total including VAT, before payable rounding | 241.37 | 241.36 |
| Payable rounding adjustment (BT-114) | — | +0.01 |
| Amount payable | 241.37 | 241.37 |

The workbook uses `round(6.50 × 7 / 107, 2) = 0.43`. The baseline uses
`round(6.07 × 7 / 100, 2) = 0.42`. The **explicit +0.01 payable adjustment**
preserves the source payment total while making the stated tax match the rounded
net tax basis. This is a documented fixture-design choice, not a change to the
spreadsheet or evidence of an engine defect. Java `BigDecimal` independently checks
line calculations, VAT breakdowns, totals, and equivalence of the UBL/CII figures.

The `source-vat` variant retains the workbook's original 0.43/37.93 VAT
figures and 241.37 total, with zero payable rounding. It passes the official rules
on both engines because of their tolerances. That pass does not make 0.43 the
rounded product of 6.07 and 7%.

## Reproduce

Requirements: JDK 25+, Maven, and network access on first execution.
Select JDK 25 through your existing `jenv` setup or `JAVA_HOME`, then run from the
repository root:

```bash
java -version
mvn -version
mvn -Pen16931-comparison -Dmaven.javadoc.skip=true verify
```

The platform-independent implementation is
[En16931Comparison.java](../src/test/java/net/sf/saxon/En16931Comparison.java),
invoked by [En16931ComparisonIT](../src/test/java/net/sf/saxon/En16931ComparisonIT.java)
through Maven Failsafe. It uses JDK APIs for downloads, hashing, ZIP extraction,
XML Schema validation, XML processing, process execution, and JSON reports.
There are no standalone comparison scripts or additional runtime libraries.

The [release check](#release-check-only-jars-xml-and-xslt) validates the same invoices without
this Java code.

The profile in the root [pom.xml](../pom.xml) copies stock
`net.sf.saxon:Saxon-HE:13.0` and XML Resolver into a separate directory before
integration testing. Maven passes `${project.build.directory}/${project.build.finalName}.jar`
to the test, so the comparison follows the current fork coordinates and version,
including `com.schubert-consulting:Saxon-HE-accuracy:13.0.1-SNAPSHOT`.
Each engine runs in its own JVM with the same XML Resolver dependencies.
The stock JAR and fork JAR are never put on the same classpath.

The integration test is disabled unless the profile is selected. Ordinary builds
do not download these validator archives or execute the comparison. Javadoc is
skipped in the command above only to shorten the build; it does not affect validation.

The default output is `target/en16931-comparison/`:

- `summary.json`: engine and stylesheet hashes, independent financial checks,
  every assertion failure (ID, location, test, message), and README example results.
- `svrl/`: all 32 full official-validator SVRL results.
- `logs/`: XSD validation, exact engine versions and transformation logs.
- `accuracy-examples-stock.xml` and `accuracy-examples-enhanced.xml`: README example results.
- `cache/`: verified upstream archives, schemas, unmodified stylesheets, and JARs.

Failsafe also writes its integration-test results to `target/failsafe-reports/`.

The test requires nonempty SVRL rule execution and checks failed assertions;
a successful Java/XSLT exit code alone does not mean an invoice passed. Intentional
negative controls must fail with exactly the expected rule IDs. Any unexpected
result fails the integration test and Maven build. Downloaded archives and stylesheets are
SHA-256 checked. Cached archives are checked again on reuse.

### Release check: only JARs, XML and XSLT

```bash
mvn -Prelease-check verify
```

The `release-check` profile builds the release artifacts, then runs every engine exactly as a user would,
one `java` process per invoice:

```bash
java -jar <engine>.jar -s:<invoice>.xml -xsl:EN16931-UBL-validation.xslt -o:svrl/<engine>/<invoice>.xml
```

- **Saxon-HE 13.0** is the official `SaxonHE13-0J.zip` from Saxonica; its JAR finds XML Resolver in `lib/`.
- **Svanton** is the `-standalone` JAR of this build. To test any other JAR, for instance one downloaded
  from a GitHub release or Maven Central, without rebuilding:

  ```bash
  mvn -Prelease-check antrun:run@release-check -Dsvanton.jar=/absolute/path/to/Saxon-HE-accuracy-13.0.1-SNAPSHOT-standalone.jar
  ```

The downloads (Saxonica ZIP, validator 1.3.16) and both validator XSLTs are SHA-256 checked. Both engines
also compute the [accuracy examples](../src/test/resources/examples/accuracy-examples.xml).
[verdict.xsl](../src/test/resources/release-check/verdict.xsl), run by stock Saxon so that a defect of the fork
cannot hide itself, compares all results with [cases.xml](../src/test/resources/en16931/cases.xml) and the
examples. It prints a summary, writes `target/release-check/report.html`, and fails the build on any mismatch.
Ant, through `maven-antrun-plugin`, only starts the processes. The only Java code involved is Saxon.

As a negative control, passing the stock JAR as `-Dsvanton.jar=target/release-check/cache/SaxonHE13-0J/saxon-he-13.0.jar`
makes the check fail with the UBL `vat-basis-1-too-low` invoice and 30 calculations.

## Observed official validation results

Run on 27 September 2026 through the Java integration test with OpenJDK 25.0.4.1,
stock Saxon-HE 13.0, and a fresh build of
`com.schubert-consulting:Saxon-HE-accuracy:13.0.1-SNAPSHOT` on `accuracy-feature`.
The validation cache had been downloaded with Java's HTTP client earlier that day
and was SHA-256 checked again on reuse. The recorded results and JAR hash below refer
to that build. The Maven suite passed: 57 tests, 56 successful and 1 disabled.
The additional Failsafe integration test passed all 32 official comparisons and all
64 example outcomes (32 README examples × 2 engines). With the profile disabled, this
integration test was confirmed to be skipped.
All sixteen XML instances (eight cases × two syntaxes) passed their XSDs.

| Case | UBL stock | UBL enhanced | CII stock | CII enhanced |
| --- | --- | --- | --- | --- |
| Baseline invoices above | PASS | PASS | PASS | PASS |
| Original workbook VAT totals (`source-vat`) | PASS | PASS | PASS | PASS |
| Negative midpoint: net −3.50, VAT −0.67 | PASS | PASS | PASS | PASS |
| Same net, VAT −0.66 instead | PASS | PASS | PASS | PASS |
| 19% VAT taxable amount 196.37, 1.00 too low | **PASS (incorrect)** | FAIL | FAIL | FAIL |
| 19% VAT taxable amount 198.37, 1.00 too high | FAIL | FAIL | FAIL | FAIL |
| VAT overstated by 5.00; totals reconciled | FAIL | FAIL | FAIL | FAIL |
| Payable total overstated by 1.00 | FAIL | FAIL | FAIL | FAIL |

Both taxable-amount variants fail only `BR-S-08`, except the 1.00-too-low UBL invoice
on stock Saxon, which passes. The VAT-overstatement control fails `BR-CO-17` and
`BR-S-09` in every run. The payable control fails `BR-CO-16` in every run. Baseline
SVRL contains 75 fired rules for UBL and 109 for CII, with no failed assertions or
successful-report warnings. All 32 outcomes match expectations.

These are **local runs of the official EU/CEN validation artefacts**, not submissions
to an online EU service. The scope is base EN16931 plus syntax XSDs, not XRechnung,
Peppol CIUS, a historical 2017 validator release, or a legal/registration check.
The [upstream project](https://github.com/ConnectingEurope/eInvoicing-EN16931)
explicitly distinguishes its base rules from CIUS rules.

## Where the official rules show a difference, and where they cannot

`BR-S-08` allows the declared VAT category taxable amount to deviate from the sum of
line amounts, charges and allowances by less than 1.00. In UBL it tests
`xs:decimal(cbc:TaxableAmount - 1) < sum(…)` and `xs:decimal(cbc:TaxableAmount + 1) > sum(…)`.
The cast comes after the addition, and without a schema `cbc:TaxableAmount` is untyped,
so stock Saxon adds in `xs:double`: 196.37 + 1 = 197.37000000000000455 exceeds the
exact sum 197.37, and the invoice passes. In decimal, 197.37 does not exceed 197.37.
For 198.37, the binary difference 198.37 − 1 does not come out below 197.37, so both
engines reject it. About half of all two-decimal amounts behave like 196.37 in one of the two
directions. The CII rule compares the taxable amount exactly with a decimal sum, so
stock Saxon rejects both CII variants, and the same business data gets different
verdicts in the two syntaxes. The CII tolerance rules `BR-AE-08`, `BR-E-08`, `BR-G-08`,
`BR-IC-08` and `BR-Z-08` use `../ram:BasisAmount - 1` in the same way.

The other effect of the fork, rounding negative halves away from zero, cannot be shown
with the official rules, for the following reasons.

In the pinned [UBL rules](https://github.com/ConnectingEurope/eInvoicing-EN16931/blob/a519ba02a59e2775436428f57ee96899feb1da8c/ubl/schematron/UBL/EN16931-UBL-model.sch),
`BR-S-09` casts the amounts and rate to `xs:decimal`. Stock Saxon already performs
these decimal operations without binary floating-point. Its division by 100 is
terminating. Both engines round positive midpoints the same way.

The VAT checks take `abs()` of the taxable amount and tax amount. Consequently,
the negative midpoint −0.665 is checked through positive magnitudes. `BR-S-09`
uses a strict ±1 monetary-unit window; `BR-CO-17` uses a strict window in UBL and
an inclusive one in CII. A one-cent difference is accepted by both. Document-total
rules typically sum values already restricted to two decimal places, so rounding
those sums to cents does not expose a negative half-cent either.

CII also contains untyped arithmetic, including the rate in `BR-S-09` and some
sums. Stock Saxon evaluates it in binary; the fork evaluates it in decimal. Merely
validating the XML separately with an XSD does not change the values seen by these
subsequent Saxon-HE transformations. With amounts limited to two decimal places and
the tolerances above, both give the same verdicts for all cases here.

Thus neither choosing a negative VAT midpoint nor carrying the workbook's extra
VAT cent creates a different verdict with these unchanged rules; only the binary
arithmetic at the tolerance boundary does.

## Supplemental arithmetic results and useful next experiments

The [example tables in the README](../README.md#simple-calculations) are deliberately
separate from the official XSLT. Their source,
[accuracy-examples.xml](../src/test/resources/examples/accuracy-examples.xml), is run on
both engines by this integration test and on the fork by `AccuracyExamplesTest`.
The fork's result follows the decimal/half-away-from-zero policy; stock Saxon's different
result is often correct under standard XPath semantics.

The line-amount examples B16 and R06 compute quantity (BT-129) × net price (BT-146),
which the official rules never recalculate. Each isolates one cause. As `xs:double`, 1.005 is
stored as 1.00499999999999989…, so stock rounds the binary value down; the fork rounds
the decimal value. In contrast, −6.375 is exact in binary, and stock rounds its negative
midpoint toward positive infinity, as XPath specifies; the fork rounds away from zero.
On stock Saxon the idiom `round(x * 100) div 100` yields 1 as well, because the
multiplication by 100 already produces 100.49999999999999; the fork yields 1.01.

For a meaningful strict VAT comparison, use a supplemental Schematron rule that
requires the **signed**, rounded tax to equal the declared tax, with decimal casts
and without the official ±1 tolerance. A −3.50 EUR line at 19%, declaring −0.67,
then exercises the desired policy. The existing focused
[invoice.sch](../src/test/resources/schematron/invoice.sch) and
[SchematronAccuracyTest](../src/test/java/net/sf/saxon/SchematronAccuracyTest.java)
provide that kind of separate test. Keep its results labelled as supplemental.

For precision, test invoice **generation** as well as validation: calculate net
prices, line totals and taxes from source quantities/prices, then validate the
result. A validator that trusts rounded line amounts cannot reveal every error
in the calculation that produced them. Exponent literals expose one current fork
benefit; explicit `xs:decimal` XML casts show the portable decimal baseline.
The fork also deliberately deviates from the specified `xs:double` result of `number()`
(example B19). It calculates in decimal floating-point, but it is not an IEEE 754
decimal128 implementation.

## Provenance

Official artefacts: [EN16931 validation release 1.3.16](https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/tag/validation-1.3.16).
The tag checkout used for reproducibility resolves to
`a519ba02a59e2775436428f57ee96899feb1da8c`.
The two XSLT files were also compared with the published release ZIP assets and
are byte-identical. No Schematron recompilation, patching or wrapper changes the
rules. CII XSDs come from the upstream repository's D16B uncoupled subset; UBL XSDs
come from the [OASIS UBL 2.1 distribution](https://docs.oasis-open.org/ubl/os-UBL-2.1/).
Upstream licences remain in the downloaded artefacts; EU validation artefacts are
EUPL 1.2. Third-party stylesheets/schemas and the workbook are not vendored.

| Asset | SHA-256 |
| --- | --- |
| UBL validation XSLT | `39f9d282867f1a49e7708d9e29a53da89643e1ee56f10cec1ebcf1277595fcbd` |
| CII validation XSLT | `0b234dea2bbfee739b7761e607a992c17fab88773014ef56355b6158cfb1cc53` |
| Stock Saxon-HE 13.0 JAR | `258fb4788b8e1bd986f9aed14269669412da88c7bb289b747878d4353f6168aa` |
| Enhanced JAR used in this run | `a19be9b187cb1cccc67c02f9e1679f9e1ab80a33fe95bc0ce4d2556438c9bb8f` |

The enhanced JAR hash can vary between builds because of archive timestamps.
Every run records its actual hash and Java version in `summary.json`.
