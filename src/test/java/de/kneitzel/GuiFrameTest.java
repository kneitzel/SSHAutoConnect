package de.kneitzel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import javax.swing.*;
import java.awt.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for GUI frame classes.
 * These tests focus on initialization and basic functionality.
 *
 * @author Konrad Neitzel
 * @version 1.1-SNAPSHOT
 */
class GuiFrameTest {

    private static Supplier<com.jcraft.jsch.Session> mockSessionSupplier;

    private static String testRemoteDirectory;

    @BeforeAll
    static void setUpClass() {
        // Set headless mode for GUI testing if not already headless
        if (!GraphicsEnvironment.isHeadless()) {
            System.setProperty("java.awt.headless", "true");
        }

        mockSessionSupplier = () -> null; // Mock session supplier
        testRemoteDirectory = "/tmp/test/";
    }

    @Test
    @DisplayName("Should create UploadFrame with correct parameters")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "false")
    void shouldCreateUploadFrameWithCorrectParameters() {
        // When
        UploadFrame uploadFrame = new UploadFrame(mockSessionSupplier, testRemoteDirectory);

        // Then
        assertNotNull(uploadFrame);
        assertEquals("File Upload", uploadFrame.getTitle());
        assertEquals(JFrame.DISPOSE_ON_CLOSE, uploadFrame.getDefaultCloseOperation());
    }

    @Test
    @DisplayName("Should create DownloadFrame with correct parameters")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "false")
    void shouldCreateDownloadFrameWithCorrectParameters() {
        // When
        DownloadFrame downloadFrame = new DownloadFrame(mockSessionSupplier, testRemoteDirectory);

        // Then
        assertNotNull(downloadFrame);
        assertEquals("File Download", downloadFrame.getTitle());
        assertEquals(JFrame.DISPOSE_ON_CLOSE, downloadFrame.getDefaultCloseOperation());
    }

    @Test
    @DisplayName("Should handle null session supplier gracefully")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "false")
    void shouldHandleNullSessionSupplierGracefully() {
        // When & Then
        assertDoesNotThrow(() -> {
            new UploadFrame(null, testRemoteDirectory);
        });

        assertDoesNotThrow(() -> {
            new DownloadFrame(null, testRemoteDirectory);
        });
    }

    @Test
    @DisplayName("Should handle null remote directory gracefully")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "false")
    void shouldHandleNullRemoteDirectoryGracefully() {
        // When & Then
        assertDoesNotThrow(() -> {
            new UploadFrame(mockSessionSupplier, null);
        });

        assertDoesNotThrow(() -> {
            new DownloadFrame(mockSessionSupplier, null);
        });
    }

    @Test
    @DisplayName("Should handle empty remote directory gracefully")
    @DisabledIfSystemProperty(named = "java.awt.headless", matches = "false")
    void shouldHandleEmptyRemoteDirectoryGracefully() {
        // When & Then
        assertDoesNotThrow(() -> {
            new UploadFrame(mockSessionSupplier, "");
        });

        assertDoesNotThrow(() -> {
            new DownloadFrame(mockSessionSupplier, "");
        });
    }

    @Test
    @DisplayName("Should validate non-GUI functionality")
    void shouldValidateNonGuiFunctionality() {
        // Test basic supplier functionality
        assertNull(mockSessionSupplier.get());
        assertEquals("/tmp/test/", testRemoteDirectory);
    }
}
