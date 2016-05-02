package com.nolio.actions.pam;

import com.nolio.platform.shared.api.*;

import javax.xml.soap.*;
import javax.xml.xpath.*;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** @author kouth01 */
@ActionDescriptor(name = "PAM - Wait For Process End", description = "This action waits for a defined period of time for a PAM process instance to end, returning the status and dataset of that instance", category={"CA Process Automation"})

public class PAMWaitForProcessEnd implements NolioAction {
	
    private static final long serialVersionUID = 2000L;
    private boolean processEnded = false;
    private SOAPMessage soapMessage = null;
    private XPath xPath = null;
    private SOAPMessage lastSoapResponse;
    
    @ParameterDescriptor(name="Username", description="PAM login username", out=false, in=true, order=1)
    private String username="";

    @ParameterDescriptor(name="Password", description="PAM login password", out=false, in=true, order=2)    
    private Password password;    

    @ParameterDescriptor(name="Domain URL", description="Example - http://pamserver:8080/itpam", out=false, in=true, order=3)  
    private String domainUrl="";                 

    @ParameterDescriptor(name="Process Instance ROID", description="ROID of process instance to check", out=false, in=true, order=5)
    private String instanceRoid="";
    
    @ParameterDescriptor(name="Polling Period (Seconds)", description="How often (in seconds) to check process status", out=false, in=true, nullable=true, defaultValueAsString="30", order=6)
    private Integer pollingSeconds=30;

    @ParameterDescriptor(name="Timeout (Seconds)", description="How long (in seconds) to wait for process to end before timing out", out=false, in=true, nullable=true, defaultValueAsString="600", order=7)
    private Integer timeoutSeconds=600;
    
    @ParameterDescriptor(name="Process Instance Status", description="Status of process instance", out=true, in=false)
    private String instanceStatus="";

    @ParameterDescriptor(name="Process Instance Dataset", description="Dataset of process instance", out=true, in=false)
    private String processDataset="";
    
    @Override
    public ActionResult executeAction()  {
    	Integer sleepSeconds;
    	Integer timeoutSecondsLeft = timeoutSeconds;
    	
	    try {
	       	while (!processEnded && timeoutSecondsLeft > 0) {

   	        	makeSoapCall();
   	   			if ("Completed".equals(instanceStatus) || "Failed".equals(instanceStatus) || "Aborted".equals(instanceStatus)) {
   	   				processEnded = true;
   	   				break;
   	   			}
   	   	   		
   	   			sleepSeconds = Math.min(timeoutSecondsLeft,  pollingSeconds);
   	   			try {
   	   				Thread.sleep(sleepSeconds * 1000);
   	   			} catch (InterruptedException e) {
   	   			}
   	   			timeoutSecondsLeft -= sleepSeconds;  
   	   	   		System.out.print("\nTimeout Seconds Left = " + timeoutSecondsLeft);
	       	}
	       	getProcessDataset(lastSoapResponse);
   	    }
	    catch (Exception e) {
        	return new ActionResult(false, e.getMessage());
        }
		
   		if (processEnded) {
   			return new ActionResult(true, "Process Instance [" + instanceRoid + "] on [" + domainUrl + "] ended with status: " + instanceStatus);
   		} else {
   			return new ActionResult(false, "Process Instance [" + instanceRoid + "] on [" + domainUrl + "] did not end within [" + timeoutSeconds +"] seconds timeout period.  Last status was: " + instanceStatus);
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

        lastSoapResponse = soapResponse;
        processSoapResponse(soapResponse);
        soapConnection.close();
    }

    /** Create the SOAP Request message */  
    private SOAPMessage createSoapRequest() throws Exception {
    	if (soapMessage != null) {
    		return soapMessage;
    	}
        MessageFactory messageFactory = MessageFactory.newInstance();
        soapMessage = messageFactory.createMessage();
        
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
        if (xPath == null) {
        	XPathFactory factory=XPathFactory.newInstance();
        	xPath=factory.newXPath();
        }
        instanceStatus = xPath.evaluate("//*[local-name()='flow-state']/text()", soapResponse.getSOAPBody());
    }
    
    private void getProcessDataset(SOAPMessage soapResponse) throws Exception {
    	String name;
        String value;      
        XPathExpression nameExpression=xPath.compile("./@name");
        XPathExpression valueExpression=xPath.compile("./text()");
        Object result = xPath.evaluate("//*[local-name()='params']/*[local-name()='param']", soapResponse.getSOAPBody(), XPathConstants.NODESET);
        NodeList params = (NodeList) result;

        for (int i = 0; i < params.getLength(); i++) {
            Node n = (Node) params.item(i);
            if (n != null && n.getNodeType() == Node.ELEMENT_NODE) {
                Element param = (Element) n;
                name = nameExpression.evaluate(param);
                value = valueExpression.evaluate(param);
                if (processDataset == null)
                {
                    processDataset = name + ":" + value;                                          	                
                } else {
                    processDataset += "\n" + name + ":" + value;                                          	                
                }
            }
        }    
    }
}