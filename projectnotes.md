# LilyAi --- Implementation Project Notes

> **Project:** LilyAi\
> **Platform:** Native Android (target phone: Android 11, API 30, so
> minSdk 30)\
> **Primary language:** Kotlin\
> **Build:** Gradle + GitHub Actions (development is phone-only, so
> builds run in GitHub Actions)\
> **Cloud database:** Turso\
> **AI providers:** Qwen3-4B (main, runs on the device) + Groq (backup) +
> additional model providers\
> **Core concept:** Native Android AI assistant with a Game Space
> subsystem and phone-control capabilities.

------------------------------------------------------------------------

## 1. Project Vision

**Primary goal:** game assistance and autoplay. AI is the main feature.
The Game Space functions were added as a replica base because of their
performance features. The target games are light games: chess, Miscrits
World Adventure and Umamusume. (Mobile Legends in the examples below is
only an example.)

LilyAi is a native Android AI assistant that can:

1.  Talk with the user through text and voice.
2.  Operate Android features through controlled tools and services.
3.  Provide a Game Space experience comparable to OEM gaming assistants.
4.  Display an assistant overlay above other applications, where Android
    permissions allow it.
5.  Analyze the current screen when the user explicitly requests it and
    the required capture permissions are available.
6.  Use Qwen3-4B as the main model and Groq as the backup, plus other AI
    providers, through a provider/model router.
7.  Store persistent user preferences, game profiles, conversations,
    memories, and useful statistics using local storage plus Turso cloud
    synchronization.
8.  Continue functioning offline for features that do not require cloud
    AI.
9.  Play supported games automatically (autoplay) through user-started
    automation using the accessibility service.

### Core principle

> **Kotlin controls the phone. AI provides intelligence. Turso provides
> synchronized cloud state.**

Python is optional and must not be required for normal Android/Game
Space functionality.

------------------------------------------------------------------------

# 2. High-Level Architecture

``` text
                              LILYAI
                                 |
          +----------------------+----------------------+
          |                      |                      |
          v                      v                      v
   Android Native           AI Engine              Cloud Data
      Kotlin                AI Providers             Turso
          |                      |                      |
          |                +-----+------+              |
          |                |            |              |
          |            Qwen3-4B       Groq             |
          |             (main)      (backup)           |
          |                                             |
          +----------------------+----------------------+
                                 |
                          Android APIs
```

Detailed application architecture:

``` text
LilyAi
|
+-- Android Native / Kotlin
|   |
|   +-- Game Space
|   +-- Game Assistant Overlay
|   +-- Android Services
|   +-- Accessibility
|   +-- MediaProjection
|   +-- Device / Network Monitoring
|   +-- Local Database
|   +-- AI Client
|   +-- Tool Registry
|
+-- AI Layer
|   |
|   +-- Model Router
|   +-- Qwen3-4B Provider (main)
|   +-- Groq Provider (backup)
|   +-- Other AI Providers
|   +-- Vision Provider
|   +-- Conversation Manager
|   +-- Tool Calling
|
+-- Cloud
|   |
|   +-- Turso
|   +-- Sync Manager
|   +-- Cloud Memory
|   +-- Cloud Statistics
|
+-- Optional Python Backend
|   |
|   +-- Advanced AI orchestration
|   +-- Vision processing
|   +-- Custom AI services
|
+-- CI/CD
    |
    +-- GitHub Actions
    +-- Build
    +-- Test
    +-- Lint
    +-- Release APK
```

------------------------------------------------------------------------

# 3. Native Android / Kotlin Rule

Kotlin is the primary implementation language.

Android-native functionality must remain independent of Python.

Kotlin is responsible for:

-   Android UI
-   Game detection
-   Game launching
-   Game sessions
-   Overlay services
-   Floating windows
-   Accessibility services
-   MediaProjection
-   Screenshot capture
-   Screen recording
-   Device information
-   Battery information
-   Network information
-   Gaming focus
-   Notifications/DND integrations where permitted
-   Brightness/rotation features where permitted
-   Android permissions
-   Game Mode APIs where supported
-   Local storage
-   Cloud API communication
-   AI API communication
-   AI tool execution

Python is optional.

If Python is introduced later, it should be treated as a backend/service
rather than a dependency of the Android app.

------------------------------------------------------------------------

# 4. Recommended Project Structure

