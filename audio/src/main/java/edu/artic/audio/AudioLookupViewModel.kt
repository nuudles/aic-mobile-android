package edu.artic.audio

import com.fuzz.rx.disposedBy
import edu.artic.audio.NumberPadElement.*
import edu.artic.db.daos.ArticObjectDao
import edu.artic.db.models.ArticObject
import edu.artic.viewmodel.BaseViewModel
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

/**
 * This class provides important audio-related logic to an [AudioLookupFragment].
 *
 * For the ViewModel shown for details about a single audio file, check out
 * [AudioDetailsViewModel] instead.
 *
 * For lookup, we always query [ArticObject.objectSelectorNumber].
 */
class AudioLookupViewModel @Inject constructor(objectLookupDao: ArticObjectDao) : BaseViewModel() {

    val adapterClicks: Subject<NumberPadElement> = PublishSubject.create()

    /**
     * New requests for audio lookup. Each entry to this will trigger a response,
     * published on [lookupResults].
     */
    val lookupRequests: Subject<String> = PublishSubject.create()

    /**
     * Responses to search queries [passed in][io.reactivex.Observer.onNext] to
     * [lookupRequests].
     */
    val lookupResults: Subject<LookupResult> = PublishSubject.create()

    /**
     * See [NumberPadAdapter] for details on all this.
     */
    val preferredNumberPadElements: Subject<List<NumberPadElement>> = BehaviorSubject.createDefault(listOf(
            Numeric("1"),
            Numeric("2"),
            Numeric("3"),
            Numeric("4"),
            Numeric("5"),
            Numeric("6"),
            Numeric("7"),
            Numeric("8"),
            Numeric("9"),
            DeleteBack,
            // NB: Due to a bug in the ideal_sans_medium font files, the 0 and o look very similar. This is a zero.
            Numeric("0"),
            GoSearch
    ))

    init {
        // This connects 'lookupRequests' to 'lookupResults' via an IO query.
        lookupRequests
                .observeOn(Schedulers.io())
                .subscribeBy {
                    val foundObject = objectLookupDao.getObjectBySelectorNumber(it)

                    val result = if (foundObject == null) {
                        LookupResult.NotFound(it)
                    } else {
                        LookupResult.FoundAudio(foundObject)
                    }

                    lookupResults.onNext(result)

                }.disposedBy(disposeBag)
    }

}

/**
 * Constant class for the asynchronous response to looking for an
 * [ArticObject] by [selector id][ArticObject.objectSelectorNumber].
 *
 * Two known subclasses:
 * * [FoundAudio]
 * * [NotFound]
 */
sealed class LookupResult {

    /**
     * Audio was found! You can pass [hostObject] to the
     * [AudioPlayerService][edu.artic.media.audio.AudioPlayerService]
     * if you want to play it.
     *
     * NB: [hostObject] can be safely assumed to contain at least one
     * [ArticAudioFile][edu.artic.db.models.ArticAudioFile].
     *
     * @see AudioLookupViewModel.lookupRequests
     */
    class FoundAudio(val hostObject: ArticObject) : LookupResult()

    /**
     * The requested id was found.
     *
     * TODO: Add analytics event indicating that this query was not found.
     */
    class NotFound(val searchQuery: String) : LookupResult()
}