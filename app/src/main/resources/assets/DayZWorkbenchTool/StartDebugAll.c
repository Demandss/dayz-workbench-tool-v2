
[WorkbenchPluginAttribute("Start debug Client/Server", "Just for testing", "alt+1", "", {"ScriptEditor"})]
class StartDebugAll : WorkbenchPlugin
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c \"%APPDATA%/.dayzworkbenchtool/tools/StartDebugAll.bat\"");
	}
}