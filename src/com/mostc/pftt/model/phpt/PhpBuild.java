package com.mostc.pftt.model.phpt;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.mostc.pftt.host.ExecOutput;
import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.Build;
import com.mostc.pftt.telemetry.ConsoleManager;
import com.mostc.pftt.util.StringUtil;

/** represents a single build of PHP.
 * 
 * To be clear, NTS and TS builds that were built together, even from the same configuration and source, are still considered two separate builds.
 * 
 * @author Matt Ficken
 * 
 */

public class PhpBuild extends Build {
	private String build_path, php_exe, php_cgi_exe;
	private WeakHashMap<PhpIni,WeakHashMap<String,Boolean>> ext_enable_map;
	private WeakReference<String> php_info;
	private WeakReference<PhpIni> php_ini;
	private String version_str;
	private EBuildBranch branch;
	private WeakReference<String[]> module_list;
	private int major, minor, release; 
	
	public PhpBuild(String build_path) {
		this.build_path = build_path;
		ext_enable_map = new WeakHashMap<PhpIni,WeakHashMap<String,Boolean>>();
	}
	
	@Override
	public String toString() {
		return build_path;
	}
	
	public boolean open(ConsoleManager cm, Host host) {
		try {
			php_exe = host.isWindows() ? build_path + "\\php.exe" : build_path + "/sapi/cli/php";
			php_cgi_exe = host.isWindows() ? build_path + "\\php-cgi.exe" : build_path + "/sapi/cgi/php-cgi";
			if (!host.exists(php_cgi_exe))
				php_cgi_exe = null; // mark as not found
			return true;
		} catch ( Exception ex ) {
			cm.printStackTrace(ex);
		}
		return false;
	} // end public boolean open
	
	/** returns who built this build of php
	 * 
	 * @param host
	 * @return
	 */
	public EBuildSourceType getBuildSourceType(Host host) {
		// '-dev-' means that its a build the user made themselves
		if (build_path.toLowerCase().contains("-dev-"))
			return EBuildSourceType.SELF;
		else
			return EBuildSourceType.WINDOWS_DOT_PHP_DOT_NET;
	}
	
	/** returns if this is an NTS or TS build of PHP
	 * 
	 * @param host
	 * @return
	 */
	public EBuildType getBuildType(Host host) {
		if (host.isWindows()) {
			// '-nts-' means its an NTS build
			if (build_path.toLowerCase().contains("-nts-")) 
				return EBuildType.NTS;
			else
				// has '-ts-' on TS snapshot builds, but doesn't have '-ts-' on release builds
				return EBuildType.TS;
		} else {
			// default
			return EBuildType.NTS;
		}
	}
	
	/** returns the path to this build's php executable
	 * 
	 * @return
	 */
	public String getPhpExe() {
		return php_exe;
	}
	
	/** returns the path to this build's php-cgi executable
	 * 
	 * @return
	 */
	@Nullable
	public String getPhpCgiExe() {
		return php_cgi_exe;
	}

	/** checks if this build includes php-cgi
	 * 
	 * @return
	 */
	public boolean hasPhpCgiExe() {
		return StringUtil.isNotEmpty(php_cgi_exe);
	}
	
	/** gets the default PhpIni used for this build (unless overriden)
	 * 
	 * @param host
	 * @param type
	 * @return
	 * @throws IOException
	 */
	public PhpIni getDefaultPhpIni(Host host, ESAPIType type) throws IOException {
		PhpIni ini;
		if (this.php_ini!=null) {
			ini = this.php_ini.get();
			if (ini!=null)
				return ini;
		}
		String path = getDefaultPhpIniPath(host, type);
		if (host.exists(path))
			ini = new PhpIni(host.getContents(path), build_path);
		else
			ini = PhpIni.createDefaultIniCopy(host);
		
		this.php_ini = new WeakReference<PhpIni>(ini);
		return ini;
	}
	
	/** calculates path on Host to php.ini used for this build and SAPIs(cgi, cli).
	 * 
	 * On Windows, php.ini is stored in same place for all SAPIs.
	 * 
	 * On others, different SAPIs have different php.inis.
	 * 
	 * @param host
	 * @param type
	 * @return
	 */
	public String getDefaultPhpIniPath(Host host, ESAPIType type) {
		if (host.isWindows()) {
			return build_path+host.dirSeparator()+"php.ini";
		} else {
			switch(type) {
			case CGI:
				return "/etc/cgi/php.ini";
			case MOD_PHP:
				return "/etc/apache/php.ini";
			case CLI:
			default:
				return "/etc/cli/php.ini";
			}
		}
	}
	
