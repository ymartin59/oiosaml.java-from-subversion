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
package dk.itst.oiosaml.sp.model;

import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.NameIDPolicy;
import org.opensaml.ws.message.encoder.MessageEncodingException;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.validation.ValidationException;

import dk.itst.oiosaml.common.SAMLUtil;
import dk.itst.oiosaml.error.Layer;
import dk.itst.oiosaml.error.WrappedException;
import dk.itst.oiosaml.logging.LogUtil;
import dk.itst.oiosaml.sp.NameIDFormat;
import dk.itst.oiosaml.sp.service.session.LoggedInHandler;
import dk.itst.oiosaml.sp.service.util.Constants;

public class OIOAuthnRequest extends OIORequest {
	private static final Logger log = Logger.getLogger(OIOAuthnRequest.class);

	private final AuthnRequest request;

	public OIOAuthnRequest(AuthnRequest request) {
		super(request);
		this.request = request;
	}
	
	
	public static OIOAuthnRequest buildAuthnRequest(String ssoServiceLocation, String spEntityId, String protocolBinding, HttpSession session, LogUtil logUtil) {
		AuthnRequest authnRequest = SAMLUtil.buildXMLObject(AuthnRequest.class);

		authnRequest.setIssuer(SAMLUtil.createIssuer(spEntityId));
		authnRequest.setID(LoggedInHandler.getInstance().getID(session, logUtil));
		authnRequest.setForceAuthn(Boolean.FALSE);
		authnRequest.setIssueInstant(new DateTime(DateTimeZone.UTC));
		authnRequest.setProtocolBinding(protocolBinding);
		authnRequest.setDestination(ssoServiceLocation);

		try {
			if (log.isDebugEnabled())
				log.debug("Validate the authnRequest...");
			authnRequest.validate(true);
			if (log.isDebugEnabled())
				log.debug("...OK");
		} catch (ValidationException e) {
			throw new WrappedException(Layer.CLIENT, e);
		}
		return new OIOAuthnRequest(authnRequest);
	}

	/**
	 * Generate a signed redirect url, which can be used for redirecting the browser to the IdP.
	 * 
	 * @param signingCredential The credential used for signing the url.
	 */
	public String getRedirectURL(Credential signingCredential, LogUtil lu) {
		lu.setRequestId(request.getID());
		Encoder enc = new Encoder();
		try {
			return enc.buildRedirectURL(signingCredential);
		} catch (MessageEncodingException e) {
			throw new WrappedException(Layer.CLIENT, e);
		} finally {
			lu.beforeService("", request.getDestination(), Constants.SERVICE_AUTHN_REQUEST, "ID=" + getID());

		}
	}
	
	public void setNameIDPolicy(String format, boolean allowCreate) {
		if (format == null || format.trim().equals("")) return;

		NameIDFormat idFormat = NameIDFormat.valueOf(format.toUpperCase());
		NameIDPolicy policy = SAMLUtil.buildXMLObject(NameIDPolicy.class);
		policy.setAllowCreate(allowCreate);
		policy.setFormat(idFormat.getFormat());
		policy.setSPNameQualifier(request.getIssuer().getValue());
		
		request.setNameIDPolicy(policy);
	}
	
	public String getRelayState() {
		return getID();
	}
	
	public boolean isForceAuthn() {
		return request.isForceAuthn() != null && request.isForceAuthn();
	}
	
	public void setForceAuthn(boolean forceAuthn) {
		request.setForceAuthn(forceAuthn);
	}
	
	public void setPasive(boolean passive) {
		request.setIsPassive(passive);
	}
	
	public boolean isPassive() {
		return request.isPassive() != null && request.isPassive();
	}
}