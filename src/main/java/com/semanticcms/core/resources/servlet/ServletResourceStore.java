/*
 * semanticcms-core-resources-servlet - Redistributable sets of SemanticCMS resources produced by the local servlet container.
 * Copyright (C) 2017  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-resources-servlet.
 *
 * semanticcms-core-resources-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-resources-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-resources-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.resources.servlet;

import com.semanticcms.core.resources.Resource;
import com.semanticcms.core.resources.ResourceStore;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;

/**
 * Accesses resources in the local {@link ServletContext}.
 *
 * @see  ServletContext#getResource(java.lang.String)
 * @see  ServletContext#getResourceAsStream(java.lang.String)
 * @see  ServletContext#getRealPath(java.lang.String)
 */
public class ServletResourceStore implements ResourceStore {

	private static final String INSTANCES_SERVLET_CONTEXT_KEY = ServletResourceStore.class.getName() + ".instances";

	/**
	 * Gets the servlet store for the given context and prefix.
	 * Only one {@link ServletResourceStore} is created per unique context and prefix.
	 *
	 * @param  prefix  Must be either empty or a {@link Resource#checkPath(java.lang.String) valid path}.
	 *                 Any trailing slash "/" will be stripped, after validity check.
	 */
	public static ServletResourceStore getInstance(ServletContext servletContext, String prefix) {
		if(!prefix.isEmpty()) {
			Resource.checkPath(prefix);
			// Strip any trailing slash
			if(prefix.endsWith("/")) prefix = prefix.substring(0, prefix.length() - 1);
			assert !prefix.endsWith("/") : "Trailing double-slash should have been caught by Resource.checkPath";
		}

		Map<String,ServletResourceStore> instances;
		synchronized(servletContext) {
			@SuppressWarnings("unchecked")
			Map<String,ServletResourceStore> map = (Map<String,ServletResourceStore>)servletContext.getAttribute(INSTANCES_SERVLET_CONTEXT_KEY);
			if(map == null) {
				map = new HashMap<String,ServletResourceStore>();
				servletContext.setAttribute(INSTANCES_SERVLET_CONTEXT_KEY, map);
			}
			instances = map;
		}
		synchronized(instances) {
			ServletResourceStore store = instances.get(prefix);
			if(store == null) {
				store = new ServletResourceStore(servletContext, prefix);
				instances.put(prefix, store);
			}
			return store;
		}
	}

	private final ServletContext servletContext;
	private final String prefix;

	private ServletResourceStore(ServletContext servletContext, String prefix) {
		this.servletContext = servletContext;
		this.prefix = prefix;
	}

	public ServletContext getServletContext() {
		return servletContext;
	}

	public String getPrefix() {
		return prefix;
	}

	@Override
	public String toString() {
		return "servlet:" + prefix;
	}

	@Override
	public ServletResource getResource(String path) {
		return new ServletResource(this, path);
	}
}