	/** gets the version string for this build
	 * 
	 * @param cm
	 * @param host
	 * @return
	 * @throws Exception
	 */
	public String getVersionString(ConsoleManager cm, Host host) throws Exception {
		if (version_str!=null) {
			return version_str;
		}
		for (String line : StringUtil.splitLines(getPhpInfo(cm, host))) {
			if (line.startsWith("PHP Version =>")) {
				version_str = line.substring("PHP Version => ".length());
				
				String[] split = version_str.split("[\\.|\\-]");
				major = Integer.parseInt(split[0]);
				minor = Integer.parseInt(split[1]);
				release = Integer.parseInt(split[2]);
				branch = EBuildBranch.guessValueOf(split[3]);
				
				return version_str;
			}
		}
		return null; // shouldn't happen
	}
	
	public int getVersionMajor(ConsoleManager cm, Host host) throws Exception {
		getVersionString(cm, host);
		return major;
	}
	
	public int getVersionMinor(ConsoleManager cm, Host host) throws Exception {
		getVersionString(cm, host);
		return minor;
	}
	
	public int getVersionRelease(ConsoleManager cm, Host host) throws Exception {
		getVersionString(cm, host);
		return release;
	}
	
	public EBuildBranch getVersionBranch(ConsoleManager cm, Host host) throws Exception {
		getVersionString(cm, host);
		return branch;
	}
	
	/** checks to see if the extension is enabled or statically builtin to this build
	 *  
	 *  Note that PhpIni#hasExtension only checks to see if the extension is enabled in a PhpIni whereas
	 *  #isExtensionEnabled checks to see if its enabled. PhpIni#hasExtension will miss builtin/static extensions.
	 *   
	 * @param cm
	 * @param host
	 * @param type
	 * @param ext_name
	 * @return
	 * @throws Exception
	 */
	public boolean isExtensionEnabled(ConsoleManager cm, Host host, ESAPIType type, String ext_name) throws Exception {
		if (ext_name.equals("spl")||ext_name.equals("standard")||ext_name.equals("core"))
			// these extensions are always there/always builtin
			return true;
		
		ext_name = ext_name.toLowerCase();
		
		PhpIni ini = getDefaultPhpIni(host, type);
		
		// key by only extensions, so cache will be reused even if additional directives are added
		// TODO if (ini!=null)
			// TODO ini = ini.getExtensionsOnly();
		
		WeakHashMap<String,Boolean> map = ext_enable_map.get(ini);
		if (map==null) {
			map = new WeakHashMap<String,Boolean>();
			ext_enable_map.put(ini, map);
		} else if (map.containsKey(ext_name)) { 
			return map.get(ext_name);
		}
				
		if (ini!=null) {
			String[] extensions = ini.getExtensions();
			if (extensions!=null) {
				for (String module:extensions) {
					if (module.toLowerCase().contains(ext_name)) {
						map.put(ext_name, true);
						return true;
					}
				}
			}
		}
		
		for (String module:getExtensionList(cm, host, ini)) {
			if (module.toLowerCase().contains(ext_name)) {
				map.put(ext_name, true);
				return true;
			}
		}
		
		map.put(ext_name, false);
		return false;
	}
	
	/** gets the PhpInfo string for this build
	 * 
	 * @param cm
	 * @param host
	 * @return
	 * @throws Exception
	 */
	public String getPhpInfo(ConsoleManager cm, Host host) throws Exception {
		String php_info;
		if (this.php_info != null) {
			php_info = this.php_info.get();
			if (php_info != null)
				return php_info;
		}
		
		ExecOutput eo = eval(host, "phpinfo();");
		eo.printOutputIfCrash(cm);
		php_info = eo.output;
		this.php_info = new WeakReference<String>(php_info);
		return php_info;
	}
	
	/** replaces the default php ini this build will use, unless overridden
	 * 
	 * @param host
	 * @param type
	 * @param ini
	 * @throws IOException
	 */
	public void setDefaultPhpIni(Host host, ESAPIType type, PhpIni ini) throws IOException {
		this.php_ini = new WeakReference<PhpIni>(ini);
		
		host.saveTextFile(getDefaultPhpIniPath(host, type), ini.toString());
		
		if (!host.isWindows()) {
			host.saveTextFile("/etc/php/cli/php.ini", ini.toString());
			host.saveTextFile("/etc/php/cgi/php.ini", ini.toString());
		}
	}
		
