import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.time.LocalDateTime

class InstantReplayCommandHandler : ListenerAdapter() {
    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        super.onGuildMessageReceived(event)
        val audioManager = event.guild.audioManager
        when {
            event.message.contentRaw == "::irecord" && event.member?.voiceState?.inVoiceChannel() == true -> {
                if (audioManager.receivingHandler == null) {
                    audioManager.receivingHandler = InstantReplayAudioHandler()
                }
                val recordChannel = event.member?.voiceState?.channel
                audioManager.openAudioConnection(recordChannel)
                println("Start replay recording on ${event.guild.name} | ${recordChannel?.name}")
            }
            event.message.contentRaw =="::ireplay" -> {
                val replayAudioHandler = audioManager.receivingHandler as? InstantReplayAudioHandler ?: return
                val wavRecord = createWAVFile(
                    replayAudioHandler.getRecordBytes(),
                    "${LocalDateTime.now()}${event.guild.id}"
                )
                val mp3Compressed = convertToMP3(wavRecord)
                wavRecord.delete()
                event.message.textChannel.sendFile(mp3Compressed).submit().thenRunAsync { mp3Compressed.delete() }
            }
        }
    }

    override fun onGuildVoiceLeave(event: GuildVoiceLeaveEvent) {
        super.onGuildVoiceLeave(event)
        if (event.member.id == clientId)
        {
            println("Bot leave from: ${event.channelLeft.name}")
            event.guild.audioManager.receivingHandler = null
        }
    }
}