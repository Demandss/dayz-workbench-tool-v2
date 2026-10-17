param([string]$ProcessName = "workbenchApp")

Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;

public static class WbDialogs
{
    delegate bool EnumProc(IntPtr h, IntPtr l);

    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc p, IntPtr l);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool AttachThreadInput(uint a, uint b, bool attach);
    [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] static extern void SwitchToThisWindow(IntPtr h, bool altTab);
    [DllImport("user32.dll")] static extern bool BringWindowToTop(IntPtr h);

    // Visible top-level windows of the process whose title is in the list
    public static List<IntPtr> Find(uint processId, string[] titles)
    {
        List<IntPtr> found = new List<IntPtr>();
        EnumWindows(delegate(IntPtr h, IntPtr l)
        {
            uint pid;
            GetWindowThreadProcessId(h, out pid);
            if (pid != processId || !IsWindowVisible(h)) return true;
            StringBuilder text = new StringBuilder(256);
            GetWindowText(h, text, 256);
            foreach (string title in titles)
            {
                if (text.ToString().Equals(title, StringComparison.OrdinalIgnoreCase)) { found.Add(h); break; }
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static bool IsForeground(IntPtr h)
    {
        return GetForegroundWindow() == h;
    }

    // A hidden background process is not allowed to take the foreground, so a bare Alt tap and the
    // thread input attach are used to lift that restriction before bringing the dialog forward
    public static bool Focus(IntPtr h)
    {
        if (GetForegroundWindow() == h) return true;
        keybd_event(0x12, 0, 0, UIntPtr.Zero);
        keybd_event(0x12, 0, 2, UIntPtr.Zero);
        uint pid;
        uint foregroundThread = GetWindowThreadProcessId(GetForegroundWindow(), out pid);
        uint me = GetCurrentThreadId();
        AttachThreadInput(me, foregroundThread, true);
        SetForegroundWindow(h);
        BringWindowToTop(h);
        AttachThreadInput(me, foregroundThread, false);
        if (GetForegroundWindow() != h) SwitchToThisWindow(h, true);
        return GetForegroundWindow() == h;
    }
}
"@

# Qt dialogs expose no native buttons, so the "... All" button is pressed through its Alt+A mnemonic.
#   Script Authorization Required -> "Yes to All"   (grants the dangerous-method access to every script of the session)
#   Virtual machine exception     -> "Ignore All"
$titles = @("Script Authorization Required", "Virtual machine exception")
$attempts = @{}
$started = Get-Date
$seen = $false

while ($true)
{
    $processes = @(Get-Process -Name $ProcessName -ErrorAction SilentlyContinue)
    if ($processes.Count -eq 0)
    {
        if ($seen -or ((Get-Date) - $started).TotalSeconds -gt 60) { break }
        Start-Sleep -Milliseconds 500
        continue
    }
    $seen = $true

    foreach ($process in $processes)
    {
        foreach ($window in [WbDialogs]::Find([uint32]$process.Id, $titles))
        {
            # Only keystrokes that were really sent count as attempts; a failed focus is retried on the next pass
            $key = $window.ToString()
            $count = 0
            if ($attempts.ContainsKey($key)) { $count = $attempts[$key] }
            if ($count -ge 5) { continue }

            if ([WbDialogs]::Focus($window))
            {
                Start-Sleep -Milliseconds 100
                # The keys must never reach another application, so the focus is re-checked right before sending
                if ([WbDialogs]::IsForeground($window))
                {
                    $attempts[$key] = $count + 1
                    [System.Windows.Forms.SendKeys]::SendWait("%a")
                    Start-Sleep -Milliseconds 400
                }
            }
        }
    }

    Start-Sleep -Milliseconds 300
}