``` text
LilyAi/
|
+-- app/
|   +-- src/main/
|       +-- java/com/lilyai/
|           |
|           +-- core/
|           |   +-- LilyAiApplication.kt
|           |   +-- permissions/
|           |   +-- security/
|           |   +-- capabilities/
|           |
|           +-- gamespace/
|           |   +-- GameSpaceActivity.kt
|           |   +-- GameRepository.kt
|           |   +-- GameDetector.kt
|           |   +-- GameSessionManager.kt
|           |   +-- GameProfile.kt
|           |   +-- GameLauncher.kt
|           |
|           +-- overlay/
|           |   +-- GameOverlayService.kt
|           |   +-- EdgeHandle.kt
|           |   +-- AssistantPanel.kt
|           |   +-- FloatingWindowManager.kt
|           |
|           +-- gaming/
|           |   +-- PerformanceManager.kt
|           |   +-- GamingFocusManager.kt
|           |   +-- NetworkMonitor.kt
|           |   +-- DeviceMonitor.kt
|           |   +-- GamingTimer.kt
|           |
|           +-- capture/
|           |   +-- ScreenshotManager.kt
|           |   +-- ScreenRecorder.kt
|           |
|           +-- accessibility/
|           |   +-- LilyAccessibilityService.kt
|           |
|           +-- ai/
|           |   +-- LilyAiEngine.kt
|           |   +-- ModelRouter.kt
|           |   +-- QwenClient.kt
|           |   +-- GroqClient.kt
|           |   +-- VisionClient.kt
|           |   +-- ToolRegistry.kt
|           |   +-- ConversationManager.kt
|           |
|           +-- data/
|               +-- local/
|               |   +-- LilyDatabase.kt
|               |   +-- GameDao.kt
|               |   +-- MemoryDao.kt
|               |   +-- ConversationDao.kt
|               |
|               +-- cloud/
|                   +-- TursoClient.kt
|                   +-- SyncManager.kt
|
+-- backend/                 # Optional
|   +-- Python services
|
+-- database/
|   +-- migrations/
|   +-- schema.sql
|
+-- .github/
|   +-- workflows/
|       +-- android.yml
|       +-- python.yml
|       +-- release.yml
|
+-- docs/
|   +-- architecture/
|   +-- permissions/
|   +-- api/
|
+-- README.md
+-- PROJECTNOTES.md
```

------------------------------------------------------------------------

# 5. Game Space Subsystem

Game Space should reproduce the common functionality found in OEM gaming
hubs while respecting Android's public APIs and device limitations.

## 5.1 Game Library

Features:

-   Scan installed applications.
-   Identify likely games.
-   Manually add games.
-   Remove games from Game Space without uninstalling them.
-   Launch games.
-   Display game icon/name.
-   Record last played.
-   Record total play time.
-   Track game sessions.
-   Open game-specific settings.

On Android 11, other apps are hidden from the app by default. Scanning
installed apps needs the QUERY_ALL_PACKAGES permission or a `<queries>`
list of game packages. A manual add list is the reliable fallback,
because Android has no flag that says an app is a game.

Suggested model:

``` text
Game
- id
- packageName
- name
- iconReference
- lastPlayed
- totalPlaytime
- createdAt
- updatedAt
```

------------------------------------------------------------------------

## 5.2 Game Profiles

Each game can have an independent profile.

Example:

``` text
Mobile Legends
- Performance: PERFORMANCE
- Gaming Focus: ON
- Overlay: ON
- Monitor: ON
- Brightness Lock: ON
- Rotation: LANDSCAPE
```

Profile fields:

``` text
GameProfile
- id
- gameId
- performanceMode
- notificationMode
- callMode
- brightnessLock
- brightnessLevel
- rotationMode
- overlayEnabled
- monitorEnabled
- networkMode
- enabledTools
```

------------------------------------------------------------------------

# 6. Game Assistant Overlay

The overlay is a major LilyAi feature.

Basic flow:

``` text
Game starts
    |
    v
Game detection
    |
    v
GameOverlayService
    |
    v
Edge handle appears
    |
    v
User swipes/taps
    |
    v
Game Assistant panel
```

Suggested toolbar:

``` text
+----------------------------+
|        LILYAI              |
+----------------------------+
| Performance                |
| Network                    |
| Gaming Focus               |
| Screenshot                 |
| Recording                  |
| Timer                      |
| Device Monitor             |
| Ask LilyAi                 |
+----------------------------+
```

The overlay should be configurable.

Possible tools:

-   Performance mode
-   Gaming Focus
-   Network monitor
-   Device monitor
-   Screenshot
-   Screen recording
-   Timer
-   Music control where supported
-   Brightness control where permitted
-   Rotation control where permitted
-   AI assistant
-   Floating tools

------------------------------------------------------------------------

# 7. Edge Handle

Provide an unobtrusive handle on the side of the screen.

``` text
GAME

                         |
                         | <- LilyAi
                         |
```

Gesture:

``` text
Swipe edge
     |
     v
Game Assistant
```

Requirements:

-   Drag position.
-   Enable/disable.
-   Left/right side.
-   Size setting.
-   Opacity setting.
-   Auto-hide.
-   Game-specific configuration.

------------------------------------------------------------------------

# 8. Gaming Focus

Gaming Focus should reduce interruptions without claiming capabilities
Android does not provide.

Possible modes:

### Normal

Notifications operate normally.

### Banner Block

Reduce visual notification interruption where Android permits.

### Full Focus

