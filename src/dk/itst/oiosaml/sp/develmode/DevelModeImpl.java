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
 * created by Trifork A/S are Copyright (C) 2009 Danish National IT 
 * and Telecom Agency (http://www.itst.dk). All Rights Reserved.
 * 
 * Contributor(s):
 *   Joakim Recht <jre@trifork.com>
 *   Rolf Njor Jensen <rolf@trifork.com>
 *
 */
package dk.itst.oiosaml.sp.develmode;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.velocity.VelocityContext;
import org.joda.time.DateTime;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;

import dk.itst.oiosaml.common.SAMLUtil;
import dk.itst.oiosaml.sp.UserAssertion;
import dk.itst.oiosaml.sp.UserAssertionHolder;
import dk.itst.oiosaml.sp.UserAssertionImpl;
import dk.itst.oiosaml.sp.model.OIOAssertion;
import dk.itst.oiosaml.sp.service.SAMLHttpServletRequest;
import dk.itst.oiosaml.sp.service.util.Constants;
import dk.itst.oiosaml.sp.service.util.HTTPUtils;
import dk.itst.oiosaml.sp.util.AttributeUtil;

public class DevelModeImpl implements DevelMode {
	private static final Logger log = Logger.getLogger(DevelModeImpl.class);

	public void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain fc, Configuration conf) throws IOException, ServletException {
		UserAssertionHolder.set(null);
		UserAssertion ua = (UserAssertion) req.getSession().getAttribute(Constants.SESSION_USER_ASSERTION);
		if (ua == null) {
			String[] users = conf.getStringArray("oiosaml-sp.develmode.users");
			if (users == null || users.length == 0) {
				log.error("No users defined in properties. Set oiosaml-sp.develmode.users");
				res.setStatus(500);
				HTTPUtils.sendCacheHeaders(res);
				render("nousers.vm", res, new HashMap<String, Object>());
				return;
			}
			
			if (users.length == 1) {
				ua = selectUser(users[0], conf);
			} else {
				String selected = req.getParameter("__oiosaml_devel");
				if (selected == null || !Arrays.asList(users).contains(selected)) {
					HTTPUtils.sendCacheHeaders(res);
					
					Map<String, Object> params = new HashMap<String, Object>();
					params.put("users", users);
					render("users.vm", res, params);
					return;
				} else {
					HTTPUtils.sendCacheHeaders(res);
					ua = selectUser(selected, conf);
					req.getSession().setAttribute(Constants.SESSION_USER_ASSERTION, ua);
					res.sendRedirect(req.getRequestURI() + "?" + req.getQueryString().substring(0, req.getQueryString().indexOf("__oiosaml_dev")));
					return;
				}
			}
		}
		
		if (ua != null) {
			req.getSession().setAttribute(Constants.SESSION_USER_ASSERTION, ua);
			UserAssertionHolder.set(ua);
			
			HttpServletRequestWrapper requestWrap = new SAMLHttpServletRequest(req, ua, "");
			fc.doFilter(requestWrap, res);
			return;
		} else {
			log.error("No assertion found");
			res.sendError(500);
			return;
		}
	}

	private UserAssertion selectUser(String user, Configuration conf) {
		Map<String, String[]> attributes = getAttributes(user, conf);
		
		Assertion a = SAMLUtil.buildXMLObject(Assertion.class);
		a.setSubject(SAMLUtil.createSubject(user, "urn:test", new DateTime().plusHours(1)));

		AttributeStatement as = SAMLUtil.buildXMLObject(AttributeStatement.class);
		a.getAttributeStatements().add(as);

		for (Map.Entry<String, String[]> e : attributes.entrySet()) {
			Attribute attr = AttributeUtil.createAttribute(e.getKey(), e.getKey(), "");
			for (String val : e.getValue()) {
				attr.getAttributeValues().add(AttributeUtil.createAttributeValue(val));
				as.getAttributes().add(attr);
			}
		}
		
		return new UserAssertionImpl(new OIOAssertion(a));
	}

	
	private Map<String, String[]> getAttributes(String user, Configuration conf) {
		String prefix = "oiosaml-sp.develmode." + user + ".";
		
		Map<String, String[]> attributes = new HashMap<String, String[]>();
		Iterator<?> i = conf.getKeys();
		while (i.hasNext()) {
			String key = (String) i.next();
			if (key.startsWith(prefix)) {
				String attr = key.substring(prefix.length());
				String[] value = conf.getStringArray(key);
				attributes.put(attr, value);
			}
		}
		return attributes;
	}

	private void render(String template, HttpServletResponse response, Map<String, ?> params) {
		VelocityContext ctx = new VelocityContext();
		for (Map.Entry<String, ?> e : params.entrySet()) {
			ctx.put(e.getKey(), e.getValue());
		}
		
		String prefix = "/" + getClass().getPackage().getName().replace('.', '/') + "/";
		try {
			HTTPUtils.getEngine().mergeTemplate(prefix + template, ctx, response.getWriter());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}
}