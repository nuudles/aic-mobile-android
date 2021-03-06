package edu.artic.media.ui

import com.fuzz.rx.disposedBy
import edu.artic.media.audio.AudioPrefManager
import edu.artic.media.audio.AudioServiceHook
import edu.artic.viewmodel.NavViewViewModel
import edu.artic.viewmodel.Navigate
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
@author Sameer Dhakal (Fuzz)
 */
class NarrowAudioPlayerViewModel @Inject constructor(
        audioServiceHook: AudioServiceHook,
        private val audioPrefManager: AudioPrefManager
) : NavViewViewModel<NarrowAudioPlayerViewModel.NavigationEndpoint>() {

    /**
     * Used to resume the audio player after user reads the audio tutorial.
     */
    val resumeAudioPlayBack: Subject<Boolean> = PublishSubject.create()

    sealed class NavigationEndpoint {
        object AudioTutorial : NavigationEndpoint()
        object AudioDetails : NavigationEndpoint()
    }


    init {
        audioServiceHook.displayAudioTutorial
                .filter { it }
                .subscribe {
                    navigateTo.onNext(Navigate.Forward(NavigationEndpoint.AudioTutorial))
                }.disposedBy(disposeBag)
    }


    fun userSawAudioTutorial() {
        audioPrefManager.hasSeenAudioTutorial = true
        resumeAudioPlayBack.onNext(true)
        navigateTo.onNext(Navigate.Forward(NavigationEndpoint.AudioDetails))
    }

    fun userClickPlayer() {
        navigateTo.onNext(Navigate.Forward(NavigationEndpoint.AudioDetails))
    }

}