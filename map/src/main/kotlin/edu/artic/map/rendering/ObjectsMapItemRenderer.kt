package edu.artic.map.rendering

import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.fuzz.rx.disposedBy
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.VisibleRegion
import edu.artic.db.daos.ArticObjectDao
import edu.artic.db.models.ArticObject
import edu.artic.image.asRequestObservable
import edu.artic.image.loadWithThumbnail
import edu.artic.map.ArticObjectMarkerGenerator
import edu.artic.map.MapChangeEvent
import edu.artic.map.MapDisplayMode
import edu.artic.map.MapFocus
import edu.artic.map.R
import edu.artic.map.getTourOrderNumberBasedOnDisplayMode
import edu.artic.map.helpers.toLatLng
import edu.artic.map.isCloseEnoughToCenter
import edu.artic.ui.util.asCDNUri
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

class ObjectsMapItemRenderer(private val objectsDao: ArticObjectDao)
    : MapItemRenderer<ArticObject>(useBitmapQueue = true) {

    private val articObjectMarkerGenerator by lazy { ArticObjectMarkerGenerator(context) }

    // The rate we check for changes from the incoming stream. (it can update faster).
    private val visibleRegionSampleRate = 500L

    private val loadingBitmap by lazy {
        BitmapDescriptorFactory.fromBitmap(
                articObjectMarkerGenerator.makeIcon(null, scale = .7f))
    }
    private val scaledDot by lazy {
        BitmapDescriptorFactory.fromBitmap(
                articObjectMarkerGenerator.makeIcon(null, scale = .15f))
    }

    init {
        visibleRegionChanges
                .sample(visibleRegionSampleRate, TimeUnit.MILLISECONDS) // too many events, prevent flooding.
                .withLatestFrom(mapItems, mapChangeEvents)
                .filter { (_, _, mapEvent) -> mapEvent.displayMode !is MapDisplayMode.Tour }
                .toFlowable(BackpressureStrategy.LATEST) // if downstream can't keep up, let's pick latest.
                .subscribeOn(Schedulers.trampoline())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy { (region, mapItems, mapChangeEvent) ->
                    Timber.d("Evaluating Markers For Visible Region Change")
                    mapItems.values.forEach { markerHolder ->
                        adjustVisibleMarkers(markerHolder, region, mapItems, mapChangeEvent)
                    }
                }
                .disposedBy(imageQueueDisposeBag)
    }

    /**
     * This method adjusts all visible [ArticObject] mapitems on screen based on [VisibleRegion] changes.
     *
     * If the marker [isCloseEnoughToCenter], we display a loading indicator and load the image on a separate thread.
     * We shouldn't hold the image in memory, so we must reload the image after it's been collapsed.
     */
    private fun adjustVisibleMarkers(
            markerHolder: MarkerHolder<ArticObject>,
            region: VisibleRegion,
            mapItems: Map<String, MarkerHolder<ArticObject>>,
            mapChangeEvent: MapChangeEvent
    ) {
        val position = getLocationFromItem(markerHolder.item)
        val itemId = getIdFromItem(markerHolder.item)
        val meta = markerHolder.marker.metaData()
                ?: MarkerMetaData(markerHolder.item, loadedBitmap = false, requestDisposable = null)

        if (position.isCloseEnoughToCenter(region.latLngBounds)) {
            if (!meta.loadedBitmap) {
                // show loading while its loading.
                mapItems[itemId]?.marker?.setIcon(loadingBitmap)

                // enqueue replacement and add to marker meta.
                markerHolder.marker.tag = meta.copy(
                        requestDisposable = enqueueBitmapFetch(item = markerHolder.item, mapChangeEvent = mapChangeEvent))
            }
        } else {
            // reset loading state here.
            mapItems[itemId]?.marker?.apply {
                meta.requestDisposable?.let { existing -> imageQueueDisposeBag.remove(existing) }
                tag = meta.copy(loadedBitmap = false, requestDisposable = null)
                setIcon(scaledDot)
            }
        }
    }

    override fun getItems(floor: Int, displayMode: MapDisplayMode): Flowable<List<ArticObject>> = when (displayMode) {
        is MapDisplayMode.CurrentFloor -> objectsDao.getObjectsByFloor(floor = floor)
        is MapDisplayMode.Tour -> objectsDao.getObjectsByIdList(displayMode.tour.tourStops.mapNotNull { it.objectId })
        is MapDisplayMode.Search<*> -> objectsDao.getObjectById((displayMode.item as ArticObject).nid).map { listOf(it) }
    }

    override fun getVisibleMapFocus(displayMode: MapDisplayMode): Set<MapFocus> =
            when (displayMode) {
                is MapDisplayMode.Tour -> MapFocus.values().toSet()
                else -> setOf(MapFocus.Individual)
            }

    override fun getLocationFromItem(item: ArticObject): LatLng = item.toLatLng()

    override fun getIdFromItem(item: ArticObject): String = item.nid

    override fun getFastBitmap(item: ArticObject, displayMode: MapDisplayMode): BitmapDescriptor? =
            loadingBitmap

    override fun getBitmapFetcher(item: ArticObject, displayMode: MapDisplayMode): Observable<BitmapDescriptor>? {
        val imageSize = context.resources.getDimension(R.dimen.artic_object_map_image_size).toInt()
        return Glide.with(context)
                .asBitmap()
                .apply(RequestOptions().disallowHardwareConfig())
                .loadWithThumbnail(
                        item.thumbnailFullPath?.asCDNUri(),
                        // Prefer 'image_url', fall back to 'large image' if necessary.
                        (item.image_url ?: item.largeImageFullPath)?.asCDNUri()
                )
                .asRequestObservable(context,
                        width = imageSize,
                        height = imageSize)
                .map { bitmap ->
                    val order: String? = item.getTourOrderNumberBasedOnDisplayMode(displayMode)
                    BitmapDescriptorFactory.fromBitmap(
                            articObjectMarkerGenerator.makeIcon(bitmap, overlay = order))
                }
    }

    override val zIndex: Float = 2.0f

    override fun getMarkerAlpha(floor: Int, mapDisplayMode: MapDisplayMode, item: ArticObject): Float {
        // on tour, set the alpha depending on current floor.
        return if (mapDisplayMode is MapDisplayMode.Tour) {
            if (item.floor == floor) ALPHA_VISIBLE else ALPHA_DIMMED
        } else {
            ALPHA_VISIBLE
        }
    }
}
