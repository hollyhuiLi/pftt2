package com.mostc.pftt.runner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpVersion;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.params.SyncBasicHttpParams;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.apache.http.protocol.ImmutableHttpProcessor;
import org.apache.http.protocol.RequestConnControl;
import org.apache.http.protocol.RequestContent;
import org.apache.http.protocol.RequestExpectContinue;
import org.apache.http.protocol.RequestTargetHost;
import org.apache.http.protocol.RequestUserAgent;

import com.mostc.pftt.host.AHost;
import com.mostc.pftt.model.app.PhpUnitActiveTestPack;
import com.mostc.pftt.model.app.PhpUnitSourceTestPack;
import com.mostc.pftt.model.app.PhpUnitTestCase;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.model.core.PhpIni;
import com.mostc.pftt.model.sapi.ApacheManager;
import com.mostc.pftt.model.sapi.SharedSAPIInstanceTestCaseGroupKey;
import com.mostc.pftt.model.sapi.TestCaseGroupKey;
import com.mostc.pftt.model.smoke.RequiredExtensionsSmokeTest;
import com.mostc.pftt.results.ConsoleManager;
import com.mostc.pftt.results.ITestResultReceiver;
import com.mostc.pftt.results.ConsoleManager.EPrintType;
import com.mostc.pftt.scenario.AbstractFileSystemScenario.ITestPackStorageDir;
import com.mostc.pftt.scenario.AbstractSMBScenario.SMBStorageDir;
import com.mostc.pftt.scenario.AbstractINIScenario;
import com.mostc.pftt.scenario.ScenarioSet;

public class LocalPhpUnitTestPackRunner extends AbstractLocalTestPackRunner<PhpUnitActiveTestPack, PhpUnitSourceTestPack, PhpUnitTestCase> {
	final Map<String,String> globals = new HashMap<String,String>();
	final Map<String, String> env = new HashMap<String,String>();
	final Map<String, String> constants = new HashMap<String,String>();
	final HttpParams params;
	final HttpProcessor httpproc;
	final HttpRequestExecutor httpexecutor;
	final ApacheManager smgr;
	
	public LocalPhpUnitTestPackRunner(ConsoleManager cm, ITestResultReceiver twriter, ScenarioSet scenario_set, PhpBuild build, AHost storage_host, AHost runner_host) {
		super(cm, twriter, scenario_set, build, storage_host, runner_host);
		
		params = new SyncBasicHttpParams();
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setContentCharset(params, "UTF-8");
		HttpProtocolParams.setUserAgent(params, "Mozilla/5.0 (Windows NT 6.1; rv:12.0) Gecko/20120405 Firefox/14.0.1");
		HttpProtocolParams.setUseExpectContinue(params, true);
		
		httpproc = new ImmutableHttpProcessor(new HttpRequestInterceptor[] {
				// Required protocol interceptors
				new RequestContent(),
				new RequestTargetHost(),
				// Recommended protocol interceptors
				new RequestConnControl(),
				new RequestUserAgent(),
				new RequestExpectContinue()
			});
		
		httpexecutor = new HttpRequestExecutor();
		
		smgr = new ApacheManager();
	}
	
	@Override
	protected ITestPackStorageDir doSetupStorageAndTestPack(boolean test_cases_read, @Nullable List<PhpUnitTestCase> test_cases) throws Exception {
		if (test_cases_read) {
			
			// TODO cm.println(EPrintType.IN_PROGRESS, getClass(), "installed tests("+test_cases.size()+") from test-pack onto storage: local="+local_test_pack_dir+" remote="+remote_test_pack_dir);
			
			return null;
		}
		return super.doSetupStorageAndTestPack(test_cases_read, test_cases);
	}

