package com.nolio.actions.pam;

import java.util.ArrayList;
import java.util.List;

import com.nolio.platform.shared.api.*;

import javax.xml.soap.*;
import javax.xml.xpath.*;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** @author kouth01 */
@ActionDescriptor(name = "PAM - Get Process Status", description = "This action returns the status and dataset of a PAM process instance", category={"CA Process Automation"})

public class PAMGetProcessStatus implements NolioAction {
	
    private static final long serialVersionUID = 2000L;
    
    @ParameterDescriptor(name="Username", description="PAM login username", out=false, in=true, order=1)
    private String username="";

    @ParameterDescriptor(name="Password", description="PAM login password", out=false, in=true, order=2)    
    private Password password;    

    @ParameterDescriptor(name="Domain URL", description="Example - http://pamserver:8080/itpam", out=false, in=true, order=3)  
    private String domainUrl="";          

    @ParameterDescriptor(name="Process Instance ROID", description="ROID of process instance to check", out=false, in=true, order=5)
    private String instanceRoid="";
    
    @ParameterDescriptor(name="Process Instance Status", description="Status of process instance", out=true, in=false)
    private String instanceStatus="";

    @ParameterDescriptor(name="Process Instance Dataset", description="Dataset of process instance", out=true, in=false)
    private String[] processDataset;
    
    @Override
    public ActionResult executeAction() {
        try {
        	makeSoapCall();
        	return new ActionResult(true, "Status for Process Instance [" + instanceRoid + "] on [" + domainUrl + "] is: " + instanceStatus );
        } catch (Exception e) {
        	return new ActionResult(false, e.getMessage());
        }    	
    }
    
    /** Send SOAP request and redirect exceptions */
    public void makeSoapCall() throws Exception {
        // Create SOAP Connection
        SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
        SOAPConnection soapConnection = soapConnectionFactory.createConnection();

        String url = domainUrl + "/soap";
        SOAPMessage soapResponse;
        try {
        	 soapResponse = soapConnection.call(createSoapRequest(), url);
        } catch (Exception e) {
        	if (e.getMessage().contains("Message send failed")) {
        		throw new Exception("Unable to connect to [" + domainUrl + "]. Please verify host and port.");
        	}
        	throw new Exception("SOAP Call Exception: " + e.getMessage());
        }        
        
        if (soapResponse.getSOAPBody().hasFault()) {
        	SOAPFault fault = soapResponse.getSOAPBody().getFault();
        	throw new Exception("SOAP Fault Received: " + fault.getFaultString());
        }

        processSoapResponse(soapResponse);
        soapConnection.close();
    }

    /** Create the SOAP Request message */  
    private SOAPMessage createSoapRequest() throws Exception {
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPMessage soapMessage = messageFactory.createMessage();

        MimeHeaders headers = soapMessage.getMimeHeaders();
        headers.addHeader("SOAPAction", "GetFlowState");
        
        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("itp", "http://www.ca.com/itpam");


        SOAPBody soapBody = envelope.getBody();
        SOAPElement soapBodyElem = soapBody.addChildElement("getProcessStatus", "itp");
        SOAPElement soapBodyElemFlow = soapBodyElem.addChildElement("flow", "itp");
        SOAPElement soapBodyElemROID = soapBodyElemFlow.addChildElement("ROID", "itp");
        soapBodyElemROID.addTextNode(instanceRoid);        
        SOAPElement soapBodyElemAction = soapBodyElemFlow.addChildElement("action", "itp");        
        soapBodyElemAction.addTextNode("check");        
        
        SOAPElement soapBodyElemAuth = soapBodyElemFlow.addChildElement("auth", "itp");
        SOAPElement soapBodyElemUser = soapBodyElemAuth.addChildElement("user", "itp");
        soapBodyElemUser.addTextNode(username);
        SOAPElement soapBodyElemPass = soapBodyElemAuth.addChildElement("password", "itp");
        soapBodyElemPass.addTextNode(password != null ? password.getPassword() : "");
        soapMessage.saveChanges();

        return soapMessage;
    }

    /** Process the SOAP response and retrieve items of interest */
    private void processSoapResponse(SOAPMessage soapResponse) throws Exception {
        XPathFactory factory=XPathFactory.newInstance();
        XPath xPath=factory.newXPath();
        instanceStatus = xPath.evaluate("//*[local-name()='flow-state']/text()", soapResponse.getSOAPBody());

        // Get names/values of all dataset parameters
        String name;
        String value;      
        XPathExpression nameExpression=xPath.compile("./@name");
        XPathExpression valueExpression=xPath.compile("./text()");
        Object result = xPath.evaluate("//*[local-name()='params']/*[local-name()='param']", soapResponse.getSOAPBody(), XPathConstants.NODESET);
        NodeList params = (NodeList) result;

        List<String> ds = new ArrayList<String>();     
        
        for (int i = 0; i < params.getLength(); i++) {
            Node n = (Node) params.item(i);
            if (n != null && n.getNodeType() == Node.ELEMENT_NODE) {
                Element param = (Element) n;
                name = nameExpression.evaluate(param);
                value = valueExpression.evaluate(param);
                ds.add(name + ":" + value);
                //if (processDataset.equals(""))
                //{
                //    processDataset = name + ":" + value;                                          	                
                //} else {
                //    processDataset += "\n" + name + ":" + value;                                          	                
                //}
            }
        }
        processDataset = new String[ds.size()];
        ds.toArray(processDataset);        
        
    }
}