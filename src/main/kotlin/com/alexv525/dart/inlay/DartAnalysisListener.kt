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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection

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
        
        // Handle files that are already open when the plugin starts (e.g., from previous session)
        // This addresses the issue where previously opened files don't get hints until manually modified
        ApplicationManager.getApplication().invokeLater {
            updateHintsForOpenDartFiles(project)
        }
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