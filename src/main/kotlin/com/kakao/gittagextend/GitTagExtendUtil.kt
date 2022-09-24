package com.kakao.gittagextend

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener

object GitTagExtendUtil {

    private const val MAX_TAG_SIZE = 100

    @Throws(VcsException::class)
    fun getAllTags(project: Project, root: VirtualFile): List<String> {
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
}