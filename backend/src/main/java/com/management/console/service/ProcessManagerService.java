package com.management.console.service;

import com.management.console.domain.entity.ManagedService;
import com.management.console.exception.LifecycleActionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing OS processes - start, stop, restart with PID tracking
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessManagerService {

    private final ServiceLogManager serviceLogManager;

    @Value("${app.logging.base-dir:./logs}")
    private String baseLogDir;

    // In-memory tracking of processes started by this console
    private final Map<Long, ProcessInfo> runningProcesses = new ConcurrentHashMap<>();
    
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int GRACEFUL_SHUTDOWN_WAIT_MS = 5000;

    /**
     * Start a service process in the background
     */
    public ProcessResult startProcess(ManagedService service) {
        log.info("Starting process for service: {}", service.getName());
        
        String startCommand = service.getStartCommand();
        if (startCommand == null || startCommand.isEmpty()) {
            throw new LifecycleActionException("No start command configured for service: " + service.getName());
        }

        // Check if already running
        if (isProcessRunning(service)) {
            return ProcessResult.builder()
                    .success(false)
                    .message("Service is already running")
                    .pid(getProcessId(service))
                    .build();
        }

        try {
            ProcessBuilder processBuilder = createProcessBuilder(startCommand, service.getWorkingDirectory());
            
            // Redirect output to log files
            File logDir = new File(getLogDirectory(service));
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            File stdoutLog = new File(logDir, service.getName() + "-stdout.log");
            File stderrLog = new File(logDir, service.getName() + "-stderr.log");
            
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(stdoutLog));
            processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(stderrLog));
            
            // Start the process
            Process process = processBuilder.start();
            long pid = process.pid();
            
            // Track the process
            ProcessInfo processInfo = new ProcessInfo(pid, process, service.getId(), System.currentTimeMillis());
            runningProcesses.put(service.getId(), processInfo);
            
            // Save PID to file for persistence
            savePidFile(service, pid);
            
            // Wait briefly to check if process started successfully
            Thread.sleep(1000);
            
            if (process.isAlive()) {
                log.info("Service {} started successfully with PID: {}", service.getName(), pid);
                
                // Log lifecycle event
                serviceLogManager.logLifecycleEvent(service, "START", 
                        String.format("Process started with PID: %d", pid));
                
                return ProcessResult.builder()
                        .success(true)
                        .message("Service started successfully")
                        .pid(pid)
                        .output("Process started with PID: " + pid)
                        .build();
            } else {
                int exitCode = process.exitValue();
                String error = "Process exited immediately with code: " + exitCode;
                log.error("Failed to start service {}: {}", service.getName(), error);
                runningProcesses.remove(service.getId());
                
                // Log failed start
                serviceLogManager.logLifecycleEvent(service, "START_FAILED", error);
                
                return ProcessResult.builder()
                        .success(false)
                        .message(error)
                        .exitCode(exitCode)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Failed to start service {}: {}", service.getName(), e.getMessage(), e);
            throw new LifecycleActionException("Failed to start service: " + e.getMessage(), e);
        }
    }

    /**
     * Stop a service process gracefully, then forcefully if needed
     */
    public ProcessResult stopProcess(ManagedService service) {
        log.info("Stopping process for service: {}", service.getName());
        
        Long pid = getProcessId(service);
        
        if (pid == null) {
            // Try using the configured stop command
            if (service.getStopCommand() != null && !service.getStopCommand().isEmpty()) {
                return executeStopCommand(service);
            }
            return ProcessResult.builder()
                    .success(true)
                    .message("Service was not running")
                    .build();
        }

        try {
            // First try graceful shutdown with stop command if available
            if (service.getStopCommand() != null && !service.getStopCommand().isEmpty()) {
                ProcessResult stopResult = executeStopCommand(service);
                if (stopResult.isSuccess()) {
                    cleanup(service);
                    return stopResult;
                }
            }
            
            // Try graceful termination via PID
            boolean stopped = killProcess(pid, false);
            
            if (!stopped) {
                // Wait and check
                Thread.sleep(GRACEFUL_SHUTDOWN_WAIT_MS);
                
                if (isProcessAlive(pid)) {
                    // Force kill
                    log.warn("Graceful shutdown failed for service {}, forcing termination", service.getName());
                    stopped = killProcess(pid, true);
                } else {
                    stopped = true;
                }
            }
            
            if (stopped) {
                cleanup(service);
                
                // Log lifecycle event
                serviceLogManager.logLifecycleEvent(service, "STOP", 
                        String.format("Process with PID %d stopped successfully", pid));
                
                return ProcessResult.builder()
                        .success(true)
                        .message("Service stopped successfully")
                        .pid(pid)
                        .build();
            } else {
                // Log failed stop
                serviceLogManager.logLifecycleEvent(service, "STOP_FAILED", 
                        String.format("Failed to stop process with PID %d", pid));
                
                return ProcessResult.builder()
                        .success(false)
                        .message("Failed to stop service")
                        .pid(pid)
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Failed to stop service {}: {}", service.getName(), e.getMessage(), e);
            throw new LifecycleActionException("Failed to stop service: " + e.getMessage(), e);
        }
    }

    /**
     * Restart a service - stop then start
     */
    public ProcessResult restartProcess(ManagedService service) {
        log.info("Restarting process for service: {}", service.getName());
        
        // If there's a dedicated restart command, use it
        if (service.getRestartCommand() != null && !service.getRestartCommand().isEmpty()) {
            try {
                String output = executeCommand(service.getRestartCommand(), service.getWorkingDirectory());
                
                // Try to get new PID
                Thread.sleep(2000);
                Long newPid = findProcessByPort(service.getPort());
                if (newPid != null) {
                    ProcessInfo processInfo = new ProcessInfo(newPid, null, service.getId(), System.currentTimeMillis());
                    runningProcesses.put(service.getId(), processInfo);
                    savePidFile(service, newPid);
                }
                
                // Log lifecycle event
                serviceLogManager.logLifecycleEvent(service, "RESTART", 
                        String.format("Service restarted with new PID: %d", newPid));
                
                return ProcessResult.builder()
                        .success(true)
                        .message("Service restarted successfully")
                        .pid(newPid)
                        .output(output)
                        .build();
            } catch (Exception e) {
                serviceLogManager.logLifecycleEvent(service, "RESTART_FAILED", e.getMessage());
                throw new LifecycleActionException("Restart command failed: " + e.getMessage(), e);
            }
        }
        
        // Stop then start
        ProcessResult stopResult = stopProcess(service);
        if (!stopResult.isSuccess() && isProcessRunning(service)) {
            throw new LifecycleActionException("Failed to stop service before restart: " + stopResult.getMessage());
        }
        
        // Wait for ports to be released
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        ProcessResult startResult = startProcess(service);
        if (startResult.isSuccess()) {
            // Log lifecycle event
            serviceLogManager.logLifecycleEvent(service, "RESTART", 
                    String.format("Service restarted via stop/start with new PID: %d", startResult.getPid()));
            
            return ProcessResult.builder()
                    .success(true)
                    .message("Service restarted successfully")
                    .pid(startResult.getPid())
                    .output(startResult.getOutput())
                    .build();
        } else {
            serviceLogManager.logLifecycleEvent(service, "RESTART_FAILED", 
                    "Failed to start service after stop: " + startResult.getMessage());
            throw new LifecycleActionException("Failed to start service after stop: " + startResult.getMessage());
        }
    }

    /**
     * Check if process is running
     */
    public boolean isProcessRunning(ManagedService service) {
        Long pid = getProcessId(service);
        if (pid == null) {
            return false;
        }
        return isProcessAlive(pid);
    }

    /**
     * Get process ID for a service
     */
    public Long getProcessId(ManagedService service) {
        // First check in-memory tracking
        ProcessInfo info = runningProcesses.get(service.getId());
        if (info != null && isProcessAlive(info.getPid())) {
            return info.getPid();
        }
        
        // Check PID file
        Long pidFromFile = readPidFile(service);
        if (pidFromFile != null && isProcessAlive(pidFromFile)) {
            return pidFromFile;
        }
        
        // Try to find by port
        if (service.getPort() != null) {
            Long pidByPort = findProcessByPort(service.getPort());
            if (pidByPort != null) {
                return pidByPort;
            }
        }
        
        return null;
    }

    /**
     * Get process status
     */
    public ProcessStatus getProcessStatus(ManagedService service) {
        Long pid = getProcessId(service);
        if (pid == null) {
            return ProcessStatus.STOPPED;
        }
        
        if (isProcessAlive(pid)) {
            return ProcessStatus.RUNNING;
        }
        
        return ProcessStatus.STOPPED;
    }

    // Helper methods

    private ProcessBuilder createProcessBuilder(String command, String workingDirectory) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        if (IS_WINDOWS) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("sh", "-c", command);
        }
        
        if (workingDirectory != null && !workingDirectory.isEmpty()) {
            File dir = new File(workingDirectory);
            if (dir.exists() && dir.isDirectory()) {
                processBuilder.directory(dir);
            }
        }
        
        // Inherit environment
        processBuilder.environment().putAll(System.getenv());
        
        return processBuilder;
    }

    private ProcessResult executeStopCommand(ManagedService service) {
        try {
            String output = executeCommand(service.getStopCommand(), service.getWorkingDirectory());
            return ProcessResult.builder()
                    .success(true)
                    .message("Stop command executed successfully")
                    .output(output)
                    .build();
        } catch (Exception e) {
            return ProcessResult.builder()
                    .success(false)
                    .message("Stop command failed: " + e.getMessage())
                    .build();
        }
    }

    private String executeCommand(String command, String workingDirectory) throws Exception {
        ProcessBuilder processBuilder = createProcessBuilder(command, workingDirectory);
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        boolean completed = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new LifecycleActionException("Command timed out");
        }
        
        return output.toString().trim();
    }

    private boolean killProcess(long pid, boolean force) {
        try {
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                if (force) {
                    pb = new ProcessBuilder("taskkill", "/F", "/PID", String.valueOf(pid));
                } else {
                    pb = new ProcessBuilder("taskkill", "/PID", String.valueOf(pid));
                }
            } else {
                if (force) {
                    pb = new ProcessBuilder("kill", "-9", String.valueOf(pid));
                } else {
                    pb = new ProcessBuilder("kill", "-15", String.valueOf(pid));
                }
            }
            
            Process killProcess = pb.start();
            boolean completed = killProcess.waitFor(10, TimeUnit.SECONDS);
            
            if (completed) {
                int exitCode = killProcess.exitValue();
                return exitCode == 0;
            }
            return false;
            
        } catch (Exception e) {
            log.error("Failed to kill process {}: {}", pid, e.getMessage());
            return false;
        }
    }

    private boolean isProcessAlive(long pid) {
        try {
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("tasklist", "/FI", "PID eq " + pid, "/NH");
            } else {
                pb = new ProcessBuilder("kill", "-0", String.valueOf(pid));
            }
            
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (completed) {
                if (IS_WINDOWS) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains(String.valueOf(pid))) {
                                return true;
                            }
                        }
                    }
                    return false;
                } else {
                    return process.exitValue() == 0;
                }
            }
            return false;
            
        } catch (Exception e) {
            log.debug("Error checking if process {} is alive: {}", pid, e.getMessage());
            return false;
        }
    }

    private Long findProcessByPort(int port) {
        try {
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("cmd.exe", "/c", "netstat -ano | findstr :" + port + " | findstr LISTENING");
            } else {
                pb = new ProcessBuilder("sh", "-c", "lsof -t -i:" + port);
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    if (IS_WINDOWS) {
                        // Parse Windows netstat output: last column is PID
                        String[] parts = line.trim().split("\\s+");
                        if (parts.length > 0) {
                            return Long.parseLong(parts[parts.length - 1]);
                        }
                    } else {
                        return Long.parseLong(line.trim());
                    }
                }
            }
            
            process.waitFor(5, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.debug("Could not find process by port {}: {}", port, e.getMessage());
        }
        return null;
    }

    private String getLogDirectory(ManagedService service) {
        // Use centralized log directory
        Path logDir = Paths.get(baseLogDir, "services", service.getName());
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            log.warn("Could not create log directory: {}", logDir);
        }
        return logDir.toString();
    }

    private void savePidFile(ManagedService service, long pid) {
        try {
            Path pidPath = getPidFilePath(service);
            Files.createDirectories(pidPath.getParent());
            Files.writeString(pidPath, String.valueOf(pid));
            log.debug("Saved PID {} to file: {}", pid, pidPath);
        } catch (Exception e) {
            log.warn("Could not save PID file for service {}: {}", service.getName(), e.getMessage());
        }
    }

    private Long readPidFile(ManagedService service) {
        try {
            Path pidPath = getPidFilePath(service);
            if (Files.exists(pidPath)) {
                String content = Files.readString(pidPath).trim();
                return Long.parseLong(content);
            }
        } catch (Exception e) {
            log.debug("Could not read PID file for service {}: {}", service.getName(), e.getMessage());
        }
        return null;
    }

    private void deletePidFile(ManagedService service) {
        try {
            Path pidPath = getPidFilePath(service);
            Files.deleteIfExists(pidPath);
        } catch (Exception e) {
            log.warn("Could not delete PID file for service {}: {}", service.getName(), e.getMessage());
        }
    }

    private Path getPidFilePath(ManagedService service) {
        String baseDir = service.getWorkingDirectory();
        if (baseDir == null || baseDir.isEmpty()) {
            baseDir = System.getProperty("java.io.tmpdir") + File.separator + "management-console";
        }
        return Paths.get(baseDir, service.getName() + ".pid");
    }

    private void cleanup(ManagedService service) {
        runningProcesses.remove(service.getId());
        deletePidFile(service);
    }

    // Inner classes

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ProcessInfo {
        private long pid;
        private Process process;
        private long serviceId;
        private long startTime;
    }

    @lombok.Data
    @lombok.Builder
    public static class ProcessResult {
        private boolean success;
        private String message;
        private Long pid;
        private Integer exitCode;
        private String output;
    }

    public enum ProcessStatus {
        RUNNING,
        STOPPED,
        UNKNOWN
    }
}

