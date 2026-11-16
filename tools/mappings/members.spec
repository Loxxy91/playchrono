# PlayChrono reflection spec — every Minecraft class/field/method the mod touches, in Mojang names.
# generate.py resolves each entry for every release (1.20 .. latest) into the runtime names used by
#   * Fabric/Quilt  1.20 .. 1.21.11  -> intermediary
#   * Forge 1.20 .. 1.20.4, NeoForge 20.2 .. 20.4 -> SRG (members) + Mojang (classes)
#   * Forge 1.20.6+, NeoForge 20.5+, every loader on 26.x -> Mojang
# and writes mod/src/main/resources/playchrono/names.tsv.
#
# Syntax:
#   class  <Alias> <mojang.Name> [<older.or.newer.mojang.Name> ...]
#   field  <Alias>.<name>
#   method <Alias>.<name>(<param types, mojang names or primitives, comma separated>)
# Members that only exist in some versions are fine: the Java side always has a fallback path.

# ---- core client -------------------------------------------------------------------------------
class  Minecraft                net.minecraft.client.Minecraft
method Minecraft.getInstance()
field  Minecraft.screen
field  Minecraft.gui
field  Minecraft.level
field  Minecraft.player
field  Minecraft.options
field  Minecraft.gameDirectory
field  Minecraft.gameMode
field  Minecraft.overlay
method Minecraft.getFps()
method Minecraft.getCurrentServer()
method Minecraft.getSingleplayerServer()
method Minecraft.getConnection()
method Minecraft.getUser()
method Minecraft.getWindow()
method Minecraft.isPaused()
method Minecraft.isWindowActive()
method Minecraft.getLaunchedVersion()

class  Gui                      net.minecraft.client.gui.Gui
method Gui.screen()
method Gui.overlay()
method Gui.getChat()

class  Window                   com.mojang.blaze3d.platform.Window
method Window.getWindow()
method Window.handle()
method Window.getWidth()
method Window.getHeight()
method Window.isFullscreen()

class  Component                net.minecraft.network.chat.Component
method Component.literal(java.lang.String)
method Component.getString()

class  ChatComponent            net.minecraft.client.gui.components.ChatComponent
method ChatComponent.addMessage(net.minecraft.network.chat.Component)
method ChatComponent.addClientSystemMessage(net.minecraft.network.chat.Component)

# ---- multiplayer / singleplayer context -------------------------------------------------------
class  ServerData               net.minecraft.client.multiplayer.ServerData
field  ServerData.name
field  ServerData.ip
field  ServerData.version
field  ServerData.ping
method ServerData.isLan()
method ServerData.isRealm()

class  MinecraftServer          net.minecraft.server.MinecraftServer
method MinecraftServer.getWorldData()
method MinecraftServer.isPublished()
class  WorldData                net.minecraft.world.level.storage.WorldData
method WorldData.getLevelName()

class  ClientPacketListener     net.minecraft.client.multiplayer.ClientPacketListener
method ClientPacketListener.getPlayerInfo(java.util.UUID)
method ClientPacketListener.getOnlinePlayers()
class  ClientCommonPacketListenerImpl net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl
method ClientCommonPacketListenerImpl.serverBrand()
class  PlayerInfo               net.minecraft.client.multiplayer.PlayerInfo
method PlayerInfo.getLatency()
class  MultiPlayerGameMode      net.minecraft.client.multiplayer.MultiPlayerGameMode
method MultiPlayerGameMode.getPlayerMode()

class  User                     net.minecraft.client.User
method User.getName()
method User.getProfileId()
method User.getAccessToken()

class  Options                  net.minecraft.client.Options
field  Options.languageCode
field  Options.resourcePacks
method Options.renderDistance()
method Options.fov()
method Options.guiScale()
class  OptionInstance           net.minecraft.client.OptionInstance
method OptionInstance.get()

# ---- player & world ----------------------------------------------------------------------------
class  Entity                   net.minecraft.world.entity.Entity
method Entity.getX()
method Entity.getY()
method Entity.getZ()
method Entity.isSprinting()
method Entity.isSwimming()
method Entity.isShiftKeyDown()
method Entity.isPassenger()
method Entity.getVehicle()
method Entity.isInWater()
method Entity.isInLava()
method Entity.isUnderWater()
method Entity.isOnFire()
method Entity.onGround()
method Entity.level()
method Entity.getUUID()
method Entity.blockPosition()
method Entity.getType()
class  LivingEntity             net.minecraft.world.entity.LivingEntity
method LivingEntity.getHealth()
method LivingEntity.getMaxHealth()
method LivingEntity.isFallFlying()
method LivingEntity.isSleeping()
method LivingEntity.getArmorValue()
class  Player                   net.minecraft.world.entity.player.Player
field  Player.experienceLevel
method Player.getAbilities()
method Player.getFoodData()
method Player.getInventory()
method Player.isCreative()
method Player.isSpectator()
method Player.displayClientMessage(net.minecraft.network.chat.Component,boolean)
method Player.sendSystemMessage(net.minecraft.network.chat.Component)
class  LocalPlayer              net.minecraft.client.player.LocalPlayer
class  Abilities                net.minecraft.world.entity.player.Abilities
field  Abilities.flying
class  FoodData                 net.minecraft.world.food.FoodData
method FoodData.getFoodLevel()
class  Inventory                net.minecraft.world.entity.player.Inventory
field  Inventory.selected
method Inventory.getSelectedSlot()
class  EntityType               net.minecraft.world.entity.EntityType
method EntityType.toShortString()
class  GameType                 net.minecraft.world.level.GameType
method GameType.getName()

