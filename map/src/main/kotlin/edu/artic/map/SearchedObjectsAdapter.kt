package edu.artic.map

import android.view.View
import com.bumptech.glide.Glide
import com.fuzz.rx.bindToMain
import com.fuzz.rx.defaultThrottle
import com.fuzz.rx.disposedBy
import com.fuzz.rx.filterValue
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.view.visibility
import com.jakewharton.rxbinding2.widget.text
import com.jakewharton.rxbinding2.widget.textRes
import edu.artic.adapter.AutoHolderRecyclerViewAdapter
import edu.artic.adapter.BaseViewHolder
import edu.artic.image.GlideApp
import edu.artic.media.audio.AudioPlayerService
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.layout_amenity_search_cell.view.*
import kotlinx.android.synthetic.main.layout_artwork_search_cell.view.*
import kotlinx.android.synthetic.main.layout_exhibition_search_cell.view.*

/**
@author Sameer Dhakal (Fuzz)
 */
class SearchedObjectsAdapter : AutoHolderRecyclerViewAdapter<SearchObjectBaseViewModel>() {

    override fun View.onBindView(item: SearchObjectBaseViewModel, position: Int) {
        when (item) {
            is DiningAnnotationViewModel -> {
                bindDiningAnnotationView(item, this)
            }
            is AnnotationViewModel -> {
                item.title
                        .bindToMain(amenityType.text())
                        .disposedBy(item.viewDisposeBag)

                item.description
                        .bindToMain(amenityDetails.text())
                        .disposedBy(item.viewDisposeBag)
            }
            is ExhibitionViewModel -> {
                bindExhibitionView(item, this)
            }
            is ArtworkViewModel -> {
                bindArtworkView(item, this)
            }
        }
    }

    fun bindDiningAnnotationView(item: DiningAnnotationViewModel, view: View) {
        item.imageUrl
                .map { it.isNotEmpty() }
                .bindToMain(view.amenityImage.visibility())
                .disposedBy(item.viewDisposeBag)

        item.imageUrl
                .filter { it.isNotEmpty() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Glide.with(view)
                            .load(it)
                            .into(view.amenityImage)
                }
                .disposedBy(item.viewDisposeBag)

        item.title
                .bindToMain(view.amenityType.text())
                .disposedBy(item.viewDisposeBag)

        item.description
                .bindToMain(view.amenityDetails.text())
                .disposedBy(item.viewDisposeBag)

        view.amenitySpace.visibility = View.GONE
    }

    fun bindExhibitionView(item: ExhibitionViewModel, view: View) {
        item.imageUrl
                .filter { it.isNotEmpty() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    view.exhibitionImage.post {
                        view.exhibitionImage.post {
                            GlideApp.with(view)
                                    .load("$it?w=${view.exhibitionImage.measuredWidth}&h=${view.exhibitionImage.measuredWidth}")
                                    .placeholder(R.color.placeholderBackground)
                                    .error(R.drawable.placeholder_thumb)
                                    .into(view.exhibitionImage)
                        }
                    }
                }
                .disposedBy(item.viewDisposeBag)
        item.title
                .bindToMain(view.exhibitionTitle.text())
                .disposedBy(item.viewDisposeBag)

        val galleryTitle = item.galleryTitle
                .subscribeOn(Schedulers.io())
                .share()

        galleryTitle
                .filterValue()
                .bindToMain(view.exhibitionGalleryTitle.text())
                .disposedBy(item.viewDisposeBag)

        galleryTitle
                .filter { it.value == null }
                .switchMap { item.floor }
                .bindToMain(view.exhibitionGalleryTitle.textRes())
                .disposedBy(item.viewDisposeBag)
    }

    fun bindArtworkView(item: ArtworkViewModel, view: View) {
        item.imageUrl
                .filter { it.isNotEmpty() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    GlideApp.with(view)
                            .load(it)
                            .placeholder(R.color.placeholderBackground)
                            .error(R.drawable.placeholder_thumb)
                            .into(view.objectImage)
                }
                .disposedBy(item.viewDisposeBag)

        item.artworkTitle
                .bindToMain(view.objectTitle.text())
                .disposedBy(item.viewDisposeBag)

        item.artistName
                .bindToMain(view.artist.text())
                .disposedBy(item.viewDisposeBag)

        item.artistName
                .map {
                    it.isNotEmpty()
                }
                .bindToMain(view.artist.visibility(View.GONE))
                .disposedBy(item.viewDisposeBag)

        item.gallery
                .map {
                    view.resources.getString(R.string.search_gallery_number, it)
                }
                .bindToMain(view.gallery.text())
                .disposedBy(item.viewDisposeBag)

        item.objectType
                .bindToMain(view.objectType.textRes())
                .disposedBy(item.viewDisposeBag)

        view.playCurrent.clicks()
                .defaultThrottle()
                .subscribe {
                    item.playAudioTranslation()
                }.disposedBy(item.viewDisposeBag)

        view.pauseCurrent.clicks()
                .defaultThrottle()
                .subscribe {
                    item.pauseAudioTranslation()
                }.disposedBy(item.viewDisposeBag)

        item.playState.subscribe { playBackState ->
            when (playBackState) {
                is AudioPlayerService.PlayBackState.Playing -> {
                    view.post {
                        view.playCurrent.visibility = View.INVISIBLE
                        view.pauseCurrent.visibility = View.VISIBLE
                    }
                }
                is AudioPlayerService.PlayBackState.Paused -> {
                    view.playCurrent.visibility = View.VISIBLE
                    view.pauseCurrent.visibility = View.INVISIBLE
                }
                is AudioPlayerService.PlayBackState.Stopped -> {
                    view.playCurrent.visibility = View.VISIBLE
                    view.pauseCurrent.visibility = View.INVISIBLE
                }
            }
        }.disposedBy(item.viewDisposeBag)

        item.hasAudio
                .bindToMain(view.playCurrent.visibility())
                .disposedBy(item.viewDisposeBag)
    }

    override fun getLayoutResId(position: Int): Int {
        val item = getItem(position)
        return when (item) {
            is DiningAnnotationViewModel -> R.layout.layout_amenity_search_cell
            is AnnotationViewModel -> R.layout.layout_amenity_search_cell
            is ArtworkViewModel -> R.layout.layout_artwork_search_cell
            is ExhibitionViewModel -> R.layout.layout_exhibition_search_cell
            else -> {
                0
                /* should never reach here*/
            }
        }
    }

    override fun onItemViewHolderRecycled(holder: BaseViewHolder, position: Int) {
        super.onItemViewHolderRecycled(holder, position)
        getItemOrNull(position)?.apply {
            cleanup()
        }
    }
}