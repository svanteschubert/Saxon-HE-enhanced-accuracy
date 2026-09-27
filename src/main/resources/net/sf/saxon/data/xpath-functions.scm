<?xml version="1.0" encoding="UTF-8"?>
<scm:schema xmlns=""
            xmlns:scm="http://ns.saxonica.com/schema-component-model"
            generatedAt="2025-10-07T09:40:37.97495+01:00"
            xsdVersion="1.1">
   <scm:complexType id="C0"
                    name="stringWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C1"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="#string">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeUse required="false" inheritable="false" ref="C4" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C5"/>
   </scm:complexType>
   <scm:complexType id="C1"
                    name="stringType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#string"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="#string">
      <scm:attributeUse required="false" inheritable="false" ref="C4" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C5"/>
   </scm:complexType>
   <scm:complexType id="C6"
                    name="lookahead-group-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="empty">
      <scm:attributeUse required="false" inheritable="false" ref="C7"/>
      <scm:attributeUse required="false" inheritable="false" ref="C8"/>
      <scm:attributeUse required="false" inheritable="false" ref="C9"/>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true"/>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C10"
                    name="mapWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C11"
                    derivationMethod="extension"
                    abstract="false"
                    variety="element-only">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C12"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C13"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C14"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C15"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C16"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C17"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C18"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C13" to="1"/>
            <scm:edge term="C17" to="1"/>
            <scm:edge term="C14" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C13" to="1"/>
            <scm:edge term="C17" to="1"/>
            <scm:edge term="C14" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C19"
                    name="group-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="mixed">
      <scm:attributeUse required="false" inheritable="false" ref="C20"/>
      <scm:elementParticle minOccurs="0" maxOccurs="unbounded" ref="C21"/>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C21" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C21" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C11"
                    name="mapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="element-only">
      <scm:attributeWildcard ref="C12"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C13"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C14"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C15"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C16"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C17"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C18"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C13" to="1"/>
            <scm:edge term="C17" to="1"/>
            <scm:edge term="C14" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C13" to="1"/>
            <scm:edge term="C17" to="1"/>
            <scm:edge term="C14" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C22"
                    name="arrayWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C23"
                    derivationMethod="extension"
                    abstract="false"
                    variety="element-only">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C24"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C25"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C26"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C27"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C28"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C29"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C30"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C25" to="1"/>
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C25" to="1"/>
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:simpleType id="C31"
                   name="finiteNumberType"
                   targetNamespace="http://www.w3.org/2005/xpath-functions"
                   base="#double"
                   variety="atomic"
                   primitiveType="#double">
      <scm:minExclusive value="-INF"/>
      <scm:maxExclusive value="INF"/>
   </scm:simpleType>
   <scm:complexType id="C32"
                    name="nullWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="empty">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true"/>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C33"
                    name="booleanWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C34"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="#boolean">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C35"/>
   </scm:complexType>
   <scm:complexType id="C36"
                    name="numberType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C31"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="C31">
      <scm:attributeWildcard ref="C37"/>
   </scm:complexType>
   <scm:complexType id="C38"
                    name="nullType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="empty">
      <scm:attributeWildcard ref="C39"/>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true"/>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C40"
                    name="analyze-string-result-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="mixed">
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C41"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C42"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C41" to="1"/>
            <scm:edge term="C42" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C41" to="1"/>
            <scm:edge term="C42" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C43"
                    name="match-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="mixed">
      <scm:modelGroupParticle minOccurs="1" maxOccurs="1">
         <scm:sequence>
            <scm:elementParticle minOccurs="0" maxOccurs="unbounded" ref="C21"/>
            <scm:elementParticle minOccurs="0" maxOccurs="unbounded" ref="C44"/>
         </scm:sequence>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C21" to="1"/>
            <scm:edge term="C44" to="2"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C21" to="1"/>
            <scm:edge term="C44" to="2"/>
         </scm:state>
         <scm:state nr="2" final="true">
            <scm:edge term="C44" to="2"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C23"
                    name="arrayType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    block="extension"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="element-only">
      <scm:attributeWildcard ref="C24"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C25"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C26"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C27"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C28"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C29"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C30"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C25" to="1"/>
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C25" to="1"/>
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C34"
                    name="booleanType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#boolean"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="#boolean">
      <scm:attributeWildcard ref="C35"/>
   </scm:complexType>
   <scm:complexType id="C45"
                    name="numberWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C36"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="C31">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C37"/>
   </scm:complexType>
   <scm:element id="C25"
                name="map"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C11"
                global="true"
                nillable="false"
                abstract="false">
      <scm:identityConstraint ref="C46"/>
   </scm:element>
   <scm:element id="C26"
                name="array"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C23"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C44"
                name="lookahead-group"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C6"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C27"
                name="string"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C1"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C29"
                name="boolean"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C34"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C41"
                name="match"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C43"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C21"
                name="group"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C19"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C28"
                name="number"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C36"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C47"
                name="analyze-string-result"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C40"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C30"
                name="null"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C38"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C42"
                name="non-match"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="#string"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:attributeGroup id="C48"
                       name="key-group"
                       targetNamespace="http://www.w3.org/2005/xpath-functions">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
   </scm:attributeGroup>
   <scm:attribute id="C2"
                  name="key"
                  type="#string"
                  global="false"
                  inheritable="false"/>
   <scm:attribute id="C3"
                  name="escaped-key"
                  type="#boolean"
                  global="false"
                  inheritable="false"/>
   <scm:attribute id="C4"
                  name="escaped"
                  type="#boolean"
                  global="false"
                  inheritable="false"
                  containingComplexType="C1"/>
   <scm:wildcard id="C5"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:attribute id="C7"
                  name="nr"
                  type="#positiveInteger"
                  global="false"
                  inheritable="false"
                  containingComplexType="C6"/>
   <scm:attribute id="C8"
                  name="value"
                  type="#string"
                  global="false"
                  inheritable="false"
                  containingComplexType="C6"/>
   <scm:attribute id="C9"
                  name="position"
                  type="#positiveInteger"
                  global="false"
                  inheritable="false"
                  containingComplexType="C6"/>
   <scm:wildcard id="C12"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:element id="C13"
                name="map"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C10"
                global="false"
                containingComplexType="C11"
                nillable="false"
                abstract="false">
      <scm:identityConstraint ref="C49"/>
   </scm:element>
   <scm:element id="C14"
                name="array"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C22"
                global="false"
                containingComplexType="C11"
                nillable="false"
                abstract="false"/>
   <scm:element id="C15"
                name="string"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C0"
                global="false"
                containingComplexType="C11"
                nillable="false"
                abstract="false"/>
   <scm:element id="C16"
                name="number"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C45"
                global="false"
                containingComplexType="C11"
                nillable="false"
                abstract="false"/>
   <scm:element id="C17"
                name="boolean"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C33"
                global="false"
                containingComplexType="C11"
                nillable="false"
                abstract="false"/>
   <scm:element id="C18"
                name="null"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C32"
                global="false"
                containingComplexType="C11"
                nillable="false"
                abstract="false"/>
   <scm:attribute id="C20"
                  name="nr"
                  type="#positiveInteger"
                  global="false"
                  inheritable="false"
                  containingComplexType="C19"/>
   <scm:wildcard id="C24"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:wildcard id="C35"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:wildcard id="C37"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:wildcard id="C39"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:unique id="C46"
               name="unique-key"
               targetNamespace="http://www.w3.org/2005/xpath-functions">
      <scm:selector nsContext="fn=~ j=http://www.w3.org/2005/xpath-functions xs=~"
                    xpath="*"
                    defaultNamespace=""/>
      <scm:field nsContext="fn=~ j=http://www.w3.org/2005/xpath-functions xs=~"
                 xpath="@key"
                 defaultNamespace=""/>
   </scm:unique>
   <scm:unique id="C49"
               name="unique-key-2"
               targetNamespace="http://www.w3.org/2005/xpath-functions">
      <scm:selector nsContext="fn=~ j=http://www.w3.org/2005/xpath-functions xs=~"
                    xpath="*"
                    defaultNamespace=""/>
      <scm:field nsContext="fn=~ j=http://www.w3.org/2005/xpath-functions xs=~"
                 xpath="@key"
                 defaultNamespace=""/>
   </scm:unique>
</scm:schema>
<?Σ b2e3c75d?>
<?Σ2 bf417a8c23ec57d97c0489e490adcbe995b29266f5bbeb8aebc077c4b2cf7ce6?>
