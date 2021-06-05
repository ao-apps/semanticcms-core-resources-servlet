/*
 * semanticcms-core-resources-servlet - Redistributable sets of SemanticCMS resources produced by the local servlet container.
 * Copyright (C) 2017, 2018, 2020, 2021  AO Industries, Inc.
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

import com.aoapps.net.Path;
import com.aoapps.servlet.ServletContextCache;
import com.semanticcms.core.resources.Resource;
import java.io.File;
import javax.servlet.ServletContext;

// TODO: Expose a way to dispatch to default servlet (or just the resource servletpath)
//       instead of serving through ResourceServlet?  This would have the side-effect of
//       potentially exposing other things mapped over this resource servletPath, which may
//       or may not be desirable.  Is this a more predictable default behavior, so servlet
//       resources remain more natural?  Security implications?  Performance implications?
public class ServletResource extends Resource {

	final ServletContext servletContext;
	final ServletContextCache cache;
	final String servletPath;

	public ServletResource(ServletResourceStore store, Path path) {
		super(store, path);
		this.servletContext = store.servletContext;
		this.cache = store.cache;
		String prefix = store.prefix;
		String pathStr = path.toString();
		int prefixLen = prefix.length();
		if(prefixLen == 0) {
			this.servletPath = pathStr;
		} else {
			int len = prefixLen + pathStr.length();
			this.servletPath =
				new StringBuilder(len)
				.append(prefix)
				.append(pathStr)
				.toString()
			;
			assert servletPath.length() == len;
		}
	}

	@Override
	public ServletResourceStore getStore() {
		return (ServletResourceStore)store;
	}

	@Override
	public boolean isFilePreferred() {
		return true;
	}

	/**
	 * @see  ServletContext#getRealPath(java.lang.String)
	 */
	@Override
	public File getFile() {
		if(cache == null) {
			// Not using cache
			String realPath = servletContext.getRealPath(servletPath);
			return (realPath == null) ? null : new File(realPath);
		} else {
			// Using cache
			String realPath = cache.getRealPath(servletPath);
			if(realPath == null) {
				return null;
			} else {
				// Check that still exists, since using cache file might have been recently removed
				File file = new File(realPath);
				return file.exists() ? file : null;
			}
		}
	}

	@Override
	public ServletResourceConnection open() {
		return new ServletResourceConnection(this);
	}
}
