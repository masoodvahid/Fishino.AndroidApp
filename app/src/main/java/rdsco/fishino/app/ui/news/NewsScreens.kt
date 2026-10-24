package rdsco.fishino.app.ui.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rdsco.fishino.app.R
import rdsco.fishino.app.panel.NewsApi
import rdsco.fishino.app.panel.NewsItem
import rdsco.fishino.app.ui.common.AuthedImage
import rdsco.fishino.app.ui.common.Chip
import rdsco.fishino.app.ui.common.ContentMaxWidth
import rdsco.fishino.app.ui.common.ErrorText
import rdsco.fishino.app.ui.common.HtmlText
import rdsco.fishino.app.ui.common.LoadError
import rdsco.fishino.app.ui.common.Loading
import rdsco.fishino.app.ui.common.PanelCard
import rdsco.fishino.app.ui.common.SectionHeader
import rdsco.fishino.app.ui.common.Tones
import rdsco.fishino.app.ui.home.PagedListContent
import rdsco.fishino.app.ui.home.persianDigits

@Composable
fun NewsListScreen(viewModel: NewsViewModel, onOpen: (Long) -> Unit) {
    PagedListContent(viewModel.list, stringResource(R.string.news_empty)) { item -> NewsImageCard(item, onClick = { onOpen(item.id) }) }
}

@Composable
private fun Meta(item: NewsItem, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(item.typeLabel, if (item.typeKey == "news") Tones.Info else Tones.Warning)
        Text(
            listOfNotNull(item.dateLabel, stringResource(R.string.news_views, item.viewCount)).joinToString(" · ").persianDigits(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NewsDetailScreen(viewModel: NewsViewModel, id: Long) {
    LaunchedEffect(id) { viewModel.detail.load(id) }
    val state by viewModel.detail.state.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val fileError by viewModel.fileError.collectAsStateWithLifecycle()
    var fullImage by rememberSaveable { mutableStateOf<String?>(null) }

    val detail = state.data
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            detail != null -> {
                item {
                    PanelCard {
                        if (detail.item.hasImage) {
                            val path = NewsApi.imagePath(id)
                            AuthedImage(
                                path = path,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).clickable { fullImage = path },
                            )
                        }
                        Text(
                            detail.item.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = if (detail.item.hasImage) 12.dp else 0.dp),
                        )
                        Meta(detail.item, Modifier.padding(top = 6.dp, bottom = 10.dp))
                        HtmlText(detail.contentHtml, Modifier.fillMaxWidth())
                    }
                }

                if (detail.gallery.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.news_gallery)) }
                    item {
                        LazyRow(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(detail.gallery) { (index, name) ->
                                val path = NewsApi.galleryPath(id, index)
                                AuthedImage(
                                    path = path,
                                    contentDescription = name,
                                    modifier = Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)).clickable { fullImage = path },
                                )
                            }
                        }
                    }
                }

                if (detail.attachments.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.news_attachments)) }
                    items(detail.attachments) { attachment ->
                        OutlinedButton(
                            onClick = { viewModel.openAttachment(id, attachment) },
                            enabled = downloading == null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
                        ) {
                            if (downloading == attachment.index) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text(attachment.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item { ErrorText(fileError) }
                }
            }
            state.loading -> item { Loading() }
            else -> item { LoadError(state.error, onRetry = viewModel.detail::reload) }
        }
    }

    fullImage?.let { path ->
        Dialog(onDismissRequest = { fullImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().clickable { fullImage = null }, contentAlignment = Alignment.Center) {
                AuthedImage(path = path, contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            }
        }
    }
}

