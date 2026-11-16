# Reflection coverage report

Generated 2026-11-16 — releases checked: 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3.

Obfuscated (intermediary/SRG): 1.20 → 1.21.11. SRG checked for: 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4.

Members that do not cover every version are expected: the Java code always has an alternative path (see `GameProbe`).

| key | present in |
|---|---|
| `Minecraft` | **all** |
| `Minecraft.getInstance` | **all** |
| `Minecraft.screen` | 1.20 → 26.1.2 |
| `Minecraft.gui` | **all** |
| `Minecraft.level` | **all** |
| `Minecraft.player` | **all** |
| `Minecraft.options` | **all** |
| `Minecraft.gameDirectory` | **all** |
| `Minecraft.gameMode` | **all** |
| `Minecraft.overlay` | 1.20 → 26.1.2 |
| `Minecraft.getFps` | **all** |
| `Minecraft.getCurrentServer` | **all** |
| `Minecraft.getSingleplayerServer` | **all** |
| `Minecraft.getConnection` | **all** |
| `Minecraft.getUser` | **all** |
| `Minecraft.getWindow` | **all** |
| `Minecraft.isPaused` | **all** |
| `Minecraft.isWindowActive` | **all** |
| `Minecraft.getLaunchedVersion` | **all** |
| `Gui` | **all** |
| `Gui.screen` | 26.2 → 26.3 |
| `Gui.overlay` | 26.2 → 26.3 |
| `Gui.getChat` | 1.20 → 26.1.2 |
| `Window` | **all** |
| `Window.getWindow` | 1.20 → 1.21.8 |
| `Window.handle` | 1.21.9 → 26.3 |
| `Window.getWidth` | **all** |
| `Window.getHeight` | **all** |
| `Window.isFullscreen` | 1.20 → 26.2 |
| `Component` | **all** |
| `Component.literal` | **all** |
| `Component.getString` | **all** |
| `ChatComponent` | **all** |
| `ChatComponent.addMessage` | 1.20 → 1.21.11 |
| `ChatComponent.addClientSystemMessage` | 26.1 → 26.3 |
| `ServerData` | **all** |
| `ServerData.name` | **all** |
| `ServerData.ip` | **all** |
| `ServerData.version` | **all** |
| `ServerData.ping` | **all** |
| `ServerData.isLan` | **all** |
| `ServerData.isRealm` | 1.20.2 → 26.3 |
| `MinecraftServer` | **all** |
| `MinecraftServer.getWorldData` | **all** |
| `MinecraftServer.isPublished` | **all** |
| `WorldData` | **all** |
| `WorldData.getLevelName` | **all** |
| `ClientPacketListener` | **all** |
| `ClientPacketListener.getPlayerInfo` | **all** |
| `ClientPacketListener.getOnlinePlayers` | **all** |
| `ClientCommonPacketListenerImpl` | 1.20.2 → 26.3 |
| `ClientCommonPacketListenerImpl.serverBrand` | 1.20.2 → 26.3 |
| `PlayerInfo` | **all** |
| `PlayerInfo.getLatency` | **all** |
| `MultiPlayerGameMode` | **all** |
| `MultiPlayerGameMode.getPlayerMode` | **all** |
| `User` | **all** |
| `User.getName` | **all** |
| `User.getProfileId` | **all** |
| `User.getAccessToken` | **all** |
| `Options` | **all** |
| `Options.languageCode` | **all** |
| `Options.resourcePacks` | **all** |
| `Options.renderDistance` | **all** |
| `Options.fov` | **all** |
| `Options.guiScale` | **all** |
| `OptionInstance` | **all** |
| `OptionInstance.get` | **all** |
| `Entity` | **all** |
| `Entity.getX` | **all** |
| `Entity.getY` | **all** |
| `Entity.getZ` | **all** |
| `Entity.isSprinting` | **all** |
| `Entity.isSwimming` | **all** |
| `Entity.isShiftKeyDown` | **all** |
| `Entity.isPassenger` | **all** |
| `Entity.getVehicle` | **all** |
| `Entity.isInWater` | **all** |
| `Entity.isInLava` | **all** |
| `Entity.isUnderWater` | **all** |
| `Entity.isOnFire` | **all** |
| `Entity.onGround` | **all** |
| `Entity.level` | **all** |
| `Entity.getUUID` | **all** |
| `Entity.blockPosition` | **all** |
| `Entity.getType` | **all** |
| `LivingEntity` | **all** |
| `LivingEntity.getHealth` | **all** |
| `LivingEntity.getMaxHealth` | **all** |
| `LivingEntity.isFallFlying` | **all** |
| `LivingEntity.isSleeping` | **all** |
| `LivingEntity.getArmorValue` | **all** |
| `Player` | **all** |
| `Player.experienceLevel` | **all** |
| `Player.getAbilities` | **all** |
| `Player.getFoodData` | **all** |
| `Player.getInventory` | **all** |
| `Player.isCreative` | **all** |
| `Player.isSpectator` | **all** |
| `Player.displayClientMessage` | 1.20 → 1.21.11 |
| `Player.sendSystemMessage` | 26.1 → 26.3 |
| `LocalPlayer` | **all** |
| `Abilities` | **all** |
| `Abilities.flying` | **all** |
| `FoodData` | **all** |
| `FoodData.getFoodLevel` | **all** |
| `Inventory` | **all** |
| `Inventory.selected` | **all** |
| `Inventory.getSelectedSlot` | 1.21.5 → 26.3 |
| `EntityType` | **all** |
| `EntityType.toShortString` | **all** |
| `GameType` | **all** |
| `GameType.getName` | **all** |
| `Level` | **all** |
| `Level.dimension` | **all** |
| `Level.getDayTime` | 1.20 → 1.21.11 |
| `Level.getDefaultClockTime` | 26.1 → 26.3 |
| `Level.isRaining` | **all** |
| `Level.isThundering` | **all** |
| `LevelReader` | **all** |
| `LevelReader.getBiome` | **all** |
| `Holder` | **all** |
| `Holder.unwrapKey` | **all** |
| `Holder.getRegisteredName` | 1.20.5 → 26.3 |
| `Screen` | **all** |
| `Screen.getTitle` | **all** |
| `ChatScreen` | **all** |
| `ChatScreen.input` | **all** |
| `EditBox` | **all** |
| `EditBox.getValue` | **all** |
| `DisconnectedScreen` | **all** |
| `DisconnectedScreen.reason` | 1.20 → 1.20.6 |
| `DisconnectedScreen.details` | 1.21 → 26.3 |
| `DisconnectionDetails` | 1.21 → 26.3 |
| `DisconnectionDetails.reason` | 1.21 → 26.3 |
| `LoadingOverlay` | **all** |
| `TitleScreen` | **all** |
| `JoinMultiplayerScreen` | **all** |
| `DirectJoinServerScreen` | **all** |
| `EditServerScreen` | **all** |
| `SelectWorldScreen` | **all** |
| `CreateWorldScreen` | **all** |
| `EditWorldScreen` | **all** |
| `RealmsMainScreen` | **all** |
| `RealmsScreen` | **all** |
| `PauseScreen` | **all** |
| `InBedChatScreen` | **all** |
| `AbstractContainerScreen` | **all** |
| `InventoryScreen` | **all** |
| `CreativeModeInventoryScreen` | **all** |
| `DeathScreen` | **all** |
| `ConnectScreen` | **all** |
| `ProgressScreen` | **all** |
| `LevelLoadingScreen` | **all** |
| `ReceivingLevelScreen` | 1.20 → 1.21.8 |
| `GenericMessageScreen` | **all** |
| `GenericWaitingScreen` | **all** |
| `AdvancementsScreen` | **all** |
| `StatsScreen` | **all** |
| `BookViewScreen` | **all** |
| `BookEditScreen` | **all** |
| `AbstractSignEditScreen` | **all** |
| `OptionsScreen` | **all** |
| `OptionsSubScreen` | **all** |
| `PackSelectionScreen` | **all** |
| `SocialInteractionsScreen` | **all** |
| `ShareToLanScreen` | 1.20 → 26.1.2 |
| `WinScreen` | **all** |
| `AccessibilityOnboardingScreen` | **all** |
