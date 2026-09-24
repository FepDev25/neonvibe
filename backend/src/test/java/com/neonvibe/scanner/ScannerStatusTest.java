package com.neonvibe.scanner;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ScannerStatus}: state transitions, counter resets on a
 * new scan and the immutability of the failed-files snapshot.
 */
class ScannerStatusTest {

    private final ScannerStatus status = new ScannerStatus();

    @Test
    void initialState_isIdleAndZeroed() {
        assertThat(status.getState()).isEqualTo(ScannerStatus.State.IDLE);
        assertThat(status.isRunning()).isFalse();
        assertThat(status.getTotalScanned()).isZero();
        assertThat(status.getProcessed()).isZero();
        assertThat(status.getFailed()).isZero();
        assertThat(status.getFailedFiles()).isEmpty();
    }

    @Test
    void markScanning_setsStateAndResetsCounters() {
        status.incScanned();
        status.incProcessed();
        status.incFailed("/old.mp3", "boom");

        Instant start = Instant.now();
        status.markScanning(start);

        assertThat(status.getState()).isEqualTo(ScannerStatus.State.SCANNING);
        assertThat(status.isRunning()).isTrue();
        assertThat(status.getStartedAt()).isEqualTo(start);
        assertThat(status.getFinishedAt()).isNull();
        assertThat(status.getTotalScanned()).isZero();
        assertThat(status.getProcessed()).isZero();
        assertThat(status.getFailed()).isZero();
        assertThat(status.getFailedFiles()).isEmpty();
    }

    @Test
    void counters_increment() {
        status.incScanned();
        status.incScanned();
        status.incProcessed();
        status.incFailed("/x.mp3", "corrupt");

        assertThat(status.getTotalScanned()).isEqualTo(2);
        assertThat(status.getProcessed()).isEqualTo(1);
        assertThat(status.getFailed()).isEqualTo(1);
        assertThat(status.getFailedFiles()).containsEntry("/x.mp3", "corrupt");
    }

    @Test
    void markIdle_setsStateAndFinish() {
        status.markScanning(Instant.now());
        Instant finish = Instant.now();

        status.markIdle(finish);

        assertThat(status.getState()).isEqualTo(ScannerStatus.State.IDLE);
        assertThat(status.isRunning()).isFalse();
        assertThat(status.getFinishedAt()).isEqualTo(finish);
    }

    @Test
    void failedFiles_isUnmodifiable() {
        status.incFailed("/x.mp3", "corrupt");

        assertThatThrownBy(() -> status.getFailedFiles().put("/y.mp3", "x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
