// ─── Enums ───────────────────────────────────────────────────────────────────

export enum State {
	None = "none",
	Ready = "ready",
	Playing = "playing",
	Paused = "paused",
	Stopped = "stopped",
	Loading = "loading",
	Buffering = "buffering",
	Error = "error",
	Ended = "ended"
}

export enum Event {
	PlayerError = "player-error",
	PlaybackState = "playback-state",
	PlaybackError = "playback-error",
	PlaybackQueueEnded = "playback-queue-ended",
	PlaybackActiveTrackChanged = "playback-active-track-changed",
	PlaybackPlayWhenReadyChanged = "playback-play-when-ready-changed",
	PlaybackProgressUpdated = "playback-progress-updated",
	PlaybackResume = "android-playback-resume",
	RemotePlay = "remote-play",
	RemotePlayPause = "remote-play-pause",
	RemotePause = "remote-pause",
	RemoteStop = "remote-stop",
	RemoteNext = "remote-next",
	RemotePrevious = "remote-previous",
	RemoteJumpForward = "remote-jump-forward",
	RemoteJumpBackward = "remote-jump-backward",
	RemoteSeek = "remote-seek",
	RemoteSetRating = "remote-set-rating",
	RemoteDuck = "remote-duck",
	RemoteLike = "remote-like",
	RemoteDislike = "remote-dislike",
	RemoteBookmark = "remote-bookmark",
	RemotePlayId = "remote-play-id",
	RemotePlaySearch = "remote-play-search",
	RemoteSkip = "remote-skip",
	MetadataChapterReceived = "metadata-chapter-received",
	MetadataTimedReceived = "metadata-timed-received",
	MetadataCommonReceived = "metadata-common-received",
	AndroidConnectorConnected = "android-controller-connected",
	AndroidConnectorDisconnected = "android-controller-disconnected",
	SabrDownloadProgress = "sabr-download-progress",
	SabrReloadPlayerResponse = "sabr-reload-player-response",
	SabrRefreshPoToken = "sabr-refresh-po-token"
}

export enum Capability {
	Play = 1,
	PlayFromId = 2,
	PlayFromSearch = 3,
	Pause = 4,
	Stop = 5,
	SeekTo = 6,
	Skip = 7,
	SkipToNext = 8,
	SkipToPrevious = 9,
	JumpForward = 10,
	JumpBackward = 11,
	SetRating = 12
}

export enum RepeatMode {
	Off = 1,
	Track = 2,
	Queue = 3
}

export enum PitchAlgorithm {
	Linear = 1,
	Music = 2,
	Voice = 3
}

export enum TrackType {
	Default = "default",
	Dash = "dash",
	HLS = "hls",
	SmoothStreaming = "smoothstreaming"
}

export enum RatingType {
	Heart = 1,
	ThumbsUpDown = 2,
	ThreeStars = 3,
	FourStars = 4,
	FiveStars = 5,
	Percentage = 6
}

export enum IOSCategory {
	Playback = "playback",
	PlayAndRecord = "playAndRecord",
	MultiRoute = "multiRoute",
	Ambient = "ambient",
	SoloAmbient = "soloAmbient",
	Record = "record"
}

export enum IOSCategoryMode {
	Default = "default",
	GameChat = "gameChat",
	Measurement = "measurement",
	MoviePlayback = "moviePlayback",
	SpokenAudio = "spokenAudio",
	VideoChat = "videoChat",
	VideoRecording = "videoRecording",
	VoiceChat = "voiceChat",
	VoicePrompt = "voicePrompt"
}

export enum IOSCategoryOptions {
	MixWithOthers = "mixWithOthers",
	DuckOthers = "duckOthers",
	InterruptSpokenAudioAndMixWithOthers = "interruptSpokenAudioAndMixWithOthers",
	AllowBluetooth = "allowBluetooth",
	AllowBluetoothA2DP = "allowBluetoothA2DP",
	AllowAirPlay = "allowAirPlay",
	DefaultToSpeaker = "defaultToSpeaker"
}

export enum AndroidAudioContentType {
	Music = "music",
	Speech = "speech",
	Sonification = "sonification",
	Movie = "movie",
	Unknown = "unknown"
}

export enum AppKilledPlaybackBehavior {
	ContinuePlayback = "continue-playback",
	PausePlayback = "pause-playback",
	StopPlaybackAndRemoveNotification = "stop-playback-and-remove-notification"
}

// ─── Core Interfaces ─────────────────────────────────────────────────────────

export interface TrackMetadataBase {
	title?: string;
	album?: string;
	artist?: string;
	duration?: number;
	artwork?: string;
	description?: string;
	mediaId?: string;
	genre?: string;
	date?: string;
	rating?: RatingType;
	isLiveStream?: boolean;
}

