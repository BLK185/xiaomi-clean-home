package io.github.blk185.xiaomicleanhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewArtworkTest {
    @Test public void fullHdIsNotReducedToHalfResolution() {
        assertEquals(1, PreviewArtwork.sample(1920,1080));
        assertEquals(2, PreviewArtwork.sample(3840,2160));
    }
    @Test public void thumbnailsAndPortraitsAreNotFullscreenBackdrops() {
        assertFalse(PreviewArtwork.landscape(3));
        assertFalse(PreviewArtwork.landscape(4));
        assertTrue(PreviewArtwork.landscape(0));
        assertFalse(PreviewArtwork.fullBackdrop(320,180));
        assertFalse(PreviewArtwork.fullBackdrop(800,1200));
        assertTrue(PreviewArtwork.fullBackdrop(1920,1080));
    }
    @Test public void upgradeOnlyKnownPublicYoutubeThumbnailShape() {
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg",PreviewArtwork.youtubeHighResolution("https://i.ytimg.com/vi/abcdefghijk/mqdefault.jpg"));
        String signed="https://example.com/vi/abcdefghijk/mqdefault.jpg?token=private";
        assertEquals(signed,PreviewArtwork.youtubeHighResolution(signed));
        String unknown="https://i.ytimg.com/vi/abcdefghijk/custom.jpg";
        assertEquals(unknown,PreviewArtwork.youtubeHighResolution(unknown));
        assertEquals("https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg",PreviewArtwork.highResolution("https://i.ytimg.com/vi/abcdefghijk/hq720.jpg?sqp=resize&x=1"));
    }
    @Test public void preferActualNetflixTitlesAndPrimeCatalogOverCollectionAndSport() {
        assertTrue(PreviewArtwork.contentPriority("com.netflix.ninja","Collection Netflix pour Halloween","https://example.com/card.jpg")<PreviewArtwork.contentPriority("com.netflix.ninja","An actual series","https://example.com/show.jpg"));
        assertTrue(PreviewArtwork.contentPriority("com.amazon.amazonvideo.livingroom","A vs B","https://example.com/le-target-images-prod/live.jpg")<PreviewArtwork.contentPriority("com.amazon.amazonvideo.livingroom","Movie","https://example.com/pv-target-images/film.jpg"));
        assertEquals(0,PreviewArtwork.contentPriority("org.jellyfin.androidtv","Collection Netflix","content://example/art"));
    }
}
