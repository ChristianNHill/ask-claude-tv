# Ask Claude for Android TV

A small Android TV app: say or type a request, edit it if needed, and Claude (`claude-opus-5-5`, low
effort) picks one action: open a web page in TV Bro, open an installed app, search YouTube, or show a
short answer. Built for the Formovie Theater; works on any Android TV with Google's speech service.

## Needs

- Google Assistant's speech service (`com.google.android.katniss`) enabled: it supplies speech-to-text.
- TV Bro (`com.phlox.tvwebbrowser`) for web pages.
- USB debugging on while you install or change settings (Developer options); off again afterwards.
- `~/Code/cleanyourtv` checked out with its `tools/venv` (the scripts use its ADB tools).

## Scripts (from this folder)

    TV_HOST=192.168.1.73 ./install.sh     # build and install
    TV_HOST=192.168.1.73 ./set-key.sh     # send ANTHROPIC_API_KEY from .env (never printed)
    TV_HOST=192.168.1.73 ./set-sites.sh   # send sites.txt ("Name = https://address" per line)

After a fresh install, grant the mic once:
`~/Code/cleanyourtv/tools/venv/bin/python -I ~/Code/cleanyourtv/tools/tv.py 'pm grant com.askclaude.tv android.permission.RECORD_AUDIO'`
(or press Speak and allow it on screen).

## Notes

- `.env` holds the API key (mode 600, git-ignored).
- Requests and chosen actions are logged under the `AskClaude` logcat tag.
- Refusals fall back server-side (`fallbacks: "default"`).
- Style: Anthropic's public brand palette (#141413, #FAF9F5, #D97757, #B0AEA5), Poppins and Lora
  (both OFL, in `app/src/main/res/font`).
