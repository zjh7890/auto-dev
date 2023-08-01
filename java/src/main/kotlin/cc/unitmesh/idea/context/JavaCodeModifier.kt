package cc.unitmesh.idea.context

import cc.unitmesh.devti.context.builder.CodeModifier
import cc.unitmesh.idea.service.JavaWriteTestService
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager

open class JavaCodeModifier : CodeModifier {
    companion object {
        val log = logger<JavaWriteTestService>()
    }

    override fun isApplicable(language: Language): Boolean {
        return language is JavaLanguage
    }

    fun lookupFile(
        project: Project,
        sourceFile: VirtualFile
    ) = PsiManager.getInstance(project).findFile(sourceFile) as PsiJavaFile

    override fun insertTestCode(sourceFile: VirtualFile, project: Project, code: String): Boolean {
        log.info("methodCode: $code")
        if (!code.contains("@Test")) {
            log.error("methodCode does not contain @Test annotation: $code")
            return false
        }

        if (code.startsWith("import") && code.contains("class ")) {
            return insertClass(sourceFile, project, code)
        }

        insertMethod(sourceFile, project, code)
        return true
    }

    override fun insertMethod(sourceFile: VirtualFile, project: Project, code: String): Boolean {
        ApplicationManager.getApplication().invokeLater {
            val rootElement = runReadAction {
                val psiJavaFile = lookupFile(project, sourceFile)
                val psiClass = psiJavaFile.classes.firstOrNull()
                if (psiClass == null) {
                    log.error("Failed to find PsiClass in the source file: $psiJavaFile, code: $code")
                    return@runReadAction null
                }

                return@runReadAction psiClass
            } ?: return@invokeLater

            val psiElementFactory = PsiElementFactory.getInstance(project)

            val newTestMethod = psiElementFactory.createMethodFromText(code, rootElement)
            if (rootElement.findMethodsByName(newTestMethod.name, false).isNotEmpty()) {
                log.error("Method already exists in the class: ${newTestMethod.name}")
            }

            log.info("newTestMethod: ${newTestMethod.text}")

            WriteCommandAction.runWriteCommandAction(project) {
                val lastMethod = rootElement.methods.lastOrNull()
                val lastMethodEndOffset = lastMethod?.textRange?.endOffset ?: 0

                val document = PsiDocumentManager.getInstance(project).getDocument(rootElement.containingFile)
                // insert new line with indent before the new method
                document?.insertString(lastMethodEndOffset, "\n    ")
                document?.insertString(lastMethodEndOffset, newTestMethod.text)
            }

            project.guessProjectDir()?.refresh(true, true)
        }

        return true
    }

    override fun insertClass(sourceFile: VirtualFile, project: Project, code: String): Boolean {
        log.info("start insertClassCode: $code")
        WriteCommandAction.runWriteCommandAction(project) {
            val psiFile = lookupFile(project, sourceFile)
            val document = psiFile.viewProvider.document!!
            document.insertString(document.textLength, code)
        }

        return true
    }
}