Use appropriate Android notification/DND capabilities where permissions
and APIs permit.

### Custom

Allow users to choose allowed apps/categories where supported.

State machine:

``` text
Game launched
     |
     v
Save previous state
     |
     v
Enable gaming focus
     |
     v
Game running
     |
     v
Game exits
     |
     v
Restore previous state
```

Never assume unrestricted control over notifications or calls.

------------------------------------------------------------------------

# 9. Call Handling

Game Space can provide call-related gaming behavior where Android allows
it.

Possible modes:

-   Allow calls
-   Block/limit interruption where permitted
-   Banner-style notification where supported

Implementation must respect Android's notification, phone, role, and
permission restrictions.

Do not attempt to bypass Android restrictions.

------------------------------------------------------------------------

# 10. Performance Management

The target games are light, so performance management is low priority.
The battery and temperature readout in the overlay is the part worth
keeping.

Provide a unified abstraction:

``` text
PerformanceManager
|
+-- Android Game Mode
+-- OEM/device-specific capability
+-- Supported system APIs
+-- Safe fallback
```

Modes:

``` text
BATTERY
BALANCED
PERFORMANCE
EXTREME
```

Important:

A normal third-party APK cannot generally:

-   Change CPU governors.
-   Overclock CPU/GPU.
-   Change thermal governors.
-   Control OEM cooling hardware.
-   Force OEM performance engines.

Android Game Mode APIs start at Android 12. The target phone runs
Android 11, so Game Mode is unavailable there and must be reported as
unavailable.

The application must detect device capabilities.

Example:

``` text
DeviceCapabilities

overlay          = AVAILABLE
screenCapture    = AVAILABLE
gameMode         = AVAILABLE
dndControl       = LIMITED
touchAutomation  = LIMITED
cpuControl       = UNAVAILABLE
gpuControl       = UNAVAILABLE
thermalControl   = UNAVAILABLE
```

Never fake unsupported features.

------------------------------------------------------------------------

# 11. Device Monitoring

The overlay can expose supported device metrics:

``` text
FPS
Ping
Battery
Temperature
CPU
RAM
Network
```

Important limitation:

A normal application cannot assume unrestricted access to another
application's rendering pipeline or exact FPS.

Implement monitoring based on Android APIs and device capabilities.

------------------------------------------------------------------------

# 12. Network Monitoring

Provide:

``` text
NetworkMonitor
|
+-- Connection type
+-- Wi-Fi/mobile state
+-- Reachability
+-- Latency/ping
+-- Network statistics
```

Do not claim arbitrary packet prioritization unless a legitimate
supported mechanism is implemented.

Possible future architecture:

``` text
NetworkManager
|
+-- Standard monitoring
+-- Optional VPN-based functionality
+-- OEM-specific optimization
```

------------------------------------------------------------------------

# 13. Screenshot

Provide a quick screenshot action from the Game Assistant.

``` text
ScreenshotTool
    |
    v
Android screenshot mechanism
    |
    v
Saved media
```

Use the appropriate Android API/permission flow for the target Android
version.

On Android 11 the first choice is
`AccessibilityService.takeScreenshot()` (API 30+). It needs the service
to declare `canTakeScreenshot`, has a short minimum interval between
calls, and returns a blank image for secure windows. Saved screenshots
go through MediaStore because Android 11 enforces scoped storage.

------------------------------------------------------------------------

# 14. Screen Recording

Use Android's MediaProjection framework where supported.

Flow:

``` text
User taps Record
       |
       v
Request MediaProjection permission
       |
       v
Start recording
       |
       v
Game continues
       |
       v
Stop recording
       |
       v
Save media
```

Do not silently capture the screen.

------------------------------------------------------------------------

# 15. Brightness and Rotation

Possible game profile settings:

``` text
Brightness
- Lock brightness
- Saved brightness level

Rotation
- Landscape
- Portrait
- Auto
- Game default
```

When possible:

``` text
Game starts
    |
    v
Save previous state
    |
    v
Apply game profile
    |
    v
Game exits
    |
    v
Restore previous state
```

Respect Android restrictions.

------------------------------------------------------------------------

# 16. Gaming Timer

Provide:

``` text
15 minutes
30 minutes
60 minutes
90 minutes
Custom
```

The timer should normally notify the user rather than forcibly
terminating another application.

Example:

``` text
Gaming Timer
----------------
Remaining: 10:00

[Continue]
[Dismiss]
```

------------------------------------------------------------------------

# 17. Gaming Statistics

Track:

-   Total playtime.
-   Session count.
-   Last played.
-   Daily playtime.
-   Weekly playtime.
-   Game rankings.
-   Screenshots/recordings if desired.
-   AI interactions.
-   Optional achievements/milestones.

Example:

``` text
Today
Gaming time: 2h 41m
Sessions: 4

This week
Gaming time: 17h 32m

Most played
1. Mobile Legends
2. Minecraft
3. Call of Duty
```

