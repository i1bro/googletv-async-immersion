package com.neilturner.aerialviews.ui.toposcan

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VideoColourPolicyTest {
    @Test
    fun `SDR and untagged video keep the effect`() {
        assertFalse(VideoColourPolicy.requiresStandardPlayer(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).build()))
        assertFalse(VideoColourPolicy.requiresStandardPlayer(format(C.COLOR_TRANSFER_SDR, C.COLOR_SPACE_BT709)))
    }

    @Test
    fun `HDR10 uses the effect while HLG Dolby Vision and other wide colour bypass`() {
        assertFalse(VideoColourPolicy.requiresStandardPlayer(format(C.COLOR_TRANSFER_ST2084)))
        assertTrue(VideoColourPolicy.isHdr10(format(C.COLOR_TRANSFER_ST2084)))
        assertTrue(VideoColourPolicy.requiresStandardPlayer(format(C.COLOR_TRANSFER_HLG)))
        assertTrue(VideoColourPolicy.requiresStandardPlayer(format(C.COLOR_TRANSFER_SDR)))
        assertTrue(VideoColourPolicy.requiresStandardPlayer(Format.Builder().setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION).build()))
        assertFalse(
            VideoColourPolicy.isHdr10(
                Format
                    .Builder()
                    .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                    .setColorInfo(format(C.COLOR_TRANSFER_ST2084).colorInfo)
                    .build(),
            ),
        )
    }

    private fun format(
        transfer: Int,
        space: Int = C.COLOR_SPACE_BT2020,
    ): Format =
        Format
            .Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H265)
            .setColorInfo(
                ColorInfo
                    .Builder()
                    .setColorTransfer(transfer)
                    .setColorSpace(space)
                    .build(),
            ).build()
}
