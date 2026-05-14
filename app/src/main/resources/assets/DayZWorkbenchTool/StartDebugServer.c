
[WorkbenchPluginAttribute("Start debug Server", "Just for testing", "alt+4", "", {"ScriptEditor"})]
class StartDebugServer : DayZTool
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c P:/dayzworkbenchtool/StartDebugServer.bat");
	}
}
