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

package com.yourcompany.podplay.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.yourcompany.podplay.R
import com.yourcompany.podplay.adapter.PodcastListAdapter
import com.yourcompany.podplay.adapter.PodcastListAdapter.PodcastListAdapterListener
import com.yourcompany.podplay.databinding.ActivityPodcastBinding
import com.yourcompany.podplay.repository.ItunesRepo
import com.yourcompany.podplay.repository.PodcastRepo
import com.yourcompany.podplay.service.ItunesService
import com.yourcompany.podplay.service.RssFeedService
import com.yourcompany.podplay.ui.PodcastDetailsFragment.OnPodcastDetailsListener
import com.yourcompany.podplay.viewmodel.PodcastViewModel
import com.yourcompany.podplay.viewmodel.SearchViewModel
import com.yourcompany.podplay.worker.EpisodeUpdateWorker
import com.yourcompany.podplay.worker.GetNewEpisodesWorker
import com.yourcompany.podplay.worker.LoadPodcastsWorker
import com.yourcompany.podplay.worker.SaveNewEpisodesWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PodcastActivity"

class PodcastActivity : AppCompatActivity(), PodcastListAdapterListener,
  OnPodcastDetailsListener {

  private val searchViewModel by viewModels<SearchViewModel>()
  private val podcastViewModel by viewModels<PodcastViewModel>()
  private lateinit var podcastListAdapter: PodcastListAdapter
  private lateinit var searchMenuItem: MenuItem
  private lateinit var databinding: ActivityPodcastBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    databinding = ActivityPodcastBinding.inflate(layoutInflater)
    setContentView(databinding.root)
    setupToolbar()
    setupViewModels()
    updateControls()
    setupPodcastListView()
    handleIntent(intent)
    addBackStackListener()
    scheduleJobs()

    // TODO: Chapter 11 - Prevent unnecessary network calls
    performSearch("")
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "onResume() called.")
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    val inflater = menuInflater
    inflater.inflate(R.menu.menu_search, menu)

    searchMenuItem = menu.findItem(R.id.search_item)
    val searchView = searchMenuItem.actionView as SearchView

    searchMenuItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
      override fun onMenuItemActionExpand(p0: MenuItem?): Boolean {
        databinding.emptyListViewGroup.visibility = View.GONE
        return true
      }

      override fun onMenuItemActionCollapse(p0: MenuItem?): Boolean {
        showSubscribedPodcasts()
        return true
      }
    })

    val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
    searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))

    if (supportFragmentManager.backStackEntryCount > 0) {
      databinding.podcastRecyclerView.visibility = View.INVISIBLE
    }

    if (databinding.podcastRecyclerView.visibility == View.INVISIBLE) {
      searchMenuItem.isVisible = false
    }

    return true
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Log.i(TAG, "onNewIntent() called with intent: ${intent.action}.")
    setIntent(intent)
    handleIntent(intent)
  }


  override fun onShowDetails(podcastSummaryViewData: SearchViewModel.PodcastSummaryViewData) {
    if (podcastSummaryViewData.feedUrl == null) {
      Log.w(
        TAG,
        "podcastSummaryViewData for podcast named '${podcastSummaryViewData.name}' feedUrl is null."
      )
      Toast.makeText(this, getString(R.string.podcast_feed_unavailable_error), Toast.LENGTH_LONG)
        .show();
      return
    } else {
      showProgressBar()
      Log.d(TAG, "Retrieving podcast feed for podcast named '${podcastSummaryViewData.name}'")
      podcastViewModel.viewModelScope.launch(context = Dispatchers.Main) {
        podcastViewModel.getPodcast(podcastSummaryViewData)
        hideProgressBar()
        showDetailsFragment()
      }
    }
  }

  private fun scheduleJobs() {
    val loadPodcasts = OneTimeWorkRequestBuilder<LoadPodcastsWorker>().build()
    val getNewEpisodes = OneTimeWorkRequestBuilder<GetNewEpisodesWorker>().build()
    val saveNewEpisodes = OneTimeWorkRequestBuilder<SaveNewEpisodesWorker>().build()

    WorkManager.getInstance(this)
      .beginWith(loadPodcasts)
      .then(getNewEpisodes)
      .then(saveNewEpisodes)
      .enqueue()
  }

  private fun showSubscribedPodcasts() {
    val podcasts = podcastViewModel.getPodcasts()?.value

    if (podcasts != null) {
      databinding.toolbar.title = getString(R.string.subscribed_podcasts)
      podcastListAdapter.setSearchData(podcasts)

      databinding.emptyListViewGroup.visibility =
        if (podcasts.isEmpty()) View.VISIBLE else View.GONE
    }
  }

  private fun performSearch(term: String) {
    showProgressBar()
    GlobalScope.launch {
      val results = searchViewModel.searchPodcasts(term)
      withContext(Dispatchers.Main) {
        hideProgressBar()
        databinding.toolbar.title = term
        podcastListAdapter.setSearchData(results)
      }
    }
  }

  private fun handleIntent(intent: Intent) {
    val query = intent.getStringExtra(SearchManager.QUERY)
    if (!query.isNullOrBlank()) {
      performSearch(query)
    }

    val podcastFeedUrl = intent.getStringExtra(EpisodeUpdateWorker.EXTRA_FEED_URL)
    if (podcastFeedUrl != null) {
      podcastViewModel.viewModelScope.launch {
        val podcastSummaryViewData = podcastViewModel.setActivePodcast(podcastFeedUrl)
        podcastSummaryViewData?.let { podcastSummaryView -> onShowDetails(podcastSummaryView) }
      }
    }
  }


  private fun setupToolbar() {
    setSupportActionBar(databinding.toolbar)
  }

  private fun setupViewModels() {
    val service = ItunesService.instance
    searchViewModel.iTunesRepo = ItunesRepo(service)
    val rssService = RssFeedService.instance
    podcastViewModel.podcastRepo = PodcastRepo(rssService, podcastViewModel.podcastDao)
  }

  private fun setupPodcastListView() {
    podcastViewModel.getPodcasts()?.observe(this, {
      if (it != null) {
        showSubscribedPodcasts()
      }
    })
  }

  private fun addBackStackListener() {
    supportFragmentManager.addOnBackStackChangedListener {
      if (supportFragmentManager.backStackEntryCount == 0) {
        databinding.podcastRecyclerView.visibility = View.VISIBLE
      }
    }
  }

  private fun updateControls() {
    databinding.podcastRecyclerView.setHasFixedSize(true)

    val layoutManager = LinearLayoutManager(this)
    databinding.podcastRecyclerView.layoutManager = layoutManager

    val dividerItemDecoration = DividerItemDecoration(
      databinding.podcastRecyclerView.context,
      layoutManager.orientation
    )
    databinding.podcastRecyclerView.addItemDecoration(dividerItemDecoration)

    podcastListAdapter = PodcastListAdapter(null, this, this)
    databinding.podcastRecyclerView.adapter = podcastListAdapter

    databinding.searchForPodcastsButton.setOnClickListener {
      searchMenuItem.expandActionView()
    }
  }


  private fun showDetailsFragment() {
    val podcastDetailsFragment = createPodcastDetailsFragment()

    supportFragmentManager.beginTransaction().add(
      R.id.podcastDetailsContainer,
      podcastDetailsFragment, TAG_DETAILS_FRAGMENT
    ).addToBackStack("DetailsFragment").commit()
    databinding.podcastRecyclerView.visibility = View.INVISIBLE
    searchMenuItem.isVisible = false
  }

  private fun showPlayerFragment() {
    val episodePlayerFragment = createEpisodePlayerFragment()

    supportFragmentManager.beginTransaction().replace(
      R.id.podcastDetailsContainer,
      episodePlayerFragment, TAG_PLAYER_FRAGMENT
    ).addToBackStack("PlayerFragment").commit()
    databinding.podcastRecyclerView.visibility = View.INVISIBLE
    searchMenuItem.isVisible = false
  }

  private fun createEpisodePlayerFragment(): EpisodePlayerFragment {

    var episodePlayerFragment = supportFragmentManager.findFragmentByTag(TAG_PLAYER_FRAGMENT) as
        EpisodePlayerFragment?

    if (episodePlayerFragment == null) {
      episodePlayerFragment = EpisodePlayerFragment.newInstance()
    }
    return episodePlayerFragment
  }

  private fun createPodcastDetailsFragment(): PodcastDetailsFragment {
    var podcastDetailsFragment =
      supportFragmentManager.findFragmentByTag(TAG_DETAILS_FRAGMENT) as PodcastDetailsFragment?

    // TODO: Chapter 10 - Disable null check for introducing memory leaks to resolve later
    if (podcastDetailsFragment == null) {
      podcastDetailsFragment = PodcastDetailsFragment.newInstance()
    }

    return podcastDetailsFragment
  }

  private fun showProgressBar() {
    databinding.progressBar.visibility = View.VISIBLE
  }

  private fun hideProgressBar() {
    databinding.progressBar.visibility = View.INVISIBLE
  }

  companion object {
    private const val TAG_DETAILS_FRAGMENT = "DetailsFragment"
    private const val TAG_EPISODE_UPDATE_JOB = "com.yourcompany.podplay.episodes"
    private const val TAG_PLAYER_FRAGMENT = "PlayerFragment"
  }

  override fun onSubscribe() {
    podcastViewModel.saveActivePodcast()
    supportFragmentManager.popBackStack()
  }

  override fun onUnsubscribe() {
    podcastViewModel.deleteActivePodcast()
    supportFragmentManager.popBackStack()
  }

  override fun onShowEpisodePlayer(episodeViewData: PodcastViewModel.EpisodeViewData) {
    podcastViewModel.activeEpisodeViewData = episodeViewData
    showPlayerFragment()
  }
}
