import { AppRegistry, Platform } from "react-native";
import { NitroModules } from "react-native-nitro-modules";
import type { AnyMap } from "react-native-nitro-modules";

import type { RepeatMode } from "./constants";
import { Event } from "./constants";
import type { AddTrack, EventPayloadByEvent, NowPlayingMetadata, PlaybackState, PlayerOptions, Progress, SabrDownloadParams, ServiceHandler, Track, TrackMetadataBase, UpdateOptions } from "./types";
import type { NativeEverythingPlayer } from "./NativeEverythingPlayer.nitro";
import resolveAssetSource from "./resolveAssetSource";

const TrackPlayer = NitroModules.createHybridObject<NativeEverythingPlayer>("NativeEverythingPlayer");

const isAndroid = Platform.OS === "android";

// MARK: - Helpers

function resolveImportedAssetOrPath(pathOrAsset: string | number | undefined) {
	return pathOrAsset === undefined ? undefined : typeof pathOrAsset === "string" ? pathOrAsset : resolveImportedAsset(pathOrAsset);
}

function resolveImportedAsset(id?: number) {
	return id ? ((resolveAssetSource(id) as { uri: string } | null) ?? undefined) : undefined;
}

function resolveTrackAssets(track: AddTrack) {
	return { ...track, url: resolveImportedAssetOrPath(track.url), artwork: resolveImportedAssetOrPath(track.artwork) };
}

function normalizeNitroValue(value: unknown): unknown {
	if (value == null) return null;
	if (typeof value === "string" || typeof value === "boolean") return value;
	if (typeof value === "number") return Number.isFinite(value) ? value : null;
	if (Array.isArray(value)) return value.map((item) => normalizeNitroValue(item));
	if (typeof value === "object") {
		const normalized: Record<string, unknown> = {};
		for (const [key, nestedValue] of Object.entries(value)) {
			if (nestedValue !== undefined) normalized[key] = normalizeNitroValue(nestedValue);
		}
		return normalized;
	}
	return String(value);
}

// MARK: - Event System (Nitro callback multiplexer)

type EventCallback<T extends Event> = EventPayloadByEvent[T] extends never ? () => void : (event: EventPayloadByEvent[T]) => void;

const listenerMap = new Map<Event, Set<(data: any) => void>>();

// Map Event → Nitro callback property name
const eventCallbackMap: Partial<Record<Event, keyof NativeEverythingPlayer>> = {
	[Event.PlaybackState]: "onPlaybackStateChanged",
	[Event.PlaybackError]: "onPlaybackError",
	[Event.PlayerError]: "onPlaybackError",
	[Event.PlaybackQueueEnded]: "onPlaybackQueueEnded",
	[Event.PlaybackActiveTrackChanged]: "onActiveTrackChanged",
	[Event.PlaybackPlayWhenReadyChanged]: "onPlayWhenReadyChanged",
	[Event.PlaybackProgressUpdated]: "onProgressUpdated",
	[Event.PlaybackResume]: "onPlaybackResume",
	[Event.RemotePlay]: "onRemotePlay",
	[Event.RemotePause]: "onRemotePause",
	[Event.RemoteStop]: "onRemoteStop",
	[Event.RemoteNext]: "onRemoteNext",
	[Event.RemotePrevious]: "onRemotePrevious",
	[Event.RemoteJumpForward]: "onRemoteJumpForward",
	[Event.RemoteJumpBackward]: "onRemoteJumpBackward",
	[Event.RemoteSeek]: "onRemoteSeek",
	[Event.RemoteSetRating]: "onRemoteSetRating",
	[Event.RemoteDuck]: "onRemoteDuck",
	[Event.RemoteLike]: "onRemoteLike",
	[Event.RemoteDislike]: "onRemoteDislike",
	[Event.RemoteBookmark]: "onRemoteBookmark",
	[Event.MetadataChapterReceived]: "onChapterMetadataReceived",
	[Event.MetadataTimedReceived]: "onTimedMetadataReceived",
	[Event.MetadataCommonReceived]: "onCommonMetadataReceived",
	[Event.AndroidConnectorConnected]: "onAndroidControllerConnected",
	[Event.AndroidConnectorDisconnected]: "onAndroidControllerDisconnected",
	[Event.SabrDownloadProgress]: "onSabrDownloadProgress",
	[Event.SabrReloadPlayerResponse]: "onSabrReloadPlayerResponse",
	[Event.SabrRefreshPoToken]: "onSabrRefreshPoToken"
};

