package com.kakao.gitnewtagextended

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.actions.GitRepositoryAction
import git4idea.i18n.GitBundle

class GitNewTagExtendedAction : GitRepositoryAction() {
    /**
     * {@inheritDoc}
     */
    //  protected String getActionName() {
    //    return GitBundle.message("tag.action.name");
    //  }
    override fun getActionName(): String {
        return "Git New Tag Extended"
    }

    /**
     * {@inheritDoc}
     */
    override fun perform(
        project: Project,
        gitRoots: List<VirtualFile>,
        defaultRoot: VirtualFile
    ) {
        val d = GitNewTagExtendedDialog(project, gitRoots, defaultRoot)
        if (d.showAndGet()) {
            object : Task.Modal(project, GitBundle.message("tag.progress.title"), true) {
                override fun run(indicator: ProgressIndicator) {
                    d.runAction()
                }
            }.queue()
        }
    }
}