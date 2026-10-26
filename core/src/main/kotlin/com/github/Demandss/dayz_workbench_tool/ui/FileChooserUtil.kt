package com.github.demandss.dayz_workbench_tool.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun pickFile(
    title: String = "Select File",
    extension: String = "exe",
    initialDir: String? = null,
): String? =
    withContext(Dispatchers.IO) {
        runPs("-Command", fileDialogScript(title, extension, initialDir))
    }

suspend fun pickDirectory(
    title: String = "Select Directory",
    initialDir: String? = null,
): String? =
    withContext(Dispatchers.IO) {
        val temp = File.createTempFile("dayz_pick_", ".ps1")
        try {
            writePowerShellFile(temp, folderPickerScript(title, initialDir))
            runPs("-ExecutionPolicy", "Bypass", "-File", temp.absolutePath)
        } finally {
            temp.delete()
        }
    }

/**
 * A text as a PowerShell string in single quotes. PowerShell takes the curly quotes (‘ ’ ‚ ‛) for the plain one, so each of
 * them inside the text is written twice, like the plain one.
 */
internal fun psQuote(text: String): String = "'" + text.replace(Regex("['‘’‚‛]")) { it.value + it.value } + "'"

/** The script of the dialog that picks a file; the title and the folder are quoted, so any text is safe in them. */
internal fun fileDialogScript(
    title: String,
    extension: String,
    initialDir: String? = null,
): String {
    val initLine = if (!initialDir.isNullOrBlank()) "\$d.InitialDirectory = ${psQuote(initialDir)}; " else ""
    return UTF8_OUTPUT +
        "Add-Type -AssemblyName System.Windows.Forms; " +
        "\$d = New-Object System.Windows.Forms.OpenFileDialog; " +
        "\$d.Title = ${psQuote(title)}; " +
        initLine +
        "\$d.Filter = ${psQuote("$extension files (*.$extension)|*.$extension")}; " +
        "if (\$d.ShowDialog() -eq 'OK') { Write-Output \$d.FileName }"
}

/** What the script says first, so that a path with letters of any alphabet comes back as it is. */
internal const val UTF8_OUTPUT = "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding \$false; "

/**
 * Writes [script] to [file] as UTF-8 with a byte order mark. Windows PowerShell 5.1 reads a script without the mark in the code
 * page of the system, and a translated title turns into other characters, some of them quotes.
 */
internal fun writePowerShellFile(
    file: File,
    script: String,
) {
    file.writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + script.toByteArray(Charsets.UTF_8))
}

private fun folderPickerScript(
    title: String,
    initialDir: String? = null,
) = UTF8_OUTPUT +
    """
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
[FolderPicker]::Pick(${psQuote(title)}, ${psQuote(initialDir ?: "")})
    """.trimIndent()

/**
 * Runs PowerShell and returns what the script wrote, or null when it wrote nothing or failed: an error message is not an answer
 * (it used to come back as if it were the path that was picked).
 */
fun runPs(vararg args: String): String? {
    val cmd = arrayOf("powershell", "-NoProfile", "-NonInteractive", "-STA") + args
    val process =
        ProcessBuilder(*cmd)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    val output =
        process.inputStream
            .bufferedReader(Charsets.UTF_8)
            .readText()
            .removePrefix("\uFEFF")
            .trim()
    if (process.waitFor() != 0) return null
    return output.ifBlank { null }
}
