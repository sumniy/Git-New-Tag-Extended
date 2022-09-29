package com.kakao.gitnewtagextended

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitNotificationIdsHolder
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener

object GitNewTagExtendedUtil {

    private const val MAX_TAG_SIZE = 100

    @Throws(VcsException::class)
    fun getAllTags(project: Project, root: VirtualFile): List<String> {
        fetchRemoteTagsBeforeGet(project, root)
        val h = GitLineHandler(project, root, GitCommand.TAG)
        h.addParameters("-l")
        h.addParameters("--sort=-creatordate")
        h.setSilent(true)
        val tags: MutableList<String> = ArrayList()
        h.addLineListener(GitLineHandlerListener { line, outputType ->
            if (outputType !== ProcessOutputTypes.STDOUT) return@GitLineHandlerListener
            if (line.length != 0 && tags.size <= MAX_TAG_SIZE) tags.add(line)
        })
        val result = Git.getInstance().runCommandWithoutCollectingOutput(h)
        result.throwOnError()
        return tags
    }

    private fun fetchRemoteTagsBeforeGet(project: Project, root: VirtualFile) {
        val notifier = VcsNotifier.getInstance(project)
        val git = Git.getInstance()
        val h = GitLineHandler(project, root, GitCommand.FETCH)
        h.addParameters("--tags")
        h.addParameters("-f")
        h.setSilent(true)
        val result = git.runCommandWithoutCollectingOutput(h)
        if (result.success()) {
            notifier.notifySuccess(
                GitNotificationIdsHolder.FETCH_SUCCESS,
                "Fetch tags success",
                ""
            )
        } else {
            notifier.notifyError(
                GitNotificationIdsHolder.FETCH_ERROR,
                "Couldn't fetch tags",
                "Error occurs while fetching tags from remote server"
            )
        }
    }
}