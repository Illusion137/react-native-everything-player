import { createRequire } from "module";
import { generateContentBoundPoToken } from "./generate_potoken.js";

const require = createRequire(import.meta.url);
const rnBridge = require("rn-bridge");

{
	const wasmUnsupported = async () => Promise.reject(new Error("WebAssembly not available"));
	const wasmUnsupportedSync = () => {
		throw new Error("WebAssembly not available");
	};
	globalThis.WebAssembly = {
		instantiate: wasmUnsupported,
		instantiateStreaming: wasmUnsupported,
		compile: wasmUnsupported,
		compileStreaming: wasmUnsupported,
		validate: () => false,
		Module: wasmUnsupportedSync,
		Instance: wasmUnsupportedSync,
		Memory: wasmUnsupportedSync,
		Table: wasmUnsupportedSync,
		Global: wasmUnsupportedSync,
		Tag: wasmUnsupportedSync,
		Exception: wasmUnsupportedSync
	};
}

process.on("uncaughtException", (e) => {
	try {
		rnBridge.channel.post("potoken", JSON.stringify({ error: `[uncaught] ${e.message}` }));
	} catch {}
});

process.on("unhandledRejection", (reason) => {
	console.error("[unhandled rejection]", String(reason));
});

rnBridge.channel.on("potoken", async (message) => {
	try {
		const parsed = JSON.parse(message ?? "{}");
		const { content_binding: contentBinding, context } = parsed;
		if (!contentBinding) throw new Error("No content_binding provided");
		if (!context) throw new Error("No context provided");
		const poToken = await generateContentBoundPoToken(contentBinding, context);
		rnBridge.channel.post("potoken", JSON.stringify({ poToken, identifier: contentBinding }));
	} catch (e) {
		const parsed = JSON.parse(message ?? "{}");
		rnBridge.channel.post("potoken", JSON.stringify({ error: e.message, identifier: parsed.content_binding }));
	}
});

rnBridge.channel.send(`Node was initialized with v${process.version}.`);
