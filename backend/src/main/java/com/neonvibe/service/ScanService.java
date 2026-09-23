package com.neonvibe.service;

import com.neonvibe.dto.ScanStatusResponse;
import com.neonvibe.scanner.MusicScannerService;
import com.neonvibe.scanner.ScannerStatus;
import org.springframework.stereotype.Service;

/**
 * Facade for scanner control used by the admin endpoints.
 */
@Service
public class ScanService {

    private final MusicScannerService musicScannerService;

    public ScanService(MusicScannerService musicScannerService) {
        this.musicScannerService = musicScannerService;
    }

    /**
     * Triggers a scan asynchronously on the scanner's executor so the HTTP
     * request returns immediately (202).
     */
    public void triggerScan() {
        musicScannerService.scanAsync();
    }

    public ScanStatusResponse status() {
        ScannerStatus s = musicScannerService.getStatus();
        return ScanStatusResponse.fromState(s.getState(), s.getTotalScanned(), s.getProcessed(),
                s.getFailed(), s.isRunning(), s.getStartedAt(), s.getFinishedAt(), s.getFailedFiles());
    }
}
