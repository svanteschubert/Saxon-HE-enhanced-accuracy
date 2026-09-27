<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
  <sch:title>Decimal invoice arithmetic regression (not a complete EN16931 ruleset)</sch:title>
  <sch:ns prefix="xs" uri="http://www.w3.org/2001/XMLSchema"/>
  <sch:ns prefix="ubl" uri="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"/>
  <sch:ns prefix="cac" uri="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"/>
  <sch:ns prefix="cbc" uri="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2"/>
  <sch:pattern>
    <sch:rule context="cac:TaxSubtotal">
      <sch:let name="tax" value="xs:decimal(cbc:TaxableAmount) * xs:decimal(cac:TaxCategory/cbc:Percent) div 100"/>
      <sch:assert id="vat-one-argument" test="xs:decimal(cbc:TaxAmount) = round($tax * 100) div 100">VAT must round ties away from zero.</sch:assert>
      <sch:assert id="vat-two-arguments" test="xs:decimal(cbc:TaxAmount) = round($tax, 2)">VAT must also round consistently with an explicit precision.</sch:assert>
    </sch:rule>
    <sch:rule context="ubl:Invoice">
      <sch:assert id="decimal-sum" test="sum(cac:InvoiceLine/cbc:LineExtensionAmount/xs:decimal(.)) = xs:decimal(cac:LegalMonetaryTotal/cbc:LineExtensionAmount)">Line amounts must add exactly.</sch:assert>
    </sch:rule>
  </sch:pattern>
</sch:schema>
