package com.example.voiceclassifierapp

import java.io.*

object PcmToWavConverter {

    fun convert16bitMono(pcmPath: String, wavPath: String, sampleRate: Int) {
        val pcmFile = File(pcmPath)
        val wavFile = File(wavPath)

        val pcmSize = pcmFile.length().toInt()
        val wavSize = pcmSize + 44

        val input = FileInputStream(pcmFile)
        val output = FileOutputStream(wavFile)

        // WAV Header
        val header = ByteArray(44)

        // ChunkID
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.toByte()

        val fileSize = wavSize - 8
        header[4] = (fileSize and 0xff).toByte()
        header[5] = ((fileSize shr 8) and 0xff).toByte()
        header[6] = ((fileSize shr 16) and 0xff).toByte()
        header[7] = ((fileSize shr 24) and 0xff).toByte()

        // Format
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // Subchunk1ID
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0

        // AudioFormat
        header[20] = 1
        header[21] = 0

        // NumChannels
        header[22] = 1
        header[23] = 0

        // SampleRate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        // ByteRate = SampleRate * NumChannels * BitsPerSample/8
        val byteRate = sampleRate * 2
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        // BlockAlign = NumChannels * BitsPerSample/8
        header[32] = 2
        header[33] = 0

        // BitsPerSample
        header[34] = 16
        header[35] = 0

        // Subchunk2ID
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk2Size
        header[40] = (pcmSize and 0xff).toByte()
        header[41] = ((pcmSize shr 8) and 0xff).toByte()
        header[42] = ((pcmSize shr 16) and 0xff).toByte()
        header[43] = ((pcmSize shr 24) and 0xff).toByte()

        output.write(header)
        input.copyTo(output)

        input.close()
        output.close()
    }
}
