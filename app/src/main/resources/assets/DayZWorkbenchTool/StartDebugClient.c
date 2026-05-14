
[WorkbenchPluginAttribute("Start debug Client", "Just for testing", "alt+3", "", {"ScriptEditor"})]
class StartDebugClient : DayZTool
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c P:/dayzworkbenchtool/StartDebugClient.bat");
	}
}
