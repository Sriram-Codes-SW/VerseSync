package dev.sriramcodessw.versesync.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ShareViewModelFactory(private val verseViewModel: VerseViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShareViewModel(verseViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
