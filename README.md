# Fourier Online Song Finder PRO

This version fixes the problem where AcoustID returns:

```json
{"results":[],"status":"ok"}
```

It does that by adding a second recognition service:

```text
1. Try AcoustID
2. If AcoustID finds nothing, try AudD
3. If either finds artist/title, open YouTube Music search
```

## Run on Linux

```bash
cd ~/Downloads
unzip FourierOnlineSongFinderPRO.zip
cd FourierOnlineSongFinderPRO
chmod +x run.sh
./run.sh
```

## Required local tools

```bash
sudo apt update
sudo apt install default-jdk libchromaprint-tools ffmpeg
```

## Keys/tokens

The app has two boxes:

```text
AcoustID key
AudD token
```

AcoustID is optional if you have AudD. AudD is recommended as the fallback.

You can paste tokens into the app or export them:

```bash
export ACOUSTID_API_KEY="your_acoustid_key"
export AUDD_API_TOKEN="your_audd_token"
./run.sh
```

## Recording a clean clip from system audio

```bash
ffmpeg -f pulse -i "alsa_output.pci-0000_00_1f.3.analog-stereo.monitor" -t 35 -ac 1 -ar 44100 ~/Downloads/clip.wav
```

Then choose:

```text
~/Downloads/clip.wav
```

## What the app does

- Draws Fourier/FFT spectral peaks from the clip.
- Tries AcoustID via fpcalc/Chromaprint.
- If AcoustID gives no result, uploads the audio file to AudD.
- Opens YouTube Music search for the best title/artist.

It does not download or rip music.
