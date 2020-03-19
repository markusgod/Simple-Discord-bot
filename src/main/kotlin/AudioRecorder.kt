import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.AudioReceiveHandler.OUTPUT_FORMAT
import net.dv8tion.jda.api.audio.CombinedAudio
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import ws.schild.jave.AudioAttributes
import ws.schild.jave.Encoder
import ws.schild.jave.EncodingAttributes
import ws.schild.jave.MultimediaObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.util.*
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.concurrent.schedule

const val recordsFolder = "records/"

class RecordCommandHandler() : ListenerAdapter()
{
    private var isRecording = false
    private var recorder = AudioRecorder()
    private var stopTask : TimerTask? = null

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        super.onGuildMessageReceived(event)
        if (!isRecording && event.message.contentRaw.startsWith("::record"))
        {
            if ((event.guild.getMemberById(event.author.id)?.voiceState ?: return).inVoiceChannel())
            {
                var scheduleTime = 300000L
                stopTask?.cancel()
                isRecording = true
                event.guild.audioManager.receivingHandler = recorder
                event.guild.audioManager.openAudioConnection(event.guild.getMemberById(event.author.id)?.voiceState!!.channel)

                val splitMessage = event.message.contentRaw.split(" ")
                if (splitMessage.size > 1 && splitMessage[1].toLongOrNull() != null) {
                    scheduleTime = splitMessage[1].toLong()
                    scheduleStop(event.message.textChannel, scheduleTime)
                }else scheduleStop(event.message.textChannel, scheduleTime)
                val displayTime = scheduleTime/1000
                event.channel.sendMessage("Starting a $displayTime second recording. Use `::stop` to stop record earlier.").queue()
            }
        }

        if (isRecording && event.message.contentRaw.startsWith("::stop"))
        {
            if ((event.guild.getMemberById(event.author.id)?.voiceState ?: return).inVoiceChannel())
            {
                stopRecord(event.message.textChannel)
            }
        }
    }

    private fun stopRecord(textChannel: TextChannel)
    {
        isRecording = false
        textChannel.guild.audioManager.closeAudioConnection()
        val filename = recordsFolder + LocalDateTime.now().toString() + textChannel.guild.id
        recorder.endRecord(filename)
        val record = File("$filename.mp3")
        textChannel.sendFile(record).queue()
        record.delete()
        stopTask?.cancel()
    }

    private fun scheduleStop(textChannel: TextChannel, time : Long)
    {
        stopTask = Timer("Limit records", false).schedule(time) {
            stopRecord(textChannel)
        }
    }
}

class AudioRecorder() : AudioReceiveHandler
{
    private var buffer = mutableListOf<Byte>()
    override fun canReceiveCombined(): Boolean {
        return true
    }

    override fun handleCombinedAudio(combinedAudio: CombinedAudio) {
        super.handleCombinedAudio(combinedAudio)
        val decodedData = combinedAudio.getAudioData(1.0)
        buffer.addAll(decodedData.toList())
    }

    fun endRecord(filename : String)
    {
        val audioStream = AudioInputStream(ByteArrayInputStream(buffer.toByteArray()), OUTPUT_FORMAT, buffer.size.toLong())
        buffer.clear()
        val wav = File("$filename.wav")
        val encoder = Encoder()
        val audio = AudioAttributes()
        audio.setCodec("libmp3lame")
        audio.setBitRate(48000)
        audio.setChannels(1)
        audio.setSamplingRate(44100)

        val attrs = EncodingAttributes()
        attrs.format = "mp3"
        attrs.audioAttributes = audio
        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, wav)
        val mp3 = File("$filename.mp3")
        encoder.encode(MultimediaObject(wav), mp3, attrs)
        wav.delete()
    }
}