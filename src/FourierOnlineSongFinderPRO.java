import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Fourier Online Song Finder PRO
 *
 * Fix over the first version:
 * - AcoustID is tried first.
 * - If AcoustID returns no match, AudD is tried as a stronger fallback.
 * - If either service finds artist/title, the app opens YouTube Music search.
 * - Fourier/FFT peak visualisation still shows the maths evidence from the clip.
 *
 * This app does not download copyrighted music. It only identifies and opens search results.
 */
public class FourierOnlineSongFinderPRO extends JFrame {
    private final JTextField audioField = new JTextField(36);
    private final JTextField acoustIdKeyField = new JTextField(24);
    private final JTextField auddTokenField = new JTextField(24);
    private final JButton chooseBtn = new JButton("Choose Audio Clip");
    private final JButton identifyBtn = new JButton("Identify with Fallbacks + Search");
    private final JButton openTopBtn = new JButton("Open Top YouTube Music Search");
    private final JButton testToolsBtn = new JButton("Test Tools");
    private final JTextArea output = new JTextArea();
    private final SpectrumPanel spectrumPanel = new SpectrumPanel();

    private File selectedFile;
    private SongCandidate topCandidate;

    public FourierOnlineSongFinderPRO() {
        super("Fourier Online Song Finder PRO - AcoustID + AudD fallback + FFT Evidence");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1220, 780));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        setContentPane(root);

        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createTitledBorder("Online song identification with fallback services"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        top.add(new JLabel("Audio clip:"), c);
        c.gridx = 1; c.weightx = 1;
        audioField.setEditable(false);
        top.add(audioField, c);
        c.gridx = 2; c.weightx = 0;
        top.add(chooseBtn, c);
        c.gridx = 3;
        top.add(testToolsBtn, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        top.add(new JLabel("AcoustID key:"), c);
        c.gridx = 1; c.weightx = 1;
        acoustIdKeyField.setText(System.getenv().getOrDefault("ACOUSTID_API_KEY", ""));
        top.add(acoustIdKeyField, c);
        c.gridx = 2; c.gridy = 1; c.gridwidth = 2;
        top.add(identifyBtn, c);
        c.gridwidth = 1;

        c.gridx = 0; c.gridy = 2; c.weightx = 0;
        top.add(new JLabel("AudD token:"), c);
        c.gridx = 1; c.weightx = 1;
        auddTokenField.setText(System.getenv().getOrDefault("AUDD_API_TOKEN", ""));
        top.add(auddTokenField, c);
        c.gridx = 2; c.gridy = 2; c.gridwidth = 2;
        openTopBtn.setEnabled(false);
        top.add(openTopBtn, c);
        c.gridwidth = 1;

        root.add(top, BorderLayout.NORTH);

        output.setEditable(false);
        output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        output.setLineWrap(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.58);
        split.setLeftComponent(new JScrollPane(output));
        split.setRightComponent(spectrumPanel);
        root.add(split, BorderLayout.CENTER);

        JLabel bottom = new JLabel("PRO flow: clip → FFT evidence → AcoustID lookup → if empty, AudD fallback → YouTube Music search.");
        root.add(bottom, BorderLayout.SOUTH);

        chooseBtn.addActionListener(this::chooseAudio);
        identifyBtn.addActionListener(this::identifyOnline);
        openTopBtn.addActionListener(e -> openTopSearch());
        testToolsBtn.addActionListener(e -> testTools());

        output.setText(welcome());
    }

    private String welcome() {
        return """


        Flow:
          1. Choose a clean audio clip.
          2. The app draws Fourier/FFT spectral peaks.
          3. The app tries AcoustID using fpcalc/Chromaprint.
          4. If AcoustID returns no result, the app uploads the clip to AudD.
          5. If a title/artist is found, it opens YouTube Music search.

        Required tools:
          sudo apt update
          sudo apt install default-jdk libchromaprint-tools ffmpeg

        Keys:
          ACOUSTID_API_KEY  optional but useful
          AUDD_API_TOKEN    recommended fallback token

        You can paste keys into the boxes or export them:
          export ACOUSTID_API_KEY="your_acoustid_key"
          export AUDD_API_TOKEN="your_audd_token"
          ./run.sh

        This app does not download music. It identifies and opens search results.
        """;
    }

    private void chooseAudio(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose audio clip");
        fc.setFileFilter(new FileNameExtensionFilter("Audio files", "wav", "mp3", "flac", "ogg", "m4a", "aiff", "aif", "au"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFile = fc.getSelectedFile();
            audioField.setText(selectedFile.getAbsolutePath());
            topCandidate = null;
            openTopBtn.setEnabled(false);
            drawFourierEvidence();
        }
    }

    private void testTools() {
        output.setText("Testing local tools...\n\n");
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() {
                StringBuilder sb = new StringBuilder();
                sb.append(runCommand("java", "-version"));
                sb.append("\n");
                sb.append(runCommand("javac", "-version"));
                sb.append("\n");
                sb.append(runCommand("fpcalc", "-version"));
                return sb.toString();
            }
            @Override protected void done() {
                try { output.append(get()); }
                catch (Exception ex) { output.append("Tool test failed: " + ex.getMessage() + "\n"); }
            }
        };
        worker.execute();
    }

    static String runCommand(String... cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();
            return String.join(" ", cmd) + "\nexitCode=" + code + "\n" + out + "\n";
        } catch (Exception ex) {
            return String.join(" ", cmd) + "\nFAILED: " + ex.getMessage() + "\n";
        }
    }

    private void drawFourierEvidence() {
        output.setText("Loading audio for Fourier evidence panel...\n\n");
        try {
            AudioData audio = AudioLoader.tryLoadMono(selectedFile, 11025.0, 45.0);
            FourierEvidence evidence = FourierAnalyzer.analyze(audio);
            spectrumPanel.setEvidence(evidence, selectedFile.getName());
            output.append("Fourier evidence ready.\n");
            output.append("Duration analysed: " + String.format(Locale.US, "%.2f", audio.durationSeconds()) + " s\n");
            output.append("FFT windows: " + evidence.frames + "\n");
            output.append("Spectral peaks drawn: " + evidence.peaks.size() + "\n\n");
            output.append("Now click Identify with Fallbacks + Search.\n");
        } catch (Exception ex) {
            spectrumPanel.setMessage("Java could not decode this audio for FFT drawing.\nOnline lookup may still work through fpcalc/AudD.");
            output.append("Could not draw FFT evidence using Java Sound:\n");
            output.append(ex.getMessage() + "\n\n");
            output.append("This is okay if fpcalc/AudD can read it.\n");
        }
    }

    private void identifyOnline(ActionEvent e) {
        if (selectedFile == null || !selectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "Choose an audio clip first.");
            return;
        }

        String acoustKey = acoustIdKeyField.getText().trim();
        String auddToken = auddTokenField.getText().trim();

        if (acoustKey.isEmpty() && auddToken.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Paste at least one key/token.\n\nRecommended: AudD token, because AcoustID can return empty results.");
            return;
        }

        identifyBtn.setEnabled(false);
        openTopBtn.setEnabled(false);
        topCandidate = null;
        output.setText("Identifying song online with fallback services...\n\n");

        SwingWorker<LookupResult, String> worker = new SwingWorker<>() {
            @Override protected LookupResult doInBackground() throws Exception {
                List<SongCandidate> allCandidates = new ArrayList<>();
                StringBuilder report = new StringBuilder();

                if (!acoustKey.isEmpty()) {
                    publish("1) Trying AcoustID first...\n");
                    try {
                        FpcalcResult fp = Fpcalc.run(selectedFile);
                        publish("   fpcalc duration: " + String.format(Locale.US, "%.1f", fp.duration) + " s\n");
                        publish("   fingerprint chars: " + fp.fingerprint.length() + "\n");
                        List<SongCandidate> c = AcoustId.lookup(fp, acoustKey);
                        publish("   AcoustID candidates: " + c.size() + "\n\n");
                        allCandidates.addAll(c);
                        report.append("AcoustID tried: ").append(c.size()).append(" candidate(s)\n");
                    } catch (Exception ex) {
                        publish("   AcoustID failed: " + ex.getMessage() + "\n\n");
                        report.append("AcoustID failed: ").append(ex.getMessage()).append("\n");
                    }
                }

                if (allCandidates.isEmpty() && !auddToken.isEmpty()) {
                    publish("2) AcoustID found nothing. Trying AudD fallback...\n");
                    try {
                        List<SongCandidate> c = AudD.lookup(selectedFile, auddToken);
                        publish("   AudD candidates: " + c.size() + "\n\n");
                        allCandidates.addAll(c);
                        report.append("AudD tried: ").append(c.size()).append(" candidate(s)\n");
                    } catch (Exception ex) {
                        publish("   AudD failed: " + ex.getMessage() + "\n\n");
                        report.append("AudD failed: ").append(ex.getMessage()).append("\n");
                    }
                }

                allCandidates.sort(Comparator.comparingDouble((SongCandidate sc) -> sc.score).reversed());
                return new LookupResult(allCandidates, report.toString());
            }

            @Override protected void process(List<String> chunks) {
                for (String s : chunks) output.append(s);
            }

            @Override protected void done() {
                try {
                    LookupResult result = get();
                    output.append(result.toReport());

                    if (!result.candidates.isEmpty()) {
                        topCandidate = result.candidates.get(0);
                        openTopBtn.setEnabled(true);
                        openYouTubeMusicSearch(topCandidate);
                    }
                } catch (Exception ex) {
                    output.append("\nFAILED:\n" + ex.getMessage() + "\n");
                } finally {
                    identifyBtn.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void openTopSearch() {
        if (topCandidate != null) openYouTubeMusicSearch(topCandidate);
    }

    private void openYouTubeMusicSearch(SongCandidate candidate) {
        try {
            String q = candidate.searchQuery();
            String url = "https://music.youtube.com/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8);
            output.append("\nOpening YouTube Music search:\n");
            output.append("  " + q + "\n");
            output.append("  " + url + "\n");

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                output.append("Could not open browser automatically. Copy the URL above.\n");
            }
        } catch (Exception ex) {
            output.append("Could not open YouTube Music: " + ex.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new FourierOnlineSongFinderPRO().setVisible(true));
    }

    static class Fpcalc {
        static FpcalcResult run(File audio) throws Exception {
            ProcessBuilder pb = new ProcessBuilder("fpcalc", "-json", audio.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int code = p.waitFor();

            if (code != 0) throw new IOException("fpcalc failed. Output:\n" + out);

            String durationText = Json.firstValue(out, "duration");
            String fingerprint = Json.firstValue(out, "fingerprint");

            if (fingerprint.isBlank()) throw new IOException("fpcalc did not return a fingerprint. Output:\n" + out);

            double duration = 0;
            try { duration = Double.parseDouble(durationText); } catch (Exception ignored) {}

            return new FpcalcResult(duration, fingerprint);
        }
    }

    static class FpcalcResult {
        final double duration;
        final String fingerprint;
        FpcalcResult(double duration, String fingerprint) {
            this.duration = duration;
            this.fingerprint = fingerprint;
        }
    }

    static class AcoustId {
        static List<SongCandidate> lookup(FpcalcResult fp, String apiKey) throws Exception {
            String body = "client=" + enc(apiKey)
                    + "&duration=" + enc(String.valueOf((int) Math.round(fp.duration)))
                    + "&fingerprint=" + enc(fp.fingerprint)
                    + "&meta=" + enc("recordings artists releasegroups");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.acoustid.org/v2/lookup"))
                    .timeout(java.time.Duration.ofSeconds(45))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "FourierOnlineSongFinderPRO")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            if (response.statusCode() != 200) {
                throw new IOException("AcoustID HTTP " + response.statusCode() + "\n" + json);
            }

            if (json.contains("\"results\": []") || json.contains("\"results\":[]")) {
                return new ArrayList<>();
            }

            List<String> scores = Json.allValues(json, "score");
            List<String> titles = Json.allValues(json, "title");
            List<String> artists = Json.artistNamesNearRecordings(json);

            List<SongCandidate> out = new ArrayList<>();
            int n = Math.min(titles.size(), Math.max(1, scores.size()));
            for (int i = 0; i < n; i++) {
                double score = parseDouble(i < scores.size() ? scores.get(i) : "0");
                String title = i < titles.size() ? titles.get(i) : "";
                String artist = i < artists.size() ? artists.get(i) : "Unknown artist";
                if (!title.isBlank()) addUnique(out, new SongCandidate(title, artist, score, "AcoustID"));
            }
            return out;
        }

        static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    }

    static class AudD {
        static List<SongCandidate> lookup(File audio, String apiToken) throws Exception {
            String boundary = "----JavaBoundary" + UUID.randomUUID();
            byte[] body = MultipartBody.of(boundary)
                    .field("api_token", apiToken)
                    .field("return", "apple_music,spotify,deezer,musicbrainz")
                    .file("file", audio)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.audd.io/"))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", "FourierOnlineSongFinderPRO")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            if (response.statusCode() != 200) {
                throw new IOException("AudD HTTP " + response.statusCode() + "\n" + json);
            }

            if (json.contains("\"result\":null") || json.contains("\"status\":\"error\"")) {
                return new ArrayList<>();
            }

            String title = Json.firstValue(json, "title");
            String artist = Json.firstValue(json, "artist");
            String songLink = Json.firstValue(json, "song_link");
            String album = Json.firstValue(json, "album");

            List<SongCandidate> out = new ArrayList<>();
            if (!title.isBlank()) {
                SongCandidate c = new SongCandidate(title, artist.isBlank() ? "Unknown artist" : artist, 1.0, "AudD");
                c.songLink = songLink;
                c.album = album;
                out.add(c);
            }
            return out;
        }
    }

    static class MultipartBody {
        final String boundary;
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        MultipartBody(String boundary) { this.boundary = boundary; }

        static MultipartBody of(String boundary) { return new MultipartBody(boundary); }

        MultipartBody field(String name, String value) throws IOException {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        }

        MultipartBody file(String name, File file) throws IOException {
            String filename = file.getName();
            String mime = Files.probeContentType(file.toPath());
            if (mime == null) mime = "application/octet-stream";

            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(Files.readAllBytes(file.toPath()));
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            return this;
        }

        byte[] build() throws IOException {
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        }
    }

    static class LookupResult {
        final List<SongCandidate> candidates;
        final String serviceReport;

        LookupResult(List<SongCandidate> candidates, String serviceReport) {
            this.candidates = candidates;
            this.serviceReport = serviceReport;
        }

        String toReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("ONLINE SONG IDENTIFICATION RESULT\n");
            sb.append("=================================\n\n");
            sb.append(serviceReport).append("\n");

            if (candidates.isEmpty()) {
                sb.append("No service found the song.\n\n");
                sb.append("This means neither available database matched the clip.\n");
                sb.append("Try another section of the song, or add ACRCloud later as a third fallback.\n");
                return sb.toString();
            }

            sb.append("Possible matches:\n");
            for (int i = 0; i < Math.min(8, candidates.size()); i++) {
                SongCandidate c = candidates.get(i);
                sb.append(String.format(Locale.US, "  %d. %-10s %-28s - %-42s score=%.3f%n",
                        i + 1, c.source, cut(c.artist, 28), cut(c.title, 42), c.score));
                if (c.album != null && !c.album.isBlank()) sb.append("      album: ").append(c.album).append("\n");
                if (c.songLink != null && !c.songLink.isBlank()) sb.append("      link:  ").append(c.songLink).append("\n");
            }

            SongCandidate top = candidates.get(0);
            sb.append("\nTop result to search on YouTube Music:\n");
            sb.append("  ").append(top.searchQuery()).append("\n\n");
            sb.append("What was used:\n");
            sb.append("  Fourier/FFT evidence is drawn from the clip.\n");
            sb.append("  AcoustID used Chromaprint/fpcalc if key was provided.\n");
            sb.append("  AudD uploaded the audio clip as fallback if token was provided.\n");
            sb.append("  The app opens YouTube Music search for the best title/artist.\n");
            return sb.toString();
        }

        static String cut(String s, int n) {
            if (s == null) return "";
            if (s.length() <= n) return s;
            return s.substring(0, n - 3) + "...";
        }
    }

    static class SongCandidate {
        final String title;
        final String artist;
        final double score;
        final String source;
        String songLink = "";
        String album = "";

        SongCandidate(String title, String artist, double score, String source) {
            this.title = title;
            this.artist = artist;
            this.score = score;
            this.source = source;
        }

        String searchQuery() {
            if (artist == null || artist.isBlank() || artist.equalsIgnoreCase("Unknown artist")) return title;
            return artist + " " + title;
        }
    }

    static void addUnique(List<SongCandidate> list, SongCandidate c) {
        for (SongCandidate x : list) {
            if (x.title.equalsIgnoreCase(c.title) && x.artist.equalsIgnoreCase(c.artist)) return;
        }
        list.add(c);
    }

    static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception ex) { return 0; }
    }

    static class Json {
        static String firstValue(String json, String key) {
            List<String> vals = allValues(json, key);
            return vals.isEmpty() ? "" : vals.get(0);
        }

        static List<String> allValues(String json, String key) {
            List<String> out = new ArrayList<>();
            String pat = "\"" + key + "\"";
            int pos = 0;

            while ((pos = json.indexOf(pat, pos)) >= 0) {
                int colon = json.indexOf(':', pos + pat.length());
                if (colon < 0) break;
                int i = colon + 1;
                while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;

                if (i < json.length() && json.charAt(i) == '"') {
                    int end = findStringEnd(json, i + 1);
                    if (end > i) out.add(unescape(json.substring(i + 1, end)));
                    pos = Math.max(i + 1, end + 1);
                } else {
                    int end = i;
                    while (end < json.length() && "0123456789.-eE".indexOf(json.charAt(end)) >= 0) end++;
                    if (end > i) out.add(json.substring(i, end));
                    pos = Math.max(end, i + 1);
                }
            }
            return out;
        }

        static List<String> artistNamesNearRecordings(String json) {
            List<String> out = new ArrayList<>();
            String pat = "\"artists\"";
            int pos = 0;

            while ((pos = json.indexOf(pat, pos)) >= 0) {
                int arr = json.indexOf('[', pos);
                int end = findMatching(json, arr, '[', ']');
                if (arr < 0 || end < 0) break;
                String block = json.substring(arr, end + 1);
                String name = firstValue(block, "name");
                if (!name.isBlank()) out.add(name);
                pos = end + 1;
            }
            return out;
        }

        static int findStringEnd(String s, int start) {
            for (int i = start; i < s.length(); i++) {
                if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) return i;
            }
            return -1;
        }

        static int findMatching(String s, int start, char open, char close) {
            if (start < 0) return -1;
            int depth = 0;
            boolean inString = false;

            for (int i = start; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inString = !inString;
                if (inString) continue;
                if (c == open) depth++;
                if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
            return -1;
        }

        static String unescape(String s) {
            return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\/", "/").replace("\\\\", "\\");
        }
    }

    static class AudioData {
        final double[] samples;
        final double sampleRate;
        AudioData(double[] samples, double sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
        double durationSeconds() { return samples.length / sampleRate; }
    }

    static class AudioLoader {
        static AudioData tryLoadMono(File file, double targetRate, double maxSeconds) throws Exception {
            try (AudioInputStream in0 = AudioSystem.getAudioInputStream(file)) {
                AudioFormat base = in0.getFormat();
                AudioFormat decoded = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(),
                        16,
                        base.getChannels(),
                        base.getChannels() * 2,
                        base.getSampleRate(),
                        false
                );

                try (AudioInputStream in = AudioSystem.getAudioInputStream(decoded, in0)) {
                    byte[] bytes = readLimited(in, decoded, maxSeconds);
                    int channels = decoded.getChannels();
                    int frameSize = decoded.getFrameSize();
                    int frames = bytes.length / frameSize;
                    double[] mono = new double[frames];

                    for (int i = 0; i < frames; i++) {
                        double sum = 0.0;
                        int frameStart = i * frameSize;
                        for (int ch = 0; ch < channels; ch++) {
                            int lo = bytes[frameStart + ch * 2] & 0xff;
                            int hi = bytes[frameStart + ch * 2 + 1];
                            int val = (hi << 8) | lo;
                            sum += val / 32768.0;
                        }
                        mono[i] = sum / channels;
                    }

                    if (Math.abs(decoded.getSampleRate() - targetRate) > 1.0) {
                        mono = resample(mono, decoded.getSampleRate(), targetRate);
                    } else {
                        targetRate = decoded.getSampleRate();
                    }

                    normalize(mono);
                    return new AudioData(mono, targetRate);
                }
            }
        }

        static byte[] readLimited(AudioInputStream in, AudioFormat format, double maxSeconds) throws IOException {
            long maxBytes = (long) (format.getFrameRate() * maxSeconds * format.getFrameSize());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0;
            int n;

            while ((n = in.read(buf)) != -1) {
                if (total + n > maxBytes) n = (int) Math.max(0, maxBytes - total);
                if (n > 0) out.write(buf, 0, n);
                total += n;
                if (total >= maxBytes) break;
            }
            return out.toByteArray();
        }

        static double[] resample(double[] x, double fromRate, double toRate) {
            int n = Math.max(1, (int) Math.round(x.length * toRate / fromRate));
            double[] y = new double[n];
            double ratio = fromRate / toRate;

            for (int i = 0; i < n; i++) {
                double pos = i * ratio;
                int j = (int) Math.floor(pos);
                double a = pos - j;
                double v0 = x[Math.min(j, x.length - 1)];
                double v1 = x[Math.min(j + 1, x.length - 1)];
                y[i] = v0 + a * (v1 - v0);
            }
            return y;
        }

        static void normalize(double[] x) {
            double max = 0;
            for (double v : x) max = Math.max(max, Math.abs(v));
            if (max < 1e-9) return;
            double s = 0.95 / max;
            for (int i = 0; i < x.length; i++) x[i] *= s;
        }
    }

    static class FourierAnalyzer {
        static final int FFT_SIZE = 2048;
        static final int HOP = 1024;

        static FourierEvidence analyze(AudioData audio) {
            List<Peak> peaks = new ArrayList<>();
            double[] window = hann(FFT_SIZE);
            int frames = Math.max(0, (audio.samples.length - FFT_SIZE) / HOP);

            for (int frame = 0; frame < frames; frame++) {
                int start = frame * HOP;
                Complex[] buf = new Complex[FFT_SIZE];

                for (int i = 0; i < FFT_SIZE; i++) {
                    buf[i] = new Complex(audio.samples[start + i] * window[i], 0);
                }

                FFT.fft(buf);

                double avg = 0;
                double[] mags = new double[FFT_SIZE / 2];
                for (int k = 1; k < mags.length; k++) {
                    mags[k] = Math.log1p(buf[k].abs());
                    avg += mags[k];
                }
                avg /= Math.max(1, mags.length - 1);

                for (int band = 0; band < 10; band++) {
                    int k1 = 2 + band * (mags.length - 5) / 10;
                    int k2 = 2 + (band + 1) * (mags.length - 5) / 10;
                    int bestK = k1;
                    double best = -1;
                    for (int k = k1; k < k2; k++) {
                        if (mags[k] > best) {
                            best = mags[k];
                            bestK = k;
                        }
                    }
                    if (best > avg * 1.25) {
                        double hz = bestK * audio.sampleRate / FFT_SIZE;
                        if (hz < 5500) peaks.add(new Peak(frame, hz, best));
                    }
                }
            }

            return new FourierEvidence(peaks, frames);
        }

        static double[] hann(int n) {
            double[] w = new double[n];
            for (int i = 0; i < n; i++) w[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            return w;
        }
    }

    static class FourierEvidence {
        final List<Peak> peaks;
        final int frames;
        FourierEvidence(List<Peak> peaks, int frames) {
            this.peaks = peaks;
            this.frames = frames;
        }
    }

    static class Peak {
        final int frame;
        final double hz;
        final double strength;
        Peak(int frame, double hz, double strength) {
            this.frame = frame;
            this.hz = hz;
            this.strength = strength;
        }
    }

    static class SpectrumPanel extends JPanel {
        private FourierEvidence evidence;
        private String title = "Fourier Evidence";
        private String message;

        SpectrumPanel() {
            setPreferredSize(new Dimension(470, 650));
            setBackground(new Color(247, 250, 255));
            setBorder(BorderFactory.createTitledBorder("Fourier / FFT evidence"));
        }

        void setEvidence(FourierEvidence evidence, String fileName) {
            this.evidence = evidence;
            this.title = fileName;
            this.message = null;
            repaint();
        }

        void setMessage(String message) {
            this.evidence = null;
            this.message = message;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int left = 45, top = 70, right = getWidth() - 35, bottom = getHeight() - 80;

            g.setColor(new Color(25, 32, 44));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 15f));
            g.drawString("Spectrogram peak map", 20, 34);
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
            g.drawString(title, 20, 53);

            g.setColor(Color.WHITE);
            g.fillRoundRect(left, top, right - left, bottom - top, 16, 16);
            g.setColor(new Color(205, 214, 226));
            g.drawRoundRect(left, top, right - left, bottom - top, 16, 16);

            if (message != null) {
                g.setColor(new Color(70, 75, 90));
                drawMultiline(g, message, left + 20, top + 35);
                return;
            }

            if (evidence == null || evidence.peaks.isEmpty()) {
                g.setColor(new Color(70, 75, 90));
                g.drawString("Choose an audio clip to draw Fourier peaks.", left + 20, top + 35);
                return;
            }

            int frames = Math.max(1, evidence.frames);
            double maxHz = 5500;

            g.setColor(new Color(30, 105, 210, 100));
            int drawn = 0;
            for (Peak p : evidence.peaks) {
                int x = left + (int) Math.round((right - left) * (p.frame / (double) frames));
                int y = bottom - (int) Math.round((bottom - top) * (p.hz / maxHz));
                int r = p.strength > 1.0 ? 3 : 2;
                g.fillOval(x - r, y - r, 2 * r, 2 * r);
                drawn++;
                if (drawn > 3000) break;
            }

            g.setColor(new Color(55, 65, 82));
            g.drawString("time →", right - 55, bottom + 22);
            g.rotate(-Math.PI / 2);
            g.drawString("frequency →", -top - 105, left - 15);
            g.rotate(Math.PI / 2);

            g.setColor(new Color(25, 32, 44));
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 12f));
            g.drawString("Blue dots = strong Fourier peaks from the clip.", 20, getHeight() - 45);
            g.drawString("Recognition tries AcoustID first, then AudD fallback.", 20, getHeight() - 26);
        }

        private void drawMultiline(Graphics2D g, String text, int x, int y) {
            int line = 0;
            for (String s : text.split("\n")) {
                g.drawString(s, x, y + line * 18);
                line++;
            }
        }
    }

    static class Complex {
        double re, im;
        Complex(double re, double im) { this.re = re; this.im = im; }
        double abs() { return Math.hypot(re, im); }
    }

    static class FFT {
        static void fft(Complex[] a) {
            int n = a.length;
            if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("FFT size must be power of two.");

            for (int i = 1, j = 0; i < n; i++) {
                int bit = n >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) {
                    Complex tmp = a[i];
                    a[i] = a[j];
                    a[j] = tmp;
                }
            }

            for (int len = 2; len <= n; len <<= 1) {
                double ang = -2 * Math.PI / len;
                double wLenRe = Math.cos(ang);
                double wLenIm = Math.sin(ang);

                for (int i = 0; i < n; i += len) {
                    double wRe = 1.0, wIm = 0.0;

                    for (int j = 0; j < len / 2; j++) {
                        Complex u = a[i + j];
                        Complex v0 = a[i + j + len / 2];

                        double vRe = v0.re * wRe - v0.im * wIm;
                        double vIm = v0.re * wIm + v0.im * wRe;

                        a[i + j] = new Complex(u.re + vRe, u.im + vIm);
                        a[i + j + len / 2] = new Complex(u.re - vRe, u.im - vIm);

                        double nextRe = wRe * wLenRe - wIm * wLenIm;
                        double nextIm = wRe * wLenIm + wIm * wLenRe;
                        wRe = nextRe;
                        wIm = nextIm;
                    }
                }
            }
        }
    }
}
