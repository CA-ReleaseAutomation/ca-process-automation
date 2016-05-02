package com.nolio.actions.pam;

import com.nolio.platform.shared.api.*;

import javax.xml.soap.*;
import javax.xml.xpath.*;

/** @author kouth01 */
@ActionDescriptor(name = "PAM - Check Server Status", description = "This action returns the status of a PAM server", category={"CA Process Automation"})

public class PAMCheckServerStatus implements NolioAction {
	
    private static final long serialVersionUID = 2000L;
    
    @ParameterDescriptor(name="Username", description="PAM login username", out=false, in=true, order=1)
    private String username="";

    @ParameterDescriptor(name="Password", description="PAM login password", out=false, in=true, order=2)    
    private Password password;    

    @ParameterDescriptor(name="Domain URL", description="Example - http://pamserver:8080/itpam", out=false, in=true, order=3)  
    private String domainUrl="";          

    @ParameterDescriptor(name="Server Status", description="Server Status", out=true, in=false)
    private String serverStatus="";

    @Override
    public ActionResult executeAction() {
        try {
        	makeSoapCall();
        	_log.info("Status of [" + domainUrl + "]: " + serverStatus);
        	return new ActionResult(true, "Status of [" + domainUrl + "]: " + serverStatus);
        } catch (Exception e) {
        	_log.error(e.getMessage());
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
        headers.addHeader("SOAPAction", "checkServerStatus");
        
        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("itp", "http://www.ca.com/itpam");

        SOAPBody soapBody = envelope.getBody();
        SOAPElement soapBodyElem = soapBody.addChildElement("checkServerStatus", "itp");
        SOAPElement soapBodyElemAuth = soapBodyElem.addChildElement("auth", "itp");
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
        serverStatus = xPath.evaluate("//*[local-name()='serverStatus']/text()", soapResponse.getSOAPBody());           
    }
}