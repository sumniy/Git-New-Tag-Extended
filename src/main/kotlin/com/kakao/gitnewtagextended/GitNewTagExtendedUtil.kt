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

    private const val TAG_NAME_KEY = "refname"
    private const val TAG_CREATOR_KEY = "author"
    private const val TAG_CREATOR_DATE_KEY = "creatordate"

    @Throws(VcsException::class)
    fun getAllTags(project: Project, root: VirtualFile): MutableList<GitTagExtended> {
        fetchRemoteTagsBeforeGet(project, root)
        return getTagDataList(project, root)
    }

    private fun getTagDataList(project: Project, root: VirtualFile): MutableList<GitTagExtended> {
        val h = GitLineHandler(project, root, GitCommand.TAG)
        h.addParameters("-l")
        h.addParameters("--sort=-$TAG_CREATOR_DATE_KEY")
        h.addParameters("--format={\"$TAG_NAME_KEY\":\"%($TAG_NAME_KEY:short)\",\"$TAG_CREATOR_KEY\":\"%($TAG_CREATOR_KEY)\",\"$TAG_CREATOR_DATE_KEY\":\"%($TAG_CREATOR_DATE_KEY:relative)\"}")
        h.setSilent(true)

        val tags: MutableList<GitTagExtended> = ArrayList()
        h.addLineListener(GitLineHandlerListener { line, outputType ->
            if (outputType !== ProcessOutputTypes.STDOUT) return@GitLineHandlerListener
            if (line.length != 0 && tags.size <= MAX_TAG_SIZE) tags.add(parse(line, tags.size))
            else if (tags.size >= MAX_TAG_SIZE) return@GitLineHandlerListener
        })

        val result = Git.getInstance().runCommandWithoutCollectingOutput(h)
        result.throwOnError()

        return tags
    }

    private fun parse(jsonString: String, order: Int): GitTagExtended {
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
        val creator = jsonObject[TAG_CREATOR_KEY].toString()
            .trim('"')
            .split(" ")
            .first()

        return GitTagExtended(
            refname = jsonObject[TAG_NAME_KEY].toString().trim('"'),
            creator = creator,
            creatordate = jsonObject[TAG_CREATOR_DATE_KEY].toString().trim('"'),
            order = order
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