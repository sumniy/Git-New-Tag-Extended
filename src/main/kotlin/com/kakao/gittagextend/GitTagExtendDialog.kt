// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.kakao.gittagextend

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
import kotlinx.coroutines.FlowPreview
import org.jetbrains.annotations.NonNls
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxUI
import javax.swing.text.JTextComponent

@OptIn(FlowPreview::class)
class GitTagExtendDialog(project: Project, roots: List<VirtualFile?>?, defaultRoot: VirtualFile?) :
    DialogWrapper(project, true) {
    private var myPanel: JPanel? = null
    private var myGitRootComboBox: JComboBox<Any>? = null
    private var myCurrentBranch: JLabel? = null
    private var myTagNameTextField: JTextField? = null
    private var myForceCheckBox: JCheckBox? = null
    private var myMessageTextArea: JTextArea? = null
    private var myCommitTextField: JTextField? = null
    private var myValidateButton: JButton? = null
    private var myCommitTextFieldValidator: GitReferenceValidator
    private var myProject: Project
    private var myGit: Git
    private var myNotifier: VcsNotifier
    private var myExistingTags: List<String> = ArrayList()
    private var comboBox1: ComboBox<Any>? = null

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
        myTagNameTextField!!.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                validateFields()
            }
        })
        val ui = comboBox1?.ui as BasicComboBoxUI
        val editorComponent = comboBox1?.editor?.editorComponent as JTextComponent
        editorComponent.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                val inputText = editorComponent.text
                doFilterList(myExistingTags, ".*$inputText.*".toRegex())
                println(editorComponent.text)
//                flow {
//                    emit(1)
//                    kotlinx.coroutines.delay(1000)
//                }.debounce(1000)
            }
        })

        editorComponent.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                super.focusGained(e)
                comboBox1?.showPopup()
            }
        })

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

    private fun doFilterList(targetList: List<String>, regex: Regex) {
        val doFilter = Runnable {
            val filteredList = targetList.filter { regex.matches(it) }
            // 자꾸 selectedItem이 자동으로 설정되는데 이 부분을 제거한 클래스를 만들던가 방법을 찾아보기
            comboBox1?.model = DefaultComboBoxModel(filteredList.toTypedArray())
        }
        SwingUtilities.invokeLater(doFilter)
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return myTagNameTextField
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
            val h = GitLineHandler(myProject, gitRoot, GitCommand.TAG)
            if (hasMessage) {
                h.addParameters("-a")
            }
            if (myForceCheckBox!!.isEnabled && myForceCheckBox!!.isSelected) {
                h.addParameters("-f")
            }
            if (hasMessage) {
                h.addParameters("-F")
                h.addAbsoluteFile(messageFile!!)
            }
            h.addParameters(myTagNameTextField!!.text)
            val `object` = myCommitTextField!!.text.trim { it <= ' ' }
            if (`object`.length != 0) {
                h.addParameters(`object`)
            }
            val result = myGit.runCommand(h)
            if (result.success()) {
                myNotifier.notifySuccess(
                    GitNotificationIdsHolder.TAG_CREATED,
                    myTagNameTextField!!.text,
                    GitBundle.message("git.tag.created.tag.successfully", myTagNameTextField!!.text)
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
        } finally {
            messageFile?.delete()
        }
    }

    private fun validateFields() {
        val text = myTagNameTextField!!.text
        if (myExistingTags.contains(text)) {
            myForceCheckBox!!.isEnabled = true
            if (!myForceCheckBox!!.isSelected) {
                setErrorText(GitBundle.message("tag.error.tag.exists"))
                isOKActionEnabled = false
                return
            }
        } else {
            myForceCheckBox!!.isEnabled = false
            myForceCheckBox!!.isSelected = false
        }
        if (myCommitTextFieldValidator.isInvalid) {
            setErrorText(GitBundle.message("tag.error.invalid.commit"))
            isOKActionEnabled = false
            return
        }
        if (text.length == 0) {
            setErrorText(null)
            isOKActionEnabled = false
            return
        }
        setErrorText(null)
        isOKActionEnabled = true
    }

    private fun fetchTags() {
        try {
            val tags = ProgressManager.getInstance()
                .runProcessWithProgressSynchronously<List<String>, VcsException>(
                    { GitTagExtendUtil.getAllTags(myProject, gitRoot) },
                    GitBundle.message("tag.getting.existing.tags"),
                    false,
                    myProject
                )
            myExistingTags = tags.distinct()
            comboBox1?.model = DefaultComboBoxModel(myExistingTags.toTypedArray())
            comboBox1?.selectedItem = ""
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
            GitTagExtendDialog::class.java
        )
        private val MESSAGE_FILE_PREFIX: @NonNls String? = "git-tag-message-"
        private val MESSAGE_FILE_SUFFIX: @NonNls String? = ".txt"
        private val MESSAGE_FILE_ENCODING: @NonNls String? = CharsetToolkit.UTF8
    }
}