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
package dk.itst.oiosaml.sp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.opensaml.common.xml.SAMLConstants;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.xml.security.credential.Credential;

import dk.itst.oiosaml.configuration.SAMLConfiguration;
import dk.itst.oiosaml.error.InvalidCertificateException;
import dk.itst.oiosaml.sp.metadata.IdpMetadata;
import dk.itst.oiosaml.sp.metadata.SPMetadata;
import dk.itst.oiosaml.sp.metadata.IdpMetadata.Metadata;
import dk.itst.oiosaml.sp.model.OIOAssertion;
import dk.itst.oiosaml.sp.model.OIOAttributeQuery;
import dk.itst.oiosaml.sp.service.util.Constants;
import dk.itst.oiosaml.sp.service.util.HttpSOAPClient;
import dk.itst.oiosaml.sp.service.util.SOAPClient;
import dk.itst.oiosaml.sp.service.util.Utils;
import dk.itst.oiosaml.sp.util.AttributeUtil;

public class UserAttributeQuery {

	private final String username;
	private final String password;
	private final SOAPClient client;
	private final Credential credential;
	private final boolean ignoreCertPath;
	private final boolean requireEncryption;
	private final Metadata idpMetadata;
	private final String spEntityId;
	
	public UserAttributeQuery() {
		this(SAMLConfiguration.getSystemConfiguration().getString(Constants.PROP_RESOLVE_USERNAME, null), SAMLConfiguration.getSystemConfiguration().getString(Constants.PROP_RESOLVE_PASSWORD, null));
	}
	
	public UserAttributeQuery(String username, String password) {
		this(UserAssertionHolder.get() != null ? UserAssertionHolder.get().getIssuer() : null, username, password);
	}

	public UserAttributeQuery(String idpEntityId, String username, String password) {
		this(IdpMetadata.getInstance().getMetadata(idpEntityId), username, password, new HttpSOAPClient(), 
				Utils.getCredential(SAMLConfiguration.getStringPrefixedWithBRSHome(
						SAMLConfiguration.getSystemConfiguration(), Constants.PROP_CERTIFICATE_LOCATION), 
						SAMLConfiguration.getSystemConfiguration().getString(Constants.PROP_CERTIFICATE_PASSWORD)),
				SAMLConfiguration.getSystemConfiguration().getBoolean(Constants.PROP_IGNORE_CERTPATH, false),
				SAMLConfiguration.getSystemConfiguration().getBoolean(Constants.PROP_REQUIRE_ENCRYPTION, true),
				SPMetadata.getInstance().getEntityID());
	}
	
	public UserAttributeQuery(Metadata idpMetadata, String username, String password, SOAPClient client, Credential credential, boolean ignoreCertPath, boolean requireEncryption, String spEntityId) {
		this.spEntityId = spEntityId;
		if (idpMetadata == null) throw new IllegalArgumentException("IdP Metadata cannot be null");
		this.idpMetadata = idpMetadata;
		this.username = username;
		this.password = password;
		this.client = client;
		this.credential = credential;
		this.ignoreCertPath = ignoreCertPath;
		this.requireEncryption = requireEncryption;
	}
	
	public Collection<UserAttribute> query(String nameId, NameIDFormat format, String ... attributes) throws InvalidCertificateException, IOException {
		UserAttribute[] attrs = new UserAttribute[attributes.length];
		for (int i = 0; i < attributes.length; i++) {
			attrs[i] = UserAttribute.create(attributes[i], null);
		}
		return query(nameId, format, attrs);
	}
	
	public Collection<UserAttribute> query(String nameId, NameIDFormat format, UserAttribute ... attributes) throws InvalidCertificateException, IOException {
		OIOAttributeQuery q = OIOAttributeQuery.newQuery(idpMetadata.getAttributeQueryServiceLocation(SAMLConstants.SAML2_SOAP11_BINDING_URI), nameId, format, spEntityId);
		
		for (UserAttribute attribute : attributes) {
			q.addAttribute(attribute.getName(), attribute.getFormat());
		}
		OIOAssertion res = q.executeQuery(client, credential, username, password, ignoreCertPath, idpMetadata.getCertificate(), !requireEncryption);
		
		Collection<UserAttribute> attrs = new ArrayList<UserAttribute>();
		for (AttributeStatement attrStatement : res.getAssertion().getAttributeStatements()) {
			for (Attribute attr : attrStatement.getAttributes()) {
				attrs.add(new UserAttribute(attr.getName(), attr.getFriendlyName(), AttributeUtil.extractAttributeValueValue(attr), attr.getNameFormat()));
			}
		}
		
		return attrs;
	}
}