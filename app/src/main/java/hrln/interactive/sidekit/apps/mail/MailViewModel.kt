package hrln.interactive.sidekit.apps.mail

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hrln.interactive.sidekit.R
import hrln.interactive.sidekit.design.action.NoticeKind
import hrln.interactive.sidekit.sdk.ui.UndoWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MailViewModel(
    private val repository: MailRepository,
    val account: MailAccount
) : ViewModel() {
    val storageError = repository.error
    private var sending = false
    val messages: StateFlow<List<MailMessage>?> = repository.messages

    /** The action-bar notice, as a string resource the screen resolves. */
    private val _notice = MutableStateFlow<Int?>(null)
    val notice: StateFlow<Int?> = _notice.asStateFlow()
    private val _noticeKind = MutableStateFlow(NoticeKind.Other)
    val noticeKind: StateFlow<NoticeKind> = _noticeKind.asStateFlow()
    private val _undo = MutableStateFlow<Triple<MailMessage, Int, Long>?>(null)
    val undo: StateFlow<Triple<MailMessage, Int, Long>?> = _undo.asStateFlow()

    /** The five-second offer; pauses with the screen instead of burning while Mail is backgrounded. */
    val undoWindow = UndoWindow(viewModelScope) { _undo.value = null }

    fun saveDraft(id: Long?, to: String, subject: String, body: String) {
        if (messages.value == null) {
            say(R.string.mail_notice_not_ready)
            return
        }
        repository.save(id, to, subject, body, MailFolder.Draft)
        say(R.string.mail_notice_draft_saved, NoticeKind.Success)
    }

    fun send(id: Long?, to: String, subject: String, body: String) {
        if (sending || messages.value == null) {
            say(R.string.mail_notice_busy)
            return
        }
        if (to.isBlank()) {
            say(R.string.mail_notice_add_recipient)
            return
        }
        sending = true
        val message = repository.save(id, to, subject, body, MailFolder.Outbox)
        viewModelScope.launch {
            try {
                when (account.send(message)) {
                    MailSendResult.Sent -> {
                        repository.folder(message.id, MailFolder.Sent)
                        say(R.string.mail_notice_sent, NoticeKind.Success)
                    }

                    MailSendResult.HandedOff -> {
                        repository.folder(message.id, MailFolder.HandedOff)
                        say(R.string.mail_notice_handed_off)
                    }

                    MailSendResult.Unavailable -> say(R.string.mail_notice_no_app)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                say(R.string.mail_notice_send_failed)
            } finally {
                sending = false
            }
        }
    }

    val busy = MutableStateFlow(false)
    fun refresh() {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                repository.replaceInbox(account.inbox())
                say(R.string.mail_notice_inbox_updated, NoticeKind.Success)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                say(R.string.mail_notice_refresh_failed)
            } finally {
                busy.value = false
            }
        }
    }

    fun delete(id: Long) {
        val (message, index) = repository.delete(id) ?: return
        _undo.value = Triple(message, index, System.nanoTime())
        undoWindow.start()
    }

    fun undo() {
        val value = _undo.value ?: return
        undoWindow.cancel()
        repository.restore(value.first, value.second)
        _undo.value = null
    }

    /** Leaving the list finalises the delete instead of offering Undo on the next screen. */
    fun commitUndo() {
        undoWindow.cancel()
        _undo.value = null
    }

    fun pauseUndo() = undoWindow.pause()

    fun resumeUndo() = undoWindow.resume()

    fun say(@StringRes text: Int, kind: NoticeKind = NoticeKind.Other) {
        _notice.value = text
        _noticeKind.value = kind
    }

    fun dismissNotice() {
        _notice.value = null
    }
}
