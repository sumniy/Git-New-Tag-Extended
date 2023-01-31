// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.kakao.gitnewtagextended

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import git4idea.GitNotificationIdsHolder
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.ui.GitReferenceValidator
import git4idea.util.GitUIUtil
import org.apache.commons.lang3.StringUtils
import org.jetbrains.annotations.NonNls
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.*
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import kotlin.collections.ArrayList

class GitNewTagExtendedDialog(
    project: Project,
    roots: List<VirtualFile?>?,
    defaultRoot: VirtualFile?
) :
    DialogWrapper(project, true) {
    private var myPanel: JPanel? = null
    private var myGitRootComboBox: JComboBox<Any>? = null
    private var myCurrentBranch: JLabel? = null
    private var myForceCheckBox: JCheckBox? = null
    private var myMessageTextArea: JTextArea? = null
    private var myCommitTextField: JTextField? = null
    private var myValidateButton: JButton? = null
    private var myCommitTextFieldValidator: GitReferenceValidator
    private var myProject: Project
    private var myGit: Git
    private var myNotifier: VcsNotifier
    private var myFilteredTagList: List<GitTagExtended> = ArrayList()
    private var myTagNameComboBox: ComboBox<*>? = null
    private var myTagNameComboBoxTextField: JTextField? = null
    private var myAddTagButton: JButton? = null
    private var myAddedTagList: JList<String>? = null
    private var tagList = DefaultListModel<String>()
    private var currentText: String = ""
    private var existsTagList: List<GitTagExtended> = ArrayList()
    private val gitTagExtendedComparator = GitTagExtendedComparator()

    init {
        title = GitBundle.message("tag.title")
        setOKButtonText(GitBundle.message("tag.button"))
        myProject = project
        myNotifier = VcsNotifier.getInstance(myProject)
        myGit = Git.getInstance()
        GitUIUtil.setupRootChooser(
            myProject,
            roots!!,
            defaultRoot,
            myGitRootComboBox!!,
            myCurrentBranch
        )
        myGitRootComboBox!!.addActionListener {
            fetchTags()
            validateFields()
        }
        fetchTags()

        myTagNameComboBoxTextField = myTagNameComboBox?.editor?.editorComponent as JTextField
        myTagNameComboBoxTextField!!.text = ""
        myTagNameComboBox!!.renderer = GitTagListRenderer()

        val selectTagName = Runnable {
            currentText = myTagNameComboBoxTextField!!.text
        }

        val filterMyTagNameComboBoxModel = Runnable {
            val isTextNotChanged = myTagNameComboBoxTextField!!.text.equals(currentText)
            if (isTextNotChanged) {
                return@Runnable
            }

            val model = myTagNameComboBox!!.model as GitTagExtendedComboBoxModel<GitTagExtended>
            val prevModelSize = model.size
            currentText = myTagNameComboBoxTextField!!.text
            myTagNameComboBox!!.selectedItem = myTagNameComboBoxTextField!!.text

            if (currentText.isEmpty()) {
                model.removeAllElements()
                model.addAll(existsTagList)
            } else {
                val prevTagList = model.objects
                val nextTagList = myFilteredTagList.filter { it.refname.contains(currentText) }

                if (nextTagList.size < model.size) {
                    prevTagList.filter { !nextTagList.contains(it) }
                        .forEach { model.removeElement(it) }
                } else {
                    val addModel = nextTagList.filter { !prevTagList.contains(it) }
                    model.addAll(addModel)
                    model.objects.sortWith(gitTagExtendedComparator)
                }
            }

            myTagNameComboBox!!.selectedItem = myTagNameComboBoxTextField!!.text
            val nextModelSize = model.size
            if ((prevModelSize < 8 || nextModelSize < 8) && prevModelSize != nextModelSize) {
                // update popup ui
                myTagNameComboBox!!.hidePopup()
                myTagNameComboBox!!.showPopup()
            }
        }

        myTagNameComboBoxTextField!!.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                validateFields()
            }
        })

        myTagNameComboBoxTextField!!.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                super.focusGained(e)
                SwingUtilities.invokeLater { myTagNameComboBox!!.showPopup() }
            }
        })

        myTagNameComboBoxTextField!!.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(keyEvent: KeyEvent?) {
                if (keyEvent != null &&
                    myTagNameComboBoxTextField!!.hasFocus() &&
                    !listOf(
                        KeyEvent.VK_UP,
                        KeyEvent.VK_DOWN,
                        KeyEvent.VK_LEFT,
                        KeyEvent.VK_RIGHT
                    ).contains(keyEvent.keyCode)) {
                    filterMyTagNameComboBoxModel.run()
                } else {
                    selectTagName.run()
                }
                super.keyReleased(keyEvent)
            }
        })

        myAddedTagList!!.isVisible = false
        myAddTagButton!!.addActionListener {
            tagList.addElement(myTagNameComboBoxTextField!!.text)
            myAddedTagList!!.model = tagList
            myTagNameComboBoxTextField!!.text = ""
            myTagNameComboBox!!.selectedItem = null
            myAddedTagList!!.isVisible = true
        }

        myCommitTextFieldValidator = GitReferenceValidator(
            project, myGitRootComboBox, myCommitTextField, myValidateButton
        ) { validateFields() }
        myForceCheckBox!!.addActionListener {
            if (myForceCheckBox!!.isEnabled) {
                validateFields()
            }
        }
        init()
        validateFields()
    }


    override fun getPreferredFocusedComponent(): JComponent? {
        return myTagNameComboBoxTextField
    }

    fun runAction() {
        val message = myMessageTextArea!!.text
        val hasMessage = message.trim { it <= ' ' }.length != 0
        val messageFile: File?
        if (hasMessage) {
            try {
                messageFile = FileUtil.createTempFile(
                    MESSAGE_FILE_PREFIX!!, MESSAGE_FILE_SUFFIX
                )
                messageFile.deleteOnExit()
                OutputStreamWriter(
                    FileOutputStream(messageFile),
                    MESSAGE_FILE_ENCODING
                ).use { out -> out.write(message) }
            } catch (ex: IOException) {
                myNotifier.notifyError(
                    GitNotificationIdsHolder.TAG_NOT_CREATED,
                    GitBundle.message("git.tag.could.not.create.tag"),
                    GitBundle.message("tag.error.creating.message.file.message", ex.toString())
                )
                return
            }
        } else {
            messageFile = null
        }
        try {
            if (StringUtils.isNotBlank(myTagNameComboBoxTextField!!.text)) {
                tagList.addElement(myTagNameComboBoxTextField!!.text)
            }

            for (element in tagList.elements()) {
                val h = GitLineHandler(myProject, gitRoot, GitCommand.TAG)
                if (hasMessage) {
                    h.addParameters("-a")
                }

                if (element == myTagNameComboBoxTextField!!.text && myForceCheckBox!!.isEnabled && myForceCheckBox!!.isSelected) {
                    h.addParameters("-f")
                } else if (element != myTagNameComboBoxTextField!!.text) {
                    h.addParameters("-f")
                }
                if (hasMessage) {
                    h.addParameters("-F")
                    h.addAbsoluteFile(messageFile!!)
                }
                h.addParameters(element)
                val `object` = myCommitTextField!!.text.trim { it <= ' ' }
                if (`object`.length != 0) {
                    h.addParameters(`object`)
                }
                val result = myGit.runCommand(h)
                if (result.success()) {
                    myNotifier.notifySuccess(
                        GitNotificationIdsHolder.TAG_CREATED,
                        element,
                        GitBundle.message("git.tag.created.tag.successfully", element)
                    )
                } else {
                    myNotifier.notifyError(
                        GitNotificationIdsHolder.TAG_NOT_CREATED,
                        GitBundle.message("git.tag.could.not.create.tag"),
                        result.errorOutputAsHtmlString,
                        true
                    )
                }
                val repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(
                    gitRoot
                )
                if (repository != null) {
                    repository.repositoryFiles.refreshTagsFiles()
                } else {
                    LOG.error("No repository registered for root: " + gitRoot)
                }
            }
        } finally {
            messageFile?.delete()
        }
    }

    private fun validateFields() {
        val text = myTagNameComboBoxTextField!!.text
        if (myFilteredTagList.map { it.refname }.contains(text)) {
            myForceCheckBox!!.isEnabled = true
            if (!myForceCheckBox!!.isSelected) {
                setErrorText(GitBundle.message("tag.error.tag.exists"))
                isOKActionEnabled = false
                myAddTagButton!!.isEnabled = false
                return
            }
        } else {
            myForceCheckBox!!.isEnabled = false
            myForceCheckBox!!.isSelected = false
        }
        if (myCommitTextFieldValidator.isInvalid) {
            setErrorText(GitBundle.message("tag.error.invalid.commit"))
            isOKActionEnabled = false
            myAddTagButton!!.isEnabled = false
            return
        }
        if (text.isEmpty() && tagList.isEmpty) {
            setErrorText(null)
            isOKActionEnabled = false
            myAddTagButton!!.isEnabled = false
            return
        }
        setErrorText(null)
        isOKActionEnabled = true
        myAddTagButton!!.isEnabled = true
    }

    private fun fetchTags() {
        try {
            val tags = ProgressManager.getInstance()
                .runProcessWithProgressSynchronously<List<GitTagExtended>, VcsException>(
                    { GitNewTagExtendedUtil.getAllTags(myProject, gitRoot) },
                    GitBundle.message("tag.getting.existing.tags"),
                    false,
                    myProject
                )
            myFilteredTagList = tags.distinct()
            existsTagList = tags.distinct()

            myTagNameComboBox?.model = GitTagExtendedComboBoxModel(Vector(myFilteredTagList))
            myTagNameComboBox?.selectedItem = null
        } catch (e: VcsException) {
            GitUIUtil.showOperationError(
                myProject,
                GitBundle.message("tag.getting.existing.tags"),
                e.message
            )
            throw ProcessCanceledException()
        }
    }

    private val gitRoot: VirtualFile
        private get() = myGitRootComboBox!!.selectedItem as VirtualFile

    override fun createCenterPanel(): JComponent? {
        return myPanel
    }

    override fun getDimensionServiceKey(): String? {
        return javaClass.name
    }

    override fun getHelpId(): String? {
        return "reference.VersionControl.Git.TagFiles"
    }

    companion object {
        private val LOG = Logger.getInstance(
            GitNewTagExtendedDialog::class.java
        )
        private val MESSAGE_FILE_PREFIX: @NonNls String? = "git-tag-message-"
        private val MESSAGE_FILE_SUFFIX: @NonNls String? = ".txt"
        private val MESSAGE_FILE_ENCODING: @NonNls String? = CharsetToolkit.UTF8
    }
}