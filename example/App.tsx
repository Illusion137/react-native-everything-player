import "@origin/youtube_dl/ytdl_polyfill";
import { useEffect, useRef, useState } from "react";
import { Button, Image, Platform, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import EverythingPlayer, { Event, State, VideoView, useActiveTrack, useIsPlaying, usePlaybackState, useProgress, useTrackPlayerEvents } from "react-native-everything-player";
import nodejs from "nodejs-mobile-react-native";
import { YouTubeDL } from "@origin/youtube_dl/index";
import { load_native_fs } from "@native/fs/fs";
import { load_native_potoken } from "@native/potoken/potoken";

// ─── INSERT YOUR TRACK HERE ───────────────────────────────────────────────────
// Replace the values below with a real track before running the example.
const EXAMPLE_TRACK = {
	// Progressive MP4 fallback (guaranteed video path for VideoView validation)
	url: "https://us.mirror.ionos.com/projects/media.ccc.de/congress/2019/h264-hd/36c3-10592-eng-deu-pol-Fairtronics_hd.mp4",
	title: "Big Buck Bunny",
	artist: "Google Sample Videos",
	artwork: "https://i.ytimg.com/vi/jIhNe1ox1ls/maxresdefault.jpg"
};

const EXAMPLE_YOUTUBE_VIDEO_ID = "wf4kRfGzflo";
// ─────────────────────────────────────────────────────────────────────────────

type SabrFormat = { mimeType?: unknown };

function getSabrMimeType(value: unknown): string {
	return typeof (value as SabrFormat)?.mimeType === "string" ? String((value as SabrFormat).mimeType).toLowerCase() : "";
}

function isCompatibleSabrVideoFormat(value: unknown): boolean {
	const mime = getSabrMimeType(value);
	return mime.includes("video") && (mime.includes("mp4") || mime.includes("avc") || mime.includes("h264"));
}

function isSabrAudioFormat(value: unknown): boolean {
	return getSabrMimeType(value).includes("audio");
}

function formatSeconds(seconds: number): string {
	const m = Math.floor(seconds / 60);
	const s = Math.floor(seconds % 60);
	return `${m}:${s.toString().padStart(2, "0")}`;
}

export default function App() {
	const [videoMode, setVideoMode] = useState(false);
	const [crossfadeInput, setCrossfadeInput] = useState("0");
	const [eqInput, setEqInput] = useState("0,0,0,0,0");
	const [statusText, setStatusText] = useState("");
	const initialized = useRef(false);

	const { state } = usePlaybackState();
	const { playing, bufferingDuringPlay } = useIsPlaying();
	const { position, duration } = useProgress();
	const activeTrack = useActiveTrack();
	const isHermes = Boolean((globalThis as any).HermesInternal);

	useEffect(() => {
		// Guard against React 18 StrictMode double-invocation in development.
		// The ref is intentionally NOT reset in the cleanup so StrictMode's
		// fake unmount+remount cycle does not re-trigger initialization.
		if (initialized.current) return;
		initialized.current = true;

		const init = async () => {
			if (Platform.OS !== "web") {
				try {
					nodejs?.start?.("main.js");
					nodejs?.channel?.addListener?.("message", (msg) => {
						console.log(`[nodejs] ${String(msg)}`);
					});
				} catch (e) {
					console.warn("Failed to start nodejs-mobile:", e);
				}
			}

			await EverythingPlayer.setupPlayer({ autoHandleInterruptions: true, autoUpdateMetadata: true });
			await EverythingPlayer.updateOptions({ progressUpdateEventInterval: 1 });

			let trackToPlay: Record<string, unknown> = EXAMPLE_TRACK;
			try {
				await load_native_fs();
				await load_native_potoken();
				// const sabrParams = await YouTubeDL.resolve_sabr_url(EXAMPLE_YOUTUBE_VIDEO_ID);
				// console.log(sabrParams);
				// if (!("error" in sabrParams)) {
				// 	const rawFormats = Array.isArray(sabrParams.sabrFormats) ? sabrParams.sabrFormats : [];
				// 	const hasCompatibleSabrVideo = rawFormats.some((format: unknown) => isCompatibleSabrVideoFormat(format));
				// 	if (hasCompatibleSabrVideo) {
				// 		const sabrFormats = rawFormats.filter((format: unknown) => isSabrAudioFormat(format) || isCompatibleSabrVideoFormat(format));
				// 		trackToPlay = { url: sabrParams.url, title: "YouTube SABR (Example)", artist: "YouTube", artwork: EXAMPLE_TRACK.artwork, isSabr: true, sabrServerUrl: sabrParams.sabrServerUrl, sabrUstreamerConfig: sabrParams.sabrUstreamerConfig, sabrFormats, poToken: sabrParams.poToken, clientInfo: sabrParams.clientInfo, cookie: sabrParams.cookie, duration: sabrParams.duration };
				// 		setStatusText("Resolved SABR stream with compatible video formats.");
				// 	} else {
				// 		setStatusText("SABR resolved without compatible video formats; using MP4 fallback track.");
				// 	}
				// } else {
				// 	setStatusText(`SABR resolve failed; using fallback track. ${String(sabrParams.error)}`);
				// }
			} catch (e) {
				setStatusText(`SABR init failed; using fallback track. ${String(e)}`);
			}

			await EverythingPlayer.add([trackToPlay as any]);
			await EverythingPlayer.play();
		};

		void init();
	}, []);

	useTrackPlayerEvents([Event.PlaybackError, Event.PlayerError], (e) => {
		console.log(`[EverythingPlayer:${e.type}]`, e);
	});

	useTrackPlayerEvents([Event.SabrRefreshPoToken, Event.SabrReloadPlayerResponse], (e) => {
		void (async () => {
			try {
				const sabrParams = await YouTubeDL.resolve_sabr_url(EXAMPLE_YOUTUBE_VIDEO_ID);
				if ("error" in sabrParams) {
					setStatusText(`SABR refresh failed: ${String(sabrParams.error)}`);
					return;
				}

				if (e.type === Event.SabrRefreshPoToken) {
					await EverythingPlayer.updateSabrPlaybackPoToken(sabrParams.poToken);
					setStatusText("Updated SABR PoToken.");
					return;
				}

				await EverythingPlayer.updateSabrPlaybackStream(sabrParams.sabrServerUrl, sabrParams.sabrUstreamerConfig);
				await EverythingPlayer.updateSabrPlaybackPoToken(sabrParams.poToken);
				setStatusText("Reloaded SABR player response.");
			} catch (err) {
				setStatusText(`SABR callback error: ${String(err)}`);
			}
		})();
	});

	const handlePlayPress = async () => {
		if (state === State.Ended) {
			await EverythingPlayer.seekTo(0);
		}
		if (state === State.Error) {
			await EverythingPlayer.retry();
			return;
		}
		await EverythingPlayer.play();
	};

	const handleSeekBy = async (seconds: number) => {
		await EverythingPlayer.seekBy(seconds);
	};

	const handleCrossfadeApply = async () => {
		const value = Number(crossfadeInput.trim());
		if (!Number.isFinite(value) || value < 0) {
			setStatusText("Crossfade must be a number >= 0.");
			return;
		}
		await EverythingPlayer.setCrossFade(value);
		setStatusText(`Crossfade set to ${value}s`);
	};

	const handleEqApply = async () => {
		const bands = eqInput
			.split(",")
			.map((v) => Number(v.trim()))
			.filter((v) => Number.isFinite(v));
		if (bands.length < 1) {
			setStatusText("Enter EQ values like: 0, -2, 1, 0, 3");
			return;
		}
		await EverythingPlayer.setEqualizer(bands);
		setStatusText(`EQ applied (${bands.length} bands)`);
	};

	const handleEqReset = async () => {
		await EverythingPlayer.removeEqualizer();
		setStatusText("EQ reset");
	};

	const stateLabel = bufferingDuringPlay ? "Buffering…" : state === State.Playing ? "Playing" : state === State.Paused ? "Paused" : (state ?? "—");

	return (
		<View style={styles.root}>
			{/* Video / Artwork area */}
			{/* <View style={styles.mediaArea}>{videoMode ? <VideoView style={styles.fill} resizeMode="contain" /> : activeTrack?.artwork ? <Image source={{ uri: activeTrack.artwork }} style={styles.artwork} resizeMode="contain" /> : <View style={styles.artworkPlaceholder} />}</View> */}
			<VideoView style={styles.fill} resizeMode="contain" />

			<ScrollView style={styles.panel} contentContainerStyle={styles.panelContent}>
				{/* Track info */}
				<View style={styles.infoArea}>
					<Text style={styles.title} numberOfLines={1}>
						{activeTrack?.title ?? "No track loaded"}
					</Text>
					<Text style={styles.artist} numberOfLines={1}>
						{activeTrack?.artist ?? ""}
					</Text>
					<Text style={styles.engineText}>Engine: {isHermes ? "Hermes" : "JSC/Other"}</Text>
				</View>

				{/* Progress */}
				<View style={styles.progressRow}>
					<Text style={styles.timeLabel}>{formatSeconds(position)}</Text>
					<View style={styles.progressTrack}>
						<View style={[styles.progressFill, { width: duration > 0 ? `${(position / duration) * 100}%` : "0%" }]} />
					</View>
					<Text style={styles.timeLabel}>{formatSeconds(duration)}</Text>
				</View>

				{/* State */}
				<Text style={styles.stateLabel}>{stateLabel}</Text>

				{/* Main controls */}
				<View style={styles.controls}>
					{playing ? <Button title="Pause" onPress={() => void EverythingPlayer.pause()} /> : <Button title="Play" onPress={() => void handlePlayPress()} />}
					<Button title="Reset" onPress={() => void EverythingPlayer.reset()} />
					<Button title={videoMode ? "Audio Mode" : "Video Mode"} onPress={() => setVideoMode((v) => !v)} />
				</View>

				{/* Seek controls */}
				<View style={styles.controls}>
					<Button title="-15s" onPress={() => void handleSeekBy(-15)} />
					<Button title="+15s" onPress={() => void handleSeekBy(15)} />
					<Button title="Start" onPress={() => void EverythingPlayer.seekTo(0)} />
					<Button title="Mid" onPress={() => void EverythingPlayer.seekTo(duration > 0 ? duration / 2 : 0)} />
				</View>

				{/* Crossfade editor */}
				<View style={styles.editorRow}>
					<Text style={styles.editorLabel}>Crossfade (seconds)</Text>
					<TextInput value={crossfadeInput} onChangeText={setCrossfadeInput} keyboardType="decimal-pad" placeholder="0" placeholderTextColor="#6b7280" style={styles.input} />
					<Button title="Apply" onPress={() => void handleCrossfadeApply()} />
				</View>

				{/* Equalizer editor */}
				<View style={styles.editorRow}>
					<Text style={styles.editorLabel}>EQ bands (comma separated dB)</Text>
					<TextInput value={eqInput} onChangeText={setEqInput} placeholder="0,0,0,0,0" placeholderTextColor="#6b7280" style={styles.input} />
					<View style={styles.controls}>
						<Button title="Apply EQ" onPress={() => void handleEqApply()} />
						<Button title="Reset EQ" onPress={() => void handleEqReset()} />
					</View>
				</View>

				{statusText ? <Text style={styles.statusText}>{statusText}</Text> : null}
			</ScrollView>
		</View>
	);
}

const styles = StyleSheet.create({
	root: { flex: 1, backgroundColor: "#111827" },
	mediaArea: { flex: 1, backgroundColor: "#000" },
	panel: { flex: 1 },
	panelContent: { paddingBottom: 20 },
	fill: { flex: 1 },
	artwork: { flex: 1, width: "100%" },
	artworkPlaceholder: { flex: 1, backgroundColor: "#1f2937" },
	infoArea: { paddingHorizontal: 24, paddingTop: 16, gap: 4 },
	title: { fontSize: 20, fontWeight: "700", color: "#f9fafb" },
	artist: { fontSize: 15, color: "#9ca3af" },
	engineText: { fontSize: 12, color: "#93c5fd" },
	progressRow: { flexDirection: "row", alignItems: "center", paddingHorizontal: 24, paddingTop: 12, gap: 8 },
	progressTrack: { flex: 1, height: 4, borderRadius: 2, backgroundColor: "#374151", overflow: "hidden" },
	progressFill: { height: "100%", borderRadius: 2, backgroundColor: "#6366f1" },
	timeLabel: { fontSize: 12, color: "#6b7280", minWidth: 36, textAlign: "center" },
	stateLabel: { textAlign: "center", fontSize: 13, color: "#6b7280", paddingTop: 4 },
	controls: { flexDirection: "row", justifyContent: "center", gap: 12, paddingVertical: 10, paddingHorizontal: 16, flexWrap: "wrap" },
	editorRow: { paddingHorizontal: 20, paddingTop: 8, gap: 8 },
	editorLabel: { color: "#d1d5db", fontSize: 13 },
	input: { borderWidth: 1, borderColor: "#374151", borderRadius: 8, color: "#f9fafb", paddingHorizontal: 10, paddingVertical: 8, backgroundColor: "#111827" },
	statusText: { color: "#93c5fd", textAlign: "center", paddingTop: 8, paddingHorizontal: 20 }
});
