/*
 * Copyright (c) 2025, Alex Li (AlexV525)
 */

@file:Suppress("UnstableApiUsage")

package com.alexv525.dart.inlay

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.alexv525.dart.inlay.psi.PsiParameterNameHintCalculator

class DartParameterNameInlayHintsProvider : InlayParameterHintsProvider {
    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        if (element !is DartCallExpression) {
            return emptyList()
        }

        // Only apply hints to .dart files
        val containingFile = element.containingFile
        if (containingFile?.virtualFile?.extension != "dart") {
            return emptyList()
        }

        // Calculate hints specifically for this call expression
        val hints = PsiParameterNameHintCalculator.calculateForCall(element)
        return hints.map { InlayInfo(it.second, it.first) }
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        if (element !is DartCallExpression) {
            return null
        }

        // Only provide hint info for .dart files
        val containingFile = element.containingFile
        if (containingFile?.virtualFile?.extension != "dart") {
            return null
        }

        // Provide information about the function being called
        val functionName = PsiParameterNameHintCalculator.getFunctionName(element)
        return if (functionName != null) {
            HintInfo.MethodInfo(functionName, emptyList())
        } else {
            null
        }
    }

    override fun getDefaultBlackList(): Set<String> {
        // Common method names that don't need parameter hints
        return setOf(
            "print",
            "debugPrint",
            "assert",
            "identical",
            "max",
            "min"
        )
    }
}