export interface Track extends TrackMetadataBase {
	url: string;
	type?: TrackType;
	userAgent?: string;
	contentType?: string;
	pitchAlgorithm?: PitchAlgorithm;

	headers?: Record<string, any>;
	isOpus?: boolean;

	// ─── Track Trimming ───────────────────────────────────────────────
	/** Playback start offset in seconds (optional, defaults to beginning). */
	startTime?: number;
	/** Playback end cutoff in seconds (optional, defaults to track duration).
	 *  Crossfade triggers relative to this value when set. */
	endTime?: number;

	// ─── DRM ─────────────────────────────────────────────────────────
	/** DRM type: 'fairplay' for iOS, 'widevine' for Android */
	drmType?: "fairplay" | "widevine";
	/** License server URL for DRM */
	drmLicenseServer?: string;
	/** Additional HTTP headers to send with license requests */
	drmHeaders?: Record<string, string>;
	/** FairPlay only: URL to fetch the DRM certificate from the CDN */
	drmCertificateUrl?: string;

	// ─── SABR (iOS — YouTube streaming) ──────────────────────────────
	isSabr?: boolean;
	sabrServerUrl?: string;
	sabrUstreamerConfig?: string;
	sabrFormats?: Record<string, unknown>[];
	poToken?: string;
	cookie?: string;
	clientInfo?: SabrClientInfo;
}

export interface ResourceObject {
	uri: string;
}

export type AddTrack = Track & { url: string | ResourceObject; artwork?: string | ResourceObject };

export interface Progress {
	position: number;
	duration: number;
	buffered: number;
}

export interface NowPlayingMetadata extends TrackMetadataBase {
	elapsedTime?: number;
}

export type PlaybackState = { state: Exclude<State, State.Error> } | { state: State.Error; error: PlaybackErrorEvent };

// ─── Options ─────────────────────────────────────────────────────────────────

export interface PlayerOptions {
	minBuffer?: number;
	maxBuffer?: number;
	backBuffer?: number;
	playBuffer?: number;
	maxCacheSize?: number;
	iosCategory?: IOSCategory;
	iosCategoryMode?: IOSCategoryMode;
	iosCategoryOptions?: IOSCategoryOptions[];
	androidAudioContentType?: AndroidAudioContentType;
	autoUpdateMetadata?: boolean;
	autoHandleInterruptions?: boolean;
}

export interface FeedbackOptions {
	isActive: boolean;
	title: string;
}

export interface AndroidOptions {
	appKilledPlaybackBehavior?: AppKilledPlaybackBehavior;
	alwaysPauseOnInterruption?: boolean;
	stopForegroundGracePeriod?: number;
	audioOffload?: boolean;
	androidSkipSilence?: boolean;
	shuffle?: boolean;
}

export interface UpdateOptions {
	android?: AndroidOptions;
	ratingType?: RatingType;
	forwardJumpInterval?: number;
	backwardJumpInterval?: number;
	progressUpdateEventInterval?: number;
	likeOptions?: FeedbackOptions;
	dislikeOptions?: FeedbackOptions;
	bookmarkOptions?: FeedbackOptions;
	capabilities?: Capability[];
	notificationCapabilities?: Capability[];
	color?: number;
}

export type ServiceHandler = () => Promise<void>;

export interface SabrClientInfo {
	clientName?: number;
	clientVersion?: string;
}

export interface SabrDownloadParams {
	sabrServerUrl: string;
	sabrUstreamerConfig: string;
	sabrFormats?: Record<string, unknown>[];
	poToken?: string;
	placeholder_po_token?: string;
	clientInfo?: SabrClientInfo;
	cookie?: string;
	/** Video duration in seconds */
	duration?: number;
	/** Download as WebM/Opus (itag 251) instead of M4A */
	preferOpus?: boolean;
}

// ─── Event Payload Types ──────────────────────────────────────────────────────

export interface PlaybackErrorEvent {
	code: string;
	message: string;
}

export interface PlayerErrorEvent {
	code: "android-foreground-service-start-not-allowed";
	message: string;
}

export interface PlaybackQueueEndedEvent {
	track: number;
	position: number;
}

export interface PlaybackActiveTrackChangedEvent {
	lastIndex?: number;
	lastTrack?: Track;
	lastPosition: number;
	index?: number;
	track?: Track;
}

export interface PlaybackPlayWhenReadyChangedEvent {
	playWhenReady: boolean;
}

export interface PlaybackProgressUpdatedEvent extends Progress {
	track: number;
}

export interface PlaybackResumeEvent {
	package: string;
}

export interface RemoteDuckEvent {
	paused: boolean;
	permanent: boolean;
}

