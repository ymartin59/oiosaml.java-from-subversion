/*
 * The contents of this file are subject to the Mozilla Public 
 * License Version 1.1 (the "License"); you may not use this 
 * file except in compliance with the License. You may obtain 
 * a copy of the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an 
 * "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express 
 * or implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 *
 * The Original Code is OIOSAML Java Service Provider.
 * 
 * The Initial Developer of the Original Code is Trifork A/S. Portions 
 * created by Trifork A/S are Copyright (C) 2008 Danish National IT 
 * and Telecom Agency (http://www.itst.dk). All Rights Reserved.
 * 
 * Contributor(s):
 *   Joakim Recht <jre@trifork.com>
 *   Rolf Njor Jensen <rolf@trifork.com>
 *
 */
package dk.itst.oiosaml.sp.service;

import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import dk.itst.oiosaml.sp.OIOPrincipal;
import dk.itst.oiosaml.sp.UserAssertion;

public class SAMLHttpServletRequest extends HttpServletRequestWrapper {

	private final UserAssertion assertion;
	private final String hostname;

	public SAMLHttpServletRequest(HttpServletRequest request, UserAssertion assertion, String hostname) {
		super(request);
		this.assertion = assertion;
		this.hostname = hostname;
	}

	@Override
	public String getRemoteUser() {
		if (assertion != null) {
			return assertion.getSubject();
		} else {
			return super.getRemoteUser();
		}
	}
	
	@Override
	public Principal getUserPrincipal() {
		if (assertion != null) {
			return new OIOPrincipal(assertion);
		} else {
			return super.getUserPrincipal();
		}
	}
	
	@Override
	public StringBuffer getRequestURL() {
		String url = super.getRequestURL().toString();
		
		String mod = hostname + url.substring(url.indexOf('/', 8));
		return new StringBuffer(mod);
	}

}