/*
 * semanticcms-core-resources-servlet - Redistributable sets of SemanticCMS resources produced by the local servlet container.
 * Copyright (C) 2017  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This contextFile is part of semanticcms-core-resources-servlet.
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

import com.aoindustries.io.IoUtils;
import com.aoindustries.servlet.ServletContextCache;
import com.aoindustries.tempfiles.TempFile;
import com.aoindustries.tempfiles.TempFileContext;
import com.semanticcms.core.resources.ResourceConnection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import javax.servlet.ServletContext;

public class ServletResourceConnection extends ResourceConnection {

	private final ServletContext servletContext;
	private final ServletContextCache cache;
	private final String servletPath;

	private File contextFile;
	private boolean contextFileSet;

	private URL contextUrl;
	private boolean contextUrlSet;

	private InputStream in;

	private boolean fileAccessed;

	private URLConnection urlConn;
	private boolean urlConnInputAccessed;
	private TempFileContext tempFileContext;
	private TempFile tempFile;

	private boolean closed;

	public ServletResourceConnection(ServletResource resource) {
		super(resource);
		this.servletContext = resource.servletContext;
		this.cache = resource.cache;
		this.servletPath = resource.servletPath;
	}

	@Override
	public ServletResource getResource() {
		return (ServletResource)resource;
	}

	/**
	 * Gets the underlying file provided by the servlet container, making sure
	 * it exists when using the cache.
	 *
	 * Only returns files that exist at the time of first access to this method.
	 */
	File getContextFile() {
		if(!contextFileSet) {
			if(cache == null) {
				// Not using cache
				String realPath = servletContext.getRealPath(servletPath);
				if(realPath != null) {
					contextFile = new File(realPath);
					assert contextFile.exists() : "File doesn't exist from ServletContext.getRealPath: File recently removed? " + contextFile;
				}
			} else {
				// Using cache
				String realPath = cache.getRealPath(servletPath);
				if(realPath != null) {
					File f = new File(realPath);
					// Check that still exists, since using cache contextFile might have been recently removed
					contextFile = f.exists() ? f : null;
				}
			}
			contextFileSet = true;
		}
		return contextFile;
	}

	private URL getContextUrl() throws MalformedURLException {
		if(!contextUrlSet) {
			contextUrl = (cache == null)
				? servletContext.getResource(servletPath)
				: cache.getResource(servletPath);
			contextUrlSet = true;
		}
		return contextUrl;
	}

	@Override
	public boolean exists() throws IOException, IllegalStateException {
		if(closed) throw new IllegalStateException("Connection closed: " + resource);
		return
			// Note: non-null from getContextFile means exists.
			getContextFile() != null // Micro optimization?  Thus shortcuts when resource is an existing local file, checking here since other methods use file first, too
			|| getContextUrl() != null
		;
	}

	@Override
	public long getLength() throws IOException, FileNotFoundException, IllegalStateException {
		if(closed) throw new IllegalStateException("Connection closed: " + resource);
		File file = getContextFile();
		if(file != null) {
			// Note: non-null from getContextFile means exists.
			// TODO: Java 1.7: Handle 0 as unknown to convert to -1: Files.readAttributes
			//                 Could do some reflection tricks to avoid hard dependency on Java 1.7, or just bump our java version globally.
			return file.length();
		} else {
			// Handle as URL
			URL url = getContextUrl();
			if(url == null) throw new FileNotFoundException(resource.toString());
			if(urlConn == null) urlConn = url.openConnection();
			// TODO: Java 1.7: getContentLengthLong(), could do now with reflection
			return urlConn.getContentLength();
		}
	}

	@Override
	public long getLastModified() throws IOException, FileNotFoundException, IllegalStateException {
		if(closed) throw new IllegalStateException("Connection closed: " + resource);
		File file = getContextFile();
		if(file != null) {
			// Note: non-null from getContextFile means exists.
			return file.lastModified();
		} else {
			// Handle as URL
			URL url = getContextUrl();
			if(url == null) throw new FileNotFoundException(resource.toString());
			if(urlConn == null) urlConn = url.openConnection();
			return urlConn.getLastModified();
		}
	}

	@Override
	public InputStream getInputStream() throws IOException, FileNotFoundException, IllegalStateException {
		if(closed) throw new IllegalStateException("Connection closed: " + resource);
		if(in != null) throw new IllegalStateException("Input already opened: " + resource.toString());
		if(fileAccessed) throw new IllegalStateException("File already accessed: " + resource.toString());
		File file = getContextFile();
		if(file != null) {
			// Note: non-null from getContextFile means exists.
			in = new FileInputStream(file);
		} else {
			// Handle as URL
			URL url = getContextUrl();
			if(url == null) throw new FileNotFoundException(resource.toString());
			if(urlConn == null) urlConn = url.openConnection();
			in = urlConn.getInputStream();
			urlConnInputAccessed = true;
		}
		return in;
	}

	@Override
	public File getFile() throws IOException, FileNotFoundException, IllegalStateException {
		if(closed) throw new IllegalStateException("Connection closed: " + resource);
		if(in != null) throw new IllegalStateException("Input already opened: " + resource.toString());
		File file = getContextFile();
		if(file != null) {
			// Note: non-null from getContextFile means exists.
			fileAccessed = true;
			return file;
		} else {
			if(tempFile == null) {
				// Handle as URL
				URL url = getContextUrl();
				if(url == null) throw new FileNotFoundException(resource.toString());
				if(urlConn == null) urlConn = url.openConnection();
				boolean success = false;
				try {
					if(tempFileContext == null) {
						tempFileContext = new TempFileContext(
							(File)servletContext.getAttribute(ServletContext.TEMPDIR) // javax.servlet.context.tempdir
						);
					}
					tempFile = tempFileContext.createTempFile(ServletResourceConnection.class.getName(), null);
					FileOutputStream tmpOut = new FileOutputStream(tempFile.getFile());
					try {
						InputStream urlIn = urlConn.getInputStream();
						try {
							urlConnInputAccessed = true;
							IoUtils.copy(urlIn, tmpOut);
						} finally {
							urlIn.close();
						}
					} finally {
						tmpOut.close();
					}
					success = true;
				} finally {
					if(tempFile != null && !success) {
						tempFile.close();
						tempFile = null;
					}
				}
				fileAccessed = true;
			}
			return tempFile.getFile();
		}
	}

	@Override
	public void close() throws IOException {
		if(in != null) in.close();
		if(urlConn != null && !urlConnInputAccessed) {
			// Close input if not accessed to let underlying URLConnection close.
			InputStream urlIn = urlConn.getInputStream();
			try {
				urlConnInputAccessed = true;
			} finally {
				urlIn.close();
			}
		}
		// Closed with its context
		//if(tempFile != null) {
		//	tempFile.close();
		//	tempFile = null;
		//}
		if(tempFileContext != null) {
			tempFileContext.close();
		}
		closed = true;
	}
}
