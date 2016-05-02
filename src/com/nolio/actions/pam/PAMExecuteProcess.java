package com.nolio.actions.pam;

import com.nolio.platform.shared.api.*;

import javax.xml.soap.*;
import javax.xml.xpath.*;

/** @author kouth01 */
@ActionDescriptor(name = "PAM - Start Process", description = "This action starts a PAM process and returns the instance ROID", category={"CA Process Automation"})

public class PAMExecuteProcess implements NolioAction {

    private static final long serialVersionUID = 2000L;
    
    @ParameterDescriptor(name="Username", description="PAM login username", out=false, in=true, order=1)
    private String username="";

    @ParameterDescriptor(name="Password", description="PAM login password", out=false, in=true, order=2)    
    private Password password;    

    @ParameterDescriptor(name="Domain URL", description="Example - http://pamserver:8080/itpam", out=false, in=true, order=3)  
    private String domainUrl="";                  

    @ParameterDescriptor(name="Process (Full Path)", description="Full path to process", out=false, in=true, order=4)  
    private String processPath="";         

    @ParameterDescriptor(name="Parameters", description="Array of parameters (in the format of name:value) to pass to process", out=false, in=true, order=5)  
    private String[] processParams;         
    
    @ParameterDescriptor(name="Process Instance ROID", description="ROID of process instance that was started", out=true, in=false)
    private String instanceRoid="";

    @Override
    public ActionResult executeAction() {
        try {
        	makeSoapCall();
        	_log.info("Process [" + processPath + "] started on [" + domainUrl + "] with Instance ROID: " + instanceRoid);
        	return new ActionResult(true, "Process [" + processPath + "] started on [" + domainUrl + "] with Instance ROID: " + instanceRoid);
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
        headers.addHeader("SOAPAction", "ExecuteC2OFlow");        
        
        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("itp", "http://www.ca.com/itpam");

        SOAPBody soapBody = envelope.getBody();
        SOAPElement soapBodyElem = soapBody.addChildElement("executeProcess", "itp");
        SOAPElement soapBodyElemFlow = soapBodyElem.addChildElement("flow", "itp");
        SOAPElement soapBodyElemName = soapBodyElemFlow.addChildElement("name", "itp");
        soapBodyElemName.addTextNode(processPath);        
        SOAPElement soapBodyElemAction = soapBodyElemFlow.addChildElement("action", "itp");        
        soapBodyElemAction.addTextNode("start");        
        
        SOAPElement soapBodyElemAuth = soapBodyElemFlow.addChildElement("auth", "itp");
        SOAPElement soapBodyElemUser = soapBodyElemAuth.addChildElement("user", "itp");
        soapBodyElemUser.addTextNode(username);
        SOAPElement soapBodyElemPass = soapBodyElemAuth.addChildElement("password", "itp");
        soapBodyElemPass.addTextNode(password != null ? password.getPassword() : "");
        
        SOAPElement soapBodyElemParams = soapBodyElemFlow.addChildElement("params", "itp");   
        SOAPElement soapBodyElemParam;
        for (int i = 0; i < processParams.length; i++) {
        	String[] splitString = (processParams[i].split(":"));
	        soapBodyElemParam = soapBodyElemParams.addChildElement("param", "itp");
	        soapBodyElemParam.setAttribute("name", splitString[0]);
	        soapBodyElemParam.addTextNode(splitString[1]);
        }
                
        soapMessage.saveChanges();

        return soapMessage;
    }

    /** Process the SOAP response and retrieve items of interest */
    private void processSoapResponse(SOAPMessage soapResponse) throws Exception {
        XPathFactory factory=XPathFactory.newInstance();
        XPath xPath=factory.newXPath();
        instanceRoid = xPath.evaluate("//*[local-name()='ROID']/text()", soapResponse.getSOAPBody());
    }
}