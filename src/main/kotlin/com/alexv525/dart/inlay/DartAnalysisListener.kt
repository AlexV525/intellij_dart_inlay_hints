/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

package com.alexv525.dart.inlay

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService

/**
 * Listens for Dart file opening and analysis events to trigger inlay hint updates.
 * This ensures that parameter name hints are computed immediately when Dart files are opened
 * and when analysis is ready, not just on file modifications or formatting.
 */
class DartAnalysisListener : ProjectActivity {

    override suspend fun execute(project: Project) {
        val connection: MessageBusConnection = project.messageBus.connect()
        
        // Listen for file editor events
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (file.extension == "dart") {
                    ApplicationManager.getApplication().invokeLater {
                        triggerInlayHintsUpdate(project, file)
                    }
                }
            }
        })
        
        // Try to listen for Dart analysis server events if available
        try {
            val analysisService = DartAnalysisServerService.getInstance(project)
            // Schedule periodic checks for analysis completion on Dart files
            ApplicationManager.getApplication().executeOnPooledThread {
                schedulePeriodicHintUpdates(project)
            }
        } catch (e: Exception) {
            // DartAnalysisServerService might not be available, ignore
        }
    }

    private fun triggerInlayHintsUpdate(project: Project, file: VirtualFile) {
        val psiManager = PsiManager.getInstance(project)
        val psiFile = psiManager.findFile(file)
        
        if (psiFile != null) {
            // Use DaemonCodeAnalyzer to restart highlighting which includes inlay hints
            ApplicationManager.getApplication().invokeLater {
                DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
            }
        }
    }
    
    private fun schedulePeriodicHintUpdates(project: Project) {
        // Check for open Dart files and update hints every few seconds
        // This provides a fallback mechanism for hint updates
        while (!project.isDisposed) {
            try {
                Thread.sleep(3000) // Wait 3 seconds
                ApplicationManager.getApplication().invokeLater {
                    refreshInlayHintsForOpenDartFiles(project)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
    }

    private fun refreshInlayHintsForOpenDartFiles(project: Project) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        
        fileEditorManager.openFiles.forEach { virtualFile ->
            if (virtualFile.extension == "dart") {
                val psiFile = psiManager.findFile(virtualFile)
                if (psiFile != null) {
                    // Restart analysis for the file to refresh inlay hints
                    DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
                }
            }
        }
    }
}