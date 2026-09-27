package yos.music.player.code

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

class AnimatedArtworkLibraryTest
{
    @Test
    fun pickBestStreamUrl_prefersHighestResolutionAvcStream()
    {
        val masterPlaylist = """
            #EXTM3U
            #EXT-X-STREAM-INF:CODECS="hvc1",RESOLUTION=2160x2160,BANDWIDTH=20000000
            hvc-2160.m3u8
            #EXT-X-STREAM-INF:CODECS="avc1",RESOLUTION=486x486,BANDWIDTH=1000000
            avc-486.m3u8
            #EXT-X-STREAM-INF:CODECS="avc1",RESOLUTION=1080x1080,BANDWIDTH=6000000
            avc-1080.m3u8
        """.trimIndent()

        val streamUrl = AnimatedArtworkLibrary.pickBestStreamUrl(
            masterPlaylist,
            "https://example.com/artwork/master.m3u8"
        )

        assertEquals("https://example.com/artwork/avc-1080.m3u8", streamUrl)
    }

    @Test
    fun extractMappedMp4Url_resolvesRelativeMapUri()
    {
        val mediaPlaylist = """
            #EXTM3U
            #EXT-X-MAP:URI="cover-.mp4",BYTERANGE="897@0"
            #EXTINF:3.5,
            cover-.mp4
        """.trimIndent()

        val mp4Url = AnimatedArtworkLibrary.extractMappedMp4Url(
            mediaPlaylist,
            "https://example.com/artwork/video.m3u8"
        )

        assertEquals("https://example.com/artwork/cover-.mp4", mp4Url)
    }

    @Test
    fun animatedArtworkFileName_keepsAlbumNameAndMp4Extension()
    {
        assertEquals("A_B Test.mp4", AnimatedArtworkLibrary.animatedArtworkFileName("A/B Test"))
    }

    @Test
    fun animatedArtworkFile_usesAnimDirectory()
    {
        assertEquals(
            File("/music/album/anim/A_B Test.mp4"),
            AnimatedArtworkLibrary.animatedArtworkFile(File("/music/album"), "A/B Test")
        )
    }

    @Test
    fun animatedArtworkCacheFile_separatesArtistsWithMatchingAlbumNames()
    {
        val cacheDirectory = File("/app/cache/animated_artwork")

        val firstArtwork = AnimatedArtworkLibrary.animatedArtworkCacheFile(cacheDirectory, "First Artist", "Greatest Hits")
        val secondArtwork = AnimatedArtworkLibrary.animatedArtworkCacheFile(cacheDirectory, "Second Artist", "Greatest Hits")

        assertEquals(cacheDirectory, firstArtwork.parentFile)
        assertNotEquals(firstArtwork, secondArtwork)
    }

    @Test
    fun readUrlBytes_rejectsOversizedResponses() = runBlocking {
        try
        {
            AnimatedArtworkLibrary.readUrlBytes(ByteArrayInputStream(ByteArray(5)), 4)
            fail("Expected an oversized response to be rejected")
        }
        catch (_: IOException)
        {
        }
    }
}
