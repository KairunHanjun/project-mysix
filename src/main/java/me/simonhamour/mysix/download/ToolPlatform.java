package me.simonhamour.mysix.download;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public record ToolPlatform(String id, boolean windows) {
    public static ToolPlatform detect() throws IOException {
        return detect(System.getProperty("os.name"), System.getProperty("os.arch"),
                Files.exists(Path.of("/etc/alpine-release")));
    }
    public static ToolPlatform detect(String os, String cpu, boolean musl) throws IOException {
        os = os.toLowerCase(Locale.ROOT); cpu = cpu.toLowerCase(Locale.ROOT);
        String family = os.contains("mac") || os.contains("darwin") ? "macos"
                : os.startsWith("windows") ? "windows" : os.contains("linux") ? "linux" : "unknown";
        String arch = switch (cpu) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> "unknown";
        };
        if (family.equals("unknown") || arch.equals("unknown") || (family.equals("windows") && arch.equals("arm64"))
                || (family.equals("linux") && musl)) {
            throw new IOException("Automatic Mysix setup does not support " + os + "/" + cpu
                    + (musl ? " (musl/Alpine)" : "") + ". Configure tools manually; no system changes were made.");
        }
        return new ToolPlatform(family + "-" + arch, family.equals("windows"));
    }
    public String executable(String name) { return name + (windows ? ".exe" : ""); }
}
