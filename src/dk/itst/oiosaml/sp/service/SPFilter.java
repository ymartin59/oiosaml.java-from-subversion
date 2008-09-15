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

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.opensaml.DefaultBootstrap;
import org.opensaml.xml.ConfigurationException;

import dk.itst.oiosaml.common.SAMLUtil;
import dk.itst.oiosaml.configuration.BRSConfiguration;
import dk.itst.oiosaml.error.Layer;
import dk.itst.oiosaml.error.WrappedException;
import dk.itst.oiosaml.logging.LogUtil;
import dk.itst.oiosaml.sp.UserAssertion;
import dk.itst.oiosaml.sp.UserAssertionHolder;
import dk.itst.oiosaml.sp.bindings.BindingHandler;
import dk.itst.oiosaml.sp.metadata.CRLChecker;
import dk.itst.oiosaml.sp.metadata.IdpMetadata;
import dk.itst.oiosaml.sp.service.session.LoggedInHandler;
import dk.itst.oiosaml.sp.service.util.Constants;

/**
 * Servlet filter for checking if the user is authenticated.
 * 
 * <p>If the user is authenticated, a session attribute, {@link Constants#SESSION_USER_ASSERTION} 
 * is set to contain a {@link UserAssertion} representing the user. The application layer
 * can access this object to retrieve SAML attributes for the user.</p>
 * 
 * <p>If the user is not authenticated, a &lt;AuthnRequest&gt; is created and sent to the IdP.
 * The protocol used for this is selected automatically based on th available bindings
 * in the SP and IdP metadata.</p>
 * 
 * <p>The atual redirects are done by {@link BindingHandler} objects.</p>
 * 
 * <p>Discovery profile is supported by looking at a request parameter named _saml_idp. If the parameter does not exist, the browser is 
 * redirected to {@link Constants#DISCOVERY_LOCATION}, which reads the domain cookie. If the returned value contains ids, one of the ids is selected.
 * If none of the ids in the list is registered, an exception is thrown. If no value has been set, the first configured IdP is selected automatically. 
 * </p>
 * 
 * @author Joakim Recht <jre@trifork.com>
 * @author Rolf Njor Jensen <rolf@trifork.com>
 */
public class SPFilter implements Filter {

	public static final String VERSION = "$Id: SPFilter.java 2950 2008-05-28 08:22:34Z jre $";
	private static final Logger log = Logger.getLogger(SPFilter.class);
	private CRLChecker crlChecker = new CRLChecker();
	
	private String entityID;
	private boolean filterInitialized;
	private Configuration conf;

	/**
	 * Static initializer for bootstrapping OpenSAML.
	 */
	static {
		try {
			DefaultBootstrap.bootstrap();
		} catch (ConfigurationException e) {
			throw new WrappedException(Layer.DATAACCESS, e);
		}
	}

	public void destroy() {
		LoggedInHandler.getInstance().stopCleanup();
		crlChecker.stopChecker();
	}

	/**
	 * Check whether the user is authenticated i.e. having session with a valid
	 * assertion. If the user is not authenticated an &lt;AuthnRequest&gt; is sent to
	 * the Login Site.
	 * 
	 * @param request
	 *            The servletRequest
	 * @param response
	 *            The servletResponse
	 */
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if (log.isDebugEnabled())
			log.debug("OIOSAML-J SP Filter invoked");

		if (!(request instanceof HttpServletRequest)) {
			throw new RuntimeException("Not supported operation...");
		}

		if(!isFilterInitialized()) {
			try {
				Configuration conf = BRSConfiguration.getSystemConfiguration();
				setRuntimeConfiguration(conf);
			} catch (IllegalStateException e) {
				request.getRequestDispatcher("/saml/configure").forward(request, response);
				return;
			}
			LoggedInHandler.getInstance().scheduleCleanupTasks(((HttpServletRequest)request).getSession().getMaxInactiveInterval());
		}
		HttpServletRequest servletRequest = ((HttpServletRequest) request);
		
		if (servletRequest.getServletPath().equals(conf.getProperty(Constants.PROP_SAML_SERVLET))) {
			log.debug("Request to SAML servlet, access granted");
			chain.doFilter(request, response);
			return;
		}
		
		final HttpSession session = servletRequest.getSession();
		if (log.isDebugEnabled())
			log.debug("sessionId....:" + session.getId());

