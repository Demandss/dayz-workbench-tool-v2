
[WorkbenchPluginAttribute("Start debug Client", "Just for testing", "alt+3", "", {"ScriptEditor"})]
class StartDebugClient : DayZTool
{
	override void Run()
	{
		Workbench.RunCmd("cmd /c &WORKDRIVE_PATH&/dayzworkbenchtool/StartDebugClient.bat");
	}
}