package vinneg.ip.grabber.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Slf4j
@Service
public class NetStatService {

    private final Set<String> ips = new HashSet<>();

    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b((?:\\d{1,3}\\.){3}\\d{1,3}):3724\\b"
    );

    // Путь к файлу с начальными IP в classpath
    private static final String IPS_FILE = "src/main/resources/ips";
    private final Path ipsFile;

    public NetStatService() {
        ipsFile = Paths.get(IPS_FILE);
        readIps();
    }

    private void readIps() {
        try {
            if (!Files.exists(ipsFile)) {
                log.warn("File {} not found in classpath. Starting with empty set.", IPS_FILE);
                return;
            }

            try (BufferedReader reader = Files.newBufferedReader(ipsFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();

                    if (!trimmed.isEmpty() && IP_PATTERN.matcher(trimmed).matches()) {
                        ips.add(trimmed);
                    } else if (!trimmed.isEmpty()) {
                        log.warn("Skipping invalid IP line: {}", line);
                    }
                }
            }

            log.info("Loaded {} initial IPs from {}", ips.size(), IPS_FILE);
        } catch (IOException e) {
            log.error("Error loading initial IPs from file", e);
        }
    }

    private void appendIps(Set<String> newIps) {
        try {
            // Создаём файл, если его ещё нет
            if (!Files.exists(ipsFile)) {
                Files.createFile(ipsFile);
                log.info("Created file {} because it did not exist.", IPS_FILE);
            }

            for (String ip : newIps) {
                Files.writeString(ipsFile, ip + System.lineSeparator(), java.nio.file.StandardOpenOption.APPEND);
            }
            log.info("Appended {} new IP(s) to {}", newIps.size(), IPS_FILE);
        } catch (IOException e) {
            log.error("Failed to write new IPs to file", e);
        }
    }


    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void monitor() {
        try {
            Process process = Runtime.getRuntime().exec("netstat -ano");

            final Set<String> newIps = new HashSet<>();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Optional.of(line)
                            .map(IP_PATTERN::matcher)
                            .filter(Matcher::find)
                            .map(v -> v.group(1))
                            .filter(v -> !ips.contains(v))
                            .ifPresent(ip -> {
                                ips.add(ip);
                                newIps.add(ip);
                            });
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("netstat command exited with code {}", exitCode);
            }

            if (!newIps.isEmpty()) {
                appendIps(newIps);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error running netstat: {}", e.getMessage(), e);
        }
    }

}
