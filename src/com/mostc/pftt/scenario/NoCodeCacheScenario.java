package com.mostc.pftt.scenario;

import com.github.mattficken.io.StringUtil;
import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.core.EAcceleratorType;
import com.mostc.pftt.model.core.PhpBuild;
import com.mostc.pftt.model.core.PhpIni;
import com.mostc.pftt.results.ConsoleManager;
import com.mostc.pftt.results.ConsoleManager.EPrintType;

/** Placeholder scenario for no code caching being used (neither APC or WinCache are used)
 * 
 *  @author Matt Ficken
 *
 */

public class NoCodeCacheScenario extends AbstractCodeCacheScenario {

	@Override
	public boolean isPlaceholder() {
		return true;
	}
	
	@Override
	public String getName() {
		return "No-Code-Cache";
	}
	
	@Override
	public boolean isImplemented() {
		return true;
	}
	
	@Override
	public boolean setup(ConsoleManager cm, Host host, PhpBuild build, PhpIni ini) {
		// assume SO is in same directory as PHP extensions
		try {
			// seems that PHP will load O+ if dll is there even though its not in INI
			//
			// ensure its not loaded by renaming .dll|.so file so it can't be loaded
			String ext_dir = ini.getExtensionDir();
			if (StringUtil.isEmpty(ext_dir))
				ext_dir = build.getDefaultExtensionDir();
			if (host.isWindows()) {
				String path = ext_dir + "/php_ZendOptimizerPlus.dll";
				
				if (host.exists(path))
					host.move(path, path.replace(".dll", ".dont_load"));
			} else {
				String path = ext_dir + "/php_ZendOptimizerPlus.so";
				
				if (host.exists(path))
					host.move(path, path.replace(".so", ".dont_load"));
			}
			
			return true;
		} catch ( Exception ex ) {
			cm.addGlobalException(EPrintType.CLUE, "setup", ex, "couldn't make sure OptimizerPlus was disabled");
		}
		return false;
	} // end public boolean setup

	@Override
	public boolean setup(ConsoleManager cm, Host host, PhpBuild build, ScenarioSet scenario_set) {
		return true;
	}

	@Override
	public EAcceleratorType getAcceleratorType() {
		return EAcceleratorType.NONE;
	}

} // end public class NoCodeCacheScenario