function syncNitroCallback(cbProp: keyof NativeEverythingPlayer, listeners: Set<(d: any) => void>) {
	if (listeners.size > 0) {
		(TrackPlayer as any)[cbProp] = (data: any) => {
			for (const l of listeners) l(data);
		};
	} else {
		(TrackPlayer as any)[cbProp] = null;
	}
}

export function addEventListener<T extends Event>(event: T, listener: EventCallback<T>): { remove: () => void } {
	const cbProp = eventCallbackMap[event];
	if (!cbProp) {
		// Unknown event — no-op subscription
		return { remove: () => {} };
	}
	if (!listenerMap.has(event)) listenerMap.set(event, new Set());

	const set = listenerMap.get(event)!;

	const fn = listener as (data: any) => void;
	set.add(fn);
	syncNitroCallback(cbProp, set);
	return {
		remove: () => {
			set.delete(fn);
			if (cbProp) syncNitroCallback(cbProp, set);
		}
	};
}

// MARK: - General API

/**
 * Initializes the player with the specified options.
 *
 * @param options The options to initialize the player with.
 * @see https://rntp.dev/docs/api/functions/lifecycle
 */
export async function setupPlayer(options: PlayerOptions = {}): Promise<void> {
	return TrackPlayer.setupPlayer(options as unknown as AnyMap);
}

/**
 * Register the playback service. The service will run as long as the player runs.
 */
export function registerPlaybackService(factory: () => ServiceHandler) {
	if (isAndroid) {
		AppRegistry.registerHeadlessTask("TrackPlayer", factory);
	} else if (Platform.OS === "web") {
		factory()();
	} else {
		setImmediate(factory());
	}
}

// MARK: - Queue API

/**
 * Adds one or more tracks to the queue.
 *
 * @param tracks The tracks to add to the queue.
 * @param insertBeforeIndex (Optional) The index to insert the tracks before.
 * By default the tracks will be added to the end of the queue.
 */
export async function add(tracks: AddTrack[] | AddTrack, insertBeforeIndex?: number): Promise<number | undefined>;
export async function add(tracks: AddTrack | AddTrack[], insertBeforeIndex = -1): Promise<number | undefined> {
	const addTracks = Array.isArray(tracks) ? tracks : [tracks];
	if (addTracks.length < 1) return undefined;
	const result = await TrackPlayer.add(addTracks.map(resolveTrackAssets) as unknown as AnyMap[], insertBeforeIndex);
	return result ?? undefined;
}

/**
 * Replaces the current track or loads the track as the first in the queue.
 *
 * @param track The track to load.
 */
export async function load(track: AddTrack): Promise<number | undefined> {
	const result = await TrackPlayer.load(resolveTrackAssets(track) as unknown as AnyMap);
	return result ?? undefined;
}

/**
 * Move a track within the queue.
 *
 * @param fromIndex The index of the track to be moved.
 * @param toIndex The index to move the track to. If the index is larger than
 * the size of the queue, then the track is moved to the end of the queue.
 */
export async function move(fromIndex: number, toIndex: number): Promise<void> {
	return TrackPlayer.move(fromIndex, toIndex);
}

/**
 * Removes multiple tracks from the queue by their indexes.
 *
 * If the current track is removed, the next track will activated. If the
 * current track was the last track in the queue, the first track will be
 * activated.
 *
 * @param indexes The indexes of the tracks to be removed.
 */
