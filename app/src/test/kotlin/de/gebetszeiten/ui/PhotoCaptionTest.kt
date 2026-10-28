package de.gebetszeiten.ui

import de.gebetszeiten.R
import de.gebetszeiten.prayer.HeroState
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.PhotoKey
import org.junit.Assert.assertEquals
import org.junit.Test

/** Die Bildunterschrift folgt dem Moment, nicht nur dem Foto: während Zawāl
 *  zeigt das Ẓuhr-Foto, aber die Sonne hat den Zenit noch nicht überschritten. */
class PhotoCaptionTest {

    private fun state(moment: Moment, photo: PhotoKey) =
        HeroState(moment, photo, remaining = null, next = null, makruh = null)

    @Test fun zawalHatEigeneUnterschrift() {
        assertEquals(R.string.photo_caption_zawal, captionResFor(state(Moment.ZAWAL, PhotoKey.ZUHR)))
    }

    @Test fun zuhrBehaeltSeineUnterschrift() {
        assertEquals(R.string.photo_caption_zuhr, captionResFor(state(Moment.DHUHR, PhotoKey.ZUHR)))
    }
}
