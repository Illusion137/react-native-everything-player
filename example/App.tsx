import { useEffect, useState } from "react";
import { Button, ScrollView, StyleSheet, Text, View } from "react-native";
import EverythingPlayer, { Event, State, type PlaybackState } from "react-native-everything-player";

export default function App() {
    const [playbackState, setPlaybackState] = useState<PlaybackState | null>(null);

    useEffect(() => {
        const setup = async () => {
            await EverythingPlayer.setupPlayer({
                autoHandleInterruptions: true,
                autoUpdateMetadata: true
            });
            await EverythingPlayer.updateOptions({
                progressUpdateEventInterval: 1
            });
        };

        void setup();

        const playbackStateSubscription = EverythingPlayer.addEventListener(Event.PlaybackState, (state: PlaybackState) => {
            setPlaybackState(state);
        });

        return () => {
            playbackStateSubscription.remove();
            void EverythingPlayer.reset();
        };
    }, []);

    return (
        <ScrollView contentContainerStyle={styles.content}>
            <Text style={styles.title}>Everything Player Example</Text>
            <Text style={styles.subtitle}>Plain Expo consumer app for Nitro module smoke-testing.</Text>

            <View style={styles.card}>
                <Text style={styles.label}>Current state</Text>
                <Text style={styles.value}>{playbackState?.state ?? State.None}</Text>
            </View>

            <View style={styles.actions}>
                <Button title="Play" onPress={() => void EverythingPlayer.play()} />
                <Button title="Pause" onPress={() => void EverythingPlayer.pause()} />
                <Button title="Reset" onPress={() => void EverythingPlayer.reset()} />
            </View>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    content: {
        flexGrow: 1,
        gap: 16,
        padding: 24,
        justifyContent: "center",
        backgroundColor: "#f5f5f5"
    },
    title: {
        fontSize: 28,
        fontWeight: "700",
        color: "#111827"
    },
    subtitle: {
        fontSize: 16,
        color: "#4b5563"
    },
    card: {
        padding: 16,
        borderRadius: 16,
        backgroundColor: "#ffffff"
    },
    label: {
        fontSize: 14,
        color: "#6b7280"
    },
    value: {
        marginTop: 8,
        fontSize: 20,
        fontWeight: "600",
        color: "#111827"
    },
    actions: {
        gap: 12
    }
});