	protected String temp_base_dir;
	@Override
	protected void setupStorageAndTestPack(ITestPackStorageDir storage_dir, List<PhpUnitTestCase> test_cases) throws Exception {
		if (!(storage_dir instanceof SMBStorageDir)) {
			temp_base_dir = runner_host.getPhpSdkDir()+"/temp/";
			
			active_test_pack = src_test_pack.installInPlace(cm, runner_host);
			
			return;
		}
		
		// generate name of directory on that storage to store the copy of the test-pack
		String local_test_pack_dir = null, remote_test_pack_dir = null;
		{
			String local_path = storage_dir.getLocalPath(storage_host);
			String remote_path = storage_dir.getRemotePath(storage_host);
			long millis = System.currentTimeMillis();
			for ( int i=0 ; i < 131070 ; i++ ) {
				// try to include version, branch info etc... from name of test-pack
				local_test_pack_dir = local_path + "/PFTT-" + src_test_pack.getName() + (i==0?"":"-" + millis) + "/";
				remote_test_pack_dir = remote_path + "/PFTT-" + src_test_pack.getName() + (i==0?"":"-" + millis) + "/";
				if (!storage_host.exists(remote_test_pack_dir) || !runner_host.exists(local_test_pack_dir))
					break;
				millis++;
				if (i%100==0)
					millis = System.currentTimeMillis();
			}
		}
		//
		
		
		cm.println(EPrintType.IN_PROGRESS, getClass(), "installing... test-pack onto storage: remote="+remote_test_pack_dir+" local="+local_test_pack_dir);
		
		try {
			active_test_pack = src_test_pack.install(cm, storage_host, local_test_pack_dir, remote_test_pack_dir);
		} catch ( Exception ex ) {
			cm.addGlobalException(EPrintType.CANT_CONTINUE, "setupStorageAndTestPack", ex, "can't install test-pack");
			close();
			return;
		}
		
		// notify storage
		if (!storage_dir.notifyTestPackInstalled(cm, runner_host)) {
			cm.println(EPrintType.CANT_CONTINUE, getClass(), "unable to prepare storage for test-pack, giving up!(2)");
			close();
			return;
		}
		
		temp_base_dir = local_test_pack_dir + "/temp/";
	} // end protected void setupStorageAndTestPack
	
	@Override
	protected TestCaseGroupKey createGroupKey(PhpUnitTestCase test_case, TestCaseGroupKey group_key) throws Exception {
		if (group_key!=null)
			return group_key;
		// CRITICAL: provide the INI to run all PhpUnitTestCases
		//           unlike PhptTestCases all PhpUnitTestCases share the same INI and environment variables
		PhpIni ini = RequiredExtensionsSmokeTest.createDefaultIniCopy(runner_host, build);
		AbstractINIScenario.setupScenarios(cm, runner_host, scenario_set, build, ini);
		src_test_pack.prepareINI(cm, runner_host, scenario_set, build, ini);
		return new SharedSAPIInstanceTestCaseGroupKey(ini, null);
	}

	@Override
	protected boolean handleNTS(TestCaseGroupKey group_key, PhpUnitTestCase test_case) {
		final String[][] names = src_test_pack.getNonThreadSafeTestFileNames();
		if (names==null)
			return false;
		for ( String[] ext_names : names ) {
			if (test_case.fileNameStartsWithAny(ext_names)) {
				addNTSTestCase(ext_names, group_key, test_case);
				
				return true;
			}
		}
		return false;
	}

	@Override
	protected PhpUnitThread createTestPackThread(boolean parallel) throws IllegalStateException, IOException {
		return new PhpUnitThread(parallel);
	}
	
	public class PhpUnitThread extends TestPackThread<PhpUnitTestCase> {
		final String my_temp_dir;

		protected PhpUnitThread(boolean parallel) throws IllegalStateException, IOException {
			super(parallel);
			my_temp_dir = runner_host.fixPath(runner_host.mktempname(temp_base_dir, getClass()) + "/");
			runner_host.mkdirs(my_temp_dir);
		}
		
		@Override
		public void run() {
			super.run();
			
			// be sure to cleanup
			runner_host.deleteIfExists(my_temp_dir);
		}

		@Override
		protected void runTest(TestCaseGroupKey group_key, PhpUnitTestCase test_case) throws IOException, Exception, Throwable {
			AbstractPhpUnitTestCaseRunner r = sapi_scenario.createPhpUnitTestCaseRunner(
					this,
					group_key,
					cm,
					twriter,
					globals,
					env,
					runner_host,
					scenario_set,
					build,
					test_case,
					my_temp_dir,
					constants,
					test_case.getPhpUnitDist().getIncludePath(),
					test_case.getPhpUnitDist().getIncludeFiles(),
					group_key.getPhpIni()
				);
			r.runTest();
		}
		
	} // end public class PhpUnitThread

} // end public class LocalPhpUnitTestPackRunner
