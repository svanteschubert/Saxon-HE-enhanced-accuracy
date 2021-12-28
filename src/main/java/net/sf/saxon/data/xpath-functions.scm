<?xml version="1.0" encoding="UTF-8"?>
<scm:schema xmlns=""
            xmlns:scm="http://ns.saxonica.com/schema-component-model"
            generatedAt="2020-05-11T08:48:27.149+01:00"
            xsdVersion="1.1">
   <scm:simpleType id="C0"
                   name="finiteNumberType"
                   targetNamespace="http://www.w3.org/2005/xpath-functions"
                   base="#double"
                   variety="atomic"
                   primitiveType="#double">
      <scm:minExclusive value="-INF"/>
      <scm:maxExclusive value="INF"/>
   </scm:simpleType>
   <scm:complexType id="C1"
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
   <scm:complexType id="C4"
                    name="nullType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="empty">
      <scm:attributeWildcard ref="C5"/>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true"/>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C6"
                    name="stringWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C7"
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
      <scm:attributeUse required="false" inheritable="false" ref="C8" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C9"/>
   </scm:complexType>
   <scm:complexType id="C10"
                    name="numberType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C0"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="C0">
      <scm:attributeWildcard ref="C11"/>
   </scm:complexType>
   <scm:complexType id="C12"
                    name="mapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="element-only">
      <scm:attributeWildcard ref="C13"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C14"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C15"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C16"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C17"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C18"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C19"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C19" to="1"/>
            <scm:edge term="C14" to="1"/>
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C17" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C19" to="1"/>
            <scm:edge term="C14" to="1"/>
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C17" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C20"
                    name="booleanWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C21"
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
      <scm:attributeWildcard ref="C22"/>
   </scm:complexType>
   <scm:complexType id="C23"
                    name="mapWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C12"
                    derivationMethod="extension"
                    abstract="false"
                    variety="element-only">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C13"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C14"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C15"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C16"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C17"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C18"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C19"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C19" to="1"/>
            <scm:edge term="C14" to="1"/>
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C17" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C18" to="1"/>
            <scm:edge term="C19" to="1"/>
            <scm:edge term="C14" to="1"/>
            <scm:edge term="C16" to="1"/>
            <scm:edge term="C15" to="1"/>
            <scm:edge term="C17" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C21"
                    name="booleanType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#boolean"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="#boolean">
      <scm:attributeWildcard ref="C22"/>
   </scm:complexType>
   <scm:complexType id="C24"
                    name="arrayType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="element-only">
      <scm:attributeWildcard ref="C25"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C26"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C27"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C28"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C29"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C30"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C31"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
            <scm:edge term="C31" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
            <scm:edge term="C31" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C32"
                    name="arrayWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C24"
                    derivationMethod="extension"
                    abstract="false"
                    variety="element-only">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C25"/>
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C26"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C27"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C28"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C29"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C30"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C31"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
            <scm:edge term="C31" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C26" to="1"/>
            <scm:edge term="C27" to="1"/>
            <scm:edge term="C29" to="1"/>
            <scm:edge term="C28" to="1"/>
            <scm:edge term="C30" to="1"/>
            <scm:edge term="C31" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C33"
                    name="numberWithinMapType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="C10"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="C0">
      <scm:attributeUse required="false" inheritable="false" ref="C2"/>
      <scm:attributeUse required="false" inheritable="false" ref="C3" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C11"/>
   </scm:complexType>
   <scm:complexType id="C34"
                    name="analyze-string-result-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="mixed">
      <scm:modelGroupParticle minOccurs="0" maxOccurs="unbounded">
         <scm:choice>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C35"/>
            <scm:elementParticle minOccurs="1" maxOccurs="1" ref="C36"/>
         </scm:choice>
      </scm:modelGroupParticle>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C35" to="1"/>
            <scm:edge term="C36" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C35" to="1"/>
            <scm:edge term="C36" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C37"
                    name="match-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="mixed">
      <scm:elementParticle minOccurs="0" maxOccurs="unbounded" ref="C38"/>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C38" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C38" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C39"
                    name="group-type"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#anyType"
                    derivationMethod="restriction"
                    abstract="false"
                    variety="mixed">
      <scm:attributeUse required="false" inheritable="false" ref="C40"/>
      <scm:elementParticle minOccurs="0" maxOccurs="unbounded" ref="C38"/>
      <scm:finiteStateMachine initialState="0">
         <scm:state nr="0" final="true">
            <scm:edge term="C38" to="1"/>
         </scm:state>
         <scm:state nr="1" final="true">
            <scm:edge term="C38" to="1"/>
         </scm:state>
      </scm:finiteStateMachine>
   </scm:complexType>
   <scm:complexType id="C7"
                    name="stringType"
                    targetNamespace="http://www.w3.org/2005/xpath-functions"
                    base="#string"
                    derivationMethod="extension"
                    abstract="false"
                    variety="simple"
                    simpleType="#string">
      <scm:attributeUse required="false" inheritable="false" ref="C8" default="false">
         <scm:default lexicalForm="false">
            <scm:item type="#boolean" value="false"/>
         </scm:default>
      </scm:attributeUse>
      <scm:attributeWildcard ref="C9"/>
   </scm:complexType>
   <scm:element id="C30"
                name="boolean"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C21"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C31"
                name="null"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C4"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C41"
                name="analyze-string-result"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C34"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C29"
                name="number"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C10"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C36"
                name="non-match"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="#string"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C26"
                name="map"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C12"
                global="true"
                nillable="false"
                abstract="false">
      <scm:identityConstraint ref="C42"/>
   </scm:element>
   <scm:element id="C35"
                name="match"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C37"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C38"
                name="group"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C39"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C27"
                name="array"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C24"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:element id="C28"
                name="string"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C7"
                global="true"
                nillable="false"
                abstract="false"/>
   <scm:attributeGroup id="C43"
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
   <scm:wildcard id="C5"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:attribute id="C8"
                  name="escaped"
                  type="#boolean"
                  global="false"
                  inheritable="false"
                  containingComplexType="C7"/>
   <scm:wildcard id="C9"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:wildcard id="C11"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:wildcard id="C13"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:element id="C14"
                name="map"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C23"
                global="false"
                containingComplexType="C12"
                nillable="false"
                abstract="false">
      <scm:identityConstraint ref="C44"/>
   </scm:element>
   <scm:element id="C15"
                name="array"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C32"
                global="false"
                containingComplexType="C12"
                nillable="false"
                abstract="false"/>
   <scm:element id="C16"
                name="string"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C6"
                global="false"
                containingComplexType="C12"
                nillable="false"
                abstract="false"/>
   <scm:element id="C17"
                name="number"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C33"
                global="false"
                containingComplexType="C12"
                nillable="false"
                abstract="false"/>
   <scm:element id="C18"
                name="boolean"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C20"
                global="false"
                containingComplexType="C12"
                nillable="false"
                abstract="false"/>
   <scm:element id="C19"
                name="null"
                targetNamespace="http://www.w3.org/2005/xpath-functions"
                type="C1"
                global="false"
                containingComplexType="C12"
                nillable="false"
                abstract="false"/>
   <scm:wildcard id="C22"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:wildcard id="C25"
                 processContents="skip"
                 constraint="not"
                 namespaces="##local http://www.w3.org/2005/xpath-functions"/>
   <scm:attribute id="C40"
                  name="nr"
                  type="#positiveInteger"
                  global="false"
                  inheritable="false"
                  containingComplexType="C39"/>
   <scm:unique id="C42"
               name="unique-key"
               targetNamespace="http://www.w3.org/2005/xpath-functions">
      <scm:selector xpath="*" defaultNamespace=""/>
      <scm:field xpath="@key" defaultNamespace=""/>
   </scm:unique>
   <scm:unique id="C44"
               name="unique-key-2"
               targetNamespace="http://www.w3.org/2005/xpath-functions">
      <scm:selector xpath="*" defaultNamespace=""/>
      <scm:field xpath="@key" defaultNamespace=""/>
   </scm:unique>
</scm:schema>
<?Σ a5e7196a?>
