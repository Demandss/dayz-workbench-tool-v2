You can work with the DayZ Layout Editor through its MCP server "dayz-layout-editor". Its tools edit .layout files of the DayZ GUI and draw them the way the game does. The server sends its full instructions when you connect.
{{connect}}
Where things are (the tools read and write only inside the work drive and the mod folders):
- Work drive: {{work_drive}}
- Game folder: {{game_folder}}
- Connected mod folders:
{{mods}}

How to start:
1. Call editor_status.
2. list_layouts finds the .layout files of the connected mods (query narrows it). open_layout opens one, create_layout makes a new one.
3. get_tree shows the widgets. Change them with the editing tools, check with screenshot (mode preview) and get_problems, then save_layout.

My task: 