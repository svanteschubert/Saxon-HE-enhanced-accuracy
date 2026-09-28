<?xml version="1.0" encoding="UTF-8"?>
<!-- Evaluates every example of accuracy-examples.xml on the Saxon engine running this stylesheet. -->
<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:err="http://www.w3.org/2005/xqt-errors" exclude-result-prefixes="xs err">
  <xsl:output method="xml" indent="yes"/>
  <xsl:template match="/examples">
    <results>
      <xsl:for-each select="example">
        <result id="{@id}">
          <xsl:attribute name="actual">
            <xsl:try>
              <xsl:variable name="value" as="item()*">
                <xsl:evaluate xpath="string(xpath)" context-item="input/*"/>
              </xsl:variable>
              <xsl:value-of select="string-join($value ! string(.), ' ')"/>
              <xsl:catch select="'error ' || local-name-from-QName($err:code)"/>
            </xsl:try>
          </xsl:attribute>
        </result>
      </xsl:for-each>
    </results>
  </xsl:template>
</xsl:stylesheet>
