
[WorkbenchPluginAttribute("Stop debug Client/Server", "Just for testing", "alt+2", "", {"ScriptEditor"})]
class StopDebugAll : DayZTool
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c \"%APPDATA%/.dayzworkbenchtool/tools/StopDebugAll.bat\"");
	}
}