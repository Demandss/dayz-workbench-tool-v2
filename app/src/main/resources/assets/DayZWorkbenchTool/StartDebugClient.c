
[WorkbenchPluginAttribute("Start debug Client", "Just for testing", "alt+3", "", {"ScriptEditor"})]
class StartDebugClient : WorkbenchPlugin
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c \"%APPDATA%/.dayzworkbenchtool/tools/StartDebugClient.bat\"");
	}
}