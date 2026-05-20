
[WorkbenchPluginAttribute("Start debug Client/Server", "Just for testing", "alt+1", "", {"ScriptEditor"})]
class StartDebugAll : DayZTool
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c \"%APPDATA%/.dayzworkbenchtool/tools/StartDebugAll.bat\"");
	}
}