package com.raywenderlich.podplay.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.regex.Pattern

internal class RegexThread : Thread("Regex Thread") {
  override fun run() {
    while (true) {
      Pattern.compile("[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\\\.)+(?:[A-Z]{2}|com|org|net|edu|gov|mil|biz|info|mobi|name|aero|asia|jobs|museum)\\\\b")
    }
  }

  init {
    start()
  }
}

class CPUStress {
  fun start() {
    val threads = 10
    val runningTime = 10
    for (i in 0 until threads) {
      RegexThread() // create a new thread
    }
    try {
      Thread.sleep((1000 * runningTime).toLong())
    } catch (e: InterruptedException) {
      e.printStackTrace()
    }
  }

  companion object {
    fun launch() {
      CoroutineScope(Dispatchers.IO).async {
        CPUStress().start()
      }
    }
  }
}
