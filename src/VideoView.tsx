import { useRef } from "react";
import { StyleSheet, type ViewStyle } from "react-native";
import { callback, getHostComponent } from "react-native-nitro-modules";
import type { HybridViewProps } from "react-native-nitro-modules";
import type { NativeVideoView, VideoViewMethods } from "./NativeVideoView.nitro";

// ─── View Config ──────────────────────────────────────────────────────────────

interface _VideoViewProps extends HybridViewProps {
	resizeMode: string;
}

const NativeVideoViewComponent = getHostComponent<_VideoViewProps, VideoViewMethods>(
	"NativeVideoView",
	() => ({
		uiViewClassName: "NativeVideoView",
		bubblingEventTypes: {},
		directEventTypes: {},
		validAttributes: {
			resizeMode: true,
		},
	})
);

// ─── Public API ───────────────────────────────────────────────────────────────

export type VideoResizeMode = "contain" | "cover" | "fill";

export interface VideoViewProps {
	style?: ViewStyle;
	resizeMode?: VideoResizeMode;
}

/**
 * A video rendering surface that auto-connects to the global EverythingPlayer.
 *
 * - For non-SABR tracks (HLS, DASH, progressive MP4, local video) the player's
 *   AVPlayer/ExoPlayer is rendered directly.
 * - For SABR tracks, video is enabled automatically when this view is mounted.
 * - When the active track is audio-only, the track's `artwork` thumbnail is shown.
 *
 * @example
 * ```tsx
 * <VideoView style={{ width: '100%', aspectRatio: 16/9 }} resizeMode="contain" />
 * ```
 */
export function VideoView({ style, resizeMode = "contain" }: VideoViewProps) {
	const refHolder = useRef<NativeVideoView | null>(null);

	return (
		<NativeVideoViewComponent
			style={[styles.fill, style]}
			resizeMode={resizeMode}
			hybridRef={callback((ref: NativeVideoView) => {
				const prev = refHolder.current;
				if (prev && prev !== ref) {
					try {
						prev.onDetach();
					} catch {}
				}
				refHolder.current = ref ?? null;
				if (ref) {
					try {
						ref.onAttach();
					} catch {}
				}
			})}
		/>
	);
}

const styles = StyleSheet.create({
	fill: { flex: 1 },
});
