/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

@file:Suppress("UnstableApiUsage")

package com.alexv525.dart.inlay

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.*
import com.alexv525.dart.inlay.psi.PsiVariableTypeHintCalculator
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

/**
 * Inlay hints provider for Dart variable type hints using the modern IntelliJ API.
 * Shows type information for implicitly typed local variables.
 */
@Suppress("UnstableApiUsage")
class DartVariableTypeInlayHintsProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("dart.variable.type.hints")

    override val name: String = "Variable Type Hints"

    override val previewText: String = """
        var String name = "John";
        final int age = 25;
        late bool value = true;
    """.trimIndent()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        // Only apply to .dart files
        if (file.virtualFile?.extension != "dart") {
            return null
        }

        // Performance safeguards
        val dartSettings = com.alexv525.dart.inlay.settings.DartInlaySettings.getInstance()
        
        // Skip if variable type hints are disabled
        if (!dartSettings.enableVariableTypeHints) {
            return null
        }
        
        // Skip large files
        val fileText = file.text
        if (fileText.length > dartSettings.maxFileSize) {
            return null
        }
        
        // Respect dumb mode - IntelliJ will handle this automatically for inlay hints
        // but we can add explicit check if needed
        
        return DartVariableTypeInlayHintsCollector(editor)
    }

    override fun createSettings(): NoSettings = NoSettings()

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): javax.swing.JComponent {
                return javax.swing.JLabel("No configuration needed")
            }
        }
    }

    override val description: String = "Show inferred type information for implicitly typed variables"

    override val group: InlayGroup
        get() = InlayGroup.TYPES_GROUP
}

/**
 * Collector that computes and displays variable type hints.
 * Includes performance optimizations and caching.
 */
private class DartVariableTypeInlayHintsCollector(editor: Editor) : FactoryInlayHintsCollector(editor) {
    private val textMetricsStorageKey = Key.create<InlayTextMetricsStorage>("InlayTextMetricsStorage")
    private val textMetricsStorage = getTextMetricStorage(editor)
    private val offsetFromTopProvider = object : InsetValueProvider {
        override val top: Int
            get() = textMetricsStorage.getFontMetrics(true).offsetFromTop() - 2
        override val right: Int = 6
    }

    // Cache for hint calculations per file modification stamp
    private var lastModificationStamp = -1L
    private val hintCache = mutableMapOf<Int, Pair<Int, String>?>()

    fun getTextMetricStorage(editor: Editor): InlayTextMetricsStorage {
        val storage = editor.getUserData(textMetricsStorageKey)
        if (storage == null) {
            val newStorage = InlayTextMetricsStorage(editor)
            editor.putUserData(textMetricsStorageKey, newStorage)
            return newStorage
        }
        return storage
    }

    // Track processed offsets to avoid duplicate hints
    private val processedOffsets = mutableSetOf<Int>()

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
        // Check if we should skip processing due to dumb mode
        if (com.intellij.openapi.project.DumbService.isDumb(element.project)) {
            return false
        }

        val currentStamp = element.containingFile?.modificationStamp ?: 0L
        
        // Clear cache if file has been modified
        if (currentStamp != lastModificationStamp) {
            hintCache.clear()
            processedOffsets.clear()
            lastModificationStamp = currentStamp
        }

        // Use cached result if available
        val elementOffset = element.textRange?.startOffset ?: return false
        val cachedHint = hintCache[elementOffset]
        
        val hint = if (cachedHint != null) {
            cachedHint
        } else {
            // Calculate and cache the hint
            val calculatedHint = PsiVariableTypeHintCalculator.calculateForElement(element)
            hintCache[elementOffset] = calculatedHint
            calculatedHint
        }

        if (hint != null) {
            val (offset, hintText) = hint

            // Only add the hint if we haven't processed this offset yet
            if (offset !in processedOffsets) {
                processedOffsets.add(offset)

                // Add the hint at the specified offset with proper inlay hint styling
                sink.addInlineElement(
                    offset = offset,
                    relatesToPrecedingText = false,
                    presentation = createPresentation(editor, hintText),
                    placeAtTheEndOfLine = false,
                )
            }
        }

        return true
    }

    private fun createPresentation(editor: Editor, hintText: String): InlayPresentation {
        var presentation: InlayPresentation = TextInlayPresentation(
            textMetricsStorage,
            true,
            hintText,
        )
        presentation = RoundWithBackgroundPresentation(
            InsetPresentation(presentation, left = 5, right = 5, top = 3, down = 3),
            8,
            8,
        )
        presentation = WithAttributesPresentation(
            presentation,
            DefaultLanguageHighlighterColors.INLAY_DEFAULT,
            editor,
            WithAttributesPresentation.AttributesFlags().withIsDefault(true),
        )
        presentation = DynamicInsetPresentation(
            presentation,
            offsetFromTopProvider
        )
        return presentation
    }
}
