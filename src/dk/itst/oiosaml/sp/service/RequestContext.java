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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.configuration.Configuration;
import org.opensaml.xml.security.credential.Credential;

import dk.itst.oiosaml.logging.LogUtil;
import dk.itst.oiosaml.sp.metadata.IdpMetadata;
import dk.itst.oiosaml.sp.metadata.SPMetadata;

public class RequestContext {
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final IdpMetadata idpMetadata;
	private final SPMetadata spMetadata;
	private final Credential credential;
	private final Configuration configuration;
	private final LogUtil lu;

	public RequestContext(HttpServletRequest request, HttpServletResponse response, IdpMetadata idpMetadata, SPMetadata spMetadata, Credential credential, Configuration configuration, LogUtil lu) {
		this.request = request;
		this.response = response;
		this.idpMetadata = idpMetadata;
		this.spMetadata = spMetadata;
		this.credential = credential;
		this.configuration = configuration;
		this.lu = lu;
	}

	public HttpServletRequest getRequest() {
		return request;
	}

	public HttpServletResponse getResponse() {
		return response;
	}

	public IdpMetadata getIdpMetadata() {
		return idpMetadata;
	}

	public SPMetadata getSpMetadata() {
		return spMetadata;
	}

	public Credential getCredential() {
		return credential;
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public LogUtil getLogUtil() {
		return lu;
	}

	public HttpSession getSession() {
		return request.getSession();
	}
}