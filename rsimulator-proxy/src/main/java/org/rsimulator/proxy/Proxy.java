package org.rsimulator.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Proxy servlet proxies as {@link URIMapper} is confgured. 
 *
 * @author Magnus Bjuvensjö
 */
public class Proxy extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final int BUFFER_SIZE = 100000;
	private static final int READ_TIMEOUT = 12000;
	private Logger log = LoggerFactory.getLogger(Proxy.class);
	private URIMapper uriMapper;

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);
		try {
			uriMapper = new URIMapper();
		} catch (Exception e) {
			log.error("Can not initialize.", e);
		}
	}

	/**
	 * @see HttpServlet#service(HttpServletRequest request, HttpServletResponse
	 *      response)
	 */
	protected void service(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		try {
			String method = request.getMethod();
			String requestURI = request.getRequestURI();
			log.debug("requestURI: {}", requestURI);
			String requestURIWithoutContext = requestURI.replaceFirst(
					"[/]*[a-zA-Z0-9]+[/]*", "");
			log.debug("requestURIWithoutContext: {}", requestURIWithoutContext);
			String mappedURL = uriMapper.map(requestURIWithoutContext);
			log.debug("mappedURL: {}", mappedURL);
			
			HttpURLConnection con = getConnection(method, mappedURL);

			copyRequestHeaders(request, con);
			copyRequest(request, con);
			
			copyResponseHeaders(response, con);
			copyResponse(response, con);			
		} catch (Exception e) {
			log.error("Can not service.", e);
			throw new ServletException(e);
		}
	}	
	private HttpURLConnection getConnection(String method, String url) throws IOException {
		HttpURLConnection con = null;
		con = (HttpURLConnection) new URL(url).openConnection();
		con.setRequestMethod(method);
		con.setDoOutput(true);
		con.setDoInput(true);
		con.setReadTimeout(READ_TIMEOUT);
		return con;
	}	

	private void copyRequestHeaders(HttpServletRequest request,
			HttpURLConnection connection) {
		@SuppressWarnings("unchecked")
		Enumeration<String> headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String headerName = headerNames.nextElement();
			String headerValue = request.getHeader(headerName);
			connection.addRequestProperty(headerName, headerValue);
		}
	}

	private void copyRequest(HttpServletRequest request, HttpURLConnection con) throws IOException {
		String method = request.getMethod();
		log.debug("method: {}", method);
		if ("GET".equals(method) || "DELETE".equals(method)) {
			return;
		}
		InputStream is = request.getInputStream();
		OutputStream os = con.getOutputStream();
		byte[] buffer = new byte[BUFFER_SIZE];
		int n;
		while ((n = is.read(buffer)) > 0) {
			os.write(buffer, 0, n);
		}			
	}

	private void copyResponseHeaders(HttpServletResponse response, HttpURLConnection con) {
		response.setContentType(con.getContentType());
//		Map<String, List<String>> headerFields = con.getHeaderFields();
//		for (Entry<String, List<String>> entry: headerFields.entrySet()) {
//			String key = entry.getKey();
//			if (key != null) {
//				for (String value: entry.getValue()) {
//					if (value != null) {
//						response.addHeader(entry.getKey(), value);
//					}
//				}
//			}
//		}
	}
	
	private void copyResponse(HttpServletResponse response,
			HttpURLConnection con) throws IOException {
		InputStream is = con.getInputStream();
		ServletOutputStream os = response.getOutputStream();
		byte[] buffer = new byte[BUFFER_SIZE];
		int n;
		while ((n = is.read(buffer)) > 0) {
			os.write(buffer, 0, n);
		}
	}	
}