package com.mostc.pftt.runner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.mostc.pftt.host.ExecOutput;
import com.mostc.pftt.host.Host;
import com.mostc.pftt.host.LocalHost;
import com.mostc.pftt.model.phpt.EPhptSection;
import com.mostc.pftt.model.phpt.EPhptTestStatus;
import com.mostc.pftt.model.phpt.PhpBuild;
import com.mostc.pftt.model.phpt.PhpIni;
import com.mostc.pftt.model.phpt.PhptTestCase;
import com.mostc.pftt.model.phpt.PhptSourceTestPack;
import com.mostc.pftt.model.phpt.PhptActiveTestPack;
import com.mostc.pftt.runner.PhptTestPackRunner.PhptThread;
import com.mostc.pftt.scenario.ScenarioSet;
import com.mostc.pftt.telemetry.PhptTelemetryWriter;
import com.mostc.pftt.telemetry.PhptTestResult;
import com.mostc.pftt.util.StringUtil;

/** one of the core classes. runs a PhptTestCase.
 * 
 * Handles all the work and behaviors to prepare, execute and evaluate a single PhptTestCase.
 * 
 * @author Matt Ficken
 * 
 */

// TODO save .php and/or .out in telemetry, make .cmd script actually usable by itself
public class CliPhptTestCaseRunner extends AbstractPhptTestCaseRunner2 {
	protected ExecOutput output;
	protected String selected_php_exe, shell_script, test_cmd, skip_cmd, ini_settings, shell_file;
	
	public CliPhptTestCaseRunner(PhpIni ini, PhptThread thread, PhptTestCase test_case, PhptTelemetryWriter twriter, Host host, ScenarioSet scenario_set, PhpBuild build, PhptSourceTestPack src_test_pack, PhptActiveTestPack active_test_pack) {
		super(ini, thread, test_case, twriter, host, scenario_set, build, src_test_pack, active_test_pack);
	}
	
	@Override
	protected boolean prepare() throws IOException, Exception {
		if (super.prepare()) {
			//
			ini_settings = ini.toCliArgString(host);
			
			selected_php_exe = build.getPhpExe();
			
			/* For GET/POST tests, check if cgi sapi is available and if it is, use it. */
			if (test_case.containsAnySection(EPhptSection.REQUEST, EPhptSection.GET, EPhptSection.POST, EPhptSection.PUT, EPhptSection.POST_RAW, EPhptSection.COOKIE, EPhptSection.EXPECTHEADERS)) {
				if (build.hasPhpCgiExe()) {
					selected_php_exe = build.getPhpCgiExe() + " -C ";
				} else {
					twriter.addResult(new PhptTestResult(host, EPhptTestStatus.XSKIP, test_case, "CGI not available", null, null, null, null, null, null, null, null, null, null));
					
					return false;
				}
			}
			
			env = generateENVForTestCase(twriter.getConsoleManager(), host, build, scenario_set, test_case);
			
			return true;
		}
		return false;
	}
	
	@Override
	protected void prepareTest() throws Exception {
		super.prepareTest();
		
		// copy CLI args to pass
		String args = test_case.containsSection(EPhptSection.ARGS) ? " -- " + test_case.get(EPhptSection.ARGS) : "";
		
		// generate cmd string to run test_file with php.exe
		//
		// -n => critical: ignores any .ini file with the php build
		test_cmd = selected_php_exe+" -n "+ini_settings+" -f \""+test_file+"\""+(args==null?"":" "+args);
		
		if (test_case.containsSection(EPhptSection.STDIN)) {
			if (host.isWindows()) {
				// @see Zend/tests/multibyte*
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write(test_case.get(EPhptSection.STDIN).getBytes());
				stdin_post = baos.toByteArray();
			} else {
				stdin_post = test_case.get(EPhptSection.STDIN).getBytes();
			}
		}
		
		String query_string;
		if (test_case.containsSection(EPhptSection.GET)) {
			query_string = test_case.getTrim(EPhptSection.GET);
		} else {
			query_string = "";
		}
	
		// critical to avoid security warning: see http://php.net/security.cgi-bin
		env.put(ENV_REDIRECT_STATUS, "1");
		if (!env.containsKey(ENV_QUERY_STRING))
			env.put(ENV_QUERY_STRING, query_string);
		if (!env.containsKey(ENV_PATH_TRANSLATED))
			env.put(ENV_PATH_TRANSLATED, test_file);
		// critical: this is actually how php-cgi gets the script filename (not with -f switch. not sure why run-test uses -f too)
		if (!env.containsKey(ENV_SCRIPT_FILENAME))
			env.put(ENV_SCRIPT_FILENAME, test_file);
	
		if (test_case.containsSection(EPhptSection.COOKIE)) {
			env.put(ENV_HTTP_COOKIE, test_case.getTrim(EPhptSection.COOKIE));
		} else if (!env.containsKey(ENV_HTTP_COOKIE)) {
			env.put(ENV_HTTP_COOKIE, "");
		}
		
		// 0 => for memory debugging
		env.put(ENV_USE_ZEND_ALLOC, "1");
		
		prepareSTDIN();
		createShellScript();
	}
	
	@Override
	protected boolean hasContentType() {
		return StringUtil.isEmpty(env.get(ENV_CONTENT_TYPE));
	}
	
	@Override
	protected void setContentEncoding(String encoding) {
		env.put(ENV_HTTP_CONTENT_ENCODING, encoding);
	}
	
	@Override
	protected void setContentType(String content_type) {
		super.setContentType(content_type);
		env.put(ENV_CONTENT_TYPE, content_type);
	}
	
	@Override
	protected void setContentLength(int content_length) {
		env.put(ENV_CONTENT_LENGTH, Integer.toString(content_length));
	}
	
