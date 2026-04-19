declare module "@origin/youtube_dl/ytdl_polyfill";
declare module "@origin/youtube_dl/index" {
	export const YouTubeDL: any;
}
declare module "@native/fs/fs" {
	export const load_native_fs: (...args: any[]) => any;
}
declare module "@native/potoken/potoken" {
	export const load_native_potoken: (...args: any[]) => any;
}
