/*
 *  Copyright (C) 2011 Junpei Kawamoto
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package nor.plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.MatchResult;

import nor.core.plugin.PluginAdapter;
import nor.core.proxy.filter.FilterRegister;
import nor.core.proxy.filter.MessageHandler;
import nor.core.proxy.filter.MessageHandlerAdapter;
import nor.core.proxy.filter.RequestFilter;
import nor.core.proxy.filter.RequestFilterAdapter;
import nor.http.HeaderName;
import nor.http.HttpHeader;
import nor.http.HttpRequest;
import nor.http.HttpResponse;
import nor.http.Status;
import nor.util.io.Stream;
import nor.util.log.Logger;

public class Redirector extends PluginAdapter{

	private final List<ExplicitRedirector> explicits = new ArrayList<ExplicitRedirector>();
	private final List<ImplicitRedirector> implicits = new ArrayList<ImplicitRedirector>();

	private static final Logger LOGGER = Logger.getLogger(Redirector.class);

	//============================================================================
	//  public methods
	//============================================================================
	@Override
	public void init(final File common, final File local) throws IOException {
		LOGGER.entering("init", common, local);

		if(!common.exists()){

			final InputStream in = this.getClass().getResourceAsStream("default.conf");
			final OutputStream out = new FileOutputStream(common);

			Stream.copy(in, out);

			out.close();
			in.close();

		}
		this.loadConfig(common);

		if(local.exists()){

			this.loadConfig(local);

		}

		LOGGER.exiting("init");
	}

	@Override
	public MessageHandler[] messageHandlers() {
		LOGGER.entering("messageHandlers");

		MessageHandler[] res = null;
		final int size = this.explicits.size();
		if(size != 0){

			res = this.explicits.toArray(new MessageHandler[size]);

		}

		LOGGER.exiting("messageHandlers", res);
		return res;
	}

	@Override
	public RequestFilter[] requestFilters() {
		LOGGER.entering("requestFilters");

		RequestFilter[] res = null;
		final int size = this.implicits.size();
		if(size != 0){

			res = this.implicits.toArray(new RequestFilter[size]);

		}

		LOGGER.exiting("requestFilters");
		return res;
	}

	//============================================================================
	//  private methods
	//============================================================================
	private void loadConfig(final File file) throws IOException{

		final Properties prop = new Properties();
		final Reader r = new FileReader(file);
		prop.load(r);
		r.close();

		for(final Object k : prop.keySet()){

			final String key = k.toString();
			final String value = prop.getProperty(key);

			if(value != null && value.length() > 2){

				final String format = value.substring(2).replaceAll("\\$(\\d+)", "%$1\\$s");
				if(value.startsWith("I:")){

					this.implicits.add(new ImplicitRedirector(key, format));
					LOGGER.info("loadConfig", "Load a redirection rule; {0} to {1}", key, value);

				}else if(value.startsWith("E:")){

					this.explicits.add(new ExplicitRedirector(key, format));
					LOGGER.info("loadConfig", "Load a redirection rule; {0} to {1}", key, value);

				}else{

					LOGGER.warning("loadConfig",
							"Invalid value: {0} (This value must be started with I: or E:)", value);

				}

			}

		}

	}


	//============================================================================
	//  private inner classes
	//============================================================================
	private class ExplicitRedirector extends MessageHandlerAdapter{

		private final String to;

		public ExplicitRedirector(final String from, final String to) {
			super(from);

			this.to = to;

		}

		@Override
		public HttpResponse doRequest(final HttpRequest request, final MatchResult url) {
			LOGGER.entering(ExplicitRedirector.class, "doRequest", request, url);

			final HttpResponse res = request.createResponse(Status.Found);
			final HttpHeader header = res.getHeader();
			header.set(HeaderName.Connection, "close");
			header.set(HeaderName.Server, Redirector.class.getName());
			header.set(HeaderName.ContentLength, "0");
			header.set(HeaderName.Location, Format(this.to, url));

			LOGGER.exiting(ExplicitRedirector.class, "doRequest");
			return res;
		}

	}

	private class ImplicitRedirector extends RequestFilterAdapter{

		private final String to;

		public ImplicitRedirector(final String from, final String to) {
			super(from, "");

			this.to = to;

		}

		@Override
		public void update(final HttpRequest msg,
				final MatchResult url, final MatchResult cType, final FilterRegister register) {

			LOGGER.entering(ImplicitRedirector.class, "update", msg, url, cType, register);

			msg.setPath(Format(this.to, url));

			LOGGER.exiting(ImplicitRedirector.class, "update");
		}

	}

	//============================================================================
	//  helper methods
	//============================================================================
	private static String Format(final String format, final MatchResult r){

		final List<String> params = new ArrayList<String>();
		for(int i = 1; i <= r.groupCount(); ++i){

			params.add(r.group(i));

		}

		return String.format(format, params.toArray());

	}

}
