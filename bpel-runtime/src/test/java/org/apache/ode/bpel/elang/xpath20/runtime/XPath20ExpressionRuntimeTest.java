/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.elang.xpath20.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.ode.bpel.common.FaultException;
import org.apache.ode.bpel.compiler.bom.Expression;
import org.apache.ode.bpel.elang.xpath20.compiler.XPath20ExpressionCompilerBPEL20;
import org.apache.ode.bpel.elang.xpath20.obj.OXPath20ExpressionBPEL20;
import org.apache.ode.bpel.explang.EvaluationContext;
import org.apache.ode.bpel.obj.OExpression;
import org.apache.ode.bpel.obj.OLink;
import org.apache.ode.bpel.obj.OMessageVarType.Part;
import org.apache.ode.bpel.obj.OProcess.OProperty;
import org.apache.ode.bpel.obj.OScope.Variable;
import org.apache.ode.utils.DOMUtils;
import org.apache.ode.utils.Namespaces;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XPath20ExpressionRuntimeTest implements EvaluationContext {

    private XPath20ExpressionRuntime _runtime;
    private Map<String, Node> _vars;
    private XPath20ExpressionCompilerBPEL20 _compiler;

    private MockCompilerContext _cc;
    private Document _vardoc;
    private Node _rootNode;

    public XPath20ExpressionRuntimeTest() {}

    @Before
    public void setUp() throws Exception {
        _vars = new HashMap<String, Node>();
        _cc = new MockCompilerContext();
        _runtime = new XPath20ExpressionRuntime();
        _runtime.initialize(new HashMap());
        _compiler = new XPath20ExpressionCompilerBPEL20();
        _compiler.setCompilerContext(_cc);

        _vardoc = DOMUtils.parse(getClass().getResourceAsStream("/xpath20/variables.xml"));
        NodeList variables = _vardoc.getDocumentElement().getChildNodes();
        for (int i = 0; i < variables.getLength(); ++i) {
            Node n = variables.item(i);
            if (n.getNodeType()!=Node.ELEMENT_NODE)
                continue;
            Element v = (Element) n;
            v.normalize();
            if (v.getLocalName().equals("elementVar")) {
                String name = v.getAttribute("name");
                Node cn = v.getFirstChild();
                while (cn != null && cn.getNodeType() != Node.ELEMENT_NODE)
                    cn = cn.getNextSibling();
                Element el = (Element)cn;
                _cc.registerElementVar(name, new QName(el.getNamespaceURI(),el.getLocalName()));
                _vars.put(name,el);
            } else if (v.getLocalName().equals("messageTypeVar")) {
                String name = v.getAttribute("name");
                Node cn = v.getFirstChild();
                while (cn != null && cn.getNodeType() != Node.ELEMENT_NODE)
                    cn = cn.getNextSibling();
                Element el = (Element)cn;
                
                java.util.List<String> partNames=new java.util.Vector<String>();
                java.util.List<QName> partTypes=new java.util.Vector<QName>();
                NodeList nl=el.getChildNodes();
                
                for (int j=0; j < nl.getLength(); j++) {
                	Node partNode=nl.item(j);
                	if (partNode instanceof Element) {
	                	partNames.add(partNode.getLocalName());
	                	
	                	partNode.normalize();
	                	
	                	Node body=((Element)partNode).getFirstChild();
	                	
	                	while (body != null && (body instanceof Element) == false) {
	                		body = body.getNextSibling();
	                	}
	                	
	                	if (body != null) {
		                	QName partType=new QName(((Element)body).getNamespaceURI(),
		                			((Element)body).getLocalName());
		                	partTypes.add(partType);
	                	}
                	}
                }
                
                _cc.registerMessageTypeVar(name, new QName(el.getNamespaceURI(),el.getLocalName()),
                				partNames, partTypes);
                _vars.put(name,el);
            }
        }
    }
    
    @After
    public void tearDown() throws Exception {
        _vars = null;
        _cc = null;
        _runtime = null;
        _compiler = null;
        _vardoc = null;
    }
    
    @Test
    public void testCompilation() throws Exception {
        compile("$foo");
    }

    @Test
    public void testVariableSelection() throws Exception {
        OXPath20ExpressionBPEL20 exp = compile("$foo");
        Node retVal = _runtime.evaluateNode(exp, this);
        assertNotNull(retVal);
        assertSame(retVal , _vars.get("foo"));
        assertSame(retVal.getOwnerDocument(),_vardoc);
    }

    @Test
    public void testVariableSelectionEmpty() throws Exception {
        OXPath20ExpressionBPEL20 exp = compile("$emptyVar");
        Node retVal = _runtime.evaluateNode(exp, this);
        assertNotNull(retVal);
        assertSame(retVal , _vars.get("emptyVar"));
        assertTrue(DOMUtils.getFirstChildElement((Element)retVal).getLocalName().equals("empty"));
    }

    @Test
    public void testVariableSelectionReallyEmpty() throws Exception {
        OXPath20ExpressionBPEL20 exp = compile("$reallyEmptyVar");
        Node retVal = _runtime.evaluateNode(exp, this);
        assertNotNull(retVal);
        assertSame(retVal , _vars.get("reallyEmptyVar"));
        assertNull(DOMUtils.getFirstChildElement((Element)retVal));
    }

    @Test
    public void testXpathDocFunction() throws Exception {
        URL url = getClass().getResource("/xpath20/variables.xml");

        OXPath20ExpressionBPEL20 exp = compile("doc('" + url.toExternalForm() + "')");
        Node retVal = _runtime.evaluateNode(exp, this);
        assertNotNull(retVal);
        assertEquals("Unexpected root node", "variables", retVal.getNodeName());
    }
    
    @Test
    public void testMessageTypeVariableSelection() throws Exception {
        OXPath20ExpressionBPEL20 exp = compile("$messageVar.parameters");
        Node retVal = _runtime.evaluateNode(exp, this);
        assertNotNull(retVal);
        assertSame(retVal , _vars.get("messageVar"));
        assertSame(retVal.getOwnerDocument(),_vardoc);
    }

    @Test
    public void testMessageInsertMissingData() throws Exception {
    	String insertElementName="InsertedNode";
    	
        OXPath20ExpressionBPEL20 exp = compile("$messageVar.parameters/"+insertElementName);
        exp.setInsertMissingData(true);
        
        // Setup root node
        _rootNode = DOMUtils.stringToDOM("<message><parameters>" +
        		"<tns:ApplicationData xmlns:tns=\"http://foobar\"/></parameters></message>");
        
        java.util.List<?> list = _runtime.evaluate(exp, this);
        
        if (list == null || list.size() != 0) {
        	fail("List should have no elements");
        }
        
        if (((Element)_rootNode).getElementsByTagName(insertElementName).getLength() != 1) {
        	fail("Missing '"+insertElementName+"' element has not been inserted");
        }
    }
    
    @Test
    public void testVariableInsertMissingData() throws Exception {
    	String insertElementName="InsertedNode";
    	
        OXPath20ExpressionBPEL20 exp = compile("$reallyEmptyVar/"+insertElementName);
        exp.setInsertMissingData(true);
        
        // Setup root node
        _rootNode = DOMUtils.stringToDOM("<tns:ApplicationData xmlns:tns=\"http://foobar\"/>");
        
        java.util.List<?> list = _runtime.evaluate(exp, this);
        
        if (list == null || list.size() != 0) {
        	fail("List should have no elements");
        }
        
        if (((Element)_rootNode).getElementsByTagName(insertElementName).getLength() != 1) {
        	fail("Missing '"+insertElementName+"' element has not been inserted");
        }
    }
    
    public void testODE911() throws Exception {
        OXPath20ExpressionBPEL20 exp = compile("ode:delete($ODE991var/tns:empty)");
        Element retVal = (Element)_runtime.evaluateNode(exp, this);
        assertNotNull(retVal);
        assertEquals(3, retVal.getElementsByTagNameNS("http://foobar", "notempty").getLength());
        assertEquals(0, retVal.getElementsByTagNameNS("http://foobar", "empty").getLength());
    }

    public Node readVariable(Variable variable, Part part) throws FaultException {
        return _vars.get(variable.getName());
    }

    public Node getPartData(Element message, Part part) throws FaultException {
        // TODO Auto-generated method stub
        return null;
    }

    public String readMessageProperty(Variable variable, OProperty property) throws FaultException {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isLinkActive(OLink olink) throws FaultException {
        // TODO Auto-generated method stub
        return false;
    }

    public Node getRootNode() {
        return _rootNode;
    }

    public Node evaluateQuery(Node root, OExpression expr) throws FaultException {
        // TODO Auto-generated method stub
        return null;
    }

    public Long getProcessId() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean narrowTypes() {
        return true;
    }

    private OXPath20ExpressionBPEL20 compile(String xpath) {
        Document doc = DOMUtils.newDocument();
        Element e = doc.createElementNS(null, "expression");
        doc.appendChild(e);
        e.appendChild(doc.createTextNode(xpath));
        Expression exp = new Expression(e);
        exp.getNamespaceContext().register("tns", "http://foobar");
        exp.getNamespaceContext().register("ode", Namespaces.ODE_EXTENSION_NS);
        return (OXPath20ExpressionBPEL20)_compiler.compileLValue(exp);
    }

    public URI getBaseResourceURI() {
        return null;
    }

    public Node getPropertyValue(QName propertyName) {
        return null;
    }

    public QName getProcessQName() {
        return null;
    }

    public Date getCurrentEventDateTime() {
        return null;
    }
}