	@Override
	protected void setRequestMethod(String request_method) {
		env.put(ENV_REQUEST_METHOD, request_method);
	}
	
	@Override
	protected String executeSkipIf() throws Exception {
		// Check if test should be skipped.
		if (skipif_file != null) {
			env.put(ENV_USE_ZEND_ALLOC, "1");
				
			// -n => critical: ignores any .ini file with the php build
			skip_cmd = selected_php_exe+" -n "+ini_settings+" -f \""+skipif_file+"\"";

			if (!env.containsKey(ENV_PATH_TRANSLATED))
				env.put(ENV_PATH_TRANSLATED, skipif_file);
			if (!env.containsKey(ENV_SCRIPT_FILENAME))
				env.put(ENV_SCRIPT_FILENAME, skipif_file);
			
			// execute SKIPIF (60 second timeout)
			output = host.exec(skip_cmd, Host.ONE_MINUTE, env, null, active_test_pack.getDirectory());
			
			return output.output;
		}
		return null;
	} // end String executeSkipIf

	@Override
	protected String executeTest() throws Exception { 
		// execute PHP to execute the TEST code ... allow up to 60 seconds for execution
		//      if test is taking longer than 40 seconds to run, spin up an additional thread to compensate (so other non-slow tests can be executed)
		output = host.exec(shell_file, Host.ONE_MINUTE, null, stdin_post, test_case.isNon8BitCharset()?test_case.getCommonCharset():null, active_test_pack.getDirectory(), thread, 40);
		
		return output.output;
	} // end String executeTest
	
	@Override
	protected void executeClean() throws Exception {
		if (test_case.containsSection(EPhptSection.CLEAN)) {
			host.saveTextFile(test_clean, test_case.getTrim(EPhptSection.CLEAN), null);
		
			env.remove(ENV_REQUEST_METHOD);
			env.remove(ENV_QUERY_STRING);
			env.remove(ENV_PATH_TRANSLATED);
			env.remove(ENV_SCRIPT_FILENAME);
			env.remove(ENV_REQUEST_METHOD);
			
			// execute cleanup script
			// FUTURE should cleanup script be ignored??
			host.exec(selected_php_exe+" "+test_clean, Host.ONE_MINUTE, env, null, active_test_pack.getDirectory());

			host.delete(test_clean);
		}
	} // end void executeClean

	@Override
	protected String getCrashedSAPIOutput() {
		if (output.isCrashed()) 
			return output.isEmpty() ? 
				"PFTT: test printed nothing. was expected to print something. exited with non-zero code (probably crash): "+output.exit_code 
				: output.output;
		else
			return null; // no crash at all
	}

	protected void createShellScript() throws IOException, Exception {
		// useful: rm -rf `find ./ -name "*.sh"`
		//
		// create a .cmd (Windows batch script) or .sh (shell script) that will actually execute PHP
		// this enables PHP to be executed like what PFTT does, but using a shell|batch script
		shell_file = test_file + (host.isWindows() ? ".cmd" : ".sh" );
		if (shell_file.startsWith(active_test_pack.getDirectory())) {
			shell_file = shell_file.substring(active_test_pack.getDirectory().length());
			if (shell_file.startsWith("/")||shell_file.startsWith("\\"))
				shell_file = shell_file.substring(1);
			shell_file = twriter.getTelemetryDir()+"/"+shell_file;
		}
		StringWriter sw = new StringWriter();
		PrintWriter fw = new PrintWriter(sw);
		if (host.isWindows()) {
			fw.println("@echo off"); // critical: or output will include these commands and fail test
		} else {
			fw.println("#!/bin/sh");
		}
		fw.println("cd "+host.fixPath(test_dir));
		for ( String name : env.keySet()) {
			String value = env.get(name);
			if (value==null)
				value = "";
			else
				value = StringUtil.replaceAll(PAT_bs, "\\\\\"", StringUtil.replaceAll(PAT_dollar, "\\\\\\$", value));
			
			if (host.isWindows()) {
				if (value.contains(" "))
					// important: on Windows, don't use " " for script variables unless you have to
					//            the " " get passed into PHP so variables won't be read correctly
					fw.println("set "+name+"=\""+value+"\"");
				else
					fw.println("set "+name+"="+value+"");
			} else
				fw.println("export "+name+"=\""+value+"\"");
		}
		fw.println(test_cmd);
		fw.close();
		shell_script = sw.toString();
		FileWriter w = new FileWriter(shell_file);
		fw = new PrintWriter(w);
		fw.print(shell_script);
		fw.flush();
		fw.close();
		w.close();		
		
		if (!host.isWindows()) {
			// make shell script executable on linux
			host.exec("chmod +x \""+shell_file+"\"", Host.NO_TIMEOUT, null, null, active_test_pack.getDirectory());
		}
	} // end protected void createShellScript

	protected void prepareSTDIN() throws IOException {
		String stdin_file = test_file + ".stdin";
		if (stdin_file.startsWith(active_test_pack.getDirectory())) {
			stdin_file = stdin_file.substring(active_test_pack.getDirectory().length());
			if (stdin_file.startsWith("/")||stdin_file.startsWith("\\"))
				stdin_file = stdin_file.substring(1);
			stdin_file = twriter.getTelemetryDir()+"/"+stdin_file;
		}
		new File(stdin_file).getParentFile().mkdirs();
		if (stdin_post!=null) {
			FileOutputStream fw = new FileOutputStream(stdin_file);
			fw.write(stdin_post);
			fw.close();
		}
	} // end protected void prepareSTDIN

	@Override
	protected String[] splitCmdString() {
		return LocalHost.splitCmdString(test_cmd);
	}
	
	@Override
	protected String getShellScript() {
		return shell_script;
	}
	
} // end public class CliTestCaseRunner
