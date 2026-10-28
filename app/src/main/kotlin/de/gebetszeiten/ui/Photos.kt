package de.gebetszeiten.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.gebetszeiten.R
import de.gebetszeiten.prayer.HeroState
import de.gebetszeiten.prayer.Moment
import de.gebetszeiten.prayer.PhotoKey

/** Das gebündelte Titelbild (Unsplash-Platzhalter) zu einem Abschnitt des Tages. */
@DrawableRes
fun photoRes(key: PhotoKey): Int = when (key) {
    PhotoKey.FAJR -> R.drawable.photo_fajr
    PhotoKey.SHURUQ -> R.drawable.photo_shuruq
    PhotoKey.ZUHR -> R.drawable.photo_zuhr
    PhotoKey.ASR -> R.drawable.photo_asr
    PhotoKey.MAGHRIB -> R.drawable.photo_maghrib
    PhotoKey.ISHA -> R.drawable.photo_isha
}

/** Die Bildunterschrift: ein Satz dazu, was am Himmel gerade geschieht. */
@StringRes
fun photoCaptionRes(key: PhotoKey): Int = when (key) {
    PhotoKey.FAJR -> R.string.photo_caption_fajr
    PhotoKey.SHURUQ -> R.string.photo_caption_shuruq
    PhotoKey.ZUHR -> R.string.photo_caption_zuhr
    PhotoKey.ASR -> R.string.photo_caption_asr
    PhotoKey.MAGHRIB -> R.string.photo_caption_maghrib
    PhotoKey.ISHA -> R.string.photo_caption_isha
}

/** Die Unterschrift auf „Heute“: während Zawāl zeigt das Ẓuhr-Foto, aber die
 *  Sonne hat den Zenit noch nicht überschritten, daher ein eigener Satz. */
@StringRes
fun captionResFor(state: HeroState): Int =
    if (state.moment == Moment.ZAWAL) R.string.photo_caption_zawal else photoCaptionRes(state.photo)

/** Titel des Abschnitts (Heute und Widget): Šurūq, Ḍuḥā und Zawāl haben keine Gebetszeile, aber einen Namen. */
@StringRes
internal fun momentLabelRes(moment: Moment): Int = when (moment) {
    Moment.FAJR -> R.string.prayer_fajr
    Moment.SHURUQ -> R.string.moment_shuruq
    Moment.DUHA -> R.string.nafl_duha
    Moment.ZAWAL -> R.string.moment_zawal
    Moment.DHUHR -> R.string.prayer_dhuhr
    Moment.ASR -> R.string.prayer_asr
    Moment.MAGHRIB -> R.string.prayer_maghrib
    Moment.ISHA -> R.string.prayer_isha
}

/** Die TalkBack-Beschreibung: sagt schlicht, was auf dem Foto zu sehen ist. */
@StringRes
fun photoDescriptionRes(key: PhotoKey): Int = when (key) {
    PhotoKey.FAJR -> R.string.photo_description_fajr
    PhotoKey.SHURUQ -> R.string.photo_description_shuruq
    PhotoKey.ZUHR -> R.string.photo_description_zuhr
    PhotoKey.ASR -> R.string.photo_description_asr
    PhotoKey.MAGHRIB -> R.string.photo_description_maghrib
    PhotoKey.ISHA -> R.string.photo_description_isha
}

/** Wer das Foto gemacht hat und wo es auf Unsplash steht. */
data class PhotoCredit(val key: PhotoKey, val photographer: String, val unsplashId: String)

val PHOTO_CREDITS: List<PhotoCredit> = listOf(
    PhotoCredit(PhotoKey.FAJR, "Luke Moss", "pbuMoKhf07U"),
    PhotoCredit(PhotoKey.SHURUQ, "Jan Canty", "KO8D3dJsfpQ"),
    PhotoCredit(PhotoKey.ZUHR, "Agata Create", "kGXST_-n36w"),
    PhotoCredit(PhotoKey.ASR, "Shaun Danny", "KhyzfY9h4hc"),
    PhotoCredit(PhotoKey.MAGHRIB, "Vanburn Gonsalves", "x1FCSyl6GJU"),
    PhotoCredit(PhotoKey.ISHA, "Olena Bohovyk", "Cq5NaI0yKBE"),
)
