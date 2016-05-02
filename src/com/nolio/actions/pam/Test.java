package com.nolio.actions.pam;

import com.nolio.platform.shared.api.*;

/**
 * An example Nolio action
 * 
 * <p>Date: Oct 22, 2013</p>
 *
 * @author Administrator
 */
@ActionDescriptor(
    name = "Hello Action",
    description = "This action receives a name and returns a welcome greeting.",
    category="Greeting.Hello" /* Parent category is greeting, and sub category is Hello */)
public class Test implements NolioAction {
    private static final long serialVersionUID = 1L;
    
    @ParameterDescriptor(
    	name = "Username",
        description = "The name of the user",
        out = false,   // Whether parameter is an output.
        in = true,     // Whether parameter is an input.
        nullable = true,   // Whether parameter can have a null value. Must be true if a default value exists.
        defaultValueAsString = "Administrator", // Used as the default value of this parameter.
        order = 1 // Indicates the order of the parameters in the UI
    )    
    private String username = "Administrator";
	
    @ParameterDescriptor(
        name = "Welcome String",
        description = "Welcome greeting",
        out = true,
        in = false)
    private String welcomeString;

    @Override
    public ActionResult executeAction() {
    	if ("error".equalsIgnoreCase(username)) {
    		return new ActionResult(false, "Error occurred. sorry!");
    	} else {
			welcomeString = "Hello " + username;
			return new ActionResult(true, "Hello " + username);
		}        
    }
}
