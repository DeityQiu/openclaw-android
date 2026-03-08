# OpenClaw Android AI Assistant

An AI assistant system for Android, built as AOSP system services.
Allows OpenClaw (AI agent) to control Android devices via WebSocket/CDP protocol.

## Architecture

```
OpenClaw Agent (server)
    ↓ WebSocket/CDP (port 7788)
OpenClawBridgeService (AOSP system service)
    ↓ Android APIs (system-level, no permission prompts)
Device: tap / swipe / screenshot / shell / UI tree / launch app
```

## Components

| Directory | Description |
|-----------|-------------|
| `bridge-service/` | AOSP system service — WebSocket server + CDP dispatcher + device controller |
| `config-app/` | Native Android config UI + voice listener service |
| `openclaw-node/` | Node.js OpenClaw tool provider |
| `aosp-integration/` | AOSP integration scripts and patches |

## Build

Targets: Pixel 1 (sailfish) and Pixel 3a (sargo), Android 10

```bash
source build/envsetup.sh
lunch aosp_sailfish-userdebug
make -j50
```

## Protocol

CDP-style JSON-RPC over WebSocket:
```json
{"id": 1, "method": "Input.tap", "params": {"x": 500, "y": 800}}
{"id": 1, "result": {"success": true}}
```
