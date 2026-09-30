<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:svrl="http://purl.oclc.org/dsdl/svrl">
  <xsl:output method="text"/>
  <xsl:template match="/">
    <xsl:if test="not(svrl:schematron-output/svrl:fired-rule)">
      <xsl:message terminate="yes">No executed validation rules found in the SVRL report.</xsl:message>
    </xsl:if>
    <xsl:variable name="failures" select="svrl:schematron-output/svrl:failed-assert"/>
    <xsl:value-of select="if (exists($failures)) then 'FAIL' else 'PASS'"/>
    <xsl:text>: </xsl:text>
    <xsl:value-of select="count($failures)"/>
    <xsl:value-of select="if (count($failures) = 1) then ' failed assertion.' else ' failed assertions.'"/>
    <xsl:text>&#10;</xsl:text>
    <xsl:for-each select="$failures">
      <xsl:value-of select="@id"/>
      <xsl:text>: </xsl:text>
      <xsl:value-of select="normalize-space(svrl:text)"/>
      <xsl:text>&#10;</xsl:text>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>
