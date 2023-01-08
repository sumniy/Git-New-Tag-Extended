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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object GitNewTagExtendedUtil {

    private const val MAX_TAG_SIZE = 100

    @Throws(VcsException::class)
    fun getAllTags(project: Project, root: VirtualFile): MutableList<GitTagExtended> {
        fetchRemoteTagsBeforeGet(project, root)
        return getTagDataList(project, root)
    }

    private fun getTagDataList(project: Project, root: VirtualFile): MutableList<GitTagExtended> {
        val h = GitLineHandler(project, root, GitCommand.TAG)
        h.addParameters("-l")
        h.addParameters("--sort=-creatordate")
        h.addParameters("--format={\"refname\":\"%(refname:short)\",\"creator\":\"%(creator)\",\"creatordate\":\"%(creatordate:relative)\"}")
        h.setSilent(true)

        val tags: MutableList<GitTagExtended> = ArrayList()
        h.addLineListener(GitLineHandlerListener { line, outputType ->
            if (outputType !== ProcessOutputTypes.STDOUT) return@GitLineHandlerListener
            if (line.length != 0 && tags.size <= MAX_TAG_SIZE) tags.add(parse(line))
            else if (tags.size >= MAX_TAG_SIZE) return@GitLineHandlerListener
        })

        val result = Git.getInstance().runCommandWithoutCollectingOutput(h)
        result.throwOnError()

        return tags
    }

    private fun parse(jsonString: String): GitTagExtended {
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
        val creator = jsonObject["creator"].toString()
            .trim('"')
            .split(" ")
            .first()

        return GitTagExtended(
            refname = jsonObject["refname"].toString().trim('"'),
            creator = creator,
            creatordate = jsonObject["creatordate"].toString().trim('"')
        )
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