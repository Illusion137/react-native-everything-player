import type { HybridObject, AnyMap } from "react-native-nitro-modules";

export interface NativeEverythingPlayer extends HybridObject<{ ios: "swift"; android: "kotlin" }> {
	// ─── Setup ────────────────────────────────────────────────────────
	setupPlayer: (options: AnyMap) => Promise<void>;
	updateOptions: (options: AnyMap) => Promise<void>;

	// ─── Queue ────────────────────────────────────────────────────────
	add: (tracks: AnyMap[], insertBeforeIndex?: number | null) => Promise<number | null>;
	load: (track: AnyMap) => Promise<number | null>;
	move: (fromIndex: number, toIndex: number) => Promise<void>;
	remove: (indexes: number[]) => Promise<void>;
	removeUpcomingTracks: () => Promise<void>;
	skip: (index: number, initialPosition?: number | null) => Promise<void>;
	skipToNext: (initialPosition?: number | null) => Promise<void>;
	skipToPrevious: (initialPosition?: number | null) => Promise<void>;
	setQueue: (tracks: AnyMap[]) => Promise<void>;
	getQueue: () => Promise<AnyMap[]>;
	getTrack: (index: number) => Promise<AnyMap | null>;
	getActiveTrackIndex: () => Promise<number | null>;
	getActiveTrack: () => Promise<AnyMap | null>;
	updateMetadataForTrack: (trackIndex: number, metadata: AnyMap) => Promise<void>;
	updateNowPlayingMetadata: (metadata: AnyMap) => Promise<void>;

	// ─── Playback Control ─────────────────────────────────────────────
	reset: () => Promise<void>;
	play: () => Promise<void>;
	pause: () => Promise<void>;
	stop: () => Promise<void>;
	retry: () => Promise<void>;
	setPlayWhenReady: (playWhenReady: boolean) => Promise<void>;
	getPlayWhenReady: () => Promise<boolean>;
	seekTo: (position: number) => Promise<void>;
	seekBy: (offset: number) => Promise<void>;
	setVolume: (level: number) => Promise<void>;
	getVolume: () => Promise<number>;
	setRate: (rate: number) => Promise<void>;
	getRate: () => Promise<number>;
	setRepeatMode: (mode: number) => Promise<void>;
	getRepeatMode: () => Promise<number>;
	getProgress: () => Promise<AnyMap>;
	getPlaybackState: () => Promise<AnyMap>;

	// ─── Audio Effects ────────────────────────────────────────────────
	setCrossFade: (seconds: number) => Promise<void>;
	setEqualizer: (bands: AnyMap[]) => Promise<void>;
	getEqualizer: () => Promise<AnyMap[]>;
	removeEqualizer: () => Promise<void>;

	// ─── SABR ─────────────────────────────────────────────────────────
	downloadSabrStream: (params: AnyMap, outputPath: string) => Promise<string>;
	updateSabrDownloadStream: (outputPath: string, serverUrl: string, ustreamerConfig: string) => Promise<void>;
	updateSabrDownloadPoToken: (outputPath: string, poToken: string) => Promise<void>;
	updateSabrPlaybackPoToken: (poToken: string) => Promise<void>;
	updateSabrPlaybackStream: (serverUrl: string, ustreamerConfig: string) => Promise<void>;

	// ─── Android Only ─────────────────────────────────────────────────
	acquireWakeLock: () => Promise<void>;
	abandonWakeLock: () => Promise<void>;
	validateOnStartCommandIntent: () => Promise<boolean>;

	// ─── Event Callbacks ──────────────────────────────────────────────
	onPlaybackStateChanged: ((event: AnyMap) => void) | null;
	onPlaybackError: ((event: AnyMap) => void) | null;
	onPlaybackQueueEnded: ((event: AnyMap) => void) | null;
	onActiveTrackChanged: ((event: AnyMap) => void) | null;
	onPlayWhenReadyChanged: ((event: AnyMap) => void) | null;
	onProgressUpdated: ((event: AnyMap) => void) | null;
	onPlaybackMetadata: ((event: AnyMap) => void) | null;
	onRemotePlay: (() => void) | null;
	onRemotePause: (() => void) | null;
	onRemoteStop: (() => void) | null;
	onRemoteNext: (() => void) | null;
	onRemotePrevious: (() => void) | null;
	onRemoteJumpForward: ((event: AnyMap) => void) | null;
	onRemoteJumpBackward: ((event: AnyMap) => void) | null;
	onRemoteSeek: ((event: AnyMap) => void) | null;
	onRemoteSetRating: ((event: AnyMap) => void) | null;
	onRemoteDuck: ((event: AnyMap) => void) | null;
	onRemoteLike: (() => void) | null;
	onRemoteDislike: (() => void) | null;
	onRemoteBookmark: (() => void) | null;
	onChapterMetadataReceived: ((event: AnyMap) => void) | null;
	onTimedMetadataReceived: ((event: AnyMap) => void) | null;
	onCommonMetadataReceived: ((event: AnyMap) => void) | null;
	onSabrDownloadProgress: ((event: AnyMap) => void) | null;
	onSabrReloadPlayerResponse: ((event: AnyMap) => void) | null;
	onSabrRefreshPoToken: ((event: AnyMap) => void) | null;
	onAndroidControllerConnected: ((event: AnyMap) => void) | null;
	onAndroidControllerDisconnected: ((event: AnyMap) => void) | null;
	onPlaybackResume: ((event: AnyMap) => void) | null;
}
