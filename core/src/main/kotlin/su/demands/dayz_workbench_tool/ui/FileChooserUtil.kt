package su.demands.dayz_workbench_tool.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun pickFile(title: String = "Select File", extension: String = "exe", initialDir: String? = null): String? =
    withContext(Dispatchers.IO) {
        val initLine = if (!initialDir.isNullOrBlank()) "\$d.InitialDirectory = '$initialDir'; " else ""
        val script =
            "Add-Type -AssemblyName System.Windows.Forms; " +
            "\$d = New-Object System.Windows.Forms.OpenFileDialog; " +
            "\$d.Title = '$title'; " +
            initLine +
            "\$d.Filter = '$extension files (*.$extension)|*.$extension'; " +
            "if (\$d.ShowDialog() -eq 'OK') { Write-Output \$d.FileName }"
        runPs("-Command", script)
    }

suspend fun pickDirectory(title: String = "Select Directory", initialDir: String? = null): String? =
    withContext(Dispatchers.IO) {
        val temp = File.createTempFile("dayz_pick_", ".ps1")
        try {
            temp.writeText(folderPickerScript(title, initialDir))
            runPs("-ExecutionPolicy", "Bypass", "-File", temp.absolutePath)
        } finally {
            temp.delete()
        }
    }

private fun folderPickerScript(title: String, initialDir: String? = null) = """
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public class FolderPicker {
    [ComImport, ClassInterface(ClassInterfaceType.None),
     Guid("DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7")]
    private class FileOpenDialog {}

    [ComImport, Guid("42F85136-DB7E-439C-85F1-E4075D135FC8"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IFileDialog {
        [PreserveSig] uint Show(IntPtr hwnd);
        void SetFileTypes(uint c, IntPtr e);
        void SetFileTypeIndex(uint i);
        void GetFileTypeIndex(out uint i);
        void Advise(IntPtr s, out uint c);
        void Unadvise(uint c);
        void SetOptions(uint f);
        void GetOptions(out uint f);
        void SetDefaultFolder(IShellItem p);
        void SetFolder(IShellItem p);
        void GetFolder(out IShellItem p);
        void GetCurrentSelection(out IShellItem p);
        void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string n);
        void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string n);
        void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string t);
        void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string t);
        void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string t);
        void GetResult(out IShellItem p);
        void AddPlace(IShellItem p, int d);
        void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string e);
        void Close(uint hr);
        void SetClientGuid(ref Guid g);
        void ClearClientData();
        void SetFilter(IntPtr f);
    }

    [ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IShellItem {
        void BindToHandler(IntPtr pbc, ref Guid bhid, ref Guid riid, out IntPtr ppv);
        void GetParent(out IShellItem p);
        void GetDisplayName(uint f, [MarshalAs(UnmanagedType.LPWStr)] out string n);
        void GetAttributes(uint m, out uint a);
        void Compare(IShellItem p, uint h, out int o);
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SHCreateItemFromParsingName(
        string pszPath, IntPtr pbc, ref Guid riid, out IShellItem ppv);

    public static string Pick(string title, string initialDir) {
        try {
            var d = (IFileDialog)new FileOpenDialog();
            uint o; d.GetOptions(out o);
            d.SetOptions(o | 0x20); // FOS_PICKFOLDERS
            d.SetTitle(title);
            if (!string.IsNullOrEmpty(initialDir)) {
                var iid = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");
                IShellItem folder;
                if (SHCreateItemFromParsingName(initialDir, IntPtr.Zero, ref iid, out folder) == 0)
                    d.SetFolder(folder);
            }
            if (d.Show(IntPtr.Zero) != 0) return null;
            IShellItem item; d.GetResult(out item);
            string path; item.GetDisplayName(0x80058000, out path); // SIGDN_FILESYSPATH
            return path;
        } catch { return null; }
    }
}
'@
[FolderPicker]::Pick('$title', '${initialDir ?: ""}')
""".trimIndent()

fun runPs(vararg args: String): String? {
    val cmd = arrayOf("powershell", "-NoProfile", "-NonInteractive", "-STA") + args
    val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    return output.ifBlank { null }
}