		// Is the user logged in?
		if (LoggedInHandler.getInstance().isLoggedIn(session)) {
			int actualAssuranceLevel = LoggedInHandler.getInstance().getAssuranceLevel(session.getId());
			int assuranceLevel = conf.getInt(Constants.PROP_ASSURANCE_LEVEL);
			if (actualAssuranceLevel < assuranceLevel) {
				LoggedInHandler.getInstance().logOut(session);
				log.warn("Assurance level too low: " + actualAssuranceLevel + ", required: " + assuranceLevel);
				throw new RuntimeException("Assurance level too low: " + actualAssuranceLevel + ", required: " + assuranceLevel);
			}
			UserAssertion ua = (UserAssertion) session.getAttribute(Constants.SESSION_USER_ASSERTION);
			if (log.isDebugEnabled())
				log.debug("Everything is ok... Assertion: " + ua);

			UserAssertionHolder.set(ua);
			HttpServletRequestWrapper requestWrap = new SAMLHttpServletRequest(servletRequest, ua);
			chain.doFilter(requestWrap, response);
			return;
		} else {
			session.removeAttribute(Constants.SESSION_USER_ASSERTION);
			UserAssertionHolder.set(null);

			// Store the requested URI on the session
			session.setAttribute(Constants.SESSION_REQUESTURI, servletRequest.getRequestURI());
			if (log.isDebugEnabled()) log.debug("requestURI...:" + servletRequest.getRequestURI());

			session.setAttribute(Constants.SESSION_QUERYSTRING, servletRequest.getQueryString());
			if (log.isDebugEnabled()) log.debug("queryString..:" + servletRequest.getQueryString());

			String loginUrl = conf.getString(Constants.PROP_SAML_SERVLET, "/saml") + "/login";
			if (log.isDebugEnabled()) log.debug("Redirecting to login handler at " + loginUrl);
			
			RequestDispatcher dispatch = servletRequest.getRequestDispatcher(loginUrl);
			dispatch.forward(request, response);
		}
	}

	public void init(FilterConfig filterConfig) throws ServletException {
		String homeParam = filterConfig.getServletContext().getInitParameter(Constants.INIT_OIOSAML_HOME);
		log.info(Constants.INIT_OIOSAML_HOME + " set to " + homeParam + " in web.xml");
		if (homeParam == null) {
			homeParam = System.getProperty(SAMLUtil.OIOSAML_HOME);
		}
		log.info("Trying to retrieve configuration from " + homeParam);
		BRSConfiguration.setHomeProperty(homeParam);
		
		if (BRSConfiguration.isConfigured()) {
 			try {
				Configuration conf = BRSConfiguration.getSystemConfiguration();
				setRuntimeConfiguration(conf);
				setFilterInitialized(true);
				return;
			} catch (IllegalStateException e) {
				log.error("Unable to configure", e);
			}
		}
		log.info("The parameter " + Constants.INIT_OIOSAML_HOME + " which is set in web.xml to: " + homeParam  + " is not set to an (existing) directory, or the directory is empty - OIOSAML-J is not configured.");
		setFilterInitialized(false);
	}
	
	private void setRuntimeConfiguration(Configuration conf) {
		LogUtil.configureLog4j(BRSConfiguration.getStringPrefixedWithBRSHome(conf, Constants.PROP_LOG_FILE_NAME));
		restartCRLChecker(conf);
		setFilterInitialized(true);
		setConfiguration(conf);
		if (!IdpMetadata.getInstance().enableDiscovery()) {
			log.info("Discovery profile disabled, only one metadata file found");
		} else {
			if (conf.getString(Constants.DISCOVERY_LOCATION) == null) {
				throw new IllegalStateException("Discovery location cannot be null when discovery profile is active");
			}
		}
		LoggedInHandler.getInstance().resetReplayProtection(BRSConfiguration.getSystemConfiguration().getInt(Constants.PROP_NUM_TRACKED_ASSERTIONIDS)); 

		log.info("Home url: " + conf.getString(Constants.PROP_HOME));
		log.info("Assurance leve: " + conf.getInt(Constants.PROP_ASSURANCE_LEVEL));
		log.info("SP entity ID: " + entityID);
	}
	
	private void restartCRLChecker(Configuration conf) {
		crlChecker.stopChecker();
		int period = conf.getInt(Constants.PROP_CRL_CHECK_PERIOD, -1);
		if (period > 0) {
			crlChecker.startChecker(period, IdpMetadata.getInstance(), conf);
		}
	}

	public void setFilterInitialized(boolean b) {
		filterInitialized = b;
	}

	public boolean isFilterInitialized() {
		return filterInitialized;
	}
	
	public void setConfiguration(Configuration configuration) {
		conf = configuration;
	}

}