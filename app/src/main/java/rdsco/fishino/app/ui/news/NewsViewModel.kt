package rdsco.fishino.app.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rdsco.fishino.app.R
import rdsco.fishino.app.auth.ApiClient
import rdsco.fishino.app.auth.ApiResult
import rdsco.fishino.app.panel.NewsApi
import rdsco.fishino.app.panel.NewsAttachment
import rdsco.fishino.app.panel.NewsDetail
import rdsco.fishino.app.panel.NewsItem
import rdsco.fishino.app.panel.PanelServices
import rdsco.fishino.app.ui.common.Loader
import rdsco.fishino.app.ui.common.PagedList
import rdsco.fishino.app.ui.login.UiText
import rdsco.fishino.app.ui.login.toUiText

class NewsViewModel(private val services: PanelServices) : ViewModel() {

    val list = PagedList<NewsItem>(viewModelScope, services.authed) { token, page -> services.news.list(token, page) }

    val detail = Loader<Long, NewsDetail>(viewModelScope, services.authed) { token, id -> services.news.detail(token, id) }

    /** Index of the attachment being downloaded, or null. */
    private val _downloading = MutableStateFlow<Int?>(null)
    val downloading: StateFlow<Int?> = _downloading.asStateFlow()

    private val _fileError = MutableStateFlow<UiText?>(null)
    val fileError: StateFlow<UiText?> = _fileError.asStateFlow()

    fun openAttachment(newsId: Long, attachment: NewsAttachment) {
        if (_downloading.value != null) return
        _downloading.value = attachment.index
        _fileError.value = null

        viewModelScope.launch {
            val result = services.authed.call { token -> services.http.download(NewsApi.attachmentPath(newsId, attachment.index), token) }
            _downloading.value = null

            when (result) {
                is ApiResult.Ok -> if (!services.files.open(result.value.bytes, attachment.name, attachment.mime)) {
                    _fileError.value = UiText.Res(R.string.file_no_viewer)
                }
                is ApiResult.Failed -> _fileError.value = if (result.error.code == ApiClient.FILE_TOO_LARGE) {
                    UiText.Res(R.string.file_too_large)
                } else {
                    result.error.toUiText()
                }
            }
        }
    }
}
