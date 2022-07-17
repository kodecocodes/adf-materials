/*
 * Copyright (c) 2022 Razeware LLC
 *   
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *   
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *   
 *   Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 *   distribute, sublicense, create a derivative work, and/or sell copies of the
 *   Software in any work that is designed, intended, or marketed for pedagogical or
 *   instructional purposes related to programming, coding, application development,
 *   or information technology.  Permission for such use, copying, modification,
 *   merger, publication, distribution, sublicensing, creation of derivative works,
 *   or sale is expressly withheld.
 *   
 *   This project and source code may use libraries or frameworks that are
 *   released under various Open-Source licenses. Use of those libraries and
 *   frameworks are governed by their own individual licenses.
 *   
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */

package com.yourcompany.podplay.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.yourcompany.podplay.db.PodPlayDatabase
import com.yourcompany.podplay.db.PodcastDao
import com.yourcompany.podplay.model.Episode
import com.yourcompany.podplay.model.Podcast
import com.yourcompany.podplay.repository.PodcastRepo
import com.yourcompany.podplay.util.DateUtils
import com.yourcompany.podplay.viewmodel.SearchViewModel.PodcastSummaryViewData
import java.util.*
import android.util.Log
import kotlin.system.measureTimeMillis

private const val TAG = "PodcastViewModel"

class PodcastViewModel(application: Application) : AndroidViewModel(application) {

  var podcastRepo: PodcastRepo? = null
  private val _podcastLiveData = MutableLiveData<PodcastViewData?>()
  private var livePodcastSummaryData: LiveData<List<PodcastSummaryViewData>>? = null
  val podcastLiveData: LiveData<PodcastViewData?> = _podcastLiveData
  var activeEpisodeViewData: EpisodeViewData? = null

  val podcastDao: PodcastDao = PodPlayDatabase
    .getInstance(application, viewModelScope)
    .podcastDao()

  private var activePodcast: Podcast? = null

  suspend fun setActivePodcast(feedUrl: String): PodcastSummaryViewData? {
    val repo = podcastRepo ?: return null
    val podcast = repo.getPodcast(feedUrl)
    return if (podcast == null) {
      null
    } else {
      _podcastLiveData.value = podcastToPodcastView(podcast)
      activePodcast = podcast
      podcastToSummaryView(podcast)
    }
  }

  suspend fun getPodcast(podcastSummaryViewData: PodcastSummaryViewData) {
    podcastSummaryViewData.feedUrl?.let { url ->
      var podcast: Podcast?
      Log.d(
        TAG,
        "Calling getPodcast for podcast named ${podcastSummaryViewData.name}, recording time."
      )
      val timeInMillis = measureTimeMillis {
        podcast = podcastRepo?.getPodcast(url)
      }

      podcast?.let {
        Log.d(
          TAG,
          "getPodcast() for podcast named ${podcastSummaryViewData.name} completed, time taken: $timeInMillis milliseconds."
        )
        it.feedTitle = podcastSummaryViewData.name ?: ""
        it.imageUrl = podcastSummaryViewData.imageUrl ?: ""
        _podcastLiveData.value = podcastToPodcastView(it)
        activePodcast = it
      } ?: run {
        _podcastLiveData.value = null
      }
    } ?: run {
      _podcastLiveData.value = null
    }
  }

  fun getPodcasts(): LiveData<List<PodcastSummaryViewData>>? {
    val repo = podcastRepo ?: return null
    if (livePodcastSummaryData == null) {
      val liveData = repo.getAll()
      livePodcastSummaryData = Transformations.map(liveData) { podcastList ->
        podcastList.map { podcast ->
          podcastToSummaryView(podcast)
        }
      }
    }

    return livePodcastSummaryData
  }

  fun saveActivePodcast() {
    val repo = podcastRepo ?: return
    activePodcast?.let {
      repo.save(it)
    }
  }

  private fun podcastToPodcastView(podcast: Podcast): PodcastViewData {
    return PodcastViewData(
      podcast.id != null,
      podcast.feedTitle,
      podcast.feedUrl,
      podcast.feedDesc,
      podcast.imageUrl,
      episodesToEpisodesView(podcast.episodes)
    )
  }

  private fun podcastToSummaryView(podcast: Podcast):
      PodcastSummaryViewData {
    return PodcastSummaryViewData(
      podcast.feedTitle,
      DateUtils.dateToShortDate(podcast.lastUpdated),
      podcast.imageUrl,
      podcast.feedUrl
    )
  }

  private fun episodesToEpisodesView(episodes: List<Episode>): List<EpisodeViewData> {
    return episodes.map {
      val isVideo = it.mimeType.startsWith("video")
      EpisodeViewData(
        it.guid,
        it.title,
        it.description,
        it.mediaUrl,
        it.releaseDate,
        it.duration,
        isVideo
      )
    }
  }

  fun deleteActivePodcast() {
    val repo = podcastRepo ?: return
    activePodcast?.let {
      repo.delete(it)
    }
  }

  data class PodcastViewData(
    var subscribed: Boolean = false,
    var feedTitle: String? = "",
    var feedUrl: String? = "",
    var feedDesc: String? = "",
    var imageUrl: String? = "",
    var episodes: List<EpisodeViewData>
  )

  data class EpisodeViewData(
    var guid: String? = "",
    var title: String? = "",
    var description: String? = "",
    var mediaUrl: String? = "",
    var releaseDate: Date? = null,
    var duration: String? = "",
    var isVideo: Boolean = false
  )
}