export async function remove(indexOrIndexes: number[] | number): Promise<void>;
export async function remove(indexOrIndexes: number | number[]): Promise<void> {
	return TrackPlayer.remove(Array.isArray(indexOrIndexes) ? indexOrIndexes : [indexOrIndexes]);
}

/**
 * Clears any upcoming tracks from the queue.
 */
export async function removeUpcomingTracks(): Promise<void> {
	return TrackPlayer.removeUpcomingTracks();
}

/**
 * Skips to a track in the queue.
 *
 * @param index The index of the track to skip to.
 * @param initialPosition (Optional) The initial position to seek to in seconds.
 */
export async function skip(index: number, initialPosition = -1): Promise<void> {
	return TrackPlayer.skip(index, initialPosition);
}

/**
 * Skips to the next track in the queue.
 *
 * @param initialPosition (Optional) The initial position to seek to in seconds.
 */
export async function skipToNext(initialPosition = -1): Promise<void> {
	return TrackPlayer.skipToNext(initialPosition);
}

/**
 * Skips to the previous track in the queue.
 *
 * @param initialPosition (Optional) The initial position to seek to in seconds.
 */
export async function skipToPrevious(initialPosition = -1): Promise<void> {
	return TrackPlayer.skipToPrevious(initialPosition);
}

// MARK: - Control Center / Notifications API

/**
 * Updates the configuration for the components.
 *
 * @param options The options to update.
 * @see https://rntp.dev/docs/api/functions/player#updateoptionsoptions
 */
export async function updateOptions(options: UpdateOptions = {}): Promise<void> {
	return TrackPlayer.updateOptions({ ...options, android: { ...(options.android as Record<string, unknown>) } } as unknown as AnyMap);
}

/**
 * Updates the metadata of a track in the queue. If the current track is updated,
 * the notification and the Now Playing Center will be updated accordingly.
 *
 * @param trackIndex The index of the track whose metadata will be updated.
 * @param metadata The metadata to update.
 */
export async function updateMetadataForTrack(trackIndex: number, metadata: TrackMetadataBase): Promise<void> {
	return TrackPlayer.updateMetadataForTrack(trackIndex, { ...metadata, artwork: resolveImportedAssetOrPath(metadata.artwork) } as unknown as AnyMap);
}

/**
 * Updates the metadata content of the notification (Android) and the Now Playing Center (iOS)
 * without affecting the data stored for the current track.
 */
export async function updateNowPlayingMetadata(metadata: NowPlayingMetadata): Promise<void> {
	return TrackPlayer.updateNowPlayingMetadata({ ...metadata, artwork: resolveImportedAssetOrPath(metadata.artwork) } as unknown as AnyMap);
}

// MARK: - Player API

/**
 * Resets the player stopping the current track and clearing the queue.
 */
export async function reset(): Promise<void> {
	return TrackPlayer.reset();
}

/**
 * Plays or resumes the current track.
 */
export async function play(): Promise<void> {
	return TrackPlayer.play();
}

/**
 * Pauses the current track.
 */
export async function pause(): Promise<void> {
	return TrackPlayer.pause();
}

/**
 * Stops the current track.
 */
export async function stop(): Promise<void> {
	return TrackPlayer.stop();
}

/**
 * Sets whether the player will play automatically when it is ready to do so.
 */
export async function setPlayWhenReady(playWhenReady: boolean): Promise<boolean> {
	await TrackPlayer.setPlayWhenReady(playWhenReady);
	return playWhenReady;
}

/**
 * Gets whether the player will play automatically when it is ready to do so.
 */
export async function getPlayWhenReady(): Promise<boolean> {
	return TrackPlayer.getPlayWhenReady();
}

/**
 * Seeks to a specified time position in the current track.
 *
 * @param position The position to seek to in seconds.
 */