class  Level                    net.minecraft.world.level.Level
method Level.dimension()
method Level.getDayTime()
method Level.getDefaultClockTime()
method Level.isRaining()
method Level.isThundering()
class  LevelReader              net.minecraft.world.level.LevelReader
method LevelReader.getBiome(net.minecraft.core.BlockPos)
class  Holder                   net.minecraft.core.Holder
method Holder.unwrapKey()
method Holder.getRegisteredName()

# ---- screens (classification) ------------------------------------------------------------------
class  Screen                   net.minecraft.client.gui.screens.Screen
method Screen.getTitle()
class  ChatScreen               net.minecraft.client.gui.screens.ChatScreen
field  ChatScreen.input
class  EditBox                  net.minecraft.client.gui.components.EditBox
method EditBox.getValue()
class  DisconnectedScreen       net.minecraft.client.gui.screens.DisconnectedScreen
field  DisconnectedScreen.reason
field  DisconnectedScreen.details
class  DisconnectionDetails     net.minecraft.network.DisconnectionDetails
method DisconnectionDetails.reason()
class  LoadingOverlay           net.minecraft.client.gui.screens.LoadingOverlay

class  TitleScreen              net.minecraft.client.gui.screens.TitleScreen
class  JoinMultiplayerScreen    net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
class  DirectJoinServerScreen   net.minecraft.client.gui.screens.DirectJoinServerScreen
class  EditServerScreen         net.minecraft.client.gui.screens.EditServerScreen net.minecraft.client.gui.screens.ManageServerScreen
class  SelectWorldScreen        net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
class  CreateWorldScreen        net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
class  EditWorldScreen          net.minecraft.client.gui.screens.worldselection.EditWorldScreen
class  RealmsMainScreen         com.mojang.realmsclient.RealmsMainScreen
class  RealmsScreen             net.minecraft.realms.RealmsScreen
class  PauseScreen              net.minecraft.client.gui.screens.PauseScreen
class  InBedChatScreen          net.minecraft.client.gui.screens.InBedChatScreen
class  AbstractContainerScreen  net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
class  InventoryScreen          net.minecraft.client.gui.screens.inventory.InventoryScreen
class  CreativeModeInventoryScreen net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
class  DeathScreen              net.minecraft.client.gui.screens.DeathScreen
class  ConnectScreen            net.minecraft.client.gui.screens.ConnectScreen
class  ProgressScreen           net.minecraft.client.gui.screens.ProgressScreen
class  LevelLoadingScreen       net.minecraft.client.gui.screens.LevelLoadingScreen
class  ReceivingLevelScreen     net.minecraft.client.gui.screens.ReceivingLevelScreen
class  GenericMessageScreen     net.minecraft.client.gui.screens.GenericMessageScreen net.minecraft.client.gui.screens.GenericDirtMessageScreen
class  GenericWaitingScreen     net.minecraft.client.gui.screens.GenericWaitingScreen
class  AdvancementsScreen       net.minecraft.client.gui.screens.advancements.AdvancementsScreen
class  StatsScreen              net.minecraft.client.gui.screens.achievement.StatsScreen
class  BookViewScreen           net.minecraft.client.gui.screens.inventory.BookViewScreen
class  BookEditScreen           net.minecraft.client.gui.screens.inventory.BookEditScreen
class  AbstractSignEditScreen   net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
class  OptionsScreen            net.minecraft.client.gui.screens.options.OptionsScreen net.minecraft.client.gui.screens.OptionsScreen
class  OptionsSubScreen         net.minecraft.client.gui.screens.options.OptionsSubScreen net.minecraft.client.gui.screens.OptionsSubScreen
class  PackSelectionScreen      net.minecraft.client.gui.screens.packs.PackSelectionScreen
class  SocialInteractionsScreen net.minecraft.client.gui.screens.social.SocialInteractionsScreen
class  ShareToLanScreen         net.minecraft.client.gui.screens.ShareToLanScreen
class  WinScreen                net.minecraft.client.gui.screens.WinScreen
class  AccessibilityOnboardingScreen net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
