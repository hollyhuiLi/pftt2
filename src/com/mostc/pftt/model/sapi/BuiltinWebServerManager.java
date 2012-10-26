package com.mostc.pftt.model.sapi;

import java.io.IOException;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import javax.annotation.concurrent.ThreadSafe;

import com.mostc.pftt.host.Host;
import com.mostc.pftt.host.LocalHost;
import com.mostc.pftt.model.phpt.PhpBuild;
import com.mostc.pftt.model.phpt.PhpIni;
import com.mostc.pftt.util.ErrorUtil;

/** manages local instances of PHP's builtin web server
 * 
 * can have multiple concurrent BuiltinWebServerInstances, assigning each one a different
 * TCP port number.
 * 
 * @author Matt Ficken
 *
 */

@ThreadSafe
public class BuiltinWebServerManager extends WebServerManager {
	protected static final int PORT_RANGE_START = 40000;
	// over 49151 may be used by client side of tcp sockets or other dynamic uses
	protected static final int PORT_RANGE_STOP = 49151;
	//
	protected int last_port = PORT_RANGE_START-1;
	//
	protected final Timer timer;
	
	public BuiltinWebServerManager() {
		timer = new Timer();
	}
	
	@Override
	protected synchronized WebServerInstance createWebServerInstance(Host host, PhpBuild build, PhpIni ini, String docroot) {
		String sapi_output = "";
		int port_attempts;
		boolean found_port;
		for (int total_attempts = 0 ; total_attempts < 3 ; total_attempts++) {
			
			// find port number not currently in use
			port_attempts = 0;
			found_port = false;
			while (port_attempts < 3) {
				last_port++;
				if (!isLocalhostTCPPortUsed(last_port)) {
					found_port = true;
					break;
				} else if (last_port > PORT_RANGE_STOP) {
					// start over and hope some ports in range are free
					last_port = PORT_RANGE_START;
					port_attempts++;
				}
			}
			
			if (!found_port) {
				// try again
				sapi_output += "PFTT: Couldn't find unused local port\n";
				continue;
			}
			
			// Windows BN: php -S won't accept connections if it listens on localhost|127.0.0.1 on Windows
			//             php won't complain about this though, it will run, but be inaccessible
			String hostname = host.getLocalhostServableAddress();
			
			Host.ExecHandle handle = null;
			try {
				// run `php.exe -S localhost:NNNN` in docroot
				String cmd = build.getPhpExe()+" -S "+hostname+":"+last_port+" "+(ini==null?"":ini.toCliArgString(host));
				
				handle = host.execThread(cmd, docroot);
				
				final Host.ExecHandle handlef = handle;
				new Exception().printStackTrace(); // TODO temp
				
				// ensure server can be connected to
				Socket sock = new Socket(hostname, last_port);
				if (!sock.isConnected())
					// kill server and try again
					throw new IOException("socket not connected");
				sock.close();
				//
			
				// provide a BuiltinWebServerInstance to handle the running web server instance
				final BuiltinWebServerInstance web = new BuiltinWebServerInstance(LocalHost.splitCmdString(cmd), ini, handle, hostname, last_port);
				
				// check web server periodically to see if it has crashed
				timer.scheduleAtFixedRate(new TimerTask() {
						@Override
						public void run() {
							if (!handlef.isRunning()) {
								try {
									if (handlef.isCrashed())
										// notify of web server crash
										//
										// provide output and exit code
										web.notifyCrash(handlef.getOutput(), handlef.getExitCode());
								} finally {
									// don't need to check any more
									cancel();
								}
							}
						} // end public void run
					}, 0, 500);
				//
				
				return web;
			} catch ( Exception ex ) {
				if (handle!=null)
					// make sure process is killed in this case
					handle.close();
				
				sapi_output += ErrorUtil.toString(ex) + "\n";
			}
		} // end for
		
		// fallback
		sapi_output += "PFTT: wasn't able to start web server instance (after many attempts)... giving up.\n";
		
		// return this failure message to client code
		return new CrashedWebServerInstance(ini, sapi_output);
	} // end protected synchronized WebServerInstance createWebServerInstance

	@Override
	public boolean allowConcurrentWebServerSAPIInstances() {
		return true;
	}

	@Override
	public boolean isSSLSupported() {
		// XXX can this web server support SSL?
		return false;
	}

} // end public class BuiltinWebServerManager
