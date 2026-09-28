# Proposal: opt-in commercial arithmetic for XPath, XQuery and XSLT 4.0

*Posted to the QT4 Community Group as [qt4cg/qtspecs#2935](https://github.com/qt4cg/qtspecs/issues/2935); the discussion continues there.*

## Summary

Add two components to the static context, each set per module and with a default that
keeps today's behaviour:

1. **Default rounding mode**, used by `fn:round` when `$mode` is absent.
   Default `half-to-ceiling`, as today.
2. **Arithmetic mode**: `double` (default, as today) or `decimal`, as Michael Kay considered
   in [#986](https://github.com/qt4cg/qtspecs/issues/986). With `decimal`, a module calculates
   in decimal floating-point throughout: untyped values, `fn:number`, numeric literals and
   mixed decimal/double arithmetic give `xs:decimal`. Binary floating-point remains only for
   values that have no decimal equivalent (`NaN`, `INF`) and for functions defined on
   `xs:double`, such as `math:sqrt`.

A stylesheet or query for commercial calculations, such as the validation of electronic
invoices, could then declare once that it rounds commercially and calculates in decimal,
instead of repeating casts and rounding modes in every expression. Nothing changes for
modules that do not opt in.

## Motivation

Commercial calculations have legal requirements that differ from the defaults of XPath:

* **Commercial rounding.** VAT in Germany must be rounded half away from zero
  (§ 14 Abs. 4 UStG with Abschn. 14.5 Abs. 20 UStAE; DIN 1333), and the French
  e-invoicing standard AFNOR XP Z12-012 (§ 4.4.6) requires the same, so that "rounding two
  strictly opposite numbers gives strictly opposite rounded numbers". EN 16931 recommends
  it as well. `fn:round(-2.5)` returns −2, but −3 is required.
* **Decimal arithmetic.** Amounts are decimal numbers. Without a schema, which is the normal
  case in Schematron validation, every amount is `xs:untypedAtomic`, and arithmetic converts
  it to `xs:double`: with `<a>0.1</a>` and `<b>0.2</b>`, `a + b` is 0.30000000000000004 and
  `a + b = 0.3` is false; `round(<p>1.005</p>, 2)` returns 1, not 1.01.

Rule authors can avoid both problems today, with `xs:decimal()` casts on every operand and
the `$mode` argument on every `fn:round` call. In practice they miss some. The official
EN 16931 validation artefacts ([release 1.3.16](https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/tag/validation-1.3.16)),
maintained by CEN and used across the EU, are an example. Rule BR-S-08 in UBL tolerates
a deviation of the VAT category taxable amount below 1.00 and tests

```
xs:decimal(cbc:TaxableAmount + 1) > sum(…xs:decimal(cbc:LineExtensionAmount)…)
```

The cast comes after the addition, so the addition is done in `xs:double`. For an invoice
whose lines add up to 197.37 and whose declared taxable amount is 196.37, exactly 1.00 too
low, 196.37 + 1 gives 197.37000000000000455 in binary, which is greater than 197.37, and the
invalid invoice is accepted. The same amount 1.00 too high (198.37) is rejected, and so is the
same invoice in CII syntax, whose rule compares exactly. About half of all two-decimal amounts
behave like 196.37 in one of the two directions. The CII rules BR-AE-08, BR-E-08, BR-G-08,
BR-IC-08 and BR-Z-08 compute `../ram:BasisAmount - 1` on the untyped amount in the same way.
The verdict of the reference validation depends on binary arithmetic, not on the rule.
[Test invoices and a reproducible comparison](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy/blob/accuracy-feature/docs/en16931-comparison.md).

Rules like these are written by domain experts, not by XPath specialists. EN 16931 alone
has several hundred of them, and national extensions (XRechnung, Factur-X/ZUGFeRD, Peppol)
and company rules add more. A single declaration per rule set is realistic; a cast on
every operand is not.

## Relation to earlier work in this group

* [#1187](https://github.com/qt4cg/qtspecs/issues/1187) and [#1274](https://github.com/qt4cg/qtspecs/issues/1274) added the rounding modes to `fn:round`, following a request about
  financial rounding (Saxon issue [6408](https://saxonica.plan.io/issues/6408)). This
  proposal only adds a way to change the default of `$mode` for a module.
* In [#986](https://github.com/qt4cg/qtspecs/issues/986), Michael Kay considered "an 'arithmetic mode' in the dynamic context, set to either
  'double' or 'decimal'", and later proposed to "change conversion from untypedAtomic to
  numeric to depend on the lexical form of the value, as it does for numeric literals".
  [#2218](https://github.com/qt4cg/qtspecs/pull/2218) adopted a related rule for general comparisons: an untyped value compared with a
  number is cast to the type of the numeric operand, with `xs:double` as fallback. This
  proposal takes the arithmetic mode further, to decimal arithmetic throughout a module, as an
  opt-in, so that existing code is not affected.

## Proposal

### 1. Default rounding mode

A new static context component, **default rounding mode**, whose value is one of the modes
of `fn:round`. Its default is `half-to-ceiling`.

`fn:round#1` and `fn:round#2`, and `fn:round#3` with an empty `$mode`, use the default
rounding mode of the static context of the call. Dynamic calls such as `round#1` bind it at
the point where the function item is created, as for the default collation.

### 2. Arithmetic mode

A new static context component, **arithmetic mode**, with the values `double` (the default)
and `decimal`. The mode `double` is today's behaviour. The mode `decimal` avoids binary
floating-point wherever a decimal value can represent the number, which gives exact results
for every calculation that XPath can express in decimals:

* **Untyped values.** Wherever an `xs:untypedAtomic` value is converted to a number without an
  explicit required type of `xs:double` or `xs:float`, a lexical form that is valid as a
  numeric literal (after whitespace normalization) is cast to `xs:integer` or `xs:decimal`,
  including scientific notation such as `1.5E3`. Other values, such as `NaN` and `INF`, are
  cast to `xs:double` as today, and invalid input raises the same errors. This applies to
  arithmetic operators, `fn:sum`, `fn:avg`, `fn:min`, `fn:max`, and the coercion of arguments
  whose required type is `xs:numeric` (such as `fn:round` and `fn:abs`).
* **`fn:number`** returns an `xs:decimal` for such a lexical form, and `NaN` as today for
  anything else.
* **Numeric literals.** A `DoubleLiteral` such as `1.5e0` denotes an `xs:decimal`.
* **Mixed arithmetic.** An operation with an `xs:decimal` and an `xs:double` or `xs:float`
  operand converts the binary operand to `xs:decimal` and returns an `xs:decimal`, the reverse
  of today's promotion, unless that operand is `NaN` or infinite.

Binary floating-point then remains only where decimal cannot represent the value (`NaN`,
positive and negative infinity), in functions defined on `xs:double` such as `math:sqrt`,
`math:log` or `math:pow`, whose results are mostly irrational, and in operations whose
operands are all explicitly typed as `xs:double` or `xs:float`. General comparisons keep the
rules of [#2218](https://github.com/qt4cg/qtspecs/pull/2218).

### Syntax

* **XSLT:** standard attributes `[xsl:]default-rounding-mode` and
  `[xsl:]arithmetic-mode`, allowed on any element and scoped like
  `[xsl:]default-collation`:

  ```xml
  <xsl:stylesheet version="4.0" default-rounding-mode="half-away-from-zero"
                  arithmetic-mode="decimal" …>
  ```

* **XQuery:** prolog declarations such as
  `declare default rounding-mode "half-away-from-zero";` and
  `declare arithmetic-mode decimal;`
* **XPath hosted by other languages:** static context properties set by the host language
  or API. Schematron could pass them through from a rule set to the generated XSLT; that
  is a matter for ISO Schematron, not for this group.

## Why the static context

* **Compatibility.** The defaults are today's behaviour. Only modules that declare the new
  settings change, so no existing stylesheet or query breaks.
* **Visibility.** The declaration is part of the module. A reader sees how it calculates, and
  it gives the same results on every conforming processor, unlike a processor configuration.
* **Scope.** A validation service runs rule sets and unrelated stylesheets in the same
  process. A module-level setting affects only the rule set that asks for it.
* **Precedent.** XPath 1.0 compatibility mode is a static context component that already
  changes the semantics of arithmetic and comparisons for a whole module.

## Costs of the decimal mode

Decimal floating-point is more accurate for every value that has a decimal representation,
which is what commercial data consists of. Its main cost is performance: decimal arithmetic
needs more memory and CPU time than hardware binary floating-point. It also changes some
results, which is why the mode is opt-in:

* **Division by zero** raises `FOAR0001` instead of returning `INF` or `NaN`.
* **Nonterminating division**, such as 1 div 3, is rounded to an implementation-defined
  precision, as for `xs:decimal` today, where `xs:double` gives about 17 significant digits.
  `fn:divide-decimals` ([#1261](https://github.com/qt4cg/qtspecs/issues/1261)) gives explicit
  control.
* **Extreme magnitudes** such as `1e308` are exact in decimal but need correspondingly large
  values; an implementation may convert exponents beyond a limit to `xs:double`.

## Open questions

* How is an `xs:double` operand converted in mixed arithmetic: to its exact binary value
  (0.1000000000000000055511151231257827…), or to the shortest decimal that identifies it
  (0.1)? The second matches what users see and what the value usually came from.
* Should the decimal mode also apply to the arithmetic of XPath 1.0 compatibility mode?

## Implementation experience

[Saxon-HE-enhanced-accuracy](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy),
a fork of Saxon-HE 13.0, implements both settings as fixed defaults in a few hundred lines:
half away from zero, and decimal arithmetic for untyped values, `fn:number`, scientific
notation and XPath 1.0 compatibility mode. It keeps mixed arithmetic with explicitly typed
`xs:double` values binary. Saxon's existing rounding modes and the [#2218](https://github.com/qt4cg/qtspecs/pull/2218) comparison code carried
most of the work. [32 example calculations](https://github.com/svanteschubert/Saxon-HE-enhanced-accuracy#simple-calculations)
are run as tests against both the stock and the modified processor, and the official EN 16931
validation artefacts are run unchanged on both. The code is available under the Mozilla Public
License 2.0, as Saxon-HE, and could serve as a starting point for an opt-in implementation.