export interface RemoteJumpForwardEvent {
	interval: number;
}

export interface RemoteJumpBackwardEvent {
	interval: number;
}

export interface RemoteSeekEvent {
	position: number;
}

export interface RemoteSetRatingEvent {
	rating: RatingType;
}

export interface RemotePlayIdEvent {
	id: string;
}

export interface RemotePlaySearchEvent {
	query: string;
	focus?: "artist" | "album" | "playlist" | "genre";
	title?: string;
	artist?: string;
	album?: string;
	date?: string;
	playlist?: string;
}

export interface RemoteSkipEvent {
	index: number;
}

export interface RawEntry {
	commonKey: string | undefined;
	keySpace: string | undefined;
	time: number | undefined;
	value: unknown | null;
	key: string;
}

export interface AudioCommonMetadata {
	title: string | undefined;
	artist: string | undefined;
	albumTitle: string | undefined;
	subtitle: string | undefined;
	description: string | undefined;
	artworkUri: string | undefined;
	trackNumber: string | undefined;
	composer: string | undefined;
	conductor: string | undefined;
	genre: string | undefined;
	compilation: string | undefined;
	station: string | undefined;
	mediaType: string | undefined;
	creationDate: string | undefined;
	creationYear: string | undefined;
}

export interface AudioMetadata extends AudioCommonMetadata {
	raw: RawEntry[];
}

export interface AudioMetadataReceivedEvent {
	metadata: AudioMetadata[];
}

export interface AudioCommonMetadataReceivedEvent {
	metadata: AudioCommonMetadata;
}

export interface AndroidControllerConnectedEvent extends AndroidControllerDisconnectedEvent {
	isMediaNotificationController: boolean;
	isAutomotiveController: boolean;
	isAutoCompanionController: boolean;
}

export interface AndroidControllerDisconnectedEvent {
	package: string;
}

// ─── EventPayloadByEvent ──────────────────────────────────────────────────────

type Simplify<T> = { [K in keyof T]: T[K] } & {};

export interface EventPayloadByEvent {
	[Event.PlayerError]: PlayerErrorEvent;
	[Event.PlaybackState]: PlaybackState;
	[Event.PlaybackError]: PlaybackErrorEvent;
	[Event.PlaybackQueueEnded]: PlaybackQueueEndedEvent;
	[Event.PlaybackActiveTrackChanged]: PlaybackActiveTrackChangedEvent;
	[Event.PlaybackPlayWhenReadyChanged]: PlaybackPlayWhenReadyChangedEvent;
	[Event.PlaybackProgressUpdated]: PlaybackProgressUpdatedEvent;
	[Event.RemotePlay]: never;
	[Event.RemotePlayPause]: never;
	[Event.RemotePlayId]: RemotePlayIdEvent;
	[Event.RemotePlaySearch]: RemotePlaySearchEvent;
	[Event.RemotePause]: never;
	[Event.RemoteStop]: never;
	[Event.RemoteSkip]: RemoteSkipEvent;
	[Event.RemoteNext]: never;
	[Event.RemotePrevious]: never;
	[Event.RemoteJumpForward]: RemoteJumpForwardEvent;
	[Event.RemoteJumpBackward]: RemoteJumpBackwardEvent;
	[Event.RemoteSeek]: RemoteSeekEvent;
	[Event.RemoteSetRating]: RemoteSetRatingEvent;
	[Event.RemoteDuck]: RemoteDuckEvent;
	[Event.RemoteLike]: never;
	[Event.RemoteDislike]: never;
	[Event.RemoteBookmark]: never;
	[Event.PlaybackResume]: PlaybackResumeEvent;
	[Event.MetadataChapterReceived]: AudioMetadataReceivedEvent;
	[Event.MetadataTimedReceived]: AudioMetadataReceivedEvent;
	[Event.MetadataCommonReceived]: AudioCommonMetadataReceivedEvent;
	[Event.AndroidConnectorConnected]: AndroidControllerConnectedEvent;
	[Event.AndroidConnectorDisconnected]: AndroidControllerDisconnectedEvent;
	[Event.SabrDownloadProgress]: { outputPath: string; progress: number };
	[Event.SabrReloadPlayerResponse]: { outputPath: string; token: string | null };
	[Event.SabrRefreshPoToken]: { outputPath: string; reason: "proactive" | "expired" };
}

export type EventPayloadByEventWithType = { [K in keyof EventPayloadByEvent]: EventPayloadByEvent[K] extends never ? { type: K } : Simplify<EventPayloadByEvent[K] & { type: K }> };

// ─── Subscription ─────────────────────────────────────────────────────────────

export interface Subscription {
	remove: () => void;
}