	/** executes/evaluates the given php code with this php build and returns the output result
	 * 	
	 * @param host
	 * @param code
	 * @return
	 * @throws Exception
	 */
	public PHPOutput eval(Host host, String code) throws Exception {
		return eval(host, code, true);
	}
	
	/** executes/evaluates the given php code with this php build and returns the output result
	 * 
	 * @param host
	 * @param code
	 * @param auto_cleanup - default=true, deletes the temporary file before returning
	 * @return
	 * @throws Exception
	 * @see PHPOutput#cleanup
	 */
	public PHPOutput eval(Host host, String code, boolean auto_cleanup) throws Exception {
		return eval(host, code, Host.NO_TIMEOUT, auto_cleanup);
	}
		
	public PHPOutput eval(Host host, String code, int timeout_seconds, boolean auto_cleanup) throws Exception {
		code = StringUtil.ensurePhpTags(code);
				
		String php_filename = host.mktempname("Build", ".php");
		
		host.saveTextFile(php_filename, code);
		
		PHPOutput output = new PHPOutput(php_filename, host.exec(php_exe+" "+php_filename, timeout_seconds, new HashMap<String,String>(), null, Host.dirname(php_filename)));
		if (auto_cleanup && !output.hasFatalError())
			// if fatal error, don't clean up so user can check it
			output.cleanup(host);
		return output;
	}
	
	public static class PHPOutput extends ExecOutput {
		/** the filename that the code was stored in for execution
		 * 
		 */
		public String php_filename;
		
		protected PHPOutput(String php_filename, ExecOutput output) {
			this.charset = output.charset;
			this.exit_code = output.exit_code;
			this.output = output.output;
			this.php_filename = php_filename;
		}
		
		public boolean hasFatalError() {
			return output.contains("Fatal") || output.contains("fatal");
		}
		
		public PHPOutput printHasFatalError(ConsoleManager cm) {
			if (cm==null||cm.isResultsOnly())
				return this;
			return printHasFatalError(System.err);
		}
		public PHPOutput printHasFatalError(PrintStream ps) {
			if (hasFatalError())
				ps.println(output);
			return printOutputIfCrash(ps);
		}
		
		public void cleanup(Host host) {
			try {
				host.delete(php_filename);
			} catch ( Exception ex ) {}
		}
		@Override
		public PHPOutput printOutputIfCrash(ConsoleManager cm) {
			return (PHPOutput) super.printOutputIfCrash(cm);
		}
		public PHPOutput printOutputIfCrash(PrintStream ps) {
			return (PHPOutput) super.printOutputIfCrash(ps);
		}
	} // end public static class PHPOutput
	
	public String[] getExtensionList(ConsoleManager cm, Host host, ESAPIType type) throws Exception {
		return getExtensionList(cm, host, getDefaultPhpIni(host, type));
	}
	
	/** gets the static builtin extensions for this build build and the dynamic extensions the
	 * PhpIni loads (minus any extensions that can't actually be loaded)
	 * 
	 * @param cm
	 * @param host
	 * @param ini
	 * @return
	 * @throws Exception
	 */
	public String[] getExtensionList(ConsoleManager cm, Host host, PhpIni ini) throws Exception {
		String[] module_list;
		if (this.module_list!=null) {
			module_list = this.module_list.get();
			if (module_list!=null)
				return module_list;
		}
		
		String ini_settings = ini==null?null:ini.toCliArgString(host);
		
		ExecOutput output = host.exec(php_exe+(ini_settings==null?"":" "+ini_settings)+" -m", Host.NO_TIMEOUT);
		output.printOutputIfCrash(cm);
		
		ArrayList<String> list = new ArrayList<String>();
		
		for (String module : output.getLines()) {
			if (!module.startsWith("[") && module.length() > 0)
				list.add(module.toLowerCase());
		}
		
		module_list = (String[]) list.toArray(new String[list.size()]);
		this.module_list = new WeakReference<String[]>(module_list);
		return module_list;
	}

	/** returns the default extension directory that will be used for this build if not overriden by a PhpIni directive
	 * 
	 * @return
	 */
	public String getDefaultExtensionDir() {
		return build_path+"/ext";
	}
	
} // end public class PhpBuild
