package org.jetbrains.kotlinx.libs.api.watchdog.fir

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.diagnostics.LightTreePositioningStrategies
import org.jetbrains.kotlin.diagnostics.LightTreePositioningStrategy
import org.jetbrains.kotlin.diagnostics.PositioningStrategies
import org.jetbrains.kotlin.diagnostics.PositioningStrategy
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.markElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.util.getChildren

/**
 * Marks a named callable's name, an explicit constructor's `constructor` keyword, or the class
 * name that stands in for an implicit primary constructor's missing keyword.
 */
internal val CALLABLE_NAME_OR_CONSTRUCTOR_KEYWORD = SourceElementPositioningStrategy(
    lightTreeStrategy = object : LightTreePositioningStrategy() {
        override fun mark(
            node: LighterASTNode,
            startOffset: Int,
            endOffset: Int,
            tree: FlyweightCapableTreeStructure<LighterASTNode>,
        ): List<TextRange> {
            if (node.tokenType != KtNodeTypes.PRIMARY_CONSTRUCTOR &&
                node.tokenType != KtNodeTypes.SECONDARY_CONSTRUCTOR
            ) {
                return LightTreePositioningStrategies.NAME_IDENTIFIER.mark(node, startOffset, endOffset, tree)
            }

            val target = node.descendant(KtTokens.CONSTRUCTOR_KEYWORD, tree)
                ?: node.ancestor(KtNodeTypes.CLASS, tree)?.child(KtTokens.IDENTIFIER, tree)
                ?: return LightTreePositioningStrategies.NAME_IDENTIFIER.mark(node, startOffset, endOffset, tree)
            return markElement(target, startOffset, endOffset, tree, node)
        }
    },
    psiStrategy = object : PositioningStrategy<PsiElement>() {
        override fun mark(element: PsiElement): List<TextRange> {
            if (element !is KtConstructor<*>) {
                return PositioningStrategies.NAME_IDENTIFIER.mark(element)
            }

            val target = element.getConstructorKeyword()
                ?: element.getContainingClassOrObject().nameIdentifier
                ?: element
            return markElement(target)
        }
    },
)

private fun LighterASTNode.child(
    tokenType: IElementType,
    tree: FlyweightCapableTreeStructure<LighterASTNode>,
): LighterASTNode? = getChildren(tree).firstOrNull { it.tokenType == tokenType }

private fun LighterASTNode.descendant(
    tokenType: IElementType,
    tree: FlyweightCapableTreeStructure<LighterASTNode>,
): LighterASTNode? {
    for (child in getChildren(tree)) {
        if (child.tokenType == tokenType) return child
        child.descendant(tokenType, tree)?.let { return it }
    }
    return null
}

private fun LighterASTNode.ancestor(
    tokenType: IElementType,
    tree: FlyweightCapableTreeStructure<LighterASTNode>,
): LighterASTNode? {
    var current = tree.getParent(this)
    while (current != null) {
        if (current.tokenType == tokenType) return current
        current = tree.getParent(current)
    }
    return null
}
