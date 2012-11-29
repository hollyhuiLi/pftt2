package com.mostc.pftt.scenario;

import java.util.Map;

import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.phpt.PhpBuild;
import com.mostc.pftt.telemetry.ConsoleManager;

/** Scenario to test PHP under.
 * 
 * Often a whole set of Scenarios (@see ScenarioSet) are used together.
 * 
 * May include custom INI configuration, extensions, environment variables, etc...
 * 
 * Can be used to setup remote services and configure PHP to use them for testing PHP core or extensions.
 *
 * @see ScenarioSet
 * 
 * Important Scenario Types
 * @see AbstractSAPIScenario - provides the SAPI that a PhpBuild is run under (Apache-ModPHP, CLI, etc...)
 * @see AbstractINIScenario - edits/adds to the INI used to run a PhptTestCase
 * @see AbstractFileSystemScenario - provides the filesystem a PhpBuild is run on (local, remote, etc...)
 * 
 * @author Matt Ficken
 *
 */

public abstract class Scenario {
	
	public Class<?> getSerialKey() {
		return getClass();
	}
	
	public boolean setup(ConsoleManager cm, Host host, PhpBuild build, ScenarioSet scenario_set) {
		return true;
	}
	public static enum EScenarioStartState {
		STARTED,
		FAILED_TO_START,
		SKIP
	}
	public EScenarioStartState start(ConsoleManager cm, Host host, PhpBuild build, ScenarioSet scenario_set) {
		return EScenarioStartState.STARTED;
	}
	public abstract String getName();
	public abstract boolean isImplemented();
	
	/** @see ScenarioSet#getENV
	 * 
	 * @param env
	 */
	public void getENV(Map<String, String> env) {
		
	}

	public boolean hasENV() {
		return false;
	}
	
	public boolean isSupported(ConsoleManager cm, Host host, PhpBuild build) {
		return true;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
	public static final CliScenario CLI_SCENARIO = new CliScenario();
	public static final LocalFileSystemScenario LOCALFILESYSTEM_SCENARIO = new LocalFileSystemScenario();
	public static final AbstractSAPIScenario DEFAULT_SAPI_SCENARIO = CLI_SCENARIO;
	public static final AbstractFileSystemScenario DEFAULT_FILESYSTEM_SCENARIO = LOCALFILESYSTEM_SCENARIO;
	
	// 90 ScenarioSets => (APC, WinCache, No) * (CLI, Buitlin-WWW, Apache, IIS-FastCGI, IIS-Express-FastCGI) * ( local filesystem, the 5 types of SMB )
	public static Scenario[] getAllDefaultScenarios() {
		return new Scenario[]{
				// sockets
				new PlainSocketScenario(),
				new SSLSocketScenario(),
				// code caches
				new NoCodeCacheScenario(),
				new APCScenario(),
				new WinCacheScenario(),
				// SAPIs
				CLI_SCENARIO,
				// filesystems
				LOCALFILESYSTEM_SCENARIO
			};
	} // end public static Scenario[] getAllDefaultScenarios
	
} // end public abstract class Scenario