export async function seekTo(position: number): Promise<void> {
	return TrackPlayer.seekTo(position);
}

/**
 * Seeks by a relative time offset in the current track.
 *
 * @param offset The time offset to seek by in seconds.
 */
export async function seekBy(offset: number): Promise<void> {
	return TrackPlayer.seekBy(offset);
}

/**
 * Sets the volume of the player.
 *
 * @param volume The volume as a number between 0 and 1.
 */
export async function setVolume(level: number): Promise<void> {
	return TrackPlayer.setVolume(level);
}

/**
 * Sets the playback rate.
 *
 * @param rate The playback rate to change to, where 0.5 would be half speed,
 * 1 would be regular speed, 2 would be double speed etc.
 */
export async function setRate(rate: number): Promise<void> {
	return TrackPlayer.setRate(rate);
}

/**
 * Sets the queue.
 *
 * @param tracks The tracks to set as the queue.
 */
export async function setQueue(tracks: Track[]): Promise<void> {
	return TrackPlayer.setQueue(tracks as unknown as AnyMap[]);
}

/**
 * Sets the queue repeat mode.
 *
 * @param repeatMode The repeat mode to set.
 */
export async function setRepeatMode(mode: RepeatMode): Promise<RepeatMode> {
	await TrackPlayer.setRepeatMode(mode);
	return mode;
}

// MARK: - Getters

/**
 * Gets the volume of the player as a number between 0 and 1.
 */
export async function getVolume(): Promise<number> {
	return TrackPlayer.getVolume();
}

/**
 * Gets the playback rate.
 */
export async function getRate(): Promise<number> {
	return TrackPlayer.getRate();
}

/**
 * Gets a track object from the queue.
 *
 * @param index The index of the track.
 */
export async function getTrack(index: number): Promise<Track | undefined> {
	return (await TrackPlayer.getTrack(index)) as unknown as Track | undefined;
}

/**
 * Gets the whole queue.
 */
export async function getQueue(): Promise<Track[]> {
	return (await TrackPlayer.getQueue()) as unknown as Track[];
}

/**
 * Gets the index of the active track in the queue or undefined if there is no
 * current track.
 */
export async function getActiveTrackIndex(): Promise<number | undefined> {
	return (await TrackPlayer.getActiveTrackIndex()) ?? undefined;
}

/**
 * Gets the active track or undefined if there is no current track.
 */
export async function getActiveTrack(): Promise<Track | undefined> {
	return ((await TrackPlayer.getActiveTrack()) as unknown as Track) ?? undefined;
}

/**
 * Gets information on the progress of the currently active track.
 */
export async function getProgress(): Promise<Progress> {
	return (await TrackPlayer.getProgress()) as unknown as Progress;
}

/**
 * Gets the playback state of the player.
 */
export async function getPlaybackState(): Promise<PlaybackState> {
	return (await TrackPlayer.getPlaybackState()) as PlaybackState;
}

/**
 * Gets the queue repeat mode.
 */
export async function getRepeatMode(): Promise<RepeatMode> {
	return TrackPlayer.getRepeatMode() as Promise<RepeatMode>;
}

/**
 * Retries the current item when the playback state is `State.Error`.
 */
export async function retry() {
	return TrackPlayer.retry();
}

/**
 * Sets the equalizer bands. Each value represents gain in decibels (typically -12 to +12).
 * @param bands Array of gain values for each frequency band
 */
export async function setEqualizer(bands: number[]): Promise<void> {
	return TrackPlayer.setEqualizer(bands.map((gain) => ({ gain })));
}

/**
 * Gets the current equalizer bands.
 * @returns Array of gain values for each frequency band
 */
export async function getEqualizer(): Promise<number[]> {
	const bands = await TrackPlayer.getEqualizer();
	const result = bands.map((b) => (typeof b === "object" && b !== null && "gain" in b ? (b.gain as number) : 0));
	return result.length > 0 ? result : [0, 0, 0, 0, 0];
}

