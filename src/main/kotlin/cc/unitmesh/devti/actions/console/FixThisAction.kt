package cc.unitmesh.devti.actions.console

import cc.unitmesh.devti.AutoDevBundle
import cc.unitmesh.devti.actions.chat.base.ChatBaseAction
import cc.unitmesh.devti.gui.chat.ChatActionType
import cc.unitmesh.devti.gui.sendToChatWindow
import cc.unitmesh.devti.provider.ContextPrompter
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.temporary.error.ErrorDescription
import com.intellij.temporary.error.ErrorMessageProcessor


class FixThisAction : ChatBaseAction() {
    init{
        val presentation = getTemplatePresentation()
        presentation.text = AutoDevBundle.message("settings.autodev.others.fixThis")
    }
    override fun getActionType(): ChatActionType = ChatActionType.FIX_ISSUE
    private val logger = logger<FixThisAction>()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val description: ErrorDescription? = ErrorMessageProcessor.getErrorDescription(event)
        if (description == null) {
            logger.error("Error description is null")
            return
        }

        val prompt = ErrorMessageProcessor.extracted(project, description)
        if (prompt == null) {
            logger.error("Prompt is null, description: $description")
            return
        }

        sendToChatWindow(project, getActionType()) { panel, service ->
            service.handlePromptAndResponse(panel, object : ContextPrompter() {
                override fun displayPrompt(): String = prompt.displayText ?: ""
                override fun requestPrompt(): String = prompt.requestText ?: ""
            }, null, true)
        }
    }
}
