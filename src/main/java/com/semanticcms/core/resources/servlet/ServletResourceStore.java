/*
 * semanticcms-core-resources-servlet - Redistributable sets of SemanticCMS resources produced by the local servlet container.
 * Copyright (C) 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.hodgepodge.util.Tuple2;
import com.aoapps.net.Path;
import com.aoapps.servlet.ServletContextCache;
import com.semanticcms.core.resources.ResourceStore;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 * Accesses resources in the local {@link ServletContext}.
 * <p>
 * Optionally, and by default, uses {@link ServletContextCache} to work around some performance issues
 * with direct use of {@link ServletContext}, especially when a large number of JAR files are deployed
 * to <code>/WEB-INF/lib</code>, which is one of the shared content distribution models for
 * <a href="https://semanticcms.com/">SemanticCMS</a>.
 * </p>
 *
 * @see  ServletContextCache
 * @see  ServletContext#getResource(java.lang.String)
 * @see  ServletContext#getResourceAsStream(java.lang.String)
 * @see  ServletContext#getRealPath(java.lang.String)
 */
public class ServletResourceStore implements ResourceStore {

	@WebListener
	public static class Initializer implements ServletContextListener {
		@Override
		public void contextInitialized(ServletContextEvent event) {
			getInstances(event.getServletContext());
		}
		@Override
		public void contextDestroyed(ServletContextEvent event) {
			// Do nothing
		}
	}

	private static final String INSTANCES_APPLICATION_ATTRIBUTE = ServletResourceStore.class.getName() + ".instances";

	private static ConcurrentMap<Tuple2<Path, Boolean>, ServletResourceStore> getInstances(ServletContext servletContext) {
		@SuppressWarnings("unchecked")
		ConcurrentMap<Tuple2<Path, Boolean>, ServletResourceStore> instances = (ConcurrentMap<Tuple2<Path, Boolean>, ServletResourceStore>)servletContext.getAttribute(INSTANCES_APPLICATION_ATTRIBUTE);
		if(instances == null) {
			instances = new ConcurrentHashMap<>();
			servletContext.setAttribute(INSTANCES_APPLICATION_ATTRIBUTE, instances);
		}
		return instances;
	}

	/**
	 * Gets the servlet store for the given context and prefix.
	 * Only one {@link ServletResourceStore} is created per unique context and prefix.
	 *
	 * @param  path  Must be a {@link Path valid path}.
	 *               Any trailing slash "/" will be stripped.
	 *
	 * @param cached  Enables use of {@link ServletContextCache} to workaround some performance issues with direct use of {@link ServletContext},
	 *                but introduces a potential delay of up to {@link ServletContextCache#REFRESH_INTERVAL} milliseconds (current 5 seconds)
	 *                before new or moved content becomes visible.
	 */
	public static ServletResourceStore getInstance(ServletContext servletContext, Path path, boolean cached) {
		// Strip trailing '/' to normalize
		{
			String pathStr = path.toString();
			if(!pathStr.equals("/") && pathStr.endsWith("/")) {
				path = path.prefix(pathStr.length() - 1);
			}
		}

		ConcurrentMap<Tuple2<Path, Boolean>, ServletResourceStore> instances = getInstances(servletContext);
		Tuple2<Path, Boolean> key = new Tuple2<>(path, cached);
		ServletResourceStore store = instances.get(key);
		if(store == null) {
			store = new ServletResourceStore(servletContext, path, cached);
			ServletResourceStore existing = instances.putIfAbsent(key, store);
			if(existing != null) store = existing;
		}
		return store;
	}

	/**
	 * Gets a cached instance.
	 *
	 * @see  #getInstance(javax.servlet.ServletContext, com.aoapps.net.Path)
	 */
	public static ServletResourceStore getInstance(ServletContext servletContext, Path path) {
		return getInstance(servletContext, path, true);
	}

	final ServletContext servletContext;
	final Path path;
	final String prefix;
	final ServletContextCache cache;

	private ServletResourceStore(ServletContext servletContext, Path path, boolean cached) {
		this.servletContext = servletContext;
		this.path = path;
		String pathStr = path.toString();
		this.prefix = pathStr.equals("/") ? "" : pathStr;
		this.cache = cached ? ServletContextCache.getInstance(servletContext) : null;
	}

	public ServletContext getServletContext() {
		return servletContext;
	}

	/**
	 * Gets the path, without any trailing slash except for "/".
	 */
	public Path getPath() {
		return path;
	}

	/**
	 * Gets the prefix useful for direct path concatenation, which is the path itself except empty string for "/".
	 */
	public String getPrefix() {
		return prefix;
	}

	@Override
	public String toString() {
		return "servlet:" + prefix;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public ServletResource getResource(Path path) {
		// TODO: If path starts with /WEB-INF(/.*) or /META-INF(/.*) (case-insensitive), always not found?
		// TODO: What if we had a local book, not published, contained in /WEB-INF/?
		return new ServletResource(this, path);
	}
}