/**
 * Removes the equalizer, resetting audio to normal.
 */
export async function removeEqualizer(): Promise<void> {
	return TrackPlayer.removeEqualizer();
}

/**
 * acquires the wake lock of MusicService (android only.)
 */
export async function acquireWakeLock() {
	if (!isAndroid) return;
	TrackPlayer.acquireWakeLock();
}

/**
 * releases the wake lock of MusicService (android only.)
 */
export async function abandonWakeLock() {
	if (!isAndroid) return;
	TrackPlayer.abandonWakeLock();
}

/**
 * get onStartCommandIntent is null or not (Android only.).
 */
export async function validateOnStartCommandIntent(): Promise<boolean> {
	if (!isAndroid) return true;
	return TrackPlayer.validateOnStartCommandIntent();
}

// MARK: - Crossfade

/**
 * Sets the crossfade duration for smooth transitions between tracks.
 * Respects track trimming — crossfade triggers relative to `endTime` when set.
 * Set to 0 to disable.
 * @param seconds Crossfade duration in seconds
 */
export async function setCrossFade(seconds: number): Promise<void> {
	return TrackPlayer.setCrossFade(seconds);
}

// MARK: - SABR (YouTube streaming)

/**
 * Downloads a YouTube SABR audio stream to a local file.
 * Emits `Event.SabrDownloadProgress` events during download.
 * @param params SABR params including sabrServerUrl, sabrUstreamerConfig, etc.
 * @param outputPath Destination file path for the downloaded audio
 * @returns The output path on success
 */
export async function downloadSabrStream(params: SabrDownloadParams, outputPath: string): Promise<string> {
	return TrackPlayer.downloadSabrStream(normalizeNitroValue(params) as AnyMap, outputPath);
}

export async function downloadSabr(params: SabrDownloadParams, outputPath: string): Promise<string> {
	return downloadSabrStream(params, outputPath);
}

/**
 * Updates the streaming URL and ustreamer config of an active SABR download.
 */
export async function updateSabrDownloadStream(outputPath: string, serverUrl: string, ustreamerConfig: string): Promise<void> {
	return TrackPlayer.updateSabrDownloadStream(outputPath, serverUrl, ustreamerConfig);
}

export async function updateSabrStream(outputPath: string, serverUrl: string, ustreamerConfig: string): Promise<void> {
	return updateSabrDownloadStream(outputPath, serverUrl, ustreamerConfig);
}

/**
 * Updates the PoToken of an active SABR download.
 */
export async function updateSabrDownloadPoToken(outputPath: string, poToken: string): Promise<void> {
	return TrackPlayer.updateSabrDownloadPoToken(outputPath, poToken);
}

export async function updateSabrPoToken(outputPath: string, poToken: string): Promise<void> {
	return updateSabrDownloadPoToken(outputPath, poToken);
}

/**
 * Updates the PoToken for the currently playing SABR stream.
 */
export async function updateSabrPlaybackPoToken(poToken: string): Promise<void> {
	return TrackPlayer.updateSabrPlaybackPoToken(poToken);
}

export async function updatePlaybackPoToken(poToken: string): Promise<void> {
	return updateSabrPlaybackPoToken(poToken);
}

/**
 * Updates the streaming URL and ustreamer config for the currently playing SABR stream.
 * Equivalent of updatePlaybackPoToken but for a fresh player response / ustreamer config.
 */
export async function updateSabrPlaybackStream(serverUrl: string, ustreamerConfig: string): Promise<void> {
	return TrackPlayer.updateSabrPlaybackStream(serverUrl, ustreamerConfig);
}

export async function updateSabrStreamPlayback(serverUrl: string, ustreamerConfig: string): Promise<void> {
	return updateSabrPlaybackStream(serverUrl, ustreamerConfig);
}
