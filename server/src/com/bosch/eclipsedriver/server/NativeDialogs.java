package com.bosch.eclipsedriver.server;

import com.bosch.eclipsedriver.protocol.ErrorCode;
import com.bosch.eclipsedriver.protocol.Json;
import com.bosch.eclipsedriver.protocol.ToolError;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Native OS dialog detection and control for the target process (FR-N1..FR-N6).
 * Runs from the server process via Win32 user32/gdi32 P/Invoke in PowerShell so no native dependency is added.
 */
public final class NativeDialogs {
    private static final String WIN32_HELPER = """
            Add-Type @"
            using System;
            using System.Collections.Generic;
            using System.Runtime.InteropServices;
            using System.Text;

            public class EdWin32 {
                public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);
                [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);
                [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr hWndParent, EnumWindowsProc lpEnumFunc, IntPtr lParam);
                [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
                [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
                [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hWnd, StringBuilder lpClassName, int nMaxCount);
                [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder lpString, int nMaxCount);
                [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, String lParam);
                [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, StringBuilder lParam);
                [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);
                [DllImport("user32.dll")] public static extern int GetDlgCtrlID(IntPtr hWnd);
                [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT lpRect);
                [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);

                [StructLayout(LayoutKind.Sequential)]
                public struct RECT { public int Left, Top, Right, Bottom; }

                public const uint WM_SETTEXT = 0x000C;
                public const uint WM_GETTEXT = 0x000D;
                public const uint WM_GETTEXTLENGTH = 0x000E;
                public const uint WM_CLOSE = 0x0010;
                public const uint WM_COMMAND = 0x0111;
                public const uint WM_KEYDOWN = 0x0100;
                public const uint BM_CLICK = 0x00F5;

                public static string ClassOf(IntPtr h) {
                    var sb = new StringBuilder(256);
                    GetClassName(h, sb, sb.Capacity);
                    return sb.ToString();
                }
                public static List<IntPtr> DialogsOf(uint pid) {
                    var res = new List<IntPtr>();
                    EnumWindows(delegate(IntPtr h, IntPtr l) {
                        if (!IsWindowVisible(h)) return true;
                        uint wp; GetWindowThreadProcessId(h, out wp);
                        if (wp == pid && ClassOf(h) == "#32770") res.Add(h);
                        return true;
                    }, IntPtr.Zero);
                    return res;
                }
                public static List<IntPtr> ChildrenOf(IntPtr parent) {
                    var res = new List<IntPtr>();
                    EnumChildWindows(parent, delegate(IntPtr h, IntPtr l) { res.Add(h); return true; }, IntPtr.Zero);
                    return res;
                }
                public static string TextOf(IntPtr h) {
                    int len = (int)SendMessage(h, WM_GETTEXTLENGTH, IntPtr.Zero, (String)null);
                    var sb = new StringBuilder(Math.Max(256, len + 2));
                    SendMessage(h, WM_GETTEXT, (IntPtr)sb.Capacity, sb);
                    if (sb.Length > 0) return sb.ToString();
                    GetWindowText(h, sb, sb.Capacity);
                    return sb.ToString();
                }
            }
            "@
            """;

    public Object handle(String tool, Map<String, Object> args, Instance instance) {
        long pid = instance.pid();
        return switch (tool) {
            case "list_native_dialogs" -> listDialogs(pid);
            case "native_dialog_set_path" -> setPath(pid, args);
            case "native_dialog_confirm" -> confirm(pid, args);
            case "native_dialog_cancel" -> cancel(pid, args);
            case "native_dialog_press" -> press(pid, args);
            case "native_dialog_screenshot" -> screenshot(pid, args);
            default -> throw new ToolError(ErrorCode.UNKNOWN_TOOL, "not a native dialog tool: " + tool);
        };
    }

