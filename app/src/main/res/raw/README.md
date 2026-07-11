# Voice Clips

Place your `.mp3`, `.wav`, or `.ogg` voice clips in this directory!

Once you add files here, you can trigger them in the game using the `MediaPlayer` class via Android's context resources, e.g.:

```kotlin
val mediaPlayer = MediaPlayer.create(context, R.raw.your_voice_clip_name)
mediaPlayer.start()
```

If you add files, remember to sync your Gradle project so that the `R.raw` IDs are generated.