------------------------------------------------------------------------

# 18. LilyAi AI Layer

AI should be provider-independent.

Qwen3-4B is the main model. Groq is the backup.

Architecture:

``` text
LilyAiEngine
      |
      v
ModelRouter
      |
+-----+------------------+------------------+
|                        |                  |
v                        v                  v
QwenProvider         GroqProvider       OtherProvider
(main)               (backup)                |
                                             v
                                       Vision/Reasoning
```

Define a common provider interface:

``` kotlin
interface AiProvider {
    suspend fun generate(
        request: AiRequest
    ): AiResponse
}
```

Providers:

-   Qwen3-4B (main)
-   Groq (backup)
-   Other cloud model providers
-   Vision model provider (Qwen3-4B is text-only)

------------------------------------------------------------------------

# 19. Model Router

Do not send every request to the same model.

Default order for language requests:

``` text
ModelRouter
    |
    v
QwenProvider (main)
    |
    +-- success --> response
    |
    +-- failure --> GroqProvider (backup)
```

Example routing:

``` text
"Open Mobile Legends."
        |
        v
Local intent/tool system
        |
        v
launchGame()
```

No AI model is necessary.

For:

``` text
"Explain what this screen means."
```

Use:

``` text
Screen capture
     |
     v
Vision model
     |
     v
Language model (Qwen3-4B, Groq as backup)
```

For:

``` text
"Analyze my game situation."
```

Use:

``` text
Vision + game context + reasoning model
```

For:

``` text
"Turn on gaming focus."
```

Use:

``` text
Tool intent
     |
     v
Kotlin ToolRegistry
```

------------------------------------------------------------------------

# 20. AI Providers: Qwen3-4B (Main) and Groq (Backup)

Qwen3-4B is the main AI model. Groq is the backup, used when the main
model cannot answer.

Each one is implemented as a provider behind `AiProvider`.

## 20.1 Qwen3-4B Provider (main)

``` text
QwenProvider
|
+-- Model loading / connection
+-- Request builder
+-- Streaming response
+-- Tool calling
+-- Error handling
+-- Timeout handling
+-- Usage metadata
```

Notes:

-   Qwen3-4B is a text model. Screen understanding needs a separate
    vision provider.
-   Qwen3-4B runs on the device. See 20.4.

## 20.2 Groq Provider (backup)

``` text
GroqProvider
|
+-- Authentication
+-- Request builder
+-- Model selection
+-- Streaming response
+-- Error handling
+-- Retry/backoff
+-- Usage metadata
```

Never hard-code secrets in the APK.

Do not ship a permanent privileged API key inside the application.

Use a secure backend/token strategy appropriate to the deployment model.

(Current state: the Groq key is stored in the app. See section 30.)

## 20.3 Failover Policy

``` text
Request
   |
   v
ModelRouter
   |
   v
QwenProvider (main)
   |
   +-- success ------------------> Response
   |
   +-- error / timeout / unavailable
           |
           v
     GroqProvider (backup)
           |
           v
        Response
```

Rules:

-   Try Qwen3-4B first for every language request that needs a model.
-   Fall back to Groq when Qwen3-4B errors, times out, or is not
    available.
-   Try Qwen3-4B again on the next request.
-   Record which provider answered (`provider` in `ai_messages`).
-   If both fail, tell the user the AI is unavailable. Game Space keeps
    working.

## 20.4 Decision

Qwen3-4B runs on the device. The target phone has 8 GB of RAM (plus 3
GB of virtual RAM, which is swap and does not count for the model), so
a 4-bit quantized 4B model fits next to a light game.

To measure on the target phone:

-   Generation speed (depends on the chip, not the RAM).
-   Heat and battery use while a game is open.
-   Whether Android keeps the model process alive in the background.

Groq stays the backup for when the local model is unavailable or too
slow.

------------------------------------------------------------------------

# 21. AI Tool Calling

The AI must never receive unrestricted Android access.

Create a controlled ToolRegistry:

``` text
ToolRegistry
|
+-- LaunchGameTool
+-- LaunchAppTool
+-- ScreenshotTool
+-- RecordingTool
+-- TimerTool
+-- DeviceInfoTool
+-- NetworkTool
+-- GamingFocusTool
+-- PerformanceTool
+-- BrightnessTool
+-- RotationTool
+-- OverlayTool
+-- AccessibilityTool
```

Flow:

``` text
User
 |
 v
LilyAi
 |
 v
AI Model
 |
 v
Tool Call
 |
 v
ToolRegistry
 |
 v
Validate arguments
 |
 v
Kotlin implementation
 |
 v
Android API
 |
 v
Result
 |
 v
LilyAi
```

The AI should only be able to execute registered and permission-checked
tools.

------------------------------------------------------------------------

# 22. Example AI Interaction

User:

> "Lily, turn on gaming mode and start Mobile Legends."

Processing:

``` text
User request
     |
     v
Intent / model router
     |
     +---- enableGamingFocus()
     |
     +---- launchGame("Mobile Legends")
     |
     v
Kotlin ToolRegistry
     |
     v
Android
```

The AI should receive the result:

``` text
Gaming Focus: enabled
Game: launched
```

Then answer the user.

------------------------------------------------------------------------

# 23. Screen Understanding

Potential flow:

``` text
Game
 |
 v
MediaProjection
 |
 v
Screenshot/frame
 |
 v
Vision model
 |
 v
Game context
 |
 v
Reasoning model
 |
 v
LilyAi response
```

Example:

> User: "Lily, what should I do?"

LilyAi can analyze the captured screen when the user explicitly requests
it and required permissions are available.

Possible future features:

-   Explain UI elements.
-   Read objectives.
-   Analyze game state.
-   Identify visible enemies/items.
-   Explain menus.
-   Translate text.
-   Provide strategy suggestions.

AI screen analysis must respect user consent and Android capture
restrictions.

## 23.1 Capture and recognition order

Capture:

1.  `AccessibilityService.takeScreenshot()` (API 30+) for on-demand
    captures. No per-session capture prompt. The same service also does
    taps and swipes for autoplay.
2.  MediaProjection for recording and continuous capture.

Games draw to a canvas, so the accessibility node tree gives almost
nothing for them. Screens must be recognized from the image.

Recognition, cheapest first:

1.  On-device OCR (for example ML Kit text recognition) and template
    matching for known screens and buttons.
2.  A vision model only for open questions. Qwen3-4B is text-only, so
    this is a separate hosted or on-device vision model. Downscale the
    screenshot before sending it.

Capture only when the user asks or when an autoplay task is running.

------------------------------------------------------------------------

# 24. Voice Assistant

Potential flow:

``` text
Voice input
   |
   v
Speech recognition
   |
   v
LilyAi Engine
   |
   +--> Local command
   |
   +--> AI model
   |
   +--> Tool call
   |
   v
Voice response
```

Example:

> "Hey Lily, set a 30 minute gaming timer."

Result:

``` text
GamingTimer.start(30 minutes)
```

------------------------------------------------------------------------

# 25. AI Memory

LilyAi can remember useful non-sensitive preferences and context.

Example:

``` text
User:
"I prefer performance mode for Mobile Legends."

Memory:
category = gaming_preference
content = performance mode for Mobile Legends
```

Later:

> "Start my usual Mobile Legends setup."

LilyAi can apply the saved profile.

Memory must be:

-   User-controllable.
-   Deletable.
-   Syncable.
-   Clearly separated from temporary context.
-   Protected by appropriate security controls.

------------------------------------------------------------------------

# 26. Local Storage

Use Room/SQLite as the primary local store.

Suggested local entities:

``` text
UserEntity
DeviceEntity
GameEntity
GameProfileEntity
GameSessionEntity
ConversationEntity
MessageEntity
MemoryEntity
SettingsEntity
```

Why local-first:

-   Works offline.
-   Fast.
-   Game Space doesn't depend on internet.
-   Reduces cloud traffic.
-   Provides temporary state.

------------------------------------------------------------------------

# 27. Turso Cloud Storage

Turso is the cloud synchronization/database layer.

Suggested tables:

``` text
users
devices
games
game_profiles
game_sessions
ai_conversations
ai_messages
memories
settings
sync_state
```

Example:

``` text
users
-----------------
id
created_at
updated_at
```

``` text
games
-----------------
id
user_id
package_name
name
last_played
total_playtime
created_at
updated_at
```

``` text
game_profiles
-----------------
id
game_id
performance_mode
notification_mode
call_mode
brightness_lock
brightness_level
rotation_mode
overlay_enabled
monitor_enabled
```

``` text
game_sessions
-----------------
id
game_id
started_at
ended_at
duration
```

``` text
ai_messages
-----------------
id
conversation_id
role
content
model
provider
created_at
```

``` text
memories
-----------------
id
user_id
category
content
importance
created_at
updated_at
```

------------------------------------------------------------------------

# 28. Turso Usage Rule

Do not use Turso as a real-time telemetry database.

Bad:

``` text
FPS -> Turso -> FPS -> Turso -> FPS -> Turso
```

Good:

``` text
Game
 |
 v
Local session state
 |
 v
Session ends
 |
 v
Sync meaningful statistics
 |
 v
Turso
```

Turso stores durable cloud state.

Room/SQLite stores fast local state.

------------------------------------------------------------------------

# 29. Offline-First Architecture

``` text
                 LilyAi Android
                       |
              +--------+--------+
              |                 |
              v                 v
          Local AI          Cloud AI
          / Tools            / Groq (backup)
              |                 |
              v                 v
          Room/SQLite         Turso
              |                 |
              +--------+--------+
                       |
                  SyncManager
```

Offline capabilities should include:

-   Game library.
-   Game launching.
-   Game profiles.
-   Overlay.
-   Screenshot.
-   Timer.
-   Supported device monitoring.
-   Local settings.
-   Previously cached data.
-   Basic commands that do not require cloud AI.

When internet returns:

``` text
Local changes
     |
     v
SyncManager
     |
     v
Turso
```

------------------------------------------------------------------------

# 30. Security

## API keys

Never embed long-lived Groq/Turso secrets directly in the APK.

Bad:

``` text
const val GROQ_API_KEY = "secret"
```

Better:

``` text
Android
   |
   v
Authenticated service/backend
   |
   +--> Groq
   +--> Turso
```

The exact production credential architecture should be chosen based on
whether LilyAi is a personal/private app, distributed APK, or public
application.

Current state: the owner stores the keys in the app itself. How they
are stored and whether to change this is still to be decided. The text
above describes the safer alternatives. Qwen3-4B runs on the device and
needs no key.

## Android permissions

Use least privilege.

Request sensitive permissions only when the feature needs them.

Important categories include:

-   Overlay permission.
-   Notification policy access where needed.
-   Accessibility service.
-   MediaProjection.
-   Notification access if a specific feature requires it.
-   Foreground service permissions/types where required.
-   Storage/media permissions where required by target Android version.

Never hide permission requirements.

------------------------------------------------------------------------

# 31. Device Capability System

Because Android and OEMs differ, implement capability detection.

Example:

``` kotlin
data class DeviceCapabilities(
    val overlay: Boolean,
    val screenCapture: Boolean,
    val gameMode: Boolean,
    val notificationControl: Capability,
    val touchAutomation: Capability,
    val cpuControl: Capability,
    val gpuControl: Capability,
    val thermalControl: Capability
)
```

Possible states:

``` text
AVAILABLE
LIMITED
UNAVAILABLE
UNKNOWN
```

UI should adapt to the capability.

Example:

``` text
Performance Mode
----------------------------
Performance        Available
Extreme            Not supported
CPU Governor       Not available
```

Never pretend unavailable OEM features are working.

------------------------------------------------------------------------

# 32. Accessibility Architecture

The accessibility service is a core capability. It provides screenshots
(`takeScreenshot`, API 30+) and the taps and swipes that autoplay needs.
Basic Game Space functions (library, launch, timer) must still work
without it.

Uses:

-   Screenshot capture.
-   Autoplay gestures (tap, swipe) in supported games.
-   Reading visible UI where permitted.
-   Navigation assistance.

Architecture:

``` text
LilyAccessibilityService
        |
        v
AccessibilityController
        |
        v
ToolRegistry
```

Every action should be explicitly validated.

Autoplay must be started by the user, stoppable at any time, and
limited to the target game. Do not build unrestricted autonomous
control that could unexpectedly manipulate the device.

Declare only the accessibility capabilities a feature needs. The
gesture capability is added when autoplay is built.

Note: some game publishers prohibit automation and can ban accounts for
it. Online chess against real people with engine help is cheating. Pick
target games and modes with that in mind.

------------------------------------------------------------------------

# 33. Optional Python Backend

Python can be added later.

It should not be required for:

-   Game Space.
-   Overlay.
-   Android controls.
-   Game launching.
-   Local storage.
-   Basic AI provider calls.

Potential Python responsibilities:

``` text
Python Backend
|
+-- Advanced AI orchestration
+-- Custom vision pipeline
+-- Long-running AI jobs
+-- Model preprocessing
+-- Advanced memory processing
+-- AI evaluation
```

Android communicates with it over an authenticated API when needed.

------------------------------------------------------------------------

# 34. Game Space + AI Differentiation

The goal is not simply:

> "Game Space clone + chatbot."

The goal is:

> **LilyAi is an AI-native Android assistant that understands the gaming
> environment and can operate supported Android features through
> controlled tools.**

Traditional Game Space:

``` text
Game
 |
 +-- Performance
 +-- Notifications
 +-- Recording
 +-- Monitoring
```

LilyAi:

``` text
Game
 |
 +-- Performance
 +-- Notifications
 +-- Recording
 +-- Monitoring
 |
 +-- AI Assistant
 +-- Screen Understanding
 +-- Voice Commands
 +-- Game Context
 +-- AI Memory
 +-- Tool Execution
 +-- Personalized Profiles
```

------------------------------------------------------------------------

# 35. Development Phases

## Phase 1 --- Native Foundation

Build:

-   Kotlin/Android project.
-   Gradle setup (minSdk 30).
-   GitHub Actions debug APK build.
-   Accessibility service with a screenshot proof.
-   Navigation.
-   Permission manager.
-   Capability manager.
-   Room database.
-   Basic settings.

## Phase 2 --- Game Space

Build:

-   Game discovery.
-   Game library.
-   Game launcher.
-   Game profiles.
-   Game sessions.
-   Playtime statistics.

## Phase 3 --- Overlay

Build:

-   Overlay service.
-   Edge handle.
-   Assistant panel.
-   Screenshot.
-   Recording.
-   Timer.
-   Device monitor.
-   Network monitor.

## Phase 4 --- Gaming Focus

Build:

-   Gaming Focus.
-   Notification integrations.
-   Call-related behavior where supported.
-   Brightness profile.
-   Rotation profile.
-   Capability-aware performance controls.

(Game Mode integration is dropped: it needs Android 12+.)

## Phase 5 --- AI Core

Build:

-   AiProvider interface.
-   Qwen3-4B provider (main, on-device).
-   Groq provider (backup).
-   Model router with failover from Qwen3-4B to Groq.
-   Conversation manager.
-   Tool registry.
-   Tool calling.
-   Streaming responses.

## Phase 6 --- LilyAi Overlay

Add:

-   Ask LilyAi.
-   Voice input.
-   AI response UI.
-   Context-aware game assistant.
-   Tool execution from overlay.

## Phase 7 --- Vision

Add:

-   MediaProjection pipeline.
-   Screenshot-to-AI.
-   Vision model.
-   Screen analysis.
-   User-requested game assistance.

## Phase 7b --- Autoplay

Add:

-   Gesture capability in the accessibility service (tap, swipe).
-   Screen recognition (OCR, template matching).
-   Scripted flows per game (chess, Miscrits World Adventure,
    Umamusume).
-   Start/stop control from the overlay.
-   AI decisions for choices a script cannot make.

## Phase 8 --- Turso

Add:

-   Cloud authentication strategy.
-   Turso schema.
-   Sync manager.
-   Game profile sync.
-   Conversation sync.
-   Memory sync.
-   Statistics sync.

## Phase 9 --- AI Memory

Add:

-   Memory extraction.
-   User preferences.
-   Game-specific memories.
-   Memory controls.
-   Delete/reset memory.

## Phase 10 --- Polish and Release

Add:

-   Error handling.
-   Offline mode.
-   Capability fallbacks.
-   Security review.
-   Permission UX.
-   Performance testing.
-   Battery testing.
-   GitHub Actions release pipeline.

------------------------------------------------------------------------

# 36. GitHub Actions

Recommended workflows:

``` text
.github/workflows/
|
+-- android.yml
+-- tests.yml
+-- release.yml
```

### android.yml

``` text
Checkout
   |
Setup JDK
   |
Gradle build
   |
Unit tests
   |
Lint
   |
Build APK
```

### tests.yml

Run:

-   Kotlin unit tests.
-   Repository tests.
-   AI routing tests.
-   Tool validation tests.
-   Database tests.

### release.yml

``` text
Tag/release
    |
    v
Build signed release
    |
    v
Run verification
    |
    v
Publish artifact
```

Secrets must be stored in GitHub Actions secrets, never committed.

------------------------------------------------------------------------

# 37. Testing Strategy

## Unit Tests

Test:

-   GameDetector.
-   GameRepository.
-   GameProfile.
-   ModelRouter (including Qwen3-4B to Groq failover).
-   ToolRegistry.
-   SyncManager.
-   Memory manager.
-   AI provider adapters.

## Instrumentation Tests

Test:

-   Game library UI.
-   Overlay behavior.
-   Permission flows.
-   Game launching.
-   Local database.
-   Screen capture permission flow.

## Device Testing

Test across:

-   Android versions.
-   Different screen sizes.
-   Different OEMs.
-   Low-memory devices.
-   Devices with/without Game Mode support.
-   Devices with/without overlay restrictions.

------------------------------------------------------------------------

# 38. Reliability Requirements

LilyAi must never make the gaming experience worse.

Priority order:

``` text
1. Android stability
2. Game stability
3. User control
4. Battery efficiency
5. AI functionality
```

If AI fails:

``` text
Qwen3-4B unavailable
    |
    v
Groq backup
    |
    v
Both unavailable
    |
    v
Game Space still works
```

If Turso fails:

``` text
Cloud unavailable
    |
    v
Local state continues
```

If an unsupported device feature is requested:

``` text
Unsupported
    |
    v
Explain limitation
    |
    v
Offer available alternative
```

------------------------------------------------------------------------

# 39. Performance Requirements

Avoid constantly running expensive processes.

Do not continuously:

-   Capture the screen without user need.
-   Upload screenshots.
-   Query cloud storage.
-   Poll device state excessively.
-   Run AI models unnecessarily.

Use event-driven architecture wherever possible.

Example:

``` text
Game launch event
     |
     +--> Start session
     +--> Apply profile
     +--> Start overlay
     +--> Start required monitors

Game exit event
     |
     +--> Stop session
     +--> Restore state
     +--> Save statistics
     +--> Sync later
```

------------------------------------------------------------------------

# 40. Final System Model

The final architecture should be understood as:

``` text
                         +----------------+
                         |    LILYAI      |
                         +-------+--------+
                                 |
             +-------------------+-------------------+
             |                   |                   |
             v                   v                   v
       GAME SPACE          AI ASSISTANT        PHONE CONTROL
             |                   |                   |
             |                   v                   |
             |              MODEL ROUTER             |
             |                   |                   |
             |          +--------+--------+          |
             |          |                 |          |
             |      Qwen3-4B           Groq          |
             |       (main)          (backup)        |
             |                                           
             +-------------------+-------------------+
                                 |
                         TOOL REGISTRY
                                 |
                         Android APIs
                                 |
                +----------------+----------------+
                |                |                |
             Overlay        Accessibility     MediaProjection
                |                |                |
                +----------------+----------------+
                                 |
                              Android

                 LOCAL DATA <----> TURSO
                 Room/SQLite        Cloud
```

------------------------------------------------------------------------

# 41. Core Design Rules

1.  **Native Android first.**
2.  **Kotlin controls Android.**
3.  **Python is optional, not a core Android dependency.**
4.  **Game Space must work without cloud AI.**
5.  **AI must use a provider abstraction.**
6.  **Qwen3-4B is the main model and Groq is the backup. Neither is the
    entire architecture.**
7.  **Use a ModelRouter for task-specific model selection.**
8.  **AI interacts with Android through ToolRegistry.**
9.  **Never give an AI model unrestricted Android control.**
10. **Room/SQLite is local-first.**
11. **Turso is cloud synchronization/persistent cloud state.**
12. **Do not store high-frequency telemetry in Turso.**
13. **Detect device capabilities instead of assuming OEM privileges.**
14. **Respect Android permission and security boundaries.**
15. **Game Space should remain functional if AI/cloud services fail.**
16. **AI features should degrade gracefully when offline.**
17. **Do not put long-lived API secrets inside the APK (current state:
    keys are stored in the app, see section 30).**
18. **Use GitHub Actions for repeatable builds and releases.**
19. **Test across Android versions and OEM devices.**
20. **Treat the overlay and AI tool system as first-class LilyAi
    components.**

------------------------------------------------------------------------

# 42. Initial MVP Definition

The first usable LilyAi release should contain:

### Game Space

-   [ ] Game detection
-   [ ] Game library
-   [ ] Launch game
-   [ ] Add/remove game
-   [ ] Game profile
-   [ ] Session tracking
-   [ ] Playtime statistics

### Overlay

-   [ ] Overlay permission flow
-   [ ] Edge handle
-   [ ] Game Assistant panel
-   [ ] Screenshot
-   [ ] Timer
-   [ ] Battery
-   [ ] Network status

### AI

-   [ ] LilyAi chat
-   [ ] Qwen3-4B provider (main)
-   [ ] Groq provider (backup)
-   [ ] Failover from Qwen3-4B to Groq
-   [ ] AI provider interface
-   [ ] Model router
-   [ ] Tool registry
-   [ ] Basic tool calling

### Autoplay

-   [ ] Accessibility service (screenshot and gestures)
-   [ ] Screen recognition (OCR, template matching)
-   [ ] One scripted flow for one game
-   [ ] Start/stop control

### Storage

-   [ ] Room
-   [ ] Local game data
-   [ ] Local settings
-   [ ] Turso schema
-   [ ] Basic synchronization

### Build

-   [ ] GitHub Actions
-   [ ] Debug APK build
-   [ ] Unit tests
-   [ ] Lint
-   [ ] Release workflow

------------------------------------------------------------------------

# 43. Future Features

After the MVP:

-   [ ] Voice activation
-   [ ] Voice responses
-   [ ] Screen analysis
-   [ ] AI game coaching
-   [ ] Game strategy assistant
-   [ ] AI-powered smart recording
-   [ ] Game highlight detection
-   [ ] Advanced memory
-   [ ] Cross-device synchronization
-   [ ] More AI providers
-   [ ] Optional local AI
-   [ ] Advanced accessibility automation
-   [ ] Supported OEM performance integrations
-   [ ] Game-specific AI agents
-   [ ] Personalized gaming profiles
-   [ ] AI-generated gaming summaries

------------------------------------------------------------------------

# 44. Project Definition

**LilyAi is not merely a Game Space clone.**

It is:

> **A native Android AI assistant that combines Game Space
> functionality, a persistent AI overlay, controlled Android automation
> (including game autoplay), multi-model AI inference with Qwen3-4B as
> the main model and Groq as the backup, and Turso-backed cloud
> memory/state.**

The fundamental relationship is:

``` text
Kotlin
  = Android / Device / UI / Game Space

AI
  = Intelligence / Reasoning / Vision / Conversation

Qwen3-4B
  = Main AI model

Groq
  = Backup AI inference provider

Turso
  = Cloud persistence / synchronization

Room / SQLite
  = Local-first state

GitHub Actions
  = Build / Test / Release

Python
  = Optional advanced backend, not required by the Android core
```

This architecture should be the baseline for implementation decisions
going forward.