    public List<Map<String, Object>> findOpenDialogs(long pid) {
        String script = WIN32_HELPER + "\n"
                + "$targetPid = " + pid + "\n"
                + "$list = New-Object System.Collections.ArrayList\n"
                + "foreach ($h in [EdWin32]::DialogsOf([uint32]$targetPid)) {\n"
                + "  $title = [EdWin32]::TextOf($h)\n"
                + "  $edits = New-Object System.Collections.ArrayList\n"
                + "  $btns = New-Object System.Collections.ArrayList\n"
                + "  $kids = New-Object System.Collections.ArrayList\n"
                + "  foreach ($ch in [EdWin32]::ChildrenOf($h)) {\n"
                + "    $ccls = [EdWin32]::ClassOf($ch)\n"
                + "    [void]$kids.Add($ccls)\n"
                + "    if ($ccls -eq 'Edit') { [void]$edits.Add([EdWin32]::TextOf($ch)) }\n"
                + "    elseif ($ccls -eq 'Button') {\n"
                + "      $bt = ([EdWin32]::TextOf($ch) -replace '&','').Trim()\n"
                + "      if ($bt.Length -gt 0) { [void]$btns.Add($bt) }\n"
                + "    }\n"
                + "  }\n"
                + "  $pf = if ($edits.Count -gt 0) { $edits[0] } else { '' }\n"
                + "  $r = New-Object EdWin32+RECT\n"
                + "  [EdWin32]::GetWindowRect($h, [ref]$r) | Out-Null\n"
                + "  [void]$list.Add(@{ hwnd = $h.ToInt64(); title = $title; pid = [int64]$targetPid; className = '#32770'; pathField = $pf; buttons = @($btns); childClasses = @($kids | Select-Object -Unique); bounds = @{ x = $r.Left; y = $r.Top; width = ($r.Right - $r.Left); height = ($r.Bottom - $r.Top) } })\n"
                + "}\n"
                + "$null = $null\n"
                + "if ($list.Count -eq 0) { Write-Output '[]' } else { Write-Output (ConvertTo-Json -InputObject @($list) -Compress -Depth 4) }\n";
        String raw = runPowerShell(script);
        return parseListOutput(raw).stream()
                .filter(d -> !(d.get("childClasses") instanceof List<?> cc
                        && cc.stream().anyMatch(c -> String.valueOf(c).startsWith("SWT_"))))
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<String, Object> listDialogs(long pid) {
        List<Map<String, Object>> dialogs = findOpenDialogs(pid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pid", pid);
        out.put("count", (long) dialogs.size());
        out.put("dialogs", dialogs);
        return out;
    }

    private Map<String, Object> setPath(long pid, Map<String, Object> args) {
        String path = (String) args.get("path");
        String title = args.get("title") instanceof String s && !s.isEmpty() ? s : null;
        Map<String, Object> dlg = requireDialog(pid, title);
        long hwnd = ((Number) dlg.get("hwnd")).longValue();
        String escaped = escapePsSingleQuoted(path);
        String script = WIN32_HELPER + "\n"
                + "$dlg = [IntPtr]::new(" + hwnd + ")\n"
                + "$val = '" + escaped + "'\n"
                + "$edits = New-Object System.Collections.ArrayList\n"
                + "$kids = [EdWin32]::ChildrenOf($dlg)\n"
                + "foreach ($ch in $kids) { if ([EdWin32]::ClassOf($ch) -eq 'Edit' -and [EdWin32]::IsWindowVisible($ch)) { [void]$edits.Add($ch) } }\n"
                + "if ($edits.Count -eq 0) {\n"
                + "  foreach ($ch in $kids) { if ([EdWin32]::ClassOf($ch) -eq 'Edit') { [void]$edits.Add($ch) } }\n"
                + "}\n"
                + "if ($edits.Count -eq 0) { Write-Output 'NO_EDIT'; exit 0 }\n"
                + "foreach ($ed in $edits) {\n"
                + "  [EdWin32]::SendMessage($ed, [EdWin32]::WM_SETTEXT, [IntPtr]::Zero, $val) | Out-Null\n"
                + "}\n"
                + "Write-Output 'OK'\n";
        String res = runPowerShell(script).trim();
        if ("NO_EDIT".equals(res)) {
            throw new ToolError(ErrorCode.NOT_FOUND, "native dialog '" + dlg.get("title") + "' has no Edit field");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", dlg.get("title"));
        out.put("hwnd", hwnd);
        out.put("path", path);
        return out;
    }

    private Map<String, Object> confirm(long pid, Map<String, Object> args) {
        String title = args.get("title") instanceof String s && !s.isEmpty() ? s : null;
        Map<String, Object> dlg = requireDialog(pid, title);
        long hwnd = ((Number) dlg.get("hwnd")).longValue();
        String script = WIN32_HELPER + "\n"
                + "$dlg = [IntPtr]::new(" + hwnd + ")\n"
                + "$targetBtn = [IntPtr]::Zero\n"
                + "$fallbackBtn = [IntPtr]::Zero\n"
                + "foreach ($ch in [EdWin32]::ChildrenOf($dlg)) {\n"
                + "  if ([EdWin32]::ClassOf($ch) -eq 'Button') {\n"
                + "    $id = [EdWin32]::GetDlgCtrlID($ch)\n"
                + "    $txt = ([EdWin32]::TextOf($ch) -replace '&','').Trim()\n"
                + "    if ($id -eq 1 -and $targetBtn -eq [IntPtr]::Zero) { $targetBtn = $ch }\n"
                + "    elseif ($txt -match '^(Open|Save|OK|Yes|Select Folder)$' -and $fallbackBtn -eq [IntPtr]::Zero) { $fallbackBtn = $ch }\n"
                + "  }\n"
                + "}\n"
                + "$btn = if ($targetBtn -ne [IntPtr]::Zero) { $targetBtn } else { $fallbackBtn }\n"
                + "if ($btn -ne [IntPtr]::Zero) {\n"
                + "  [EdWin32]::PostMessage($btn, [EdWin32]::BM_CLICK, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null\n"
                + "} else {\n"
                + "  [EdWin32]::PostMessage($dlg, [EdWin32]::WM_COMMAND, [IntPtr]::new(1), [IntPtr]::Zero) | Out-Null\n"
                + "}\n"
                + "Write-Output 'OK'\n";
        runPowerShell(script);
        waitDialogClose(pid, hwnd);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", dlg.get("title"));
        out.put("hwnd", hwnd);
        out.put("confirmed", true);
        return out;
    }

    private Map<String, Object> cancel(long pid, Map<String, Object> args) {
        String title = args.get("title") instanceof String s && !s.isEmpty() ? s : null;
        Map<String, Object> dlg = requireDialog(pid, title);
        long hwnd = ((Number) dlg.get("hwnd")).longValue();
        String script = WIN32_HELPER + "\n"
                + "$dlg = [IntPtr]::new(" + hwnd + ")\n"
                + "[EdWin32]::PostMessage($dlg, [EdWin32]::WM_COMMAND, [IntPtr]::new(2), [IntPtr]::Zero) | Out-Null\n"
                + "[EdWin32]::PostMessage($dlg, [EdWin32]::WM_CLOSE, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null\n"
                + "Write-Output 'OK'\n";
        runPowerShell(script);
        waitDialogClose(pid, hwnd);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", dlg.get("title"));
        out.put("hwnd", hwnd);
        out.put("cancelled", true);
        return out;
    }

    private Map<String, Object> press(long pid, Map<String, Object> args) {
        String buttonName = (String) args.get("button");
        String title = args.get("title") instanceof String s && !s.isEmpty() ? s : null;
        Map<String, Object> dlg = requireDialog(pid, title);
        long hwnd = ((Number) dlg.get("hwnd")).longValue();
        String escaped = escapePsSingleQuoted(buttonName);
        String script = WIN32_HELPER + "\n"
                + "$dlg = [IntPtr]::new(" + hwnd + ")\n"
                + "$want = '" + escaped + "'\n"
                + "$hit = [IntPtr]::Zero\n"
                + "foreach ($ch in [EdWin32]::ChildrenOf($dlg)) {\n"
                + "  if ([EdWin32]::ClassOf($ch) -eq 'Button') {\n"
                + "    $txt = ([EdWin32]::TextOf($ch) -replace '&','').Trim()\n"
                + "    if ($txt -ieq $want -or $txt -like \"*$want*\") { $hit = $ch; break }\n"
                + "  }\n"
                + "}\n"
                + "if ($hit -eq [IntPtr]::Zero) { Write-Output 'NOT_FOUND'; exit 0 }\n"
                + "[EdWin32]::PostMessage($hit, [EdWin32]::BM_CLICK, [IntPtr]::Zero, [IntPtr]::Zero) | Out-Null\n"
                + "Write-Output 'OK'\n";
        String res = runPowerShell(script).trim();
        if ("NOT_FOUND".equals(res)) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no button '" + buttonName + "' in native dialog '" + dlg.get("title") + "'")
                    .with("candidates", dlg.get("buttons"));
        }
        waitDialogClose(pid, hwnd);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", dlg.get("title"));
        out.put("hwnd", hwnd);
        out.put("pressed", buttonName);
        return out;
    }

    private Map<String, Object> screenshot(long pid, Map<String, Object> args) {
        String title = args.get("title") instanceof String s && !s.isEmpty() ? s : null;
        Map<String, Object> dlg = requireDialog(pid, title);
        long hwnd = ((Number) dlg.get("hwnd")).longValue();
        Path outPath;
        if (args.get("path") instanceof String s && !s.isBlank()) {
            outPath = Path.of(s).toAbsolutePath();
        } else {
            try {
                outPath = Files.createTempFile("eclipse-driver-native-shot-", ".png").toAbsolutePath();
            } catch (IOException e) {
                throw new ToolError(ErrorCode.INTERNAL_ERROR, "failed to create temp screenshot file: " + e.getMessage());
            }
        }
        String escapedOut = escapePsSingleQuoted(outPath.toString());
        String script = WIN32_HELPER + "\n"
                + "Add-Type -AssemblyName System.Drawing\n"
                + "$dlg = [IntPtr]::new(" + hwnd + ")\n"
                + "$r = New-Object EdWin32+RECT\n"
                + "[EdWin32]::GetWindowRect($dlg, [ref]$r) | Out-Null\n"
                + "$w = [Math]::Max(1, $r.Right - $r.Left)\n"
                + "$h = [Math]::Max(1, $r.Bottom - $r.Top)\n"
                + "$bmp = New-Object System.Drawing.Bitmap($w, $h)\n"
                + "$g = [System.Drawing.Graphics]::FromImage($bmp)\n"
                + "$g.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))\n"
                + "$g.Dispose()\n"
                + "$bmp.Save('" + escapedOut + "', [System.Drawing.Imaging.ImageFormat]::Png)\n"
                + "$bmp.Dispose()\n"
                + "Write-Output \"$w,$h\"\n";
        String dims = runPowerShell(script).trim();
        long width = 0;
        long height = 0;
        String[] parts = dims.split(",");
        if (parts.length == 2) {
            try {
                width = Long.parseLong(parts[0].trim());
                height = Long.parseLong(parts[1].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        long bytes = 0;
        try {
            bytes = Files.size(outPath);
        } catch (IOException ignored) {
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", dlg.get("title"));
        out.put("hwnd", hwnd);
        out.put("path", outPath.toString());
        out.put("width", width);
        out.put("height", height);
        out.put("bytes", bytes);
        return out;
    }

    private void waitDialogClose(long pid, long hwnd) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            boolean stillOpen = findOpenDialogs(pid).stream()
                    .anyMatch(d -> ((Number) d.get("hwnd")).longValue() == hwnd);
            if (!stillOpen) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Map<String, Object> requireDialog(long pid, String titlePart) {
        List<Map<String, Object>> dialogs = findOpenDialogs(pid);
        if (dialogs.isEmpty()) {
            throw new ToolError(ErrorCode.NOT_FOUND, "no native OS dialog is open for pid " + pid)
                    .hint("call list_native_dialogs to check");
        }
        if (titlePart == null) {
            return dialogs.get(0);
        }
        for (Map<String, Object> d : dialogs) {
            String t = String.valueOf(d.get("title"));
            if (t.toLowerCase().contains(titlePart.toLowerCase())) {
                return d;
            }
        }
        throw new ToolError(ErrorCode.NOT_FOUND, "no native dialog matching '" + titlePart + "'")
                .with("candidates", dialogs);
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> parseListOutput(String raw) {
        if (raw == null) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "[]".equals(trimmed) || "null".equals(trimmed)) {
            return List.of();
        }
        int bracket = trimmed.indexOf('[');
        int brace = trimmed.indexOf('{');
        if (bracket >= 0 && (brace < 0 || bracket < brace)) {
            trimmed = trimmed.substring(bracket);
        } else if (brace >= 0) {
            trimmed = "[" + trimmed.substring(brace) + "]";
        }
        Object parsed = Json.parse(trimmed);
        if (parsed instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        if (parsed instanceof Map<?, ?> single) {
            return List.of((Map<String, Object>) single);
        }
        return List.of();
    }

    public static ToolError augmentUiBlocked(ToolError error, List<Map<String, Object>> openDialogs) {
        if (openDialogs == null || openDialogs.isEmpty()) {
            return error;
        }
        List<String> titles = new ArrayList<>();
        for (Map<String, Object> d : openDialogs) {
            titles.add(String.valueOf(d.get("title")));
        }
        return new ToolError(
                ErrorCode.UI_THREAD_BLOCKED,
                error.getMessage() + "; native OS dialog open: " + titles)
                .hint("use list_native_dialogs, native_dialog_set_path, native_dialog_confirm or native_dialog_cancel")
                .with("nativeDialogs", openDialogs);
    }

    private static String escapePsSingleQuoted(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    private static String runPowerShell(String script) {
        java.nio.file.Path file = null;
        try {
            file = java.nio.file.Files.createTempFile("ed-native-", ".ps1");
            java.nio.file.Files.write(file, ("\uFEFF" + script).getBytes(StandardCharsets.UTF_8));
            Process proc = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                    "-File", file.toString())
                    .redirectErrorStream(true)
                    .redirectInput(ProcessBuilder.Redirect.from(new java.io.File("NUL")))
                    .start();
            byte[] out = proc.getInputStream().readAllBytes();
            if (!proc.waitFor(15, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new ToolError(ErrorCode.TIMEOUT, "PowerShell native-dialog helper timed out");
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolError(ErrorCode.INTERNAL_ERROR, "failed to run PowerShell for native dialog: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolError(ErrorCode.INTERNAL_ERROR, "interrupted while running native dialog helper");
        } finally {
            if (file != null) {
                try { java.nio.file.Files.deleteIfExists(file); } catch (IOException ignored) { }
            }
        }
    }
}
