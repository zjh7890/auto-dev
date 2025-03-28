package cc.unitmesh.devti.language.compiler.exec

import cc.unitmesh.devti.devin.InsCommand
import cc.unitmesh.devti.devin.dataprovider.BuiltinCommand
import cc.unitmesh.devti.language.compiler.error.DEVINS_ERROR
import cc.unitmesh.devti.language.compiler.model.LineInfo
import cc.unitmesh.devti.language.psi.DevInUsed
import cc.unitmesh.devti.language.utils.lookupFile
import cc.unitmesh.devti.sketch.ui.patch.writeText
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class WriteInsCommand(val myProject: Project, val argument: String, val content: String, val used: DevInUsed) :
    InsCommand {
    override val commandName: BuiltinCommand = BuiltinCommand.WRITE

    private val pathSeparator = "/"

    override suspend fun execute(): String? {
        val range: LineInfo? = LineInfo.fromString(runReadAction { used.text })
        val filepath = argument.split("#")[0]
        val projectDir = myProject.guessProjectDir() ?: return "$DEVINS_ERROR: Project directory not found"

        val fileExists = runReadAction {
            myProject.lookupFile(filepath) != null
        }

        if (!fileExists) {
            return writeToFile(filepath, projectDir)
        }

        val virtualFile = runReadAction { myProject.lookupFile(filepath) }!!
        val psiFile = runReadAction { PsiManager.getInstance(myProject).findFile(virtualFile) }
            ?: return "$DEVINS_ERROR: File not found: $argument"

        var output: String? = null
        runInEdt {
            FileEditorManager.getInstance(myProject).openFile(virtualFile, true)
            output = executeInsert(psiFile, range, content)
        }
        return output
    }

    private fun writeToFile(filepath: String, projectDir: VirtualFile): String {
        val hasChildPath = filepath.contains(pathSeparator)
        if (!hasChildPath) {
            return createNewContent(projectDir, filepath, content) ?: "$DEVINS_ERROR: Create File failed: $argument"
        }

        val filename = filepath.substringAfterLast(pathSeparator)
        val dirPath = filepath.substringBeforeLast(pathSeparator)

        var currentDir = projectDir
        val pathSegments = dirPath.split(pathSeparator).filter { it.isNotEmpty() }
        var result: String? = null

        runInEdt {
            WriteCommandAction.runWriteCommandAction(myProject) {
                try {
                    for (segment in pathSegments) {
                        val childDir = currentDir.findChild(segment)
                        if (childDir == null) {
                            val newDir = currentDir.createChildDirectory(this, segment)
                            currentDir = newDir
                        } else {
                            currentDir = childDir
                        }
                    }
                    
                    // Create the file in the final directory
                    val name = if (filename.contains(pathSeparator)) filename.substringAfterLast(pathSeparator) else filename
                    if (name.isEmpty()) {
                        result = "$DEVINS_ERROR: File name is empty: $argument"
                        return@runWriteCommandAction
                    }

                    val newFile = currentDir.createChildData(this, name)
                    newFile.writeText(content)
                    result = "Writing to file: $argument"
                } catch (e: Exception) {
                    result = "$DEVINS_ERROR: ${e.message}"
                }
            }
        }

        return result ?: "$DEVINS_ERROR: Operation failed for unknown reason"
    }

    private fun createNewContent(parentDir: VirtualFile, filepath: String, content: String): String? {
        var result: String? = null
        
        runInEdt {
            WriteCommandAction.runWriteCommandAction(myProject) {
                try {
                    val name = if (filepath.contains(pathSeparator)) filepath.substringAfterLast(pathSeparator) else filepath
                    if (name.isEmpty()) {
                        result = "$DEVINS_ERROR: File name is empty: $argument"
                        return@runWriteCommandAction
                    }

                    val newFile = parentDir.createChildData(this, name)
                    newFile.writeText(content)
                    result = "Writing to file: $argument"
                } catch (e: Exception) {
                    result = "$DEVINS_ERROR: ${e.message}"
                }
            }
        }

        return result
    }

    private fun executeInsert(
        psiFile: PsiFile,
        range: LineInfo?,
        content: String
    ): String {
        val document = runReadAction {
            PsiDocumentManager.getInstance(myProject).getDocument(psiFile)
        } ?: return "$DEVINS_ERROR: File not found: $argument"

        val startLine = range?.startLine ?: 0
        val endLine = if (document.lineCount == 0) 1 else range?.endLine ?: document.lineCount

        try {
            val startOffset = document.getLineStartOffset(startLine)
            val endOffset = document.getLineEndOffset(endLine - 1)
            WriteCommandAction.runWriteCommandAction(myProject) {
                document.replaceString(startOffset, endOffset, content)
            }

            return "Writing to file: $argument"
        } catch (e: Exception) {
            return "$DEVINS_ERROR: ${e.message}"
        }
    }
}
