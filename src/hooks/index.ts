import { useEffect, useRef, useState, startTransition } from "react";

import { getProgress, getActiveTrack, getPlaybackState, getPlayWhenReady, addEventListener } from "../everything_player";

import { Event, State } from "../constants";
import type { EventPayloadByEvent, EventPayloadByEventWithType, PlaybackState, Progress, Track } from "../types";

// ─── useTrackPlayerEvents ─────────────────────────────────────────────────────

/**
 * Attaches a handler to the given EverythingPlayer events and cleans up on unmount.
 */
export function useTrackPlayerEvents<T extends Event[], H extends (data: EventPayloadByEventWithType[T[number]]) => void>(events: T, handler: H): void {
	const savedHandler = useRef(handler);
	savedHandler.current = handler;

	const eventsKey = events.join("\0");

	useEffect(() => {
		if (__DEV__) {
			const allowedTypes = Object.values(Event);
			const invalidTypes = events.filter((e) => !allowedTypes.includes(e));
			if (invalidTypes.length) {
				console.warn(`[EverythingPlayer] Unknown events passed to useTrackPlayerEvents: ${invalidTypes.join(", ")}`);
			}
		}

		const subs = events.map((type) =>
			addEventListener(type, (payload: EventPayloadByEvent[typeof type]) => {
				// @ts-expect-error - payload type is correct per the event mapping
				savedHandler.current({ ...payload, type });
			})
		);

		return () => subs.forEach((sub) => sub.remove());
	}, [eventsKey]);
}

// ─── useProgress ─────────────────────────────────────────────────────────────

const INITIAL_PROGRESS: Progress = { position: 0, duration: 0, buffered: 0 };

/**
 * Polls for track progress at the given interval (milliseconds, default 1000).
 */
export function useProgress(updateInterval = 1000): Progress {
	const [state, setState] = useState<Progress>(INITIAL_PROGRESS);

	useTrackPlayerEvents([Event.PlaybackActiveTrackChanged], () => {
		setState(INITIAL_PROGRESS);
	});

	useEffect(() => {
		let mounted = true;

		const poll = async () => {
			try {
				const { position, duration, buffered } = await getProgress();
				if (!mounted) return;
				setState((cur) => (position === cur.position && duration === cur.duration && buffered === cur.buffered ? cur : { position, duration, buffered }));
			} catch {
				// ignore — throws before setup
			}
			if (!mounted) return;
			await new Promise<void>((res) => setTimeout(res, updateInterval));
			if (!mounted) return;
			poll();
		};

		poll();
		return () => {
			mounted = false;
		};
	}, [updateInterval]);

	return state;
}

// ─── useActiveTrack ───────────────────────────────────────────────────────────

/**
 * Returns the currently active track, updated on track changes.
 */
export function useActiveTrack(): Track | undefined {
	const [track, setTrack] = useState<Track | undefined>();

	useEffect(() => {
		let unmounted = false;
		getActiveTrack()
			.then((t) => {
				if (!unmounted) setTrack((cur) => cur ?? t ?? undefined);
			})
			.catch(() => {
				/* not yet set up */
			});
		return () => {
			unmounted = true;
		};
	}, []);

	useTrackPlayerEvents([Event.PlaybackActiveTrackChanged], ({ track: newTrack }) => {
		setTrack(newTrack ?? undefined);
	});

	return track;
}

// ─── usePlaybackState ─────────────────────────────────────────────────────────

/**
 * Returns the current playback state. `state` is `undefined` while the initial
 * state is being fetched from native.
 */
export function usePlaybackState(): PlaybackState | { state: undefined } {
	const [playbackState, setPlaybackState] = useState<PlaybackState | { state: undefined }>({ state: undefined });

	useEffect(() => {
		let mounted = true;

		getPlaybackState()
			.then((s) => {
				if (!mounted) return;
				setPlaybackState((cur) => (cur.state ? cur : s));
			})
			.catch(() => {
				/* not yet set up */
			});

		const sub = addEventListener(Event.PlaybackState, (s) => {
			startTransition(() => setPlaybackState(s));
		});

		return () => {
			mounted = false;
			sub.remove();
		};
	}, []);

	return playbackState;
}

// ─── usePlayWhenReady ─────────────────────────────────────────────────────────

export function usePlayWhenReady(): boolean | undefined {
	const [pwr, setPwr] = useState<boolean | undefined>();

	useEffect(() => {
		let mounted = true;
		getPlayWhenReady()
			.then((v) => {
				if (!mounted) return;
				setPwr((cur) => cur ?? v);
			})
			.catch(() => {
				/* not yet set up */
			});

		const sub = addEventListener(Event.PlaybackPlayWhenReadyChanged, ({ playWhenReady }) => {
			setPwr(playWhenReady);
		});

		return () => {
			mounted = false;
			sub.remove();
		};
	}, []);

	return pwr;
}

// ─── useIsPlaying ─────────────────────────────────────────────────────────────

/**
 * Returns `playing` (whether the UI should show a Pause button) and
 * `bufferingDuringPlay` (whether to show a loading spinner).
 */
export function useIsPlaying(): { playing: boolean | undefined; bufferingDuringPlay: boolean | undefined } {
	const { state } = usePlaybackState();
	const pwr = usePlayWhenReady();

	if (pwr === undefined || state === undefined) {
		return { playing: undefined, bufferingDuringPlay: undefined };
	}

	const isLoading = state === State.Loading || state === State.Buffering;
	const isEnded = state === State.Error || state === State.Ended || state === State.None;

	return { playing: pwr && !isEnded, bufferingDuringPlay: pwr && isLoading };
}
