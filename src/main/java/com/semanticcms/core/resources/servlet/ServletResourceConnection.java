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

import com.aoindustries.io.FileUtils;
import com.aoindustries.io.IoUtils;
import com.aoindustries.servlet.ServletContextCache;
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
	private File tmpFile;

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
			if(tmpFile == null) {
				// Handle as URL
				URL url = getContextUrl();
				if(url == null) throw new FileNotFoundException(resource.toString());
				if(urlConn == null) urlConn = url.openConnection();
				File tmpDir = (File)servletContext.getAttribute(ServletContext.TEMPDIR); // javax.servlet.context.tempdir
				// Be resilient to temp directory issues
				if(
					tmpDir == null
					|| !tmpDir.exists()
					|| !tmpDir.isDirectory()
					|| !tmpDir.canWrite()
					|| !tmpDir.canRead()
				) {
					File systemTmpDir = new File(System.getProperty("java.io.tmpdir"));
					if(!systemTmpDir.equals(tmpDir)) {
						servletContext.log("Servlet temporary directory not set, doesn't exist, is not a directory, is not writable, or is not readable; using system temp directory instead: servletTemp=" + tmpDir + ", systemTemp=" + systemTmpDir);
						if(!systemTmpDir.exists()) throw new IOException("System temporary directory does not exist: " + systemTmpDir);
						if(!systemTmpDir.isDirectory()) throw new IOException("System temporary directory is not a directory: " + systemTmpDir);
						if(!systemTmpDir.canWrite()) throw new IOException("System temporary directory is not writable: " + systemTmpDir);
						if(!systemTmpDir.canRead()) throw new IOException("System temporary directory is not readable: " + systemTmpDir);
						tmpDir = systemTmpDir;
					}
				}
				boolean success = false;
				try {
					tmpFile = File.createTempFile(ServletResourceConnection.class.getName(), null, tmpDir);
					tmpFile.deleteOnExit(); // TODO: JDK implementation builds an ever-growing set.  Find or create an implementation with a shutdown hook that allows deregistering.
					FileOutputStream tmpOut = new FileOutputStream(tmpFile);
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
					if(tmpFile != null && !success) {
						FileUtils.delete(tmpFile);
						tmpFile = null;
					}
				}
				fileAccessed = true;
			}
			return tmpFile;
		}
	}

	@Override
	public void close() throws IOException {
		if(in != null) in.close();
		if(urlConn != null && !urlConnInputAccessed) {
			InputStream urlIn = urlConn.getInputStream();
			try {
				urlConnInputAccessed = true;
			} finally {
				urlIn.close();
			}
		}
		if(tmpFile != null) {
			FileUtils.delete(tmpFile);
			tmpFile = null;
		}
		closed = true;
	}
}
