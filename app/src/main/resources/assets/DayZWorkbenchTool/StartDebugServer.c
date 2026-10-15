
[WorkbenchPluginAttribute("Start debug Server", "Just for testing", "alt+4", "", {"ScriptEditor"})]
class StartDebugServer : WorkbenchPlugin
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c \"%APPDATA%/.dayzworkbenchtool/tools/StartDebugServer.bat\"");
	}
}