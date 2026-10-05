package com.neilturner.aerialviews.ui.toposcan

import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@UnstableApi
object VideoColourPolicy {
    fun isHdr10(format: Format): Boolean =
        format.sampleMimeType != MimeTypes.VIDEO_DOLBY_VISION &&
            format.colorInfo?.colorSpace == C.COLOR_SPACE_BT2020 &&
            format.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084

    fun requiresStandardPlayer(format: Format): Boolean =
        !isHdr10(format) && (
            ColorInfo.isTransferHdr(format.colorInfo) ||
                format.colorInfo?.colorSpace == C.COLOR_SPACE_BT2020 ||
                format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION
        )
}
