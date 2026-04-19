import type { HybridView, HybridViewProps, HybridViewMethods } from "react-native-nitro-modules";

interface VideoViewProps extends HybridViewProps {
	resizeMode: string;
}

export interface VideoViewMethods extends HybridViewMethods {
	onAttach(): void;
	onDetach(): void;
}

export type NativeVideoView = HybridView<VideoViewProps, VideoViewMethods>;
