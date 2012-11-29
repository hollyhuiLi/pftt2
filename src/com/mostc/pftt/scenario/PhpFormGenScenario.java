package com.mostc.pftt.scenario;

import com.mostc.pftt.host.Host;
import com.mostc.pftt.model.phpt.PhpBuild;
import com.mostc.pftt.telemetry.ConsoleManager;

/** phpFormGenerator is a an easy, online tool for creating reliable, efficient, and aesthetically
 * pleasing web forms in a snap. No programming of any sort is required: phpFormGenerator generates
 * the HTML code, the form processor code (PHP), and the field validation code automatically via an
 * easy, point-and-click interface. 
 * 
 * @see http://phpformgen.sourceforge.net/
 * 
 */

public class PhpFormGenScenario extends ApplicationScenario {

	@Override
	public boolean setup(ConsoleManager cm, Host host, PhpBuild build, ScenarioSet scenario_set) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getName() {
		return "PhpFormGen";
	}

	@Override
	public boolean isImplemented() {
		return false;
	}

}
