/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection
import com.alexv525.dart.inlay.settings.DartInlaySettings

/**
 * Listens for Dart file events and project startup to trigger inlay hint updates.
 * This ensures that parameter name hints are computed immediately when:
 * - Dart files are opened
 * - Project starts with already-open Dart files (handles previous session files)
 * - Analysis is completed (via automatic IntelliJ analysis lifecycle)
 */
class DartAnalysisListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        val connection: MessageBusConnection = project.messageBus.connect()

        // Listen for file editor events (when files are newly opened)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (file.extension == "dart") {
                    ApplicationManager.getApplication().invokeLater {
                        triggerInlayHintsUpdate(project, file)
                    }
                }
            }
        })

        // Listen for settings changes to refresh hints immediately
        setupSettingsListener(project)

        // Handle files that are already open when the plugin starts (e.g., from previous session)
        // This addresses the issue where previously opened files don't get hints until manually modified
        ApplicationManager.getApplication().invokeLater {
            updateHintsForOpenDartFiles(project)
        }
    }

    // TODO: Implement to listen settings change
    private fun setupSettingsListener(project: Project) {
        // Listen for settings changes and refresh all Dart files immediately
        val settings = DartInlaySettings.getInstance()

        // Note: For a complete implementation, we would need to add a proper settings change listener.
        // For now, this comment serves as a placeholder for the feature.
        // The IntelliJ platform typically handles inlay hint refreshing automatically when
        // the settings UI is used, but custom settings might need manual refresh triggers.

        // A full implementation would involve:
        // 1. Adding a SettingsChangeListener to DartInlaySettings
        // 2. Triggering updateHintsForOpenDartFiles(project) when settings change
        // 3. Using the MessageBus to communicate settings changes across the plugin
    }

    private fun triggerInlayHintsUpdate(project: Project, file: VirtualFile) {
        val psiManager = PsiManager.getInstance(project)
        val psiFile = psiManager.findFile(file)

        if (psiFile != null && psiFile.language.id == "Dart") {
            // Use DaemonCodeAnalyzer to restart highlighting which includes inlay hints
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
        }
    }

    private fun updateHintsForOpenDartFiles(project: Project) {
        if (project.isDisposed) return

        val fileEditorManager = FileEditorManager.getInstance(project)
        val psiManager = PsiManager.getInstance(project)

        fileEditorManager.openFiles.forEach { virtualFile ->
            if (virtualFile.extension == "dart") {
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile != null && psiFile.language.id == "Dart") {
                    // Restart analysis for the file to refresh inlay hints
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                }
            }
        }
    }
}
