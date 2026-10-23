package io.github.blk185.xiaomicleanhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class OfficialArtworkTest {
    @Test public void tf1UsesPublishedSignatureForSameAssetRatherThanRewritingDimensions(){
        String low="https://photos.tf1.fr/395/222/show-jpg-abcdef-123abc-0@1x.jpg";
        String high="https://photos.tf1.fr/1280/720/show-jpg-abcdef-456def-0@1x.jpg";
        String other="https://photos.tf1.fr/1920/1080/another-jpg-ffabcd-112233-0@1x.jpg";
        assertEquals(high,OfficialArtwork.tf1("<img src=\""+other+"\"><img src=\""+high+"\">",low));
        assertNull(OfficialArtwork.tf1(other,low));
    }
    @Test public void tf1RejectsWrongHostsPortraitsAndUnsignedGuesses(){
        String low="https://photos.tf1.fr/395/222/show-abcdef-123abc-0@1x.jpg";
        assertNull(OfficialArtwork.tf1("https://evil.example/1280/720/show-abcdef-456def-0@1x.jpg",low));
        assertNull(OfficialArtwork.tf1("https://photos.tf1.fr/1080/1920/show-abcdef-456def-0@1x.jpg",low));
        assertNull(OfficialArtwork.asset("https://photos.tf1.fr.evil.example/395/222/show-abcdef-123abc-0@1x.jpg"));
    }
    @Test public void netflixExtractsOnlyExplicitTitleFields(){
        assertEquals("12345678",OfficialArtwork.netflixId("intent://www.netflix.com/watch/12345678?trackId=11111111#Intent;end"));
        assertEquals("12345678",OfficialArtwork.netflixId("nflx://www.netflix.com/browse?movieid=12345678"));
        assertNull(OfficialArtwork.netflixId("nflx://browse?trackId=12345678"));
    }
    @Test public void netflixPreservesSignedCdnUrlAndDecodesDataWithoutExecutingScripts(){
        String page="\"artwork\":{\"billboard\":{\"small\":\"https:\\x2F\\x2Focc-0.example.nflxso.net\\x2Fdnm\\x2Fexample.jpg\\x3Fr\\x3Dabc\"}}";
        assertEquals("https://occ-0.example.nflxso.net/dnm/example.jpg?r=abc",OfficialArtwork.netflix(page));
        assertNull(OfficialArtwork.netflix("\"artwork\":{\"billboard\":{\"small\":\"https://evil.example/image.jpg\"}}"));
        assertNull(OfficialArtwork.netflix("<script>stealCookies()</script>"));
    }
}
